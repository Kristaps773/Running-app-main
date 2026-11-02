package com.orienteer.app.data.model

import com.orienteer.app.util.GeoUtils

/**
 * A complete running route composed of ordered checkpoints.
 *
 * @param id                  Locally-generated UUID.
 * @param checkpoints         Ordered list; first and last are the start/finish point.
 * @param polylinePoints      Full walking-path geometry returned by OSRM (for drawing).
 * @param targetDistanceM     User-requested distance in metres.
 * @param totalDistanceM      Sum of straight-line (air) leg distances in metres.
 * @param actualPathDistanceM OSRM walking-path distance in metres (0 until refined).
 * @param estimatedDurationS  OSRM walking duration in seconds.
 * @param createdAt           Unix epoch millis.
 */
data class Route(
    val id: String,
    val checkpoints: List<Checkpoint>,
    val polylinePoints: List<GeoPoint>,
    val targetDistanceM: Double,
    val totalDistanceM: Double,
    val actualPathDistanceM: Double = 0.0,
    val estimatedDurationS: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** True when [polylinePoints] is provisional straight-line geometry awaiting OSRM refinement. */
    val isDraft: Boolean
        get() = polylinePoints.size <= checkpoints.size + 2 && polylinePoints.size < 32

    val startPoint: GeoPoint get() = checkpoints.first().position
    val waypointCount: Int get() = checkpoints.count { !it.isStart }
    val distanceAccuracyPct: Double get() = (totalDistanceM / targetDistanceM - 1.0) * 100.0

    /** Sum of straight-line (air) legs between consecutive checkpoints, including return to start. */
    val airDistanceM: Double
        get() {
            if (checkpoints.size < 2) return 0.0
            var total = 0.0
            for (i in 0 until checkpoints.lastIndex) {
                total += GeoUtils.distanceMeters(
                    checkpoints[i].position,
                    checkpoints[i + 1].position
                )
            }
            // Closing leg only when finish is not already appended as a trailing isStart.
            if (!hasTrailingFinish) {
                total += GeoUtils.distanceMeters(
                    checkpoints.last().position,
                    checkpoints.first().position
                )
            }
            return total
        }

    /** True when the last checkpoint is a return-to-start finish (distinct from the sole start entry). */
    val hasTrailingFinish: Boolean
        get() = checkpoints.size >= 2 &&
            checkpoints.last().isStart &&
            checkpoints.count { it.isStart } >= 2

    /**
     * True when the trailing finish is at a different place than the start (one-way course).
     */
    val isOneWayCourse: Boolean
        get() {
            if (!hasTrailingFinish || checkpoints.isEmpty()) return false
            val start = checkpoints.first().position
            val finish = checkpoints.last().position
            return start.latitude != finish.latitude || start.longitude != finish.longitude
        }

    /**
     * Ensures a trailing finish checkpoint. Default [finishPosition] is start (return-to-start).
     * Pass a different point for one-way courses. No-op when already present.
     */
    fun withFinishCheckpoint(finishPosition: GeoPoint? = null): Route {
        if (checkpoints.isEmpty() || hasTrailingFinish) return this
        val start = checkpoints.first()
        val from = checkpoints.last()
        val at = finishPosition ?: start.position
        val returnToStart =
            at.latitude == start.position.latitude && at.longitude == start.position.longitude
        val finishId = (checkpoints.maxOfOrNull { it.id } ?: 0) + 1
        val finish = Checkpoint(
            id = finishId,
            position = at,
            description = if (returnToStart) {
                "Finish — return to start to complete your run."
            } else {
                "Finish — reach this point to complete your run."
            },
            isStart = true,
            distanceFromPrev = GeoUtils.distanceMeters(from.position, at)
        )
        return copy(checkpoints = checkpoints + finish)
    }

    /** True when OSRM has returned a walking-path distance (not straight-line placeholder). */
    val hasRoutedWalkingPath: Boolean
        get() = actualPathDistanceM > 0.0

    /** OSRM walking-path metres, or null while routing / if roads could not be resolved. */
    val pathDistanceM: Double?
        get() = actualPathDistanceM.takeIf { it > 0.0 }

    /** Sum of straight segments along [polylinePoints] (for verifying OSRM distance). */
    val polylineLengthM: Double
        get() {
            if (polylinePoints.size < 2) return 0.0
            var total = 0.0
            for (i in 0 until polylinePoints.lastIndex) {
                total += GeoUtils.distanceMeters(polylinePoints[i], polylinePoints[i + 1])
            }
            return total
        }

    val pathToAirRatio: Double?
        get() {
            val path = pathDistanceM ?: return null
            val air = airDistanceM.coerceAtLeast(1.0)
            return path / air
        }
}
