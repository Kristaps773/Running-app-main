package com.orienteer.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPathLeadModeTest {

    @Test
    fun enabled_onlyWhenFootpathAndYouAreHere() {
        assertTrue(
            RunPathLeadMode.isEnabled(
                showWalkingPathPolyline = true,
                showYouAreHereDuringRun = true
            )
        )
    }

    @Test
    fun disabled_whenFootpathOff() {
        assertFalse(
            RunPathLeadMode.isEnabled(
                showWalkingPathPolyline = false,
                showYouAreHereDuringRun = true
            )
        )
    }

    @Test
    fun disabled_whenYouAreHereOff() {
        assertFalse(
            RunPathLeadMode.isEnabled(
                showWalkingPathPolyline = true,
                showYouAreHereDuringRun = false
            )
        )
    }

    @Test
    fun disabled_whenBothOff() {
        assertFalse(
            RunPathLeadMode.isEnabled(
                showWalkingPathPolyline = false,
                showYouAreHereDuringRun = false
            )
        )
    }
}
