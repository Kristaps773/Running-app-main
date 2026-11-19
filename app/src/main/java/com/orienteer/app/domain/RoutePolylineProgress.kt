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

    /** Small look-back when matching the next ordered waypoint onto a reused road. */
    const val ORDERED_LOOKBACK_M = 30.0

    /** Allow snap slightly past the next CP so arrival GPS noise does not stall. */
    const val ORDERED_AHEAD_SLACK_M = 40.0

    fun project(polyline: List<GeoPoint>, location: GeoPoint): Progress {
        return projectWithAmbiguity(polyline, location).best
    }

    /**
     * Active-run lead snap: nearest point only on the path **toward the next checkpoint
     * in course order** (previous CP → next CP window), not the global closest segment.
     */
    fun projectTowardNextCheckpoint(
        polyline: List<GeoPoint>,
        location: GeoPoint,
        checkpointPositions: List<GeoPoint>,
        nextCheckpointIdx: Int
    ): Progress {
        if (polyline.size < 2 || checkpointPositions.isEmpty()) {
            return project(polyline, location)
        }
        val idx = nextCheckpointIdx.coerceIn(0, checkpointPositions.lastIndex)
        val alongs = orderedWaypointAlongMeters(polyline, checkpointPositions)
        val total = pathLengthMeters(polyline)
        val fromM = if (idx > 0) alongs[idx - 1] else 0.0
        val toM = alongs[idx]
        val windowStart = (fromM - ORDERED_LOOKBACK_M).coerceAtLeast(0.0)
        val windowEnd = (toM + ORDERED_AHEAD_SLACK_M).coerceAtMost(total)
        return projectInWindow(polyline, location, windowStart, windowEnd.coerceAtLeast(windowStart + 1.0))
    }

    /**
     * Monotonic along-path metres for waypoints in course order (skips reverse/out-and-back twins).
     */
    fun orderedWaypointAlongMeters(
        polyline: List<GeoPoint>,
        waypoints: List<GeoPoint>
    ): DoubleArray {
        val out = DoubleArray(waypoints.size)
        if (polyline.size < 2 || waypoints.isEmpty()) return out
        var minAlong = 0.0
        for (i in waypoints.indices) {
            val p = projectFromMinAlong(
                polyline,
                waypoints[i],
                minAlongM = (minAlong - ORDERED_LOOKBACK_M).coerceAtLeast(0.0)
            )
            out[i] = p.distanceAlongM
            minAlong = p.distanceAlongM
        }
        return out
    }

    /**
     * Same as [project], plus the second-nearest segment (for snap flip / out-and-back debug).
     */
    data class AmbiguousProject(
        val best: Progress,
        val secondSegmentIndex: Int,
        val secondDistanceAlongM: Double,
        val secondDistanceToPathM: Double
    )

    fun projectWithAmbiguity(polyline: List<GeoPoint>, location: GeoPoint): AmbiguousProject {
        return projectInWindowWithAmbiguity(
            polyline,
            location,
            fromM = 0.0,
            toM = Double.POSITIVE_INFINITY
        )
    }

    fun projectInWindow(
        polyline: List<GeoPoint>,
        location: GeoPoint,
        fromM: Double,
        toM: Double
    ): Progress = projectInWindowWithAmbiguity(polyline, location, fromM, toM).best

    fun projectFromMinAlong(
        polyline: List<GeoPoint>,
        location: GeoPoint,
        minAlongM: Double
    ): Progress = projectInWindow(
        polyline,
        location,
        fromM = minAlongM,
        toM = Double.POSITIVE_INFINITY
    )

    private fun projectInWindowWithAmbiguity(
        polyline: List<GeoPoint>,
        location: GeoPoint,
        fromM: Double,
        toM: Double
    ): AmbiguousProject {
        if (polyline.isEmpty()) {
            val p = Progress(
                segmentIndex = 0,
                distanceAlongM = 0.0,
                traveledDistanceM = 0.0,
                remainingDistanceM = 0.0,
                distanceToPathM = 0.0,
                snapPoint = location
            )
            return AmbiguousProject(p, 0, 0.0, Double.POSITIVE_INFINITY)
        }
        if (polyline.size == 1) {
            val d = GeoUtils.distanceMeters(location, polyline.first())
            val p = Progress(
                segmentIndex = 0,
                distanceAlongM = 0.0,
                traveledDistanceM = 0.0,
                remainingDistanceM = 0.0,
                distanceToPathM = d,
                snapPoint = polyline.first()
            )
            return AmbiguousProject(p, 0, 0.0, Double.POSITIVE_INFINITY)
        }

        var bestDist = Double.POSITIVE_INFINITY
        var bestSeg = 0
        var bestPoint = polyline.first()
        var prefix = 0.0
        var bestAlong = 0.0
        var secondDist = Double.POSITIVE_INFINITY
        var secondSeg = 0
        var secondAlong = 0.0
        var anyInWindow = false

        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val len = GeoUtils.distanceMeters(a, b)
            val segStart = prefix
            val segEnd = prefix + len
            // Segment overlaps [fromM, toM]
            if (segEnd >= fromM && segStart <= toM) {
                val (t, closest) = closestOnSegment(location, a, b)
                var alongHere = prefix + t * len
                if (alongHere < fromM) {
                    alongHere = fromM
                }
                if (alongHere > toM) {
                    alongHere = toM
                }
                // Recompute closest if we clamped along onto the window edge
                val clampedPoint = if (alongHere == prefix + t * len) {
                    closest
                } else {
                    pointAlongMeters(polyline, alongHere)
                }
                val d = GeoUtils.distanceMeters(location, clampedPoint)
                anyInWindow = true
                if (d < bestDist) {
                    secondDist = bestDist
                    secondSeg = bestSeg
                    secondAlong = bestAlong
                    bestDist = d
                    bestSeg = i
                    bestPoint = clampedPoint
                    bestAlong = alongHere
                } else if (d < secondDist) {
                    secondDist = d
                    secondSeg = i
                    secondAlong = alongHere
                }
            }
            prefix += len
        }

        val total = prefix
        if (!anyInWindow) {
            // Empty / inverted window — fall back to global nearest (once).
            if (fromM > 0.0 || toM < total) {
                return projectInWindowWithAmbiguity(polyline, location, 0.0, total)
            }
            val fallback = pointAlongMeters(polyline, fromM.coerceIn(0.0, total))
            val d = GeoUtils.distanceMeters(location, fallback)
            val p = Progress(
                segmentIndex = 0,
                distanceAlongM = fromM.coerceIn(0.0, total),
                traveledDistanceM = fromM.coerceIn(0.0, total),
                remainingDistanceM = (total - fromM).coerceAtLeast(0.0),
                distanceToPathM = d,
                snapPoint = fallback
            )
            return AmbiguousProject(p, 0, p.distanceAlongM, Double.POSITIVE_INFINITY)
        }
        val along = bestAlong.coerceIn(0.0, total)
        val best = Progress(
            segmentIndex = bestSeg,
            distanceAlongM = along,
            traveledDistanceM = along,
            remainingDistanceM = (total - along).coerceAtLeast(0.0),
            distanceToPathM = if (bestDist.isFinite()) bestDist else 0.0,
            snapPoint = bestPoint
        )
        return AmbiguousProject(
            best = best,
            secondSegmentIndex = secondSeg,
            secondDistanceAlongM = secondAlong.coerceIn(0.0, total),
            secondDistanceToPathM = if (secondDist.isFinite()) secondDist else Double.POSITIVE_INFINITY
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
        aheadM: Double = LEAD_LOOKAHEAD_M,
        /** When set, do not draw the lead past this along-metres (next CP in order). */
        aheadCapAlongM: Double? = null
    ): LeadRibbon {
        if (polyline.size < 2) {
            val p = polyline.firstOrNull() ?: progress.snapPoint
            return LeadRibbon(listOf(p, p), runnerOffsetM = 0.0, runnerPoint = progress.snapPoint)
        }
        val total = pathLengthMeters(polyline)
        val fromM = (progress.distanceAlongM - behindM.coerceAtLeast(0.0)).coerceIn(0.0, total)
        val rawAhead = progress.distanceAlongM + aheadM.coerceAtLeast(1.0)
        val capped = aheadCapAlongM?.let { rawAhead.coerceAtMost(it) } ?: rawAhead
        val toM = capped.coerceIn(0.0, total).coerceAtLeast(fromM + 1.0)
        val points = sliceAlongMeters(polyline, fromM, toM)
        val runnerOffset = (progress.distanceAlongM - fromM).coerceAtLeast(0.0)
        return LeadRibbon(
            points = points,
            runnerOffsetM = runnerOffset,
            runnerPoint = progress.snapPoint
        )
    }

    /**
     * Short lead toward the next checkpoint in course order:
     * - normally only [LEAD_LOOKAHEAD_M] ahead along the footpath
     * - when the next CP is inside that window, stop at the checkpoint center
     */
    fun leadRunnerToNextCheckpoint(
        polyline: List<GeoPoint>,
        runner: GeoPoint,
        checkpointPositions: List<GeoPoint>,
        nextCheckpointIdx: Int,
        lookaheadM: Double = LEAD_LOOKAHEAD_M
    ): LeadRibbon {
        if (checkpointPositions.isEmpty()) {
            return LeadRibbon(listOf(runner, runner), runnerOffsetM = 0.0, runnerPoint = runner)
        }
        val idx = nextCheckpointIdx.coerceIn(0, checkpointPositions.lastIndex)
        val nextCp = checkpointPositions[idx]
        val window = lookaheadM.coerceAtLeast(1.0)
        if (polyline.size < 2) {
            val dist = GeoUtils.distanceMeters(runner, nextCp)
            val end = if (dist <= window) nextCp else GeoUtils.offsetPoint(
                runner,
                window,
                GeoUtils.bearingDegrees(runner, nextCp)
            )
            return LeadRibbon(listOf(runner, end), runnerOffsetM = 0.0, runnerPoint = runner)
        }

        val progress = projectTowardNextCheckpoint(
            polyline,
            runner,
            checkpointPositions,
            idx
        )
        val alongs = orderedWaypointAlongMeters(polyline, checkpointPositions)
        val nextAlong = alongs[idx]
        val fromAlong = progress.distanceAlongM.coerceAtMost(nextAlong)
        val remainingToCpM = (nextAlong - fromAlong).coerceAtLeast(0.0)
        val reachesCp = remainingToCpM <= window
        val toAlong = if (reachesCp) {
            nextAlong.coerceAtLeast(fromAlong + 1.0)
        } else {
            (fromAlong + window).coerceAtMost(pathLengthMeters(polyline))
        }
        val pathSlice = sliceAlongMeters(polyline, fromAlong, toAlong)

        val out = ArrayList<GeoPoint>(pathSlice.size + 3)
        out.add(runner)
        if (progress.distanceToPathM > 1.5 &&
            GeoUtils.distanceMeters(runner, progress.snapPoint) > 1.5
        ) {
            out.add(progress.snapPoint)
        }
        for (p in pathSlice) {
            if (out.isEmpty() || GeoUtils.distanceMeters(out.last(), p) > 0.5) {
                out.add(p)
            }
        }
        // Only pin the end on the checkpoint center when the CP is within the short lead.
        if (reachesCp &&
            (out.isEmpty() || GeoUtils.distanceMeters(out.last(), nextCp) > 0.5)
        ) {
            out.add(nextCp)
        }
        if (out.size == 1) {
            out.add(pointAlongMeters(polyline, toAlong))
        }
        return LeadRibbon(
            points = out,
            runnerOffsetM = 0.0,
            runnerPoint = runner
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
