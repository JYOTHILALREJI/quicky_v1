package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom hand-crafted SVG-style vector icons for Quicky.
 *
 * These are built as ImageVectors (the Compose equivalent of SVG assets) so
 * they scale perfectly at any size and are tinted by the caller like any
 * other material icon.
 */

/**
 * iOS-style back chevron ("<") used in the top bar of the Games Hub and
 * Clubs screens, placed before the Quicky logo. A single thin rounded
 * stroke, mirroring SF Symbols' "chevron.left".
 */
val QuickyBackChevron: ImageVector by lazy {
    ImageVector.Builder(
        name = "QuickyBackChevron",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // A single elegant chevron stroke: down-right, then up-right,
        // meeting at a crisp left point with rounded caps (iOS look).
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.75f, 4.75f)
            lineTo(7.5f, 12f)
            lineTo(14.75f, 19.25f)
        }
    }.build()
}

/** Gamepad icon used in the personal chat top bar (next to the 3-dots menu). */
val QuickyGamesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "QuickyGames",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Outlined gamepad body (rounded rectangle with soft shoulders)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7.5f, 7f)
            horizontalLineToRelative(9f)
            curveToRelative(2.21f, 0f, 4f, 1.79f, 4f, 4f)
            verticalLineToRelative(2f)
            curveToRelative(0f, 2.21f, -1.79f, 4f, -4f, 4f)
            horizontalLineToRelative(-9f)
            curveToRelative(-2.21f, 0f, -4f, -1.79f, -4f, -4f)
            verticalLineToRelative(-2f)
            curveToRelative(0f, -2.21f, 1.79f, -4f, 4f, -4f)
            close()
        }
        // D-pad (vertical + horizontal bars of the cross)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7.6f, 10f)
            lineTo(7.6f, 14f)
            moveTo(5.6f, 12f)
            lineTo(9.6f, 12f)
        }
        // Action buttons (two diagonal circles on the right)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.25f, 10.6f)
            arcToRelative(0.95f, 0.95f, 0f, false, true, 1.9f, 0f)
            arcToRelative(0.95f, 0.95f, 0f, false, true, -1.9f, 0f)
            close()
            moveTo(16.65f, 13.4f)
            arcToRelative(0.95f, 0.95f, 0f, false, true, 1.9f, 0f)
            arcToRelative(0.95f, 0.95f, 0f, false, true, -1.9f, 0f)
            close()
        }
    }.build()
}

/** Sticker icon (peeling sticker) used inside the chat composer right end. */
val QuickyStickerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "QuickySticker",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Sticker body with the bottom-right corner peeled away
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 3f)
            horizontalLineToRelative(10f)
            curveToRelative(2.2f, 0f, 4f, 1.8f, 4f, 4f)
            verticalLineToRelative(7f)
            lineToRelative(-7f, 7f)
            horizontalLineToRelative(-7f)
            curveToRelative(-2.2f, 0f, -4f, -1.8f, -4f, -4f)
            verticalLineToRelative(-10f)
            curveToRelative(0f, -2.2f, 1.8f, -4f, 4f, -4f)
            close()
        }
        // Folded corner flap (separated by a thin diagonal gap)
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.5f, 21f)
            lineTo(20.5f, 16f)
            verticalLineTo(17.7f)
            curveToRelative(0f, 1.82f, -1.48f, 3.3f, -3.3f, 3.3f)
            close()
        }
    }.build()
}
