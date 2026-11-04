package com.orienteer.app.domain

/**
 * Decide whether a candidate OSRM footpath should replace the live guidance path.
 */
object LiveFootpathReroute {

    /** Switch only when the new path is at least this many metres shorter. */
    const val MIN_SAVINGS_M = 100.0

    fun shouldSwitch(remainingCurrentPathM: Double, candidatePathM: Double): Boolean {
        if (!remainingCurrentPathM.isFinite() || !candidatePathM.isFinite()) return false
        if (remainingCurrentPathM < 0.0 || candidatePathM < 0.0) return false
        val savings = remainingCurrentPathM - candidatePathM
        return savings >= MIN_SAVINGS_M
    }
}
