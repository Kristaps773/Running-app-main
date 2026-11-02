package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.model.RunSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRunUseCaseTest {

    private val verifyCheckpointUseCase = VerifyCheckpointUseCase()
    private val useCase = TrackRunUseCase(verifyCheckpointUseCase)

    private val route = Route(
        id = "route-1",
        checkpoints = listOf(
            Checkpoint(id = 0, position = GeoPoint(56.95, 24.10), description = "Start", isStart = true),
            Checkpoint(id = 1, position = GeoPoint(56.95008, 24.10008), description = "CP1"),
            Checkpoint(id = 2, position = GeoPoint(56.95, 24.10), description = "Finish", isStart = true)
        ),
        polylinePoints = emptyList(),
        targetDistanceM = 3000.0,
        totalDistanceM = 0.0,
        estimatedDurationS = 0.0
    )

    @Test
    fun firstGpsPointAddsNoDistance() {
        val session = RunSession(routeId = route.id, currentCheckpointIdx = 1)
        val point = GeoPoint(56.95, 24.10)

        val updated = useCase(session, route, point, elapsedTimeMs = 1000L, timestampMs = 1000L)

        assertEquals(0.0, updated.totalDistanceM, 0.0001)
        assertEquals(1, updated.trackedPoints.size)
    }

    @Test
    fun pausedSessionIgnoresGpsUpdates() {
        val session = RunSession(
            routeId = route.id,
            trackedPoints = listOf(GeoPoint(56.95, 24.10)),
            currentCheckpointIdx = 1,
            isPaused = true,
            pausedAt = 5_000L,
            elapsedTimeMs = 5_000L
        )
        val point = GeoPoint(56.95005, 24.10005)

        val updated = useCase(session, route, point, elapsedTimeMs = 5_000L, timestampMs = 6_000L)

        assertEquals(session, updated)
    }

    @Test
    fun smallMoveAddsDistance() {
        val session = RunSession(
            routeId = route.id,
            trackedPoints = listOf(GeoPoint(56.95, 24.10)),
            currentCheckpointIdx = 1
        )
        // ~5 m move (above GPS noise floor)
        val point = GeoPoint(56.95005, 24.10005)

        val updated = useCase(session, route, point, elapsedTimeMs = 2000L, timestampMs = 2000L)

        assertTrue(updated.totalDistanceM > 0.0)
    }

    @Test
    fun gpsNoiseUnder3mAddsZeroDistance() {
        val session = RunSession(
            routeId = route.id,
            trackedPoints = listOf(GeoPoint(56.95, 24.10)),
            currentCheckpointIdx = 1
        )
        val jitter = GeoPoint(56.95001, 24.10001)

        val updated = useCase(session, route, jitter, elapsedTimeMs = 2000L, timestampMs = 2000L)

        assertEquals(0.0, updated.totalDistanceM, 0.0001)
    }

    @Test
    fun gpsSpikeOver50mAddsZeroDistance() {
        val session = RunSession(
            routeId = route.id,
            trackedPoints = listOf(GeoPoint(56.95, 24.10)),
            currentCheckpointIdx = 1
        )
        val farPoint = GeoPoint(56.96, 24.11)

        val updated = useCase(session, route, farPoint, elapsedTimeMs = 3000L, timestampMs = 3000L)

        assertEquals(0.0, updated.totalDistanceM, 0.0001)
    }

    @Test
    fun reachingCheckpointIncrementsIndex() {
        val session = RunSession(
            routeId = route.id,
            trackedPoints = listOf(GeoPoint(56.95, 24.10)),
            currentCheckpointIdx = 1
        )
        val nearCheckpoint = GeoPoint(56.95008, 24.10008)

        val updated = useCase(session, route, nearCheckpoint, elapsedTimeMs = 4000L, timestampMs = 4000L)

        assertEquals(2, updated.currentCheckpointIdx)
        assertEquals(4000L, updated.checkpointReachedAtMs[1])
    }

    @Test
    fun reachingLastCheckpointFinishesRun() {
        val finishingRoute = route.copy(
            checkpoints = listOf(
                Checkpoint(id = 0, position = GeoPoint(56.95, 24.10), description = "Start", isStart = true),
                Checkpoint(id = 1, position = GeoPoint(56.95008, 24.10008), description = "Finish", isStart = true)
            )
        )
        val session = RunSession(
            routeId = finishingRoute.id,
            trackedPoints = listOf(GeoPoint(56.95, 24.10)),
            currentCheckpointIdx = 1
        )
        val finishPoint = GeoPoint(56.95008, 24.10008)

        val updated = useCase(session, finishingRoute, finishPoint, elapsedTimeMs = 5000L, timestampMs = 5000L)

        assertFalse(updated.isActive)
        assertNotNull(updated.finishedAt)
    }

    @Test
    fun reachingLastWaypointDoesNotFinishUntilReturnToStart() {
        val twoCpRoute = route // Start, CP1, Finish(at start)
        val session = RunSession(
            routeId = twoCpRoute.id,
            trackedPoints = listOf(GeoPoint(56.95, 24.10)),
            currentCheckpointIdx = 1
        )
        val nearCp1 = GeoPoint(56.95008, 24.10008)

        val afterCp1 = useCase(session, twoCpRoute, nearCp1, elapsedTimeMs = 4000L, timestampMs = 4000L)

        assertEquals(2, afterCp1.currentCheckpointIdx)
        assertTrue(afterCp1.isActive)
        assertEquals(null, afterCp1.finishedAt)

        val atFinish = GeoPoint(56.95, 24.10)
        val done = useCase(afterCp1, twoCpRoute, atFinish, elapsedTimeMs = 8000L, timestampMs = 8000L)

        assertFalse(done.isActive)
        assertNotNull(done.finishedAt)
        assertEquals(8000L, done.checkpointReachedAtMs[2])
    }
}
