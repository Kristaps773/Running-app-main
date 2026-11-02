package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils

/**
 * Pure helpers for history timing edits. Path geometry is never modified here.
 */
object RunHistoryTiming {

    /**
     * Build absolute timestamps from [startedAtMs] and ordered inter-point gaps.
     * [gapDurationsMs] has size `pointCount - 1` (time from point i to i+1).
     */
    fun timestampsFromGaps(startedAtMs: Long, gapDurationsMs: List<Long>): List<Long> {
        require(gapDurationsMs.all { it >= 0L }) { "gap durations must be non-negative" }
        val out = ArrayList<Long>(gapDurationsMs.size + 1)
        var t = startedAtMs
        out.add(t)
        for (gap in gapDurationsMs) {
            t += gap
            out.add(t)
        }
        return out
    }

    /** Inter-point gaps derived from absolute timestamps (size n-1). */
    fun gapsFromTimestamps(timestampsMs: List<Long>): List<Long> {
        if (timestampsMs.size < 2) return emptyList()
        return timestampsMs.zipWithNext { a, b -> (b - a).coerceAtLeast(0L) }
    }

    data class SegmentSpeed(
        val fromIndex: Int,
        val toIndex: Int,
        val speedKmh: Double
    )

    fun smoothedSegmentSpeeds(points: List<GeoPoint>, times: List<Long>): List<SegmentSpeed> {
        if (points.size < 2 || points.size != times.size) return emptyList()
        val raw = points.indices.drop(1).mapNotNull { i ->
            val dtMs = times[i] - times[i - 1]
            if (dtMs <= 0) return@mapNotNull null
            val dM = GeoUtils.distanceMeters(points[i - 1], points[i])
            val kmh = (dM / 1000.0) / (dtMs / 3_600_000.0)
            SegmentSpeed(fromIndex = i - 1, toIndex = i, speedKmh = kmh)
        }
        return raw.mapIndexed { index, s ->
            val from = (index - 1).coerceAtLeast(0)
            val to = (index + 1).coerceAtMost(raw.lastIndex)
            val avg = raw.subList(from, to + 1).map { it.speedKmh }.average()
            s.copy(speedKmh = avg)
        }
    }

    /**
     * Rebuild checkpoint arrival times from leg durations.
     * [orderedCheckpointIds] is course order (start … finish).
     * [legDurationMsToCheckpoint] maps destination checkpoint id → duration of the leg ending there.
     * Path GPS breadcrumbs are not modified.
     */
    fun checkpointReachedAtFromLegDurations(
        startedAtMs: Long,
        orderedCheckpointIds: List<Int>,
        legDurationMsToCheckpoint: Map<Int, Long>
    ): Map<Int, Long> {
        if (orderedCheckpointIds.isEmpty()) return emptyMap()
        val out = linkedMapOf<Int, Long>()
        var t = startedAtMs
        out[orderedCheckpointIds.first()] = t
        for (i in 1 until orderedCheckpointIds.size) {
            val destId = orderedCheckpointIds[i]
            val gap = legDurationMsToCheckpoint[destId] ?: 0L
            require(gap >= 0L) { "leg durations must be non-negative" }
            t += gap
            out[destId] = t
        }
        return out
    }
}

/**
 * Ensures finish + stop only persist a run session once.
 * Thread-safe for service use.
 */
class RunPersistOnce {
    @Volatile
    private var started = false

    /** Returns true only the first time; subsequent calls return false. */
    fun tryStart(): Boolean {
        synchronized(this) {
            if (started) return false
            started = true
            return true
        }
    }

    fun reset() {
        synchronized(this) { started = false }
    }
}
