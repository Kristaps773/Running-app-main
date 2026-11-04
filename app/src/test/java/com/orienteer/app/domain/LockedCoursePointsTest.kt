package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LockedCoursePointsTest {

    private val origin = GeoPoint(56.95, 24.10)
    private val finish = GeoUtils.offsetPoint(origin, 5_000.0, 90.0)
    private val stoneA = GeoUtils.offsetPoint(origin, 1_500.0, 45.0)
    private val stoneB = GeoUtils.offsetPoint(origin, 3_000.0, 60.0)
    private val stoneC = GeoUtils.offsetPoint(origin, 2_000.0, 120.0)

    @Test
    fun visitOrder_inOrder_preservesSequence() {
        val locked = listOf(stoneA, stoneB, stoneC)
        val order = LockedCoursePoints.visitOrder(locked, anyOrder = false, rng = Random(1))
        assertEquals(locked, order)
    }

    @Test
    fun visitOrder_anyOrder_permutesButKeepsSet() {
        val locked = listOf(stoneA, stoneB, stoneC)
        val seen = mutableSetOf<List<GeoPoint>>()
        repeat(40) { i ->
            val order = LockedCoursePoints.visitOrder(locked, anyOrder = true, rng = Random(i.toLong()))
            assertEquals(locked.toSet(), order.toSet())
            assertEquals(3, order.size)
            seen += order
        }
        assertTrue("expected more than one permutation across seeds", seen.size > 1)
    }

    @Test
    fun visitOrder_singleStone_ignoresAnyOrder() {
        val locked = listOf(stoneA)
        assertEquals(
            locked,
            LockedCoursePoints.visitOrder(locked, anyOrder = true, rng = Random(99))
        )
    }

    @Test
    fun spine_loop_returnsToOrigin() {
        val s = LockedCoursePoints.spine(origin, listOf(stoneA, stoneB), finish = null)
        assertEquals(listOf(origin, stoneA, stoneB, origin), s)
    }

    @Test
    fun spine_oneWay_endsAtFinish() {
        val s = LockedCoursePoints.spine(origin, listOf(stoneA), finish)
        assertEquals(listOf(origin, stoneA, finish), s)
    }

    @Test
    fun spineAir_includesLegsBetweenStones() {
        val air = LockedCoursePoints.spineAirM(origin, listOf(stoneA, stoneB), finish)
        val expected = CourseEndpoints.openChainAirM(listOf(origin, stoneA, stoneB, finish))
        assertEquals(expected, air, 0.01)
        assertTrue(air > CourseEndpoints.minAirDistanceM(origin, finish))
    }

    @Test
    fun effectiveMin_neverBelowLockedCount() {
        assertEquals(3, LockedCoursePoints.effectiveMinIntermediates(lockedCount = 3, userMin = 2))
        assertEquals(5, LockedCoursePoints.effectiveMinIntermediates(lockedCount = 3, userMin = 5))
        assertEquals(0, LockedCoursePoints.effectiveMinIntermediates(lockedCount = 0, userMin = 0))
    }

    @Test
    fun distributeFillers_sumsToCount() {
        val parts = LockedCoursePoints.distributeFillers(legCount = 4, fillerCount = 5, rng = Random(7))
        assertEquals(4, parts.size)
        assertEquals(5, parts.sum())
        assertTrue(parts.all { it >= 0 })
    }

    @Test
    fun planIntermediates_keepsAllLocked_andFillersBetween() {
        val locked = listOf(stoneA, stoneB)
        val plan = LockedCoursePoints.planIntermediates(
            locked = locked,
            anyOrder = false,
            totalIntermediates = 5,
            rng = Random(3)
        )
        assertEquals(5, plan.size)
        val lockedSlots = plan.filterIsInstance<LockedCoursePoints.IntermediateSlot.Locked>()
        assertEquals(2, lockedSlots.size)
        assertEquals(listOf(stoneA, stoneB), lockedSlots.map { it.point })
        assertEquals(3, plan.count { it is LockedCoursePoints.IntermediateSlot.Auto })
        // First locked appears after zero or more autos; second after first — order preserved
        val idxA = plan.indexOfFirst {
            it is LockedCoursePoints.IntermediateSlot.Locked && it.point == stoneA
        }
        val idxB = plan.indexOfFirst {
            it is LockedCoursePoints.IntermediateSlot.Locked && it.point == stoneB
        }
        assertTrue(idxA < idxB)
    }

    @Test
    fun planIntermediates_anyOrder_stillIncludesEveryStone() {
        val locked = listOf(stoneA, stoneB, stoneC)
        val plan = LockedCoursePoints.planIntermediates(
            locked = locked,
            anyOrder = true,
            totalIntermediates = 3,
            rng = Random(11)
        )
        val points = plan.filterIsInstance<LockedCoursePoints.IntermediateSlot.Locked>().map { it.point }
        assertEquals(locked.toSet(), points.toSet())
        assertEquals(0, plan.count { it is LockedCoursePoints.IntermediateSlot.Auto })
    }

    @Test
    fun evaluate_withStones_raisesFloorToSpineAir() {
        val air = LockedCoursePoints.spineAirM(origin, listOf(stoneA, stoneB), finish)
        val low = LockedCoursePoints.evaluateTargetDistance(
            targetM = air - 200.0,
            origin = origin,
            finish = finish,
            locked = listOf(stoneA, stoneB)
        )
        assertTrue(low is CourseEndpoints.TargetDistanceDecision.BelowMinimum)
        val ok = LockedCoursePoints.evaluateTargetDistance(
            targetM = air + 500.0,
            origin = origin,
            finish = finish,
            locked = listOf(stoneA, stoneB)
        )
        assertTrue(ok is CourseEndpoints.TargetDistanceDecision.Ok)
    }

    @Test
    fun fillerLegBudgets_stretchShortSpineToTarget() {
        // 3 locked → 4 legs; put 2 fillers on first two legs only.
        val legAir = listOf(400.0, 400.0, 400.0, 400.0) // 1.6 km spine
        val perLeg = listOf(1, 1, 0, 0)
        val budgets = LockedCoursePoints.fillerLegBudgetsM(
            legAirM = legAir,
            fillersPerLeg = perLeg,
            targetDistanceM = 5_000.0
        )
        assertEquals(4, budgets.size)
        assertEquals(0.0, budgets[2], 0.01)
        assertEquals(0.0, budgets[3], 0.01)
        // Empty legs keep 800 m direct; stretch pool = 5000 - 800 = 4200 on filler legs.
        assertEquals(4_200.0, budgets[0] + budgets[1], 1.0)
        assertTrue(budgets[0] > 0 && budgets[1] > 0)
    }

    @Test
    fun effectiveMin_userMinFiveWithThreeLocked_isFive() {
        assertEquals(5, LockedCoursePoints.effectiveMinIntermediates(3, 5))
        val plan = LockedCoursePoints.planIntermediates(
            locked = listOf(stoneA, stoneB, stoneC),
            anyOrder = false,
            totalIntermediates = 5,
            rng = Random(1)
        )
        assertEquals(5, plan.size)
        assertEquals(2, plan.count { it is LockedCoursePoints.IntermediateSlot.Auto })
        assertEquals(3, plan.count { it is LockedCoursePoints.IntermediateSlot.Locked })
    }

    @Test
    fun evaluate_emptyLocked_delegatesToCourseEndpoints() {
        val floor = CourseEndpoints.minAirDistanceM(origin, finish)
        val d = LockedCoursePoints.evaluateTargetDistance(
            floor + 1_000.0,
            origin,
            finish,
            locked = emptyList()
        )
        assertTrue(d is CourseEndpoints.TargetDistanceDecision.Ok)
        val loop = LockedCoursePoints.evaluateTargetDistance(
            5_000.0,
            origin,
            finish = null,
            locked = emptyList()
        )
        assertTrue(loop is CourseEndpoints.TargetDistanceDecision.Ok)
    }

    @Test
    fun minVisitAir_inOrder_matchesSpine() {
        val locked = listOf(stoneA, stoneB, stoneC)
        val ordered = LockedCoursePoints.minVisitAirM(origin, finish, locked, anyOrder = false)
        assertEquals(LockedCoursePoints.spineAirM(origin, locked, finish), ordered, 0.01)
    }

    @Test
    fun minVisitAir_anyOrder_nn_notLongerThanWorstListOrder() {
        // Far-then-near list order is a long spine; NN should be shorter or equal.
        val far = GeoUtils.offsetPoint(origin, 4_000.0, 0.0)
        val near = GeoUtils.offsetPoint(origin, 500.0, 90.0)
        val locked = listOf(far, near)
        val ordered = LockedCoursePoints.minVisitAirM(origin, finish = null, locked, anyOrder = false)
        val nn = LockedCoursePoints.minVisitAirM(origin, finish = null, locked, anyOrder = true)
        assertTrue("NN ($nn) should be ≤ ordered spine ($ordered)", nn <= ordered + 0.01)
        assertEquals(
            LockedCoursePoints.nearestNeighborTourAirM(origin, locked, finish = null),
            nn,
            0.01
        )
    }

    @Test
    fun coerce_anyOrder_usesNnFloor() {
        val far = GeoUtils.offsetPoint(origin, 4_000.0, 0.0)
        val near = GeoUtils.offsetPoint(origin, 500.0, 90.0)
        val locked = listOf(far, near)
        val nnFloor = LockedCoursePoints.minVisitAirM(origin, null, locked, anyOrder = true)
        val coerced = LockedCoursePoints.coerceTargetDistanceM(
            targetM = 100.0,
            origin = origin,
            finish = null,
            locked = locked,
            anyOrder = true
        )
        assertEquals(maxOf(CourseEndpoints.MIN_TARGET_M, nnFloor), coerced, 0.01)
    }

    @Test
    fun evaluate_anyOrder_rejectsBelowNnFloor() {
        val far = GeoUtils.offsetPoint(origin, 4_000.0, 0.0)
        val near = GeoUtils.offsetPoint(origin, 500.0, 90.0)
        val locked = listOf(far, near)
        val nnFloor = LockedCoursePoints.minVisitAirM(origin, null, locked, anyOrder = true)
        val low = LockedCoursePoints.evaluateTargetDistance(
            targetM = nnFloor - 50.0,
            origin = origin,
            finish = null,
            locked = locked,
            anyOrder = true
        )
        assertTrue(low is CourseEndpoints.TargetDistanceDecision.BelowMinimum)
    }
}
