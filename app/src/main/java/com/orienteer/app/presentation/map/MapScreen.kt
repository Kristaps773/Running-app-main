package com.orienteer.app.presentation.map

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import com.orienteer.app.ui.icons.OrienteerIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orienteer.app.BuildConfig
import com.orienteer.app.R
import com.orienteer.app.debug.ScreenshotDemoMode
import com.orienteer.app.util.PdfExportScale
import com.orienteer.app.util.RoutePdfExporter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.presentation.components.*
import com.orienteer.app.presentation.setup.SetupUiState
import com.orienteer.app.ui.components.OrienteerGhostButton
import com.orienteer.app.presentation.setup.SetupViewModel
import com.orienteer.app.ui.theme.OrienteerSpacing
import com.orienteer.app.ui.theme.mapOverlayColors
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.domain.ProAccess
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
    var routeSheetExpanded by remember {
        mutableStateOf(!(BuildConfig.DEBUG && ScreenshotDemoMode.collapseSheetsForCapture))
    }
    var routeSheetVisibleHeightPx by remember { mutableFloatStateOf(0f) }
    val mapColors = mapOverlayColors()
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val startArgb = MaterialTheme.colorScheme.tertiary.toArgb()
    var showPdfScaleDialog by remember { mutableStateOf(false) }
    var selectedPdfScale by remember { mutableStateOf<PdfExportScale?>(null) }
    val context = LocalContext.current
    val shareGpxLabel = stringResource(R.string.share_gpx_for_oomap)
    val exportPdfLabel = stringResource(R.string.export_pdf)

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            RoutePlottingLoader(
                modifier = Modifier.align(Alignment.Center),
                label = "Plotting course…"
            )
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
                    basemapStyle = mapPrefs.basemapStyle,
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
                    trailingIcon = OrienteerIcons.Layers,
                    onTrailingClick = { viewModel.cycleBasemapStyle() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                )

                if (generationError != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 56.dp, start = OrienteerSpacing.md, end = OrienteerSpacing.md)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = OrienteerSpacing.md, vertical = OrienteerSpacing.sm)) {
                            Text(
                                text = "Route generation failed",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = generationError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = OrienteerSpacing.xxs)
                            )
                        }
                    }
                } else if (route.isDraft && generationRunning) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 56.dp, start = OrienteerSpacing.md, end = OrienteerSpacing.md)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = OrienteerSpacing.md, vertical = OrienteerSpacing.sm)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(OrienteerSpacing.xs))
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
                        .statusBarsPadding()
                        .padding(top = 64.dp, end = OrienteerSpacing.md),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        mapStyleShortLabel(mapPrefs.basemapStyle),
                        modifier = Modifier.padding(horizontal = OrienteerSpacing.sm, vertical = OrienteerSpacing.xs),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                MapAttributionBar(
                    basemapStyle = mapPrefs.basemapStyle,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(
                            start = OrienteerSpacing.sm,
                            bottom = OrienteerSpacing.sm,
                            end = MapChromeEndReserve
                        )
                        // Translate with the sheet — no layout pass every frame.
                        .graphicsLayer {
                            translationY = -routeSheetVisibleHeightPx
                        }
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(
                            end = OrienteerSpacing.md,
                            bottom = OrienteerSpacing.sm
                        )
                        .graphicsLayer {
                            translationY = -routeSheetVisibleHeightPx
                        },
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)
                ) {
                    MapZoomControls(
                        onZoomIn = { mapViewRef?.controller?.zoomIn() },
                        onZoomOut = { mapViewRef?.controller?.zoomOut() }
                    )
                    FloatingActionButton(
                        onClick = {
                            val center = route.startPoint
                            mapViewRef?.controller?.setCenter(
                                OsmGeoPoint(center.latitude, center.longitude)
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(OrienteerIcons.MyLocation, contentDescription = "Recenter")
                    }
                }

                RouteBottomSheet(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    expanded = routeSheetExpanded,
                    onExpandedChange = { routeSheetExpanded = it },
                    onVisibleHeightPxChanged = { routeSheetVisibleHeightPx = it }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)
                    ) {
                        DistanceStatCard(
                            label = "Air",
                            value = GeoUtils.formatDistanceDetailed(route.airDistanceM),
                            modifier = Modifier.weight(1f),
                            compact = true
                        )
                        DistanceStatCard(
                            label = "Path",
                            value = route.pathDistanceM?.let { GeoUtils.formatDistanceDetailed(it) }
                                ?: if (route.isDraft || generationRunning) "Routing…" else "—",
                            valueSuffix = route.pathDistanceM?.let {
                                route.pathToAirRatio?.let { ratio -> "(${"%.1f".format(ratio)}× air)" }
                            },
                            modifier = Modifier.weight(1f),
                            emphasized = true,
                            compact = true
                        )
                    }
                    Spacer(Modifier.height(OrienteerSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip(OrienteerIcons.Flag, "${route.waypointCount}", "Checkpoints")
                        StatChip(OrienteerIcons.Schedule, "${(route.estimatedDurationS / 60).toInt()} min", "Est. time")
                    }
                    Spacer(Modifier.height(OrienteerSpacing.sm))
                    if (!route.isDraft && generationError == null) {
                        OrienteerGhostButton(
                            text = shareGpxLabel,
                            onClick = { RoutePdfExporter.shareRouteAsGpx(context, route) }
                        )
                        Spacer(Modifier.height(OrienteerSpacing.xs))
                        if (ProAccess.canExportRoutePdf()) {
                            OrienteerGhostButton(
                                text = exportPdfLabel,
                                onClick = {
                                    val options = RoutePdfExporter.availableExportScales(
                                        route,
                                        mapPrefs.includePdfLegends
                                    )
                                    selectedPdfScale = RoutePdfExporter.recommendedExportScale(
                                        route,
                                        mapPrefs.includePdfLegends
                                    ).takeIf { it in options } ?: options.firstOrNull()
                                    showPdfScaleDialog = true
                                }
                            )
                            Spacer(Modifier.height(OrienteerSpacing.xs))
                        }
                    }
                    PrimaryGreenButton(
                        text = when {
                            generationError != null -> "Generation failed"
                            route.isDraft -> "Refining route…"
                            else -> "Start Run"
                        },
                        onClick = { onStartRun(route.id) },
                        enabled = !route.isDraft && generationError == null,
                        leadingIcon = OrienteerIcons.DirectionsRun
                    )
                    Spacer(Modifier.height(OrienteerSpacing.xs))
                }

                if (showPdfScaleDialog) {
                    val scaleOptions = remember(route.id, mapPrefs.includePdfLegends) {
                        RoutePdfExporter.availableExportScales(route, mapPrefs.includePdfLegends)
                    }
                    AlertDialog(
                        onDismissRequest = { showPdfScaleDialog = false },
                        title = { Text(stringResource(R.string.export_pdf_scale_title)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.export_pdf_scale_body))
                                Spacer(Modifier.height(OrienteerSpacing.sm))
                                scaleOptions.forEach { option ->
                                    val selected = selectedPdfScale == option
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedPdfScale = option },
                                        label = {
                                            Text(
                                                when (option) {
                                                    is PdfExportScale.Fixed -> option.scale.label
                                                    PdfExportScale.FitToPage ->
                                                        stringResource(R.string.export_pdf_fit_to_page)
                                                }
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val map = mapViewRef
                                    val scale = selectedPdfScale
                                    showPdfScaleDialog = false
                                    if (map != null && scale != null) {
                                        RoutePdfExporter.exportToPdf(
                                            context = context,
                                            mapView = map,
                                            route = route,
                                            includePdfLegends = mapPrefs.includePdfLegends,
                                            exportScale = scale,
                                            mapPrefs = mapPrefs,
                                            mapColors = mapColors,
                                            primaryColorArgb = primaryArgb,
                                            startColorArgb = startArgb
                                        )
                                    }
                                },
                                enabled = selectedPdfScale != null
                            ) {
                                Text(stringResource(R.string.export_pdf_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPdfScaleDialog = false }) {
                                Text(stringResource(R.string.export_pdf_cancel))
                            }
                        }
                    )
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

@Composable
private fun mapStyleShortLabel(style: BasemapStyle): String = when (style) {
    BasemapStyle.STANDARD -> stringResource(R.string.map_style_standard)
    BasemapStyle.DARK -> stringResource(R.string.map_style_dark)
    BasemapStyle.PSEUDO_O -> stringResource(R.string.map_style_pseudo_o)
    BasemapStyle.STREET_O -> stringResource(R.string.map_style_street_o)
    BasemapStyle.OPEN_TOPO -> stringResource(R.string.map_style_open_topo)
    BasemapStyle.CYCLOSM -> stringResource(R.string.map_style_cyclosm)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
