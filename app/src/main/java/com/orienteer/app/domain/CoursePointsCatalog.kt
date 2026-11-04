package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint

/**
 * Pure list ops for Recent / Favorites course-point sets (no Android).
 * Persistence serializes [CoursePointsSet] via DataStore + Gson.
 */
data class CoursePointsSet(
    val id: String,
    val savedAtMs: Long,
    val start: GeoPoint?,
    val finish: GeoPoint?,
    val locked: List<GeoPoint>,
    val anyOrder: Boolean,
    val usedGpsStart: Boolean
)

sealed class FavoriteAddResult {
    data class Ok(val list: List<CoursePointsSet>) : FavoriteAddResult()
    data class Full(val list: List<CoursePointsSet>) : FavoriteAddResult()
}

object CoursePointsCatalog {
    const val MAX_RECENT = 5
    const val MAX_FAVORITES = 10

    fun autoLabel(set: CoursePointsSet): String {
        val n = set.locked.size
        val order = when {
            n < 2 -> ""
            set.anyOrder -> " · any"
            else -> " · in order"
        }
        val km = if (set.start != null && (set.finish != null || set.locked.isNotEmpty())) {
            val air = LockedCoursePoints.minVisitAirM(
                set.start,
                set.finish,
                set.locked,
                set.anyOrder && n >= 2
            )
            " · ~${"%.1f".format(air / 1000.0)} km"
        } else {
            ""
        }
        return when {
            n == 0 && set.finish != null -> "One-way$km"
            n == 0 -> "Start only$km"
            else -> "$n locked$order$km"
        }
    }

    /** Newest first; drop duplicates by geometry fingerprint; cap [MAX_RECENT]. */
    fun pushRecent(existing: List<CoursePointsSet>, incoming: CoursePointsSet): List<CoursePointsSet> {
        val finger = fingerprint(incoming)
        val without = existing.filterNot { fingerprint(it) == finger }
        return (listOf(incoming) + without).take(MAX_RECENT)
    }

    fun removeById(list: List<CoursePointsSet>, id: String): List<CoursePointsSet> =
        list.filterNot { it.id == id }

    /**
     * Add favorite if under cap. If full, returns [FavoriteAddResult.Full] without changing list
     * (UI must pick which id to replace, then call [replaceFavorite]).
     */
    fun addFavorite(existing: List<CoursePointsSet>, incoming: CoursePointsSet): FavoriteAddResult {
        val finger = fingerprint(incoming)
        val withoutDup = existing.filterNot { fingerprint(it) == finger }
        if (withoutDup.any { it.id == incoming.id }) {
            return FavoriteAddResult.Ok(
                listOf(incoming) + withoutDup.filterNot { it.id == incoming.id }
            )
        }
        if (withoutDup.size >= MAX_FAVORITES) {
            return FavoriteAddResult.Full(existing)
        }
        return FavoriteAddResult.Ok(listOf(incoming.copy(id = incoming.id)) + withoutDup)
    }

    fun replaceFavorite(
        existing: List<CoursePointsSet>,
        removeId: String,
        incoming: CoursePointsSet
    ): List<CoursePointsSet> {
        val without = existing.filterNot { it.id == removeId || fingerprint(it) == fingerprint(incoming) }
        return (listOf(incoming) + without).take(MAX_FAVORITES)
    }

    fun fingerprint(set: CoursePointsSet): String {
        fun pt(g: GeoPoint?) =
            g?.let { "${"%.5f".format(it.latitude)},${"%.5f".format(it.longitude)}" } ?: "-"
        val locks = set.locked.joinToString(";") { pt(it) }
        return "${pt(set.start)}|${pt(set.finish)}|$locks|${set.anyOrder}|${set.usedGpsStart}"
    }
}
