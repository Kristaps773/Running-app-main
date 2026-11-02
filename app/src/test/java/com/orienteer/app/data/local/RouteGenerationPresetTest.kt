package com.orienteer.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteGenerationPresetTest {

    @Test
    fun matching_detectsBalancedDefaults() {
        val ratios = RouteGenerationRatios(
            maxPathToAirRatio = 1.8,
            airDistanceToleranceM = 1500.0
        )
        assertEquals(RouteGenerationPreset.BALANCED, RouteGenerationPreset.matching(ratios))
    }

    @Test
    fun matching_detectsPreciseAndFlexible() {
        assertEquals(
            RouteGenerationPreset.PRECISE,
            RouteGenerationPreset.matching(RouteGenerationRatios(1.5, 700.0))
        )
        assertEquals(
            RouteGenerationPreset.FLEXIBLE,
            RouteGenerationPreset.matching(RouteGenerationRatios(1.8, 2000.0))
        )
    }

    @Test
    fun matching_returnsNullForCustom() {
        assertNull(
            RouteGenerationPreset.matching(RouteGenerationRatios(2.2, 900.0))
        )
    }
}
