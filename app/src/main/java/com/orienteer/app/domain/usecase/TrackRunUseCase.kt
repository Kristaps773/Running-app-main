package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.model.RunSession
import com.orienteer.app.util.GeoUtils
import javax.inject.Inject

/**
 * Pure domain logic for updating a [RunSession] when a new GPS point arrives.
 *
 * Separating this logic from the ViewModel/Service makes it fully testable
 * without Android dependencies and easy to disable (swap to a no-op) if
 * tracking is turned off by the user.
 */
class TrackRunUseCase @Inject constructor(
    private val verifyCheckpoint: VerifyCheckpointUseCase
) {

    companion object {
        /** Ignore single-fix jumps above this (GPS spike). */
        private const val GPS_SPIKE_MAX_M = 50.0
        /** Ignore tiny hops below this while stationary (GPS noise). */
        private const val GPS_NOISE_MIN_M = 3.0
    }

    /**
     * Process a new GPS fix and return an updated [RunSession].
     *
     * @param session       Current run state.
     * @param route         The route being run (for checkpoint verification).
     * @param newPoint      The latest GPS coordinate.
     * @param elapsedTimeMs Wall-clock elapsed time since run start.
     */
    operator fun invoke(
        session: RunSession,
        route: Route,
        newPoint: GeoPoint,
        elapsedTimeMs: Long,
        timestampMs: Long
    ): RunSession {
        if (!session.isActive) return session

        // Accumulate distance from the last tracked point
        val addedDistance = if (session.trackedPoints.isNotEmpty()) {
            val prev = session.trackedPoints.last()
            val d = GeoUtils.distanceMeters(prev, newPoint)
            when {
                d > GPS_SPIKE_MAX_M -> 0.0
                d < GPS_NOISE_MIN_M -> 0.0
                else -> d
            }
        } else 0.0

        val newPoints = session.trackedPoints + newPoint
        val newPointTimes = session.trackedPointTimestampsMs + timestampMs
        val newDistance = session.totalDistanceM + addedDistance

        // Check whether the current target checkpoint has been reached
        val targetCheckpoint = route.checkpoints.getOrNull(session.currentCheckpointIdx)
        val checkpointReached = targetCheckpoint != null &&
                verifyCheckpoint(newPoint, targetCheckpoint)

        val newCheckpointIdx = if (checkpointReached) {
            session.currentCheckpointIdx + 1
        } else {
            session.currentCheckpointIdx
        }

        // Route is finished when we've passed all checkpoints (last = start again)
        val allDone = newCheckpointIdx >= route.checkpoints.size
        val finishedAt = if (allDone) System.currentTimeMillis() else null

        val reachedAt = if (checkpointReached) {
            val reachedId = targetCheckpoint!!.id
            session.checkpointReachedAtMs + (reachedId to timestampMs)
        } else {
            session.checkpointReachedAtMs
        }

        return session.copy(
            trackedPoints = newPoints,
            trackedPointTimestampsMs = newPointTimes,
            totalDistanceM = newDistance,
            elapsedTimeMs = elapsedTimeMs,
            currentCheckpointIdx = newCheckpointIdx,
            checkpointReachedAtMs = reachedAt,
            isActive = !allDone,
            finishedAt = finishedAt
        )
    }
}
