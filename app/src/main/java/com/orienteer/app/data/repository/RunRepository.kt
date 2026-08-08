package com.orienteer.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orienteer.app.data.local.dao.RunSessionDao
import com.orienteer.app.data.local.entity.RunSessionEntity
import com.orienteer.app.data.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class RunSummary(
    val sessionId: Long,
    val routeId: String,
    val totalDistanceM: Double,
    val elapsedTimeMs: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val checkpointsReached: Int
)

data class RunDetail(
    val summary: RunSummary,
    val trackedPoints: List<GeoPoint>,
    val trackedPointTimestampsMs: List<Long>,
    val checkpointReachedAtMs: Map<Int, Long>
)

@Singleton
class RunRepository @Inject constructor(
    private val runSessionDao: RunSessionDao,
    private val gson: Gson
) {
    fun observeRecentRuns(): Flow<List<RunSummary>> =
        runSessionDao.observeRecentSessions().map { list -> list.map { it.toSummary() } }

    suspend fun saveSession(
        routeId: String,
        totalDistanceM: Double,
        elapsedTimeMs: Long,
        startedAt: Long,
        finishedAt: Long?,
        checkpointsReached: Int,
        trackedPoints: List<GeoPoint>,
        trackedPointTimestampsMs: List<Long>,
        checkpointReachedAtMs: Map<Int, Long>
    ): Long = runSessionDao.insertSession(
        RunSessionEntity(
            routeId = routeId,
            totalDistanceM = totalDistanceM,
            elapsedTimeMs = elapsedTimeMs,
            startedAt = startedAt,
            finishedAt = finishedAt,
            checkpointsReached = checkpointsReached,
            trackedPointsJson = gson.toJson(trackedPoints),
            trackedPointTimestampsJson = gson.toJson(trackedPointTimestampsMs),
            checkpointReachedAtJson = gson.toJson(checkpointReachedAtMs)
        )
    )

    suspend fun getLastSessionForRoute(routeId: String): RunSummary? =
        runSessionDao.getLastSessionForRoute(routeId)?.toSummary()

    suspend fun getRunDetail(sessionId: Long): RunDetail? =
        runSessionDao.getSessionById(sessionId)?.toDetail(gson)

    suspend fun deleteRun(sessionId: Long) {
        runSessionDao.deleteSessionById(sessionId)
    }
}

private fun RunSessionEntity.toSummary() = RunSummary(
    sessionId = sessionId,
    routeId = routeId,
    totalDistanceM = totalDistanceM,
    elapsedTimeMs = elapsedTimeMs,
    startedAt = startedAt,
    finishedAt = finishedAt,
    checkpointsReached = checkpointsReached
)

private fun RunSessionEntity.toDetail(gson: Gson): RunDetail {
    val pointsType = object : TypeToken<List<GeoPoint>>() {}.type
    val timesType = object : TypeToken<List<Long>>() {}.type
    val checkpointsType = object : TypeToken<Map<Int, Long>>() {}.type
    return RunDetail(
        summary = toSummary(),
        trackedPoints = gson.fromJson(trackedPointsJson, pointsType) ?: emptyList(),
        trackedPointTimestampsMs = gson.fromJson(trackedPointTimestampsJson, timesType) ?: emptyList(),
        checkpointReachedAtMs = gson.fromJson(checkpointReachedAtJson, checkpointsType) ?: emptyMap()
    )
}
