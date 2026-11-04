package com.orienteer.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.data.repository.RunDetail
import com.orienteer.app.data.repository.RunRepository
import com.orienteer.app.data.repository.RunSummary
import com.orienteer.app.domain.RunHistoryTiming
import com.orienteer.app.util.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LegPace(
    val checkpointId: Int,
    val paceSecPerKm: Double?,
    val durationMs: Long?,
    val distanceM: Double
)

enum class CheckpointVisitStatus {
    REACHED,
    UNREACHABLE,
    NOT_REACHED
}

data class CheckpointVisitUi(
    val checkpointId: Int,
    val label: String,
    val status: CheckpointVisitStatus
)

data class RunDetailUi(
    val summary: RunSummary,
    val route: Route?,
    val trackedPoints: List<GeoPoint>,
    val trackedPointTimestampsMs: List<Long>,
    val legPaces: List<LegPace>,
    val checkpointVisits: List<CheckpointVisitUi> = emptyList()
)

data class HistoryUiState(
    val runs: List<RunSummary> = emptyList(),
    val selectedRun: RunDetailUi? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val runRepository: RunRepository,
    private val routeRepository: RouteRepository,
    userPreferencesStore: UserPreferencesStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val mapDisplayPreferences: StateFlow<MapDisplayPreferences> =
        userPreferencesStore.mapDisplayPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            MapDisplayPreferences(
                showAirLegLines = UserPreferencesStore.DEFAULT_SHOW_AIR_LEGS,
                showAirLegDistanceLabels = UserPreferencesStore.DEFAULT_SHOW_AIR_LABELS,
                showWalkingPathPolyline = UserPreferencesStore.DEFAULT_SHOW_WALKING_PATH,
                basemapStyle = UserPreferencesStore.DEFAULT_BASEMAP_STYLE,
                useIofCourseOverprint = UserPreferencesStore.DEFAULT_USE_IOF_COURSE_OVERPRINT
            )
        )

    init {
        runRepository.observeAllRuns()
            .onEach { runs ->
                val previous = _uiState.value
                val selectedId = previous.selectedRun?.summary?.sessionId
                val selectionStillValid = selectedId != null && runs.any { it.sessionId == selectedId }
                _uiState.value = previous.copy(
                    runs = runs,
                    selectedRun = if (selectionStillValid) previous.selectedRun else null
                )
            }
            .launchIn(viewModelScope)
    }

    fun loadRunDetail(sessionId: Long) {
        viewModelScope.launch {
            try {
                val detail = runRepository.getRunDetail(sessionId) ?: return@launch
                val route = routeRepository.getRoute(detail.summary.routeId)
                _uiState.value = _uiState.value.copy(
                    selectedRun = detail.toUi(route)
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(selectedRun = null)
            }
        }
    }

    fun deleteRun(sessionId: Long) {
        viewModelScope.launch {
            runRepository.deleteRun(sessionId)
            if (_uiState.value.selectedRun?.summary?.sessionId == sessionId) {
                _uiState.value = _uiState.value.copy(selectedRun = null)
            }
        }
    }

    /** Route for a history session (Run again / New from this run). */
    suspend fun routeForSession(sessionId: Long): Route? {
        val detail = runRepository.getRunDetail(sessionId) ?: return null
        return routeRepository.getRoute(detail.summary.routeId)?.withFinishCheckpoint()
    }

    /**
     * Persist edited **leg** durations (checkpoint → checkpoint). GPS track is not modified.
     * [legDurationMsToCheckpoint] maps destination checkpoint id → leg duration ms.
     */
    fun saveLegDurations(sessionId: Long, legDurationMsToCheckpoint: Map<Int, Long>) {
        viewModelScope.launch {
            val detail = runRepository.getRunDetail(sessionId) ?: return@launch
            val route = routeRepository.getRoute(detail.summary.routeId)?.withFinishCheckpoint()
                ?: return@launch
            if (legDurationMsToCheckpoint.values.any { it < 0L }) return@launch
            val orderedIds = route.checkpoints.map { it.id }
            val reached = RunHistoryTiming.checkpointReachedAtFromLegDurations(
                startedAtMs = detail.summary.startedAt,
                orderedCheckpointIds = orderedIds,
                legDurationMsToCheckpoint = legDurationMsToCheckpoint
            )
            val finishedAt = reached[orderedIds.lastOrNull()]
            val elapsed = if (finishedAt != null) {
                (finishedAt - detail.summary.startedAt).coerceAtLeast(0L)
            } else {
                detail.summary.elapsedTimeMs
            }
            runRepository.updateCheckpointReachedAt(
                sessionId = sessionId,
                checkpointReachedAtMs = reached,
                elapsedTimeMs = elapsed,
                finishedAt = finishedAt
            )
            loadRunDetail(sessionId)
        }
    }

    private fun RunDetail.toUi(route: Route?): RunDetailUi {
        val course = route?.withFinishCheckpoint()
        val paces = computeLegPaces(
            route = course,
            reachedAtMs = checkpointReachedAtMs
        )
        return RunDetailUi(
            summary = summary,
            route = course,
            trackedPoints = trackedPoints,
            trackedPointTimestampsMs = trackedPointTimestampsMs,
            legPaces = paces,
            checkpointVisits = computeCheckpointVisits(
                route = course,
                reachedAtMs = checkpointReachedAtMs,
                skippedIds = skippedCheckpointIds
            )
        )
    }

    private fun computeLegPaces(route: Route?, reachedAtMs: Map<Int, Long>): List<LegPace> {
        if (route == null) return emptyList()
        // Every step after start (including trailing finish), not GPS breadcrumbs.
        return route.checkpoints.zipWithNext { prev, cp ->
            val startMs = reachedAtMs[prev.id]
            val endMs = reachedAtMs[cp.id]
            val durationMs = if (startMs != null && endMs != null && endMs >= startMs) endMs - startMs else null
            val pace = if (durationMs != null && cp.distanceFromPrev > 0) {
                (durationMs / 1000.0) / (cp.distanceFromPrev / 1000.0)
            } else {
                null
            }
            LegPace(
                checkpointId = cp.id,
                paceSecPerKm = pace,
                durationMs = durationMs,
                distanceM = cp.distanceFromPrev
            )
        }
    }

    private fun computeCheckpointVisits(
        route: Route?,
        reachedAtMs: Map<Int, Long>,
        skippedIds: Set<Int>
    ): List<CheckpointVisitUi> {
        if (route == null) return emptyList()
        return route.checkpoints.mapIndexed { index, cp ->
            val isFinish = cp.isStart && index == route.checkpoints.lastIndex && route.hasTrailingFinish
            val label = when {
                isFinish -> "Finish"
                cp.isStart -> "Start"
                else -> "CP ${cp.id}"
            }
            val status = when {
                cp.id in skippedIds -> CheckpointVisitStatus.UNREACHABLE
                cp.id in reachedAtMs -> CheckpointVisitStatus.REACHED
                else -> CheckpointVisitStatus.NOT_REACHED
            }
            CheckpointVisitUi(checkpointId = cp.id, label = label, status = status)
        }
    }
}

