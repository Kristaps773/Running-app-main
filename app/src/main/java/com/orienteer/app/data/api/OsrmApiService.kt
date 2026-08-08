package com.orienteer.app.data.api

import com.orienteer.app.data.model.OsrmNearestResponse
import com.orienteer.app.data.model.OsrmRouteResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * OSRM HTTP API (foot / walking profile).
 *
 * Public endpoint: https://router.project-osrm.org
 * Foot routing mirror: https://routing.openstreetmap.de/routed-foot
 *
 * The coordinates path parameter format is: longitude,latitude (OSRM uses lng first).
 */
interface OsrmApiService {

    /**
     * Snap a raw coordinate to the nearest road/path.
     * @param coordinates  "longitude,latitude"
     */
    @GET("nearest/v1/foot/{coordinates}")
    suspend fun nearest(
        @Path("coordinates") coordinates: String,
        @Query("number") number: Int = 1
    ): OsrmNearestResponse

    /**
     * Compute a walking route through multiple waypoints.
     * @param coordinates  Semicolon-separated "lng,lat" pairs.
     */
    @GET("route/v1/foot/{coordinates}")
    suspend fun route(
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = false
    ): OsrmRouteResponse
}
