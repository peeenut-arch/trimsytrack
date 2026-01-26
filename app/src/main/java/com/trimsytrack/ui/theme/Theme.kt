package com.trimsytrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val TrimsyLightColors = lightColorScheme(
    // Home tile accents (Manual / Review / Journal)
    // No blue: keep actions neutral.
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEEEEE),
    onPrimaryContainer = Color(0xFF111111),

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

private val TrimsyDarkColors = darkColorScheme(
    // No blue: keep actions neutral on dark UI.
    primary = Color.White,
    onPrimary = Color(0xFF0F1115),
    primaryContainer = Color(0xFF1B2130),
    onPrimaryContainer = Color(0xFFEAECEF),

    secondary = Color(0xFF2ECC71),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1EA85A),
    onSecondaryContainer = Color.White,

    tertiary = Color(0xFFFFC857),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFE2A62B),
    onTertiaryContainer = Color.Black,

    background = Color(0xFF0F1115),
    onBackground = Color(0xFFEAECEF),

    surface = Color(0xFF141821),
    onSurface = Color(0xFFEAECEF),

    surfaceVariant = Color(0xFF1B2130),
    onSurfaceVariant = Color(0xFFEAECEF),

    outline = Color.White.copy(alpha = 0.28f),
    outlineVariant = Color.White.copy(alpha = 0.18f),

    scrim = Color.Black,
)

private val OldUiShapes = Shapes()

private val NewUiShapes = Shapes(
    // Modern minimal: visibly softer corners throughout the UI.
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun TrimsyTheme(
    darkTheme: Boolean,
    useNewUi: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TrimsyDarkColors else TrimsyLightColors,
        shapes = if (useNewUi) NewUiShapes else OldUiShapes,
        content = content,
    )
}
