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
    fun hardCap_allowsFifteen() {
        assertEquals(15, CheckpointCountPlanner.MAX_CHECKPOINTS)
        assertEquals(15, CheckpointCountPlanner.pickCount(min = 15, max = 15, rng = Random(1)))
    }

    @Test
    fun averageLegMeters_dividesTargetByLegs() {
        // n intermediates → n+1 legs (including return / finish leg)
        assertEquals(500.0, CheckpointCountPlanner.averageLegMeters(targetDistanceM = 2_000.0, n = 3), 0.01)
        assertEquals(1_000.0, CheckpointCountPlanner.averageLegMeters(targetDistanceM = 5_000.0, n = 4), 0.01)
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
