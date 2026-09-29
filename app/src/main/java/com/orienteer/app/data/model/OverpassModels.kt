package com.orienteer.app.data.model

import com.google.gson.annotations.SerializedName

data class OverpassResponse(
    @SerializedName("elements") val elements: List<OverpassElement>
)

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
        get() = tags?.entries
            ?.firstOrNull { (k, _) -> k in INTERESTING_KEYS }
            ?.let { (k, v) -> "$k=$v" }

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
            "shop", "craft", "sport"
        )
    }
}

data class OverpassCenter(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double
)
