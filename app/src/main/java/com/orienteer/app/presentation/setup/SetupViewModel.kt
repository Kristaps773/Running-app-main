package com.orienteer.app.presentation.setup

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.BuildConfig
import com.orienteer.app.data.local.ActiveRunStore
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.data.local.CoursePointsStore
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.local.RouteGenerationPreferences
import com.orienteer.app.data.local.RouteGenerationPreset
import com.orienteer.app.data.local.RouteGenerationRatios
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.repository.LocationRepository
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.domain.CourseEndpoints
import com.orienteer.app.domain.CoursePointsSet
import com.orienteer.app.domain.CourseReuseFromRoute
import com.orienteer.app.domain.CourseReusePayload
import com.orienteer.app.domain.CourseReuseSnapshot
import com.orienteer.app.domain.LockedCoursePoints
import com.orienteer.app.domain.usecase.GenerateRouteUseCase
import com.orienteer.app.domain.usecase.RouteGenerationFailedException
import com.orienteer.app.service.LocationTrackingService
import com.orienteer.app.util.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SetupUiState {
    data object Idle : SetupUiState
    data object LocatingGps : SetupUiState
    data object GeneratingRoute : SetupUiState
    data class RefiningRoute(val route: Route) : SetupUiState
    data class Success(val route: Route) : SetupUiState
    data class Error(val message: String) : SetupUiState
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val generateRoute: GenerateRouteUseCase,
    private val routeRepository: RouteRepository,
    private val activeRunStore: ActiveRunStore,
    private val preferencesStore: UserPreferencesStore,
    private val coursePointsStore: CoursePointsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private val _distanceKm = MutableStateFlow(5.0)
    val distanceKm: StateFlow<Double> = _distanceKm.asStateFlow()

    private val _minCheckpoints = MutableStateFlow(UserPreferencesStore.DEFAULT_MIN_CHECKPOINTS)
    val minCheckpoints: StateFlow<Int> = _minCheckpoints.asStateFlow()

    private val _maxCheckpoints = MutableStateFlow(UserPreferencesStore.DEFAULT_MAX_CHECKPOINTS)
    val maxCheckpoints: StateFlow<Int> = _maxCheckpoints.asStateFlow()

    /** Optional map-picked start; null → use GPS at generate time. */
    private val _customStart = MutableStateFlow<GeoPoint?>(null)
    val customStart: StateFlow<GeoPoint?> = _customStart.asStateFlow()

    /** Non-null → one-way finish; null → return-to-start. */
    private val _customFinish = MutableStateFlow<GeoPoint?>(null)
    val customFinish: StateFlow<GeoPoint?> = _customFinish.asStateFlow()

    /** User-locked intermediate checkpoints ("set in stone"). */
    private val _lockedCheckpoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val lockedCheckpoints: StateFlow<List<GeoPoint>> = _lockedCheckpoints.asStateFlow()

    /** When true and ≥2 locked pins, generator may permute their visit order each Generate. Default on. */
    private val _lockedAnyOrder = MutableStateFlow(true)
    val lockedAnyOrder: StateFlow<Boolean> = _lockedAnyOrder.asStateFlow()

    private val _lastCommittedRoute = MutableStateFlow<Route?>(null)
    val lastCommittedRoute: StateFlow<Route?> = _lastCommittedRoute.asStateFlow()

    private val _mapPrefsLoaded = MutableStateFlow(false)
    /** False until the first DataStore map-prefs emission (avoids green→IOF pink flash in picker). */
    val mapPrefsLoaded: StateFlow<Boolean> = _mapPrefsLoaded.asStateFlow()

    val mapDisplayPreferences: StateFlow<MapDisplayPreferences> =
        preferencesStore.mapDisplayPreferences
            .map { prefs ->
                _mapPrefsLoaded.value = true
                prefs
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                MapDisplayPreferences()
            )

    fun setBasemapStyle(style: BasemapStyle) {
        viewModelScope.launch { preferencesStore.setBasemapStyle(style) }
    }

    private data class WarmupKey(
        val distanceBucketM: Long,
        val minCheckpoints: Int,
        val maxCheckpoints: Int
    )

    private data class WarmedRoute(
        val origin: GeoPoint,
        val targetDistanceM: Double,
        val route: Route
    )

    private val warmupCache = mutableMapOf<WarmupKey, WarmedRoute>()
    private val warmupLock = Any()
    private var warmupJob: Job? = null
    /** Canonical key the in-flight warmup is prioritizing (distance + checkpoint window). */
    private var warmupPrimaryKey: WarmupKey? = null
    private var warmupPrimaryDone: CompletableDeferred<Unit>? = null
    private var distanceDebounceJob: Job? = null
    private var generationJob: Job? = null
    /** Bumped on every generate/cancel so late Main updates cannot revive a cancelled run. */
    private var generationSession: Int = 0
    private var activeGenerationRouteId: String? = null

    init {
        viewModelScope.launch {
            preferencesStore.routeGenerationPreferences.collect { prefs ->
                val (lo, hi) = coerceWindowWithLocked(prefs.minCheckpoints, prefs.maxCheckpoints)
                _minCheckpoints.value = lo
                _maxCheckpoints.value = hi
            }
        }
        viewModelScope.launch {
            _lastCommittedRoute.value = routeRepository.observeRoutes().first().firstOrNull()
        }
    }

    /**
     * Slider / prefs window, floored by locked pin count so min cannot drop below
     * custom intermediate points already placed.
     */
    private fun coerceWindowWithLocked(min: Int, max: Int): Pair<Int, Int> {
        val lockedFloor = _lockedCheckpoints.value.size
            .coerceIn(0, UserPreferencesStore.MAX_CHECKPOINTS)
        val absoluteFloor = maxOf(UserPreferencesStore.MIN_CHECKPOINTS, lockedFloor)
        val (lo0, hi0) = UserPreferencesStore.coerceCheckpointWindow(min, max)
        val lo = maxOf(lo0, absoluteFloor).coerceIn(
            UserPreferencesStore.MIN_CHECKPOINTS,
            UserPreferencesStore.MAX_CHECKPOINTS
        )
        val hi = maxOf(hi0, lo).coerceIn(lo, UserPreferencesStore.MAX_CHECKPOINTS)
        return lo to hi
    }

    fun setCheckpointWindow(min: Int, max: Int) {
        val (lo, hi) = coerceWindowWithLocked(min, max)
        _minCheckpoints.value = lo
        _maxCheckpoints.value = hi
        viewModelScope.launch {
            preferencesStore.setCheckpointWindow(lo, hi)
        }
        cancelWarmupJobOnly()
        synchronized(warmupLock) { warmupCache.clear() }
        distanceDebounceJob?.cancel()
        distanceDebounceJob = viewModelScope.launch {
            delay(WARMUP_DEBOUNCE_MS)
            if (_uiState.value is SetupUiState.Idle) {
                scheduleRouteWarmup()
            }
        }
    }

    val recentCoursePoints: StateFlow<List<CoursePointsSet>> =
        coursePointsStore.recent.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val favoriteCoursePoints: StateFlow<List<CoursePointsSet>> =
        coursePointsStore.favorites.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun setDistance(km: Double) {
        val start = _customStart.value
        val finish = _customFinish.value
        val locked = _lockedCheckpoints.value
        val anyOrder = _lockedAnyOrder.value && locked.size >= 2
        val maxKm = com.orienteer.app.domain.ProAccess.maxTargetDistanceKm()
        val originForFloor = start
        val coercedKm = if (originForFloor != null && (finish != null || locked.isNotEmpty())) {
            LockedCoursePoints.coerceTargetDistanceM(
                km * 1000.0, originForFloor, finish, locked, anyOrder = anyOrder
            ) / 1000.0
        } else {
            km.coerceIn(0.5, maxKm)
        }
        _distanceKm.value = coercedKm.coerceIn(0.5, maxKm)
        distanceDebounceJob?.cancel()
        distanceDebounceJob = viewModelScope.launch {
            delay(WARMUP_DEBOUNCE_MS)
            if (_uiState.value is SetupUiState.Idle) {
                scheduleRouteWarmup()
            }
        }
    }

    /**
     * @param start optional custom start (null → GPS at generate).
     * @param finish null means return-to-start; non-null means one-way.
     * @param locked intermediate pins that must be visited.
     * @param anyOrder when true, permute locked visit order each Generate (≥2 pins). Default on.
     */
    fun applyCoursePoints(
        start: GeoPoint?,
        finish: GeoPoint?,
        locked: List<GeoPoint> = emptyList(),
        anyOrder: Boolean = true,
        rememberRecent: Boolean = true
    ) {
        _customStart.value = start
        _customFinish.value = finish
        _lockedCheckpoints.value = locked.take(LockedCoursePoints.MAX_LOCKED_INTERMEDIATES)
        // Persist preference even with <2 pins so the next multi-pin course stays Any order.
        _lockedAnyOrder.value = anyOrder
        // Raise min/max so the Setup sliders cannot sit below the locked pin count.
        val (lo, hi) = coerceWindowWithLocked(_minCheckpoints.value, _maxCheckpoints.value)
        if (lo != _minCheckpoints.value || hi != _maxCheckpoints.value) {
            setCheckpointWindow(lo, hi)
        }
        val hasCustom = start != null || finish != null || locked.isNotEmpty()
        if (hasCustom) {
            setDistance(_distanceKm.value)
            cancelWarmupJobOnly()
            synchronized(warmupLock) { warmupCache.clear() }
            if (rememberRecent && start != null) {
                rememberActiveAsRecent()
            }
            if (finish == null && locked.isEmpty()) {
                scheduleRouteWarmup()
            }
        } else {
            scheduleRouteWarmup()
        }
    }

    /** @deprecated Prefer [applyCoursePoints]; kept for call-site compatibility. */
    fun applyCourseEndpoints(start: GeoPoint?, finish: GeoPoint?) {
        applyCoursePoints(start, finish, _lockedCheckpoints.value, _lockedAnyOrder.value)
    }

    /** Clears active pins only — Recent / Favorites are untouched. */
    fun clearCourseEndpoints() {
        _customStart.value = null
        _customFinish.value = null
        _lockedCheckpoints.value = emptyList()
        _lockedAnyOrder.value = true
        scheduleRouteWarmup()
    }

    fun applyCoursePointsSet(set: CoursePointsSet) {
        val start = set.start
        val hasGenerateSettings = set.targetDistanceKm != null ||
            set.minCheckpoints != null ||
            set.maxCheckpoints != null ||
            set.maxPathToAirRatio != null ||
            set.airDistanceToleranceM != null
        if (hasGenerateSettings && start != null) {
            applyReusePayload(
                CourseReusePayload(
                    start = start,
                    finish = set.finish,
                    locked = set.locked,
                    anyOrder = set.anyOrder,
                    targetDistanceKm = set.targetDistanceKm ?: _distanceKm.value,
                    minCheckpoints = set.minCheckpoints,
                    maxCheckpoints = set.maxCheckpoints,
                    generationPrefs = if (
                        set.maxPathToAirRatio != null ||
                        set.airDistanceToleranceM != null ||
                        set.minCheckpoints != null ||
                        set.maxCheckpoints != null
                    ) {
                        RouteGenerationPreferences(
                            maxPathToAirRatio = set.maxPathToAirRatio
                                ?: RouteGenerationPreferences().maxPathToAirRatio,
                            airDistanceToleranceM = set.airDistanceToleranceM
                                ?: RouteGenerationPreferences().airDistanceToleranceM,
                            minCheckpoints = set.minCheckpoints
                                ?: RouteGenerationPreferences().minCheckpoints,
                            maxCheckpoints = set.maxCheckpoints
                                ?: RouteGenerationPreferences().maxCheckpoints
                        )
                    } else {
                        null
                    }
                ),
                withPins = true
            )
        } else {
            applyCoursePoints(
                start = set.start,
                finish = set.finish,
                locked = set.locked,
                anyOrder = set.anyOrder
            )
        }
    }

    /**
     * History → New from this run.
     * @param withPins when false (Knobs only), clears locked pins after applying distance/prefs.
     */
    fun applyReusePayload(payload: CourseReusePayload, withPins: Boolean) {
        payload.generationPrefs?.let { prefs ->
            setCheckpointWindow(
                payload.minCheckpoints ?: prefs.minCheckpoints,
                payload.maxCheckpoints ?: prefs.maxCheckpoints
            )
            viewModelScope.launch {
                preferencesStore.setMaxPathToAirRatio(prefs.maxPathToAirRatio)
                preferencesStore.setAirDistanceToleranceM(prefs.airDistanceToleranceM)
            }
        } ?: run {
            val min = payload.minCheckpoints
            val max = payload.maxCheckpoints
            if (min != null && max != null) setCheckpointWindow(min, max)
        }
        _distanceKm.value = payload.targetDistanceKm
        if (withPins) {
            applyCoursePoints(
                start = payload.start,
                finish = payload.finish,
                locked = payload.locked,
                anyOrder = payload.anyOrder
            )
        } else {
            applyCoursePoints(
                start = payload.start,
                finish = payload.finish,
                locked = emptyList(),
                anyOrder = true,
                rememberRecent = false
            )
        }
    }

    fun applyReuseFromRoute(route: Route, withPins: Boolean) {
        val payload = CourseReuseFromRoute.fromRoute(route) ?: return
        applyReusePayload(payload, withPins)
    }

    fun rememberActiveAsRecent() {
        val start = _customStart.value ?: return
        val set = CoursePointsSet(
            id = UUID.randomUUID().toString(),
            savedAtMs = System.currentTimeMillis(),
            start = start,
            finish = _customFinish.value,
            locked = _lockedCheckpoints.value,
            anyOrder = _lockedAnyOrder.value,
            usedGpsStart = false
        )
        viewModelScope.launch { coursePointsStore.pushRecent(set) }
    }

    /** @return null if saved; favorites list when full. */
    suspend fun saveSetToFavorites(set: CoursePointsSet): List<CoursePointsSet>? =
        coursePointsStore.addFavorite(set)

    /** @return null if saved or nothing to save; favorites list when full. */
    suspend fun saveActiveToFavorites(): List<CoursePointsSet>? {
        val start = _customStart.value ?: return null
        return saveSetToFavorites(
            CoursePointsSet(
                id = UUID.randomUUID().toString(),
                savedAtMs = System.currentTimeMillis(),
                start = start,
                finish = _customFinish.value,
                locked = _lockedCheckpoints.value,
                anyOrder = _lockedAnyOrder.value,
                usedGpsStart = false
            )
        )
    }

    /** Pins + generate knobs from a past route (History → favorites). */
    fun favoriteSetFromRoute(route: Route): CoursePointsSet? {
        val payload = CourseReuseFromRoute.fromRoute(route) ?: return null
        val prefs = payload.generationPrefs
        return CoursePointsSet(
            id = UUID.randomUUID().toString(),
            savedAtMs = System.currentTimeMillis(),
            start = payload.start,
            finish = payload.finish,
            locked = payload.locked,
            anyOrder = payload.anyOrder,
            usedGpsStart = false,
            targetDistanceKm = payload.targetDistanceKm,
            minCheckpoints = payload.minCheckpoints ?: prefs?.minCheckpoints,
            maxCheckpoints = payload.maxCheckpoints ?: prefs?.maxCheckpoints,
            maxPathToAirRatio = prefs?.maxPathToAirRatio,
            airDistanceToleranceM = prefs?.airDistanceToleranceM
        )
    }

    /**
     * History → Add to favorites: pins + generate knobs so Setup → Use can regenerate.
     * @return null if saved or nothing to save; favorites list when full.
     */
    suspend fun saveRoutePinsToFavorites(route: Route): List<CoursePointsSet>? {
        val set = favoriteSetFromRoute(route) ?: return null
        return saveSetToFavorites(set)
    }

    fun removeRecentCoursePoints(id: String) {
        viewModelScope.launch { coursePointsStore.removeRecent(id) }
    }

    fun removeFavoriteCoursePoints(id: String) {
        viewModelScope.launch { coursePointsStore.removeFavorite(id) }
    }

    fun replaceFavoriteCoursePoints(removeId: String, set: CoursePointsSet) {
        viewModelScope.launch { coursePointsStore.replaceFavorite(removeId, set) }
    }

    fun renameFavoriteCoursePoints(id: String, name: String) {
        viewModelScope.launch { coursePointsStore.renameFavorite(id, name) }
    }

    private fun Route.withCurrentReuseSnapshot(prefs: RouteGenerationPreferences): Route {
        val locked = _lockedCheckpoints.value
        val anyOrder = _lockedAnyOrder.value && locked.size >= 2
        // Persist the *user* min/max from Setup sliders, not the effective floor used mid-generate.
        val snapPrefs = prefs.copy(
            minCheckpoints = _minCheckpoints.value,
            maxCheckpoints = _maxCheckpoints.value
        )
        return copy(
            reuseSnapshot = CourseReuseSnapshot.from(
                anyOrder = anyOrder,
                prefs = snapPrefs,
                start = _customStart.value ?: checkpoints.firstOrNull()?.position,
                finish = _customFinish.value,
                lockedPins = locked
            )
        )
    }

    private suspend fun persistGeneratedRoute(
        route: Route,
        prefs: RouteGenerationPreferences,
        pushRecent: Boolean = false
    ) {
        val saved = route.withCurrentReuseSnapshot(prefs)
        routeRepository.saveRoute(saved)
        _lastCommittedRoute.value = saved
        if (pushRecent && (_customStart.value != null || _lockedCheckpoints.value.isNotEmpty())) {
            rememberActiveAsRecent()
        }
    }

    suspend fun resolveGpsForPicker(): GeoPoint? =
        runCatching { locationRepository.getCurrentLocation() }.getOrNull()

    fun onSetupScreenActive() {
        scheduleRouteWarmup()
    }

    fun onSetupScreenInactive() {
        cancelWarmupJobOnly()
        synchronized(warmupLock) { warmupCache.clear() }
    }

    private fun cancelWarmupJobOnly() {
        warmupJob?.cancel()
        warmupJob = null
        synchronized(warmupLock) {
            warmupPrimaryDone?.complete(Unit)
            warmupPrimaryDone = null
            warmupPrimaryKey = null
        }
    }

    private fun scheduleRouteWarmup() {
        // Skip warmup for one-way courses (cache is origin+distance only).
        if (_customFinish.value != null) {
            cancelWarmupJobOnly()
            synchronized(warmupLock) { warmupCache.clear() }
            return
        }
        cancelWarmupJobOnly()
        val currentM = (_distanceKm.value * 1000.0).coerceIn(500.0, 50_000.0)
        val minCp = _minCheckpoints.value
        val maxCp = _maxCheckpoints.value
        val primaryKey = warmupKey(currentM, minCp, maxCp)
        val primaryDone = CompletableDeferred<Unit>()
        synchronized(warmupLock) {
            warmupPrimaryKey = primaryKey
            warmupPrimaryDone = primaryDone
        }
        warmupJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                delay(WARMUP_START_DELAY_MS)
                if (_uiState.value != SetupUiState.Idle) return@launch
                // Slider first; then most common run-history distance (5 km if no history).
                val frequent = routeRepository.getFrequentTargetDistancesM(top = 2)
                val seen = LinkedHashSet<Long>()
                val targets = buildList {
                    if (seen.add(canonicalTargetM(currentM))) add(currentM)
                    for (f in frequent) {
                        val m = f.coerceIn(500.0, 50_000.0)
                        if (seen.add(canonicalTargetM(m))) add(m)
                    }
                }.take(MAX_WARMUP_TARGETS)

                val origin = runCatching { locationRepository.getCurrentLocation() }.getOrNull()
                    ?: return@launch

                synchronized(warmupLock) {
                    warmupCache.entries.removeAll { (_, w) ->
                        GeoUtils.distanceMeters(w.origin, origin) > ORIGIN_MATCH_MAX_M
                    }
                }

                val prefs = preferencesStore.routeGenerationPreferences.first().copy(
                    minCheckpoints = minCp,
                    maxCheckpoints = maxCp
                )
                for (targetM in targets) {
                    ensureActive()
                    if (_uiState.value != SetupUiState.Idle) return@launch
                    val key = warmupKey(targetM, minCp, maxCp)
                    val alreadyHave = synchronized(warmupLock) {
                        val existing = warmupCache[key]
                        existing != null &&
                            GeoUtils.distanceMeters(existing.origin, origin) <= ORIGIN_MATCH_MAX_M
                    }
                    if (alreadyHave) {
                        if (key == primaryKey) primaryDone.complete(Unit)
                        continue
                    }

                    val route = withContext(Dispatchers.Default) {
                        generateRoute(origin, targetM, prefs).getOrNull()
                    }
                    if (!isActive) return@launch
                    if (route != null && _uiState.value is SetupUiState.Idle) {
                        synchronized(warmupLock) {
                            warmupCache[key] = WarmedRoute(origin, targetM, route)
                        }
                    }
                    if (key == primaryKey) primaryDone.complete(Unit)
                }
            } finally {
                primaryDone.complete(Unit)
                synchronized(warmupLock) {
                    if (warmupPrimaryDone === primaryDone) {
                        warmupPrimaryDone = null
                        warmupPrimaryKey = null
                    }
                }
            }
        }
    }

    fun generateRoute() {
        if (generationJob?.isActive == true) return
        val session = ++generationSession
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                stopActiveRunIfAny()
                withContext(Dispatchers.Main) {
                    if (session == generationSession) {
                        _uiState.value = SetupUiState.LocatingGps
                    }
                }
                ensureActive()
                if (session != generationSession) return@launch

                val gps = try {
                    locationRepository.getCurrentLocation()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Custom start alone can still generate without live GPS.
                    if (_customStart.value == null) {
                        cancelWarmupJobOnly()
                        reportGenerationFailureToSentry(
                            error = e,
                            stage = "gps",
                            targetDistanceM = _distanceKm.value * 1000.0,
                            prefs = null,
                            origin = null
                        )
                        withContext(Dispatchers.Main) {
                            if (session == generationSession) {
                                _uiState.value = SetupUiState.Error("GPS unavailable: ${e.message}")
                            }
                        }
                        scheduleRouteWarmup()
                        return@launch
                    }
                    null
                }

                val origin = _customStart.value ?: gps
                    ?: run {
                        withContext(Dispatchers.Main) {
                            if (session == generationSession) {
                                _uiState.value = SetupUiState.Error("GPS unavailable")
                            }
                        }
                        return@launch
                    }
                ensureActive()
                if (session != generationSession) return@launch
                val destination = _customFinish.value
                val locked = _lockedCheckpoints.value
                val anyOrder = _lockedAnyOrder.value && locked.size >= 2

                var userTargetM = _distanceKm.value * 1000.0
                if (destination != null || locked.isNotEmpty()) {
                    userTargetM = LockedCoursePoints.coerceTargetDistanceM(
                        userTargetM, origin, destination, locked, anyOrder = anyOrder
                    )
                    when (
                        val decision = LockedCoursePoints.evaluateTargetDistance(
                            userTargetM, origin, destination, locked, anyOrder = anyOrder
                        )
                    ) {
                        is CourseEndpoints.TargetDistanceDecision.EndpointsExceedMax -> {
                            withContext(Dispatchers.Main) {
                                if (session == generationSession) {
                                    _uiState.value = SetupUiState.Error(
                                        "Locked course points are ${GeoUtils.formatDistance(decision.airM)} apart — " +
                                            "over the ${GeoUtils.formatDistance(decision.maxM)} limit."
                                    )
                                }
                            }
                            return@launch
                        }
                        is CourseEndpoints.TargetDistanceDecision.BelowMinimum,
                        is CourseEndpoints.TargetDistanceDecision.AboveMaximum -> {
                            withContext(Dispatchers.Main) {
                                if (session == generationSession) {
                                    _uiState.value = SetupUiState.Error(
                                        "Invalid target distance for these course points."
                                    )
                                }
                            }
                            return@launch
                        }
                        is CourseEndpoints.TargetDistanceDecision.Ok ->
                            userTargetM = decision.targetM
                    }
                    withContext(Dispatchers.Main) {
                        if (session == generationSession) {
                            _distanceKm.value = userTargetM / 1000.0
                        }
                    }
                }
                ensureActive()
                if (session != generationSession) return@launch

                val minCp = LockedCoursePoints.effectiveMinIntermediates(
                    locked.size,
                    _minCheckpoints.value
                )
                val maxCp = _maxCheckpoints.value.coerceAtLeast(minCp)
                val userKey = warmupKey(userTargetM, minCp, maxCp)
                // Warmup is loop-only without locked pins.
                val prefs = preferencesStore.routeGenerationPreferences.first().copy(
                    minCheckpoints = minCp,
                    maxCheckpoints = maxCp
                )
                if (destination == null && locked.isEmpty()) {
                    takeWarmRouteIfReady(origin, userTargetM, minCp, maxCp)?.let { route ->
                        if (session != generationSession) return@launch
                        cancelWarmupJobOnly()
                        persistGeneratedRoute(route, prefs, pushRecent = true)
                        withContext(Dispatchers.Main) {
                            if (session == generationSession) {
                                _uiState.value = SetupUiState.Success(route.withCurrentReuseSnapshot(prefs))
                            }
                        }
                        return@launch
                    }

                    // Same target as in-flight warmup → wait for it instead of cancelling.
                    val awaitSame = synchronized(warmupLock) {
                        if (warmupPrimaryKey == userKey && warmupJob?.isActive == true) {
                            warmupPrimaryDone
                        } else {
                            null
                        }
                    }
                    if (awaitSame != null) {
                        withContext(Dispatchers.Main) {
                            if (session == generationSession) {
                                _uiState.value = SetupUiState.GeneratingRoute
                            }
                        }
                        if (session != generationSession) return@launch
                        withTimeoutOrNull(WARMUP_AWAIT_MAX_MS) { awaitSame.await() }
                        ensureActive()
                        if (session != generationSession) return@launch
                        takeWarmRouteIfReady(origin, userTargetM, minCp, maxCp)?.let { route ->
                            cancelWarmupJobOnly()
                            persistGeneratedRoute(route, prefs, pushRecent = true)
                            withContext(Dispatchers.Main) {
                                if (session == generationSession) {
                                    _uiState.value = SetupUiState.Success(route.withCurrentReuseSnapshot(prefs))
                                }
                            }
                            return@launch
                        }
                    }
                }
                if (session != generationSession) return@launch
                cancelWarmupJobOnly()

                withContext(Dispatchers.Main) {
                    if (session == generationSession) {
                        _uiState.value = SetupUiState.GeneratingRoute
                    }
                }
                ensureActive()
                if (session != generationSession) return@launch
                activeGenerationRouteId = null
                val result = generateRoute(
                    origin = origin,
                    targetDistanceM = userTargetM,
                    generationPrefs = prefs,
                    destination = destination,
                    lockedCheckpoints = locked,
                    lockedAnyOrder = anyOrder
                ) onDraft@{ route ->
                    if (session != generationSession) return@onDraft
                    activeGenerationRouteId = route.id
                    withContext(Dispatchers.Main) {
                        if (session != generationSession) return@withContext
                        if ((_uiState.value is SetupUiState.GeneratingRoute ||
                                _uiState.value is SetupUiState.RefiningRoute) &&
                            route.hasRoutedWalkingPath
                        ) {
                            _uiState.value = SetupUiState.RefiningRoute(route.withCurrentReuseSnapshot(prefs))
                        }
                    }
                }
                if (session != generationSession) return@launch
                result
                    .onSuccess { route ->
                        if (session != generationSession) return@onSuccess
                        activeGenerationRouteId = null
                        persistGeneratedRoute(route, prefs, pushRecent = true)
                        withContext(Dispatchers.Main) {
                            if (session != generationSession) return@withContext
                            // Skip if Setup already reset after navigating on a refining update.
                            when (_uiState.value) {
                                is SetupUiState.GeneratingRoute,
                                is SetupUiState.RefiningRoute -> {
                                    _uiState.value = SetupUiState.Success(route.withCurrentReuseSnapshot(prefs))
                                }
                                else -> Unit
                            }
                        }
                    }
                    .onFailure { e ->
                        if (session != generationSession) return@onFailure
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        reportGenerationFailureToSentry(
                            error = e,
                            stage = "generate",
                            targetDistanceM = userTargetM,
                            prefs = prefs,
                            origin = origin
                        )
                        activeGenerationRouteId?.let { failedId ->
                            routeRepository.deleteRoute(failedId)
                        }
                        activeGenerationRouteId = null
                        withContext(Dispatchers.Main) {
                            if (session == generationSession) {
                                _uiState.value = SetupUiState.Error(userFacingGenerationError(e))
                            }
                        }
                        scheduleRouteWarmup()
                    }
            } catch (t: Throwable) {
                // Coroutine cancellation is normal (user cancel / navigates away); not a failure.
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (session != generationSession) return@launch
                reportGenerationFailureToSentry(
                    error = t,
                    stage = "unexpected",
                    targetDistanceM = _distanceKm.value * 1000.0,
                    prefs = null,
                    origin = null
                )
                withContext(Dispatchers.Main) {
                    if (session == generationSession) {
                        _uiState.value = SetupUiState.Error(userFacingGenerationError(t))
                    }
                }
                scheduleRouteWarmup()
            }
        }
    }

    /** Stops in-flight GPS / generate / refine and returns Setup to Idle. */
    fun cancelGeneration() {
        generationSession++
        val job = generationJob
        generationJob = null
        val draftId = activeGenerationRouteId
        activeGenerationRouteId = null
        cancelWarmupJobOnly()
        // Idle first so in-flight Main updates see non-generating state and skip.
        _uiState.value = SetupUiState.Idle
        job?.cancel(kotlinx.coroutines.CancellationException("User cancelled route generation"))
        if (draftId != null) {
            viewModelScope.launch {
                routeRepository.deleteRoute(draftId)
            }
        }
        scheduleRouteWarmup()
    }

    private fun userFacingGenerationError(error: Throwable): String {
        val cause = generateSequence(error) { it.cause }.firstOrNull {
            it is UnknownHostException || it is SocketTimeoutException || it is IOException
        }
        return when {
            cause is UnknownHostException ->
                "No network connection. Connect to the internet and try again."
            cause is SocketTimeoutException ->
                "Routing timed out. Try again or choose a shorter distance."
            cause is IOException ->
                "Routing service unavailable. Check your connection and try again."
            !error.message.isNullOrBlank() -> error.message!!
            else -> "Route generation failed. Try again."
        }
    }

    /**
     * Sends generation failures to Sentry with routing context (no precise GPS).
     * Skipped when DSN is blank (local builds without Sentry).
     */
    private fun reportGenerationFailureToSentry(
        error: Throwable,
        stage: String,
        targetDistanceM: Double,
        prefs: RouteGenerationPreferences?,
        origin: GeoPoint?
    ) {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        val genFail = error as? RouteGenerationFailedException
            ?: error.cause as? RouteGenerationFailedException
        val diagnostics = genFail?.diagnostics.orEmpty()
        val category = (diagnostics["routing_failure_kind"] as? String)
            ?.takeIf { it != "none" }
            ?: categorizeGenerationFailure(error)
        val preset = prefs?.let {
            RouteGenerationPreset.matching(
                RouteGenerationRatios(
                    maxPathToAirRatio = it.maxPathToAirRatio.toDouble(),
                    airDistanceToleranceM = it.airDistanceToleranceM.toDouble()
                )
            )?.name ?: "CUSTOM"
        }
        val causeChain = generateSequence(error as Throwable?) { it?.cause }
            .filterNotNull()
            .map { "${it.javaClass.simpleName}: ${it.message}" }
            .toList()
        Sentry.withScope { scope ->
            scope.level = SentryLevel.ERROR
            scope.setTag("feature", "route_generation")
            scope.setTag("generation_stage", stage)
            scope.setTag("failure_category", category)
            if (preset != null) scope.setTag("route_preset", preset)
            (diagnostics["generator"] as? String)?.let { scope.setTag("generator", it) }
            (diagnostics["osrm_code"] as? String)?.let { scope.setTag("osrm_code", it) }
            (diagnostics["http_status"] as? Number)?.let { scope.setTag("http_status", it.toString()) }
            (diagnostics["had_routed_path"] as? Boolean)?.let {
                scope.setTag("had_routed_path", it.toString())
            }
            (diagnostics["rejection_gate"] as? String)?.let {
                scope.setTag("rejection_gate", it)
            }
            (diagnostics["last_osrm_call"] as? String)?.let {
                scope.setTag("last_osrm_call", it)
            }

            scope.addBreadcrumb(
                Breadcrumb().apply {
                    this.category = "route_generation"
                    this.level = SentryLevel.ERROR
                    this.message = "Generation failed: $category / $stage" +
                        (diagnostics["rejection_gate"]?.let { " [$it]" } ?: "")
                    setData("target_m", targetDistanceM.roundToInt())
                    setData("user_message", userFacingGenerationError(error))
                    diagnostics["rejection_gate"]?.let { setData("rejection_gate", it) }
                    diagnostics["generator"]?.let { setData("generator", it) }
                }
            )

            scope.setContexts(
                "route_generation",
                buildMap {
                    put("stage", stage)
                    put("failure_category", category)
                    put("target_distance_m", targetDistanceM.roundToInt())
                    put("target_distance_km", (targetDistanceM / 1000.0 * 10).roundToInt() / 10.0)
                    put("user_message", userFacingGenerationError(error))
                    put("exception_message", error.message)
                    put("exception_type", error.javaClass.simpleName)
                    put("cause_chain", causeChain)
                    put("device_locale", Locale.getDefault().toLanguageTag())
                    put("format_locale", Locale.getDefault(Locale.Category.FORMAT).toLanguageTag())
                    put("android_sdk", android.os.Build.VERSION.SDK_INT)
                    put("build_type", if (BuildConfig.DEBUG) "debug" else "release")
                    put("app_version", "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}")
                    if (prefs != null) {
                        put("max_path_to_air_ratio", prefs.maxPathToAirRatio)
                        put("air_tolerance_m", prefs.airDistanceToleranceM)
                        put("route_preset", preset)
                    }
                    // Coarse location only (~1 km) — not precise GPS.
                    if (origin != null) {
                        put("origin_lat_approx", (origin.latitude * 10).roundToInt() / 10.0)
                        put("origin_lon_approx", (origin.longitude * 10).roundToInt() / 10.0)
                    }
                    // Full structured dump from GenerateRouteUseCase (attempts, OSRM, best route, …).
                    putAll(diagnostics)
                }
            )
            scope.fingerprint = listOf(
                "route_generation",
                stage,
                category,
                diagnostics["rejection_gate"]?.toString() ?: "unknown_gate",
                diagnostics["generator"]?.toString() ?: "unknown_generator"
            )
            Sentry.captureException(genFail ?: error)
        }
    }

    private fun categorizeGenerationFailure(error: Throwable): String {
        val cause = generateSequence(error as Throwable?) { it?.cause }.filterNotNull()
        val message = cause.mapNotNull { it.message }.joinToString(" ").lowercase()
        return when {
            cause.any { it is UnknownHostException } || message.contains("no network") ->
                "network_offline"
            cause.any { it is SocketTimeoutException } || message.contains("timed out") ->
                "timeout"
            cause.any { it is IOException } &&
                (message.contains("reach") || message.contains("unavailable") || message.contains("connection")) ->
                "network_io"
            message.contains("invalid data") || message.contains("response parse") ->
                "routing_parse"
            message.contains("http") ->
                "routing_http"
            message.contains("snap") ->
                "road_snap"
            message.contains("path/air") || message.contains("acceptable route") ||
                message.contains("adjust settings") ->
                "settings_reject"
            message.contains("gps") ->
                "gps"
            message.contains("unreachable") || message.contains("no roads") ->
                "unreachable"
            else -> "other"
        }
    }

    private suspend fun stopActiveRunIfAny() {
        val activeId = activeRunStore.readActiveRouteId()
        if (activeId == null) return
        context.startService(
            Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_STOP
            }
        )
        activeRunStore.clear()
    }

    fun dismissError() {
        _uiState.value = SetupUiState.Idle
        scheduleRouteWarmup()
    }

    fun resetAfterNavigation() {
        _uiState.value = SetupUiState.Idle
        scheduleRouteWarmup()
    }

    private fun takeWarmRouteIfReady(
        origin: GeoPoint,
        userTargetM: Double,
        minCheckpoints: Int,
        maxCheckpoints: Int
    ): Route? {
        val key = warmupKey(userTargetM, minCheckpoints, maxCheckpoints)
        synchronized(warmupLock) {
            val w = warmupCache.remove(key) ?: return null
            if (GeoUtils.distanceMeters(w.origin, origin) > ORIGIN_MATCH_MAX_M) return null
            return w.route.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis()
            )
        }
    }

    private companion object {
        private const val WARM_TARGET_BUCKET_M = 250.0
        private const val ORIGIN_MATCH_MAX_M = 400.0
        /** Slider + one run-history favourite (5 km when no history). */
        private const val MAX_WARMUP_TARGETS = 2
        private const val WARMUP_DEBOUNCE_MS = 500L
        private const val WARMUP_START_DELAY_MS = 4_000L
        private const val WARMUP_AWAIT_MAX_MS = 55_000L

        private fun canonicalTargetM(m: Double): Long {
            val clamped = m.coerceIn(500.0, 50_000.0)
            return ((clamped / WARM_TARGET_BUCKET_M).roundToInt() * WARM_TARGET_BUCKET_M.toLong())
                .coerceIn(500L, 50_000L)
        }

        private fun warmupKey(m: Double, minCheckpoints: Int, maxCheckpoints: Int): WarmupKey =
            WarmupKey(
                distanceBucketM = canonicalTargetM(m),
                minCheckpoints = minCheckpoints,
                maxCheckpoints = maxCheckpoints
            )
    }
}
