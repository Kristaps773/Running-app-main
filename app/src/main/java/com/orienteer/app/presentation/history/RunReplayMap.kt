package com.orienteer.app.presentation.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.presentation.map.IofCourseOverprintOverlay
import com.orienteer.app.presentation.map.addCheckpointMarkers
import com.orienteer.app.presentation.map.addOrienteeringAirLegOverlays
import com.orienteer.app.presentation.map.addReplayPolylines
import com.orienteer.app.presentation.map.airLegSegmentsFromCheckpoints
import com.orienteer.app.ui.theme.mapOverlayColors
import com.orienteer.app.presentation.map.boundingBoxForReplay
import com.orienteer.app.presentation.map.configureReplayMapInteraction
import com.orienteer.app.presentation.map.createReplayMapHost
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.presentation.map.applyBasemapStyle
import com.orienteer.app.presentation.map.clearRouteOverlays
import com.orienteer.app.presentation.map.fitMapToBoundingBox
import com.orienteer.app.presentation.map.requireMapViewFromHost
import org.osmdroid.util.GeoPoint as OsmGeoPoint

@Composable
fun RunReplayMap(
    planned: List<GeoPoint>,
    actual: List<GeoPoint>,
    checkpoints: List<Checkpoint>,
    basemapStyle: BasemapStyle,
    modifier: Modifier = Modifier,
    compactPreview: Boolean = true,
    useIofCourseOverprint: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val startColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val mapColors = mapOverlayColors()
    val airColor = mapColors.airLeg.toArgb()
    val airHaloColor = mapColors.airLegHalo.toArgb()
    var appliedReplayKey by remember { mutableStateOf<String?>(null) }
    var appliedTileKey by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier
            .graphicsLayer { clip = true }
            .clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            createReplayMapHost(
                context = ctx,
                basemapStyle = basemapStyle,
                compactPreview = compactPreview
            )
        },
        update = { view ->
            val map = (view as FrameLayout).requireMapViewFromHost()
            configureReplayMapInteraction(map, compactPreview)
            val styleKey = basemapStyle.name
            if (appliedTileKey != styleKey) {
                applyBasemapStyle(map, map.context, basemapStyle)
                map.setTag(com.orienteer.app.R.id.map_tag_basemap_style, styleKey)
                appliedTileKey = styleKey
            }

            val replayKey = buildString {
                append(styleKey)
                append('|')
                append(compactPreview)
                append('|')
                append(useIofCourseOverprint)
                append('|')
                append(planned.size)
                planned.forEach {
                    append('|')
                    append(it.latitude)
                    append(',')
                    append(it.longitude)
                }
                append('|')
                append(actual.size)
                actual.forEach {
                    append('|')
                    append(it.latitude)
                    append(',')
                    append(it.longitude)
                }
                append('|')
                append(checkpoints.size)
                checkpoints.forEach {
                    append('|')
                    append(it.id)
                    append(':')
                    append(it.position.latitude)
                    append(',')
                    append(it.position.longitude)
                }
            }

            if (replayKey == appliedReplayKey) {
                return@AndroidView
            }

            clearRouteOverlays(map)
            // Contours sit in overlays; re-apply style so Pseud-O keeps them under polylines.
            applyBasemapStyle(map, map.context, basemapStyle)
            val center = actual.firstOrNull() ?: planned.firstOrNull() ?: checkpoints.firstOrNull()?.position
            if (center != null) {
                map.controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
            }

            addReplayPolylines(map, planned, actual) {
                if (!useIofCourseOverprint && checkpoints.size >= 2) {
                    map.addOrienteeringAirLegOverlays(
                        airLegSegmentsFromCheckpoints(checkpoints),
                        airColor,
                        airHaloColor
                    )
                }
            }
            if (checkpoints.isNotEmpty()) {
                if (useIofCourseOverprint) {
                    map.overlays.add(
                        IofCourseOverprintOverlay(
                            checkpoints = checkpoints,
                            purpleArgb = airColor,
                            showLegs = true
                        )
                    )
                } else {
                    addCheckpointMarkers(map, checkpoints, primaryColor, startColor)
                }
            }

            val box = boundingBoxForReplay(planned, actual, checkpoints)
            map.invalidate()
            val paddingPx = (40 * map.resources.displayMetrics.density).toInt()
            fitMapToBoundingBox(map, box, paddingPx)
            appliedReplayKey = replayKey
        }
    )
}

@Composable
fun RunReplayMapFullScreen(
    planned: List<GeoPoint>,
    actual: List<GeoPoint>,
    checkpoints: List<Checkpoint>,
    basemapStyle: BasemapStyle,
    modifier: Modifier = Modifier,
    useIofCourseOverprint: Boolean = false
) {
    RunReplayMap(
        planned = planned,
        actual = actual,
        checkpoints = checkpoints,
        basemapStyle = basemapStyle,
        modifier = modifier.fillMaxSize(),
        compactPreview = false,
        useIofCourseOverprint = useIofCourseOverprint
    )
}
