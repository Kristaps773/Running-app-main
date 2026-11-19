package com.orienteer.app.presentation.map

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirLegCrossingGapsTest {

    @Test
    fun segmentIntersection_findsCrossing() {
        val a1 = GeoPoint(57.0, 24.0)
        val a2 = GeoPoint(57.01, 24.01)
        val b1 = GeoPoint(57.01, 24.0)
        val b2 = GeoPoint(57.0, 24.01)
        val hit = AirLegCrossingGaps.segmentIntersection(a1, a2, b1, b2)
        assertNotNull(hit)
        assertTrue(hit!!.latitude in 57.0..57.01)
        assertTrue(hit.longitude in 24.0..24.01)
    }

    @Test
    fun segmentIntersection_nullWhenParallel() {
        val a1 = GeoPoint(57.0, 24.0)
        val a2 = GeoPoint(57.01, 24.0)
        val b1 = GeoPoint(57.0, 24.01)
        val b2 = GeoPoint(57.01, 24.01)
        assertNull(AirLegCrossingGaps.segmentIntersection(a1, a2, b1, b2))
    }

    @Test
    fun gappedSegments_breaksLaterLegOnly() {
        val legEarly = GeoPoint(57.0, 24.0) to GeoPoint(57.01, 24.01)
        val legLate = GeoPoint(57.01, 24.0) to GeoPoint(57.0, 24.01)
        val pieces = AirLegCrossingGaps.gappedSegments(listOf(legEarly, legLate))
        // Early leg intact + late leg split into 2.
        assertEquals(3, pieces.size)
        assertEquals(legEarly.first.latitude, pieces[0].first.latitude, 1e-9)
        assertEquals(legEarly.second.latitude, pieces[0].second.latitude, 1e-9)
        val lateLen =
            GeoUtils.distanceMeters(pieces[1].first, pieces[1].second) +
                GeoUtils.distanceMeters(pieces[2].first, pieces[2].second)
        val fullLate = GeoUtils.distanceMeters(legLate.first, legLate.second)
        assertTrue(
            "gap should remove ~2× half-gap from late leg",
            lateLen < fullLate - AirLegCrossingGaps.GAP_HALF_M
        )
    }

    @Test
    fun gappedSegments_adjacentLegsUntouched() {
        val a = GeoPoint(57.0, 24.0)
        val b = GeoPoint(57.005, 24.005)
        val c = GeoPoint(57.01, 24.0)
        val pieces = AirLegCrossingGaps.gappedSegments(listOf(a to b, b to c))
        assertEquals(2, pieces.size)
    }

    @Test
    fun gappedPieces_exposesCuttingBearingOnBevels() {
        val legEarly = GeoPoint(57.0, 24.0) to GeoPoint(57.01, 24.01)
        val legLate = GeoPoint(57.01, 24.0) to GeoPoint(57.0, 24.01)
        val pieces = AirLegCrossingGaps.gappedPieces(listOf(legEarly, legLate))
        assertEquals(3, pieces.size)
        // Early leg: no bevels.
        assertEquals(null, pieces[0].startCutBearingDeg)
        assertEquals(null, pieces[0].endCutBearingDeg)
        // Late leg halves: gap faces carry the early leg's bearing.
        val earlyBearing = GeoUtils.bearingDegrees(legEarly.first, legEarly.second)
        assertEquals(null, pieces[1].startCutBearingDeg)
        assertEquals(earlyBearing, pieces[1].endCutBearingDeg!!, 1.0)
        assertEquals(earlyBearing, pieces[2].startCutBearingDeg!!, 1.0)
        assertEquals(null, pieces[2].endCutBearingDeg)
    }

    @Test
    fun alongHalfForCrossing_widensWhenNearlyParallel() {
        val perpendicular = AirLegCrossingGaps.alongHalfForCrossing(0.0, 90.0, 7.0)
        val shallow = AirLegCrossingGaps.alongHalfForCrossing(0.0, 15.0, 7.0)
        assertEquals(7.0, perpendicular, 0.2)
        assertTrue(shallow > perpendicular)
    }

    @Test
    fun gappedPieces_breaksAroundMidLegSymbol() {
        // Long N–S leg with a start-triangle sized obstacle on the path (not an endpoint).
        val from = GeoPoint(57.0, 24.0)
        val to = GeoPoint(57.02, 24.0)
        val mid = GeoUtils.offsetPoint(from, GeoUtils.distanceMeters(from, to) / 2.0, 0.0)
        val obstacle = AirLegCrossingGaps.SymbolObstacle(mid, radiusM = 40.0)
        val pieces = AirLegCrossingGaps.gappedPieces(
            legs = listOf(from to to),
            symbols = listOf(obstacle)
        )
        assertEquals(2, pieces.size)
        val drawn =
            GeoUtils.distanceMeters(pieces[0].from, pieces[0].to) +
                GeoUtils.distanceMeters(pieces[1].from, pieces[1].to)
        val full = GeoUtils.distanceMeters(from, to)
        assertTrue("should clear ~2× radius through the symbol", drawn < full - 60.0)
        assertNotNull(pieces[0].endCutBearingDeg)
        assertNotNull(pieces[1].startCutBearingDeg)
    }

    @Test
    fun gappedPieces_ignoresSymbolAtOwnEndpoint() {
        val from = GeoPoint(57.0, 24.0)
        val to = GeoPoint(57.01, 24.0)
        // Obstacle sits just behind the shortened start — must not gap again.
        val nearStart = GeoUtils.offsetPoint(from, 5.0, 180.0)
        val pieces = AirLegCrossingGaps.gappedPieces(
            legs = listOf(from to to),
            symbols = listOf(AirLegCrossingGaps.SymbolObstacle(nearStart, 40.0))
        )
        assertEquals(1, pieces.size)
    }
}
