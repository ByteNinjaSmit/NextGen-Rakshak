package com.rakshak.app.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale (4dp base unit). Every screen previously picked its own
 * gap — 8, 10, 12, 14, 16 — with no system behind the choice. Six steps cover
 * every case that came up across the app: [xs] for icon-to-label gaps, [xxl] for
 * screen-edge margins.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Elevation scale, used with [androidx.compose.material3.Surface]'s tonalElevation for depth. */
object Elevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
    val level4 = 8.dp
    val level5 = 12.dp
}
