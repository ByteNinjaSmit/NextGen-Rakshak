package com.rakshak.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner-radius scale. Every screen used to pick its own radius per component
 * (12.dp here, 16.dp there, 20.dp, 25.dp for a "pill" button) with no relationship
 * between them — these five steps are the only radii the app should need.
 */
val RakshakShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** A fully-rounded "pill" shape for primary CTAs and status chips, sized to the row height. */
val PillShape = RoundedCornerShape(50)
