package com.orienteer.app.presentation.run

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.BuildConfig
import com.orienteer.app.data.local.ActiveRunStore
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.model.RunSession
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.debug.AgentDebugLog
import com.orienteer.app.debug.ScreenshotDemoMode
import com.orienteer.app.debug.ScreenshotDemoSeeder
import com.orienteer.app.domain.RunPathLeadMode
import com.orienteer.app.domain.RoutePolylineProgress
import com.orienteer.app.domain.usecase.LiveFootpathGuidanceUseCase
import com.orienteer.app.service.LocationTrackingService
import com.orienteer.app.service.RunSessionStarter
import com.orienteer.app.util.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import javax.inject.Inject

data class RunUiState(
    val route: Route? = null,
    val session: RunSession? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val shouldSaveRun: Boolean = true,
    val error: String? = null,
    /** Session-only live OSRM guidance (may soft-reroute); not written to PDF route. */
    val liveGuidancePolyline: List<GeoPoint> = emptyList(),
    val movementBearingDeg: Float = 0f
)

@HiltViewModel
class RunViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeRepository: RouteRepository,
    private val activeRunStore: ActiveRunStore,
    private val userPreferencesStore: UserPreferencesStore,
    private val liveFootpathGuidance: LiveFootpathGuidanceUseCase,
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

    private var lastRerouteAtMs: Long = 0L
    private var lastBearingLocation: GeoPoint? = null
    private var rerouteJob: Job? = null
    private var lastLeadAlongM: Double? = null
    private var lastLeadSeg: Int? = null
    private var lastLeadLogAtMs: Long = 0L

    companion object {
        private const val REROUTE_COOLDOWN_MS = 35_000L
        private const val OFF_PATH_TO_CHECK_M = 55.0
        private const val MIN_BEARING_MOVE_M = 8.0
        private const val LEAD_DEBUG_MIN_MS = 800L
    }

    fun setBasemapStyle(style: BasemapStyle) {
        viewModelScope.launch { userPreferencesStore.setBasemapStyle(style) }
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
                    val prev = _uiState.value
                    val bearing = session?.trackedPoints?.lastOrNull()?.let { loc ->
                        updateMovementBearing(loc) ?: prev.movementBearingDeg
                    } ?: prev.movementBearingDeg
                    _uiState.value = prev.copy(
                        session = session,
                        isRunning = session?.isActive == true,
                        isPaused = session?.isPaused == true,
                        isFinished = session?.isActive == false && session?.finishedAt != null,
                        movementBearingDeg = bearing
                    )
                    // #region agent log
                    logPathLeadDebug(session, bearing)
                    // #endregion
                    maybeSoftReroute(session)
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
            if (BuildConfig.DEBUG && ScreenshotDemoMode.useFakeActiveRun &&
                routeId == ScreenshotDemoMode.ROUTE_ID
            ) {
                // Fake mid-run UI for store screenshots — do not start FGS.
                return@launch
            }
            if (activeRunStore.readActiveRouteId() == routeId) {
                _uiState.value = _uiState.value.copy(isRunning = true)
                bindTrackingService()
            }
        }
    }

    private fun loadRoute() {
        viewModelScope.launch {
            val route = routeRepository.getRoute(routeId)
            if (BuildConfig.DEBUG && ScreenshotDemoMode.useFakeActiveRun &&
                route != null && routeId == ScreenshotDemoMode.ROUTE_ID
            ) {
                val fake = ScreenshotDemoSeeder.fakeMidRunSession(route)
                _uiState.value = _uiState.value.copy(
                    route = route,
                    session = fake,
                    isRunning = true,
                    isPaused = false,
                    isFinished = false,
                    liveGuidancePolyline = route.polylinePoints,
                    error = null
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                route = route,
                liveGuidancePolyline = route?.polylinePoints.orEmpty(),
                error = if (route == null) "Route not found" else null
            )
        }
    }

    private fun updateMovementBearing(location: GeoPoint): Float? {
        val prev = lastBearingLocation
        lastBearingLocation = location
        if (prev == null) return null
        if (GeoUtils.distanceMeters(prev, location) < MIN_BEARING_MOVE_M) return null
        val next = GeoUtils.bearingDegrees(prev, location).toFloat()
        // #region agent log
        val delta = abs((((next - (_uiState.value.movementBearingDeg)) + 540f) % 360f) - 180f)
        if (delta >= 60f) {
            AgentDebugLog.log(
                hypothesisId = "C",
                location = "RunViewModel.updateMovementBearing",
                message = "bearing jump",
                data = mapOf(
                    "prevBearing" to _uiState.value.movementBearingDeg,
                    "nextBearing" to next,
                    "deltaDeg" to delta,
                    "moveM" to GeoUtils.distanceMeters(prev, location)
                )
            )
        }
        // #endregion
        return next
    }

    // #region agent log
    private fun logPathLeadDebug(session: RunSession?, movementBearing: Float) {
        if (session == null || !session.isActive || session.isPaused) return
        val prefs = mapDisplayPreferences.value
        if (!RunPathLeadMode.isEnabled(prefs.showWalkingPathPolyline, prefs.showYouAreHereDuringRun)) {
            return
        }
        val route = _uiState.value.route ?: return
        val location = session.trackedPoints.lastOrNull() ?: return
        val guidance = _uiState.value.liveGuidancePolyline.ifEmpty { route.polylinePoints }
        if (guidance.size < 2) return

        val now = System.currentTimeMillis()
        val cps = route.checkpoints
        val cpIdx = session.currentCheckpointIdx.coerceIn(0, (cps.size - 1).coerceAtLeast(0))
        val cpPositions = buildList {
            if (cpIdx > 0) add(cps[cpIdx - 1].position)
            for (i in cpIdx until cps.size) add(cps[i].position)
        }
        val leadNextIdx = if (cpIdx > 0) 1 else 0
        val ordered = RoutePolylineProgress.projectTowardNextCheckpoint(
            guidance,
            location,
            cpPositions,
            leadNextIdx
        )
        val nearest = RoutePolylineProgress.projectWithAmbiguity(guidance, location)
        val p = ordered
        val prevAlong = lastLeadAlongM
        val prevSeg = lastLeadSeg
        val alongDelta = if (prevAlong != null) p.distanceAlongM - prevAlong else 0.0
        val alongJump = prevAlong != null && abs(alongDelta) >= 40.0
        val nearestDisagrees =
            abs(nearest.best.distanceAlongM - p.distanceAlongM) >= 40.0
        val alongs = RoutePolylineProgress.orderedWaypointAlongMeters(guidance, cpPositions)
        val nextAlong = alongs.getOrNull(leadNextIdx.coerceIn(0, alongs.lastIndex)) ?: -1.0
        val aheadPt = RoutePolylineProgress.pointAlongMeters(
            guidance,
            (p.distanceAlongM + 25.0).coerceAtMost(
                if (nextAlong >= 0) nextAlong else p.distanceAlongM + p.remainingDistanceM
            )
        )
        val pathBearing = GeoUtils.bearingDegrees(p.snapPoint, aheadPt)
        val travelDelta = abs((((pathBearing - movementBearing) + 540.0) % 360.0) - 180.0)
        val wrongWayVsTravel = travelDelta >= 120.0 && movementBearing != 0f

        val shouldLog =
            alongJump || nearestDisagrees || wrongWayVsTravel ||
                (now - lastLeadLogAtMs >= LEAD_DEBUG_MIN_MS)
        if (!shouldLog) return
        lastLeadLogAtMs = now
        lastLeadAlongM = p.distanceAlongM
        lastLeadSeg = p.segmentIndex

        AgentDebugLog.log(
            hypothesisId = when {
                nearestDisagrees -> "A"
                alongJump -> "A"
                wrongWayVsTravel -> "E"
                else -> "A"
            },
            location = "RunViewModel.logPathLeadDebug",
            message = when {
                nearestDisagrees -> "ordered vs nearest disagree"
                alongJump -> "snap along jump"
                wrongWayVsTravel -> "lead vs travel mismatch"
                else -> "ordered lead sample"
            },
            data = mapOf(
                "seg" to p.segmentIndex,
                "prevSeg" to (prevSeg ?: -1),
                "alongM" to p.distanceAlongM,
                "alongDeltaM" to alongDelta,
                "toPathM" to p.distanceToPathM,
                "remainM" to p.remainingDistanceM,
                "nearestAlongM" to nearest.best.distanceAlongM,
                "nearestToPathM" to nearest.best.distanceToPathM,
                "nextCpAlongM" to nextAlong,
                "pathBearing" to pathBearing,
                "travelBearing" to movementBearing,
                "pathVsTravelDeg" to travelDelta,
                "guidancePts" to guidance.size,
                "cpIdx" to cpIdx,
                "alongJump" to alongJump,
                "nearestDisagrees" to nearestDisagrees,
                "wrongWayVsTravel" to wrongWayVsTravel,
                "leadToNextCp" to true,
                "runIdTag" to "post-fix"
            ),
            runId = "post-fix"
        )
    }
    // #endregion

    private fun maybeSoftReroute(session: RunSession?) {
        if (session == null || !session.isActive || session.isPaused) return
        val prefs = mapDisplayPreferences.value
        if (!RunPathLeadMode.isEnabled(prefs.showWalkingPathPolyline, prefs.showYouAreHereDuringRun)) {
            return
        }
        val route = _uiState.value.route ?: return
        val location = session.trackedPoints.lastOrNull() ?: return
        val guidance = _uiState.value.liveGuidancePolyline.ifEmpty { route.polylinePoints }
        if (guidance.size < 2) return

        val cps = route.checkpoints
        val cpIdx = session.currentCheckpointIdx.coerceIn(0, (cps.size - 1).coerceAtLeast(0))
        val cpPositions = buildList {
            if (cpIdx > 0) add(cps[cpIdx - 1].position)
            for (i in cpIdx until cps.size) add(cps[i].position)
        }
        val leadNextIdx = if (cpIdx > 0) 1 else 0
        val progress = RoutePolylineProgress.projectTowardNextCheckpoint(
            guidance,
            location,
            cpPositions,
            leadNextIdx
        )
        if (progress.distanceToPathM < OFF_PATH_TO_CHECK_M) {
            // #region agent log
            // on-path: no reroute attempt
            // #endregion
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastRerouteAtMs < REROUTE_COOLDOWN_MS) return
        if (rerouteJob?.isActive == true) return

        val remaining = route.checkpoints.drop(session.currentCheckpointIdx)
        if (remaining.isEmpty()) return

        lastRerouteAtMs = now
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "B",
            location = "RunViewModel.maybeSoftReroute",
            message = "reroute attempt",
            data = mapOf(
                "toPathM" to progress.distanceToPathM,
                "alongM" to progress.distanceAlongM,
                "remainM" to progress.remainingDistanceM,
                "guidancePts" to guidance.size,
                "remainingCp" to remaining.size
            )
        )
        // #endregion
        rerouteJob = viewModelScope.launch {
            val result = liveFootpathGuidance.maybeReroute(
                currentLocation = location,
                remainingCheckpoints = remaining,
                currentGuidance = guidance
            ) ?: return@launch
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "B",
                location = "RunViewModel.maybeSoftReroute.result",
                message = if (result.switched) "reroute switched" else "reroute kept",
                data = mapOf(
                    "switched" to result.switched,
                    "newDistM" to result.distanceM,
                    "newPts" to result.polyline.size,
                    "oldRemainM" to progress.remainingDistanceM
                )
            )
            // #endregion
            if (result.switched) {
                _uiState.value = _uiState.value.copy(liveGuidancePolyline = result.polyline)
                lastLeadAlongM = null
                lastLeadSeg = null
            }
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
