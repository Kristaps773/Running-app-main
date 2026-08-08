package com.orienteer.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.data.repository.RunSummary
import com.orienteer.app.ui.components.OrienteerLabel
import com.orienteer.app.ui.components.OrienteerPrimaryButton
import com.orienteer.app.ui.components.OrienteerTopBar
import com.orienteer.app.ui.theme.mapOverlayColors
import com.orienteer.app.util.GeoUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenFullMap: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    var showDeleteDialog by remember { mutableStateOf(false) }

    val selectedId = uiState.selectedRun?.summary?.sessionId

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OrienteerTopBar(
                title = "Run History",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.runs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No runs saved yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Recent runs",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(uiState.runs, key = { it.sessionId }) { run ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RunSummaryCard(
                            run = run,
                            selected = run.sessionId == selectedId,
                            onClick = { viewModel.loadRunDetail(run.sessionId) }
                        )
                        if (run.sessionId == selectedId) {
                            uiState.selectedRun?.let { detail ->
                                val sessionId = detail.summary.sessionId
                                OrienteerPrimaryButton(
                                    text = "Open full-screen map",
                                    onClick = { onOpenFullMap(sessionId) },
                                    icon = Icons.Default.Fullscreen
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    RunReplayPreviewCanvas(
                                        planned = detail.route?.polylinePoints ?: emptyList(),
                                        actual = detail.trackedPoints,
                                        checkpoints = detail.route?.checkpoints ?: emptyList(),
                                        modifier = Modifier.fillMaxSize(),
                                        onClick = { onOpenFullMap(sessionId) }
                                    )
                                }

                                RunReplayMapLegend(modifier = Modifier.fillMaxWidth())

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showDeleteDialog = true },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete run")
                                        Spacer(Modifier.width(6.dp))
                                        Text("Delete run")
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "Smoothed segment speed (top 5)",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        detail.smoothedSegmentSpeeds
                                            .sortedByDescending { it.speedKmh }
                                            .take(5)
                                            .forEach { s ->
                                                Text(
                                                    "Points ${s.fromIndex}-${s.toIndex}: ${"%.1f".format(s.speedKmh)} km/h",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "Leg pace by checkpoint",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        detail.legPaces.forEach { leg ->
                                            val pace = leg.paceSecPerKm?.let { GeoUtils.formatPace(it) } ?: "n/a"
                                            Text(
                                                "CP ${leg.checkpointId}: ${GeoUtils.formatDistance(leg.distanceM)} - $pace",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && uiState.selectedRun != null) {
        val sessionId = uiState.selectedRun!!.summary.sessionId
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete run?") },
            text = { Text("This removes the run from history. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRun(sessionId)
                        showDeleteDialog = false
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
private fun RunSummaryCard(run: RunSummary, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer)
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Run",
                style = MaterialTheme.typography.titleLarge,
                color = content,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(run.startedAt)),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.8f)
            )
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    OrienteerLabel(text = "Distance", color = content.copy(alpha = 0.7f))
                    Text(
                        GeoUtils.formatDistance(run.totalDistanceM),
                        style = MaterialTheme.typography.headlineMedium,
                        color = content,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    OrienteerLabel(text = "Time", color = content.copy(alpha = 0.7f))
                    Text(
                        GeoUtils.formatDuration(run.elapsedTimeMs),
                        style = MaterialTheme.typography.headlineMedium,
                        color = content,
                        fontWeight = FontWeight.Bold
                    )
                }
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
        LegendSwatch(
            color = mapColors.plannedPath,
            label = "Planned"
        )
        Spacer(modifier = Modifier.width(20.dp))
        LegendSwatch(
            color = mapColors.actualPath,
            label = "Your run"
        )
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
