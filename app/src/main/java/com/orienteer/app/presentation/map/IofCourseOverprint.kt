package com.orienteer.app.presentation.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.sqrt
import org.osmdroid.util.GeoPoint as OsmGeoPoint

/** IOF purple overprint (~PMS purple / ISOM course colour). */
const val IOF_PURPLE_ARGB: Int = 0xFFAD0078.toInt()

/** Nominal control circle diameter on the ground (≈ 7 mm at 1:10 000). */
const val IOF_CONTROL_DIAMETER_M: Double = 75.0

/**
 * Pure course geometry for classic IOF overprint (701 start, 703 controls, 705 finish).
 */
object IofCourseGeometry {

    sealed class Symbol {
        data class Start(val position: GeoPoint, val facingBearingDeg: Double) : Symbol()
        data class Control(val position: GeoPoint, val number: String) : Symbol()
        data class Finish(val position: GeoPoint) : Symbol()
    }

    data class CoursePlan(
        val symbols: List<Symbol>,
        val legs: List<Pair<GeoPoint, GeoPoint>>,
        /** Radius used to break legs at symbol edges (half of control diameter). */
        val trimRadiusM: Double
    )

    fun build(
        checkpoints: List<Checkpoint>,
        controlDiameterM: Double = IOF_CONTROL_DIAMETER_M
    ): CoursePlan {
        if (checkpoints.isEmpty()) {
            return CoursePlan(emptyList(), emptyList(), controlDiameterM / 2.0)
        }
        val firstStartIndex = checkpoints.indexOfFirst { it.isStart }.let { if (it < 0) 0 else it }
        val lastIndex = checkpoints.lastIndex
        val startPos = checkpoints[firstStartIndex].position
        val oneWayDistinct = checkpoints.size >= 2 &&
            checkpoints.first().isStart &&
            checkpoints.last().isStart &&
            (
                checkpoints.first().position.latitude != checkpoints.last().position.latitude ||
                    checkpoints.first().position.longitude != checkpoints.last().position.longitude
            )

        val symbols = ArrayList<Symbol>()
        val nextAfterStart = checkpoints.getOrNull(firstStartIndex + 1)?.position
            ?: checkpoints.getOrNull((firstStartIndex + 1) % checkpoints.size)?.position
        val facing = if (nextAfterStart != null) {
            GeoUtils.bearingDegrees(startPos, nextAfterStart)
        } else {
            0.0
        }
        symbols.add(Symbol.Start(startPos, facing))

        checkpoints.forEachIndexed { index, cp ->
            if (cp.isStart) return@forEachIndexed
            symbols.add(Symbol.Control(cp.position, cp.id.toString()))
        }

        if (oneWayDistinct) {
            symbols.add(Symbol.Finish(checkpoints[lastIndex].position))
        }

        val rawLegs = airLegSegmentsFromCheckpoints(checkpoints)
        val trim = controlDiameterM / 2.0
        val legs = rawLegs.mapNotNull { (from, to) -> shortenLeg(from, to, trim, trim) }

        return CoursePlan(symbols, legs, trim)
    }

    /**
     * Shorten a segment so it stops outside circular symbols of [startTrimM] / [endTrimM].
     * Returns null when the remaining segment would be tiny.
     */
    fun shortenLeg(
        from: GeoPoint,
        to: GeoPoint,
        startTrimM: Double,
        endTrimM: Double
    ): Pair<GeoPoint, GeoPoint>? {
        val dist = GeoUtils.distanceMeters(from, to)
        if (dist <= startTrimM + endTrimM + 2.0) return null
        val bearing = GeoUtils.bearingDegrees(from, to)
        val start = GeoUtils.offsetPoint(from, startTrimM, bearing)
        val end = GeoUtils.offsetPoint(to, endTrimM, (bearing + 180.0) % 360.0)
        return start to end
    }

    /** Distance from triangle centre to a vertex when side length equals [controlDiameterM]. */
    fun startTriangleVertexRadiusM(controlDiameterM: Double): Double =
        controlDiameterM / sqrt(3.0)
}

/**
 * Draws classic IOF course overprint into an OSMDroid [MapView].
 *
 * @param markerScale scales ground symbol size (PDF export uses &lt; 1).
 */
class IofCourseOverprintOverlay(
    private val checkpoints: List<Checkpoint>,
    private val purpleArgb: Int = IOF_PURPLE_ARGB,
    private val showLegs: Boolean = true,
    private val controlDiameterM: Double = IOF_CONTROL_DIAMETER_M,
    private val markerScale: Float = 1f
) : Overlay() {

    private val plan: IofCourseGeometry.CoursePlan =
        IofCourseGeometry.build(
            checkpoints,
            controlDiameterM = (controlDiameterM * markerScale.toDouble()).coerceIn(20.0, 120.0)
        )

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = purpleArgb
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = purpleArgb
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val path = Path()
    private val pointA = Point()
    private val pointB = Point()
    private val pointC = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val diameterM = (controlDiameterM * markerScale.toDouble()).coerceIn(20.0, 120.0)
        val radiusM = diameterM / 2.0

        strokePaint.strokeWidth = strokeWidthPx(mapView, diameterM)

        if (showLegs) {
            plan.legs.forEach { (from, to) ->
                projection.toPixels(OsmGeoPoint(from.latitude, from.longitude), pointA)
                projection.toPixels(OsmGeoPoint(to.latitude, to.longitude), pointB)
                canvas.drawLine(
                    pointA.x.toFloat(),
                    pointA.y.toFloat(),
                    pointB.x.toFloat(),
                    pointB.y.toFloat(),
                    strokePaint
                )
            }
        }

        plan.symbols.forEach { symbol ->
            when (symbol) {
                is IofCourseGeometry.Symbol.Start ->
                    drawStartTriangle(canvas, projection, symbol, diameterM)
                is IofCourseGeometry.Symbol.Control ->
                    drawControl(canvas, projection, symbol, radiusM)
                is IofCourseGeometry.Symbol.Finish ->
                    drawFinish(canvas, projection, symbol.position, radiusM)
            }
        }
    }

    private fun strokeWidthPx(mapView: MapView, diameterM: Double): Float {
        val center = mapView.mapCenter
        val at = GeoPoint(center.latitude, center.longitude)
        val px = metresToPixels(mapView.projection, at, diameterM * 0.06)
        return px.coerceIn(2.5f, 7f)
    }

    private fun drawStartTriangle(
        canvas: Canvas,
        projection: Projection,
        start: IofCourseGeometry.Symbol.Start,
        diameterM: Double
    ) {
        val r = IofCourseGeometry.startTriangleVertexRadiusM(diameterM)
        val apex = GeoUtils.offsetPoint(start.position, r, start.facingBearingDeg)
        val left = GeoUtils.offsetPoint(start.position, r, start.facingBearingDeg + 120.0)
        val right = GeoUtils.offsetPoint(start.position, r, start.facingBearingDeg - 120.0)
        projection.toPixels(OsmGeoPoint(apex.latitude, apex.longitude), pointA)
        projection.toPixels(OsmGeoPoint(left.latitude, left.longitude), pointB)
        projection.toPixels(OsmGeoPoint(right.latitude, right.longitude), pointC)
        path.reset()
        path.moveTo(pointA.x.toFloat(), pointA.y.toFloat())
        path.lineTo(pointB.x.toFloat(), pointB.y.toFloat())
        path.lineTo(pointC.x.toFloat(), pointC.y.toFloat())
        path.close()
        canvas.drawPath(path, strokePaint)
    }

    private fun drawControl(
        canvas: Canvas,
        projection: Projection,
        control: IofCourseGeometry.Symbol.Control,
        radiusM: Double
    ) {
        val center = control.position
        projection.toPixels(OsmGeoPoint(center.latitude, center.longitude), pointA)
        val radiusPx = metresToPixels(projection, center, radiusM)
        canvas.drawCircle(pointA.x.toFloat(), pointA.y.toFloat(), radiusPx, strokePaint)

        // Number north-up, just outside the circle.
        textPaint.textSize = (radiusPx * 0.9f).coerceIn(18f, 36f)
        val labelY = pointA.y - radiusPx - textPaint.textSize * 0.35f
        canvas.drawText(control.number, pointA.x.toFloat(), labelY, textPaint)
    }

    private fun drawFinish(
        canvas: Canvas,
        projection: Projection,
        position: GeoPoint,
        radiusM: Double
    ) {
        projection.toPixels(OsmGeoPoint(position.latitude, position.longitude), pointA)
        val outer = metresToPixels(projection, position, radiusM)
        val inner = outer * 0.72f
        canvas.drawCircle(pointA.x.toFloat(), pointA.y.toFloat(), outer, strokePaint)
        canvas.drawCircle(pointA.x.toFloat(), pointA.y.toFloat(), inner, strokePaint)
    }

    private fun metresToPixels(projection: Projection, at: GeoPoint, metres: Double): Float {
        val origin = OsmGeoPoint(at.latitude, at.longitude)
        val east = GeoUtils.offsetPoint(at, metres, 90.0)
        val scratchOrigin = Point()
        val scratchEast = Point()
        projection.toPixels(origin, scratchOrigin)
        projection.toPixels(OsmGeoPoint(east.latitude, east.longitude), scratchEast)
        val dx = (scratchEast.x - scratchOrigin.x).toDouble()
        val dy = (scratchEast.y - scratchOrigin.y).toDouble()
        return sqrt(dx * dx + dy * dy).toFloat().coerceAtLeast(4f)
    }
}
