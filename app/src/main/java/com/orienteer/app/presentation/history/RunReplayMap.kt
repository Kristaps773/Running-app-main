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
import com.orienteer.app.presentation.map.addCheckpointMarkers
import com.orienteer.app.presentation.map.addReplayPolylines
import com.orienteer.app.presentation.map.boundingBoxForReplay
import com.orienteer.app.presentation.map.configureReplayMapInteraction
import com.orienteer.app.presentation.map.createReplayMapHost
import com.orienteer.app.presentation.map.defaultBasemapTileSource
import com.orienteer.app.presentation.map.fitMapToBoundingBox
import com.orienteer.app.presentation.map.requireMapViewFromHost
import org.osmdroid.util.GeoPoint as OsmGeoPoint

@Composable
fun RunReplayMap(
    planned: List<GeoPoint>,
    actual: List<GeoPoint>,
    checkpoints: List<Checkpoint>,
    useDarkBasemap: Boolean,
    modifier: Modifier = Modifier,
    compactPreview: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val startColor = MaterialTheme.colorScheme.tertiary.toArgb()
    var appliedReplayKey by remember { mutableStateOf<String?>(null) }
    var appliedTileKey by remember { mutableStateOf<Boolean?>(null) }

    AndroidView(
        modifier = modifier
            .graphicsLayer { clip = true }
            .clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            createReplayMapHost(
                context = ctx,
                useDarkBasemap = useDarkBasemap,
                compactPreview = compactPreview
            )
        },
        update = { view ->
            val map = (view as FrameLayout).requireMapViewFromHost()
            configureReplayMapInteraction(map, compactPreview)
            if (appliedTileKey != useDarkBasemap) {
                map.setTileSource(defaultBasemapTileSource(useDarkBasemap))
                appliedTileKey = useDarkBasemap
            }

            val replayKey = buildString {
                append(useDarkBasemap)
                append('|')
                append(compactPreview)
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

            map.overlays.clear()
            val center = actual.firstOrNull() ?: planned.firstOrNull() ?: checkpoints.firstOrNull()?.position
            if (center != null) {
                map.controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
            }

            addReplayPolylines(map, planned, actual)
            if (checkpoints.isNotEmpty()) {
                addCheckpointMarkers(map, checkpoints, primaryColor, startColor)
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
    useDarkBasemap: Boolean,
    modifier: Modifier = Modifier
) {
    RunReplayMap(
        planned = planned,
        actual = actual,
        checkpoints = checkpoints,
        useDarkBasemap = useDarkBasemap,
        modifier = modifier.fillMaxSize(),
        compactPreview = false
    )
}
