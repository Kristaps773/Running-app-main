package com.orienteer.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline acceptance rules used by the route matrix (mirrors GenerateRouteUseCase). */
class RouteAcceptanceRulesTest {

    @Test
    fun acceptsWhenWithinAirAndRatio() {
        assertTrue(
            RouteGenerationMatrixTest.wouldAccept(
                airM = 5000.0,
                pathM = 7000.0,
                targetM = 5000.0,
                maxRatio = 1.5,
                airToleranceM = 700.0
            )
        )
    }

    @Test
    fun rejectsWhenAirOutsideTolerance() {
        assertFalse(
            RouteGenerationMatrixTest.wouldAccept(
                airM = 6000.0,
                pathM = 7000.0,
                targetM = 5000.0,
                maxRatio = 2.5,
                airToleranceM = 700.0
            )
        )
    }

    @Test
    fun rejectsWhenPathAirExceedsRatio() {
        assertFalse(
            RouteGenerationMatrixTest.wouldAccept(
                airM = 5000.0,
                pathM = 9000.0,
                targetM = 5000.0,
                maxRatio = 1.5,
                airToleranceM = 2500.0
            )
        )
    }

    @Test
    fun rejectsWhenNoPath() {
        assertFalse(
            RouteGenerationMatrixTest.wouldAccept(
                airM = 5000.0,
                pathM = 0.0,
                targetM = 5000.0,
                maxRatio = 2.5,
                airToleranceM = 2500.0
            )
        )
    }
}
