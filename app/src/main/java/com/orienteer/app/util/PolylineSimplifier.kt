package com.orienteer.app.util

import com.orienteer.app.data.model.GeoPoint

/**
 * Reduces polyline point count for map rendering using the Douglas-Peucker algorithm.
 * Geographic accuracy is approximate (planar projection) — suitable for simplification
 * tolerances of a few metres on typical running routes.
 */
object PolylineSimplifier {

    const val DEFAULT_TOLERANCE_M = 8.0

    fun simplify(points: List<GeoPoint>, toleranceM: Double = DEFAULT_TOLERANCE_M): List<GeoPoint> {
        if (points.size <= 2) return points
        return douglasPeucker(points, toleranceM)
    }

    private fun douglasPeucker(points: List<GeoPoint>, toleranceM: Double): List<GeoPoint> {
        if (points.size < 3) return points

        var maxDist = 0.0
        var index = 0
        val end = points.lastIndex
        for (i in 1 until end) {
            val d = perpendicularDistanceM(points[i], points.first(), points[end])
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }

        return if (maxDist > toleranceM) {
            val left = douglasPeucker(points.subList(0, index + 1), toleranceM)
            val right = douglasPeucker(points.subList(index, points.size), toleranceM)
            left.dropLast(1) + right
        } else {
            listOf(points.first(), points.last())
        }
    }

    private fun perpendicularDistanceM(point: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude
        if (dx == 0.0 && dy == 0.0) {
            return GeoUtils.distanceMeters(point, lineStart)
        }
        val t = ((point.longitude - lineStart.longitude) * dx +
            (point.latitude - lineStart.latitude) * dy) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(0.0, 1.0)
        val projected = GeoPoint(
            lineStart.latitude + clamped * dy,
            lineStart.longitude + clamped * dx
        )
        return GeoUtils.distanceMeters(point, projected)
    }
}
