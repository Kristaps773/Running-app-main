package com.orienteer.app.domain

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.orienteer.app.data.local.RouteGenerationPreferences
import com.orienteer.app.data.model.GeoPoint

/**
 * Generate knobs + user-locked pins stored with a [com.orienteer.app.data.model.Route]
 * so History → New from this run restores what the user set, not every control on the course.
 */
data class CourseReuseSnapshot(
    val anyOrder: Boolean = true,
    val minCheckpoints: Int? = null,
    val maxCheckpoints: Int? = null,
    val maxPathToAirRatio: Float? = null,
    val airDistanceToleranceM: Float? = null,
    /** User-locked intermediates at Generate time (not auto fillers). */
    val lockedPins: List<GeoPoint> = emptyList(),
    val start: GeoPoint? = null,
    /** Null = return-to-start. */
    val finish: GeoPoint? = null,
    val hasExplicitFinish: Boolean = false
) {
    fun toGenerationPreferences(
        fallback: RouteGenerationPreferences
    ): RouteGenerationPreferences = RouteGenerationPreferences(
        maxPathToAirRatio = maxPathToAirRatio ?: fallback.maxPathToAirRatio,
        airDistanceToleranceM = airDistanceToleranceM ?: fallback.airDistanceToleranceM,
        minCheckpoints = minCheckpoints ?: fallback.minCheckpoints,
        maxCheckpoints = maxCheckpoints ?: fallback.maxCheckpoints
    )

    companion object {
        fun from(
            anyOrder: Boolean,
            prefs: RouteGenerationPreferences,
            start: GeoPoint?,
            finish: GeoPoint?,
            lockedPins: List<GeoPoint>
        ) = CourseReuseSnapshot(
            anyOrder = anyOrder,
            minCheckpoints = prefs.minCheckpoints,
            maxCheckpoints = prefs.maxCheckpoints,
            maxPathToAirRatio = prefs.maxPathToAirRatio,
            airDistanceToleranceM = prefs.airDistanceToleranceM,
            lockedPins = lockedPins,
            start = start,
            finish = finish,
            hasExplicitFinish = finish != null
        )

        /**
         * Safe JSON decode — nested GeoPoints must not go through bare Gson
         * (LinkedTreeMap ClassCastException under R8).
         */
        fun fromJson(json: String?): CourseReuseSnapshot? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val obj = JsonParser.parseString(json).asJsonObject
                CourseReuseSnapshot(
                    anyOrder = obj.get("anyOrder")?.asBoolean ?: true,
                    minCheckpoints = obj.get("minCheckpoints")?.takeIf { !it.isJsonNull }?.asInt,
                    maxCheckpoints = obj.get("maxCheckpoints")?.takeIf { !it.isJsonNull }?.asInt,
                    maxPathToAirRatio = obj.get("maxPathToAirRatio")?.takeIf { !it.isJsonNull }?.asFloat,
                    airDistanceToleranceM = obj.get("airDistanceToleranceM")?.takeIf { !it.isJsonNull }?.asFloat,
                    lockedPins = decodePoints(obj.get("lockedPins")),
                    start = decodePoint(obj.get("start")),
                    finish = decodePoint(obj.get("finish")),
                    hasExplicitFinish = obj.get("hasExplicitFinish")?.asBoolean
                        ?: (obj.get("finish")?.isJsonObject == true)
                )
            }.getOrNull()
        }

        private fun decodePoints(el: JsonElement?): List<GeoPoint> {
            if (el == null || !el.isJsonArray) return emptyList()
            return el.asJsonArray.mapNotNull { decodePoint(it) }
        }

        private fun decodePoint(el: JsonElement?): GeoPoint? {
            if (el == null || el.isJsonNull || !el.isJsonObject) return null
            val o = el.asJsonObject
            val lat = o.get("latitude")?.asDouble ?: return null
            val lon = o.get("longitude")?.asDouble ?: return null
            return GeoPoint(lat, lon)
        }
    }
}
