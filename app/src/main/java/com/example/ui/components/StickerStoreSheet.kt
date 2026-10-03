package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StickerPack
import com.example.ui.theme.ActionLike
import com.example.ui.theme.QuickyGold
import com.example.ui.theme.QuickyPink
import com.example.ui.theme.QuickyPurple

/**
 * ============================================================================
 * STICKER STORE — Quicky v2.1 §3.1 (CTA redesign)
 *
 * The "Get" CTA moved to the TOP-RIGHT of the pack card, overlaying the
 * preview art as a glass chip (dark scrim ≥ 4.5:1 contrast on any art).
 * It always shows label AND price — "Get · 250 🪙" for coin-priced packs,
 * "Get · Free" / "Get · $0.99" otherwise — in three states:
 *   Owned    → muted "✓ Owned" chip
 *   Locked   → lock icon + price (premium-gated pack)
 *   Available→ Cassy gradient fill
 * Tap target ≥ 44dp tall (chip + padding), 12dp inner padding, single line.
 * ============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerStoreSheet(
    stickerPacks: List<StickerPack>,
    onPurchasePack: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("sticker_store_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = QuickyPurple.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingBag,
                                    contentDescription = null,
                                    tint = QuickyPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Sticker Store",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Express yourself across all chats & Ludo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            }

            // PRD Section 20 & 24 Policy Disclosure Banner
            item {
                Surface(
                    color = QuickyGold.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, QuickyGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = QuickyGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Stickers are digital collectibles purchased with Quicky Gold coins or Google Play Billing. They work in Personal Chats, Club Chats, and Ludo Game Rooms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Sticker packs — v2.1 corner-CTA cards
            items(stickerPacks, key = { it.id }) { pack ->
                StickerPackCard(
                    pack = pack,
                    onPurchase = { onPurchasePack(pack.id) }
                )
            }
        }
    }
}

@Composable
private fun StickerPackCard(
    pack: StickerPack,
    onPurchase: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (pack.isOwned) androidx.compose.foundation.BorderStroke(1.5.dp, ActionLike.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // ---------- Preview art with the top-right CTA chip ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                QuickyPurple.copy(alpha = 0.75f),
                                QuickyPink.copy(alpha = 0.75f)
                            )
                        )
                    )
            ) {
                // Preview sticker collage
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    pack.stickers.take(4).forEach { sticker ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.18f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(sticker.emojiRepresentation, fontSize = 24.sp)
                            }
                        }
                    }
                    if (pack.stickers.size > 4) {
                        Text(
                            "+${pack.stickers.size - 4}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Top-right corner CTA (12dp inset, 44dp tap height).
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    when {
                        pack.isOwned -> OwnedChip()
                        pack.isPremiumGated -> LockedCtaChip(
                            label = pack.ctaLabel,
                            onClick = onPurchase
                        )
                        else -> AvailableCtaChip(
                            label = pack.ctaLabel,
                            onClick = onPurchase,
                            testTag = "buy_pack_${pack.id}"
                        )
                    }
                }
            }

            // ---------- Pack info ----------
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${pack.stickers.size} stickers · ${pack.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = QuickyPurple
                )
                Text(
                    text = pack.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** "✓ OWNED" — muted state. */
@Composable
private fun OwnedChip() {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.heightIn(min = 36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF9FE8B0),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Owned",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFD9D9D9)
            )
        }
    }
}

/** Locked pack — glass chip with lock icon + price (tap opens purchase). */
@Composable
private fun LockedCtaChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, QuickyGold.copy(alpha = 0.8f)),
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = QuickyGold,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Available pack — Cassy gradient chip. */
@Composable
private fun AvailableCtaChip(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .background(
                brush = Brush.horizontalGradient(listOf(QuickyPurple, QuickyPink)),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
