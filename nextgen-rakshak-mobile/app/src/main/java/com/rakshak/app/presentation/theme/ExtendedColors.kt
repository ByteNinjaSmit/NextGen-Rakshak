package com.rakshak.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors Material's own [androidx.compose.material3.ColorScheme] has no
 * slot for, but that carry real meaning across this app: a "strong" match, an
 * offline/pending sighting. Kept container/on-container pairs like the rest of
 * the scheme so a badge or banner built from these is contrast-safe by
 * construction, the same way `errorContainer`/`onErrorContainer` are.
 */
data class RakshakExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val LightExtendedColors = RakshakExtendedColors(
    success = md_light_success,
    onSuccess = md_light_onSuccess,
    successContainer = md_light_successContainer,
    onSuccessContainer = md_light_onSuccessContainer,
    warning = md_light_warning,
    onWarning = md_light_onWarning,
    warningContainer = md_light_warningContainer,
    onWarningContainer = md_light_onWarningContainer,
)

val DarkExtendedColors = RakshakExtendedColors(
    success = md_dark_success,
    onSuccess = md_dark_onSuccess,
    successContainer = md_dark_successContainer,
    onSuccessContainer = md_dark_onSuccessContainer,
    warning = md_dark_warning,
    onWarning = md_dark_onWarning,
    warningContainer = md_dark_warningContainer,
    onWarningContainer = md_dark_onWarningContainer,
)

val LocalRakshakExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** `RakshakExtras.current.success`, alongside `MaterialTheme.colorScheme.*`. */
object RakshakExtras {
    val current: RakshakExtendedColors
        @Composable get() = LocalRakshakExtendedColors.current
}
