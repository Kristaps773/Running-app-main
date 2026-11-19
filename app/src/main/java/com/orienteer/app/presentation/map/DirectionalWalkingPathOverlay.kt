package com.orienteer.app.presentation.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Shader
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.domain.RoutePolylineProgress
import com.orienteer.app.util.GeoUtils
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import kotlin.math.ceil
import kotlin.math.max

/**
 * Full course footpath at low visibility, plus a lead ribbon from the runner
 * to the next checkpoint center (bright at runner → quieter toward the CP).
 */
class DirectionalWalkingPathOverlay(
    private val polyline: List<GeoPoint>,
    private val leadRibbon: RoutePolylineProgress.LeadRibbon,
    private val paintColors: WalkingPathPaint
) : Overlay() {

    private val baseHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = fadeArgb(paintColors.haloArgb, BASE_HALO_ALPHA)
    }
    private val baseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = fadeArgb(paintColors.strokeArgb, BASE_STROKE_ALPHA)
    }
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
    private val screenA = Point()
    private val screenB = Point()
    private val basePath = Path()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || polyline.size < 2) return
        val projection = mapView.projection ?: return
        drawFullCourseDim(canvas, projection)
        if (leadRibbon.points.size >= 2) {
            drawRibbon(canvas, projection, leadRibbon)
        }
    }

    /** Whole planned path as one solid stroke, quiet so the lead stays readable. */
    private fun drawFullCourseDim(canvas: Canvas, projection: Projection) {
        basePath.reset()
        projection.toPixels(
            OsmGeoPoint(polyline.first().latitude, polyline.first().longitude),
            screen
        )
        basePath.moveTo(screen.x.toFloat(), screen.y.toFloat())
        for (i in 1..polyline.lastIndex) {
            val p = polyline[i]
            projection.toPixels(OsmGeoPoint(p.latitude, p.longitude), screen)
            basePath.lineTo(screen.x.toFloat(), screen.y.toFloat())
        }
        canvas.drawPath(basePath, baseHaloPaint)
        canvas.drawPath(basePath, baseStrokePaint)
    }

    private fun drawRibbon(
        canvas: Canvas,
        projection: Projection,
        ribbon: RoutePolylineProgress.LeadRibbon
    ) {
        var along = 0.0
        val pts = ribbon.points
        for (i in 0 until pts.lastIndex) {
            val a = pts[i]
            val b = pts[i + 1]
            val segLen = GeoUtils.distanceMeters(a, b)
            if (segLen < 0.05) {
                along += segLen
                continue
            }
            val steps = max(1, ceil(segLen / STEP_M).toInt())
            val stepLen = segLen / steps
            for (s in 0 until steps) {
                val t0 = s.toDouble() / steps
                val t1 = (s + 1).toDouble() / steps
                val p0 = lerpGeo(a, b, t0)
                val p1 = lerpGeo(a, b, t1)
                val along0 = along + stepLen * s
                val along1 = along0 + stepLen
                drawGradientStep(
                    canvas,
                    projection,
                    p0,
                    p1,
                    fadeT(along0, ribbon.runnerOffsetM),
                    fadeT(along1, ribbon.runnerOffsetM)
                )
            }
            along += segLen
        }
    }

    private fun fadeT(alongOnRibbonM: Double, runnerOffsetM: Double): Float {
        val delta = alongOnRibbonM - runnerOffsetM
        if (delta < 0.0) return 1f
        val window = RoutePolylineProgress.LEAD_LOOKAHEAD_M.coerceAtLeast(1.0)
        return (delta / window).toFloat().coerceIn(0f, 1f)
    }

    private fun drawGradientStep(
        canvas: Canvas,
        projection: Projection,
        a: GeoPoint,
        b: GeoPoint,
        t0: Float,
        t1: Float
    ) {
        projection.toPixels(OsmGeoPoint(a.latitude, a.longitude), screenA)
        projection.toPixels(OsmGeoPoint(b.latitude, b.longitude), screenB)
        val x0 = screenA.x.toFloat()
        val y0 = screenA.y.toFloat()
        val x1 = screenB.x.toFloat()
        val y1 = screenB.y.toFloat()
        if (x0 == x1 && y0 == y1) return

        haloPaint.shader = LinearGradient(
            x0, y0, x1, y1, haloColor(t0), haloColor(t1), Shader.TileMode.CLAMP
        )
        strokePaint.shader = LinearGradient(
            x0, y0, x1, y1, strokeColor(t0), strokeColor(t1), Shader.TileMode.CLAMP
        )
        canvas.drawLine(x0, y0, x1, y1, haloPaint)
        canvas.drawLine(x0, y0, x1, y1, strokePaint)
        haloPaint.shader = null
        strokePaint.shader = null
    }

    private fun strokeColor(t: Float): Int {
        val factor = 1.0f - (0.62f * t)
        return mixStroke(paintColors.strokeArgb, factor)
    }

    private fun haloColor(t: Float): Int {
        val alphaFactor = 1.0f - (0.50f * t)
        return fadeArgb(paintColors.haloArgb, alphaFactor.coerceIn(0.35f, 1f))
    }

    private fun mixStroke(argb: Int, factor: Float): Int {
        val f = factor.coerceIn(0.38f, 1.12f)
        fun ch(v: Int): Int {
            val lit = if (f > 1f) {
                v + ((255 - v) * ((f - 1f) / 0.12f)).toInt()
            } else {
                (v * f).toInt()
            }
            return lit.coerceIn(0, 255)
        }
        return Color.argb(Color.alpha(argb), ch(Color.red(argb)), ch(Color.green(argb)), ch(Color.blue(argb)))
    }

    private fun fadeArgb(argb: Int, alphaFactor: Float): Int {
        val a = (Color.alpha(argb) * alphaFactor.coerceIn(0.15f, 1f)).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(argb), Color.green(argb), Color.blue(argb))
    }

    private fun lerpGeo(a: GeoPoint, b: GeoPoint, t: Double): GeoPoint =
        GeoPoint(
            latitude = a.latitude + (b.latitude - a.latitude) * t,
            longitude = a.longitude + (b.longitude - a.longitude) * t
        )

    companion object {
        /** Short samples so LinearGradient blends read as a smooth solid, not color blocks. */
        private const val STEP_M = 2.0
        private const val BASE_HALO_ALPHA = 0.22f
        private const val BASE_STROKE_ALPHA = 0.28f
    }
}
