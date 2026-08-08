package com.orienteer.app.presentation.run

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.presentation.components.GhostTextButton
import com.orienteer.app.presentation.components.OrienteerTopBar
import com.orienteer.app.presentation.components.PrimaryGreenButton
import com.orienteer.app.presentation.components.RouteBottomSheet
import com.orienteer.app.presentation.map.OsmMapHost
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
    var showStopConfirm by remember { mutableStateOf(false) }

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
    LaunchedEffect(uiState.isRunning, session?.startedAt) {
        if (!uiState.isRunning || session?.startedAt == null) return@LaunchedEffect
        while (isActive) {
            tickMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedMs = if (uiState.isRunning && session != null) {
        (tickMs - session.startedAt).coerceAtLeast(0L)
    } else {
        session?.elapsedTimeMs ?: 0L
    }
    val distanceM = session?.totalDistanceM ?: 0.0
    val paceLabel = GeoUtils.formatRunPace(distanceM, elapsedMs)
    val currentLocation = session?.trackedPoints?.lastOrNull()
    val nextCp = route?.checkpoints?.getOrNull(session?.currentCheckpointIdx ?: 0)
    val mapPrefs = MapDisplayPreferences(
        showAirLegLines = true,
        showAirLegDistanceLabels = true,
        showWalkingPathPolyline = true,
        useDarkBasemap = false
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (route != null) {
            OsmMapHost(
                route = route,
                currentLocation = currentLocation,
                mapPrefs = mapPrefs,
                interactive = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            OrienteerTopBar(
                title = "Active Run",
                showBack = true,
                onBack = onNavigateBack
            )
            CompactRunMetricsBar(
                distance = GeoUtils.formatDistance(distanceM),
                duration = GeoUtils.formatDuration(elapsedMs),
                pace = paceLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        RouteBottomSheet(modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (nextCp != null) {
                    Text(
                        text = when {
                            nextCp.isStart &&
                                (session?.currentCheckpointIdx ?: 0) >= (route?.checkpoints?.lastIndex ?: 0) ->
                                "Finish"
                            nextCp.isStart -> "Start"
                            else -> "Checkpoint ${nextCp.id}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isNext -> MaterialTheme.colorScheme.primaryContainer
                                isDone -> MaterialTheme.colorScheme.surfaceContainerHighest
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
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
                        text = "Start Run",
                        onClick = { viewModel.startRun() },
                        leadingIcon = Icons.Default.Map
                    )
                } else {
                    PrimaryGreenButton(
                        text = "Stop Run",
                        onClick = { showStopConfirm = true },
                        leadingIcon = Icons.Default.Stop
                    )
                    GhostTextButton(
                        text = "Can't reach it? Mark as unreachable",
                        onClick = { viewModel.markCurrentCheckpointUnreachable() },
                        modifier = Modifier.fillMaxWidth()
                    )
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
