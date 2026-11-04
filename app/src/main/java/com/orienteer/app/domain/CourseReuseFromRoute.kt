package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.local.RouteGenerationPreferences

/**
 * Payload to reuse a past [Route] for a new generate (History → New from this run).
 * Intermediate controls become locked pins (all non-start / non-trailing-finish).
 */
data class CourseReusePayload(
    val start: GeoPoint,
    val finish: GeoPoint?,
    val locked: List<GeoPoint>,
    val anyOrder: Boolean,
    val targetDistanceKm: Double,
    val minCheckpoints: Int?,
    val maxCheckpoints: Int?,
    val generationPrefs: RouteGenerationPreferences?
)

object CourseReuseFromRoute {

    /**
     * Rebuild reuse payload from any saved route.
     * Locked pins = every intermediate (not only [com.orienteer.app.data.model.Checkpoint.isFixed]).
     * Snapshot prefs win when present; otherwise [anyOrderDefault] / explicit overrides.
     */
    fun fromRoute(
        route: Route,
        anyOrderDefault: Boolean = true,
        minCheckpoints: Int? = null,
        maxCheckpoints: Int? = null,
        generationPrefs: RouteGenerationPreferences? = null
    ): CourseReusePayload? {
        if (route.checkpoints.isEmpty()) return null
        val start = route.checkpoints.first().position
        val finish = when {
            route.isOneWayCourse -> route.checkpoints.last().position
            else -> null
        }
        val locked = route.checkpoints
            .filter { !it.isStart }
            .map { it.position }
        val snap = route.reuseSnapshot
        val anyOrder = when {
            locked.size < 2 -> false
            snap != null -> snap.anyOrder
            else -> anyOrderDefault
        }
        val prefs = when {
            generationPrefs != null -> generationPrefs
            snap != null -> RouteGenerationPreferences(
                maxPathToAirRatio = snap.maxPathToAirRatio
                    ?: RouteGenerationPreferences().maxPathToAirRatio,
                airDistanceToleranceM = snap.airDistanceToleranceM
                    ?: RouteGenerationPreferences().airDistanceToleranceM,
                minCheckpoints = snap.minCheckpoints
                    ?: minCheckpoints
                    ?: RouteGenerationPreferences().minCheckpoints,
                maxCheckpoints = snap.maxCheckpoints
                    ?: maxCheckpoints
                    ?: RouteGenerationPreferences().maxCheckpoints
            )
            else -> null
        }
        return CourseReusePayload(
            start = start,
            finish = finish,
            locked = locked,
            anyOrder = anyOrder,
            targetDistanceKm = route.targetDistanceM / 1000.0,
            minCheckpoints = snap?.minCheckpoints ?: minCheckpoints,
            maxCheckpoints = snap?.maxCheckpoints ?: maxCheckpoints,
            generationPrefs = prefs
        )
    }
}
