package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.R

// ============================================================================
// CASSY TYPOGRAPHY — Quicky v2 (PRD §5.3)
//
//   Display   → Playfair Display Bold   (32–40sp)  — headlineLarge/Medium
//   Headline  → DM Sans SemiBold        (24–28sp)  — headlineSmall
//   Title     → DM Sans Medium          (18–20sp)  — title*
//   Body      → DM Sans Regular         (14–16sp)  — body*
//   Caption   → DM Sans Regular         (12–13sp)  — bodySmall
//   Badge     → DM Sans Bold            (10–11sp)  — labelSmall (call sites
//                                                      render ALL CAPS)
//
// Fonts are resolved on demand through Google Play Services (downloadable
// fonts). If the device has no Play Services or is offline at first launch,
// Compose gracefully falls back to the platform sans-serif/serif — the app
// keeps working, it just loses the premium flourish.
// ============================================================================

private val cassyFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val PlayfairDisplay = FontFamily(
    Font(
        googleFont = GoogleFont("Playfair Display"),
        fontProvider = cassyFontProvider,
        weight = FontWeight.Bold
    ),
    Font(
        googleFont = GoogleFont("Playfair Display"),
        fontProvider = cassyFontProvider,
        weight = FontWeight.ExtraBold
    )
)

private val DMSans = FontFamily(
    Font(
        googleFont = GoogleFont("DM Sans"),
        fontProvider = cassyFontProvider,
        weight = FontWeight.Normal
    ),
    Font(
        googleFont = GoogleFont("DM Sans"),
        fontProvider = cassyFontProvider,
        weight = FontWeight.Medium
    ),
    Font(
        googleFont = GoogleFont("DM Sans"),
        fontProvider = cassyFontProvider,
        weight = FontWeight.SemiBold
    ),
    Font(
        googleFont = GoogleFont("DM Sans"),
        fontProvider = cassyFontProvider,
        weight = FontWeight.Bold
    )
)

val Typography = Typography(
    // Display — Playfair Display
    headlineLarge = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.25).sp
    ),
    // Headline — DM Sans SemiBold
    headlineSmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    // Title — DM Sans Medium
    titleLarge = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // Body — DM Sans Regular
    bodyLarge = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    // Labels — DM Sans Medium / Bold
    labelLarge = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)
