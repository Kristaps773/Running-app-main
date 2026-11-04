package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePolylineProgressTest {

    private val origin = GeoPoint(56.95, 24.10)
    /** Straight east polyline: 0 → 100 → 200 → 300 m. */
    private val line = listOf(
        origin,
        GeoUtils.offsetPoint(origin, 100.0, 90.0),
        GeoUtils.offsetPoint(origin, 200.0, 90.0),
        GeoUtils.offsetPoint(origin, 300.0, 90.0)
    )

    @Test
    fun snap_atStart_remainingIsFullLength() {
        val p = RoutePolylineProgress.project(line, origin)
        assertEquals(0, p.segmentIndex)
        assertEquals(0.0, p.distanceAlongM, 1.0)
        assertEquals(300.0, p.remainingDistanceM, 2.0)
        assertEquals(0.0, p.traveledDistanceM, 1.0)
    }

    @Test
    fun snap_midway_splitsTraveledAndRemaining() {
        val mid = GeoUtils.offsetPoint(origin, 150.0, 90.0)
        val p = RoutePolylineProgress.project(line, mid)
        assertEquals(150.0, p.distanceAlongM, 3.0)
        assertEquals(150.0, p.remainingDistanceM, 3.0)
        assertEquals(150.0, p.traveledDistanceM, 3.0)
    }

    @Test
    fun snap_offPath_projectsToNearestSegment() {
        val beside = GeoUtils.offsetPoint(
            GeoUtils.offsetPoint(origin, 100.0, 90.0),
            25.0,
            0.0
        )
        val p = RoutePolylineProgress.project(line, beside)
        assertEquals(100.0, p.distanceAlongM, 5.0)
        assertTrue(p.distanceToPathM in 20.0..30.0)
    }

    @Test
    fun snap_pastEnd_clampsToFinish() {
        val past = GeoUtils.offsetPoint(origin, 400.0, 90.0)
        val p = RoutePolylineProgress.project(line, past)
        assertEquals(300.0, p.distanceAlongM, 3.0)
        assertEquals(0.0, p.remainingDistanceM, 2.0)
    }

    @Test
    fun progressBucket_stableWithinWindow() {
        val a = RoutePolylineProgress.progressBucket(distanceAlongM = 10.0, bucketSizeM = 70.0)
        val b = RoutePolylineProgress.progressBucket(distanceAlongM = 60.0, bucketSizeM = 70.0)
        val c = RoutePolylineProgress.progressBucket(distanceAlongM = 75.0, bucketSizeM = 70.0)
        assertEquals(a, b)
        assertTrue(c != a)
    }

    @Test
    fun remainingFraction_monotoneAlongPath() {
        val near = RoutePolylineProgress.project(line, GeoUtils.offsetPoint(origin, 50.0, 90.0))
        val far = RoutePolylineProgress.project(line, GeoUtils.offsetPoint(origin, 250.0, 90.0))
        // Fraction 0 at runner, 1 at end of remaining — near runner has more remaining length.
        assertTrue(near.remainingDistanceM > far.remainingDistanceM)
        val tNearEnd = RoutePolylineProgress.remainingColorT(
            distanceAlongPointM = near.distanceAlongM + near.remainingDistanceM,
            progress = near
        )
        val tNearStart = RoutePolylineProgress.remainingColorT(
            distanceAlongPointM = near.distanceAlongM,
            progress = near
        )
        assertEquals(0.0, tNearStart, 0.02)
        assertEquals(1.0, tNearEnd, 0.02)
        assertTrue(tNearStart < tNearEnd)
    }

    @Test
    fun sliceRemaining_startsAtSnap() {
        val mid = GeoUtils.offsetPoint(origin, 150.0, 90.0)
        val p = RoutePolylineProgress.project(line, mid)
        val remaining = RoutePolylineProgress.remainingPoints(line, p)
        assertTrue(remaining.size >= 2)
        assertEquals(0.0, GeoUtils.distanceMeters(remaining.first(), mid), 3.0)
        assertEquals(0.0, GeoUtils.distanceMeters(remaining.last(), line.last()), 1.0)
    }

    @Test
    fun emptyPolyline_returnsZeroProgress() {
        val p = RoutePolylineProgress.project(emptyList(), origin)
        assertEquals(0.0, p.distanceAlongM, 0.0)
        assertEquals(0.0, p.remainingDistanceM, 0.0)
    }
}
