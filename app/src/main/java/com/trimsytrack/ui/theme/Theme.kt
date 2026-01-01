package com.trimsytrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TrimsyLightColors = lightColorScheme(
    // Home tile accents (Manual / Review / Journal)
    primary = Color(0xFF2F62FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF244FE0),
    onPrimaryContainer = Color.White,

    secondary = Color(0xFF2ECC71),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1EA85A),
    onSecondaryContainer = Color.White,

    tertiary = Color(0xFFFFC857),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFE2A62B),
    onTertiaryContainer = Color.Black,

    background = Color.White,
    onBackground = Color(0xFF121212),

    surface = Color.White,
    onSurface = Color(0xFF121212),

    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF121212),

    outline = Color.Black.copy(alpha = 0.28f),
    outlineVariant = Color.Black.copy(alpha = 0.18f),

    scrim = Color.Black,
)

@Composable
fun TrimsyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TrimsyLightColors,
        content = content
    )
}
