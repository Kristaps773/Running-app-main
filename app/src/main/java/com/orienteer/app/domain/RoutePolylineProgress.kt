package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils

/**
 * Project a GPS fix onto a walking polyline for active-run path lead.
 */
object RoutePolylineProgress {

    const val DEFAULT_BUCKET_SIZE_M = 70.0

    data class Progress(
        val segmentIndex: Int,
        /** Metres from polyline start along the path to the snap point. */
        val distanceAlongM: Double,
        val traveledDistanceM: Double,
        val remainingDistanceM: Double,
        /** Perpendicular distance from GPS to the snapped path point. */
        val distanceToPathM: Double,
        val snapPoint: GeoPoint
    )

    fun project(polyline: List<GeoPoint>, location: GeoPoint): Progress {
        if (polyline.isEmpty()) {
            return Progress(
                segmentIndex = 0,
                distanceAlongM = 0.0,
                traveledDistanceM = 0.0,
                remainingDistanceM = 0.0,
                distanceToPathM = 0.0,
                snapPoint = location
            )
        }
        if (polyline.size == 1) {
            val d = GeoUtils.distanceMeters(location, polyline.first())
            return Progress(
                segmentIndex = 0,
                distanceAlongM = 0.0,
                traveledDistanceM = 0.0,
                remainingDistanceM = 0.0,
                distanceToPathM = d,
                snapPoint = polyline.first()
            )
        }

        var bestDist = Double.POSITIVE_INFINITY
        var bestSeg = 0
        var bestPoint = polyline.first()
        var prefix = 0.0
        var bestAlong = 0.0

        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val len = GeoUtils.distanceMeters(a, b)
            val (t, closest) = closestOnSegment(location, a, b)
            val d = GeoUtils.distanceMeters(location, closest)
            if (d < bestDist) {
                bestDist = d
                bestSeg = i
                bestPoint = closest
                bestAlong = prefix + t * len
            }
            prefix += len
        }

        val total = prefix
        val along = bestAlong.coerceIn(0.0, total)
        return Progress(
            segmentIndex = bestSeg,
            distanceAlongM = along,
            traveledDistanceM = along,
            remainingDistanceM = (total - along).coerceAtLeast(0.0),
            distanceToPathM = if (bestDist.isFinite()) bestDist else 0.0,
            snapPoint = bestPoint
        )
    }

    fun progressBucket(distanceAlongM: Double, bucketSizeM: Double = DEFAULT_BUCKET_SIZE_M): Int {
        val size = bucketSizeM.coerceAtLeast(1.0)
        return (distanceAlongM.coerceAtLeast(0.0) / size).toInt()
    }

    /**
     * 0 at the runner’s snap (start of remaining), 1 at the end of the remaining path.
     */
    fun remainingColorT(distanceAlongPointM: Double, progress: Progress): Double {
        val rem = progress.remainingDistanceM
        if (rem <= 1e-3) return 1.0
        val fromSnap = (distanceAlongPointM - progress.distanceAlongM).coerceIn(0.0, rem)
        return (fromSnap / rem).coerceIn(0.0, 1.0)
    }

    fun remainingPoints(polyline: List<GeoPoint>, progress: Progress): List<GeoPoint> {
        if (polyline.size < 2) return polyline
        val start = progress.snapPoint
        val idx = progress.segmentIndex.coerceIn(0, polyline.lastIndex - 1)
        val out = ArrayList<GeoPoint>(polyline.size - idx + 1)
        out.add(start)
        for (i in (idx + 1)..polyline.lastIndex) {
            out.add(polyline[i])
        }
        if (out.size == 1) out.add(polyline.last())
        return out
    }

    fun pathLengthMeters(polyline: List<GeoPoint>): Double {
        if (polyline.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until polyline.lastIndex) {
            total += GeoUtils.distanceMeters(polyline[i], polyline[i + 1])
        }
        return total
    }

    /**
     * Closest point on segment AB to [p], with t in [0,1] along AB.
     * Uses local ENU metres approximation around A.
     */
    private fun closestOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<Double, GeoPoint> {
        val ax = 0.0
        val ay = 0.0
        val (bx, by) = toLocalMeters(a, b)
        val (px, py) = toLocalMeters(a, p)
        val abx = bx - ax
        val aby = by - ay
        val abLen2 = abx * abx + aby * aby
        if (abLen2 < 1e-6) return 0.0 to a
        val t = ((px - ax) * abx + (py - ay) * aby) / abLen2
        val tc = t.coerceIn(0.0, 1.0)
        val cx = ax + abx * tc
        val cy = ay + aby * tc
        return tc to fromLocalMeters(a, cx, cy)
    }

    private fun toLocalMeters(origin: GeoPoint, p: GeoPoint): Pair<Double, Double> {
        val latRad = Math.toRadians(origin.latitude)
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * kotlin.math.cos(latRad)
        val x = (p.longitude - origin.longitude) * metersPerDegLon
        val y = (p.latitude - origin.latitude) * metersPerDegLat
        return x to y
    }

    private fun fromLocalMeters(origin: GeoPoint, x: Double, y: Double): GeoPoint {
        val latRad = Math.toRadians(origin.latitude)
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * kotlin.math.cos(latRad).coerceAtLeast(1e-6)
        return GeoPoint(
            latitude = origin.latitude + y / metersPerDegLat,
            longitude = origin.longitude + x / metersPerDegLon
        )
    }
}
