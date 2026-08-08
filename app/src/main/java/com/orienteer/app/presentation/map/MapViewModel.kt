package com.orienteer.app.presentation.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.repository.LocationRepository
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.domain.usecase.VerifyCheckpointUseCase
import com.orienteer.app.util.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val route: Route? = null,
    val currentLocation: GeoPoint? = null,
    val distanceToNext: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository,
    private val verifyCheckpoint: VerifyCheckpointUseCase,
    preferencesStore: UserPreferencesStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routeId: String = checkNotNull(savedStateHandle["routeId"])

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    val mapDisplayPreferences: StateFlow<MapDisplayPreferences> =
        preferencesStore.mapDisplayPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MapDisplayPreferences()
        )

    init {
        observeRoute()
        trackLocation()
    }

    private fun observeRoute() {
        viewModelScope.launch {
            routeRepository.observeRoute(routeId).collect { route ->
                _uiState.value = _uiState.value.copy(
                    route = route,
                    isLoading = false,
                    error = if (route == null) "Route not found" else null
                )
            }
        }
    }

    private fun trackLocation() {
        viewModelScope.launch {
            locationRepository.locationUpdates(intervalMs = PREVIEW_LOCATION_INTERVAL_MS)
                .distinctUntilChanged { prev, next ->
                    GeoUtils.distanceMeters(prev, next) < PREVIEW_LOCATION_MIN_MOVE_M
                }
                .collect { location ->
                    val state = _uiState.value
                    val route = state.route ?: return@collect

                    val nextCheckpoint = route.checkpoints.firstOrNull { !it.isStart && !it.isReached }
                    val distanceToNext = nextCheckpoint?.let {
                        verifyCheckpoint.distanceTo(location, it)
                    } ?: 0.0

                    _uiState.value = state.copy(
                        currentLocation = location,
                        distanceToNext = distanceToNext
                    )
                }
        }
    }

    fun getNextCheckpoint(): Checkpoint? =
        _uiState.value.route?.checkpoints?.firstOrNull { !it.isStart && !it.isReached }

    private companion object {
        /** Preview map: slow GPS updates — only need a rough "you are here" dot. */
        private const val PREVIEW_LOCATION_INTERVAL_MS = 8_000L
        private const val PREVIEW_LOCATION_MIN_MOVE_M = 12.0
    }
}
