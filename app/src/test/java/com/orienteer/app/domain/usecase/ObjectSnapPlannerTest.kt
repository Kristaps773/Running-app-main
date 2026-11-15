package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class ObjectSnapPlannerTest {

    private val anchor = GeoPoint(56.95, 24.10)

    @Test
    fun snapDistanceConstant_exposedForRegression() {
        assertEquals(100.0, ObjectSnapPlanner.SNAP_DISTANCE_M, 0.0)
        assertEquals(3, ObjectSnapPlanner.MAX_RATIO_REVERT_ATTEMPTS)
    }

    @Test
    fun objectWithinSnapRange_movesControlAndSetsOnObject() = runBlocking {
        val objectAt60m = GeoUtils.offsetPoint(anchor, 60.0, 90.0)
        val result = ObjectSnapPlanner.planSnaps(
            controls = listOf(
                ControlSnapRequest(
                    index = 0,
                    anchor = anchor,
                    candidates = listOf(
                        SnapObjectCandidate(
                            position = objectAt60m,
                            landmarkType = "amenity=bench",
                        )
                    ),
                )
            ),
            airDistanceM = 800.0,
            maxPathToAirRatio = 1.8,
            measurePathM = { _ -> 1_000.0 },
        )
        val outcome = result.outcomes.single()
        assertTrue(
            "control should snap onto object within routing budget",
            outcome.onObject
        )
        assertEquals(objectAt60m.latitude, outcome.position.latitude, 1e-6)
        assertEquals(objectAt60m.longitude, outcome.position.longitude, 1e-6)
        assertTrue(outcome.description.contains("At ", ignoreCase = false))
        assertEquals("amenity=bench", outcome.landmarkType)
    }

    @Test
    fun objectBeyondSnapRange_keepsAnchorNearLabelNotOnObject() = runBlocking {
        val objectAt150m = GeoUtils.offsetPoint(anchor, 150.0, 45.0)
        val result = ObjectSnapPlanner.planSnaps(
            controls = listOf(
                ControlSnapRequest(
                    index = 0,
                    anchor = anchor,
                    candidates = listOf(
                        SnapObjectCandidate(
                            position = objectAt150m,
                            landmarkType = "amenity=bench",
                        )
                    ),
                )
            ),
            airDistanceM = 800.0,
            maxPathToAirRatio = 1.8,
            measurePathM = { _ -> 1_000.0 },
        )
        val outcome = result.outcomes.single()
        assertFalse(outcome.onObject)
        assertEquals(anchor.latitude, outcome.position.latitude, 1e-6)
        assertEquals(anchor.longitude, outcome.position.longitude, 1e-6)
        assertTrue(outcome.description.contains("Near ", ignoreCase = false))
        assertEquals("amenity=bench", outcome.landmarkType)
    }

    @Test
    fun noObjectNearby_assignsGenericPathJunctionLandmark() = runBlocking {
        val result = ObjectSnapPlanner.planSnaps(
            controls = listOf(
                ControlSnapRequest(index = 0, anchor = anchor, candidates = emptyList())
            ),
            airDistanceM = 600.0,
            maxPathToAirRatio = 1.8,
            measurePathM = { _ -> 700.0 },
        )
        val outcome = result.outcomes.single()
        assertFalse(outcome.onObject)
        assertEquals(anchor.latitude, outcome.position.latitude, 1e-6)
        assertNotNull(outcome.landmarkType)
        assertEquals("generic=path_junction", outcome.landmarkType)
    }

    @Test
    fun rerouteOverMaxRatio_revertsLargestDisplacementThenAllLabelsKept() = runBlocking {
        val anchor0 = GeoPoint(56.950, 24.100)
        val anchor1 = GeoPoint(56.952, 24.102)
        val anchor2 = GeoPoint(56.954, 24.104)
        val snap0 = GeoUtils.offsetPoint(anchor0, 80.0, 0.0)
        val snap1 = GeoUtils.offsetPoint(anchor1, 50.0, 90.0)
        val snap2 = GeoUtils.offsetPoint(anchor2, 25.0, 180.0)

        val requests = listOf(
            ControlSnapRequest(
                index = 0,
                anchor = anchor0,
                candidates = listOf(SnapObjectCandidate(snap0, "amenity=bench")),
            ),
            ControlSnapRequest(
                index = 1,
                anchor = anchor1,
                candidates = listOf(SnapObjectCandidate(snap1, "natural=tree")),
            ),
            ControlSnapRequest(
                index = 2,
                anchor = anchor2,
                candidates = listOf(SnapObjectCandidate(snap2, "man_made=cairn")),
            ),
        )

        suspend fun pathFor(outcomes: List<ControlSnapOutcome>): Double {
            val snappedCount = outcomes.count { it.onObject }
            return when (snappedCount) {
                3 -> 2_000.0
                2 -> 1_650.0
                1 -> 1_350.0
                else -> 1_050.0
            }
        }

        val result = ObjectSnapPlanner.planSnaps(
            controls = requests,
            airDistanceM = 1_000.0,
            maxPathToAirRatio = 1.3,
            measurePathM = ::pathFor,
        )

        assertEquals(3, result.ratioRevertCount)
        assertTrue(result.fullRevert)
        assertEquals(3, result.outcomes.size)
        result.outcomes.zip(listOf(anchor0, anchor1, anchor2)).forEach { (outcome, expectedAnchor) ->
            assertFalse(outcome.onObject)
            assertEquals(expectedAnchor.latitude, outcome.position.latitude, 1e-6)
            assertEquals(expectedAnchor.longitude, outcome.position.longitude, 1e-6)
            assertTrue(
                "description label should survive reverts",
                outcome.description.contains("At ", ignoreCase = false) ||
                    outcome.description.contains("Near ", ignoreCase = false)
            )
            assertNotNull(outcome.landmarkType)
        }
    }
}