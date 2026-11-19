package com.orienteer.app.presentation.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.R
import com.orienteer.app.ui.components.OrienteerGhostButton
import com.orienteer.app.ui.components.OrienteerPrimaryButton
import com.orienteer.app.ui.components.OrienteerTopBar
import com.orienteer.app.ui.icons.OrienteerIcons
import com.orienteer.app.ui.theme.OrienteerCardShape
import com.orienteer.app.ui.theme.OrienteerRadius
import com.orienteer.app.ui.theme.OrienteerSpacing
import com.orienteer.app.ui.theme.mapOverlayColors
import com.orienteer.app.util.GeoUtils
import com.orienteer.app.util.RoutePdfExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryReplayMapScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    onRunAgain: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val mapDisplay by viewModel.mapDisplayPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportGpxLabel = stringResource(R.string.export_run_gpx)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editTimesExpanded by remember { mutableStateOf(false) }
    var checkpointsExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(sessionId) {
        viewModel.loadRunDetail(sessionId)
    }

    val detail = uiState.selectedRun?.takeIf { it.summary.sessionId == sessionId }

    // One editable duration per course leg (checkpoint→checkpoint), not raw GPS breadcrumbs.
    var legSecondsByDestId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(sessionId, detail?.legPaces) {
        legSecondsByDestId = detail?.legPaces?.associate { leg ->
            val ms = leg.durationMs
            val text = if (ms == null) {
                ""
            } else {
                val s = ms / 1000.0
                if (s == s.toLong().toDouble()) s.toLong().toString() else "%.1f".format(s)
            }
            leg.checkpointId to text
        } ?: emptyMap()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OrienteerTopBar(
                title = "Activity",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                OrienteerIcons.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(exportGpxLabel) },
                                onClick = {
                                    menuOpen = false
                                    detail?.let { d ->
                                        RoutePdfExporter.shareRunAsGpx(
                                            context = context,
                                            route = d.route,
                                            trackedPoints = d.trackedPoints,
                                            trackedPointTimestampsMs = d.trackedPointTimestampsMs,
                                            startedAt = d.summary.startedAt,
                                            sessionLabel = "run_${d.summary.sessionId}"
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete activity",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (detail == null) {
            Text(
                text = "Loading…",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(OrienteerSpacing.md)
            )
            return@Scaffold
        }

        val summary = detail.summary
        val pace = GeoUtils.paceSecPerKm(summary.totalDistanceM, summary.elapsedTimeMs)

        // Map fixed above scroll so OSM tiles paint (MapView inside verticalScroll often blanks).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = OrienteerSpacing.md)
                    .clip(RoundedCornerShape(OrienteerRadius.md))
            ) {
                RunReplayMapFullScreen(
                    planned = detail.route?.polylinePoints ?: emptyList(),
                    actual = detail.trackedPoints,
                    checkpoints = detail.route?.checkpoints ?: emptyList(),
                    basemapStyle = mapDisplay.basemapStyle,
                    modifier = Modifier.fillMaxSize(),
                    useIofCourseOverprint = mapDisplay.useIofCourseOverprint
                )
            }
            RunReplayMapLegend(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = OrienteerSpacing.md,
                        vertical = OrienteerSpacing.xs
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OrienteerSpacing.md),
                verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.sm)
            ) {
                Text(
                    text = "Orienteering",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault())
                        .format(Date(summary.startedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    shape = OrienteerCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OrienteerSpacing.md),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DetailMetric("Distance", GeoUtils.formatDistance(summary.totalDistanceM))
                        DetailMetric("Pace", pace?.let { GeoUtils.formatPace(it) } ?: "—")
                        DetailMetric("Time", GeoUtils.formatDuration(summary.elapsedTimeMs))
                    }
                }

                CollapsibleSection(
                    title = "Checkpoints",
                    expanded = checkpointsExpanded,
                    onToggle = { checkpointsExpanded = !checkpointsExpanded }
                ) {
                    if (detail.checkpointVisits.isEmpty()) {
                        Text(
                            "No checkpoint data for this activity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        detail.checkpointVisits.forEach { visit ->
                            val statusLabel = when (visit.status) {
                                CheckpointVisitStatus.REACHED -> "Reached"
                                CheckpointVisitStatus.UNREACHABLE -> "Unreachable"
                                CheckpointVisitStatus.NOT_REACHED -> "Not reached"
                            }
                            val statusColor = when (visit.status) {
                                CheckpointVisitStatus.REACHED -> MaterialTheme.colorScheme.primary
                                CheckpointVisitStatus.UNREACHABLE -> MaterialTheme.colorScheme.error
                                CheckpointVisitStatus.NOT_REACHED ->
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    visit.label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    statusLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }

                // Simple: time between each control + total (no GPS segment breakdown).
                Card(
                    shape = OrienteerCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(OrienteerSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)
                    ) {
                        Text(
                            "Times",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val legs = detail.legPaces
                        val visits = detail.checkpointVisits
                        if (legs.isEmpty()) {
                            Text(
                                "No leg times recorded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            legs.forEachIndexed { index, leg ->
                                val fromLabel = visits.getOrNull(index)?.label ?: "Start"
                                val toLabel = visits.getOrNull(index + 1)?.label
                                    ?: "${leg.checkpointId}"
                                val timeText = leg.durationMs?.let { GeoUtils.formatDuration(it) } ?: "—"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "$fromLabel → $toLabel",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        timeText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = OrienteerSpacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Total",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    GeoUtils.formatDuration(detail.summary.elapsedTimeMs),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        TextButton(onClick = { editTimesExpanded = !editTimesExpanded }) {
                            Text(if (editTimesExpanded) "Hide edit" else "Edit times")
                        }
                        if (editTimesExpanded && legs.isNotEmpty()) {
                            Text(
                                "Seconds between controls. Path stays fixed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            legs.forEachIndexed { index, leg ->
                                val fromLabel = visits.getOrNull(index)?.label ?: "Start"
                                val toLabel = visits.getOrNull(index + 1)?.label
                                    ?: "${leg.checkpointId}"
                                val value = legSecondsByDestId[leg.checkpointId].orEmpty()
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { new ->
                                        legSecondsByDestId = legSecondsByDestId + (
                                            leg.checkpointId to
                                                new.filter { ch -> ch.isDigit() || ch == '.' }
                                            )
                                    },
                                    label = { Text("$fromLabel → $toLabel (sec)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OrienteerPrimaryButton(
                                text = "Save times",
                                onClick = {
                                    val durations = legs.mapNotNull { leg ->
                                        val sec = legSecondsByDestId[leg.checkpointId]
                                            ?.toDoubleOrNull()
                                            ?: return@mapNotNull null
                                        leg.checkpointId to
                                            (sec * 1000.0).toLong().coerceAtLeast(0L)
                                    }.toMap()
                                    if (durations.size == legs.size) {
                                        viewModel.saveLegDurations(sessionId, durations)
                                        editTimesExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }

                OrienteerPrimaryButton(
                    text = "Run again",
                    onClick = { onRunAgain(summary.routeId) }
                )
                OrienteerGhostButton(
                    text = exportGpxLabel,
                    onClick = {
                        RoutePdfExporter.shareRunAsGpx(
                            context = context,
                            route = detail.route,
                            trackedPoints = detail.trackedPoints,
                            trackedPointTimestampsMs = detail.trackedPointTimestampsMs,
                            startedAt = detail.summary.startedAt,
                            sessionLabel = "run_${detail.summary.sessionId}"
                        )
                    }
                )

                Spacer(modifier = Modifier.height(OrienteerSpacing.lg))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete activity?") },
            text = {
                Text("This permanently removes the run from history on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRun(sessionId)
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = OrienteerCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(OrienteerSpacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(vertical = OrienteerSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = OrienteerIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (expanded) 0f else -90f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(OrienteerSpacing.xs))
                content()
            }
        }
    }
}

@Composable
fun RunReplayMapLegend(modifier: Modifier = Modifier) {
    val mapColors = mapOverlayColors()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendSwatch(color = mapColors.plannedPath, label = "Planned")
        Spacer(modifier = Modifier.width(20.dp))
        LegendSwatch(color = mapColors.actualPath, label = "Your run")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

