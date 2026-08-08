package com.orienteer.app.presentation.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupLayoutMetricsTest {

    @Test
    fun shortScreenUsesCompactSizes() {
        val metrics = setupLayoutMetrics(520f)

        assertEquals(8f, metrics.topSpacerDp)
        assertEquals(48f, metrics.iconSizeDp)
        assertEquals(32f, metrics.distanceFontSp)
        assertTrue(metrics.bottomContentPadDp <= 8f)
    }

    @Test
    fun mediumScreenUsesMidSizes() {
        val metrics = setupLayoutMetrics(640f)

        assertEquals(16f, metrics.topSpacerDp)
        assertEquals(64f, metrics.iconSizeDp)
        assertEquals(38f, metrics.distanceFontSp)
    }

    @Test
    fun tallScreenUsesComfortableSizes() {
        val metrics = setupLayoutMetrics(800f)

        assertEquals(32f, metrics.topSpacerDp)
        assertEquals(80f, metrics.iconSizeDp)
        assertEquals(44f, metrics.distanceFontSp)
    }

    @Test
    fun shorterScreensNeverUseLargerChromeThanTallerOnes() {
        val short = setupLayoutMetrics(500f)
        val tall = setupLayoutMetrics(800f)

        assertTrue(short.topSpacerDp <= tall.topSpacerDp)
        assertTrue(short.iconSizeDp <= tall.iconSizeDp)
        assertTrue(short.distanceFontSp <= tall.distanceFontSp)
        assertTrue(short.sectionSpacerDp <= tall.sectionSpacerDp)
    }
}
