package com.orienteer.app.presentation.setup

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.BuildConfig
import com.orienteer.app.data.local.ActiveRunStore
import com.orienteer.app.data.local.BasemapStyle
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
    private val preferencesStore: UserPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private val _distanceKm = MutableStateFlow(5.0)
    val distanceKm: StateFlow<Double> = _distanceKm.asStateFlow()

    /** Optional map-picked start; null → use GPS at generate time. */
    private val _customStart = MutableStateFlow<GeoPoint?>(null)
    val customStart: StateFlow<GeoPoint?> = _customStart.asStateFlow()

    /** Non-null → one-way finish; null → return-to-start. */
    private val _customFinish = MutableStateFlow<GeoPoint?>(null)
    val customFinish: StateFlow<GeoPoint?> = _customFinish.asStateFlow()

    val latestRoute: StateFlow<Route?> = routeRepository.observeRoutes()
        .map { routes -> routes.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val mapDisplayPreferences: StateFlow<MapDisplayPreferences> =
        preferencesStore.mapDisplayPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MapDisplayPreferences()
        )

    fun cycleBasemapStyle() {
        viewModelScope.launch {
            val styles = BasemapStyle.entries
            val current = mapDisplayPreferences.value.basemapStyle
            val next = styles[(styles.indexOf(current) + 1) % styles.size]
            preferencesStore.setBasemapStyle(next)
        }
    }

    private data class WarmedRoute(
        val origin: GeoPoint,
        val targetDistanceM: Double,
        val route: Route
    )

    private val warmupCache = mutableMapOf<Long, WarmedRoute>()
    private val warmupLock = Any()
    private var warmupJob: Job? = null
    /** Canonical metres key the in-flight warmup is prioritizing (slider distance). */
    private var warmupPrimaryKey: Long? = null
    private var warmupPrimaryDone: CompletableDeferred<Unit>? = null
    private var distanceDebounceJob: Job? = null
    private var activeGenerationRouteId: String? = null

    fun setDistance(km: Double) {
        val start = _customStart.value
        val finish = _customFinish.value
        val maxKm = com.orienteer.app.domain.ProAccess.maxTargetDistanceKm()
        val coercedKm = if (finish != null && start != null) {
            CourseEndpoints.coerceTargetDistanceM(km * 1000.0, start, finish) / 1000.0
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
     * @param start optional custom start (null keeps previous / GPS-only).
     * @param finish null means return-to-start; non-null means one-way.
     */
    fun applyCourseEndpoints(start: GeoPoint?, finish: GeoPoint?) {
        _customStart.value = start
        _customFinish.value = finish
        if (finish != null && start != null) {
            setDistance(_distanceKm.value)
            cancelWarmupJobOnly()
            synchronized(warmupLock) { warmupCache.clear() }
        } else if (finish == null) {
            scheduleRouteWarmup()
        }
    }

    fun clearCourseEndpoints() {
        _customStart.value = null
        _customFinish.value = null
        scheduleRouteWarmup()
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
        val primaryKey = canonicalTargetM(currentM)
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

                val prefs = preferencesStore.routeGenerationPreferences.first()
                for (targetM in targets) {
                    ensureActive()
                    if (_uiState.value != SetupUiState.Idle) return@launch
                    val key = canonicalTargetM(targetM)
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
        viewModelScope.launch(Dispatchers.Default) {
            try {
                stopActiveRunIfAny()
                withContext(Dispatchers.Main) {
                    _uiState.value = SetupUiState.LocatingGps
                }

                val gps = runCatching { locationRepository.getCurrentLocation() }
                    .getOrElse { e ->
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
                                _uiState.value = SetupUiState.Error("GPS unavailable: ${e.message}")
                            }
                            scheduleRouteWarmup()
                            return@launch
                        }
                        null
                    }

                val origin = _customStart.value ?: gps
                    ?: run {
                        withContext(Dispatchers.Main) {
                            _uiState.value = SetupUiState.Error("GPS unavailable")
                        }
                        return@launch
                    }
                val destination = _customFinish.value

                var userTargetM = _distanceKm.value * 1000.0
                if (destination != null) {
                    userTargetM = CourseEndpoints.coerceTargetDistanceM(
                        userTargetM, origin, destination
                    )
                    when (
                        val decision = CourseEndpoints.evaluateTargetDistance(
                            userTargetM, origin, destination
                        )
                    ) {
                        is CourseEndpoints.TargetDistanceDecision.EndpointsExceedMax -> {
                            withContext(Dispatchers.Main) {
                                _uiState.value = SetupUiState.Error(
                                    "Start and finish are ${GeoUtils.formatDistance(decision.airM)} apart — " +
                                        "over the ${GeoUtils.formatDistance(decision.maxM)} limit."
                                )
                            }
                            return@launch
                        }
                        is CourseEndpoints.TargetDistanceDecision.BelowMinimum,
                        is CourseEndpoints.TargetDistanceDecision.AboveMaximum -> {
                            withContext(Dispatchers.Main) {
                                _uiState.value = SetupUiState.Error(
                                    "Invalid target distance for these start/finish points."
                                )
                            }
                            return@launch
                        }
                        is CourseEndpoints.TargetDistanceDecision.Ok ->
                            userTargetM = decision.targetM
                    }
                    withContext(Dispatchers.Main) {
                        _distanceKm.value = userTargetM / 1000.0
                    }
                }

                val userKey = canonicalTargetM(userTargetM)
                if (destination == null) {
                    takeWarmRouteIfReady(origin, userTargetM)?.let { route ->
                        cancelWarmupJobOnly()
                        routeRepository.saveRoute(route)
                        withContext(Dispatchers.Main) {
                            _uiState.value = SetupUiState.Success(route)
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
                            _uiState.value = SetupUiState.GeneratingRoute
                        }
                        withTimeoutOrNull(WARMUP_AWAIT_MAX_MS) { awaitSame.await() }
                        takeWarmRouteIfReady(origin, userTargetM)?.let { route ->
                            cancelWarmupJobOnly()
                            routeRepository.saveRoute(route)
                            withContext(Dispatchers.Main) {
                                _uiState.value = SetupUiState.Success(route)
                            }
                            return@launch
                        }
                    }
                }
                cancelWarmupJobOnly()

                withContext(Dispatchers.Main) {
                    _uiState.value = SetupUiState.GeneratingRoute
                }
                activeGenerationRouteId = null
                val prefs = preferencesStore.routeGenerationPreferences.first()
                val result = generateRoute(
                    origin = origin,
                    targetDistanceM = userTargetM,
                    generationPrefs = prefs,
                    destination = destination
                ) { route ->
                    activeGenerationRouteId = route.id
                    routeRepository.saveRoute(route)
                    withContext(Dispatchers.Main) {
                        if ((_uiState.value is SetupUiState.GeneratingRoute ||
                                _uiState.value is SetupUiState.RefiningRoute) &&
                            route.hasRoutedWalkingPath
                        ) {
                            _uiState.value = SetupUiState.RefiningRoute(route)
                        }
                    }
                }
                result
                    .onSuccess { route ->
                        activeGenerationRouteId = null
                        routeRepository.saveRoute(route)
                        withContext(Dispatchers.Main) {
                            // Skip if Setup already reset after navigating on a refining update.
                            when (_uiState.value) {
                                is SetupUiState.GeneratingRoute,
                                is SetupUiState.RefiningRoute -> {
                                    _uiState.value = SetupUiState.Success(route)
                                }
                                else -> Unit
                            }
                        }
                    }
                    .onFailure { e ->
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
                            _uiState.value = SetupUiState.Error(userFacingGenerationError(e))
                        }
                        scheduleRouteWarmup()
                    }
            } catch (t: Throwable) {
                // Coroutine cancellation is normal (user navigates away / new generate); not a failure.
                if (t is kotlinx.coroutines.CancellationException) throw t
                reportGenerationFailureToSentry(
                    error = t,
                    stage = "unexpected",
                    targetDistanceM = _distanceKm.value * 1000.0,
                    prefs = null,
                    origin = null
                )
                withContext(Dispatchers.Main) {
                    _uiState.value = SetupUiState.Error(userFacingGenerationError(t))
                }
                scheduleRouteWarmup()
            }
        }
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

    private fun takeWarmRouteIfReady(origin: GeoPoint, userTargetM: Double): Route? {
        val key = canonicalTargetM(userTargetM)
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
    }
}
