package com.rakshak.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Rakshak brand palette — Signal Red + Graphite.
 *
 * Signal red is the one color a volunteer scanning a crowd in daylight needs to
 * find instantly: it marks an active alert, a live scan, a confirmed match. It is
 * used sparingly against a neutral graphite scale so it keeps that meaning instead
 * of becoming wallpaper. Reject/danger uses a separate, darker red
 * ([DangerLight]/[DangerDark]) so "this is not the child" never reads as the same
 * color as the brand itself.
 *
 * Every role below is named for what it is, not where it is used — screens should
 * reference [RakshakColorScheme] / `MaterialTheme.colorScheme`, never these
 * constants directly.
 */

// ---- Core brand ----
val SignalRed = Color(0xFFE23D3D)
val SignalRedLight = Color(0xFFFF6B5C) // dark-theme primary: lighter for contrast on graphite

// ---- Light scheme ----
val md_light_primary = Color(0xFFE23D3D)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFFFDAD6)
val md_light_onPrimaryContainer = Color(0xFF410002)

val md_light_secondary = Color(0xFF5B6169)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFE1E2E6)
val md_light_onSecondaryContainer = Color(0xFF171C21)

val md_light_tertiary = Color(0xFF2E6F86)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFBFEAFF)
val md_light_onTertiaryContainer = Color(0xFF001F2A)

val md_light_error = Color(0xFF8E1F1F)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410001)

val md_light_background = Color(0xFFF8F7F6)
val md_light_onBackground = Color(0xFF121316)
val md_light_surface = Color(0xFFF8F7F6)
val md_light_onSurface = Color(0xFF121316)
val md_light_surfaceVariant = Color(0xFFE7E0DE)
val md_light_onSurfaceVariant = Color(0xFF4F4544)
val md_light_outline = Color(0xFF817473)
val md_light_outlineVariant = Color(0xFFD3C4C2)
val md_light_inverseSurface = Color(0xFF2E2F31)
val md_light_inverseOnSurface = Color(0xFFEFF0F2)
val md_light_inversePrimary = Color(0xFFFFB4A9)
val md_light_scrim = Color(0xFF000000)

val md_light_surfaceDim = Color(0xFFDBD9D8)
val md_light_surfaceBright = Color(0xFFF8F7F6)
val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_light_surfaceContainerLow = Color(0xFFF2F0EF)
val md_light_surfaceContainer = Color(0xFFECEAE9)
val md_light_surfaceContainerHigh = Color(0xFFE6E4E3)
val md_light_surfaceContainerHighest = Color(0xFFE0DEDD)

// ---- Dark scheme ----
val md_dark_primary = Color(0xFFFFB4A9)
val md_dark_onPrimary = Color(0xFF690003)
val md_dark_primaryContainer = Color(0xFFC5271E) // brand red kept vivid as the dark-theme "container" accent
val md_dark_onPrimaryContainer = Color(0xFFFFDAD6)

val md_dark_secondary = Color(0xFFC4C6CB)
val md_dark_onSecondary = Color(0xFF2C3237)
val md_dark_secondaryContainer = Color(0xFF43484E)
val md_dark_onSecondaryContainer = Color(0xFFE1E2E6)

val md_dark_tertiary = Color(0xFF9ACFEA)
val md_dark_onTertiary = Color(0xFF003546)
val md_dark_tertiaryContainer = Color(0xFF124F63)
val md_dark_onTertiaryContainer = Color(0xFFBFEAFF)

val md_dark_error = Color(0xFFFFB4A9)
val md_dark_onError = Color(0xFF690003)
val md_dark_errorContainer = Color(0xFF8E1F1F)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_dark_background = Color(0xFF17181B)
val md_dark_onBackground = Color(0xFFF1F1F2)
val md_dark_surface = Color(0xFF17181B)
val md_dark_onSurface = Color(0xFFF1F1F2)
val md_dark_surfaceVariant = Color(0xFF4F4544)
val md_dark_onSurfaceVariant = Color(0xFFD3C4C2)
val md_dark_outline = Color(0xFF9C8E8C)
val md_dark_outlineVariant = Color(0xFF4F4544)
val md_dark_inverseSurface = Color(0xFFE0DEDD)
val md_dark_inverseOnSurface = Color(0xFF2E2F31)
val md_dark_inversePrimary = Color(0xFFE23D3D)
val md_dark_scrim = Color(0xFF000000)

val md_dark_surfaceDim = Color(0xFF17181B)
val md_dark_surfaceBright = Color(0xFF3D3E41)
val md_dark_surfaceContainerLowest = Color(0xFF121316)
val md_dark_surfaceContainerLow = Color(0xFF1F2023)
val md_dark_surfaceContainer = Color(0xFF232427)
val md_dark_surfaceContainerHigh = Color(0xFF2D2E31)
val md_dark_surfaceContainerHighest = Color(0xFF38393C)

// ---- Semantic (not part of Material's role set, but load-bearing across the
// app: match confidence, sighting status, mesh/offline state) ----
val md_light_success = Color(0xFF2FA35C)
val md_light_onSuccess = Color(0xFFFFFFFF)
val md_light_successContainer = Color(0xFFB7F2C6)
val md_light_onSuccessContainer = Color(0xFF00210D)

val md_light_warning = Color(0xFFB37600)
val md_light_onWarning = Color(0xFFFFFFFF)
val md_light_warningContainer = Color(0xFFFFDDB0)
val md_light_onWarningContainer = Color(0xFF3A2600)

val md_dark_success = Color(0xFF8CDA9E)
val md_dark_onSuccess = Color(0xFF00391A)
val md_dark_successContainer = Color(0xFF157A3F)
val md_dark_onSuccessContainer = Color(0xFFB7F2C6)

val md_dark_warning = Color(0xFFF2BB5C)
val md_dark_onWarning = Color(0xFF3A2600)
val md_dark_warningContainer = Color(0xFF8A5A00)
val md_dark_onWarningContainer = Color(0xFFFFDDB0)
