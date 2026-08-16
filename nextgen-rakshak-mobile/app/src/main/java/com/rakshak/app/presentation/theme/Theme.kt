package com.rakshak.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = White,
    secondary = PrimaryBlue,
    error = AlertRed,
    tertiary = SafeGreen,
    background = White,
    surface = White,
    onSurface = DarkText,
    onBackground = DarkText,
    surfaceVariant = LightGray,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = White,
    secondary = PrimaryBlue,
    error = AlertRed,
    tertiary = SafeGreen,
    background = DarkText,
    surface = DarkText,
    onSurface = White,
    onBackground = White,
    surfaceVariant = LightGray,
)

@Composable
fun RakshakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
