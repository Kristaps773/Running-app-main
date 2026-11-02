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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.orienteer.app.ui.icons.OrienteerIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.orienteer.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.presentation.components.GhostTextButton
import com.orienteer.app.presentation.components.OrienteerTopBar
import com.orienteer.app.presentation.components.PrimaryGreenButton
import com.orienteer.app.presentation.components.RouteBottomSheet
import com.orienteer.app.presentation.components.RoutePlottingLoader
import com.orienteer.app.presentation.map.MapAttributionBar
import com.orienteer.app.presentation.map.OsmMapHost
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
    var runPanelExpanded by remember { mutableStateOf(true) }

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
    val paceLabel = GeoUtils.formatRunPace(distanceM, elapsedMs)
    val currentLocation = session?.trackedPoints?.lastOrNull()
    val nextCp = route?.checkpoints?.getOrNull(session?.currentCheckpointIdx ?: 0)
    // Keep air legs on during a run; honor Settings toggle for the OSRM foot path.
    val mapPrefs = mapDisplay.copy(
        showAirLegLines = true,
        showAirLegDistanceLabels = true
    )
    val context = LocalContext.current
    val exportGpxLabel = stringResource(R.string.export_run_gpx)

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
                modifier = Modifier.fillMaxSize()
            )
        } else {
            RoutePlottingLoader(
                modifier = Modifier.align(Alignment.Center),
                label = "Loading course…"
            )
        }

        MapAttributionBar(
            basemapStyle = mapDisplay.basemapStyle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = OrienteerSpacing.sm,
                    bottom = if (runPanelExpanded) 220.dp else 72.dp,
                    end = OrienteerSpacing.sm
                )
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
                trailingIcon = OrienteerIcons.Layers,
                onTrailingClick = { viewModel.cycleBasemapStyle() }
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
            Surface(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = OrienteerSpacing.md, bottom = OrienteerSpacing.xs),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
            ) {
                Text(
                    mapStyleShortLabel(mapDisplay.basemapStyle),
                    modifier = Modifier.padding(
                        horizontal = OrienteerSpacing.sm,
                        vertical = OrienteerSpacing.xs
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        RouteBottomSheet(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f),
            expanded = runPanelExpanded,
            onExpandedChange = { runPanelExpanded = it }
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
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    text = cp.description,
                                    style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun mapStyleShortLabel(style: BasemapStyle): String = when (style) {
    BasemapStyle.STANDARD -> stringResource(R.string.map_style_standard)
    BasemapStyle.DARK -> stringResource(R.string.map_style_dark)
    BasemapStyle.PSEUDO_O -> stringResource(R.string.map_style_pseudo_o)
    BasemapStyle.STREET_O -> stringResource(R.string.map_style_street_o)
    BasemapStyle.OPEN_TOPO -> stringResource(R.string.map_style_open_topo)
    BasemapStyle.CYCLOSM -> stringResource(R.string.map_style_cyclosm)
}
