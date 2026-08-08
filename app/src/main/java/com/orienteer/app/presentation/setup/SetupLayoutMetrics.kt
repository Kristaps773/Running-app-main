package com.orienteer.app.presentation.setup

/**
 * Density-independent layout sizes for [SetupScreen], scaled to available height
 * so the home screen fits without forced scrolling on short devices.
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
)

fun setupLayoutMetrics(availableHeightDp: Float): SetupLayoutMetrics {
    val h = availableHeightDp.coerceAtLeast(0f)
    return when {
        h < 560f -> SetupLayoutMetrics(
            topSpacerDp = 8f,
            iconSizeDp = 48f,
            iconInnerDp = 28f,
            afterIconSpacerDp = 8f,
            subtitleBottomDp = 12f,
            cardPaddingDp = 12f,
            distanceFontSp = 32f,
            sectionSpacerDp = 8f,
            afterInfoSpacerDp = 12f,
            bottomContentPadDp = 8f,
        )
        h < 700f -> SetupLayoutMetrics(
            topSpacerDp = 16f,
            iconSizeDp = 64f,
            iconInnerDp = 36f,
            afterIconSpacerDp = 12f,
            subtitleBottomDp = 16f,
            cardPaddingDp = 16f,
            distanceFontSp = 38f,
            sectionSpacerDp = 12f,
            afterInfoSpacerDp = 16f,
            bottomContentPadDp = 8f,
        )
        else -> SetupLayoutMetrics(
            topSpacerDp = 32f,
            iconSizeDp = 80f,
            iconInnerDp = 44f,
            afterIconSpacerDp = 16f,
            subtitleBottomDp = 24f,
            cardPaddingDp = 20f,
            distanceFontSp = 44f,
            sectionSpacerDp = 16f,
            afterInfoSpacerDp = 24f,
            bottomContentPadDp = 8f,
        )
    }
}
