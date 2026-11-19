package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun leadFadeT_brightOnlyWithinShortLookahead() {
        val near = RoutePolylineProgress.project(line, GeoUtils.offsetPoint(origin, 50.0, 90.0))
        val lookahead = RoutePolylineProgress.LEAD_LOOKAHEAD_M
        val tAtRunner = RoutePolylineProgress.leadFadeT(
            distanceAlongPointM = near.distanceAlongM,
            progress = near
        )
        val tAtLookahead = RoutePolylineProgress.leadFadeT(
            distanceAlongPointM = near.distanceAlongM + lookahead,
            progress = near
        )
        val tBeyond = RoutePolylineProgress.leadFadeT(
            distanceAlongPointM = near.distanceAlongM + lookahead + 50.0,
            progress = near
        )
        val tBehind = RoutePolylineProgress.leadFadeT(
            distanceAlongPointM = near.distanceAlongM - 20.0,
            progress = near
        )
        assertEquals(0.0, tAtRunner, 0.02)
        assertEquals(1.0, tAtLookahead, 0.02)
        assertEquals(1.0, tBeyond, 0.02)
        assertTrue(tBehind >= 1.0)
        assertEquals(180.0, lookahead, 0.0)
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

    @Test
    fun activeLeadRibbon_isOneContinuousSlice_notWholeOutAndBack() {
        // Out 200 m east, then back 200 m west (same corridor twice).
        val turn = GeoUtils.offsetPoint(origin, 200.0, 90.0)
        val outAndBack = listOf(
            origin,
            GeoUtils.offsetPoint(origin, 100.0, 90.0),
            turn,
            GeoUtils.offsetPoint(origin, 100.0, 90.0),
            origin
        )
        val midOut = GeoUtils.offsetPoint(origin, 80.0, 90.0)
        val progress = RoutePolylineProgress.project(outAndBack, midOut)
        val ribbon = RoutePolylineProgress.activeLeadRibbon(outAndBack, progress)
        assertTrue(ribbon.points.size >= 2)
        val ribbonLen = RoutePolylineProgress.pathLengthMeters(ribbon.points)
        val maxLen = RoutePolylineProgress.LEAD_BEHIND_M + RoutePolylineProgress.LEAD_LOOKAHEAD_M + 15.0
        assertTrue(
            "ribbon should be a short local object, was ${ribbonLen}m",
            ribbonLen <= maxLen
        )
        // Mid-out at ~80 m with 180 m lookahead reaches the turn (~200 m); that is OK.
        // Ribbon must still be one local slice, not the whole out-and-back (~400 m).
        assertTrue(ribbonLen < 250.0)
        assertEquals(0.0, GeoUtils.distanceMeters(ribbon.runnerPoint, midOut), 5.0)
    }

    @Test
    fun projectTowardNext_outAndBack_staysOnOutboundLeg() {
        val turn = GeoUtils.offsetPoint(origin, 200.0, 90.0)
        val finish = origin
        val outAndBack = listOf(
            origin,
            GeoUtils.offsetPoint(origin, 100.0, 90.0),
            turn,
            GeoUtils.offsetPoint(origin, 100.0, 90.0),
            finish
        )
        val checkpoints = listOf(origin, turn, finish)
        val midOut = GeoUtils.offsetPoint(origin, 80.0, 90.0)
        // Next CP is the turn (index 1) — must snap on outbound (~80 m), not return (~320 m).
        val ordered = RoutePolylineProgress.projectTowardNextCheckpoint(
            outAndBack,
            midOut,
            checkpoints,
            nextCheckpointIdx = 1
        )
        assertEquals(80.0, ordered.distanceAlongM, 8.0)
        assertTrue(
            "ordered snap should be before the turn",
            ordered.distanceAlongM < 200.0
        )
        val nearest = RoutePolylineProgress.project(outAndBack, midOut)
        // Nearest alone may flip; ordered must not follow a far return twin.
        assertTrue(abs(ordered.distanceAlongM - nearest.distanceAlongM) < 50.0 ||
            ordered.distanceAlongM < 150.0)
    }

    @Test
    fun leadRunnerToNextCheckpoint_farCp_isShortLookahead_notWholeLeg() {
        val far = GeoUtils.offsetPoint(origin, 500.0, 90.0)
        val line = listOf(
            origin,
            GeoUtils.offsetPoint(origin, 250.0, 90.0),
            far
        )
        val runner = GeoUtils.offsetPoint(origin, 40.0, 90.0)
        val ribbon = RoutePolylineProgress.leadRunnerToNextCheckpoint(
            polyline = line,
            runner = runner,
            checkpointPositions = listOf(origin, far),
            nextCheckpointIdx = 1
        )
        assertTrue(ribbon.points.size >= 2)
        assertEquals(0.0, GeoUtils.distanceMeters(ribbon.points.first(), runner), 1.0)
        val len = RoutePolylineProgress.pathLengthMeters(ribbon.points)
        assertTrue(
            "far CP lead should stay near lookahead, was ${len}m",
            len <= RoutePolylineProgress.LEAD_LOOKAHEAD_M + 40.0
        )
        assertTrue(
            "far CP lead must not end on the checkpoint",
            GeoUtils.distanceMeters(ribbon.points.last(), far) > 50.0
        )
    }

    @Test
    fun leadRunnerToNextCheckpoint_nearCp_stopsAtCheckpointCenter() {
        val near = GeoUtils.offsetPoint(origin, 120.0, 90.0)
        val line = listOf(
            origin,
            GeoUtils.offsetPoint(origin, 60.0, 90.0),
            near
        )
        val runner = GeoUtils.offsetPoint(origin, 80.0, 90.0)
        val ribbon = RoutePolylineProgress.leadRunnerToNextCheckpoint(
            polyline = line,
            runner = runner,
            checkpointPositions = listOf(origin, near),
            nextCheckpointIdx = 1
        )
        assertTrue(ribbon.points.size >= 2)
        assertEquals(0.0, GeoUtils.distanceMeters(ribbon.points.first(), runner), 1.0)
        assertEquals(0.0, GeoUtils.distanceMeters(ribbon.points.last(), near), 2.0)
    }

    @Test
    fun orderedWaypoints_assignReturnFinishAfterTurn() {
        val turn = GeoUtils.offsetPoint(origin, 200.0, 90.0)
        val outAndBack = listOf(
            origin,
            GeoUtils.offsetPoint(origin, 100.0, 90.0),
            turn,
            GeoUtils.offsetPoint(origin, 100.0, 90.0),
            origin
        )
        val alongs = RoutePolylineProgress.orderedWaypointAlongMeters(
            outAndBack,
            listOf(origin, turn, origin)
        )
        assertEquals(0.0, alongs[0], 5.0)
        assertEquals(200.0, alongs[1], 8.0)
        assertTrue("finish on return leg should be near 400 m, was ${alongs[2]}", alongs[2] > 350.0)
    }
}
