package com.orienteer.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val OrienteerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val OrienteerBottomSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
val OrienteerCardShape = RoundedCornerShape(16.dp)
val OrienteerChipShape = RoundedCornerShape(999.dp)
