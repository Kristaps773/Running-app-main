package com.orienteer.app.data.model

import com.orienteer.app.util.GeoUtils

/**
 * Live state of an active or completed run.
 *
 * @param routeId               References [Route.id].
 * @param trackedPoints         GPS breadcrumbs recorded during the run.
 * @param currentCheckpointIdx  Index into [Route.checkpoints] for the next target.
 * @param skippedCheckpointIds  Checkpoint ids the user marked as unreachable (e.g. private place).
 * @param totalDistanceM        Distance travelled so far (metres).
 * @param elapsedTimeMs         Wall-clock time since run start (milliseconds).
 * @param isActive              False once all checkpoints are reached.
 * @param startedAt             Unix epoch millis when the run began.
 * @param finishedAt            Unix epoch millis when completed, null if still running.
 */
data class RunSession(
    val routeId: String,
    val trackedPoints: List<GeoPoint> = emptyList(),
    val trackedPointTimestampsMs: List<Long> = emptyList(),
    val currentCheckpointIdx: Int = 1,
    val checkpointReachedAtMs: Map<Int, Long> = emptyMap(),
    val skippedCheckpointIds: Set<Int> = emptySet(),
    val totalDistanceM: Double = 0.0,
    val elapsedTimeMs: Long = 0L,
    val isActive: Boolean = true,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
) {
    /** Current pace in seconds per kilometre. Returns null when not enough distance or moving too slowly. */
    val paceSecPerKm: Double?
        get() = GeoUtils.paceSecPerKm(totalDistanceM, elapsedTimeMs)

    /** Average speed in km/h. */
    val speedKmh: Double
        get() = if (elapsedTimeMs > 0) (totalDistanceM / 1000.0) / (elapsedTimeMs / 3_600_000.0) else 0.0
}
