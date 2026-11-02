package com.orienteer.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orienteer.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the R8-safe Gson patterns used when decoding run history JSON.
 * Anonymous `object : TypeToken<...>()` crashes in release minify.
 */
class RunDetailGsonTest {

    private val gson = Gson()

    @Test
    fun decodeTrackedPoints_viaArrayClass() {
        val json = gson.toJson(listOf(GeoPoint(56.95, 24.1), GeoPoint(56.96, 24.11)))
        val points = gson.fromJson(json, Array<GeoPoint>::class.java).toList()
        assertEquals(2, points.size)
        assertEquals(56.95, points[0].latitude, 1e-6)
    }

    @Test
    fun decodeTimestamps_viaArrayClass() {
        val json = gson.toJson(listOf(1000L, 2000L))
        val times = gson.fromJson(json, Array<Long>::class.java).toList()
        assertEquals(listOf(1000L, 2000L), times)
    }

    @Test
    fun decodeCheckpointMap_viaGetParameterized() {
        val original = mapOf(1 to 1000L, 2 to 2500L)
        val json = gson.toJson(original)
        val type = TypeToken.getParameterized(
            MutableMap::class.java,
            Integer::class.java,
            java.lang.Long::class.java
        ).type
        val decoded: Map<Int, Long> = gson.fromJson(json, type) ?: emptyMap()
        assertEquals(1000L, decoded[1])
        assertEquals(2500L, decoded[2])
    }

    @Test
    fun decodeEmptyPayloads() {
        assertTrue(gson.fromJson("[]", Array<GeoPoint>::class.java).isEmpty())
        assertTrue(gson.fromJson("[]", Array<Long>::class.java).isEmpty())
        val type = TypeToken.getParameterized(
            MutableMap::class.java,
            Integer::class.java,
            java.lang.Long::class.java
        ).type
        val map: Map<Int, Long> = gson.fromJson("{}", type) ?: emptyMap()
        assertTrue(map.isEmpty())
    }

    @Test
    fun decodeSkippedCheckpointIds_viaIntArray() {
        val json = gson.toJson(listOf(1, 2))
        val skipped = gson.fromJson(json, IntArray::class.java).toSet()
        assertEquals(setOf(1, 2), skipped)
        assertTrue(gson.fromJson("[]", IntArray::class.java).isEmpty())
    }
}
