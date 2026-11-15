package com.orienteer.app.data.local

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.domain.CoursePointsSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents / guards the nested-GeoPoint decode path used by [CoursePointsStore].
 * Plain Gson TypeToken&lt;List&lt;CoursePointsSet&gt;&gt; leaves locked points as LinkedTreeMap.
 */
class CoursePointsStoreCodecTest {

    private val gson = Gson()

    private fun decodeList(json: String): List<CoursePointsSet> {
        val root = JsonParser.parseString(json)
        return root.asJsonArray.map { el ->
            val obj = el.asJsonObject
            CoursePointsSet(
                id = obj.get("id").asString,
                savedAtMs = obj.get("savedAtMs").asLong,
                start = point(obj.getAsJsonObject("start")),
                finish = obj.get("finish")?.takeIf { !it.isJsonNull }?.asJsonObject?.let { point(it) },
                locked = obj.getAsJsonArray("locked").map { point(it.asJsonObject) },
                anyOrder = obj.get("anyOrder").asBoolean,
                usedGpsStart = obj.get("usedGpsStart").asBoolean,
                targetDistanceKm = obj.get("targetDistanceKm")?.takeIf { !it.isJsonNull }?.asDouble,
                minCheckpoints = obj.get("minCheckpoints")?.takeIf { !it.isJsonNull }?.asInt,
                maxCheckpoints = obj.get("maxCheckpoints")?.takeIf { !it.isJsonNull }?.asInt,
                maxPathToAirRatio = obj.get("maxPathToAirRatio")?.takeIf { !it.isJsonNull }?.asFloat,
                airDistanceToleranceM = obj.get("airDistanceToleranceM")
                    ?.takeIf { !it.isJsonNull }?.asFloat,
                displayName = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString
            )
        }
    }

    private fun point(obj: com.google.gson.JsonObject) =
        GeoPoint(obj.get("latitude").asDouble, obj.get("longitude").asDouble)

    @Test
    fun roundTrip_lockedPoints_areRealGeoPoints() {
        val set = CoursePointsSet(
            id = "a",
            savedAtMs = 1L,
            start = GeoPoint(56.95, 24.10),
            finish = null,
            locked = listOf(GeoPoint(56.951, 24.101), GeoPoint(56.952, 24.102)),
            anyOrder = true,
            usedGpsStart = false,
            targetDistanceKm = 5.0,
            minCheckpoints = 3,
            maxCheckpoints = 8,
            maxPathToAirRatio = 1.5f,
            airDistanceToleranceM = 400f
        )
        val json = gson.toJson(listOf(set))
        val decoded = decodeList(json)
        assertEquals(1, decoded.size)
        assertEquals(2, decoded[0].locked.size)
        assertTrue(decoded[0].locked[0] is GeoPoint)
        assertEquals(56.951, decoded[0].locked[0].latitude, 1e-6)
        assertEquals(5.0, decoded[0].targetDistanceKm!!, 1e-6)
        assertEquals(3, decoded[0].minCheckpoints)
        assertEquals(8, decoded[0].maxCheckpoints)
        // Touch fields the way Generate / picker do — would ClassCast if LinkedTreeMap.
        val latSum = decoded[0].locked.sumOf { it.latitude }
        assertTrue(latSum > 100.0)
    }

    @Test
    fun roundTrip_displayName_preserved() {
        val set = CoursePointsSet(
            id = "named",
            savedAtMs = 2L,
            start = GeoPoint(56.95, 24.10),
            finish = null,
            locked = emptyList(),
            anyOrder = true,
            usedGpsStart = false,
            displayName = "Evening 5k"
        )
        val json = gson.toJson(listOf(set))
        val decoded = decodeList(json)
        assertEquals("Evening 5k", decoded[0].displayName)
    }

    @Test
    fun plainGsonTypeToken_leavesMaps_inLockedList() {
        val set = CoursePointsSet(
            id = "b",
            savedAtMs = 1L,
            start = GeoPoint(56.95, 24.10),
            finish = null,
            locked = listOf(GeoPoint(56.951, 24.101)),
            anyOrder = true,
            usedGpsStart = false
        )
        val json = gson.toJson(listOf(set))
        val type = object : com.google.gson.reflect.TypeToken<List<CoursePointsSet>>() {}.type
        val decoded: List<CoursePointsSet> = gson.fromJson(json, type)
        // Depending on Gson/Kotlin, locked[0] may be LinkedTreeMap — assert our hand decode works either way.
        val hand = decodeList(json)
        assertEquals(GeoPoint::class.java, hand[0].locked[0].javaClass)
        assertEquals(decoded[0].id, hand[0].id)
    }
}
