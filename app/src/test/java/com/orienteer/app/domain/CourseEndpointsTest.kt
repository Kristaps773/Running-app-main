package com.orienteer.app.domain

import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CourseEndpointsTest {

    private val start = GeoPoint(56.95, 24.10)

    /** ~5.0 km east of [start] (approx). */
    private val finishFiveKm: GeoPoint =
        GeoUtils.offsetPoint(start, 5_000.0, 90.0)

    @Test
    fun isOneWay_onlyWhenFinishExplicit() {
        assertFalse(CourseEndpoints.isOneWay(null))
        assertTrue(CourseEndpoints.isOneWay(finishFiveKm))
    }

    @Test
    fun minAir_matchesHaversine() {
        val expected = GeoUtils.distanceMeters(start, finishFiveKm)
        assertEquals(expected, CourseEndpoints.minAirDistanceM(start, finishFiveKm), 0.01)
        assertTrue(CourseEndpoints.minAirDistanceM(start, finishFiveKm) in 4_800.0..5_200.0)
    }

    @Test
    fun openChainAir_doesNotAddReturnLeg() {
        val mid = GeoUtils.offsetPoint(start, 2_000.0, 45.0)
        val chain = listOf(start, mid, finishFiveKm)
        val open = CourseEndpoints.openChainAirM(chain)
        val withReturn = open + GeoUtils.distanceMeters(finishFiveKm, start)
        assertTrue(open < withReturn - 100.0)
        assertEquals(
            GeoUtils.distanceMeters(start, mid) + GeoUtils.distanceMeters(mid, finishFiveKm),
            open,
            0.01
        )
    }

    @Test
    fun evaluate_returnToStart_onlyGlobalBounds() {
        val ok = CourseEndpoints.evaluateTargetDistance(5_000.0, start, finish = null)
        assertTrue(ok is CourseEndpoints.TargetDistanceDecision.Ok)

        val low = CourseEndpoints.evaluateTargetDistance(100.0, start, finish = null)
        assertTrue(low is CourseEndpoints.TargetDistanceDecision.BelowMinimum)

        val high = CourseEndpoints.evaluateTargetDistance(60_000.0, start, finish = null)
        assertTrue(high is CourseEndpoints.TargetDistanceDecision.AboveMaximum)
    }

    @Test
    fun evaluate_oneWay_rejectsBelowAirFloor() {
        val minAir = CourseEndpoints.minAirDistanceM(start, finishFiveKm)
        val decision = CourseEndpoints.evaluateTargetDistance(
            targetM = minAir - 500.0,
            start = start,
            finish = finishFiveKm
        )
        assertTrue(decision is CourseEndpoints.TargetDistanceDecision.BelowMinimum)
        val below = decision as CourseEndpoints.TargetDistanceDecision.BelowMinimum
        assertEquals(minAir, below.minM, 1.0)
    }

    @Test
    fun evaluate_oneWay_acceptsAtOrAboveFloor() {
        val minAir = CourseEndpoints.minAirDistanceM(start, finishFiveKm)
        val atFloor = CourseEndpoints.evaluateTargetDistance(minAir, start, finishFiveKm)
        assertTrue(atFloor is CourseEndpoints.TargetDistanceDecision.Ok)

        val above = CourseEndpoints.evaluateTargetDistance(minAir + 2_000.0, start, finishFiveKm)
        assertTrue(above is CourseEndpoints.TargetDistanceDecision.Ok)
    }

    @Test
    fun evaluate_oneWay_endpointsBeyondMax() {
        val far = GeoUtils.offsetPoint(start, 60_000.0, 0.0)
        val decision = CourseEndpoints.evaluateTargetDistance(50_000.0, start, far)
        assertTrue(decision is CourseEndpoints.TargetDistanceDecision.EndpointsExceedMax)
    }

    @Test
    fun coerce_bumpsTargetToFloor() {
        val minAir = CourseEndpoints.minAirDistanceM(start, finishFiveKm)
        val coerced = CourseEndpoints.coerceTargetDistanceM(3_000.0, start, finishFiveKm)
        assertEquals(minAir, coerced, 1.0)
    }

    @Test
    fun wouldAccept_sameRulesAsLoopAcceptance() {
        assertTrue(
            CourseEndpoints.wouldAccept(
                airM = 5000.0,
                pathM = 7000.0,
                targetM = 5000.0,
                maxRatio = 1.5,
                airToleranceM = 700.0
            )
        )
        assertFalse(
            CourseEndpoints.wouldAccept(
                airM = 5000.0,
                pathM = 9000.0,
                targetM = 5000.0,
                maxRatio = 1.5,
                airToleranceM = 2500.0
            )
        )
        assertFalse(
            CourseEndpoints.wouldAccept(
                airM = 5000.0,
                pathM = 0.0,
                targetM = 5000.0,
                maxRatio = 2.5,
                airToleranceM = 2500.0
            )
        )
    }

    @Test
    fun route_withFinishAt_oneWay_lastCheckpointAtFinish() {
        val mid = GeoUtils.offsetPoint(start, 2_000.0, 90.0)
        val draft = Route(
            id = "t",
            checkpoints = listOf(
                Checkpoint(0, start, "Start", isStart = true),
                Checkpoint(1, mid, "CP1", distanceFromPrev = 2000.0)
            ),
            polylinePoints = listOf(start, mid, finishFiveKm),
            targetDistanceM = 7000.0,
            totalDistanceM = 7000.0,
            estimatedDurationS = 1000.0
        )
        val finished = draft.withFinishCheckpoint(finishPosition = finishFiveKm)
        assertTrue(finished.hasTrailingFinish)
        assertEquals(finishFiveKm.latitude, finished.checkpoints.last().position.latitude, 1e-9)
        assertEquals(finishFiveKm.longitude, finished.checkpoints.last().position.longitude, 1e-9)
        // Open-chain air should not equal loop perimeter.
        val openAir = finished.airDistanceM
        val loopAir = openAir + GeoUtils.distanceMeters(finishFiveKm, start)
        assertTrue(abs(loopAir - openAir) > 100.0)
    }

    @Test
    fun route_withFinishCheckpoint_default_returnsToStart() {
        val mid = GeoUtils.offsetPoint(start, 1_000.0, 0.0)
        val draft = Route(
            id = "t",
            checkpoints = listOf(
                Checkpoint(0, start, "Start / Finish", isStart = true),
                Checkpoint(1, mid, "CP1", distanceFromPrev = 1000.0)
            ),
            polylinePoints = listOf(start, mid, start),
            targetDistanceM = 2000.0,
            totalDistanceM = 2000.0,
            estimatedDurationS = 500.0
        )
        val finished = draft.withFinishCheckpoint()
        assertTrue(finished.hasTrailingFinish)
        assertEquals(start.latitude, finished.checkpoints.last().position.latitude, 1e-9)
        assertEquals(start.longitude, finished.checkpoints.last().position.longitude, 1e-9)
    }
}
