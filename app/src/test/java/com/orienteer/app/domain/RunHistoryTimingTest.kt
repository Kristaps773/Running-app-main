package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunHistoryTimingTest {

    @Test
    fun timestampsFromGaps_buildsAbsoluteTimes() {
        val times = RunHistoryTiming.timestampsFromGaps(
            startedAtMs = 1_000L,
            gapDurationsMs = listOf(500L, 1_500L)
        )
        assertEquals(listOf(1_000L, 1_500L, 3_000L), times)
    }

    @Test
    fun gapsFromTimestamps_roundTrips() {
        val gaps = listOf(100L, 200L, 300L)
        val times = RunHistoryTiming.timestampsFromGaps(10_000L, gaps)
        assertEquals(gaps, RunHistoryTiming.gapsFromTimestamps(times))
    }

    @Test
    fun smoothedSegmentSpeeds_changeWhenGapsChange_pathUnchanged() {
        val points = listOf(
            GeoPoint(56.95, 24.10),
            GeoPoint(56.951, 24.101),
            GeoPoint(56.952, 24.102)
        )
        val slow = RunHistoryTiming.timestampsFromGaps(0L, listOf(60_000L, 60_000L))
        val fast = RunHistoryTiming.timestampsFromGaps(0L, listOf(10_000L, 10_000L))
        val slowSpeeds = RunHistoryTiming.smoothedSegmentSpeeds(points, slow)
        val fastSpeeds = RunHistoryTiming.smoothedSegmentSpeeds(points, fast)
        assertEquals(2, slowSpeeds.size)
        assertEquals(2, fastSpeeds.size)
        assertTrue(fastSpeeds[0].speedKmh > slowSpeeds[0].speedKmh)
        // Geometry identity: same point count / indices; timings are the only input that changed.
        assertEquals(slowSpeeds.map { it.fromIndex to it.toIndex }, fastSpeeds.map { it.fromIndex to it.toIndex })
    }

    @Test
    fun persistOnce_onlyFirstTryStarts() {
        val gate = RunPersistOnce()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.reset()
        assertTrue(gate.tryStart())
    }

    @Test
    fun checkpointReachedAtFromLegDurations_twoControls_oneLeg() {
        // Start (0) + Finish (0 trailing uses same id pattern: 0 then 1 for single CP course)
        val reached = RunHistoryTiming.checkpointReachedAtFromLegDurations(
            startedAtMs = 1_000L,
            orderedCheckpointIds = listOf(0, 1),
            legDurationMsToCheckpoint = mapOf(1 to 90_000L)
        )
        assertEquals(1_000L, reached[0])
        assertEquals(91_000L, reached[1])
        assertEquals(2, reached.size)
    }
}
