package com.orienteer.app.data.repository

import com.google.gson.Gson
import com.orienteer.app.data.local.dao.RunSessionDao
import com.orienteer.app.data.local.entity.RunSessionEntity
import com.orienteer.app.data.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for unlimited history: many inserts, delete, timing-only update.
 * Uses an in-memory fake DAO (no LIMIT) matching the intended Room query.
 */
class RunHistoryRepositoryContractTest {

    private val gson = Gson()
    private val dao = FakeRunSessionDao()
    private val repo = RunRepository(dao, gson)

    private val points = listOf(
        GeoPoint(56.95, 24.10),
        GeoPoint(56.951, 24.101),
        GeoPoint(56.952, 24.102)
    )

    @Test
    fun observeAllRuns_returnsEveryInsertedSession_newestFirst() = runBlocking {
        repeat(25) { i ->
            repo.saveSession(
                routeId = "r$i",
                totalDistanceM = 1000.0 + i,
                elapsedTimeMs = 60_000L,
                startedAt = 1_000_000L + i * 1_000L,
                finishedAt = 1_000_000L + i * 1_000L + 60_000L,
                checkpointsReached = 2,
                trackedPoints = points,
                trackedPointTimestampsMs = listOf(0L, 30_000L, 60_000L),
                checkpointReachedAtMs = emptyMap()
            )
        }
        val runs = repo.observeAllRuns().first()
        assertEquals(25, runs.size)
        assertTrue(runs.zipWithNext().all { (a, b) -> a.startedAt >= b.startedAt })
    }

    @Test
    fun deleteRun_removesOnlyThatSession() = runBlocking {
        val id1 = saveMinimal("a", startedAt = 100L)
        val id2 = saveMinimal("b", startedAt = 200L)
        repo.deleteRun(id1)
        val runs = repo.observeAllRuns().first()
        assertEquals(1, runs.size)
        assertEquals(id2, runs.first().sessionId)
    }

    @Test
    fun updateTimestamps_keepsPointsJson_changesTimes() = runBlocking {
        val id = saveMinimal("t", startedAt = 1_000L)
        val before = repo.getRunDetail(id)!!
        val pointsJsonBefore = dao.getSessionById(id)!!.trackedPointsJson
        val newTimes = listOf(1_000L, 1_500L, 3_000L)
        repo.updateTrackedPointTimestamps(id, newTimes)
        val after = repo.getRunDetail(id)!!
        assertEquals(before.trackedPoints, after.trackedPoints)
        assertEquals(pointsJsonBefore, dao.getSessionById(id)!!.trackedPointsJson)
        assertEquals(newTimes, after.trackedPointTimestampsMs)
    }

    @Test
    fun updateTimestamps_missingSession_isNoOp() = runBlocking {
        repo.updateTrackedPointTimestamps(999L, listOf(1L, 2L))
        assertNull(repo.getRunDetail(999L))
    }

    private suspend fun saveMinimal(routeId: String, startedAt: Long): Long =
        repo.saveSession(
            routeId = routeId,
            totalDistanceM = 500.0,
            elapsedTimeMs = 60_000L,
            startedAt = startedAt,
            finishedAt = startedAt + 60_000L,
            checkpointsReached = 1,
            trackedPoints = points,
            trackedPointTimestampsMs = listOf(startedAt, startedAt + 30_000L, startedAt + 60_000L),
            checkpointReachedAtMs = emptyMap()
        )
}

private class FakeRunSessionDao : RunSessionDao {
    private val rows = linkedMapOf<Long, RunSessionEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<RunSessionEntity>>(emptyList())

    private fun emit() {
        flow.value = rows.values.sortedByDescending { it.startedAt }
    }

    override fun observeAllSessions(): Flow<List<RunSessionEntity>> = flow

    override suspend fun getAllSessions(): List<RunSessionEntity> =
        rows.values.sortedByDescending { it.startedAt }

    override suspend fun getRecentRunTargetDistancesM(): List<Double> =
        rows.values
            .sortedByDescending { it.startedAt }
            .map { it.totalDistanceM }
            .filter { it >= 500.0 }
            .take(120)

    override suspend fun insertSession(session: RunSessionEntity): Long {
        val id = nextId++
        rows[id] = session.copy(sessionId = id)
        emit()
        return id
    }

    override suspend fun insertSessions(sessions: List<RunSessionEntity>) {
        for (session in sessions) {
            val id = if (session.sessionId != 0L) session.sessionId else nextId++
            if (id >= nextId) nextId = id + 1
            rows[id] = session.copy(sessionId = id)
        }
        emit()
    }

    override suspend fun updateSession(session: RunSessionEntity) {
        rows[session.sessionId] = session
        emit()
    }

    override suspend fun getLastSessionForRoute(routeId: String): RunSessionEntity? =
        rows.values.filter { it.routeId == routeId }.maxByOrNull { it.startedAt }

    override suspend fun getSessionById(sessionId: Long): RunSessionEntity? = rows[sessionId]

    override suspend fun deleteSessionById(sessionId: Long) {
        rows.remove(sessionId)
        emit()
    }

    override suspend fun deleteAllSessions() {
        rows.clear()
        emit()
    }
}
