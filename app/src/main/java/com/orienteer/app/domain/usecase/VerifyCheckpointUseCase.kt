package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import javax.inject.Inject

/**
 * Determines whether the runner has reached the target checkpoint.
 *
 * A checkpoint is considered reached when:
 *   - The runner's current position is within [Checkpoint.PROXIMITY_RADIUS_M] metres of the
 *     checkpoint's GPS coordinate, AND
 *   - The checkpoint has not already been marked as reached.
 *
 * This is evaluated on every GPS update during an active run.
 */
class VerifyCheckpointUseCase @Inject constructor() {

    /**
     * @param currentPosition   Runner's latest GPS fix.
     * @param targetCheckpoint  The checkpoint the runner is currently navigating to.
     * @return                  True if the runner is within the proximity radius.
     */
    operator fun invoke(
        currentPosition: GeoPoint,
        targetCheckpoint: Checkpoint
    ): Boolean {
        if (targetCheckpoint.isReached) return false

        val distance = GeoUtils.distanceMeters(currentPosition, targetCheckpoint.position)
        return distance <= Checkpoint.PROXIMITY_RADIUS_M
    }

    /**
     * Distance (metres) between the runner and the next checkpoint.
     * Useful for UI progress indicators.
     */
    fun distanceTo(currentPosition: GeoPoint, targetCheckpoint: Checkpoint): Double =
        GeoUtils.distanceMeters(currentPosition, targetCheckpoint.position)
}
