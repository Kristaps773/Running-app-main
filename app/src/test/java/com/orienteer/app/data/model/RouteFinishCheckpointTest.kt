package com.orienteer.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteFinishCheckpointTest {

    @Test
    fun withFinishCheckpoint_appendsReturnToStart() {
        val route = Route(
            id = "r1",
            checkpoints = listOf(
                Checkpoint(id = 0, position = GeoPoint(56.95, 24.10), description = "Start", isStart = true),
                Checkpoint(id = 1, position = GeoPoint(56.96, 24.11), description = "CP1"),
                Checkpoint(id = 2, position = GeoPoint(56.97, 24.12), description = "CP2")
            ),
            polylinePoints = emptyList(),
            targetDistanceM = 3000.0,
            totalDistanceM = 3000.0,
            estimatedDurationS = 0.0
        )

        assertFalse(route.hasTrailingFinish)
        val withFinish = route.withFinishCheckpoint()
        assertTrue(withFinish.hasTrailingFinish)
        assertEquals(4, withFinish.checkpoints.size)
        assertTrue(withFinish.checkpoints.last().isStart)
        assertEquals(56.95, withFinish.checkpoints.last().position.latitude, 1e-9)
        assertEquals(3, withFinish.checkpoints.last().id)
        // Waypoints exclude both start and finish.
        assertEquals(2, withFinish.waypointCount)
        // Idempotent.
        assertEquals(withFinish.checkpoints.size, withFinish.withFinishCheckpoint().checkpoints.size)
    }

    @Test
    fun airDistance_doesNotDoubleCountWhenFinishPresent() {
        val without = Route(
            id = "r1",
            checkpoints = listOf(
                Checkpoint(id = 0, position = GeoPoint(56.95, 24.10), description = "Start", isStart = true),
                Checkpoint(id = 1, position = GeoPoint(56.951, 24.101), description = "CP1")
            ),
            polylinePoints = emptyList(),
            targetDistanceM = 1000.0,
            totalDistanceM = 1000.0,
            estimatedDurationS = 0.0
        )
        val with = without.withFinishCheckpoint()
        assertEquals(without.airDistanceM, with.airDistanceM, 0.5)
    }
}
