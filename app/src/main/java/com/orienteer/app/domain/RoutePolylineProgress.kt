package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils

/**
 * Project a GPS fix onto a walking polyline for active-run path lead.
 */
object RoutePolylineProgress {

    const val DEFAULT_BUCKET_SIZE_M = 70.0

    /** Bright lead fades out over this many metres ahead of the runner (not the whole course). */
    const val LEAD_LOOKAHEAD_M = 180.0

    /** Short dim trail behind the runner on the same continuous ribbon. */
    const val LEAD_BEHIND_M = 25.0

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
     * Lead fade along a short lookahead window:
     * - behind runner → 1 (dim)
     * - at snap → 0 (bright)
     * - at snap+[LEAD_LOOKAHEAD_M] and beyond → 1 (quiet)
     */
    fun leadFadeT(
        distanceAlongPointM: Double,
        progress: Progress,
        lookaheadM: Double = LEAD_LOOKAHEAD_M
    ): Double {
        val window = lookaheadM.coerceAtLeast(1.0)
        val delta = distanceAlongPointM - progress.distanceAlongM
        if (delta < 0.0) return 1.0
        if (delta >= window) return 1.0
        return (delta / window).coerceIn(0.0, 1.0)
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

    /**
     * One continuous local path object around the runner (behind + ahead along route order).
     * Avoids drawing the full course so out-and-back / reused roads do not appear as
     * overlapping parallel strokes.
     */
    data class LeadRibbon(
        val points: List<GeoPoint>,
        /** Metres along [points] from ribbon start to the runner snap. */
        val runnerOffsetM: Double,
        val runnerPoint: GeoPoint
    )

    fun activeLeadRibbon(
        polyline: List<GeoPoint>,
        progress: Progress,
        behindM: Double = LEAD_BEHIND_M,
        aheadM: Double = LEAD_LOOKAHEAD_M
    ): LeadRibbon {
        if (polyline.size < 2) {
            val p = polyline.firstOrNull() ?: progress.snapPoint
            return LeadRibbon(listOf(p, p), runnerOffsetM = 0.0, runnerPoint = progress.snapPoint)
        }
        val total = pathLengthMeters(polyline)
        val fromM = (progress.distanceAlongM - behindM.coerceAtLeast(0.0)).coerceIn(0.0, total)
        val toM = (progress.distanceAlongM + aheadM.coerceAtLeast(1.0)).coerceIn(0.0, total)
        val points = sliceAlongMeters(polyline, fromM, toM)
        val runnerOffset = (progress.distanceAlongM - fromM).coerceAtLeast(0.0)
        return LeadRibbon(
            points = points,
            runnerOffsetM = runnerOffset,
            runnerPoint = progress.snapPoint
        )
    }

    fun pathLengthMeters(polyline: List<GeoPoint>): Double {
        if (polyline.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until polyline.lastIndex) {
            total += GeoUtils.distanceMeters(polyline[i], polyline[i + 1])
        }
        return total
    }

    fun pointAlongMeters(polyline: List<GeoPoint>, distanceM: Double): GeoPoint {
        if (polyline.isEmpty()) return GeoPoint(0.0, 0.0)
        if (polyline.size == 1) return polyline.first()
        var remaining = distanceM.coerceAtLeast(0.0)
        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val len = GeoUtils.distanceMeters(a, b)
            if (remaining <= len || i == polyline.lastIndex - 1) {
                val t = if (len < 1e-6) 0.0 else (remaining / len).coerceIn(0.0, 1.0)
                return GeoPoint(
                    latitude = a.latitude + (b.latitude - a.latitude) * t,
                    longitude = a.longitude + (b.longitude - a.longitude) * t
                )
            }
            remaining -= len
        }
        return polyline.last()
    }

    fun sliceAlongMeters(polyline: List<GeoPoint>, fromM: Double, toM: Double): List<GeoPoint> {
        if (polyline.size < 2) return polyline
        val start = fromM.coerceAtMost(toM)
        val end = toM.coerceAtLeast(fromM)
        if (end - start < 0.5) {
            val p = pointAlongMeters(polyline, start)
            return listOf(p, pointAlongMeters(polyline, (start + 1.0).coerceAtMost(pathLengthMeters(polyline))))
        }
        val out = ArrayList<GeoPoint>()
        out.add(pointAlongMeters(polyline, start))
        var along = 0.0
        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val len = GeoUtils.distanceMeters(a, b)
            val nextAlong = along + len
            if (nextAlong > start && along < end) {
                // Include vertex b when it lies inside (start, end]
                if (nextAlong > start + 0.3 && nextAlong < end - 0.3) {
                    out.add(b)
                }
            }
            along = nextAlong
            if (along >= end) break
        }
        val endPt = pointAlongMeters(polyline, end)
        if (out.isEmpty() || GeoUtils.distanceMeters(out.last(), endPt) > 0.3) {
            out.add(endPt)
        }
        if (out.size == 1) out.add(endPt)
        return out
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
