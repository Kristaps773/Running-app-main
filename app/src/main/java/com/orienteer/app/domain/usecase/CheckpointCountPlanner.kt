package com.orienteer.app.domain.usecase

import kotlin.math.min
import kotlin.random.Random

/**
 * Pure helpers for intermediate checkpoint count and density-aware spacing floors.
 * Start/finish are not included in [n].
 */
object CheckpointCountPlanner {
    const val MIN_CHECKPOINTS = 2
    const val MAX_CHECKPOINTS = 15

    const val DEFAULT_MIN_CHECKPOINTS = 3
    const val DEFAULT_MAX_CHECKPOINTS = 6

    const val ABSOLUTE_MIN_LEG_M = 50.0
    const val ABSOLUTE_MIN_SPACING_M = 40.0

    private const val NOMINAL_SEGMENT_MIN_M = 200.0
    private const val NOMINAL_CANDIDATE_SPACING_M = 120.0

    fun coerceWindow(min: Int, max: Int): Pair<Int, Int> {
        val lo = min.coerceIn(MIN_CHECKPOINTS, MAX_CHECKPOINTS)
        val hi = max.coerceIn(MIN_CHECKPOINTS, MAX_CHECKPOINTS)
        return if (lo <= hi) lo to hi else hi to lo
    }

    fun pickCount(min: Int, max: Int, rng: Random): Int {
        val (lo, hi) = coerceWindow(min, max)
        return if (lo == hi) lo else rng.nextInt(lo, hi + 1)
    }

    /** Average air leg length for [n] intermediate controls (n+1 legs including return/finish). */
    fun averageLegMeters(targetDistanceM: Double, n: Int): Double {
        val legs = (n + 1).coerceAtLeast(1)
        return targetDistanceM.coerceAtLeast(0.0) / legs
    }

    /**
     * Soft floor for segment length: shrinks with dense courses so high [n] can still place.
     * Never above the historic 200 m nominal; never below [ABSOLUTE_MIN_LEG_M].
     */
    fun segmentMinFloorM(avgLegM: Double): Double {
        val scaled = avgLegM * 0.5
        return min(NOMINAL_SEGMENT_MIN_M, scaled).coerceAtLeast(ABSOLUTE_MIN_LEG_M)
    }

    /**
     * Soft floor for candidate/checkpoint spacing: shrinks with density.
     * Never above the historic 120 m nominal; never below [ABSOLUTE_MIN_SPACING_M].
     */
    fun candidateSpacingFloorM(avgLegM: Double): Double {
        val scaled = avgLegM * 0.4
        return min(NOMINAL_CANDIDATE_SPACING_M, scaled).coerceAtLeast(ABSOLUTE_MIN_SPACING_M)
    }
}
