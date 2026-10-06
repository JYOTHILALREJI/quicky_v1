package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Entitlements
import com.example.model.InteractionInsight
import com.example.model.UserProfile
import com.example.model.toDisplayLocation
import com.example.ui.components.PremiumBadge
import com.example.ui.components.glassNavBarOverlayHeight
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.*

/**
 * ============================================================================
 * MY PROFILE SCREEN — Quicky v2.2 (Settings & Profile PRD redesign §4)
 *
 * Clear vertical hierarchy inside the summary card (PRD §4.3/§4.4):
 * photo → badges → name + age → location → occupation → completion →
 * edit button, with deliberate breathing room between each band. The
 * photos section is its own card; Gold, verification and insights follow
 * as separate labeled sections. The floating glass bottom nav stays clear
 * of the content via glassNavBarOverlayHeight bottom padding.
 * ============================================================================
 */
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    entitlements: Entitlements,
    insights: List<InteractionInsight>,
    isInsightsEnabled: Boolean,
    isProcessingPhoto: Boolean,
    onEditProfileClick: () -> Unit,
    onStartVerificationClick: () -> Unit,
    onSetPrimaryPhoto: (Int) -> Unit,
    onDeletePhoto: (Int) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onPremiumStoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Real photo picker for the empty slots — uploads go through the
    // ViewModel (face check + Supabase Storage + profiles.photo_urls).
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAddPhoto(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // PRD §4.7: bottom padding keeps the last card fully scrollable
            // above the floating liquid-glass navigation bar.
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 16.dp + glassNavBarOverlayHeight())
            .testTag("my_profile_screen")
            // v2.1 §3.5 — tap anywhere dismisses the keyboard.
            .dismissKeyboardOnTap(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileSummaryCard(
            userProfile = userProfile,
            onEditProfileClick = onEditProfileClick,
            onStartVerificationClick = onStartVerificationClick
        )

        ProfilePhotosSection(
            userProfile = userProfile,
            isProcessingPhoto = isProcessingPhoto,
            onSetPrimaryPhoto = onSetPrimaryPhoto,
            onDeletePhoto = onDeletePhoto,
            onPickPhoto = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        // --- Quicky Gold membership (§4.6 additional section) ---
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPremiumStoreClick() }
                .testTag("premium_banner_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF9C27B0), Color(0xFFFF2A6D))
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = QuickyGold,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = Color.Black)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Quicky Gold",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            if (entitlements.isPremium) {
                                // v2.1 §5 — redesigned champagne-gold badge
                                PremiumBadge(label = "ACTIVE")
                            }
                        }

                        Text(
                            text = if (entitlements.isPremium) "All Games, Voice Chat, 10× Boosts & Filters Active" else "Unlock All Games, Voice Chat, 10× Boosts & Advanced Filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
                }
            }
        }

        // --- Verification status / prompt (PRD §34–§39) ---
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ActionVerified.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.VerifiedUser, contentDescription = null, tint = ActionVerified)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (userProfile.isVerified) "Quicky Verified Profile" else "Get Verified for 3× Visibility",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (userProfile.isVerified) "Your live selfie challenge is approved. 3× discovery exposure active!" else "Take a quick selfie live challenge to earn your blue verification badge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!userProfile.isVerified) {
                    Button(
                        onClick = onStartVerificationClick,
                        colors = ButtonDefaults.buttonColors(containerColor = ActionVerified),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Verify", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- Interaction insights (PRD §25 & §26) ---
        if (isInsightsEnabled) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, tint = QuickyGold)
                        Text(
                            text = "Interaction Style & Character",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Observable behavioral dimensions derived from your conversation rhythm and game choices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    insights.forEach { insight ->
                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = insight.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${insight.score}% • ${insight.traitLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = QuickyPink
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            LinearProgressIndicator(
                                progress = { insight.score / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = QuickyPink,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Text(
                                text = insight.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // (v2.1 §3.9) All settings rows live in the dedicated Settings
        // screen — reachable via the gear icon in the top bar. The
        // Profile page is now a pure display surface.
    }
}

// -----------------------------------------------------------------
// Summary card hierarchy (PRD §4.3/§4.4)
// -----------------------------------------------------------------

/**
 * Centered profile summary: photo → badges → name + age → location →
 * occupation → completion indicator → Edit Profile & Prompts action.
 */
@Composable
private fun ProfileSummaryCard(
    userProfile: UserProfile,
    onEditProfileClick: () -> Unit,
    onStartVerificationClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 1. Main profile photo (112–128dp, subtle brand border) ---
            val avatarUri = userProfile.photoUris.firstOrNull()
            val avatarRes = userProfile.photoResIds.firstOrNull() ?: R.drawable.img_onboarding_hero
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(122.dp)
            ) {
                val photoModifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .border(
                        3.dp,
                        Brush.linearGradient(listOf(QuickyPink, QuickyPurple)),
                        CircleShape
                    )
                if (avatarUri != null) {
                    coil.compose.AsyncImage(
                        model = avatarUri,
                        contentDescription = "My profile photo",
                        modifier = photoModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = avatarRes),
                        contentDescription = "My profile photo",
                        modifier = photoModifier,
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 2. Status badges (compact, wrapping row) ---
            ProfileBadgeRow(
                userProfile = userProfile,
                onStartVerificationClick = onStartVerificationClick
            )

            Spacer(modifier = Modifier.height(18.dp))

            // --- 3. Name + age on one baseline ---
            Text(
                text = "${userProfile.name}, ${userProfile.age}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // --- 4. Location (no postal code) + occupation beneath ---
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = userProfile.city.toDisplayLocation()
                    .ifBlank { "Location not set" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (userProfile.occupation.isNotBlank()) {
                Text(
                    text = userProfile.occupation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }

            // --- 5. Completion: label row + progress bar beneath ---
            Spacer(modifier = Modifier.height(22.dp))
            ProfileCompletionIndicator(
                completionPercent = userProfile.profileCompletionScore
            )

            // --- 6. One clearly visible primary action ---
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onEditProfileClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuickyPink),
                modifier = Modifier.testTag("edit_profile_button")
            ) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Profile & Prompts", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Character badge + verification status in one compact row (PRD §4.3). */
@Composable
private fun ProfileBadgeRow(
    userProfile: UserProfile,
    onStartVerificationClick: () -> Unit
) {
    // FlowRow-lite: a single centered Row is enough for the two badges; the
    // parent centers horizontally and wraps are not needed at this width.
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (userProfile.showCharacterBadge) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = QuickyPurple.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, QuickyPurple.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "✦", color = QuickyGold, fontSize = 12.sp)
                    Text(
                        text = userProfile.characterBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = QuickyPurple
                    )
                }
            }
        }

        if (userProfile.isVerified) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ActionVerified.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ActionVerified)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Verified",
                        tint = ActionVerified,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Verified",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ActionVerified
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onStartVerificationClick() }
            ) {
                Text(
                    text = "Get Verified ✓",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = QuickyPink,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Label left, percentage right, progress bar directly underneath (PRD §4.3). */
@Composable
private fun ProfileCompletionIndicator(
    completionPercent: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Profile Completion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$completionPercent% Complete",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = QuickyPink
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { (completionPercent.coerceIn(0, 100)) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = QuickyPink,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// -----------------------------------------------------------------
// Photos section (PRD §4.5)
// -----------------------------------------------------------------

/** "My Profile Photos" as its own card: heading, count, hint, 3 slots. */
@Composable
private fun ProfilePhotosSection(
    userProfile: UserProfile,
    isProcessingPhoto: Boolean,
    onSetPrimaryPhoto: (Int) -> Unit,
    onDeletePhoto: (Int) -> Unit,
    onPickPhoto: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Profile Photos",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${
                        if (userProfile.photoUris.isNotEmpty()) userProfile.photoUris.size
                        else userProfile.photoResIds.size
                    }/3",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = QuickyPink
                )
            }

            Text(
                text = "Tap a photo to make it your main photo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val useUploadedPhotos = userProfile.photoUris.isNotEmpty()
                val photoCount = if (useUploadedPhotos) userProfile.photoUris.size
                else userProfile.photoResIds.size
                for (i in 0 until 3) {
                    ProfilePhotoItem(
                        index = i,
                        hasPhoto = i < photoCount,
                        isMain = i == 0 && i < photoCount,
                        canRemove = photoCount > 1,
                        isProcessingPhoto = isProcessingPhoto,
                        photoUri = if (useUploadedPhotos) userProfile.photoUris.getOrNull(i) else null,
                        photoResId = if (!useUploadedPhotos) userProfile.photoResIds.getOrNull(i) else null,
                        onSetPrimary = { onSetPrimaryPhoto(i) },
                        onDelete = { onDeletePhoto(i) },
                        onPickPhoto = onPickPhoto,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** One photo slot: main marker, remove control, or the add/empty state. */
@Composable
private fun ProfilePhotoItem(
    index: Int,
    hasPhoto: Boolean,
    isMain: Boolean,
    canRemove: Boolean,
    isProcessingPhoto: Boolean,
    photoUri: String?,
    photoResId: Int?,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                if (isMain) QuickyPink else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp)
            )
            .clickable {
                if (hasPhoto && index != 0) {
                    onSetPrimary()
                } else if (!hasPhoto) {
                    onPickPhoto()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (hasPhoto) {
            if (photoUri != null) {
                coil.compose.AsyncImage(
                    model = photoUri,
                    contentDescription = "Photo ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (photoResId != null) {
                Image(
                    painter = painterResource(id = photoResId),
                    contentDescription = "Photo ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (isMain) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = QuickyPink,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "★ Main",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (canRemove) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isProcessingPhoto) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = QuickyPink,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Uploading…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Icon(imageVector = Icons.Filled.AddPhotoAlternate, contentDescription = "Add photo", tint = QuickyPink)
                    Text("Add", style = MaterialTheme.typography.labelSmall, color = QuickyPink, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
