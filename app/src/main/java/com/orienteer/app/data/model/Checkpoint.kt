package com.orienteer.app.data.model

/**
 * A single orienteering checkpoint on the route.
 *
 * @param id              Sequential index (0 = start/finish, 1..n = waypoints).
 * @param position        Snapped-to-road GPS coordinate.
 * @param description     Short natural-language description of the landmark nearby.
 * @param landmarkName    Raw landmark name from OSM tags (may be null).
 * @param landmarkType    OSM tag key+value, e.g. "amenity=bench".
 * @param isStart         True only for the origin / finish checkpoint.
 * @param isReached       Set to true when the runner comes within PROXIMITY_RADIUS_M.
 * @param distanceFromPrev  Walking distance from the previous checkpoint, metres.
 */
data class Checkpoint(
    val id: Int,
    val position: GeoPoint,
    val description: String,
    val landmarkName: String? = null,
    val landmarkType: String? = null,
    val isStart: Boolean = false,
    val isReached: Boolean = false,
    val distanceFromPrev: Double = 0.0
) {
    companion object {
        /** Runner must be within this radius (metres) to register a checkpoint visit. */
        const val PROXIMITY_RADIUS_M = 15.0
    }
}
