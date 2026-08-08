package com.orienteer.app.presentation.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.presentation.map.boundingBoxForReplay
import com.orienteer.app.ui.theme.mapOverlayColors

@Composable
fun RunReplayPreviewCanvas(
    planned: List<GeoPoint>,
    actual: List<GeoPoint>,
    checkpoints: List<Checkpoint>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bbox = remember(planned, actual, checkpoints) {
        boundingBoxForReplay(planned, actual, checkpoints)
    }
    val primary = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val mapColors = mapOverlayColors()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .clickable(onClick = onClick)
    ) {
        if (bbox == null) {
            Text(
                text = "No track data",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = 12f
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            if (w <= 0f || h <= 0f) return@Canvas

            fun project(p: GeoPoint): Offset {
                val lonSpan = (bbox.lonEast - bbox.lonWest).coerceAtLeast(1e-9)
                val latSpan = (bbox.latNorth - bbox.latSouth).coerceAtLeast(1e-9)
                val x = pad + ((p.longitude - bbox.lonWest) / lonSpan).toFloat() * w
                val y = pad + ((bbox.latNorth - p.latitude) / latSpan).toFloat() * h
                return Offset(x, y)
            }

            fun drawTrack(points: List<GeoPoint>, color: Color, stroke: Float) {
                if (points.size < 2) return
                val path = Path().apply {
                    val first = project(points.first())
                    moveTo(first.x, first.y)
                    points.drop(1).forEach { moveToLine(project(it)) }
                }
                drawPath(path, color = color, style = Stroke(width = stroke))
            }

            drawTrack(planned, mapColors.plannedPath.copy(alpha = 0.85f), 3f)
            drawTrack(actual, mapColors.actualPath, 5f)

            checkpoints.forEach { cp ->
                val center = project(cp.position)
                val fill = if (cp.isStart) startColor else primary
                val label = if (cp.isStart) "S" else "${cp.id}"
                drawCircle(color = Color.White, radius = 12f, center = center)
                drawCircle(color = fill, radius = 10f, center = center)
                val textLayout = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        center.x - textLayout.size.width / 2f,
                        center.y - textLayout.size.height / 2f
                    )
                )
            }
        }

        Text(
            text = "Static preview — tap for interactive map",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    RoundedCornerShape(topStart = 8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Path.moveToLine(offset: Offset) {
    lineTo(offset.x, offset.y)
}
