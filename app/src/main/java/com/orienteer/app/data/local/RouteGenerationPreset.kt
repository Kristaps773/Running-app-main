package com.orienteer.app.data.local

import kotlin.math.abs

/**
 * Quick route-generation profiles from the Riga live matrix
 * (3 generations × 19 distances). Strict-gate miss rates are for the old
 * hard-reject behavior; the app still returns a best-effort route when OSRM succeeds.
 */
enum class RouteGenerationPreset(
    val maxPathToAirRatio: Float,
    val airDistanceToleranceM: Float
) {
    /** Closer to target air; ~1 in 5 strict-gate miss in matrix. */
    PRECISE(1.5f, 700f),

    /** Everyday default; ~1 in 10 strict-gate miss. */
    BALANCED(1.8f, 1500f),

    /** Easiest to generate; ~1 in 20 strict-gate miss. */
    FLEXIBLE(1.8f, 2000f);

    companion object {
        private const val RATIO_EPS = 0.06f
        private const val TOL_EPS_M = 60f

        fun matching(ratios: RouteGenerationRatios): RouteGenerationPreset? =
            entries.firstOrNull {
                abs(it.maxPathToAirRatio - ratios.maxPathToAirRatio.toFloat()) <= RATIO_EPS &&
                    abs(it.airDistanceToleranceM - ratios.airDistanceToleranceM.toFloat()) <= TOL_EPS_M
            }
    }
}
