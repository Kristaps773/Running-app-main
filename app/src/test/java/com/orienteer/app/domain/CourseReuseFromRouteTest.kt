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
    private val finishAway = GeoPoint(56.97, 24.15)

    private fun cp(
        id: Int,
        pos: GeoPoint,
        isStart: Boolean = false,
        isFixed: Boolean = false
    ) = Checkpoint(
        id = id,
        position = pos,
        description = "cp$id",
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
    fun fromRoute_loop_locksAllIntermediates_notOnlyFixed() {
        val route = Route(
            id = "loop",
            checkpoints = listOf(
                cp(0, start, isStart = true),
                cp(1, midA, isFixed = true),
                cp(2, midB, isFixed = false),
                cp(3, start, isStart = true)
            ),
            polylinePoints = listOf(start, midA, midB, start),
            targetDistanceM = 6_000.0,
            totalDistanceM = 5_500.0,
            estimatedDurationS = 3_000.0
        )
        val payload = CourseReuseFromRoute.fromRoute(route)!!
        assertEquals(start, payload.start)
        assertNull(payload.finish)
        assertEquals(listOf(midA, midB), payload.locked)
        assertEquals(6.0, payload.targetDistanceKm, 0.001)
        assertTrue(payload.anyOrder)
    }

    @Test
    fun fromRoute_oneWay_setsFinish_excludesFinishFromLocked() {
        val route = Route(
            id = "ow",
            checkpoints = listOf(
                cp(0, start, isStart = true),
                cp(1, midA),
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
        assertTrue(!payload.anyOrder || payload.locked.size < 2)
    }

    @Test
    fun fromRoute_singleIntermediate_anyOrderFalse() {
        val route = Route(
            id = "one",
            checkpoints = listOf(
                cp(0, start, isStart = true),
                cp(1, midA),
                cp(2, start, isStart = true)
            ),
            polylinePoints = listOf(start, midA, start),
            targetDistanceM = 3_000.0,
            totalDistanceM = 2_800.0,
            estimatedDurationS = 1_500.0
        )
        val payload = CourseReuseFromRoute.fromRoute(route, anyOrderDefault = true)!!
        assertEquals(1, payload.locked.size)
        assertEquals(false, payload.anyOrder)
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
                airDistanceToleranceM = 250f
            )
        )
        val payload = CourseReuseFromRoute.fromRoute(route, anyOrderDefault = true)!!
        assertEquals(false, payload.anyOrder)
        assertEquals(4, payload.minCheckpoints)
        assertEquals(8, payload.maxCheckpoints)
        assertNotNull(payload.generationPrefs)
        assertEquals(1.3f, payload.generationPrefs!!.maxPathToAirRatio, 0.001f)
    }
}
