package com.orienteer.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/** Material3 theme shapes (must be CornerBasedShape). */
val OrienteerShapes = Shapes(
    extraSmall = RoundedCornerShape(OrienteerRadius.xs),
    small = RoundedCornerShape(OrienteerRadius.sm),
    medium = RoundedCornerShape(OrienteerRadius.md),
    large = RoundedCornerShape(OrienteerRadius.lg),
    extraLarge = RoundedCornerShape(OrienteerRadius.xl)
)

val OrienteerBottomSheetShape = RoundedCornerShape(
    topStart = OrienteerRadius.sheet,
    topEnd = OrienteerRadius.sheet
)

/** Primary cards, buttons, hero tiles — iOS continuous corners. */
val OrienteerCardShape: Shape = ContinuousCornerShape(OrienteerRadius.card)

val OrienteerChipShape: Shape = ContinuousCornerShape(OrienteerRadius.sm)

val OrienteerHeroShape: Shape = ContinuousCornerShape(OrienteerRadius.hero)

/** Inner shape for content inset inside a card of [outer] radius. */
fun OrienteerInnerShape(outer: Dp, inset: Dp): Shape =
    concentricContinuous(outer, inset)
