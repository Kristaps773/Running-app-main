package com.orienteer.app.presentation.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.domain.RoutePolylineProgress
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.util.GeoPoint as OsmGeoPoint

/**
 * Active-run footpath lead: dim behind the runner, bright→quiet gradient on the remaining path.
 * Uses existing basemap stroke/halo hues (lightness/alpha only).
 */
class DirectionalWalkingPathOverlay(
    private val polyline: List<GeoPoint>,
    private val progress: RoutePolylineProgress.Progress,
    private val paintColors: WalkingPathPaint
) : Overlay() {

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val screen = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || polyline.size < 2) return
        val projection = mapView.projection ?: return
        drawPath(canvas, projection)
    }

    private fun drawPath(canvas: Canvas, projection: Projection) {
        var along = 0.0
        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val segLen = com.orienteer.app.util.GeoUtils.distanceMeters(a, b)
            val midAlong = along + segLen * 0.5
            val (halo, stroke) = colorsForAlong(midAlong)
            haloPaint.color = halo
            strokePaint.color = stroke
            projection.toPixels(OsmGeoPoint(a.latitude, a.longitude), screen)
            val x1 = screen.x.toFloat()
            val y1 = screen.y.toFloat()
            projection.toPixels(OsmGeoPoint(b.latitude, b.longitude), screen)
            canvas.drawLine(x1, y1, screen.x.toFloat(), screen.y.toFloat(), haloPaint)
            canvas.drawLine(x1, y1, screen.x.toFloat(), screen.y.toFloat(), strokePaint)
            along += segLen
        }
    }

    private fun colorsForAlong(distanceAlongM: Double): Pair<Int, Int> {
        val behind = distanceAlongM < progress.distanceAlongM - 0.5
        if (behind) {
            return fadeArgb(paintColors.haloArgb, 0.35f) to fadeArgb(paintColors.strokeArgb, 0.40f)
        }
        val t = RoutePolylineProgress.remainingColorT(distanceAlongM, progress)
        // t=0 near runner (brighter), t=1 far (quieter)
        val strokeFactor = 1.0f - (0.55f * t.toFloat())
        val haloFactor = 1.0f - (0.45f * t.toFloat())
        return fadeArgb(paintColors.haloArgb, haloFactor) to
            brightenArgb(paintColors.strokeArgb, strokeFactor)
    }

    private fun fadeArgb(argb: Int, alphaFactor: Float): Int {
        val a = (Color.alpha(argb) * alphaFactor.coerceIn(0.15f, 1f)).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(argb), Color.green(argb), Color.blue(argb))
    }

    /** Keep hue; scale channel brightness toward white when factor > ~0.85 near runner. */
    private fun brightenArgb(argb: Int, factor: Float): Int {
        val f = factor.coerceIn(0.35f, 1.15f)
        fun ch(v: Int): Int {
            val lit = if (f > 1f) {
                v + ((255 - v) * (f - 1f) / 0.15f).toInt()
            } else {
                (v * f).toInt()
            }
            return lit.coerceIn(0, 255)
        }
        return Color.argb(Color.alpha(argb), ch(Color.red(argb)), ch(Color.green(argb)), ch(Color.blue(argb)))
    }
}
