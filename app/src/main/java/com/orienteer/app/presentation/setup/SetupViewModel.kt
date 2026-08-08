package com.orienteer.app.presentation.setup

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.data.local.ActiveRunStore
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.repository.LocationRepository
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.domain.usecase.GenerateRouteUseCase
import com.orienteer.app.service.LocationTrackingService
import com.orienteer.app.util.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
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

    val latestRoute: StateFlow<Route?> = routeRepository.observeRoutes()
        .map { routes -> routes.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class WarmedRoute(
        val origin: GeoPoint,
        val targetDistanceM: Double,
        val route: Route
    )

    private val warmupCache = mutableMapOf<Long, WarmedRoute>()
    private val warmupLock = Any()
    private var warmupJob: Job? = null
    private var distanceDebounceJob: Job? = null
    private var activeGenerationRouteId: String? = null

    fun setDistance(km: Double) {
        _distanceKm.value = km.coerceIn(0.5, 50.0)
        distanceDebounceJob?.cancel()
        distanceDebounceJob = viewModelScope.launch {
            delay(WARMUP_DEBOUNCE_MS)
            if (_uiState.value is SetupUiState.Idle) {
                scheduleRouteWarmup()
            }
        }
    }

    fun onSetupScreenActive() {
        scheduleRouteWarmup()
    }

    fun onSetupScreenInactive() {
        warmupJob?.cancel()
        warmupJob = null
        synchronized(warmupLock) { warmupCache.clear() }
    }

    private fun cancelWarmupJobOnly() {
        warmupJob?.cancel()
        warmupJob = null
    }

    private fun scheduleRouteWarmup() {
        cancelWarmupJobOnly()
        warmupJob = viewModelScope.launch(Dispatchers.Default) {
            if (_uiState.value != SetupUiState.Idle) return@launch
            val currentM = (_distanceKm.value * 1000.0).coerceIn(500.0, 50_000.0)
            val frequent = routeRepository.getFrequentTargetDistancesM(top = 3)
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
                if (alreadyHave) continue

                val route = withContext(Dispatchers.Default) {
                    generateRoute(origin, targetM, prefs).getOrNull()
                } ?: continue
                if (!isActive || _uiState.value != SetupUiState.Idle) return@launch
                synchronized(warmupLock) {
                    warmupCache[key] = WarmedRoute(origin, targetM, route)
                }
            }
        }
    }

    fun generateRoute() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                cancelWarmupJobOnly()
                stopActiveRunIfAny()
                withContext(Dispatchers.Main) {
                    _uiState.value = SetupUiState.LocatingGps
                }

                val origin = runCatching { locationRepository.getCurrentLocation() }
                    .getOrElse { e ->
                        withContext(Dispatchers.Main) {
                            _uiState.value = SetupUiState.Error("GPS unavailable: ${e.message}")
                        }
                        scheduleRouteWarmup()
                        return@launch
                    }

                val userTargetM = _distanceKm.value * 1000.0
                takeWarmRouteIfReady(origin, userTargetM)?.let { route ->
                    routeRepository.saveRoute(route)
                    withContext(Dispatchers.Main) {
                        _uiState.value = SetupUiState.Success(route)
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = SetupUiState.GeneratingRoute
                }
                activeGenerationRouteId = null
                val prefs = preferencesStore.routeGenerationPreferences.first()
                val result = generateRoute(origin, userTargetM, prefs) { route ->
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
                        activeGenerationRouteId?.let { failedId ->
                            routeRepository.deleteRoute(failedId)
                        }
                        activeGenerationRouteId = null
                        withContext(Dispatchers.Main) {
                            _uiState.value = SetupUiState.Error("Route generation failed: ${e.message}")
                        }
                        scheduleRouteWarmup()
                    }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _uiState.value = SetupUiState.Error("Unexpected error: ${t.message}")
                }
                scheduleRouteWarmup()
            }
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
        private const val MAX_WARMUP_TARGETS = 3
        private const val WARMUP_DEBOUNCE_MS = 500L

        private fun canonicalTargetM(m: Double): Long {
            val clamped = m.coerceIn(500.0, 50_000.0)
            return ((clamped / WARM_TARGET_BUCKET_M).roundToInt() * WARM_TARGET_BUCKET_M.toLong())
                .coerceIn(500L, 50_000L)
        }
    }
}
