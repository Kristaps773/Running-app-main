package com.orienteer.app.presentation.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.sqrt
import org.osmdroid.util.GeoPoint as OsmGeoPoint

/**
 * Non-IOF air legs: later legs break at crossings with faces parallel to the cutter,
 * and every leg stops outside checkpoint badges (circle) / start triangle.
 */
class AirLegsOverlay(
    private val checkpoints: List<Checkpoint>,
    private val airColorArgb: Int,
    private val haloColorArgb: Int,
    private val markerScale: Float = 1f
) : Overlay() {

    private val pointA = Point()
    private val pointB = Point()
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = haloColorArgb
    }
    private val airPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = airColorArgb
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || checkpoints.size < 2) return
        val projection = mapView.projection
        val density = mapView.context.resources.displayMetrics.density
        // Match CheckpointMarkerIcon CIRCLE_DP (42) → radius in screen px, then metres.
        val badgeRadiusPx = (21f * density * markerScale).coerceAtLeast(8f)
        val sample = checkpoints.first().position
        val circleR = pixelsToMetres(projection, sample, badgeRadiusPx)
        val startR = circleR * 1.05

        val raw = airLegSegmentsFromCheckpoints(checkpoints)
        val oneWayDistinct = checkpoints.size >= 2 &&
            checkpoints.first().isStart &&
            checkpoints.last().isStart &&
            (
                checkpoints.first().position.latitude != checkpoints.last().position.latitude ||
                    checkpoints.first().position.longitude != checkpoints.last().position.longitude
            )

        // Meet own badges at the rim; SYMBOL_PAD_M is only for mid-leg pass-through gaps.
        val trimmed = raw.mapIndexedNotNull { index, (from, to) ->
            val startTrim = if (index == 0) startR else circleR
            val endTrim = when {
                index == raw.lastIndex && oneWayDistinct -> circleR
                index == raw.lastIndex && !oneWayDistinct -> startR
                else -> circleR
            }
            IofCourseGeometry.shortenLeg(from, to, startTrim, endTrim)
        }

        val obstacles = ArrayList<AirLegCrossingGaps.SymbolObstacle>()
        val firstStartIdx = checkpoints.indexOfFirst { it.isStart }.let { if (it < 0) 0 else it }
        val startPos = checkpoints[firstStartIdx].position
        val nextAfterStart = checkpoints.getOrNull(firstStartIdx + 1)?.position
            ?: checkpoints.getOrNull((firstStartIdx + 1) % checkpoints.size)?.position
        val facing = if (nextAfterStart != null) {
            GeoUtils.bearingDegrees(startPos, nextAfterStart)
        } else {
            0.0
        }
        obstacles.add(
            AirLegCrossingGaps.SymbolObstacle(startPos, startR, triangleFacingDeg = facing)
        )
        checkpoints.forEach { cp ->
            if (cp.isStart) return@forEach
            obstacles.add(AirLegCrossingGaps.SymbolObstacle(cp.position, circleR))
        }
        if (oneWayDistinct) {
            obstacles.add(
                AirLegCrossingGaps.SymbolObstacle(checkpoints.last().position, circleR)
            )
        }

        val pieces = AirLegCrossingGaps.gappedPieces(trimmed, symbols = obstacles)
        val haloHalf = 5.5f
        val airHalf = 2.75f
        pieces.forEach { piece ->
            projection.toPixels(
                OsmGeoPoint(piece.from.latitude, piece.from.longitude),
                pointA
            )
            projection.toPixels(
                OsmGeoPoint(piece.to.latitude, piece.to.longitude),
                pointB
            )
            val x0 = pointA.x.toFloat()
            val y0 = pointA.y.toFloat()
            val x1 = pointB.x.toFloat()
            val y1 = pointB.y.toFloat()
            val startCut = piece.startCutBearingDeg?.let {
                CourseLegStroke.screenDirFromBearing(projection, piece.from, it)
            }
            val endCut = piece.endCutBearingDeg?.let {
                CourseLegStroke.screenDirFromBearing(projection, piece.to, it)
            }
            canvas.drawPath(
                CourseLegStroke.beveledOutline(x0, y0, x1, y1, haloHalf, startCut, endCut),
                haloPaint
            )
            canvas.drawPath(
                CourseLegStroke.beveledOutline(x0, y0, x1, y1, airHalf, startCut, endCut),
                airPaint
            )
        }
    }

    private fun pixelsToMetres(projection: Projection, at: GeoPoint, pixels: Float): Double {
        if (pixels <= 0f) return 12.0
        val origin = OsmGeoPoint(at.latitude, at.longitude)
        val east = GeoUtils.offsetPoint(at, 50.0, 90.0)
        val a = Point()
        val b = Point()
        projection.toPixels(origin, a)
        projection.toPixels(OsmGeoPoint(east.latitude, east.longitude), b)
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val pxPer50 = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
        return (pixels * 50.0 / pxPer50).coerceIn(8.0, 60.0)
    }
}
