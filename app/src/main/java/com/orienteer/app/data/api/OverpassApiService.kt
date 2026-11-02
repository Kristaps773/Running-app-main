package com.orienteer.app.data.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Overpass API for querying OpenStreetMap point-of-interest data.
 * Returns [ResponseBody]; callers parse with Gson + [com.orienteer.app.data.model.OverpassResponse].
 * Public endpoint: https://overpass-api.de/api/interpreter
 */
interface OverpassApiService {

    @GET("interpreter")
    suspend fun query(
        @Query("data") overpassQuery: String
    ): ResponseBody
}
