package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// CASSY DESIGN SYSTEM — Quicky v2 "Premium Cassy" Edition (PRD §5.2)
//
// "Cassy" = Classy + Sassy. Warm, sophisticated, intimate, confident.
//  - Warm ivory backgrounds / deep ink in dark mode
//  - Rosewood primary with a rosewood→blush gradient
//  - Champagne metallic accents for premium & verification badges
//  - Emerald success and muted crimson danger
//
// The legacy Quicky/Spark/Light*/Dark* token names below are kept as
// aliases into the Cassy palette so every existing screen inherits the
// new identity without per-file rewrites.
// ============================================================================

// ---- Cassy core tokens (LIGHT MODE) -----------------------------------------
val CassyBackground = Color(0xFFFDF8F6)          // warm ivory
val CassySurface = Color(0xFFFFFFFF)             // cards, sheets
val CassySurfaceElevated = Color(0xFFF7F2F0)      // elevated cards, modals
val CassyPrimary = Color(0xFFC45A6C)              // rosewood — CTAs, active states
val CassyPrimaryGradientStart = CassyPrimary
val CassyPrimaryGradientEnd = Color(0xFFD4A0A8)   // blush
val CassyAccent = Color(0xFFB8956A)               // champagne — premium, verification
val CassyTextPrimary = Color(0xFF1A1518)          // headlines, body
val CassyTextSecondary = Color(0xFF6B5E62)        // supporting text
val CassyTextMuted = Color(0xFF9C8F94)            // captions, placeholders
val CassyBorder = Color(0xFFE8E0E2)               // dividers, card outlines
val CassySuccess = Color(0xFF5B8C6B)               // match confirmation
val CassyDanger = Color(0xFFC45050)               // report, block, pass

// ---- Cassy core tokens (DARK MODE) ------------------------------------------
val CassyBackgroundDark = Color(0xFF121015)       // deep ink
val CassySurfaceDark = Color(0xFF1C1A22)
val CassySurfaceElevatedDark = Color(0xFF26232E)
val CassyPrimaryDark = Color(0xFFE88B9A)         // soft rose
val CassyPrimaryGradientEndDark = Color(0xFFF0C4C8)
val CassyAccentDark = Color(0xFFD4B88C)           // light champagne
val CassyTextPrimaryDark = Color(0xFFF5F0F2)
val CassyTextSecondaryDark = Color(0xFFA89AA0)
val CassyTextMutedDark = Color(0xFF7A6E74)
val CassyBorderDark = Color(0xFF332E38)
val CassySuccessDark = Color(0xFF7BB88A)
val CassyDangerDark = Color(0xFFE88B8B)

// ============================================================================
// LEGACY TOKEN ALIASES — remapped onto the Cassy palette
// ============================================================================

// Primary brand accents (rosewood in light, soft rose in dark is applied
// through the color scheme, not these constants)
val QuickyPink = CassyPrimary
val QuickyRose = CassyPrimary
val SparkRose = CassyPrimary
val SparkRoseDark = CassyPrimaryDark
val QuickyCoral = Color(0xFFC45050)               // muted crimson, aligned w/ CassyDanger

// Secondary accent: champagne replaces the old violet for the premium look.
// On white text this reads as the "muted metallic" the PRD calls for.
val QuickyPurple = CassyAccent
val QuickyViolet = CassyAccent
val SparkPurple = CassyAccent
val SparkGold = CassyAccent
val QuickyGold = CassyAccent

// Cyan is no longer part of the identity — mapped to champagne as well.
val QuickyCyan = CassyAccent
val SparkCyan = CassyAccent

// Retired legacy alias (kept for source compatibility).
val SparkCoral = QuickyCoral

// Dating Action Colors (PRD: success = emerald, danger = muted crimson)
val ActionLike = CassySuccess
val ActionPass = CassyDanger
val ActionSuperLike = Color(0xFF5A7FA8)           // dusty blue — harmonizes with Cassy
val ActionBoost = CassyAccent                     // champagne
val ActionRewind = Color(0xFFB8860B)               // deep amber
val ActionVerified = CassyAccent                   // champagne verification

// Light Theme Surfaces (DEFAULT) — warm ivory family
val LightBg = CassyBackground
val LightSurface = CassySurface
val LightSurfaceElevated = CassySurfaceElevated
val LightSurfaceHighlight = CassyBorder
val LightTextPrimary = CassyTextPrimary
val LightTextSecondary = CassyTextSecondary
val LightTextMuted = CassyTextMuted
val LightBorder = CassyBorder

// Dark Theme Surfaces — deep ink family
val DarkBg = CassyBackgroundDark
val DarkSurface = CassySurfaceDark
val DarkSurfaceElevated = CassySurfaceElevatedDark
val DarkSurfaceHighlight = CassyBorderDark
val DarkTextPrimary = CassyTextPrimaryDark
val DarkTextSecondary = CassyTextSecondaryDark
val DarkTextMuted = CassyTextMutedDark
val DarkBorder = CassyBorderDark
