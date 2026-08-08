package com.orienteer.app.presentation.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.data.model.Route
import com.orienteer.app.presentation.components.*

@Composable
fun SetupScreen(
    onRouteReady: (Route) -> Unit,
    onViewPreviousRoute: (String) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    hasLocationPermission: Boolean = true,
    onRequestLocationPermission: () -> Unit = {},
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val distanceKm by viewModel.distanceKm.collectAsStateWithLifecycle()
    val latestRoute by viewModel.latestRoute.collectAsStateWithLifecycle()
    val presetDistances = remember { listOf(3.0, 5.0, 10.0) }
    var customDistanceActive by remember { mutableStateOf(false) }
    var customDistanceText by remember { mutableStateOf("") }

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
            // Stay on Setup during RefiningRoute — avoid Map "Routing…" and double-nav.
            else -> Unit
        }
        previousState = uiState
    }

    StitchBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = remember(maxHeight) { setupLayoutMetrics(maxHeight.value) }

            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(layout.topSpacerDp.dp))

                    Box(
                        modifier = Modifier
                            .size(layout.iconSizeDp.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(layout.iconInnerDp.dp)
                        )
                    }

                    Spacer(Modifier.height(layout.afterIconSpacerDp.dp))

                    Text(
                        text = "OrienteerRun",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Generate your route, find the checkpoints.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = layout.subtitleBottomDp.dp)
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(layout.cardPaddingDp.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TARGET DISTANCE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "%.1f km".format(distanceKm),
                                fontSize = layout.distanceFontSp.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = distanceKm.toFloat(),
                                onValueChange = {
                                    if (!customDistanceActive && it.toDouble() !in presetDistances) {
                                        customDistanceActive = true
                                    }
                                    viewModel.setDistance(it.toDouble())
                                    if (customDistanceActive) {
                                        customDistanceText = "%.1f".format(it)
                                    }
                                },
                                valueRange = 1f..42f,
                                steps = 40,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(3.0, 5.0, 10.0).forEach { km ->
                                    DistanceChip(
                                        label = "${km.toInt()} km",
                                        selected = !customDistanceActive && distanceKm == km,
                                        onClick = {
                                            customDistanceActive = false
                                            viewModel.setDistance(km)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (customDistanceActive) {
                                OutlinedTextField(
                                    value = customDistanceText,
                                    onValueChange = { text ->
                                        customDistanceText = text.filter { it.isDigit() || it == '.' }
                                        customDistanceText.toDoubleOrNull()?.let { viewModel.setDistance(it) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    suffix = { Text("km") },
                                    placeholder = { Text("Enter distance") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            } else {
                                DistanceChip(
                                    label = "Other",
                                    selected = false,
                                    onClick = {
                                        customDistanceActive = true
                                        customDistanceText = "%.1f".format(distanceKm)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(layout.sectionSpacerDp.dp))

                    val checkpointCount = estimateCheckpoints(distanceKm)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip(Icons.Default.LocationOn, "~$checkpointCount checkpoints")
                        InfoChip(Icons.Default.Schedule, "${estimateTime(distanceKm)} min est.")
                    }

                    Spacer(Modifier.height(layout.afterInfoSpacerDp.dp))

                    if (latestRoute != null) {
                        SettingsListRow(
                            title = "View previous route",
                            onClick = { onViewPreviousRoute(latestRoute!!.id) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    SettingsListRow(
                        title = "Run history",
                        subtitle = "Past runs and replay",
                        onClick = onOpenHistory
                    )
                    SettingsListRow(
                        title = "Settings",
                        subtitle = "Route generation and map display",
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (!hasLocationPermission) {
                        Spacer(Modifier.height(layout.sectionSpacerDp.dp))
                        SettingsListRow(
                            title = "Location permission required",
                            subtitle = "Tap to grant access for GPS routes",
                            onClick = onRequestLocationPermission
                        )
                    }

                    Spacer(Modifier.height(layout.bottomContentPadDp.dp))
                }

                val isLoading = uiState is SetupUiState.LocatingGps ||
                    uiState is SetupUiState.GeneratingRoute
                val isRefining = uiState is SetupUiState.RefiningRoute
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        if (isRefining) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Refining walking path…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        PrimaryGreenButton(
                            text = when (uiState) {
                                is SetupUiState.LocatingGps -> "Getting GPS fix…"
                                is SetupUiState.GeneratingRoute -> "Generating route…"
                                is SetupUiState.RefiningRoute -> "Refining route…"
                                else -> "Generate Route"
                            },
                            onClick = { viewModel.generateRoute() },
                            enabled = !isLoading && !isRefining,
                            loading = isLoading,
                            leadingIcon = if (!isLoading && !isRefining) Icons.Default.MyLocation else null,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.dismissError() }) {
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
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun estimateCheckpoints(distanceKm: Double): Int =
    (distanceKm / 1.2).toInt().coerceIn(3, 8)

private fun estimateTime(distanceKm: Double): Int =
    (distanceKm * 6).toInt()
