package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunRollingPaceTest {

    @Test
    fun mismatchedLists_returnsNull() {
        val points = listOf(GeoPoint(56.95, 24.10), GeoPoint(56.951, 24.10))
        val times = listOf(1_000L)
        assertNull(RunRollingPace.paceSecPerKm(points, times, nowMs = 10_000L))
    }

    @Test
    fun windowPace_matchesKnownDistanceAndTime() {
        val origin = GeoPoint(56.95, 24.10)
        val end = GeoUtils.offsetPoint(origin, 1_000.0, bearingDeg = 0.0)
        val now = 1_000_000L
        val points = listOf(origin, end)
        val times = listOf(now - 300_000L, now)
        val expected = GeoUtils.paceSecPerKm(1_000.0, 300_000L)!!
        assertEquals(expected, RunRollingPace.paceSecPerKm(points, times, nowMs = now)!!, 1e-6)
    }

    @Test
    fun ignoresPointsOlderThanWindow() {
        val origin = GeoPoint(56.95, 24.10)
        val mid = GeoUtils.offsetPoint(origin, 2_000.0, bearingDeg = 0.0)
        val end = GeoUtils.offsetPoint(mid, 500.0, bearingDeg = 0.0)
        val now = 1_000_000L
        val points = listOf(origin, mid, end)
        // origin→mid is entirely before the 5 min window; mid is just before cutoff so the
        // helper steps back one index and counts mid→end only for pace gates.
        val times = listOf(
            now - 600_000L,
            now - RunRollingPace.WINDOW_MS - 1L,
            now
        )
        val rolling = RunRollingPace.paceSecPerKm(points, times, nowMs = now)
        val midToEnd = GeoUtils.distanceMeters(mid, end)
        val expected = GeoUtils.paceSecPerKm(midToEnd, times.last() - times[1])!!
        assertEquals(expected, rolling!!, 1e-6)
    }

    @Test
    fun formatDisplayPace_fallsBackToWholeRun() {
        val origin = GeoPoint(56.95, 24.10)
        val near = GeoUtils.offsetPoint(origin, 10.0, bearingDeg = 0.0)
        val now = 1_000_000L
        val points = listOf(origin, near)
        val times = listOf(now - 60_000L, now)
        val totalDistanceM = 2_000.0
        val totalElapsedMs = 600_000L
        val expected = GeoUtils.formatRunPace(totalDistanceM, totalElapsedMs)
        val actual = RunRollingPace.formatDisplayPace(
            points = points,
            timestampsMs = times,
            nowMs = now,
            totalDistanceM = totalDistanceM,
            totalElapsedMs = totalElapsedMs
        )
        assertEquals(expected, actual)
    }
}
