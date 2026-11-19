package com.orienteer.app.presentation.run

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.orienteer.app.ui.icons.OrienteerIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.orienteer.app.BuildConfig
import com.orienteer.app.R
import com.orienteer.app.debug.ScreenshotDemoMode
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.presentation.components.GhostTextButton
import com.orienteer.app.presentation.components.OrienteerTopBar
import com.orienteer.app.presentation.components.PrimaryGreenButton
import com.orienteer.app.presentation.components.RouteBottomSheet
import com.orienteer.app.presentation.components.RoutePlottingLoader
import com.orienteer.app.domain.RunMapCameraMode
import com.orienteer.app.domain.RunPathLeadMode
import com.orienteer.app.domain.RunRollingPace
import com.orienteer.app.presentation.map.BasemapStyleMenuButton
import com.orienteer.app.presentation.map.MapAttributionBar
import com.orienteer.app.presentation.map.MapChromeEndReserve
import com.orienteer.app.presentation.map.MapFindMeClicks
import com.orienteer.app.presentation.map.MapZoomControls
import com.orienteer.app.presentation.map.OsmMapHost
import com.orienteer.app.presentation.map.PathLeadDrawState
import com.orienteer.app.presentation.map.centerOnVisibleArea
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import com.orienteer.app.ui.components.OrienteerGhostButton
import com.orienteer.app.util.RoutePdfExporter
import com.orienteer.app.ui.theme.OrienteerCardShape
import com.orienteer.app.ui.theme.OrienteerRadius
import com.orienteer.app.ui.theme.OrienteerSpacing
import com.orienteer.app.util.GeoUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RunScreen(
    routeId: String,
    onRunComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    onRunStopped: () -> Unit = onNavigateBack,
    viewModel: RunViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapDisplay by viewModel.mapDisplayPreferences.collectAsStateWithLifecycle()
    var showStopConfirm by remember { mutableStateOf(false) }
    var runPanelExpanded by remember {
        mutableStateOf(!(BuildConfig.DEBUG && ScreenshotDemoMode.collapseSheetsForCapture))
    }
    var runPanelVisibleHeightPx by remember { mutableFloatStateOf(0f) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val findMeActions = remember {
        object {
            var recenter: () -> Unit = {}
            var resetNorth: () -> Unit = {}
            val clicks = MapFindMeClicks(
                onFindMe = { recenter() },
                onResetNorth = { resetNorth() }
            )
        }
    }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onRunComplete()
    }

    LaunchedEffect(Unit) {
        viewModel.bindToServiceIfRunning()
    }

    BackHandler(enabled = uiState.isRunning) {
        onNavigateBack()
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("Stop run?") },
            text = {
                Text(
                    if (uiState.shouldSaveRun) {
                        "Your progress will be saved to history."
                    } else {
                        "This run will not be saved to history."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    viewModel.stopRun()
                    onRunStopped()
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val session = uiState.session
    val route = uiState.route
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.isRunning, uiState.isPaused, session?.startedAt, session?.totalPausedMs) {
        if (!uiState.isRunning || uiState.isPaused || session?.startedAt == null) return@LaunchedEffect
        while (isActive) {
            tickMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedMs = when {
        session == null -> 0L
        uiState.isRunning && !uiState.isPaused -> session.activeElapsedMs(tickMs)
        else -> session.elapsedTimeMs
    }
    val distanceM = session?.totalDistanceM ?: 0.0
    var paceLabel by remember { mutableStateOf("--:-- /km") }
    LaunchedEffect(uiState.isRunning, uiState.isPaused, session?.trackedPoints?.size) {
        val activeSession = session ?: return@LaunchedEffect
        if (!uiState.isRunning) {
            paceLabel = "--:-- /km"
            return@LaunchedEffect
        }
        while (isActive) {
            val nowMs = System.currentTimeMillis()
            val totalElapsed = if (!uiState.isPaused) {
                activeSession.activeElapsedMs(nowMs)
            } else {
                activeSession.elapsedTimeMs
            }
            paceLabel = RunRollingPace.formatDisplayPace(
                points = activeSession.trackedPoints,
                timestampsMs = activeSession.trackedPointTimestampsMs,
                nowMs = nowMs,
                totalDistanceM = activeSession.totalDistanceM,
                totalElapsedMs = totalElapsed
            )
            delay(RunRollingPace.REFRESH_MS)
        }
    }
    val currentLocation = session?.trackedPoints?.lastOrNull()
    val nextCp = route?.checkpoints?.getOrNull(session?.currentCheckpointIdx ?: 0)
    val leadEnabled = RunPathLeadMode.isEnabled(
        showWalkingPathPolyline = mapDisplay.showWalkingPathPolyline,
        showYouAreHereDuringRun = mapDisplay.showYouAreHereDuringRun
    )
    var cameraMode by remember { mutableStateOf(RunMapCameraMode.initial()) }
    // Keep air legs on during a run; honor Settings toggle for the OSRM foot path.
    val mapPrefs = mapDisplay.copy(
        showAirLegLines = true,
        showAirLegDistanceLabels = true
    )
    val guidancePolyline = uiState.liveGuidancePolyline.ifEmpty {
        route?.polylinePoints.orEmpty()
    }
    // Previous + remaining CPs only so soft-reroute polylines still order correctly.
    val cps = route?.checkpoints.orEmpty()
    val nextIdx = (session?.currentCheckpointIdx ?: 0).coerceIn(0, (cps.size - 1).coerceAtLeast(0))
    val leadCheckpoints = remember(route?.id, nextIdx, cps.size) {
        buildList {
            if (nextIdx > 0) add(cps[nextIdx - 1].position)
            for (i in nextIdx until cps.size) add(cps[i].position)
        }
    }
    val leadNextIdx = if (nextIdx > 0) 1 else 0
    val pathLead = if (leadEnabled && guidancePolyline.size >= 2) {
        PathLeadDrawState(
            guidancePolyline = guidancePolyline,
            progressLocation = currentLocation,
            checkpointPositions = leadCheckpoints,
            nextCheckpointIdx = leadNextIdx
        )
    } else {
        null
    }
    val context = LocalContext.current
    val exportGpxLabel = stringResource(R.string.export_run_gpx)
    val recenterLabel = stringResource(R.string.run_recenter_map)

    val nextCpTitle = nextCp?.let { cp ->
        when {
            cp.isStart &&
                (session?.currentCheckpointIdx ?: 0) >= (route?.checkpoints?.lastIndex ?: 0) ->
                "Finish"
            cp.isStart -> "Start"
            else -> "Checkpoint ${cp.id}"
        }
    }

    // Full-bleed map with Compose overlays (MapScreen pattern) so OSMDroid cannot paint over HUD.
    Box(modifier = Modifier.fillMaxSize()) {
        if (route != null) {
            OsmMapHost(
                route = route,
                currentLocation = currentLocation.takeIf { mapDisplay.showYouAreHereDuringRun },
                mapPrefs = mapPrefs,
                basemapStyle = mapDisplay.basemapStyle,
                interactive = true,
                pathLead = pathLead,
                followCamera = RunMapCameraMode.shouldAutoFollow(cameraMode) &&
                    currentLocation != null,
                followLocation = currentLocation,
                mapBearingDeg = uiState.movementBearingDeg,
                followBottomInsetPx = runPanelVisibleHeightPx,
                onUserMapGesture = {
                    cameraMode = RunMapCameraMode.onUserGesture(cameraMode)
                },
                onMapReady = { mapViewRef = it },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            RoutePlottingLoader(
                modifier = Modifier.align(Alignment.Center),
                label = "Loading course…"
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = OrienteerSpacing.md, bottom = OrienteerSpacing.sm)
                .graphicsLayer { translationY = -runPanelVisibleHeightPx }
                .zIndex(2f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)
        ) {
            MapZoomControls(
                onZoomIn = { mapViewRef?.controller?.zoomIn() },
                onZoomOut = { mapViewRef?.controller?.zoomOut() }
            )
            if (currentLocation != null) {
                findMeActions.recenter = {
                    mapViewRef?.let { map ->
                        map.centerOnVisibleArea(
                            OsmGeoPoint(
                                currentLocation.latitude,
                                currentLocation.longitude
                            ),
                            runPanelVisibleHeightPx
                        )
                        // Seed so the next compose pass does not re-animate to the same fix.
                        map.setTag(
                            R.id.map_tag_follow_target,
                            "${currentLocation.latitude},${currentLocation.longitude}"
                        )
                    }
                    cameraMode = RunMapCameraMode.onRecenter(cameraMode)
                }
                findMeActions.resetNorth = {
                    mapViewRef?.mapOrientation = 0f
                    mapViewRef?.invalidate()
                }
                FloatingActionButton(
                    onClick = { findMeActions.clicks.onClick() },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(OrienteerIcons.MyLocation, contentDescription = recenterLabel)
                }
            }
        }

        MapAttributionBar(
            basemapStyle = mapDisplay.basemapStyle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(
                    start = OrienteerSpacing.sm,
                    bottom = OrienteerSpacing.sm,
                    end = MapChromeEndReserve
                )
                .graphicsLayer { translationY = -runPanelVisibleHeightPx }
                .zIndex(1f)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .zIndex(2f)
        ) {
            OrienteerTopBar(
                title = if (uiState.isPaused) {
                    stringResource(R.string.run_paused)
                } else {
                    stringResource(R.string.active_run)
                },
                showBack = true,
                onBack = onNavigateBack,
                trailingContent = {
                    BasemapStyleMenuButton(
                        selected = mapDisplay.basemapStyle,
                        onSelect = viewModel::setBasemapStyle
                    )
                }
            )
            CompactRunMetricsBar(
                distance = GeoUtils.formatDistance(distanceM),
                duration = GeoUtils.formatDuration(elapsedMs),
                pace = paceLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = OrienteerSpacing.sm,
                        vertical = OrienteerSpacing.xs
                    )
            )
        }

        RouteBottomSheet(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f),
            expanded = runPanelExpanded,
            onExpandedChange = { runPanelExpanded = it },
            onVisibleHeightPxChanged = { runPanelVisibleHeightPx = it }
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)
            ) {
                if (nextCp != null) {
                    Text(
                        text = nextCpTitle.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = nextCp.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (route != null) {
                    Text(
                        text = "CHECKPOINTS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    route.checkpoints.forEachIndexed { index, cp ->
                        if (cp.isStart) return@forEachIndexed
                        val isNext = index == (session?.currentCheckpointIdx ?: -1)
                        val isDone = index < (session?.currentCheckpointIdx ?: 0)
                        Surface(
                            shape = RoundedCornerShape(OrienteerRadius.md),
                            color = when {
                                isNext -> MaterialTheme.colorScheme.primaryContainer
                                isDone -> MaterialTheme.colorScheme.surfaceContainerHighest
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(OrienteerSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${cp.id}.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNext) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    text = cp.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isNext) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (!uiState.isRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Save run to history",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Turn off for a temporary run.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.shouldSaveRun,
                            onCheckedChange = viewModel::setShouldSaveRun
                        )
                    }
                }

                if (!uiState.isRunning) {
                    PrimaryGreenButton(
                        text = stringResource(R.string.start_run),
                        onClick = { viewModel.startRun() },
                        leadingIcon = OrienteerIcons.Map
                    )
                } else {
                    if (uiState.isPaused) {
                        PrimaryGreenButton(
                            text = stringResource(R.string.resume_run),
                            onClick = { viewModel.resumeRun() },
                            leadingIcon = OrienteerIcons.PlayArrow
                        )
                    } else {
                        PrimaryGreenButton(
                            text = stringResource(R.string.pause_run),
                            onClick = { viewModel.pauseRun() },
                            leadingIcon = OrienteerIcons.Pause
                        )
                    }
                    OrienteerGhostButton(
                        text = stringResource(R.string.stop_run),
                        onClick = { showStopConfirm = true }
                    )
                    if (session != null && session.trackedPoints.isNotEmpty()) {
                        OrienteerGhostButton(
                            text = exportGpxLabel,
                            onClick = {
                                RoutePdfExporter.shareRunAsGpx(
                                    context = context,
                                    route = route,
                                    trackedPoints = session.trackedPoints,
                                    trackedPointTimestampsMs = session.trackedPointTimestampsMs,
                                    startedAt = session.startedAt,
                                    sessionLabel = "run_active_${(route?.id ?: "active").take(8)}"
                                )
                            }
                        )
                    }
                    val targetingFinish = nextCp?.isStart == true &&
                        (session?.currentCheckpointIdx ?: 0) >= (route?.checkpoints?.lastIndex ?: 0)
                    if (!uiState.isPaused && !targetingFinish) {
                        GhostTextButton(
                            text = stringResource(R.string.mark_unreachable),
                            onClick = { viewModel.markCurrentCheckpointUnreachable() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactRunMetricsBar(
    distance: String,
    duration: String,
    pace: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = OrienteerCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrienteerSpacing.sm, vertical = OrienteerSpacing.xs + OrienteerSpacing.xxs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactMetric("Dist", distance)
            CompactMetric("Time", duration)
            CompactMetric("Pace", pace)
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

