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
 * @param elapsedTimeMs         Moving time since run start (excludes paused intervals).
 * @param isActive              False once all checkpoints are reached or the run is stopped.
 * @param isPaused              True while the run is paused (GPS/timer frozen).
 * @param pausedAt              Unix epoch millis when the current pause began, if [isPaused].
 * @param totalPausedMs         Sum of completed pause intervals (not including an open pause).
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
    val isPaused: Boolean = false,
    val pausedAt: Long? = null,
    val totalPausedMs: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
) {
    /** Current pace in seconds per kilometre. Returns null when not enough distance or moving too slowly. */
    val paceSecPerKm: Double?
        get() = GeoUtils.paceSecPerKm(totalDistanceM, elapsedTimeMs)

    /** Average speed in km/h. */
    val speedKmh: Double
        get() = if (elapsedTimeMs > 0) (totalDistanceM / 1000.0) / (elapsedTimeMs / 3_600_000.0) else 0.0

    /**
     * Moving elapsed time at [nowMs], excluding completed pauses and any open pause interval.
     * When inactive, returns the last stored [elapsedTimeMs].
     */
    fun activeElapsedMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (!isActive) return elapsedTimeMs
        val openPauseMs = if (isPaused) {
            (pausedAt?.let { nowMs - it } ?: 0L).coerceAtLeast(0L)
        } else {
            0L
        }
        return (nowMs - startedAt - totalPausedMs - openPauseMs).coerceAtLeast(0L)
    }

    /** Fold an open pause into [totalPausedMs] and refresh [elapsedTimeMs]. */
    fun withPauseClosed(nowMs: Long = System.currentTimeMillis()): RunSession {
        if (!isPaused) {
            return copy(elapsedTimeMs = activeElapsedMs(nowMs))
        }
        val pauseStarted = pausedAt ?: nowMs
        val added = (nowMs - pauseStarted).coerceAtLeast(0L)
        val newTotalPaused = totalPausedMs + added
        val elapsed = (nowMs - startedAt - newTotalPaused).coerceAtLeast(0L)
        return copy(
            isPaused = false,
            pausedAt = null,
            totalPausedMs = newTotalPaused,
            elapsedTimeMs = elapsed
        )
    }
}
