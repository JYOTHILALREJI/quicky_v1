package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CassyAccent
import com.example.ui.theme.CassyBackground
import com.example.ui.theme.CassyBackgroundDark
import com.example.ui.theme.CassyBorder
import com.example.ui.theme.CassyBorderDark
import com.example.ui.theme.CassyPrimaryGradientEnd
import com.example.ui.theme.CassyPrimaryGradientEndDark
import com.example.ui.theme.CassyPrimaryGradientStart
import com.example.ui.theme.CassyPrimaryDark
import com.example.ui.theme.CassyTextSecondary
import com.example.ui.theme.CassyTextSecondaryDark
import com.example.ui.theme.cassyChampagneGradient
import com.example.ui.theme.cassyPrimaryGradient

/**
 * ============================================================================
 * CASSY SHARED COMPONENTS — Quicky v2 (PRD §5.4)
 *
 * The reusable building blocks of the Cassy design language:
 *  - CassyGradientButton   pill-shaped primary CTA with rosewood→blush fill
 *  - CassyGlassOutline     glass-outline secondary CTA
 *  - CassyBadge            champagne "premium" badge (verified / boosts)
 *  - CassySectionBox       bordered box that hosts one preference group
 *  - cassyCinematicBrush   layered warm background for onboarding/auth
 * ============================================================================
 */

/** True when the app is currently rendering the Cassy dark scheme. */
@Composable
fun isCassyDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Primary CTA — gradient fill, pill shape, subtle press scale.
 * Replaces flat-color M3 buttons on the key screens.
 */
@Composable
fun CassyGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Int = 52,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
) {
    val isDark = isCassyDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val disabledContainer = if (isDark) CassyBorderDark else CassyBorder
    val isActive = enabled && !isLoading

    // Signature gradient painted behind a transparent pill button
    // (M3 ButtonColors can't take a Brush directly).
    val buttonBackground: Brush = if (isActive) {
        cassyPrimaryGradient(isDark = isDark)
    } else {
        androidx.compose.ui.graphics.SolidColor(disabledContainer)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .graphicsLayer {
                val scale = if (pressed && isActive) 0.97f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(percent = 50))
            .background(buttonBackground)
    ) {
        Button(
            onClick = onClick,
            enabled = isActive,
            interactionSource = interactionSource,
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = if (isDark) Color(0xFF3A141C) else Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = if (isDark) CassyTextSecondaryDark else CassyTextSecondary
            ),
            contentPadding = contentPadding
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (text != null) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                        )
                    }
                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A gradient surface that sits behind Button content when the button's
 * container is transparent — call this from `Modifier.drawBehind` via the
 * [cassyPrimaryGradient] brush. Provided as a public helper so custom
 * composables (e.g. the match CTA) can paint the signature gradient.
 */
@Composable
fun cassyPrimaryButtonBrush(): Brush = cassyPrimaryGradient(isDark = isCassyDarkTheme())

/**
 * Secondary CTA — translucent frosted surface with a hairline border.
 */
@Composable
fun CassyGlassOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    height: Int = 52
) {
    val isDark = isCassyDarkTheme()
    val strokeColor by animateColorAsState(
        targetValue = if (isDark) CassyPrimaryGradientEndDark else CassyPrimaryGradientStart,
        label = "cassy_glass_stroke"
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, strokeColor.copy(alpha = 0.65f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.45f),
            contentColor = if (isDark) CassyPrimaryGradientEndDark else CassyPrimaryGradientStart
        )
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

/**
 * Champagne metallic badge for premium signals — "VERIFIED", "BOOST",
 * "QUICKY+ GOLD". Renders an icon + all-caps micro label.
 */
@Composable
fun CassyBadge(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.WorkspacePremium,
    tone: CassyBadgeTone = CassyBadgeTone.Champagne
) {
    val isDark = isCassyDarkTheme()
    val (container, content) = when (tone) {
        CassyBadgeTone.Champagne ->
            if (isDark) Color(0xFF3A2E1B) to CassyBadgePalette.champagneDark
            else CassyBadgePalette.champagneLight to CassyBadgePalette.champagneOnLight
        CassyBadgeTone.Rose ->
            if (isDark) CassyPrimaryDark.copy(alpha = 0.16f) to CassyPrimaryGradientEndDark
            else CassyPrimaryGradientStart.copy(alpha = 0.12f) to CassyPrimaryGradientStart
        CassyBadgeTone.Glass ->
            Color.Black.copy(alpha = 0.42f) to Color.White
    }

    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.75.dp, if (tone == CassyBadgeTone.Glass) {
            Color.White.copy(alpha = 0.35f)
        } else {
            (if (isDark) CassyAccent else CassyAccent).copy(alpha = 0.55f)
        }),
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = content
            )
        }
    }
}

enum class CassyBadgeTone { Champagne, Rose, Glass }

private object CassyBadgePalette {
    val champagneLight = Color(0xFF8A6A3F)
    val champagneOnLight = Color(0xFF7A5C34)
    val champagneDark = Color(0xFFE2C9A2)
}

/**
 * Bordered section container — one preference group per box, mirroring
 * the polished Discovery Preferences layout across other sheets.
 */
@Composable
fun CassySectionBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = isCassyDarkTheme()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF1F1C26) else Color.White,
        border = BorderStroke(1.dp, if (isDark) CassyBorderDark else CassyBorder),
        shadowElevation = if (isDark) 0.dp else 1.dp
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/**
 * Layered cinematic background — the warm ivory-to-blush wash used by the
 * onboarding flow (PRD §5.4: "cinematic full-bleed gradient backgrounds").
 */
fun cassyCinematicBrush(isDark: Boolean = false): Brush = Brush.verticalGradient(
    colors = if (isDark) {
        listOf(Color(0xFF1C1A22), Color(0xFF26232E), Color(0xFF332E38))
    } else {
        listOf(
            Color(0xFFFFF9F7),
            CassyBackground,
            Color(0xFFF9EDEE),
            Color(0xFFF3E1E3)
        )
    }
)

/** Ambient champagne glow dot for celebration overlays. */
@Composable
fun CassyGlowDot(size: Int = 8, tone: CassyBadgeTone = CassyBadgeTone.Champagne) {
    val isDark = isCassyDarkTheme()
    val brush = when (tone) {
        CassyBadgeTone.Champagne -> cassyChampagneGradient(isDark)
        CassyBadgeTone.Rose -> cassyPrimaryGradient(isDark)
        CassyBadgeTone.Glass -> androidx.compose.ui.graphics.SolidColor(Color.White)
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(brush)
    )
}
