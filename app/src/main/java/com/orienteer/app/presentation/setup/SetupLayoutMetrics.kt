package com.orienteer.app.presentation.setup

/**
 * Density-independent layout sizes for [SetupScreen], scaled to available height
 * so the home screen fits without scrolling on typical phones.
 * Values stay on the 8pt grid (4pt fine adjustments only where needed).
 *
 * Brand chrome is title-only (no hero icon) — DESIGN.md mobile compact + sticky CTA;
 * the dual checkpoint sliders need that vertical budget.
 */
data class SetupLayoutMetrics(
    val topSpacerDp: Float,
    val subtitleBottomDp: Float,
    val cardPaddingDp: Float,
    val distanceFontSp: Float,
    val sectionSpacerDp: Float,
    val afterInfoSpacerDp: Float,
    val bottomContentPadDp: Float,
    /** Show tagline under brand title (tall viewports only). */
    val showTagline: Boolean,
    /** Estimate chips under checkpoints — drop on short/mid phones to keep Settings visible. */
    val showEstimateStrip: Boolean,
)

fun setupLayoutMetrics(availableHeightDp: Float): SetupLayoutMetrics {
    val h = availableHeightDp.coerceAtLeast(0f)
    return when {
        h < 640f -> SetupLayoutMetrics(
            topSpacerDp = 4f,
            subtitleBottomDp = 6f,
            cardPaddingDp = 10f,
            distanceFontSp = 26f,
            sectionSpacerDp = 6f,
            afterInfoSpacerDp = 6f,
            bottomContentPadDp = 4f,
            showTagline = false,
            showEstimateStrip = false,
        )
        h < 760f -> SetupLayoutMetrics(
            topSpacerDp = 6f,
            subtitleBottomDp = 8f,
            cardPaddingDp = 12f,
            distanceFontSp = 30f,
            sectionSpacerDp = 8f,
            afterInfoSpacerDp = 8f,
            bottomContentPadDp = 6f,
            showTagline = false,
            showEstimateStrip = false,
        )
        else -> SetupLayoutMetrics(
            topSpacerDp = 10f,
            subtitleBottomDp = 10f,
            cardPaddingDp = 14f,
            distanceFontSp = 34f,
            sectionSpacerDp = 10f,
            afterInfoSpacerDp = 10f,
            bottomContentPadDp = 8f,
            showTagline = true,
            showEstimateStrip = true,
        )
    }
}
