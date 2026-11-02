package com.orienteer.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple HIG–inspired layout tokens for Compose (8pt grid).
 * Nested radius rule: inner = max(0, outer − padding).
 */
object OrienteerSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp

    /** Screen / sheet horizontal inset (HIG ~20–24pt). */
    val screenHorizontal: Dp = 20.dp

    /** Minimum interactive target (HIG 44pt). */
    val minTouch: Dp = 44.dp

    /** Primary CTA height. */
    val buttonHeight: Dp = 56.dp

    /** Gap between grouped list rows (hairline separator feel). */
    val listGap: Dp = 2.dp
}

object OrienteerRadius {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 22.dp
    val xl: Dp = 28.dp
    val sheet: Dp = 36.dp
    val full: Dp = 999.dp

    /** Card / panel outer continuous corner. */
    val card: Dp = lg

    /** Hero icon tile. */
    val hero: Dp = xl

    /** Concentric inner corner: outer radius minus distance to outer edge. */
    fun concentric(outer: Dp, inset: Dp): Dp {
        val result = outer.value - inset.value
        return if (result <= 0f) 0.dp else result.dp
    }
}
