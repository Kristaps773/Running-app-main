package com.orienteer.app.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class OverpassResponse(
    // Array (not List) so R8+Gson keeps element typing in release builds.
    @SerializedName("elements") val elements: Array<OverpassElement> = emptyArray()
)

@Keep
data class OverpassElement(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: Long,
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lon") val lon: Double?,
    @SerializedName("tags") val tags: Map<String, String>?,
    @SerializedName("center") val center: OverpassCenter?
) {
    /** Human-readable tag like "amenity=bench" or "historic=monument". */
    val primaryTag: String?
        get() = OverpassLandmarkTags.primaryTagFromTags(tags)

    val displayName: String?
        get() = tags?.get("name")

    fun coordinateOrNull(): GeoPoint? {
        val latValue = lat ?: center?.lat
        val lonValue = lon ?: center?.lon
        if (latValue == null || lonValue == null) return null
        return GeoPoint(latValue, lonValue)
    }

    companion object {
        val INTERESTING_KEYS = setOf(
            "amenity", "historic", "tourism", "natural",
            "leisure", "man_made", "barrier", "highway",
            "waterway", "power", "building", "landuse",
            "shop", "craft", "sport"
        )
    }
}

@Keep
data class OverpassCenter(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double
)
