package com.orienteer.app.data.api

import com.orienteer.app.data.model.OverpassResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Overpass API for querying OpenStreetMap point-of-interest data.
 * Public endpoint: https://overpass-api.de/api/interpreter
 */
interface OverpassApiService {

    @GET("interpreter")
    suspend fun query(
        @Query("data") overpassQuery: String
    ): OverpassResponse
}
