package com.orienteer.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionTypographyTest {

    @Test
    fun primaryCtaUsesLabelLargeBold() {
        assertEquals("labelLarge", ActionTypography.PRIMARY_CTA_STYLE)
        assertEquals("Bold", ActionTypography.PRIMARY_CTA_WEIGHT)
    }

    @Test
    fun tertiaryTextUsesLabelMediumSemiBold() {
        assertEquals("labelMedium", ActionTypography.TERTIARY_TEXT_STYLE)
        assertEquals("SemiBold", ActionTypography.TERTIARY_TEXT_WEIGHT)
    }

    @Test
    fun distanceChipHeightMatchesMinTouchMinusTwo() {
        assertEquals(42f, ActionTypography.DistanceChipHeightDp)
    }
}
