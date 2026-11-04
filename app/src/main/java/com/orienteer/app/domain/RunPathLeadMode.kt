package com.orienteer.app.domain

/**
 * Active-run path lead (gradient + soft reroute + heading follow) requires both
 * walking-path and you-are-here preferences.
 */
object RunPathLeadMode {

    fun isEnabled(
        showWalkingPathPolyline: Boolean,
        showYouAreHereDuringRun: Boolean
    ): Boolean = showWalkingPathPolyline && showYouAreHereDuringRun
}
