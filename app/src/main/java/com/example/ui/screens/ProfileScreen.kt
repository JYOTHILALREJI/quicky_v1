package com.example.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppThemeMode
import com.example.model.Entitlements
import com.example.model.InteractionInsight
import com.example.model.UserProfile
import com.example.ui.components.glassNavBarOverlayHeight
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    entitlements: Entitlements,
    insights: List<InteractionInsight>,
    isInsightsEnabled: Boolean,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onEditProfileClick: () -> Unit,
    onPersonalInformationClick: () -> Unit,
    onDiscoveryPreferencesClick: () -> Unit,
    onStartVerificationClick: () -> Unit,
    onSetPrimaryPhoto: (Int) -> Unit,
    onDeletePhoto: (Int) -> Unit,
    onAddPhoto: (Int) -> Unit,
    onPremiumStoreClick: () -> Unit,
    onStickerStoreClick: () -> Unit,
    onSafetyCenterClick: () -> Unit,
    onPrivacyCenterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Extra bottom padding so the last settings row can scroll
            // clear above the floating liquid-glass navigation bar.
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + glassNavBarOverlayHeight())
            .testTag("my_profile_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // PRD Section 11 & 12: CENTER ALIGNED Hero Profile Card
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally // Mandatory center alignment
            ) {
                // Centered Profile Avatar
                val avatarRes = userProfile.photoResIds.firstOrNull() ?: R.drawable.img_onboarding_hero
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(116.dp)
                ) {
                    Image(
                        painter = painterResource(id = avatarRes),
                        contentDescription = "My profile photo",
                        modifier = Modifier
                            .size(108.dp)
                            .clip(CircleShape)
                            .border(
                                3.dp,
                                Brush.linearGradient(listOf(QuickyPink, QuickyPurple)),
                                CircleShape
                            ),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Badges Row (Character Badge + Verification Badge)
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

                Spacer(modifier = Modifier.height(10.dp))

                // Centered Name, Age
                Text(
                    text = "${userProfile.name}, ${userProfile.age}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                // Centered Location & Occupation
                Text(
                    text = "${userProfile.city} • ${userProfile.occupation.ifBlank { "Architectural Designer" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Profile Completion Progress Bar (PRD Section 32)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Profile Completion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${userProfile.profileCompletionScore}% Complete",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = QuickyPink
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { userProfile.profileCompletionScore / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = QuickyPink,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Edit Profile Button
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

        // PRD Section 13: Profile Photo Management (MAXIMUM 3 PHOTOS)
        Card(
            shape = RoundedCornerShape(22.dp),
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
                        text = "My Profile Photos (Max 3)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${userProfile.photoResIds.size}/3",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = QuickyPink
                    )
                }

                Text(
                    text = "Stored securely in Supabase Storage. Tap to set as primary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // 3 Photo Slots Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (i in 0 until 3) {
                        val hasPhoto = i < userProfile.photoResIds.size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (i == 0 && hasPhoto) QuickyPink else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    if (hasPhoto && i != 0) {
                                        onSetPrimaryPhoto(i)
                                    } else if (!hasPhoto) {
                                        onAddPhoto(R.drawable.img_truth_dare_banner)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasPhoto) {
                                Image(
                                    painter = painterResource(id = userProfile.photoResIds[i]),
                                    contentDescription = "Photo ${i + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (i == 0) {
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

                                if (userProfile.photoResIds.size > 1) {
                                    IconButton(
                                        onClick = { onDeletePhoto(i) },
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
                                    Icon(imageVector = Icons.Filled.AddPhotoAlternate, contentDescription = "Add photo", tint = QuickyPink)
                                    Text("Add", style = MaterialTheme.typography.labelSmall, color = QuickyPink, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quicky Gold Premium Card
        Card(
            shape = RoundedCornerShape(22.dp),
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
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ActionLike
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
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

        // Section: Verification Prompt / Status (PRD Section 34-39)
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

        // Section: Interaction Insights (PRD Section 25 & 26)
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

        // Section: Personal Information & Settings (PRD Section 65-69)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // Personal Information & Field Visibility
                ListItem(
                    headlineContent = { Text("Personal Information & Visibility", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Height, education, occupation, interests & visibility") },
                    leadingContent = { Icon(Icons.Outlined.Badge, contentDescription = null, tint = QuickyPink) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPersonalInformationClick() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Discovery Preferences
                ListItem(
                    headlineContent = { Text("Discovery Preferences", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Who you want to see, age range, distance, height") },
                    leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = QuickyPurple) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onDiscoveryPreferencesClick() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Theme Mode Selector (PRD Section 3 & 61: Light default, Dark, System)
                ListItem(
                    headlineContent = { Text("Appearance Theme", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Current: ${themeMode.name.lowercase().replaceFirstChar { c -> c.uppercase() }} (Default: Light)") },
                    leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null, tint = QuickyGold) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = { onThemeChange(if (themeMode == AppThemeMode.LIGHT) AppThemeMode.DARK else AppThemeMode.LIGHT) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(if (themeMode == AppThemeMode.LIGHT) "Dark Mode" else "Light Mode")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Sticker Store
                ListItem(
                    headlineContent = { Text("Sticker Store", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Buy digital packs for Personal, Club, and Ludo chats") },
                    leadingContent = { Text("💖", fontSize = 20.sp) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onStickerStoreClick() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Safety Center
                ListItem(
                    headlineContent = { Text("Safety & Protection", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Dating guidelines, reporting and zero-tolerance policy") },
                    leadingContent = { Icon(Icons.Outlined.Security, contentDescription = null, tint = ActionLike) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSafetyCenterClick() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Privacy Center
                ListItem(
                    headlineContent = { Text("Privacy Controls", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Incognito, online status, character badge visibility") },
                    leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = QuickyPurple) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPrivacyCenterClick() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Account & Legal
                ListItem(
                    headlineContent = { Text("Account & Legal", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Theme mode, download data, terms, delete account") },
                    leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSettingsClick() }
                )
            }
        }
    }
}
