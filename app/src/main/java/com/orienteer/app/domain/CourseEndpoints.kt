package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import kotlin.math.max

/**
 * Pure helpers for return-to-start vs one-way (Start → Finish) courses.
 *
 * [finish] null means return-to-start (finish at start). A non-null [finish] means the user
 * chose their own finish → one-way; distance floor is air(S, F).
 */
object CourseEndpoints {

    const val MIN_TARGET_M = 500.0
    const val MAX_TARGET_M = 50_000.0

    /** User explicitly set a finish point → one-way course. */
    fun isOneWay(finish: GeoPoint?): Boolean = finish != null

    fun minAirDistanceM(start: GeoPoint, finish: GeoPoint): Double =
        GeoUtils.distanceMeters(start, finish)

    /** Open-chain air distance along ordered points (no forced return to first). */
    fun openChainAirM(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += GeoUtils.distanceMeters(points[i], points[i + 1])
        }
        return total
    }

    sealed class TargetDistanceDecision {
        data class Ok(val targetM: Double) : TargetDistanceDecision()
        data class BelowMinimum(val minM: Double, val requestedM: Double) : TargetDistanceDecision()
        data class AboveMaximum(val maxM: Double, val requestedM: Double) : TargetDistanceDecision()
        data class EndpointsExceedMax(val airM: Double, val maxM: Double) : TargetDistanceDecision()
    }

    /**
     * Validates [targetM] for generate / slider.
     * @param finish null = return-to-start (only 500 m–50 km bounds).
     */
    fun evaluateTargetDistance(
        targetM: Double,
        start: GeoPoint,
        finish: GeoPoint?
    ): TargetDistanceDecision {
        if (finish == null) {
            return when {
                targetM < MIN_TARGET_M ->
                    TargetDistanceDecision.BelowMinimum(MIN_TARGET_M, targetM)
                targetM > MAX_TARGET_M ->
                    TargetDistanceDecision.AboveMaximum(MAX_TARGET_M, targetM)
                else -> TargetDistanceDecision.Ok(targetM)
            }
        }
        val minAir = minAirDistanceM(start, finish)
        if (minAir > MAX_TARGET_M) {
            return TargetDistanceDecision.EndpointsExceedMax(minAir, MAX_TARGET_M)
        }
        val floor = max(MIN_TARGET_M, minAir)
        return when {
            targetM < floor -> TargetDistanceDecision.BelowMinimum(floor, targetM)
            targetM > MAX_TARGET_M -> TargetDistanceDecision.AboveMaximum(MAX_TARGET_M, targetM)
            else -> TargetDistanceDecision.Ok(targetM)
        }
    }

    /** Clamp target up to the S→F floor and into 500 m–50 km (for slider UI). */
    fun coerceTargetDistanceM(
        targetM: Double,
        start: GeoPoint,
        finish: GeoPoint?
    ): Double {
        if (finish == null) {
            return targetM.coerceIn(MIN_TARGET_M, MAX_TARGET_M)
        }
        val minAir = minAirDistanceM(start, finish)
        if (minAir > MAX_TARGET_M) return MAX_TARGET_M
        val floor = max(MIN_TARGET_M, minAir)
        return targetM.coerceIn(floor, MAX_TARGET_M)
    }

    /** Same acceptance gates as loop generation (path present, air tolerance, path/air ratio). */
    fun wouldAccept(
        airM: Double,
        pathM: Double,
        targetM: Double,
        maxRatio: Double,
        airToleranceM: Double
    ): Boolean {
        if (pathM <= 0.0) return false
        if (kotlin.math.abs(airM - targetM) > airToleranceM) return false
        if (pathM > airM.coerceAtLeast(1.0) * maxRatio) return false
        return true
    }
}
