package com.orienteer.app.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.presentation.map.AirLegCrossingGaps
import com.orienteer.app.presentation.map.CourseLegStroke
import com.orienteer.app.presentation.map.IOF_PURPLE_ARGB
import com.orienteer.app.presentation.map.IofCourseGeometry
import com.orienteer.app.presentation.map.NorthLinesOverlay
import kotlin.math.cos
import kotlin.math.hypot
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
        val strokeW = (lineWidthMm * PT_PER_MM).toFloat().coerceAtLeast(0.7f)
        val purple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = IOF_PURPLE_ARGB
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeW
        }
        val purpleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = IOF_PURPLE_ARGB
            style = Paint.Style.FILL
        }
        val fillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = IOF_PURPLE_ARGB
            style = Paint.Style.FILL
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val radiusPt = (circleDiameterMm / 2.0 * PT_PER_MM).toFloat()

        AirLegCrossingGaps.gappedPieces(plan.legs, symbols = plan.obstacles).forEach { piece ->
            val a = project(piece.from) ?: return@forEach
            val b = project(piece.to) ?: return@forEach
            val startCut = piece.startCutBearingDeg?.let {
                CourseLegStroke.screenDirFromBearing(piece.from, it, project)
            }
            val endCut = piece.endCutBearingDeg?.let {
                CourseLegStroke.screenDirFromBearing(piece.to, it, project)
            }
            canvas.drawPath(
                CourseLegStroke.beveledOutline(
                    x0 = a.first,
                    y0 = a.second,
                    x1 = b.first,
                    y1 = b.second,
                    halfWidth = strokeW * 0.5f,
                    startCutDir = startCut,
                    endCutDir = endCut
                ),
                purpleFill
            )
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
     * True-north meridians on the PDF. Lines are clipped to [mapRect]; arrowheads sit at
     * the north edge **just above** the map so they stay visible and clear of course symbols.
     */
    fun drawNorthMeridians(
        canvas: Canvas,
        mapRect: PdfRect,
        longitudes: List<Double>,
        latSouth: Double,
        latNorth: Double,
        project: (GeoPoint) -> Pair<Float, Float>?,
        colorArgb: Int = 0xD100695C.toInt()
    ) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.25f
            color = colorArgb
            strokeCap = Paint.Cap.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colorArgb
        }
        val arrow = Path()
        // Tip peeks into the page margin above the map (~2 mm).
        val tipAboveMap = (2.0 * PT_PER_MM).toFloat().coerceAtMost(mapRect.top - 1f)
        val arrowSize = (3.6 * PT_PER_MM).toFloat() // ~3.6 mm on paper

        for (lon in longitudes) {
            val a = project(GeoPoint(latSouth, lon)) ?: continue
            val b = project(GeoPoint(latNorth, lon)) ?: continue
            // Geographic north end has smaller PDF Y (page Y grows downward).
            val northIsB = b.second <= a.second
            val xN = if (northIsB) b.first else a.first
            val yN = if (northIsB) b.second else a.second
            val xS = if (northIsB) a.first else b.first
            val yS = if (northIsB) a.second else b.second
            val ndx = xN - xS
            val ndy = yN - yS
            val len = hypot(ndx.toDouble(), ndy.toDouble()).toFloat()
            if (len < 8f) continue
            val ux = ndx / len
            val uy = ndy / len

            // Clip stroke to the map frame (extended geo tips would be off-page).
            val xTop = xOnLineAtY(xS, yS, xN, yN, mapRect.top)
            val xBot = xOnLineAtY(xS, yS, xN, yN, mapRect.bottom)
            canvas.drawLine(xBot, mapRect.bottom, xTop, mapRect.top, stroke)

            // Arrow tip just above the map on this meridian (along true-north dir).
            val tipX = xTop + ux * tipAboveMap
            val tipY = mapRect.top + uy * tipAboveMap
            drawMeridianNorthArrowAt(
                canvas = canvas,
                path = arrow,
                fill = fill,
                tipX = tipX,
                tipY = tipY,
                ux = ux,
                uy = uy,
                size = arrowSize
            )
        }
    }

    /** X on segment (x0,y0)→(x1,y1) at a given Y (meridians are nearly vertical). */
    private fun xOnLineAtY(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        y: Float
    ): Float {
        val dy = y1 - y0
        if (kotlin.math.abs(dy) < 1e-3f) return (x0 + x1) * 0.5f
        val t = (y - y0) / dy
        return x0 + t * (x1 - x0)
    }

    /** Filled triangle; tip at ([tipX],[tipY]), pointing along unit ([ux],[uy]) toward north. */
    private fun drawMeridianNorthArrowAt(
        canvas: Canvas,
        path: Path,
        fill: Paint,
        tipX: Float,
        tipY: Float,
        ux: Float,
        uy: Float,
        size: Float
    ) {
        val baseX = tipX - ux * size
        val baseY = tipY - uy * size
        val half = size * 0.55f
        val px = -uy * half
        val py = ux * half
        path.reset()
        path.moveTo(tipX, tipY)
        path.lineTo(baseX + px, baseY + py)
        path.lineTo(baseX - px, baseY - py)
        path.close()
        canvas.drawPath(path, fill)
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
