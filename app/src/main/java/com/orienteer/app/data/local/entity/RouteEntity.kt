package com.orienteer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint

@Entity(tableName = "routes")
@TypeConverters(RouteConverters::class)
data class RouteEntity(
    @PrimaryKey val id: String,
    val checkpointsJson: String,
    val polylineJson: String,
    val targetDistanceM: Double,
    val totalDistanceM: Double,
    val actualPathDistanceM: Double = 0.0,
    val estimatedDurationS: Double,
    val createdAt: Long
)

@Entity(tableName = "run_sessions")
data class RunSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val routeId: String,
    val totalDistanceM: Double,
    val elapsedTimeMs: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val checkpointsReached: Int,
    val trackedPointsJson: String = "[]",
    val trackedPointTimestampsJson: String = "[]",
    val checkpointReachedAtJson: String = "{}"
)

class RouteConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromCheckpointList(value: List<Checkpoint>): String =
        gson.toJson(value)

    @TypeConverter
    fun toCheckpointList(value: String): List<Checkpoint> =
        gson.fromJson(value, object : TypeToken<List<Checkpoint>>() {}.type)

    @TypeConverter
    fun fromGeoPointList(value: List<GeoPoint>): String =
        gson.toJson(value)

    @TypeConverter
    fun toGeoPointList(value: String): List<GeoPoint> =
        gson.fromJson(value, object : TypeToken<List<GeoPoint>>() {}.type)
}
