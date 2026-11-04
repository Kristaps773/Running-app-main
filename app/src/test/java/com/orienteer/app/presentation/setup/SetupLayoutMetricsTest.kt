package com.orienteer.app.presentation.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupLayoutMetricsTest {

    @Test
    fun shortScreenUsesCompactSizes() {
        val metrics = setupLayoutMetrics(520f)

        assertEquals(4f, metrics.topSpacerDp)
        assertEquals(26f, metrics.distanceFontSp)
        assertFalse(metrics.showTagline)
        assertFalse(metrics.showEstimateStrip)
        assertTrue(metrics.bottomContentPadDp <= 8f)
    }

    @Test
    fun mediumScreenHidesEstimateToKeepSettingsVisible() {
        val metrics = setupLayoutMetrics(700f)

        assertEquals(6f, metrics.topSpacerDp)
        assertEquals(30f, metrics.distanceFontSp)
        assertFalse(metrics.showTagline)
        assertFalse(metrics.showEstimateStrip)
    }

    @Test
    fun tallScreenUsesComfortableSizes() {
        val metrics = setupLayoutMetrics(800f)

        assertEquals(10f, metrics.topSpacerDp)
        assertEquals(34f, metrics.distanceFontSp)
        assertTrue(metrics.showTagline)
        assertTrue(metrics.showEstimateStrip)
    }

    @Test
    fun shorterScreensNeverUseLargerChromeThanTallerOnes() {
        val short = setupLayoutMetrics(500f)
        val tall = setupLayoutMetrics(800f)

        assertTrue(short.topSpacerDp <= tall.topSpacerDp)
        assertTrue(short.distanceFontSp <= tall.distanceFontSp)
        assertTrue(short.sectionSpacerDp <= tall.sectionSpacerDp)
        assertTrue(short.cardPaddingDp <= tall.cardPaddingDp)
    }
}
