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
    val checkpointReachedAtMs: Map<Int, Long>,
    val skippedCheckpointIds: Set<Int> = emptySet()
)

@Singleton
class RunRepository @Inject constructor(
    private val runSessionDao: RunSessionDao,
    private val gson: Gson
) {
    fun observeAllRuns(): Flow<List<RunSummary>> =
        runSessionDao.observeAllSessions().map { list -> list.map { it.toSummary() } }

    /** @deprecated Prefer [observeAllRuns]. */
    fun observeRecentRuns(): Flow<List<RunSummary>> = observeAllRuns()

    suspend fun saveSession(
        routeId: String,
        totalDistanceM: Double,
        elapsedTimeMs: Long,
        startedAt: Long,
        finishedAt: Long?,
        checkpointsReached: Int,
        trackedPoints: List<GeoPoint>,
        trackedPointTimestampsMs: List<Long>,
        checkpointReachedAtMs: Map<Int, Long>,
        skippedCheckpointIds: Set<Int> = emptySet()
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
            checkpointReachedAtJson = gson.toJson(checkpointReachedAtMs),
            skippedCheckpointIdsJson = gson.toJson(skippedCheckpointIds.toList())
        )
    )

    suspend fun getLastSessionForRoute(routeId: String): RunSummary? =
        runSessionDao.getLastSessionForRoute(routeId)?.toSummary()

    suspend fun getRunDetail(sessionId: Long): RunDetail? =
        runSessionDao.getSessionById(sessionId)?.toDetail(gson)

    /**
     * Updates GPS point timestamps only. Track coordinates ([RunSessionEntity.trackedPointsJson])
     * are left unchanged.
     */
    suspend fun updateTrackedPointTimestamps(sessionId: Long, timestampsMs: List<Long>) {
        val existing = runSessionDao.getSessionById(sessionId) ?: return
        val elapsed = if (timestampsMs.size >= 2) {
            timestampsMs.last() - timestampsMs.first()
        } else {
            existing.elapsedTimeMs
        }.coerceAtLeast(0L)
        runSessionDao.updateSession(
            existing.copy(
                trackedPointTimestampsJson = gson.toJson(timestampsMs),
                elapsedTimeMs = elapsed,
                finishedAt = if (timestampsMs.isNotEmpty()) timestampsMs.last() else existing.finishedAt
            )
        )
    }

    /**
     * Updates checkpoint arrival times (leg timing). GPS track coordinates/timestamps unchanged.
     */
    suspend fun updateCheckpointReachedAt(
        sessionId: Long,
        checkpointReachedAtMs: Map<Int, Long>,
        elapsedTimeMs: Long,
        finishedAt: Long?
    ) {
        val existing = runSessionDao.getSessionById(sessionId) ?: return
        runSessionDao.updateSession(
            existing.copy(
                checkpointReachedAtJson = gson.toJson(checkpointReachedAtMs),
                elapsedTimeMs = elapsedTimeMs.coerceAtLeast(0L),
                finishedAt = finishedAt
            )
        )
    }

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
    // Avoid anonymous TypeToken {} — R8 strips its generic signature in release.
    val trackedPoints = gson.fromJson(trackedPointsJson, Array<GeoPoint>::class.java)
        ?.toList()
        ?: emptyList()
    val trackedPointTimestampsMs = gson.fromJson(trackedPointTimestampsJson, Array<Long>::class.java)
        ?.toList()
        ?: emptyList()
    val checkpointMapType = TypeToken.getParameterized(
        MutableMap::class.java,
        Integer::class.java,
        java.lang.Long::class.java
    ).type
    val checkpointReachedAtMs: Map<Int, Long> =
        gson.fromJson(checkpointReachedAtJson, checkpointMapType) ?: emptyMap()
    val skippedCheckpointIds = gson.fromJson(skippedCheckpointIdsJson, IntArray::class.java)
        ?.toSet()
        ?: emptySet()
    return RunDetail(
        summary = toSummary(),
        trackedPoints = trackedPoints,
        trackedPointTimestampsMs = trackedPointTimestampsMs,
        checkpointReachedAtMs = checkpointReachedAtMs,
        skippedCheckpointIds = skippedCheckpointIds
    )
}
