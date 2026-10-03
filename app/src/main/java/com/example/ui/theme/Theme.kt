package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ============================================================================
// CASSY THEME — Quicky v2 "Premium Cassy" Edition (PRD §5)
//
// Light: warm ivory background, rosewood primary, champagne accents.
// Dark: deep ink background, soft-rose primary, light-champagne accents.
// Shapes follow the "rounded geometry" pillar of the Cassy identity.
// ============================================================================

private val CassyLightColorScheme = lightColorScheme(
    primary = CassyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF9E4E8),
    onPrimaryContainer = Color(0xFF7E3644),
    secondary = CassyAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E7D8),
    onSecondaryContainer = Color(0xFF6B5330),
    tertiary = CassySuccess,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2EEE5),
    onTertiaryContainer = Color(0xFF2E5138),
    background = CassyBackground,
    onBackground = CassyTextPrimary,
    surface = CassySurface,
    onSurface = CassyTextPrimary,
    surfaceVariant = CassySurfaceElevated,
    onSurfaceVariant = CassyTextSecondary,
    surfaceTint = CassyPrimary,
    inverseSurface = Color(0xFF332E38),
    inverseOnSurface = Color(0xFFF5F0F2),
    outline = CassyBorder,
    outlineVariant = Color(0xFFD5C9CC),
    error = CassyDanger,
    onError = Color.White
)

private val CassyDarkColorScheme = darkColorScheme(
    primary = CassyPrimaryDark,
    onPrimary = Color(0xFF3A141C),
    primaryContainer = Color(0xFF6E3040),
    onPrimaryContainer = Color(0xFFF9DFE3),
    secondary = CassyAccentDark,
    onSecondary = Color(0xFF3A2E1B),
    secondaryContainer = Color(0xFF5C4B33),
    onSecondaryContainer = Color(0xFFF1E7D8),
    tertiary = CassySuccessDark,
    onTertiary = Color(0xFF0F2A17),
    tertiaryContainer = Color(0xFF2E5138),
    onTertiaryContainer = Color(0xFFDAEDDE),
    background = CassyBackgroundDark,
    onBackground = CassyTextPrimaryDark,
    surface = CassySurfaceDark,
    onSurface = CassyTextPrimaryDark,
    surfaceVariant = CassySurfaceElevatedDark,
    onSurfaceVariant = CassyTextSecondaryDark,
    surfaceTint = CassyPrimaryDark,
    inverseSurface = Color(0xFFF5F0F2),
    inverseOnSurface = Color(0xFF1A1518),
    outline = CassyBorderDark,
    outlineVariant = Color(0xFF474152),
    error = CassyDangerDark,
    onError = Color(0xFF3A1414)
)

/**
 * Cassy shape language — generous rounded geometry:
 * cards 26dp, sheets 32dp, inputs 16dp, chips/badges fully rounded.
 */
private val CassyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * The signature Cassy gradient (rosewood → blush / soft rose → pale rose).
 * Use for primary CTAs, active badges and highlighted fills.
 */
fun cassyPrimaryGradient(isDark: Boolean = false): Brush = Brush.horizontalGradient(
    colors = if (isDark) {
        listOf(CassyPrimaryDark, CassyPrimaryGradientEndDark)
    } else {
        listOf(CassyPrimaryGradientStart, CassyPrimaryGradientEnd)
    }
)

/** Softer vertical variant for scrim/hero overlays. */
fun cassyVerticalGradient(isDark: Boolean = false): Brush = Brush.verticalGradient(
    colors = if (isDark) {
        listOf(CassyPrimaryDark, CassyPrimaryGradientEndDark)
    } else {
        listOf(CassyPrimaryGradientStart, CassyPrimaryGradientEnd)
    }
)

/** Champagne shimmer for premium badges and boost highlights. */
fun cassyChampagneGradient(isDark: Boolean = false): Brush = Brush.horizontalGradient(
    colors = if (isDark) {
        listOf(CassyAccentDark, Color(0xFFEBD9BC))
    } else {
        listOf(CassyAccent, Color(0xFFD9BC8E))
    }
)

@Composable
fun QuickyTheme(
    darkTheme: Boolean = false, // PRD Section 3: Default is LIGHT THEME
    dynamicColor: Boolean = false, // Always preserve Cassy signature branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CassyDarkColorScheme
        else -> CassyLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CassyShapes,
        content = content
    )
}

// Backwards compatibility aliases — the whole app keeps its entry points.
@Composable
fun SparkTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = QuickyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)

@Composable
fun CassyTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = QuickyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
