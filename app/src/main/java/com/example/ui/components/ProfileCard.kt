package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.UserProfile
import com.example.model.VisibilityLevel
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileCard(
    profile: UserProfile,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onSuperLike: () -> Unit,
    onRewind: () -> Unit,
    onBoost: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    // Lifts the bottom info column (and the action dock) above the
    // floating liquid-glass nav bar while the photo keeps extending
    // behind the frosted translucent surface.
    bottomContentInset: Dp = 0.dp
) {
    var currentPhotoIndex by remember(profile.id) { mutableIntStateOf(0) }
    // Photos may be remote URLs (server-served candidates) or bundled
    // drawables — remote wins, drawable is the fallback.
    val totalPhotos = maxOf(profile.photoUris.size, profile.photoResIds.size)
    val photoCount = totalPhotos.coerceAtLeast(1)
    // Cassy micro-interaction (PRD §5.5): light haptic impact the moment
    // a decision button fires — same cue users feel crossing the swipe
    // threshold in gesture-driven decks.
    val haptics = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_card_${profile.id}"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Current photo: prefer remote URL, fall back to drawable, then hero
            val currentPhotoUri = profile.photoUris.getOrNull(
                currentPhotoIndex % maxOf(1, profile.photoUris.size)
            )
            val currentPhotoRes = profile.photoResIds.getOrNull(
                currentPhotoIndex % maxOf(1, profile.photoResIds.size)
            )
            if (currentPhotoUri != null) {
                coil.compose.AsyncImage(
                    model = currentPhotoUri,
                    contentDescription = "${profile.name}'s photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(
                        id = currentPhotoRes ?: R.drawable.img_onboarding_hero
                    ),
                    contentDescription = "${profile.name}'s photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Tap zones to cycle photos (max 3 photos)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            if (currentPhotoIndex > 0) currentPhotoIndex--
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            if (currentPhotoIndex < photoCount - 1) currentPhotoIndex++
                        }
                )
            }

            // Top Photo Segment Indicators
            if (photoCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(photoCount) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (index == currentPhotoIndex) CassyPrimaryGradientEnd else Color.White.copy(alpha = 0.35f)
                                )
                        )
                    }
                }
            }

            // Smart-Deck compatibility badge (PRD §8.1) — a subtle champagne
            // pill at the TOP-LEFT corner showing the shared-interest score.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    CassyAccent.copy(alpha = 0.75f)
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 22.dp, start = 16.dp)
                    .testTag("compatibility_badge_${profile.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = CassyPrimaryGradientEnd,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "${profile.compatibilityScore}% MATCH",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            // PRD Section 6 & 83: Character Badge & Verified Badge at the TOP-RIGHT corner of the image
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (profile.showCharacterBadge) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, QuickyPurple.copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(text = "✦", color = QuickyGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = profile.characterBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                if (profile.isVerified) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ActionVerified,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Verified profile",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Verified",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Cinematic Cassy gradient scrim — a warm rosewood-tinted ink
            // wash keeps the profile content readable while feeling curated.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF1A0E12).copy(alpha = 0.62f),
                                Color(0xFF120A0E).copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            // Bottom Profile Information Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + bottomContentInset)
            ) {
                // Name, Age and Detail Sheet Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${profile.name}, ${profile.age}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onOpenDetail,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .testTag("info_button_${profile.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "View full profile",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Location & Relationship Intent
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${profile.city} • ${profile.distanceKm} km away",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }

                // Short Bio
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    modifier = Modifier.padding(top = 6.dp)
                )

                // Interest Badges arranged across multiple rows
                // (respecting the candidate's interests visibility setting)
                if (profile.fieldVisibility["interests"] != VisibilityLevel.ONLY_ME &&
                    profile.fieldVisibility["interests"] != VisibilityLevel.MATCHES_ONLY &&
                    profile.interests.isNotEmpty()
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        profile.interests.forEach { interest ->
                            Surface(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = interest,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Floating Action Dock Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRewind()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("action_rewind"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            contentColor = ActionRewind
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Replay, contentDescription = "Rewind")
                    }

                    // Pass
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPass()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("action_pass"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            contentColor = ActionPass
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Pass",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Super Like
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSuperLike()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("action_super_like"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            contentColor = ActionSuperLike
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Star, contentDescription = "Super Like")
                    }

                    // Like (Cassy rosewood gradient pill)
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLike()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("action_like"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = QuickyPink,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Like",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Boost (10x exposure)
                    FilledIconButton(
                        onClick = onBoost,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("action_boost"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            contentColor = QuickyPurple
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.RocketLaunch, contentDescription = "Profile Boost")
                    }
                }
            }
        }
    }
}
