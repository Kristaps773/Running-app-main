package com.orienteer.app.domain

import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseReuseFromRouteTest {

    private val start = GeoPoint(56.95, 24.10)
    private val midA = GeoPoint(56.955, 24.105)
    private val midB = GeoPoint(56.960, 24.110)
    private val midC = GeoPoint(56.962, 24.112)
    private val finishAway = GeoPoint(56.97, 24.15)

    private fun cp(
        id: Int,
        pos: GeoPoint,
        isStart: Boolean = false,
        isFixed: Boolean = false
    ) = Checkpoint(
        id = id,
        position = pos,
        description = "control$id",
        isStart = isStart,
        isFixed = isFixed
    )

    @Test
    fun fromRoute_empty_returnsNull() {
        val route = Route(
            id = "empty",
            checkpoints = emptyList(),
            polylinePoints = emptyList(),
            targetDistanceM = 5_000.0,
            totalDistanceM = 0.0,
            estimatedDurationS = 0.0
        )
        assertNull(CourseReuseFromRoute.fromRoute(route))
    }

    @Test
    fun fromRoute_withoutSnapshot_usesOnlyFixedPins() {
        val route = Route(
            id = "loop",
            checkpoints = listOf(
                cp(0, start, isStart = true),
                cp(1, midA, isFixed = true),
                cp(2, midB, isFixed = false),
                cp(3, midC, isFixed = false),
                cp(4, start, isStart = true)
            ),
            polylinePoints = listOf(start, midA, midB, midC, start),
            targetDistanceM = 6_000.0,
            totalDistanceM = 5_500.0,
            estimatedDurationS = 3_000.0
        )
        val payload = CourseReuseFromRoute.fromRoute(route)!!
        assertEquals(start, payload.start)
        assertNull(payload.finish)
        assertEquals(listOf(midA), payload.locked)
        assertEquals(6.0, payload.targetDistanceKm, 0.001)
    }

    @Test
    fun fromRoute_snapshotLockedPins_notAllIntermediates() {
        val route = Route(
            id = "snap-pins",
            checkpoints = listOf(
                cp(0, start, isStart = true),
                cp(1, midA, isFixed = true),
                cp(2, midB, isFixed = false),
                cp(3, midC, isFixed = false),
                cp(4, finishAway, isStart = true)
            ),
            polylinePoints = listOf(start, midA, midB, midC, finishAway),
            targetDistanceM = 3_000.0,
            totalDistanceM = 2_900.0,
            estimatedDurationS = 1_800.0,
            reuseSnapshot = CourseReuseSnapshot(
                anyOrder = true,
                minCheckpoints = 3,
                maxCheckpoints = 15,
                lockedPins = listOf(midA, midB),
                start = start,
                finish = finishAway,
                hasExplicitFinish = true
            )
        )
        val payload = CourseReuseFromRoute.fromRoute(route)!!
        assertEquals(listOf(midA, midB), payload.locked)
        assertEquals(finishAway, payload.finish)
        assertEquals(3, payload.minCheckpoints)
        assertEquals(15, payload.maxCheckpoints)
        assertTrue(payload.anyOrder)
    }

    @Test
    fun fromRoute_oneWay_setsFinish_excludesFinishFromLocked() {
        val route = Route(
            id = "ow",
            checkpoints = listOf(
                cp(0, start, isStart = true),
                cp(1, midA, isFixed = true),
                cp(2, finishAway, isStart = true)
            ),
            polylinePoints = listOf(start, midA, finishAway),
            targetDistanceM = 4_000.0,
            totalDistanceM = 3_800.0,
            estimatedDurationS = 2_000.0
        )
        assertTrue(route.isOneWayCourse)
        val payload = CourseReuseFromRoute.fromRoute(route)!!
        assertEquals(start, payload.start)
        assertEquals(finishAway, payload.finish)
        assertEquals(listOf(midA), payload.locked)
    }

    @Test
    fun fromRoute_snapshotPrefs_winOverDefaults() {
        val route = Route(
            id = "snap",
            checkpoints = listOf(
                cp(0, start, isStart = true),
                cp(1, midA),
                cp(2, midB),
                cp(3, start, isStart = true)
            ),
            polylinePoints = listOf(start, midA, midB, start),
            targetDistanceM = 7_000.0,
            totalDistanceM = 6_500.0,
            estimatedDurationS = 3_500.0,
            reuseSnapshot = CourseReuseSnapshot(
                anyOrder = false,
                minCheckpoints = 4,
                maxCheckpoints = 8,
                maxPathToAirRatio = 1.3f,
                airDistanceToleranceM = 250f,
                lockedPins = listOf(midA),
                start = start,
                finish = null,
                hasExplicitFinish = false
            )
        )
        val payload = CourseReuseFromRoute.fromRoute(route, anyOrderDefault = true)!!
        assertEquals(false, payload.anyOrder)
        assertEquals(4, payload.minCheckpoints)
        assertEquals(8, payload.maxCheckpoints)
        assertEquals(listOf(midA), payload.locked)
        assertNotNull(payload.generationPrefs)
        assertEquals(1.3f, payload.generationPrefs!!.maxPathToAirRatio, 0.001f)
    }

    @Test
    fun snapshot_fromJson_roundTripsLockedPins() {
        val snap = CourseReuseSnapshot.from(
            anyOrder = true,
            prefs = com.orienteer.app.data.local.RouteGenerationPreferences(
                minCheckpoints = 3,
                maxCheckpoints = 15
            ),
            start = start,
            finish = finishAway,
            lockedPins = listOf(midA, midB)
        )
        val json = com.google.gson.Gson().toJson(snap)
        val decoded = CourseReuseSnapshot.fromJson(json)!!
        assertEquals(2, decoded.lockedPins.size)
        assertEquals(midA.latitude, decoded.lockedPins[0].latitude, 1e-6)
        assertEquals(true, decoded.hasExplicitFinish)
        assertEquals(3, decoded.minCheckpoints)
    }
}
