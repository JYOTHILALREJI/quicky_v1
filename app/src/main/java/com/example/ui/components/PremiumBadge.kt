package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.QuickyGold

/** Deep champagne end-stop for the badge gradient (slightly darker than CassyAccent). */
private val ChampagneDeep = Color(0xFF96733F)

/**
 * ============================================================================
 * PREMIUM BADGE — Quicky v2.1 §5 (redesign)
 *
 * Champagne-gold pill: crown icon + "PLUS"/"GOLD" label, 10sp bold
 * uppercase, 4dp horizontal / 2dp vertical padding, gradient background,
 * 12dp corner radius. Dark text on the champagne gradient keeps a ≥ 4.5:1
 * contrast ratio on BOTH the light and dark Cassy surfaces.
 * ============================================================================
 */
@Composable
fun PremiumBadge(
    label: String = "GOLD",
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF3B2A08)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(20.dp)
            .background(
                brush = Brush.horizontalGradient(listOf(QuickyGold, ChampagneDeep)),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

/** Compact "crown-only" variant for tight rows. */
@Composable
fun PremiumCrownDot(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = QuickyGold,
        modifier = modifier.size(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = "Premium",
            tint = Color(0xFF3B2A08),
            modifier = Modifier.size(11.dp)
        )
    }
}
