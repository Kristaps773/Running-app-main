package com.orienteer.app.data.model

import com.google.gson.annotations.SerializedName

// ── OSRM Nearest endpoint ──────────────────────────────────────────────────

data class OsrmNearestResponse(
    @SerializedName("code") val code: String,
    @SerializedName("waypoints") val waypoints: List<OsrmWaypoint>
)

data class OsrmWaypoint(
    @SerializedName("location") val location: List<Double>,   // [longitude, latitude]
    @SerializedName("name") val name: String,
    @SerializedName("distance") val distance: Double
) {
    val latitude: Double get() = location[1]
    val longitude: Double get() = location[0]
}

// ── OSRM Route endpoint ────────────────────────────────────────────────────

data class OsrmRouteResponse(
    @SerializedName("code") val code: String,
    @SerializedName("routes") val routes: List<OsrmRoute>,
    @SerializedName("waypoints") val waypoints: List<OsrmWaypoint>
)

data class OsrmRoute(
    @SerializedName("distance") val distance: Double,          // metres
    @SerializedName("duration") val duration: Double,          // seconds
    @SerializedName("geometry") val geometry: OsrmGeometry,
    @SerializedName("legs") val legs: List<OsrmLeg>
)

data class OsrmLeg(
    @SerializedName("distance") val distance: Double,
    @SerializedName("duration") val duration: Double,
    @SerializedName("summary") val summary: String
)

data class OsrmGeometry(
    @SerializedName("coordinates") val coordinates: List<List<Double>>,  // [[lng, lat], ...]
    @SerializedName("type") val type: String
) {
    fun toGeoPoints(): List<GeoPoint> = coordinates.map { GeoPoint(it[1], it[0]) }
}
