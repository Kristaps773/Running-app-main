package com.orienteer.app.presentation.history

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.data.repository.RunSummary
import com.orienteer.app.ui.components.OrienteerTopBar
import com.orienteer.app.ui.icons.OrienteerIcons
import com.orienteer.app.ui.theme.OrienteerCardShape
import com.orienteer.app.ui.theme.OrienteerSpacing
import com.orienteer.app.util.GeoUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenRunDetail: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val dayGroups = remember(uiState.runs) { groupRunsByDay(uiState.runs) }
    // Day key → expanded. Default expanded so first open shows activities; user can collapse.
    val expandedDays = remember { mutableStateMapOf<String, Boolean>() }

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
                    if (dayGroups.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val anyExpanded = dayGroups.any { (dayKey, _) ->
                                    expandedDays[dayKey] != false
                                }
                                dayGroups.forEach { (dayKey, _) ->
                                    expandedDays[dayKey] = !anyExpanded
                                }
                            }
                        ) {
                            Text(
                                text = if (dayGroups.any { (dayKey, _) ->
                                        expandedDays[dayKey] != false
                                    }
                                ) {
                                    "Collapse all"
                                } else {
                                    "Expand all"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
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
                    "No activities yet.\nFinish a run with “Save to history” on.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = OrienteerSpacing.md),
                verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = OrienteerSpacing.xs,
                    bottom = OrienteerSpacing.lg
                )
            ) {
                dayGroups.forEach { (dayKey, runs) ->
                    val expanded = expandedDays[dayKey] != false
                    item(key = "header-$dayKey") {
                        DaySectionHeader(
                            title = dayKey,
                            runCount = runs.size,
                            expanded = expanded,
                            onToggle = { expandedDays[dayKey] = !expanded }
                        )
                    }
                    if (expanded) {
                        items(runs, key = { it.sessionId }) { run ->
                            ActivityRowCard(
                                run = run,
                                onClick = { onOpenRunDetail(run.sessionId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySectionHeader(
    title: String,
    runCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = OrienteerSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (runCount == 1) "1 activity" else "$runCount activities",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = OrienteerIcons.ExpandMore,
            contentDescription = if (expanded) "Collapse day" else "Expand day",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .rotate(if (expanded) 0f else -90f)
        )
    }
}

@Composable
private fun ActivityRowCard(run: RunSummary, onClick: () -> Unit) {
    val border = BorderStroke(
        width = 1.5.dp,
        color = MaterialTheme.colorScheme.outline
    )
    val paceSecPerKm = GeoUtils.paceSecPerKm(run.totalDistanceM, run.elapsedTimeMs)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = OrienteerCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrienteerSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Orienteering",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(run.startedAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(OrienteerSpacing.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.md),
                    verticalAlignment = Alignment.Bottom
                ) {
                    MetricChip(
                        label = "Distance",
                        value = GeoUtils.formatDistance(run.totalDistanceM)
                    )
                    MetricChip(
                        label = "Pace",
                        value = paceSecPerKm?.let { GeoUtils.formatPace(it) } ?: "—"
                    )
                    MetricChip(
                        label = "Time",
                        value = GeoUtils.formatDuration(run.elapsedTimeMs)
                    )
                }
            }
            Icon(
                imageVector = OrienteerIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun groupRunsByDay(runs: List<RunSummary>): List<Pair<String, List<RunSummary>>> {
    val dayFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    val cal = Calendar.getInstance()
    val todayStart = dayStartMillis(cal)
    cal.timeInMillis = todayStart
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStart = cal.timeInMillis

    val grouped = linkedMapOf<String, MutableList<RunSummary>>()
    for (run in runs) {
        val dayStart = dayStartMillis(Calendar.getInstance().apply { timeInMillis = run.startedAt })
        val label = when (dayStart) {
            todayStart -> "Today"
            yesterdayStart -> "Yesterday"
            else -> dayFmt.format(Date(run.startedAt))
        }
        grouped.getOrPut(label) { mutableListOf() }.add(run)
    }
    return grouped.map { it.key to it.value }
}

private fun dayStartMillis(cal: Calendar): Long {
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

