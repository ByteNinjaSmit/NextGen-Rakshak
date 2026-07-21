package com.rakshak.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Slate900,
    onPrimary = White,
    secondary = Slate700,
    error = AlertRed,
    tertiary = SafeGreen,
    background = Slate100,
    surface = White,
)

private val DarkColors = darkColorScheme(
    primary = Slate100,
    onPrimary = Slate900,
    secondary = Slate700,
    error = AlertRed,
    tertiary = SafeGreen,
    background = Slate900,
    surface = Slate900,
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
