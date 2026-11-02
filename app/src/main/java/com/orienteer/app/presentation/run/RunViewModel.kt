package com.orienteer.app.presentation.run

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.data.local.ActiveRunStore
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.model.RunSession
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.service.LocationTrackingService
import com.orienteer.app.service.RunSessionStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunUiState(
    val route: Route? = null,
    val session: RunSession? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val shouldSaveRun: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RunViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeRepository: RouteRepository,
    private val activeRunStore: ActiveRunStore,
    private val userPreferencesStore: UserPreferencesStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routeId: String = checkNotNull(savedStateHandle["routeId"])

    private val _uiState = MutableStateFlow(RunUiState())
    val uiState: StateFlow<RunUiState> = _uiState.asStateFlow()

    val mapDisplayPreferences: StateFlow<MapDisplayPreferences> =
        userPreferencesStore.mapDisplayPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MapDisplayPreferences()
        )

    fun cycleBasemapStyle() {
        viewModelScope.launch {
            val styles = BasemapStyle.entries
            val current = mapDisplayPreferences.value.basemapStyle
            val next = styles[(styles.indexOf(current) + 1) % styles.size]
            userPreferencesStore.setBasemapStyle(next)
        }
    }

    private var trackingService: LocationTrackingService? = null
    private var sessionCollectJob: Job? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocationTrackingService.LocalBinder
            val svc = binder.getService()
            trackingService = svc

            svc.runSession.value?.let { snapshot ->
                _uiState.value = _uiState.value.copy(
                    session = snapshot,
                    isRunning = snapshot.isActive,
                    isPaused = snapshot.isPaused
                )
            }

            sessionCollectJob?.cancel()
            sessionCollectJob = viewModelScope.launch {
                svc.runSession.collect { session ->
                    _uiState.value = _uiState.value.copy(
                        session = session,
                        isRunning = session?.isActive == true,
                        isPaused = session?.isPaused == true,
                        isFinished = session?.isActive == false && session?.finishedAt != null
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            trackingService = null
            bound = false
        }
    }

    init {
        loadRoute()
        viewModelScope.launch {
            if (activeRunStore.readActiveRouteId() == routeId) {
                _uiState.value = _uiState.value.copy(isRunning = true)
                bindTrackingService()
            }
        }
    }

    private fun loadRoute() {
        viewModelScope.launch {
            val route = routeRepository.getRoute(routeId)
            _uiState.value = _uiState.value.copy(
                route = route,
                error = if (route == null) "Route not found" else null
            )
        }
    }

    fun bindToServiceIfRunning() {
        viewModelScope.launch {
            if (activeRunStore.readActiveRouteId() == routeId) {
                _uiState.value = _uiState.value.copy(isRunning = true)
                bindTrackingService()
            }
        }
    }

    fun markCurrentCheckpointUnreachable() {
        trackingService?.skipCurrentCheckpoint() ?: context.startService(
            Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_SKIP
            }
        )
    }

    fun startRun() {
        val route = _uiState.value.route ?: return
        _uiState.value = _uiState.value.copy(isRunning = true)
        RunSessionStarter.start(context, route.id, _uiState.value.shouldSaveRun)
        bindTrackingService()
    }

    fun pauseRun() {
        trackingService?.pauseTracking() ?: context.startService(
            Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_PAUSE
            }
        )
    }

    fun resumeRun() {
        trackingService?.resumeTracking() ?: context.startService(
            Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_RESUME
            }
        )
    }

    fun stopRun() {
        if (bound) {
            runCatching { context.unbindService(serviceConnection) }
            bound = false
        }
        sessionCollectJob?.cancel()
        sessionCollectJob = null
        trackingService = null

        context.startService(
            Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_STOP
            }
        )
        _uiState.value = _uiState.value.copy(isRunning = false, isPaused = false)
    }

    private fun bindTrackingService() {
        if (bound) return
        bound = true
        context.bindService(
            Intent(context, LocationTrackingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun setShouldSaveRun(value: Boolean) {
        if (_uiState.value.isRunning) return
        _uiState.value = _uiState.value.copy(shouldSaveRun = value)
    }

    override fun onCleared() {
        super.onCleared()
        sessionCollectJob?.cancel()
        if (bound) {
            runCatching { context.unbindService(serviceConnection) }
            bound = false
        }
    }
}
