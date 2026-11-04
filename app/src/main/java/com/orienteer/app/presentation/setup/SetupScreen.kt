package com.orienteer.app.presentation.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.Route
import com.orienteer.app.domain.CourseEndpoints
import com.orienteer.app.domain.LockedCoursePoints
import com.orienteer.app.domain.ProAccess
import com.orienteer.app.presentation.components.DistanceChip
import com.orienteer.app.presentation.components.PrimaryGreenButton
import com.orienteer.app.presentation.components.SettingsGroupedList
import com.orienteer.app.presentation.components.SettingsListRow
import com.orienteer.app.presentation.components.StitchBackground
import com.orienteer.app.ui.icons.OrienteerIcons
import com.orienteer.app.ui.theme.OrienteerCardShape
import com.orienteer.app.ui.theme.OrienteerInnerShape
import com.orienteer.app.ui.theme.OrienteerRadius
import com.orienteer.app.ui.theme.OrienteerSpacing
import com.orienteer.app.ui.theme.OrienteerTheme
import com.orienteer.app.ui.theme.ThemeMode
import com.orienteer.app.util.GeoUtils

@Composable
fun SetupScreen(
    onRouteReady: (Route) -> Unit,
    onViewPreviousRoute: (String) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenEndpointPicker: () -> Unit = {},
    hasLocationPermission: Boolean = true,
    onRequestLocationPermission: () -> Unit = {},
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val distanceKm by viewModel.distanceKm.collectAsStateWithLifecycle()
    val minCheckpoints by viewModel.minCheckpoints.collectAsStateWithLifecycle()
    val maxCheckpoints by viewModel.maxCheckpoints.collectAsStateWithLifecycle()
    val latestRoute by viewModel.latestRoute.collectAsStateWithLifecycle()
    val customStart by viewModel.customStart.collectAsStateWithLifecycle()
    val customFinish by viewModel.customFinish.collectAsStateWithLifecycle()
    val lockedCheckpoints by viewModel.lockedCheckpoints.collectAsStateWithLifecycle()
    val lockedAnyOrder by viewModel.lockedAnyOrder.collectAsStateWithLifecycle()
    val presetDistances = remember { listOf(3.0, 5.0, 10.0) }
    var customDistanceActive by remember { mutableStateOf(false) }
    var customDistanceText by remember { mutableStateOf("") }
    val minDistanceKm = remember(customStart, customFinish, lockedCheckpoints, lockedAnyOrder) {
        val s = customStart
        val f = customFinish
        if (s != null && (f != null || lockedCheckpoints.isNotEmpty())) {
            LockedCoursePoints.minVisitAirM(
                s,
                f,
                lockedCheckpoints,
                anyOrder = lockedAnyOrder && lockedCheckpoints.size >= 2
            ) / 1000.0
        } else if (s != null && f != null) {
            CourseEndpoints.minAirDistanceM(s, f) / 1000.0
        } else {
            0.5
        }
    }
    val hasLockedFloor = lockedCheckpoints.isNotEmpty()
    val isOneWay = customFinish != null
    val hasCustomCoursePoints =
        customStart != null || customFinish != null || lockedCheckpoints.isNotEmpty()

    LaunchedEffect(distanceKm) {
        if (distanceKm !in presetDistances && !customDistanceActive) {
            customDistanceActive = true
            customDistanceText = "%.1f".format(distanceKm)
        }
    }

    DisposableEffect(Unit) {
        viewModel.onSetupScreenActive()
        onDispose { viewModel.onSetupScreenInactive() }
    }

    var previousState by remember { mutableStateOf<SetupUiState?>(null) }
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SetupUiState.Success -> {
                if (previousState !is SetupUiState.Success) {
                    onRouteReady(state.route)
                    viewModel.resetAfterNavigation()
                }
            }
            else -> Unit
        }
        previousState = uiState
    }

    SetupScreenContent(
        uiState = uiState,
        distanceKm = distanceKm,
        minCheckpoints = minCheckpoints,
        maxCheckpoints = maxCheckpoints,
        lockedCount = lockedCheckpoints.size,
        latestRouteId = latestRoute?.id,
        customDistanceActive = customDistanceActive,
        customDistanceText = customDistanceText,
        hasLocationPermission = hasLocationPermission,
        isOneWay = isOneWay,
        minDistanceKm = minDistanceKm,
        hasLockedFloor = hasLockedFloor,
        endpointsSummary = buildString {
            when {
                isOneWay && customStart != null && customFinish != null ->
                    append(
                        "One-way · ${GeoUtils.formatDistance(CourseEndpoints.minAirDistanceM(customStart!!, customFinish!!))} air"
                    )
                customStart != null ->
                    append("Custom start · return to start")
                else ->
                    append("GPS · return to start")
            }
            if (lockedCheckpoints.isNotEmpty()) {
                append(" · ${lockedCheckpoints.size} locked")
                append(if (lockedAnyOrder) " · any order" else " · in order")
            }
        },
        hasCustomCoursePoints = hasCustomCoursePoints,
        onCustomDistanceActiveChange = { customDistanceActive = it },
        onCustomDistanceTextChange = { customDistanceText = it },
        onSetDistance = viewModel::setDistance,
        onSetCheckpointWindow = viewModel::setCheckpointWindow,
        onGenerate = viewModel::generateRoute,
        onDismissError = viewModel::dismissError,
        onViewPreviousRoute = onViewPreviousRoute,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        onOpenEndpointPicker = onOpenEndpointPicker,
        onClearEndpoints = viewModel::clearCourseEndpoints,
        onRequestLocationPermission = onRequestLocationPermission
    )
}

@Composable
private fun SetupScreenContent(
    uiState: SetupUiState,
    distanceKm: Double,
    minCheckpoints: Int = UserPreferencesStore.DEFAULT_MIN_CHECKPOINTS,
    maxCheckpoints: Int = UserPreferencesStore.DEFAULT_MAX_CHECKPOINTS,
    lockedCount: Int = 0,
    latestRouteId: String?,
    customDistanceActive: Boolean,
    customDistanceText: String,
    hasLocationPermission: Boolean,
    isOneWay: Boolean = false,
    minDistanceKm: Double = 0.5,
    hasLockedFloor: Boolean = false,
    endpointsSummary: String = "Using GPS - return to start",
    hasCustomCoursePoints: Boolean = false,
    onCustomDistanceActiveChange: (Boolean) -> Unit,
    onCustomDistanceTextChange: (String) -> Unit,
    onSetDistance: (Double) -> Unit,
    onSetCheckpointWindow: (Int, Int) -> Unit = { _, _ -> },
    onGenerate: () -> Unit,
    onDismissError: () -> Unit,
    onViewPreviousRoute: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEndpointPicker: () -> Unit = {},
    onClearEndpoints: () -> Unit = {},
    onRequestLocationPermission: () -> Unit
) {
    val cardRadius = OrienteerRadius.card

    StitchBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = remember(maxHeight) { setupLayoutMetrics(maxHeight.value) }
            val cardPad = layout.cardPaddingDp.dp
            val innerShape = OrienteerInnerShape(cardRadius, cardPad)

            Column(modifier = Modifier.fillMaxSize()) {
                // Title-only brand (no hero icon) so distance + dual checkpoint
                // sliders + Settings stay above the fold on typical phones.
                // Scroll is a last-resort overflow safety (large font / short devices).
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = OrienteerSpacing.screenHorizontal),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(layout.topSpacerDp.dp))

                    Text(
                        text = "OrienteerRun",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (layout.showTagline) {
                        Text(
                            text = if (isOneWay) {
                                "Start here, finish there."
                            } else {
                                "Generate a loop. Hit real checkpoints."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = layout.subtitleBottomDp.dp)
                        )
                    } else {
                        Spacer(Modifier.height(layout.subtitleBottomDp.dp))
                    }

                    // Distance card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = OrienteerCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(cardPad),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Target distance",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(OrienteerSpacing.xxs))
                            Text(
                                text = "%.1f".format(distanceKm),
                                fontSize = layout.distanceFontSp.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "kilometers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasLockedFloor) {
                                Text(
                                    text = "At least %.1f km to use all points."
                                        .format(minDistanceKm),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            } else if (isOneWay) {
                                Text(
                                    text = "At least %.1f km (straight to finish). Extra adds checkpoints."
                                        .format(minDistanceKm),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(Modifier.height(OrienteerSpacing.xs))
                            val sliderMin = when {
                                hasLockedFloor || isOneWay ->
                                    minDistanceKm.toFloat().coerceAtLeast(0.5f)
                                else -> 1f
                            }
                            val sliderMax = ProAccess.maxTargetDistanceKm().toFloat()
                            val sliderValue = distanceKm.toFloat().coerceIn(sliderMin, sliderMax)
                            Slider(
                                value = sliderValue,
                                onValueChange = {
                                    if (!customDistanceActive && it.toDouble() !in listOf(3.0, 5.0, 10.0)) {
                                        onCustomDistanceActiveChange(true)
                                    }
                                    onSetDistance(it.toDouble())
                                    if (customDistanceActive) {
                                        onCustomDistanceTextChange("%.1f".format(it))
                                    }
                                },
                                valueRange = sliderMin..sliderMax.coerceAtLeast(sliderMin + 0.1f),
                                steps = 40,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                            Spacer(Modifier.height(OrienteerSpacing.xxs))
                            Row(horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)) {
                                listOf(3.0, 5.0, 10.0).forEach { km ->
                                    val chipEnabled =
                                        (!isOneWay && !hasLockedFloor) || km >= minDistanceKm - 0.05
                                    DistanceChip(
                                        label = "${km.toInt()} km",
                                        selected = !customDistanceActive && distanceKm == km,
                                        onClick = {
                                            if (!chipEnabled) return@DistanceChip
                                            onCustomDistanceActiveChange(false)
                                            onSetDistance(km)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(OrienteerSpacing.xxs))
                            if (customDistanceActive) {
                                OutlinedTextField(
                                    value = customDistanceText,
                                    onValueChange = { text ->
                                        val filtered = text.filter { it.isDigit() || it == '.' }
                                        onCustomDistanceTextChange(filtered)
                                        filtered.toDoubleOrNull()?.let(onSetDistance)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(innerShape),
                                    singleLine = true,
                                    suffix = { Text("km") },
                                    placeholder = { Text("Custom distance") },
                                    shape = innerShape,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            } else {
                                DistanceChip(
                                    label = "Other",
                                    selected = false,
                                    onClick = {
                                        onCustomDistanceActiveChange(true)
                                        onCustomDistanceTextChange("%.1f".format(distanceKm))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(layout.sectionSpacerDp.dp))

                    // Intermediate checkpoints — secondary control (quieter than distance).
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = OrienteerCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = OrienteerSpacing.sm,
                                vertical = OrienteerSpacing.xs
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Checkpoints",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "intermediate (not start/finish)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.85f
                                        )
                                    )
                                }
                                Text(
                                    text = if (minCheckpoints == maxCheckpoints) {
                                        "$minCheckpoints"
                                    } else {
                                        "$minCheckpoints–$maxCheckpoints"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (lockedCount > 0) {
                                Text(
                                    text = "Min locked to $lockedCount custom points",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            val sliderFloor = maxOf(
                                UserPreferencesStore.MIN_CHECKPOINTS,
                                lockedCount
                            ).coerceAtMost(UserPreferencesStore.MAX_CHECKPOINTS)
                            val sliderCeil = UserPreferencesStore.MAX_CHECKPOINTS
                            val sliderSteps = (sliderCeil - sliderFloor - 1).coerceAtLeast(0)
                            val sliderRange = sliderFloor.toFloat()..sliderCeil.toFloat()
                            Text(
                                text = "Min $minCheckpoints",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = OrienteerSpacing.xxs)
                            )
                            Slider(
                                value = minCheckpoints.toFloat()
                                    .coerceIn(sliderFloor.toFloat(), sliderCeil.toFloat()),
                                onValueChange = { v ->
                                    val newMin = v.toInt().coerceAtLeast(sliderFloor)
                                    onSetCheckpointWindow(newMin, maxOf(newMin, maxCheckpoints))
                                },
                                valueRange = sliderRange,
                                steps = sliderSteps,
                                enabled = sliderFloor < sliderCeil,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.outline,
                                    activeTrackColor = MaterialTheme.colorScheme.outline,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                            Text(
                                text = "Max $maxCheckpoints",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Slider(
                                value = maxCheckpoints.toFloat()
                                    .coerceIn(sliderFloor.toFloat(), sliderCeil.toFloat()),
                                onValueChange = { v ->
                                    val newMax = v.toInt().coerceAtLeast(sliderFloor)
                                    onSetCheckpointWindow(
                                        minOf(maxOf(minCheckpoints, sliderFloor), newMax),
                                        newMax
                                    )
                                },
                                valueRange = sliderRange,
                                steps = sliderSteps,
                                enabled = sliderFloor < sliderCeil,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.outline,
                                    activeTrackColor = MaterialTheme.colorScheme.outline,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                        }
                    }

                    if (layout.showEstimateStrip) {
                        Spacer(Modifier.height(layout.sectionSpacerDp.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = OrienteerCardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = OrienteerSpacing.sm,
                                        vertical = OrienteerSpacing.xs
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)
                            ) {
                                val cpLabel = if (minCheckpoints == maxCheckpoints) {
                                    "$minCheckpoints checkpoints"
                                } else {
                                    "$minCheckpoints–$maxCheckpoints checkpoints"
                                }
                                InfoChip(
                                    icon = Icons.Default.LocationOn,
                                    label = cpLabel,
                                    modifier = Modifier.weight(1f)
                                )
                                InfoChip(
                                    icon = OrienteerIcons.Schedule,
                                    label = "${estimateTime(distanceKm)} min est.",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(layout.afterInfoSpacerDp.dp))

                    // Single-line rows to keep Settings above the fold
                    SettingsGroupedList {
                        if (latestRouteId != null) {
                            SettingsListRow(
                                title = "Previous route",
                                onClick = { onViewPreviousRoute(latestRouteId) },
                                flat = true
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                        }
                        SettingsListRow(
                            title = "Run history",
                            onClick = onOpenHistory,
                            flat = true
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                        SettingsListRow(
                            title = "Settings",
                            onClick = onOpenSettings,
                            flat = true
                        )
                        if (!hasLocationPermission) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                            SettingsListRow(
                                title = "Location permission",
                                onClick = onRequestLocationPermission,
                                flat = true
                            )
                        }
                    }

                    Spacer(Modifier.height(layout.bottomContentPadDp.dp))
                }

                val isLoading = uiState is SetupUiState.LocatingGps ||
                    uiState is SetupUiState.GeneratingRoute
                val isRefining = uiState is SetupUiState.RefiningRoute
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(
                                horizontal = OrienteerSpacing.screenHorizontal,
                                vertical = OrienteerSpacing.sm
                            )
                    ) {
                        if (isRefining) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(OrienteerCardShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Spacer(Modifier.height(OrienteerSpacing.xs))
                            Text(
                                text = "Refining walking path…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(OrienteerSpacing.xs))
                        }
                        if (hasCustomCoursePoints) {
                            Text(
                                text = endpointsSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = OrienteerSpacing.xxs),
                                textAlign = TextAlign.Center
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PrimaryGreenButton(
                                text = when (uiState) {
                                    is SetupUiState.LocatingGps -> "GPS…"
                                    is SetupUiState.GeneratingRoute -> "Generating…"
                                    is SetupUiState.RefiningRoute -> "Refining…"
                                    else -> "Generate"
                                },
                                onClick = onGenerate,
                                enabled = !isLoading && !isRefining,
                                loading = isLoading,
                                leadingIcon = if (!isLoading && !isRefining) OrienteerIcons.MyLocation else null,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = onOpenEndpointPicker,
                                enabled = !isLoading && !isRefining,
                                shape = OrienteerCardShape,
                                color = if (hasCustomCoursePoints) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = OrienteerIcons.Flag,
                                        contentDescription = if (isOneWay) "Start and finish" else "Set start / finish",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (hasCustomCoursePoints) {
                                TextButton(
                                    onClick = onClearEndpoints,
                                    enabled = !isLoading && !isRefining,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState is SetupUiState.Error,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                if (uiState is SetupUiState.Error) {
                    Snackbar(
                        modifier = Modifier.padding(OrienteerSpacing.md),
                        shape = OrienteerCardShape,
                        action = {
                            TextButton(onClick = onDismissError) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text((uiState as SetupUiState.Error).message)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val shape = OrienteerInnerShape(OrienteerRadius.card, OrienteerSpacing.sm)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = OrienteerSpacing.sm,
                vertical = OrienteerSpacing.xs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

private fun estimateTime(distanceKm: Double): Int =
    (distanceKm * 6).toInt()

@Preview(showBackground = true, name = "Setup — alpine light")
@Composable
private fun SetupScreenPreview() {
    OrienteerTheme(themeMode = ThemeMode.LIGHT) {
        SetupScreenContent(
            uiState = SetupUiState.Idle,
            distanceKm = 5.0,
            latestRouteId = "preview",
            customDistanceActive = false,
            customDistanceText = "",
            hasLocationPermission = true,
            onCustomDistanceActiveChange = {},
            onCustomDistanceTextChange = {},
            onSetDistance = {},
            onGenerate = {},
            onDismissError = {},
            onViewPreviousRoute = {},
            onOpenHistory = {},
            onOpenSettings = {},
            onRequestLocationPermission = {}
        )
    }
}
