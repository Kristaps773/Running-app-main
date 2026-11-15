package com.orienteer.app.domain.usecase

import kotlin.math.floor
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

    /**
     * Minimum average air leg so a course of length [targetDistanceM] with path/air
     * [maxPathToAirRatio] stays denser-than-needed without forcing tiny legs that
     * accumulate walking detours past the ratio budget.
     *
     * Lower ratio → longer legs; higher ratio → denser controls allowed.
     */
    fun minAverageAirLegM(maxPathToAirRatio: Double): Double {
        val r = maxPathToAirRatio.coerceIn(1.1, 2.5)
        return (380.0 * 1.5 / r).coerceIn(220.0, 500.0)
    }

    /**
     * Hard ceiling on intermediate count from distance + path/air — independent of UI prefs.
     * Legs = n + 1 (return/finish included).
     */
    fun maxCountForTarget(targetDistanceM: Double, maxPathToAirRatio: Double): Int {
        val target = targetDistanceM.coerceAtLeast(0.0)
        if (target <= 0.0) return MIN_CHECKPOINTS
        val minAvg = minAverageAirLegM(maxPathToAirRatio)
        val maxLegs = floor(target / minAvg).toInt().coerceAtLeast(MIN_CHECKPOINTS + 1)
        return (maxLegs - 1).coerceIn(MIN_CHECKPOINTS, MAX_CHECKPOINTS)
    }

    fun coerceWindow(min: Int, max: Int): Pair<Int, Int> {
        val lo = min.coerceIn(MIN_CHECKPOINTS, MAX_CHECKPOINTS)
        val hi = max.coerceIn(MIN_CHECKPOINTS, MAX_CHECKPOINTS)
        return if (lo <= hi) lo to hi else hi to lo
    }

    /** Prefs-only window (no distance awareness). Prefer [pickCount] with target + ratio. */
    fun pickCount(min: Int, max: Int, rng: Random): Int =
        pickCount(
            min = min,
            max = max,
            targetDistanceM = Double.POSITIVE_INFINITY,
            maxPathToAirRatio = 2.5,
            rng = rng
        )

    /**
     * Pick intermediate count inside prefs [min]..[max], further capped so average air
     * legs stay near a median that fits [targetDistanceM] under [maxPathToAirRatio].
     */
    fun pickCount(
        min: Int,
        max: Int,
        targetDistanceM: Double,
        maxPathToAirRatio: Double,
        rng: Random
    ): Int {
        val dynMax = maxCountForTarget(targetDistanceM, maxPathToAirRatio)
        val (lo0, hi0) = coerceWindow(min, max)
        val hi = min(hi0, dynMax)
        val lo = min(lo0, hi)
        return if (lo == hi) lo else rng.nextInt(lo, hi + 1)
    }

    /** Average air leg length for [n] intermediate controls (n+1 legs including return/finish). */
    fun averageLegMeters(targetDistanceM: Double, n: Int): Double {
        val legs = (n + 1).coerceAtLeast(1)
        return targetDistanceM.coerceAtLeast(0.0) / legs
    }

    /**
     * Random air-leg lengths that **sum to [targetM]** but are **not** equal splits.
     * Each leg is drawn in a band around the median (`targetM / segmentCount`),
     * clamped by [segmentMinM] / [segmentMaxM] and widened slightly when
     * [maxPathToAirRatio] allows more path wiggle.
     */
    fun randomSegmentLengths(
        segmentCount: Int,
        targetM: Double,
        segmentMinM: Double,
        maxPathToAirRatio: Double,
        rng: Random,
        segmentMaxM: Double = 2_000.0
    ): List<Double> {
        val n = segmentCount.coerceAtLeast(1)
        val target = targetM.coerceAtLeast(n * ABSOLUTE_MIN_LEG_M)
        val median = target / n
        val loFloor = segmentMinM.coerceAtLeast(ABSOLUTE_MIN_LEG_M)
        val hiCap = segmentMaxM.coerceAtLeast(loFloor + 1.0)
        // ±35% at strict ratio → up to ±70% when path/air is loose — still centered on median.
        val spread = (0.35 + (maxPathToAirRatio.coerceIn(1.1, 2.5) - 1.1) * 0.25)
            .coerceIn(0.35, 0.70)
        val lo = maxOf(loFloor, median * (1.0 - spread))
        val hi = minOf(hiCap, median * (1.0 + spread)).coerceAtLeast(lo + 1.0)
        val raw = List(n) { lo + rng.nextDouble() * (hi - lo) }
        val sum = raw.sum().coerceAtLeast(1e-6)
        return raw.map { it * (target / sum) }
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
