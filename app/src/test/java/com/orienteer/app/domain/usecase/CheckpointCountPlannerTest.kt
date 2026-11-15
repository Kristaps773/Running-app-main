package com.orienteer.app.domain.usecase

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointCountPlannerTest {

    @Test
    fun pickCount_staysWithinMinMaxWindow() {
        val rng = Random(42)
        repeat(50) {
            val n = CheckpointCountPlanner.pickCount(min = 3, max = 6, rng = rng)
            assertTrue("n=$n out of 3..6", n in 3..6)
        }
    }

    @Test
    fun pickCount_minEqualsMax_isExact() {
        val rng = Random(0)
        repeat(20) {
            assertEquals(8, CheckpointCountPlanner.pickCount(min = 8, max = 8, rng = rng))
        }
    }

    @Test
    fun pickCount_coercesIntoHardBounds() {
        assertEquals(
            CheckpointCountPlanner.MAX_CHECKPOINTS,
            CheckpointCountPlanner.pickCount(min = 99, max = 99, rng = Random(1))
        )
        assertEquals(
            CheckpointCountPlanner.MIN_CHECKPOINTS,
            CheckpointCountPlanner.pickCount(min = 0, max = 0, rng = Random(1))
        )
        // Swapped min/max still yields a valid window
        val n = CheckpointCountPlanner.pickCount(min = 10, max = 4, rng = Random(7))
        assertTrue(n in CheckpointCountPlanner.MIN_CHECKPOINTS..CheckpointCountPlanner.MAX_CHECKPOINTS)
    }

    @Test
    fun hardCap_allowsFifteenWithoutDistanceCap() {
        assertEquals(15, CheckpointCountPlanner.MAX_CHECKPOINTS)
        assertEquals(15, CheckpointCountPlanner.pickCount(min = 15, max = 15, rng = Random(1)))
    }

    @Test
    fun maxCountForTarget_capsDenseCoursesOnShortDistance() {
        // ~4 km @ 1.5× must not allow 14 intermediates (tiny air legs → path/air blow-up).
        val maxN = CheckpointCountPlanner.maxCountForTarget(
            targetDistanceM = 4_000.0,
            maxPathToAirRatio = 1.5
        )
        assertTrue("maxN=$maxN should be well below 14 on 4 km", maxN < 14)
        assertTrue(maxN >= CheckpointCountPlanner.MIN_CHECKPOINTS)
    }

    @Test
    fun pickCount_withTarget_respectsDynamicCapEvenIfPrefsAllowFifteen() {
        val rng = Random(3)
        repeat(40) {
            val n = CheckpointCountPlanner.pickCount(
                min = 2,
                max = 15,
                targetDistanceM = 4_000.0,
                maxPathToAirRatio = 1.5,
                rng = rng
            )
            val dyn = CheckpointCountPlanner.maxCountForTarget(4_000.0, 1.5)
            assertTrue("n=$n > dynMax=$dyn", n <= dyn)
        }
    }

    @Test
    fun pickCount_higherRatio_allowsDenserThanStrictRatio() {
        val strict = CheckpointCountPlanner.maxCountForTarget(5_000.0, 1.2)
        val loose = CheckpointCountPlanner.maxCountForTarget(5_000.0, 2.4)
        assertTrue("loose=$loose should be >= strict=$strict", loose >= strict)
    }

    @Test
    fun averageLegMeters_dividesTargetByLegs() {
        // n intermediates → n+1 legs (including return / finish leg)
        assertEquals(500.0, CheckpointCountPlanner.averageLegMeters(targetDistanceM = 2_000.0, n = 3), 0.01)
        assertEquals(1_000.0, CheckpointCountPlanner.averageLegMeters(targetDistanceM = 5_000.0, n = 4), 0.01)
    }

    @Test
    fun randomSegmentLengths_sumToTarget_butNotAllEqual() {
        val rng = Random(11)
        val lengths = CheckpointCountPlanner.randomSegmentLengths(
            segmentCount = 10,
            targetM = 10_000.0,
            segmentMinM = 200.0,
            maxPathToAirRatio = 1.5,
            rng = rng
        )
        assertEquals(10, lengths.size)
        assertEquals(10_000.0, lengths.sum(), 1.0)
        // Must not be a flat 1 km grid — still a dynamic band around the median.
        assertTrue(
            "legs should vary, got ${lengths.distinctBy { (it / 10).toInt() }.size} buckets",
            lengths.maxOrNull()!! - lengths.minOrNull()!! > 50.0
        )
        val median = 1_000.0
        lengths.forEach { leg ->
            assertTrue("leg=$leg far from median=$median", leg in median * 0.25..median * 1.75)
        }
    }

    @Test
    fun segmentMinFloor_shrinksWhenDense() {
        val sparse = CheckpointCountPlanner.segmentMinFloorM(avgLegM = 1_000.0)
        val dense = CheckpointCountPlanner.segmentMinFloorM(avgLegM = 150.0)
        assertTrue("sparse=$sparse should be higher than dense=$dense", sparse > dense)
        assertTrue(dense >= CheckpointCountPlanner.ABSOLUTE_MIN_LEG_M)
        assertTrue(sparse <= 200.0)
    }

    @Test
    fun candidateSpacingFloor_shrinksWhenDense() {
        val sparse = CheckpointCountPlanner.candidateSpacingFloorM(avgLegM = 800.0)
        val dense = CheckpointCountPlanner.candidateSpacingFloorM(avgLegM = 100.0)
        assertTrue(sparse > dense)
        assertTrue(dense >= CheckpointCountPlanner.ABSOLUTE_MIN_SPACING_M)
        assertTrue(sparse <= 120.0)
    }

    @Test
    fun coerceWindow_enforcesMinLessOrEqualMax() {
        val (lo, hi) = CheckpointCountPlanner.coerceWindow(min = 7, max = 4)
        assertTrue(lo <= hi)
        assertTrue(lo >= CheckpointCountPlanner.MIN_CHECKPOINTS)
        assertTrue(hi <= CheckpointCountPlanner.MAX_CHECKPOINTS)
    }
}
