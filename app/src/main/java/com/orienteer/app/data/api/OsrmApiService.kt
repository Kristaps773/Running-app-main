package com.orienteer.app.data.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * OSRM HTTP API (foot / walking profile).
 *
 * Returns [ResponseBody] so release R8 minify cannot strip Kotlin `Continuation<T>`
 * generics (that caused `Class cannot be cast to ParameterizedType` with Gson).
 * Callers parse with `Gson.fromJson(..., OsrmNearestResponse::class.java)`.
 *
 * Foot routing mirror: https://routing.openstreetmap.de/routed-foot
 */
interface OsrmApiService {

    /**
     * Snap a raw coordinate to the nearest road/path.
     * @param coordinates  "longitude,latitude" (already URL-safe; must not re-encode `;`/`,`)
     */
    @GET("nearest/v1/foot/{coordinates}")
    suspend fun nearest(
        @Path(value = "coordinates", encoded = true) coordinates: String,
        @Query("number") number: Int = 1
    ): ResponseBody

    /**
     * Compute a walking route through multiple waypoints.
     * @param coordinates  Semicolon-separated "lng,lat" pairs.
     */
    @GET("route/v1/foot/{coordinates}")
    suspend fun route(
        @Path(value = "coordinates", encoded = true) coordinates: String,
        // simplified = much smaller payload than full (generation was timing out on FOSSGIS).
        @Query("overview") overview: String = "simplified",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = false
    ): ResponseBody
}
