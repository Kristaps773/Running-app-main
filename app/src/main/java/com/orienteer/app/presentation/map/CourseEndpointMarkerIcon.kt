package com.orienteer.app.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Course-picker Start / Finish glyphs — same shape language as map overprint,
 * same badge strokes as [CheckpointMarkerIcon] when classic IOF is off.
 *
 * - Start = triangle (IOF 701)
 * - Finish = double circle (IOF 705)
 * - Combined = both (return-to-start / “Same place”)
 */
object CourseEndpointMarkerIcon {

    enum class Kind { Start, Finish, Combined }

    /** Match [CheckpointMarkerIcon] disc size. */
    private const val SIZE_DP = 42f
    private const val STROKE_DP = 3f
    private const val SUPERSAMPLE = 3

    /** Alpine Trail CTA / non-IOF badge fill. */
    const val APP_BADGE_ARGB: Int = 0xFF1B5E20.toInt()

    fun create(
        context: Context,
        kind: Kind,
        /** True → purple stroke overprint; false → filled forest badges. */
        useIofStyle: Boolean,
        selected: Boolean = false,
        scale: Float = 1f
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val s = scale.coerceIn(0.75f, 1.35f)
        val padDp = if (selected) 5f else 0f
        val displayPx = ((SIZE_DP + padDp * 2f) * density * s).toInt().coerceAtLeast(28)
        val hi = displayPx * SUPERSAMPLE
        val raw = Bitmap.createBitmap(hi, hi, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(raw)
        val c = hi / 2f
        val ss = SUPERSAMPLE.toFloat()
        val padPx = padDp * density * s * ss
        val strokePx = (STROKE_DP * density * s * ss).coerceAtLeast(4f)
        val accent = if (useIofStyle) IOF_PURPLE_ARGB else APP_BADGE_ARGB

        if (selected) {
            val selectHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (4.5f * density * s * ss).coerceAtLeast(3f)
                color = Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent))
            }
            val selectRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (2.5f * density * s * ss).coerceAtLeast(2f)
                color = Color.WHITE
            }
            val ringR = c - padPx * 0.35f
            canvas.drawCircle(c, c, ringR, selectHalo)
            canvas.drawCircle(c, c, ringR - selectHalo.strokeWidth * 0.25f, selectRing)
        }

        val glyphR = (c - padPx - strokePx) * 0.92f

        if (useIofStyle) {
            drawIof(canvas, c, glyphR, strokePx, kind)
        } else {
            drawAppBadge(canvas, c, glyphR, strokePx, kind)
        }

        val scaled = Bitmap.createScaledBitmap(raw, displayPx, displayPx, true)
        if (scaled !== raw) raw.recycle()
        return BitmapDrawable(context.resources, scaled).apply {
            setBounds(0, 0, displayPx, displayPx)
        }
    }

    /** Stroke-only purple — matches [IofCourseOverprintOverlay]. */
    private fun drawIof(
        canvas: Canvas,
        center: Float,
        radius: Float,
        strokePx: Float,
        kind: Kind
    ) {
        // Soft white under-stroke (same idea as air-leg halo) for tile contrast.
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx * 1.55f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = IOF_PURPLE_ARGB
        }
        when (kind) {
            Kind.Start -> {
                val path = trianglePath(center, radius)
                canvas.drawPath(path, halo)
                canvas.drawPath(path, ink)
            }
            Kind.Finish -> {
                canvas.drawCircle(center, center, radius, halo)
                canvas.drawCircle(center, center, radius * 0.72f, halo)
                canvas.drawCircle(center, center, radius, ink)
                canvas.drawCircle(center, center, radius * 0.72f, ink)
            }
            Kind.Combined -> {
                canvas.drawCircle(center, center, radius, halo)
                canvas.drawCircle(center, center, radius * 0.72f, halo)
                canvas.drawCircle(center, center, radius, ink)
                canvas.drawCircle(center, center, radius * 0.72f, ink)
                val path = trianglePath(center, radius * 0.48f)
                canvas.drawPath(path, halo)
                canvas.drawPath(path, ink)
            }
        }
    }

    /** Filled forest + white collar — matches [CheckpointMarkerIcon] badge language. */
    private fun drawAppBadge(
        canvas: Canvas,
        center: Float,
        radius: Float,
        strokePx: Float,
        kind: Kind
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = APP_BADGE_ARGB
        }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }
        when (kind) {
            Kind.Start -> {
                val path = trianglePath(center, radius)
                canvas.drawPath(path, fill)
                canvas.drawPath(path, white)
            }
            Kind.Finish -> {
                canvas.drawCircle(center, center, radius, fill)
                canvas.drawCircle(center, center, radius - strokePx / 2f, white)
                // Inner ring reads as IOF double-circle while staying a filled badge.
                val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx * 0.85f
                    color = Color.WHITE
                }
                canvas.drawCircle(center, center, radius * 0.55f, inner)
            }
            Kind.Combined -> {
                canvas.drawCircle(center, center, radius, fill)
                canvas.drawCircle(center, center, radius - strokePx / 2f, white)
                val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx * 0.75f
                    color = Color.WHITE
                }
                canvas.drawCircle(center, center, radius * 0.62f, inner)
                val path = trianglePath(center, radius * 0.42f)
                val triFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.WHITE
                }
                canvas.drawPath(path, triFill)
            }
        }
    }

    private fun trianglePath(center: Float, radius: Float): Path {
        val path = Path()
        for (i in 0..2) {
            val angle = Math.toRadians((-90.0 + i * 120.0))
            val x = center + radius * cos(angle).toFloat()
            val y = center + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
