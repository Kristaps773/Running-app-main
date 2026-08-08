package com.orienteer.app.presentation.map

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.presentation.components.*
import com.orienteer.app.presentation.setup.SetupUiState
import com.orienteer.app.presentation.setup.SetupViewModel
import com.orienteer.app.util.GeoUtils
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView

@Composable
fun MapScreen(
    routeId: String,
    onStartRun: (String) -> Unit,
    onNavigateBack: () -> Unit,
    setupViewModel: SetupViewModel? = null,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapPrefs by viewModel.mapDisplayPreferences.collectAsStateWithLifecycle()
    val setupUiState = rememberSetupUiState(setupViewModel)
    val generationError = (setupUiState as? SetupUiState.Error)?.message
    val generationRunning = setupUiState is SetupUiState.GeneratingRoute ||
        setupUiState is SetupUiState.RefiningRoute
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val route = uiState.route
            if (route != null) {
                OsmMapHost(
                    route = route,
                    currentLocation = uiState.currentLocation,
                    mapPrefs = mapPrefs,
                    useDarkBasemap = mapPrefs.useDarkBasemap,
                    interactive = true,
                    onMapReady = { mapViewRef = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .mapVignette(MaterialTheme.colorScheme.background)
                )

                OrienteerTopBar(
                    title = "Route Preview",
                    showBack = true,
                    onBack = onNavigateBack,
                    trailingIcon = Icons.Default.Layers,
                    onTrailingClick = { },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                if (generationError != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = "Route generation failed",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = generationError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else if (route.isDraft && generationRunning) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Refining walking path…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 72.dp, end = 16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        if (mapPrefs.useDarkBasemap) "Dark" else "Standard",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                FloatingActionButton(
                    onClick = {
                        val center = route.startPoint
                        mapViewRef?.controller?.setCenter(
                            OsmGeoPoint(center.latitude, center.longitude)
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter")
                }

                RouteBottomSheet(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DistanceStatCard(
                            label = "Air",
                            value = GeoUtils.formatDistanceDetailed(route.airDistanceM),
                            modifier = Modifier.weight(1f)
                        )
                        DistanceStatCard(
                            label = "Path",
                            value = route.pathDistanceM?.let { path ->
                                val ratio = route.pathToAirRatio
                                val dist = GeoUtils.formatDistanceDetailed(path)
                                ratio?.let { "$dist (${"%.2f".format(it)}× air)" } ?: dist
                            } ?: if (route.isDraft || generationRunning) "Routing…" else "—",
                            modifier = Modifier.weight(1f),
                            emphasized = true
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip(Icons.Default.Flag, "${route.waypointCount}", "Checkpoints")
                        StatChip(Icons.Default.Schedule, "${(route.estimatedDurationS / 60).toInt()} min", "Est. time")
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryGreenButton(
                        text = when {
                            generationError != null -> "Generation failed"
                            route.isDraft -> "Refining route…"
                            else -> "Start Run"
                        },
                        onClick = { onStartRun(route.id) },
                        enabled = !route.isDraft && generationError == null,
                        leadingIcon = Icons.Default.DirectionsRun
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun rememberSetupUiState(setupViewModel: SetupViewModel?): SetupUiState? {
    if (setupViewModel == null) return null
    val state by setupViewModel.uiState.collectAsStateWithLifecycle()
    return state
}

private fun Modifier.mapVignette(background: Color): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                background.copy(alpha = 0.45f),
                Color.Transparent,
                Color.Transparent,
                background.copy(alpha = 0.25f)
            )
        )
    )
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
