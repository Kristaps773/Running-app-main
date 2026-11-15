package com.orienteer.app.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.presentation.map.IOF_PURPLE_ARGB
import com.orienteer.app.presentation.map.IofCourseGeometry
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 1 mm in PDF points (1 pt = 1/72 in). */
private const val PT_PER_MM = 72.0 / 25.4

/**
 * IOF-style course overprint drawn as **vectors on the PDF page** (not a screen overlay).
 * Symbol sizes are in millimetres on paper — same idea as Purple Pen / xcorso / OCAD export.
 */
object PdfVectorCourse {

    /** Forest ISOM: 75 m ground footprint → paper diameter mm at print ratio. */
    fun controlCircleDiameterMm(printRatio: Int): Double =
        (75_000.0 / printRatio.toDouble()).coerceIn(3.0, 14.0)

    /** ~0.35 mm course line (ISOM course marking). */
    fun courseLineWidthMm(): Double = 0.35

    fun printRatioForExport(
        exportScale: PdfExportScale,
        frameMm: MapFrameMm,
        metresPerPixel: Double,
        mapWidthPx: Int
    ): Int = when (exportScale) {
        is PdfExportScale.Fixed -> exportScale.scale.ratio
        PdfExportScale.FitToPage ->
            nicePrintRatio(
                PdfMapScaleBar.approximateRatio(metresPerPixel, mapWidthPx, frameMm.widthMm)
            )
    }

    /**
     * Map a view-pixel point through the center-crop into [mapRect] PDF coordinates.
     */
    fun viewPxToPdf(
        viewX: Float,
        viewY: Float,
        viewCrop: PdfRect,
        mapRect: PdfRect
    ): Pair<Float, Float> {
        val u = (viewX - viewCrop.left) / viewCrop.width().coerceAtLeast(0.01f)
        val v = (viewY - viewCrop.top) / viewCrop.height().coerceAtLeast(0.01f)
        return mapRect.left + u * mapRect.width() to mapRect.top + v * mapRect.height()
    }

    fun draw(
        canvas: Canvas,
        plan: IofCourseGeometry.CoursePlan,
        project: (GeoPoint) -> Pair<Float, Float>?,
        circleDiameterMm: Double,
        lineWidthMm: Double = courseLineWidthMm()
    ) {
        val purple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = IOF_PURPLE_ARGB
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = (lineWidthMm * PT_PER_MM).toFloat().coerceAtLeast(0.7f)
        }
        val fillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = IOF_PURPLE_ARGB
            style = Paint.Style.FILL
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val radiusPt = (circleDiameterMm / 2.0 * PT_PER_MM).toFloat()

        plan.legs.forEach { (from, to) ->
            val a = project(from) ?: return@forEach
            val b = project(to) ?: return@forEach
            canvas.drawLine(a.first, a.second, b.first, b.second, purple)
        }

        plan.symbols.forEach { symbol ->
            when (symbol) {
                is IofCourseGeometry.Symbol.Start -> {
                    val c = project(symbol.position) ?: return@forEach
                    drawStartTriangle(canvas, c.first, c.second, symbol.facingBearingDeg, radiusPt, purple)
                }
                is IofCourseGeometry.Symbol.Control -> {
                    val c = project(symbol.position) ?: return@forEach
                    canvas.drawCircle(c.first, c.second, radiusPt, purple)
                    fillText.textSize = (radiusPt * 0.95f).coerceIn(7f, 16f)
                    canvas.drawText(
                        symbol.number,
                        c.first,
                        c.second - radiusPt - fillText.textSize * 0.25f,
                        fillText
                    )
                }
                is IofCourseGeometry.Symbol.Finish -> {
                    val c = project(symbol.position) ?: return@forEach
                    canvas.drawCircle(c.first, c.second, radiusPt, purple)
                    canvas.drawCircle(c.first, c.second, radiusPt * 0.72f, purple)
                }
            }
        }
    }

    /**
     * North arrow in PDF points (map is north-up).
     */
    fun drawNorthArrow(canvas: Canvas, mapRect: PdfRect, sizeMm: Double = 8.0) {
        val size = (sizeMm * PT_PER_MM).toFloat()
        val pad = (2.0 * PT_PER_MM).toFloat()
        val cx = mapRect.right - pad - size * 0.45f
        val top = mapRect.top + pad
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F1A13.toInt()
            style = Paint.Style.FILL
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            textSize = size * 0.28f
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F1A13.toInt()
            style = Paint.Style.STROKE
            strokeWidth = size * 0.08f
        }
        val tipY = top
        val baseY = top + size * 0.55f
        val half = size * 0.28f
        val path = Path().apply {
            moveTo(cx, tipY)
            lineTo(cx + half, baseY)
            lineTo(cx - half, baseY)
            close()
        }
        canvas.drawPath(path, ink)
        canvas.drawLine(cx, baseY - 1f, cx, top + size * 0.78f, stroke)
        canvas.drawText("N", cx, top + size * 0.98f, ink)
    }

    private fun drawStartTriangle(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        facingBearingDeg: Double,
        radiusPt: Float,
        paint: Paint
    ) {
        // Equilateral-ish: apex toward next control (bearing from north, clockwise).
        val r = radiusPt * (2f / sqrt(3f))
        fun vertex(bearingDeg: Double): Pair<Float, Float> {
            val rad = Math.toRadians(bearingDeg)
            // Screen Y grows down; geographic bearing 0 = north = up = -Y.
            return cx + (r * sin(rad)).toFloat() to cy - (r * cos(rad)).toFloat()
        }
        val a = vertex(facingBearingDeg)
        val b = vertex(facingBearingDeg + 120.0)
        val c = vertex(facingBearingDeg - 120.0)
        val path = Path().apply {
            moveTo(a.first, a.second)
            lineTo(b.first, b.second)
            lineTo(c.first, c.second)
            close()
        }
        canvas.drawPath(path, paint)
    }
}
