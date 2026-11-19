package com.orienteer.app.domain.usecase

import com.google.gson.Gson
import com.orienteer.app.data.api.OsrmApiService
import com.orienteer.app.data.api.parseJson
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OsrmRouteResponse
import com.orienteer.app.domain.LiveFootpathReroute
import com.orienteer.app.domain.RoutePolylineProgress
import com.orienteer.app.util.GeoUtils
import javax.inject.Inject

/**
 * Soft live reroute during an active run: OSRM from GPS through remaining checkpoints.
 * Switches only when the candidate saves at least [LiveFootpathReroute.MIN_SAVINGS_M].
 */
class LiveFootpathGuidanceUseCase @Inject constructor(
    private val osrm: OsrmApiService,
    private val gson: Gson
) {

    data class Result(
        val polyline: List<GeoPoint>,
        val distanceM: Double,
        val switched: Boolean
    )

    suspend fun maybeReroute(
        currentLocation: GeoPoint,
        remainingCheckpoints: List<Checkpoint>,
        currentGuidance: List<GeoPoint>
    ): Result? {
        if (remainingCheckpoints.isEmpty()) return null
        val progress = RoutePolylineProgress.project(currentGuidance, currentLocation)
        val remainingCurrent = progress.remainingDistanceM
        if (remainingCurrent < LiveFootpathReroute.MIN_SAVINGS_M) return null

        val waypoints = buildList {
            add(currentLocation)
            remainingCheckpoints.forEach { add(it.position) }
        }
        if (waypoints.size < 2) return null

        // Active-run lead needs dense geometry — simplified (often ~3 pts) flips the lead at bends.
        val response = runCatching {
            osrm.route(
                coordinates = GeoUtils.toOsrmCoordinates(waypoints),
                overview = "full"
            ).parseJson(gson, OsrmRouteResponse::class.java)
        }.getOrNull() ?: return null

        if (response.code != null && response.code != "Ok") return null
        val route = response.routeList().firstOrNull() ?: return null
        val geometry = route.geometry?.toGeoPoints().orEmpty()
        if (geometry.size < 2) return null

        if (!LiveFootpathReroute.shouldSwitch(remainingCurrent, route.distance)) {
            return Result(currentGuidance, remainingCurrent, switched = false)
        }
        return Result(geometry, route.distance, switched = true)
    }
}
