package com.orienteer.app.domain

import com.orienteer.app.data.local.RouteGenerationPreferences

/**
 * Optional knobs stored with a [com.orienteer.app.data.model.Route] so History
 * can rebuild generate constraints without guessing current Settings.
 */
data class CourseReuseSnapshot(
    val anyOrder: Boolean = true,
    val minCheckpoints: Int? = null,
    val maxCheckpoints: Int? = null,
    val maxPathToAirRatio: Float? = null,
    val airDistanceToleranceM: Float? = null
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
            prefs: RouteGenerationPreferences
        ) = CourseReuseSnapshot(
            anyOrder = anyOrder,
            minCheckpoints = prefs.minCheckpoints,
            maxCheckpoints = prefs.maxCheckpoints,
            maxPathToAirRatio = prefs.maxPathToAirRatio,
            airDistanceToleranceM = prefs.airDistanceToleranceM
        )
    }
}
