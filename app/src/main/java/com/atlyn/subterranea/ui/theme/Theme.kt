package com.atlyn.subterranea.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BiolumCyan,         // Primary action color
    secondary = BiolumGreen,      // Secondary accent
    tertiary = BiolumPurple,      // Tertiary accent
    background = CaveDark,        // Deep cave background
    surface = SurfaceMid,         // Card/surface backgrounds
    surfaceVariant = SurfaceLight,// Elevated surfaces
    onPrimary = CaveDark,         // Text on primary
    onSecondary = CaveDark,       // Text on secondary
    onBackground = BiolumCyanBright, // Text on background
    onSurface = BiolumCyanBright, // Text on surface
    error = AccentError,          // Error states
    onError = CaveDark
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SubterraneaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled for consistent cave theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Always use dark theme for cave atmosphere
    val colorScheme = DarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CaveDark.toArgb()
            window.navigationBarColor = CaveDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
