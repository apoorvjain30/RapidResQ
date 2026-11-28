package com.rapidresq.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    background = CanvasBg,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder
)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = BrandNavy,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = Color.White,
    secondary = TextSecondary,
    onSecondary = BrandNavy,
    error = EmergencyRed,
    onError = BrandNavy,
    errorContainer = EmergencyRedSoft,
    onErrorContainer = EmergencyRed,
    background = CanvasBg,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextPrimary,
    outline = CardBorder
)

/**
 * RapidResQ Material 3 theme.
 * Dynamic wallpaper colors are intentionally disabled so emergency colors
 * (Red = Critical, Amber = High, Green = Resolved) remain consistent on every device.
 * For Pass 2, we enforce Light theme for high-contrast visibility.
 */
@Composable
fun RapidResQTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = CanvasBg.toArgb()
            window.navigationBarColor = CanvasBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    // Enforcing dark scheme for now to guarantee maximum contrast in field ops
    val colorScheme = DarkColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
