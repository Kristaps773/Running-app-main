package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectRingPlannerTest {

    private val origin = GeoPoint(56.95, 24.10)

    @Test
    fun seedRadius_shrinksForPathPrior() {
        val r = ObjectRingPlanner.seedRadiusM(5_000.0, maxPathToAirRatio = 1.8)
        val raw = 5_000.0 / (2.0 * PI)
        assertTrue(r < raw)
        assertTrue(r > 500.0)
    }

    @Test
    fun ringSlots_areEvenlySpacedAroundOrigin() {
        val slots = ObjectRingPlanner.ringSlots(origin, radiusM = 800.0, count = 4, bearing0Deg = 0.0)
        assertEquals(4, slots.size)
        slots.forEach { p ->
            assertTrue(abs(GeoUtils.distanceMeters(origin, p) - 800.0) < 5.0)
        }
    }

    @Test
    fun assignObjects_picksOnePerSlotWithSpacing() {
        val slots = ObjectRingPlanner.ringSlots(origin, 600.0, 3, 10.0)
        val pool = slots.flatMapIndexed { i, slot ->
            listOf(
                ObjectRingPlanner.RankedPoi(
                    position = GeoUtils.offsetPoint(slot, 30.0, 0.0),
                    weight = 2.0,
                    key = "near-$i"
                ),
                ObjectRingPlanner.RankedPoi(
                    position = GeoUtils.offsetPoint(slot, 200.0, 90.0),
                    weight = 5.0,
                    key = "far-$i"
                )
            )
        }
        val chosen = ObjectRingPlanner.assignObjectsToRing(
            origin, slots, pool, minSpacingM = 80.0
        )
        assertNotNull(chosen)
        assertEquals(3, chosen!!.size)
        assertEquals(3, chosen.map { it.key }.toSet().size)
    }

    @Test
    fun airSoftError_allowsShort_penalizesOverMax() {
        val target = 5_000.0
        val max = 5_700.0
        assertEquals(0.0, ObjectRingPlanner.airSoftErrorM(5_000.0, target, max), 0.01)
        assertTrue(ObjectRingPlanner.airSoftErrorM(4_000.0, target, max) < 10_000.0)
        assertTrue(ObjectRingPlanner.airSoftErrorM(6_000.0, target, max) > 10_000.0)
    }

    @Test
    fun adjustRadius_shrinksWhenPathAirHigh() {
        val next = ObjectRingPlanner.adjustRadiusM(
            currentR = 700.0,
            airM = 5_000.0,
            pathM = 9_000.0,
            targetAirM = 5_000.0,
            airMaxM = 5_700.0,
            maxPathToAir = 1.5
        )
        assertTrue(next < 700.0)
    }

    @Test
    fun swapWorst_returnsDifferentControl() {
        val slots = ObjectRingPlanner.ringSlots(origin, 500.0, 3, 0.0)
        val current = slots.mapIndexed { i, p ->
            ObjectRingPlanner.RankedPoi(p, 1.0, "c$i")
        }
        // Far enough from sibling controls to pass spacing, still near the ring.
        val unused = listOf(
            ObjectRingPlanner.RankedPoi(
                GeoUtils.offsetPoint(slots[0], 90.0, 0.0),
                2.0,
                "u0"
            )
        )
        val swapped = ObjectRingPlanner.swapWorstControl(
            origin, current, unused, 4_000.0, 4_500.0, 50.0, Random(1)
        )
        assertNotNull(swapped)
        assertTrue(swapped!!.any { it.key == "u0" })
    }
}
