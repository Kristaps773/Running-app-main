package com.orienteer.app.presentation.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * True-north meridians with O-map style arrowheads pointing geographic north:
 * roughly constant screen distance (adapts as you zoom), about 3 lines
 * across the phone (never more than 4), always spanning the full viewport.
 */
class NorthLinesOverlay(
    lineColorArgb: Int
) : Overlay() {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // ≥ half of visible air-leg outline (see RouteMapOverlay 5.5f).
        strokeWidth = NORTH_LINE_STROKE_PX
        color = lineColorArgb
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = lineColorArgb
    }

    private val scratch = android.graphics.Point()
    private val arrowPath = Path()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val widthPx = mapView.width
        if (widthPx <= 0) return
        val projection = mapView.projection ?: return
        val heightPx = mapView.height.coerceAtLeast(1)

        // Screen corners (not MapView.boundingBox) so rotation does not skew lon span.
        val bounds = geoBoundsFromViewport(projection, widthPx, heightPx)
        val latSpan = (bounds.latNorth - bounds.latSouth).coerceAtLeast(1e-9)
        val latSouthDraw = bounds.latSouth - latSpan * LAT_OVERSHOOT_RATIO
        val latNorthDraw = bounds.latNorth + latSpan * LAT_OVERSHOOT_RATIO
        val longitudes = meridianLongitudesForViewport(
            projection = projection,
            widthPx = widthPx,
            heightPx = heightPx,
            lonWest = bounds.lonWest,
            lonEast = bounds.lonEast
        )
        for (drawLon in longitudes) {
            drawMeridian(canvas, projection, drawLon, latSouthDraw, latNorthDraw)
        }
    }

    private fun drawMeridian(
        canvas: Canvas,
        projection: Projection,
        lon: Double,
        latSouth: Double,
        latNorth: Double
    ) {
        projection.toPixels(GeoPoint(latSouth, lon), scratch)
        val xSouth = scratch.x.toFloat()
        val ySouth = scratch.y.toFloat()
        projection.toPixels(GeoPoint(latNorth, lon), scratch)
        val xNorth = scratch.x.toFloat()
        val yNorth = scratch.y.toFloat()
        canvas.drawLine(xSouth, ySouth, xNorth, yNorth, strokePaint)
        val segLen = hypot((xNorth - xSouth).toDouble(), (yNorth - ySouth).toDouble()).toFloat()
        for (along in meridianArrowAlongFractions(segLen)) {
            drawNorthArrow(canvas, xSouth, ySouth, xNorth, yNorth, along)
        }
    }

    /**
     * Filled triangle on the meridian at fraction [along] from south→north, tip pointing north.
     */
    private fun drawNorthArrow(
        canvas: Canvas,
        xSouth: Float,
        ySouth: Float,
        xNorth: Float,
        yNorth: Float,
        along: Float
    ) {
        val dx = xNorth - xSouth
        val dy = yNorth - ySouth
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < MIN_MERIDIAN_PX_FOR_ARROWS) return
        val ux = dx / len
        val uy = dy / len
        val tipX = xSouth + dx * along
        val tipY = ySouth + dy * along
        val size = ARROW_HEAD_SIZE_PX
        val baseX = tipX - ux * size
        val baseY = tipY - uy * size
        val half = size * 0.55f
        val px = -uy * half
        val py = ux * half
        arrowPath.reset()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(baseX + px, baseY + py)
        arrowPath.lineTo(baseX - px, baseY - py)
        arrowPath.close()
        canvas.drawPath(arrowPath, fillPaint)
    }

    companion object {
        /** Visible air-leg stroke on map overlays ([RouteMapOverlay] outline 5.5f). */
        const val AIR_LEG_STROKE_PX = 5.5f
        /** North meridian line — at least half air-leg width for phone readability. */
        const val NORTH_LINE_STROKE_PX = 3.5f

        /** Screen px between O-map style north arrowheads (~4× sparser than dense guides). */
        const val ARROW_SPACING_PX = 320f
        const val ARROW_HEAD_SIZE_PX = 13f
        private const val MIN_MERIDIAN_PX_FOR_ARROWS = 48f

        /**
         * Fractions 0..1 south→north where arrowheads should sit for a meridian [segmentLengthPx] long.
         */
        fun meridianArrowAlongFractions(segmentLengthPx: Float): List<Float> {
            if (segmentLengthPx < MIN_MERIDIAN_PX_FOR_ARROWS) return emptyList()
            val inset = ARROW_SPACING_PX * 0.35f
            val usable = segmentLengthPx - inset * 2f
            if (usable < ARROW_SPACING_PX * 0.45f) return emptyList()
            val count = (usable / ARROW_SPACING_PX).toInt().coerceAtLeast(1)
            val step = usable / (count + 1)
            return List(count) { i ->
                (inset + step * (i + 1)) / segmentLengthPx
            }
        }

        /** Extend meridians past the visible map band (O-map style). */
        private const val LAT_OVERSHOOT_RATIO = 0.35
        /** Hard cap — orienteering-style sparse guides. */
        const val MAX_LINES = 4
        /** ~3 lines across → 2 gaps as the target density. */
        private const val TARGET_GAPS = 2
        const val MIN_LINES = 2

        /**
         * Evenly spaced longitudes covering the full [lonWest, lonEast] span.
         * Pure / testable — does not stop early on the west edge.
         */
        fun longitudesAcrossSpan(
            lonWest: Double,
            lonEast: Double,
            lineCount: Int
        ): List<Double> {
            var west = lonWest
            var east = lonEast
            if (east < west) east += 360.0
            val span = (east - west).coerceAtLeast(1e-9)
            val n = lineCount.coerceAtLeast(1)
            if (n == 1) return listOf(normalizeLon(west + span * 0.5))
            val step = span / (n - 1)
            return List(n) { i -> normalizeLon(west + step * i) }
        }

        /**
         * Longitude values for north meridians across the visible lon span.
         * Line count comes from desired ~screen spacing, then positions span west→east fully.
         */
        fun meridianLongitudesForViewport(
            projection: Projection,
            widthPx: Int,
            heightPx: Int,
            lonWest: Double,
            lonEast: Double,
            maxLines: Int = MAX_LINES
        ): List<Double> {
            var west = lonWest
            var east = lonEast
            if (east < west) east += 360.0
            val span = (east - west).coerceAtLeast(1e-9)
            val lineCount = lineCountForViewport(projection, widthPx, heightPx, span, maxLines)
            return longitudesAcrossSpan(west, east, lineCount)
        }

        fun lineCountForViewport(
            projection: Projection,
            widthPx: Int,
            heightPx: Int,
            spanDeg: Double,
            maxLines: Int = MAX_LINES
        ): Int {
            val pixelStep = lonStepForViewport(projection, widthPx, heightPx, spanDeg)
            // How many lines if we kept ~pixelStep across the full span?
            val fromPixels = ((spanDeg / pixelStep).roundToInt() + 1)
                .coerceIn(MIN_LINES, maxLines.coerceAtLeast(MIN_LINES))
            return fromPixels
        }

        /**
         * Geo bounds of what is on screen (handles map rotation; unlike [MapView.boundingBox]).
         */
        fun geoBoundsFromViewport(
            projection: Projection,
            widthPx: Int,
            heightPx: Int
        ): ViewportGeoBounds {
            val w = widthPx.coerceAtLeast(1)
            val h = heightPx.coerceAtLeast(1)
            val xs = intArrayOf(0, w, w, 0)
            val ys = intArrayOf(0, 0, h, h)
            var latSouth = Double.POSITIVE_INFINITY
            var latNorth = Double.NEGATIVE_INFINITY
            var lonWest = Double.POSITIVE_INFINITY
            var lonEast = Double.NEGATIVE_INFINITY
            for (i in 0..3) {
                val gp = projection.fromPixels(xs[i], ys[i])
                latSouth = minOf(latSouth, gp.latitude)
                latNorth = maxOf(latNorth, gp.latitude)
                lonWest = minOf(lonWest, gp.longitude)
                lonEast = maxOf(lonEast, gp.longitude)
            }
            return ViewportGeoBounds(
                latSouth = latSouth,
                latNorth = latNorth,
                lonWest = lonWest,
                lonEast = lonEast
            )
        }

        fun lonStepForViewport(
            projection: Projection,
            widthPx: Int,
            heightPx: Int,
            spanDeg: Double
        ): Double {
            val spacingPx = (widthPx.toFloat() / TARGET_GAPS).coerceIn(90f, 200f)
            val midY = (heightPx / 2).coerceAtLeast(0)
            val left = projection.fromPixels(0, midY)
            val right = projection.fromPixels(spacingPx.toInt().coerceAtLeast(1), midY)
            var step = abs(right.longitude - left.longitude)
            if (step < 1e-9) {
                step = spanDeg / TARGET_GAPS
            }
            // Prefer denser spacing when zoomed in, but never wider than ~half the span.
            val maxStep = spanDeg / (MIN_LINES - 1).coerceAtLeast(1)
            if (step > maxStep) step = maxStep
            return step.coerceAtLeast(1e-9)
        }

        private fun normalizeLon(lon: Double): Double {
            var x = lon
            while (x > 180.0) x -= 360.0
            while (x < -180.0) x += 360.0
            return x
        }
    }

    data class ViewportGeoBounds(
        val latSouth: Double,
        val latNorth: Double,
        val lonWest: Double,
        val lonEast: Double
    )
}
