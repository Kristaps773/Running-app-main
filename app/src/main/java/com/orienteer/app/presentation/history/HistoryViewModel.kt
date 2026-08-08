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

data class SegmentSpeedSample(
    val fromIndex: Int,
    val toIndex: Int,
    val speedKmh: Double
)

data class LegPace(
    val checkpointId: Int,
    val paceSecPerKm: Double?,
    val durationMs: Long?,
    val distanceM: Double
)

data class RunDetailUi(
    val summary: RunSummary,
    val route: Route?,
    val trackedPoints: List<GeoPoint>,
    val smoothedSegmentSpeeds: List<SegmentSpeedSample>,
    val legPaces: List<LegPace>
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
                useDarkBasemap = UserPreferencesStore.DEFAULT_DARK_BASEMAP
            )
        )

    init {
        runRepository.observeRecentRuns()
            .onEach { runs ->
                val previous = _uiState.value
                if (runs.isEmpty()) {
                    _uiState.value = previous.copy(runs = runs, selectedRun = null)
                    return@onEach
                }
                val selectedId = previous.selectedRun?.summary?.sessionId
                val selectionStillValid = selectedId != null && runs.any { it.sessionId == selectedId }
                _uiState.value = previous.copy(runs = runs)
                when {
                    selectionStillValid -> Unit
                    else -> loadRunDetail(runs.first().sessionId)
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadRunDetail(sessionId: Long) {
        viewModelScope.launch {
            val detail = runRepository.getRunDetail(sessionId) ?: return@launch
            val route = routeRepository.getRoute(detail.summary.routeId)
            _uiState.value = _uiState.value.copy(
                selectedRun = detail.toUi(route)
            )
        }
    }

    fun deleteRun(sessionId: Long) {
        viewModelScope.launch {
            runRepository.deleteRun(sessionId)
        }
    }

    private fun RunDetail.toUi(route: Route?): RunDetailUi {
        val segmentSpeeds = computeSmoothedSegmentSpeeds(trackedPoints, trackedPointTimestampsMs)
        val paces = computeLegPaces(
            route = route,
            reachedAtMs = checkpointReachedAtMs
        )
        return RunDetailUi(
            summary = summary,
            route = route,
            trackedPoints = trackedPoints,
            smoothedSegmentSpeeds = segmentSpeeds,
            legPaces = paces
        )
    }

    private fun computeSmoothedSegmentSpeeds(points: List<GeoPoint>, times: List<Long>): List<SegmentSpeedSample> {
        if (points.size < 2 || points.size != times.size) return emptyList()
        val raw = points.indices.drop(1).mapNotNull { i ->
            val dtMs = times[i] - times[i - 1]
            if (dtMs <= 0) return@mapNotNull null
            val dM = GeoUtils.distanceMeters(points[i - 1], points[i])
            val kmh = (dM / 1000.0) / (dtMs / 3_600_000.0)
            SegmentSpeedSample(fromIndex = i - 1, toIndex = i, speedKmh = kmh)
        }
        return raw.mapIndexed { index, s ->
            val from = (index - 1).coerceAtLeast(0)
            val to = (index + 1).coerceAtMost(raw.lastIndex)
            val avg = raw.subList(from, to + 1).map { it.speedKmh }.average()
            s.copy(speedKmh = avg)
        }
    }

    private fun computeLegPaces(route: Route?, reachedAtMs: Map<Int, Long>): List<LegPace> {
        if (route == null) return emptyList()
        val checkpoints = route.checkpoints.filter { !it.isStart }
        return checkpoints.map { cp ->
            val prevId = cp.id - 1
            val startMs = reachedAtMs[prevId]
            val endMs = reachedAtMs[cp.id]
            val durationMs = if (startMs != null && endMs != null && endMs > startMs) endMs - startMs else null
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
}
