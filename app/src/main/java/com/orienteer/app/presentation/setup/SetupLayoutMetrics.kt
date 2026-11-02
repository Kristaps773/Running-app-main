package com.orienteer.app.presentation.setup

/**
 * Density-independent layout sizes for [SetupScreen], scaled to available height
 * so the home screen fits without scrolling on typical phones.
 * Values stay on the 8pt grid (4pt fine adjustments only where needed).
 */
data class SetupLayoutMetrics(
    val topSpacerDp: Float,
    val iconSizeDp: Float,
    val iconInnerDp: Float,
    val afterIconSpacerDp: Float,
    val subtitleBottomDp: Float,
    val cardPaddingDp: Float,
    val distanceFontSp: Float,
    val sectionSpacerDp: Float,
    val afterInfoSpacerDp: Float,
    val bottomContentPadDp: Float,
    /** Show tagline under brand title (tall viewports only). */
    val showTagline: Boolean,
)

fun setupLayoutMetrics(availableHeightDp: Float): SetupLayoutMetrics {
    val h = availableHeightDp.coerceAtLeast(0f)
    return when {
        h < 560f -> SetupLayoutMetrics(
            topSpacerDp = 4f,
            iconSizeDp = 36f,
            iconInnerDp = 20f,
            afterIconSpacerDp = 4f,
            subtitleBottomDp = 8f,
            cardPaddingDp = 12f,
            distanceFontSp = 28f,
            sectionSpacerDp = 8f,
            afterInfoSpacerDp = 8f,
            bottomContentPadDp = 8f,
            showTagline = false,
        )
        h < 700f -> SetupLayoutMetrics(
            topSpacerDp = 8f,
            iconSizeDp = 40f,
            iconInnerDp = 22f,
            afterIconSpacerDp = 6f,
            subtitleBottomDp = 8f,
            cardPaddingDp = 12f,
            distanceFontSp = 32f,
            sectionSpacerDp = 8f,
            afterInfoSpacerDp = 8f,
            bottomContentPadDp = 8f,
            showTagline = false,
        )
        else -> SetupLayoutMetrics(
            topSpacerDp = 12f,
            iconSizeDp = 48f,
            iconInnerDp = 28f,
            afterIconSpacerDp = 8f,
            subtitleBottomDp = 12f,
            cardPaddingDp = 16f,
            distanceFontSp = 36f,
            sectionSpacerDp = 12f,
            afterInfoSpacerDp = 12f,
            bottomContentPadDp = 8f,
            showTagline = true,
        )
    }
}
