package com.orienteer.app.data.local.dao

import androidx.room.*
import com.orienteer.app.data.local.entity.RouteEntity
import com.orienteer.app.data.local.entity.RunSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun observeAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    fun observeRouteById(routeId: String): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: String): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRoute(): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)

    @Delete
    suspend fun deleteRoute(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: String)

    @Query("SELECT targetDistanceM FROM routes ORDER BY createdAt DESC LIMIT 120")
    suspend fun getRecentTargetDistancesM(): List<Double>
}

@Dao
interface RunSessionDao {

    @Query("SELECT * FROM run_sessions ORDER BY startedAt DESC LIMIT 20")
    fun observeRecentSessions(): Flow<List<RunSessionEntity>>

    @Insert
    suspend fun insertSession(session: RunSessionEntity): Long

    @Update
    suspend fun updateSession(session: RunSessionEntity)

    @Query("SELECT * FROM run_sessions WHERE routeId = :routeId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLastSessionForRoute(routeId: String): RunSessionEntity?

    @Query("SELECT * FROM run_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: Long): RunSessionEntity?

    @Query("DELETE FROM run_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}
