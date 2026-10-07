package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserProfile
import com.example.ui.components.CassyGradientButton
import com.example.ui.components.DiscoveryNativeAdCard
import com.example.ui.components.ProfileCard
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.components.glassNavBarOverlayHeight
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.SparkRose

@Composable
fun DiscoverScreen(
    deck: List<UserProfile>,
    showAdCard: Boolean = false,
    onLike: (UserProfile, isSuperLike: Boolean) -> Unit,
    onPass: (UserProfile) -> Unit,
    onDismissAdCard: () -> Unit = {},
    onRewind: () -> Unit,
    onBoost: () -> Unit,
    onOpenDetail: (UserProfile) -> Unit,
    onResetDeck: () -> Unit,
    distanceUnit: String = "km",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // v2.1 §3.5 — tap anywhere dismisses the keyboard.
            .dismissKeyboardOnTap()
            .testTag("discover_screen")
    ) {
        // Countdown gate for the Sponsored card (v2.1 §3.6.1) — dismissal
        // unlocks only after the 5s ring completes.
        var adCountdownDone by remember(showAdCard) { mutableStateOf(false) }

        when {
            // v2.1 §3.6.1 — Sponsored card injected every N swipes. Not
            // dismissable during the 5s countdown; afterwards swipe-LEFT
            // (dismiss) is the only exit — swipe-right stays disabled so
            // nobody "likes" an ad by accident.
            showAdCard -> {
                // v3.2 (PRD §5): the Sponsored card is a DEFINED visual
                // container and the Skip control sits BELOW the card —
                // visually separated, anchored above the nav bar, with a
                // consistent touch target that never overlaps ad content.
                DiscoveryNativeAdCard(
                    onCountdownDone = { adCountdownDone = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = glassNavBarOverlayHeight(extra = 52.dp))
                )
                SkipAdPill(
                    enabled = adCountdownDone,
                    onDismiss = onDismissAdCard,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = glassNavBarOverlayHeight(extra = 6.dp))
                )
            }

            deck.isNotEmpty() -> {
                val topProfile = deck.first()
                // The whole swipe card now sits fully ABOVE the floating
                // liquid-glass nav bar (system navigation-bar inset included),
                // so no part of the profile — photo, info column or action
                // dock — is ever covered by it.
                ProfileCard(
                    profile = topProfile,
                    onLike = { onLike(topProfile, false) },
                    onPass = { onPass(topProfile) },
                    onSuperLike = { onLike(topProfile, true) },
                    onRewind = onRewind,
                    onBoost = onBoost,
                    onOpenDetail = { onOpenDetail(topProfile) },
                    distanceUnit = distanceUnit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = glassNavBarOverlayHeight(extra = 4.dp))
                )
            }

            else -> {
                // Polished Empty Deck State
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "✨", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You're All Caught Up!",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You've explored all active profiles matching your current filters. Adjust your radius or reset your deck to see profiles again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        // Cassy gradient pill CTA (PRD §5.4)
                        CassyGradientButton(
                            onClick = onResetDeck,
                            text = "Reset Deck",
                            leadingIcon = Icons.Filled.Refresh,
                            height = 48,
                            modifier = Modifier.testTag("reset_deck_button")
                        )
                    }
                }
            }
        }
    }
}

/** "Skip ad" pill — anchored below the Sponsored card, above the nav bar. */
@Composable
private fun SkipAdPill(
    enabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(50),
        modifier = modifier.testTag("skip_ad_button")
    ) {
        Text(
            text = if (enabled) "Skip ad ⌄" else "Sponsored",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onDismiss)
                .padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}
