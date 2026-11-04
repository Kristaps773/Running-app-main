package com.orienteer.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFootpathRerouteTest {

    @Test
    fun shouldSwitch_whenSavingsAtLeast100m() {
        assertTrue(
            LiveFootpathReroute.shouldSwitch(
                remainingCurrentPathM = 1_000.0,
                candidatePathM = 900.0
            )
        )
        assertTrue(
            LiveFootpathReroute.shouldSwitch(
                remainingCurrentPathM = 500.0,
                candidatePathM = 400.0
            )
        )
    }

    @Test
    fun shouldSwitch_falseWhenSavingsBelow100m() {
        assertFalse(
            LiveFootpathReroute.shouldSwitch(
                remainingCurrentPathM = 1_000.0,
                candidatePathM = 950.0
            )
        )
        assertFalse(
            LiveFootpathReroute.shouldSwitch(
                remainingCurrentPathM = 500.0,
                candidatePathM = 401.0
            )
        )
    }

    @Test
    fun shouldSwitch_falseWhenCandidateLonger() {
        assertFalse(
            LiveFootpathReroute.shouldSwitch(
                remainingCurrentPathM = 800.0,
                candidatePathM = 900.0
            )
        )
    }

    @Test
    fun shouldSwitch_falseForNonFinite() {
        assertFalse(
            LiveFootpathReroute.shouldSwitch(
                remainingCurrentPathM = Double.NaN,
                candidatePathM = 100.0
            )
        )
        assertFalse(
            LiveFootpathReroute.shouldSwitch(
                remainingCurrentPathM = 500.0,
                candidatePathM = Double.POSITIVE_INFINITY
            )
        )
    }

    @Test
    fun minSavingsMeters_is100() {
        assertEquals100(LiveFootpathReroute.MIN_SAVINGS_M)
    }

    private fun assertEquals100(value: Double) {
        org.junit.Assert.assertEquals(100.0, value, 0.0)
    }
}
