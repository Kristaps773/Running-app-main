package com.orienteer.app.data.local.dao

import androidx.room.*
import com.orienteer.app.data.local.entity.RouteEntity
import com.orienteer.app.data.local.entity.RunSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun observeAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    suspend fun getAllRoutes(): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    fun observeRouteById(routeId: String): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: String): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRoute(): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Delete
    suspend fun deleteRoute(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: String)

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Query("SELECT targetDistanceM FROM routes ORDER BY createdAt DESC LIMIT 120")
    suspend fun getRecentTargetDistancesM(): List<Double>
}

@Dao
interface RunSessionDao {

    /** All saved runs, newest first (no artificial cap). */
    @Query("SELECT * FROM run_sessions ORDER BY startedAt DESC")
    fun observeAllSessions(): Flow<List<RunSessionEntity>>

    @Query("SELECT * FROM run_sessions ORDER BY startedAt DESC")
    suspend fun getAllSessions(): List<RunSessionEntity>

    /**
     * Target distances from completed (or measurable) runs via their route,
     * falling back to run distance when the route row is gone.
     */
    @Query(
        """
        SELECT CASE
            WHEN routes.targetDistanceM IS NOT NULL AND routes.targetDistanceM >= 500
                THEN routes.targetDistanceM
            ELSE run_sessions.totalDistanceM
        END
        FROM run_sessions
        LEFT JOIN routes ON routes.id = run_sessions.routeId
        WHERE run_sessions.totalDistanceM >= 500
           OR (routes.targetDistanceM IS NOT NULL AND routes.targetDistanceM >= 500)
        ORDER BY run_sessions.startedAt DESC
        LIMIT 120
        """
    )
    suspend fun getRecentRunTargetDistancesM(): List<Double>

    @Insert
    suspend fun insertSession(session: RunSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<RunSessionEntity>)

    @Update
    suspend fun updateSession(session: RunSessionEntity)

    @Query("SELECT * FROM run_sessions WHERE routeId = :routeId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLastSessionForRoute(routeId: String): RunSessionEntity?

    @Query("SELECT * FROM run_sessions WHERE routeId = :routeId")
    suspend fun getSessionsForRoute(routeId: String): List<RunSessionEntity>

    @Query("SELECT * FROM run_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: Long): RunSessionEntity?

    @Query("DELETE FROM run_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM run_sessions WHERE routeId = :routeId")
    suspend fun deleteSessionsForRoute(routeId: String)

    @Query("DELETE FROM run_sessions")
    suspend fun deleteAllSessions()
}
