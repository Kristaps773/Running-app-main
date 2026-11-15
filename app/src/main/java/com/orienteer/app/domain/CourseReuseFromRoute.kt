package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.local.RouteGenerationPreferences

/**
 * Payload to reuse a past [Route] for a new generate (History → New from this run).
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
     * Rebuild reuse payload from a saved route.
     *
     * Locked pins preference order:
     * 1. [CourseReuseSnapshot.lockedPins] (exact pins the user set before Generate)
     * 2. Checkpoints with [com.orienteer.app.data.model.Checkpoint.isFixed]
     * 3. Empty — never promote every intermediate control to a locked pin
     *
     * Start/finish prefer the snapshot when present so one-way vs loop matches Generate.
     */
    fun fromRoute(
        route: Route,
        anyOrderDefault: Boolean = true,
        minCheckpoints: Int? = null,
        maxCheckpoints: Int? = null,
        generationPrefs: RouteGenerationPreferences? = null
    ): CourseReusePayload? {
        if (route.checkpoints.isEmpty()) return null
        val snap = route.reuseSnapshot

        val start = snap?.start ?: route.checkpoints.first().position
        val finish = when {
            snap != null -> if (snap.hasExplicitFinish) snap.finish else null
            route.isOneWayCourse -> route.checkpoints.last().position
            else -> null
        }

        val locked = when {
            snap != null -> snap.lockedPins
            else -> {
                val fixed = route.checkpoints
                    .filter { !it.isStart && it.isFixed }
                    .map { it.position }
                fixed
            }
        }

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
