package com.rapidresq.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = BrandNavy,
    secondary = BrandNavy,
    onSecondary = Color.White,
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = EmergencyRedBg,
    onErrorContainer = EmergencyRed,
    background = LightBackground,
    onBackground = SlateTextPrimary,
    surface = LightSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    outline = SubtleBorder
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = BrandNavy,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF94A3B8),
    onSecondary = BrandNavy,
    error = Color(0xFFF87171),
    onError = BrandNavy,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = DarkBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569)
)

/**
 * RapidResQ Material 3 theme.
 * Dynamic wallpaper colors are intentionally disabled so emergency colors
 * (Red = Critical, Amber = High, Green = Resolved) remain consistent on every device.
 */
@Composable
fun RapidResQTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}