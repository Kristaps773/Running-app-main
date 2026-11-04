package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import kotlin.math.max
import kotlin.random.Random

/**
 * Pure helpers for user-locked ("set in stone") intermediate checkpoints.
 * Start/finish are separate; locked points are always visited. Auto fillers may sit between them.
 *
 * Min/max slider window ownership lives in CheckpointCountPlanner — this only applies the
 * floor that [n] cannot be below [locked.size].
 */
object LockedCoursePoints {

    /** Soft cap so stones leave room under the generator intermediate ceiling. */
    const val MAX_LOCKED_INTERMEDIATES = 14

    sealed class IntermediateSlot {
        data class Locked(val point: GeoPoint) : IntermediateSlot()
        data object Auto : IntermediateSlot()
    }

    /** Visit order for stone pins. [anyOrder] ⇒ shuffle each call; else keep list order. */
    fun visitOrder(locked: List<GeoPoint>, anyOrder: Boolean, rng: Random): List<GeoPoint> {
        if (locked.isEmpty()) return emptyList()
        return if (anyOrder && locked.size >= 2) locked.shuffled(rng) else locked.toList()
    }

    /**
     * Full spine including origin and end (finish, or origin again for return-to-start).
     * [orderedLocked] must already be in visit order.
     */
    fun spine(
        origin: GeoPoint,
        orderedLocked: List<GeoPoint>,
        finish: GeoPoint?
    ): List<GeoPoint> {
        val end = finish ?: origin
        return buildList(orderedLocked.size + 2) {
            add(origin)
            addAll(orderedLocked)
            add(end)
        }
    }

    fun spineAirM(origin: GeoPoint, orderedLocked: List<GeoPoint>, finish: GeoPoint?): Double =
        CourseEndpoints.openChainAirM(spine(origin, orderedLocked, finish))

    /**
     * Minimum air distance to visit all locked points from [origin] ending at [finish]
     * (or back at [origin] when finish is null).
     *
     * - In order: list-order spine.
     * - Any order: nearest-neighbor tour through locked pins (fast approximation).
     */
    fun minVisitAirM(
        origin: GeoPoint,
        finish: GeoPoint?,
        locked: List<GeoPoint>,
        anyOrder: Boolean
    ): Double {
        if (locked.isEmpty()) {
            return if (finish != null) {
                CourseEndpoints.minAirDistanceM(origin, finish)
            } else {
                0.0
            }
        }
        if (!anyOrder || locked.size < 2) {
            return spineAirM(origin, locked, finish)
        }
        return nearestNeighborTourAirM(origin, locked, finish)
    }

    /** Nearest-neighbor: origin → closest unused locked… → finish ?: origin. */
    fun nearestNeighborTourAirM(
        origin: GeoPoint,
        locked: List<GeoPoint>,
        finish: GeoPoint?
    ): Double {
        if (locked.isEmpty()) {
            return if (finish != null) CourseEndpoints.minAirDistanceM(origin, finish) else 0.0
        }
        val remaining = locked.toMutableList()
        var current = origin
        var total = 0.0
        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestD = Double.MAX_VALUE
            for (i in remaining.indices) {
                val d = CourseEndpoints.minAirDistanceM(current, remaining[i])
                if (d < bestD) {
                    bestD = d
                    bestIdx = i
                }
            }
            total += bestD
            current = remaining.removeAt(bestIdx)
        }
        val end = finish ?: origin
        total += CourseEndpoints.minAirDistanceM(current, end)
        return total
    }

    /**
     * Intermediate count floor: never below the number of locked stones.
     * Does not own slider max — callers clamp with CheckpointCountPlanner.
     */
    fun effectiveMinIntermediates(lockedCount: Int, userMin: Int): Int =
        max(lockedCount, userMin).coerceAtLeast(0)

    /**
     * Distribute [fillerCount] auto slots across [legCount] legs (before first stone,
     * between stones, after last stone / before finish). Sum equals [fillerCount].
     * Prefer spreading across legs so detours can absorb target distance.
     */
    fun distributeFillers(legCount: Int, fillerCount: Int, rng: Random): List<Int> {
        require(legCount >= 1) { "legCount >= 1" }
        if (fillerCount <= 0) return List(legCount) { 0 }
        val counts = MutableList(legCount) { 0 }
        // First pass: one filler per leg until exhausted (spread).
        var remaining = fillerCount
        val order = (0 until legCount).shuffled(rng)
        for (i in order) {
            if (remaining == 0) break
            counts[i]++
            remaining--
        }
        // Remainder: random legs.
        repeat(remaining) {
            counts[rng.nextInt(legCount)]++
        }
        return counts
    }

    /**
     * Air budget for auto-points on each spine leg so the course can reach [targetDistanceM].
     *
     * Legs with zero fillers keep only their direct locked→locked air (not returned here as
     * a generate budget — caller just connects). Legs with fillers share
     * `target - sum(direct empty-leg air)` so short locked spines still stretch to the
     * user distance (e.g. 1.5 km spine + min 5 CPs → ~5 km).
     *
     * @return same size as [fillersPerLeg]; 0 where that leg has no autos.
     */
    fun fillerLegBudgetsM(
        legAirM: List<Double>,
        fillersPerLeg: List<Int>,
        targetDistanceM: Double
    ): List<Double> {
        require(legAirM.size == fillersPerLeg.size) {
            "legAir (${legAirM.size}) vs fillers (${fillersPerLeg.size})"
        }
        if (legAirM.isEmpty()) return emptyList()
        val directOnlyAir = legAirM.indices
            .filter { fillersPerLeg[it] <= 0 }
            .sumOf { legAirM[it].coerceAtLeast(0.0) }
        val stretchPool = (targetDistanceM - directOnlyAir).coerceAtLeast(0.0)
        val fillerLegs = fillersPerLeg.indices.filter { fillersPerLeg[it] > 0 }
        if (fillerLegs.isEmpty()) {
            return List(legAirM.size) { 0.0 }
        }
        // Weight by filler count × leg air so busy/long legs get more detour metres.
        val weights = fillerLegs.map { i ->
            fillersPerLeg[i].toDouble().coerceAtLeast(1.0) *
                legAirM[i].coerceAtLeast(1.0)
        }
        val weightSum = weights.sum().coerceAtLeast(1.0)
        val budgets = MutableList(legAirM.size) { 0.0 }
        fillerLegs.forEachIndexed { idx, leg ->
            budgets[leg] = stretchPool * (weights[idx] / weightSum)
        }
        return budgets
    }

    /**
     * Build intermediate slots: autos on each leg, locked stones between legs.
     * [totalIntermediates] includes locked + autos; must be >= locked.size.
     */
    fun planIntermediates(
        locked: List<GeoPoint>,
        anyOrder: Boolean,
        totalIntermediates: Int,
        rng: Random
    ): List<IntermediateSlot> {
        require(totalIntermediates >= locked.size) {
            "totalIntermediates ($totalIntermediates) < locked (${locked.size})"
        }
        val ordered = visitOrder(locked, anyOrder, rng)
        val fillers = totalIntermediates - ordered.size
        val legCount = ordered.size + 1
        val perLeg = distributeFillers(legCount, fillers, rng)
        return buildList(totalIntermediates) {
            for (i in 0 until legCount) {
                repeat(perLeg[i]) { add(IntermediateSlot.Auto) }
                if (i < ordered.size) add(IntermediateSlot.Locked(ordered[i]))
            }
        }
    }

    /**
     * Target-distance validation using S/F rules plus min visit air (ordered or NN).
     */
    fun evaluateTargetDistance(
        targetM: Double,
        origin: GeoPoint,
        finish: GeoPoint?,
        locked: List<GeoPoint>,
        anyOrder: Boolean = false
    ): CourseEndpoints.TargetDistanceDecision {
        if (locked.isEmpty()) {
            return CourseEndpoints.evaluateTargetDistance(targetM, origin, finish)
        }
        val air = minVisitAirM(origin, finish, locked, anyOrder)
        if (air > CourseEndpoints.MAX_TARGET_M) {
            return CourseEndpoints.TargetDistanceDecision.EndpointsExceedMax(
                air,
                CourseEndpoints.MAX_TARGET_M
            )
        }
        val floor = max(CourseEndpoints.MIN_TARGET_M, air)
        return when {
            targetM < floor ->
                CourseEndpoints.TargetDistanceDecision.BelowMinimum(floor, targetM)
            targetM > CourseEndpoints.MAX_TARGET_M ->
                CourseEndpoints.TargetDistanceDecision.AboveMaximum(
                    CourseEndpoints.MAX_TARGET_M,
                    targetM
                )
            else -> CourseEndpoints.TargetDistanceDecision.Ok(targetM)
        }
    }

    fun coerceTargetDistanceM(
        targetM: Double,
        origin: GeoPoint,
        finish: GeoPoint?,
        locked: List<GeoPoint>,
        anyOrder: Boolean = false
    ): Double {
        if (locked.isEmpty()) {
            return CourseEndpoints.coerceTargetDistanceM(targetM, origin, finish)
        }
        val air = minVisitAirM(origin, finish, locked, anyOrder)
        if (air > CourseEndpoints.MAX_TARGET_M) return CourseEndpoints.MAX_TARGET_M
        val floor = max(CourseEndpoints.MIN_TARGET_M, air)
        return targetM.coerceIn(floor, CourseEndpoints.MAX_TARGET_M)
    }
}
