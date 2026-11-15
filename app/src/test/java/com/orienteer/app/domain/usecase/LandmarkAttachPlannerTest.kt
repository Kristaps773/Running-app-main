package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkAttachPlannerTest {

    private val origin = GeoPoint(56.95, 24.10)

    @Test
    fun nearbyPoiWithinFocusRadius_movesOntoPoi() {
        val road = GeoUtils.offsetPoint(origin, 500.0, 0.0)
        val poi = GeoUtils.offsetPoint(road, 80.0, 90.0)
        val pool = listOf(
            LandmarkAttachPlanner.Candidate(position = poi, key = "bench-1", weight = 1.0)
        )
        val results = LandmarkAttachPlanner.attach(
            roadPositions = listOf(road),
            pool = pool,
            minSpacingM = 100.0,
            focusRadiusM = 200.0,
            nearRadiusM = 350.0
        )
        assertEquals(1, results.size)
        assertEquals(LandmarkAttachPlanner.Mode.FOCUS, results[0].mode)
        assertEquals(0, results[0].poolIndex)
        assertTrue(GeoUtils.distanceMeters(results[0].position, poi) < 1.0)
    }

    @Test
    fun defaultFocusRadius_movesOntoPoiOnlyWithin15m() {
        val road = GeoUtils.offsetPoint(origin, 500.0, 0.0)
        val poi = GeoUtils.offsetPoint(road, 10.0, 90.0)
        val pool = listOf(
            LandmarkAttachPlanner.Candidate(position = poi, key = "bench-1", weight = 1.0)
        )
        val results = LandmarkAttachPlanner.attach(
            roadPositions = listOf(road),
            pool = pool,
            minSpacingM = 20.0
        )
        assertEquals(LandmarkAttachPlanner.Mode.FOCUS, results[0].mode)
        assertTrue(GeoUtils.distanceMeters(results[0].position, poi) < 1.0)
    }

    @Test
    fun poiBeyondFocusButWithinNear_keepsRoadAndAttachesNear() {
        val road = GeoUtils.offsetPoint(origin, 500.0, 0.0)
        val poi = GeoUtils.offsetPoint(road, 280.0, 90.0)
        val pool = listOf(
            LandmarkAttachPlanner.Candidate(position = poi, key = "bench-1", weight = 1.0)
        )
        val results = LandmarkAttachPlanner.attach(
            roadPositions = listOf(road),
            pool = pool,
            minSpacingM = 100.0,
            focusRadiusM = 200.0,
            nearRadiusM = 350.0
        )
        assertEquals(LandmarkAttachPlanner.Mode.NEAR, results[0].mode)
        assertEquals(0, results[0].poolIndex)
        assertTrue(GeoUtils.distanceMeters(results[0].position, road) < 1.0)
    }

    @Test
    fun defaultNearRadius_keepsRoadForPoiBetween15And220m() {
        val road = GeoUtils.offsetPoint(origin, 500.0, 0.0)
        val poi = GeoUtils.offsetPoint(road, 50.0, 90.0)
        val pool = listOf(
            LandmarkAttachPlanner.Candidate(position = poi, key = "bench-1", weight = 1.0)
        )
        val results = LandmarkAttachPlanner.attach(
            roadPositions = listOf(road),
            pool = pool,
            minSpacingM = 20.0
        )
        assertEquals(LandmarkAttachPlanner.Mode.NEAR, results[0].mode)
        assertTrue(GeoUtils.distanceMeters(results[0].position, road) < 1.0)
    }

    @Test
    fun snapWouldViolateSpacing_staysNearWithoutMoving() {
        // Roads close enough that each has a focusable POI, but those POIs sit < spacing apart.
        val road1 = GeoUtils.offsetPoint(origin, 400.0, 0.0)
        val road2 = GeoUtils.offsetPoint(road1, 90.0, 0.0)
        val poi1 = GeoUtils.offsetPoint(road1, 40.0, 90.0)
        val poi2 = GeoUtils.offsetPoint(poi1, 30.0, 0.0) // ~30 m from focused poi1
        assertTrue(GeoUtils.distanceMeters(road2, poi2) < 200.0)
        val pool = listOf(
            LandmarkAttachPlanner.Candidate(poi1, "a", 1.0),
            LandmarkAttachPlanner.Candidate(poi2, "b", 1.0)
        )
        val results = LandmarkAttachPlanner.attach(
            roadPositions = listOf(road1, road2),
            pool = pool,
            minSpacingM = 100.0,
            focusRadiusM = 200.0,
            nearRadiusM = 400.0
        )
        assertEquals(LandmarkAttachPlanner.Mode.FOCUS, results[0].mode)
        // road2's nearest unused is poi2, but focusing would be <100 m from focused poi1
        assertEquals(LandmarkAttachPlanner.Mode.NEAR, results[1].mode)
        assertTrue(GeoUtils.distanceMeters(results[1].position, road2) < 1.0)
        assertNotNull(results[1].poolIndex)
    }

    @Test
    fun emptyPool_returnsNoneKeepingRoad() {
        val road = GeoUtils.offsetPoint(origin, 300.0, 45.0)
        val results = LandmarkAttachPlanner.attach(
            roadPositions = listOf(road),
            pool = emptyList(),
            minSpacingM = 100.0
        )
        assertEquals(LandmarkAttachPlanner.Mode.NONE, results[0].mode)
        assertNull(results[0].poolIndex)
        assertTrue(GeoUtils.distanceMeters(results[0].position, road) < 1.0)
    }

    @Test
    fun nearestPoiWins_notHigherWeightFartherAway() {
        val road = GeoUtils.offsetPoint(origin, 500.0, 0.0)
        val nearBench = GeoUtils.offsetPoint(road, 60.0, 90.0)
        val farMemorial = GeoUtils.offsetPoint(road, 150.0, 90.0)
        val pool = listOf(
            LandmarkAttachPlanner.Candidate(farMemorial, "memorial", weight = 5.0),
            LandmarkAttachPlanner.Candidate(nearBench, "bench", weight = 1.0)
        )
        val results = LandmarkAttachPlanner.attach(
            roadPositions = listOf(road),
            pool = pool,
            minSpacingM = 80.0,
            focusRadiusM = 200.0,
            nearRadiusM = 350.0
        )
        assertEquals(LandmarkAttachPlanner.Mode.FOCUS, results[0].mode)
        assertEquals(1, results[0].poolIndex) // nearer bench, not heavier memorial
        assertTrue(GeoUtils.distanceMeters(results[0].position, nearBench) < 1.0)
    }

    @Test
    fun lockedControl_neverMovesButCanAttachNear() {
        val road = GeoUtils.offsetPoint(origin, 500.0, 0.0)
        val poi = GeoUtils.offsetPoint(road, 40.0, 90.0)
        val pool = listOf(
            LandmarkAttachPlanner.Candidate(poi, "bench", 1.0)
        )
        val results = LandmarkAttachPlanner.attach(
            roadPositions = listOf(road),
            pool = pool,
            minSpacingM = 80.0,
            focusRadiusM = 200.0,
            nearRadiusM = 350.0,
            lockedFlags = listOf(true)
        )
        assertEquals(LandmarkAttachPlanner.Mode.NEAR, results[0].mode)
        assertTrue(GeoUtils.distanceMeters(results[0].position, road) < 1.0)
        assertEquals(0, results[0].poolIndex)
    }
}
