package com.orienteer.app.data.repository

import com.google.gson.Gson
import com.orienteer.app.data.local.dao.RouteDao
import com.orienteer.app.data.local.dao.RunSessionDao
import com.orienteer.app.data.local.entity.RouteEntity
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.domain.CourseReuseSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class RouteRepository @Inject constructor(
    private val routeDao: RouteDao,
    private val runSessionDao: RunSessionDao,
    private val gson: Gson
) {
    fun observeRoutes(): Flow<List<Route>> =
        routeDao.observeAllRoutes()
            .map { entities -> entities.map { it.toDomain(gson) } }
            .flowOn(Dispatchers.Default)

    suspend fun getRoute(routeId: String): Route? =
        routeDao.getRouteById(routeId)?.toDomain(gson)

    fun observeRoute(routeId: String): Flow<Route?> =
        routeDao.observeRouteById(routeId)
            .map { entity -> entity?.toDomain(gson) }
            .flowOn(Dispatchers.Default)

    suspend fun saveRoute(route: Route) =
        routeDao.insertRoute(route.toEntity(gson))

    suspend fun deleteRoute(routeId: String) =
        routeDao.deleteRouteById(routeId)

    suspend fun getLatestRoute(): Route? =
        routeDao.getLatestRoute()?.toDomain(gson)

    /**
     * Most common target distances from **run history** (not generated-only routes).
     * With no history, returns 5 km only.
     */
    suspend fun getFrequentTargetDistancesM(top: Int = 3, bucketM: Double = 500.0): List<Double> {
        val recent = runSessionDao.getRecentRunTargetDistancesM()
            .filter { it in 500.0..50_000.0 }
        if (recent.isEmpty()) {
            return listOf(5_000.0).take(top)
        }
        val counts = recent.groupingBy { (it / bucketM).roundToInt() }.eachCount()
        return counts.entries
            .sortedByDescending { it.value }
            .take(top)
            .map { (bucket, _) -> (bucket * bucketM).coerceIn(500.0, 50_000.0) }
    }
}

private fun RouteEntity.toDomain(gson: Gson): Route {
    val checkpoints: List<Checkpoint> = gson.fromJson(
        checkpointsJson,
        Array<Checkpoint>::class.java
    ).toList()
    val polyline: List<GeoPoint> = gson.fromJson(
        polylineJson,
        Array<GeoPoint>::class.java
    ).toList()
    val snapshot = reuseSnapshotJson.takeIf { it.isNotBlank() }?.let { json ->
        runCatching { gson.fromJson(json, CourseReuseSnapshot::class.java) }.getOrNull()
    }
    return Route(
        id = id,
        checkpoints = checkpoints,
        polylinePoints = polyline,
        targetDistanceM = targetDistanceM,
        totalDistanceM = totalDistanceM,
        actualPathDistanceM = actualPathDistanceM,
        estimatedDurationS = estimatedDurationS,
        createdAt = createdAt,
        reuseSnapshot = snapshot
    )
}

private fun Route.toEntity(gson: Gson) = RouteEntity(
    id = id,
    checkpointsJson = gson.toJson(checkpoints),
    polylineJson = gson.toJson(polylinePoints),
    targetDistanceM = targetDistanceM,
    totalDistanceM = totalDistanceM,
    actualPathDistanceM = actualPathDistanceM,
    estimatedDurationS = estimatedDurationS,
    createdAt = createdAt,
    reuseSnapshotJson = reuseSnapshot?.let { gson.toJson(it) }.orEmpty()
)
