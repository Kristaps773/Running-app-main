package com.orienteer.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Smooth continuous-looking corners (iOS-inspired).
 *
 * Uses a slightly oversized circular radius so corners read softer than stock
 * Material [RoundedCornerShape], without Kotlin-2-only GitHub deps.
 * [smooth] 0f → exact radius, 1f → +35% optical soften (clamped to half-side).
 */
@Immutable
data class ContinuousCornerShape(
    val radius: Dp,
    val smooth: Float = 0.6f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val base = with(density) { radius.toPx() }
        if (base <= 0.5f || size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val boost = 1f + (smooth.coerceIn(0f, 1f) * 0.35f)
        val r = (base * boost).coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = Path().apply {
            addRoundRect(RoundRect(size.toRect(), CornerRadius(r, r)))
        }
        return Outline.Generic(path)
    }
}

fun concentricContinuous(outer: Dp, inset: Dp, smooth: Float = 0.6f): ContinuousCornerShape =
    ContinuousCornerShape(radius = OrienteerRadius.concentric(outer, inset), smooth = smooth)

/** Prefer continuous; fall back to Material rounded when a CornerBasedShape is required. */
fun continuousOrRounded(radius: Dp): RoundedCornerShape = RoundedCornerShape(radius)
