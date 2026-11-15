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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.BuildConfig
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.repository.RunSummary
import com.orienteer.app.debug.ScreenshotDemoMode
import com.orienteer.app.domain.CoursePointsCatalog
import com.orienteer.app.domain.CoursePointsSet
import com.orienteer.app.ui.components.OrienteerTopBar
import com.orienteer.app.ui.icons.OrienteerIcons
import com.orienteer.app.ui.theme.OrienteerCardShape
import com.orienteer.app.ui.theme.OrienteerSpacing
import com.orienteer.app.util.GeoUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenRunDetail: (Long) -> Unit,
    onRunAgain: (String) -> Unit,
    onApplyReuse: (Route, withPins: Boolean) -> Unit,
    onEditCheckpointsFromRoute: (Route) -> Unit,
    onGoToSetup: () -> Unit,
    onSaveRouteToFavorites: suspend (Route) -> List<CoursePointsSet>?,
    onFavoriteSetFromRoute: (Route) -> CoursePointsSet?,
    onReplaceFavorite: (removeId: String, set: CoursePointsSet) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val dayGroups = remember(uiState.runs) { groupRunsByDay(uiState.runs) }
    val expandedDays = remember { mutableStateMapOf<String, Boolean>() }
    // Store screenshots: keep only the newest day expanded so more day headers show.
    LaunchedEffect(dayGroups) {
        if (!BuildConfig.DEBUG ||
            !ScreenshotDemoMode.scene.equals("history", ignoreCase = true)
        ) {
            return@LaunchedEffect
        }
        dayGroups.forEachIndexed { index, (dayKey, _) ->
            expandedDays[dayKey] = index == 0
        }
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var reuseSessionId by remember { mutableStateOf<Long?>(null) }
    var pendingConfirm by remember { mutableStateOf<NewFromRunMode?>(null) }
    var pendingRoute by remember { mutableStateOf<Route?>(null) }
    var pendingFavorite by remember { mutableStateOf<CoursePointsSet?>(null) }
    var replaceCandidates by remember { mutableStateOf<List<CoursePointsSet>?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                onClick = { onOpenRunDetail(run.sessionId) },
                                onRunAgain = { onRunAgain(run.routeId) },
                                onNewFromThisRun = { reuseSessionId = run.sessionId },
                                onSaveFavorite = {
                                    scope.launch {
                                        val route = viewModel.routeForSession(run.sessionId)
                                        if (route == null) {
                                            snackbarHostState.showSnackbar("Could not load run settings")
                                            return@launch
                                        }
                                        val full = runCatching {
                                            onSaveRouteToFavorites(route)
                                        }.getOrElse {
                                            snackbarHostState.showSnackbar("Could not save favorite")
                                            return@launch
                                        }
                                        if (full != null) {
                                            val set = onFavoriteSetFromRoute(route)
                                            if (set != null) {
                                                pendingFavorite = set
                                                replaceCandidates = full
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    "Favorites full — remove one in Setup"
                                                )
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                "Saved to favorites — Use it on Setup to regenerate"
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val sheetSessionId = reuseSessionId
    if (sheetSessionId != null) {
        NewFromRunSheet(
            onDismiss = {
                reuseSessionId = null
                pendingConfirm = null
                pendingRoute = null
            },
            onChooseMode = { mode ->
                scope.launch {
                    val route = viewModel.routeForSession(sheetSessionId) ?: return@launch
                    pendingRoute = route
                    when (mode) {
                        NewFromRunMode.EditCheckpoints -> {
                            onEditCheckpointsFromRoute(route)
                            reuseSessionId = null
                            pendingConfirm = null
                            pendingRoute = null
                        }
                        NewFromRunMode.SameConstraints -> {
                            onApplyReuse(route, true)
                            pendingConfirm = mode
                        }
                        NewFromRunMode.KnobsOnly -> {
                            onApplyReuse(route, false)
                            pendingConfirm = mode
                        }
                    }
                }
            },
            pendingConfirm = pendingConfirm,
            onGoToSetup = {
                reuseSessionId = null
                pendingConfirm = null
                pendingRoute = null
                onGoToSetup()
            },
            onStayInHistory = {
                reuseSessionId = null
                pendingConfirm = null
                pendingRoute = null
            }
        )
    }

    val candidates = replaceCandidates
    val pending = pendingFavorite
    if (candidates != null && pending != null) {
        AlertDialog(
            onDismissRequest = {
                replaceCandidates = null
                pendingFavorite = null
            },
            title = { Text("Favorites full") },
            text = {
                Column {
                    Text("Replace an existing favorite with this run's settings:")
                    candidates.forEach { set ->
                        TextButton(
                            onClick = {
                                onReplaceFavorite(set.id, pending)
                                replaceCandidates = null
                                pendingFavorite = null
                                scope.launch {
                                    snackbarHostState.showSnackbar("Favorite replaced")
                                }
                            }
                        ) {
                            Text(CoursePointsCatalog.autoLabel(set))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        replaceCandidates = null
                        pendingFavorite = null
                    }
                ) { Text("Cancel") }
            }
        )
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
private fun ActivityRowCard(
    run: RunSummary,
    onClick: () -> Unit,
    onRunAgain: () -> Unit,
    onNewFromThisRun: () -> Unit,
    onSaveFavorite: () -> Unit
) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrienteerSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            Spacer(Modifier.height(OrienteerSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRunAgain) {
                    Text("Run again", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onNewFromThisRun) {
                    Text("New from this run…", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onSaveFavorite) {
                    Text("Add to favorites", style = MaterialTheme.typography.labelMedium)
                }
            }
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
