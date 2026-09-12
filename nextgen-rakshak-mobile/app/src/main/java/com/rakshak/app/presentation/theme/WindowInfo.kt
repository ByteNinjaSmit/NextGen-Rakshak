package com.rakshak.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

data class WindowInfo(
    val isLandscape: Boolean,
    val widthClass: WindowWidthClass,
    val widthDp: Int,
    val heightDp: Int,
)

/**
 * Cheap, config-driven substitute for `androidx.compose.material3.windowsizeclass`
 * (not a dependency of this module) — orientation plus a three-bucket width class
 * is all any screen here branches on: a phone in portrait, a phone rotated to
 * landscape, and a tablet/large-screen width in either orientation.
 */
@Composable
fun rememberWindowInfo(): WindowInfo {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp
        WindowInfo(
            isLandscape = widthDp > heightDp,
            widthClass = when {
                widthDp < 600 -> WindowWidthClass.COMPACT
                widthDp < 840 -> WindowWidthClass.MEDIUM
                else -> WindowWidthClass.EXPANDED
            },
            widthDp = widthDp,
            heightDp = heightDp,
        )
    }
}
