package com.orienteer.app.data.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.orienteer.app.data.api.OsrmApiService
import com.orienteer.app.data.api.parseJson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class OsrmLiveParseSmokeTest {
    @Test
    fun gson_parsesFossgisNearestWithScientificNodeIds() {
        val json = """{"code":"Ok","waypoints":[{"nodes":[1.246176072e+10,8867022866],"hint":"x","location":[24.09999,56.900013],"name":"","distance":1.57}]}"""
        val parsed = Gson().fromJson(json, OsrmNearestResponse::class.java)
        assertEquals("Ok", parsed.code)
        assertEquals(56.900013, parsed.waypointList().first().latitude, 1e-6)
        assertEquals(24.09999, parsed.waypointList().first().longitude, 1e-6)
    }

    @Test
    fun gson_parsesRouteWithNestedArrays() {
        val json = """
            {"code":"Ok","routes":[{"distance":100.0,"duration":50.0,
              "geometry":{"type":"LineString","coordinates":[[24.1,56.9],[24.11,56.91]]},
              "legs":[{"distance":100.0,"duration":50.0,"summary":""}]}],
             "waypoints":[{"location":[24.1,56.9],"distance":0.5},{"location":[24.11,56.91],"distance":1.0}]}
        """.trimIndent()
        val parsed = Gson().fromJson(json, OsrmRouteResponse::class.java)
        assertEquals("Ok", parsed.code)
        assertEquals(1, parsed.routeList().size)
        assertEquals(100.0, parsed.routeList().first().distance, 0.01)
        val points = parsed.routeList().first().geometry!!.toGeoPoints()
        assertEquals(2, points.size)
        assertEquals(56.9, points[0].latitude, 1e-6)
        // Nested type must be OsrmRoute, not LinkedTreeMap
        assertEquals(OsrmRoute::class.java, parsed.routeList().first().javaClass)
        assertEquals(OsrmWaypoint::class.java, parsed.waypointList().first().javaClass)
    }

    @Test
    fun retrofit_nearest_riga_parses() = runBlocking {
        val gson = GsonBuilder().create()
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "OrienteerRun/1.3.16 (Android; com.orienteer.app; routing)")
                        .build()
                )
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://routing.openstreetmap.de/routed-foot/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OsrmApiService::class.java)
        val nearest = api.nearest("24.1,56.9").parseJson(gson, OsrmNearestResponse::class.java)
        assertEquals("Ok", nearest.code)
        assertTrue(nearest.waypointList().isNotEmpty())
        assertEquals(OsrmWaypoint::class.java, nearest.waypointList().first().javaClass)
        val route = api.route("24.105,56.949;24.12,56.96;24.105,56.949")
            .parseJson(gson, OsrmRouteResponse::class.java)
        assertEquals("Ok", route.code)
        assertTrue(route.routeList().isNotEmpty())
        assertEquals(OsrmRoute::class.java, route.routeList().first().javaClass)
        assertTrue(route.routeList().first().geometry!!.toGeoPoints().size >= 2)
    }
}
