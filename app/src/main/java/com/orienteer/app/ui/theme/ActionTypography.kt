package com.orienteer.app.ui.theme

/**
 * Locked CTA / chip contracts for unit tests and Compose buttons.
 * Primary CTAs use Material [labelLarge] + Bold; chips share [DistanceChipHeightDp].
 */
object ActionTypography {
    const val PRIMARY_CTA_STYLE = "labelLarge"
    const val PRIMARY_CTA_WEIGHT = "Bold"
    const val TERTIARY_TEXT_STYLE = "labelMedium"
    const val TERTIARY_TEXT_WEIGHT = "SemiBold"

    /** Matches [OrienteerSpacing.minTouch] (44) − 2. */
    const val DistanceChipHeightDp = 42f
}
