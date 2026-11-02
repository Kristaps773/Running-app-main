package com.orienteer.app.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * OSRM JSON models.
 *
 * Nested collections use Array / DoubleArray (not List). Under R8 full mode, Gson + List<T>
 * field signatures get erased → LinkedTreeMap ClassCastException / silent empty routes,
 * which made generation spin until timeout.
 */

// ── OSRM Nearest endpoint ──────────────────────────────────────────────────

@Keep
data class OsrmNearestResponse(
    @SerializedName("code") val code: String? = null,
    @SerializedName("waypoints") val waypoints: Array<OsrmWaypoint>? = null
) {
    fun waypointList(): Array<OsrmWaypoint> = waypoints ?: emptyArray()
}

@Keep
data class OsrmWaypoint(
    @SerializedName("location") val location: DoubleArray = doubleArrayOf(0.0, 0.0),
    @SerializedName("name") val name: String? = null,
    @SerializedName("distance") val distance: Double = 0.0
) {
    val latitude: Double get() = location.getOrElse(1) { 0.0 }
    val longitude: Double get() = location.getOrElse(0) { 0.0 }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OsrmWaypoint
        return distance == other.distance && name == other.name && location.contentEquals(other.location)
    }

    override fun hashCode(): Int {
        var result = location.contentHashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + distance.hashCode()
        return result
    }
}

// ── OSRM Route endpoint ────────────────────────────────────────────────────

@Keep
data class OsrmRouteResponse(
    @SerializedName("code") val code: String? = null,
    @SerializedName("routes") val routes: Array<OsrmRoute>? = null,
    @SerializedName("waypoints") val waypoints: Array<OsrmWaypoint>? = null
) {
    fun routeList(): Array<OsrmRoute> = routes ?: emptyArray()
    fun waypointList(): Array<OsrmWaypoint> = waypoints ?: emptyArray()
}

@Keep
data class OsrmRoute(
    @SerializedName("distance") val distance: Double,          // metres
    @SerializedName("duration") val duration: Double,          // seconds
    @SerializedName("geometry") val geometry: OsrmGeometry?,
    @SerializedName("legs") val legs: Array<OsrmLeg> = emptyArray()
)

@Keep
data class OsrmLeg(
    @SerializedName("distance") val distance: Double,
    @SerializedName("duration") val duration: Double,
    @SerializedName("summary") val summary: String? = null
)

@Keep
data class OsrmGeometry(
    @SerializedName("coordinates") val coordinates: Array<DoubleArray> = emptyArray(),
    @SerializedName("type") val type: String = "LineString"
) {
    fun toGeoPoints(): List<GeoPoint> = coordinates.mapNotNull { pair ->
        if (pair.size < 2) null else GeoPoint(pair[1], pair[0])
    }
}
