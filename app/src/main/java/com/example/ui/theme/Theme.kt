package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val QuickyLightColorScheme = lightColorScheme(
    primary = QuickyPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8EE),
    onPrimaryContainer = QuickyPink,
    secondary = QuickyPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = QuickyPurple,
    tertiary = QuickyGold,
    onTertiary = Color.Black,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFCBD5E1),
    error = ActionPass,
    onError = Color.White
)

private val QuickyDarkColorScheme = darkColorScheme(
    primary = QuickyPink,
    onPrimary = Color.White,
    primaryContainer = QuickyPink.copy(alpha = 0.25f),
    onPrimaryContainer = QuickyCoral,
    secondary = QuickyPurple,
    onSecondary = Color.White,
    secondaryContainer = QuickyPurple.copy(alpha = 0.25f),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = QuickyGold,
    onTertiary = Color.Black,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkSurfaceHighlight,
    error = ActionPass,
    onError = Color.White
)

@Composable
fun QuickyTheme(
    darkTheme: Boolean = false, // PRD Section 3: Default is LIGHT THEME
    dynamicColor: Boolean = false, // Always preserve Quicky signature branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> QuickyDarkColorScheme
        else -> QuickyLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backwards compatibility alias
@Composable
fun SparkTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = QuickyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
