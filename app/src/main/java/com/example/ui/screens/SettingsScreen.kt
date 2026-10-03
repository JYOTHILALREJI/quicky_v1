package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.model.AppThemeMode
import com.example.model.Entitlements
import com.example.model.NotificationPreferences
import com.example.model.PremiumGate
import com.example.model.PrivacySettings
import com.example.model.UserProfile
import com.example.ui.components.PremiumBadge
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.*

/**
 * ============================================================================
 * DEDICATED SETTINGS SCREEN — Quicky v2.1 §3.9
 *
 * Opened from the gear icon in the Profile top bar (the Profile page
 * itself is now a pure display surface). Sections mirror the PRD table:
 * Account · Discovery · Notifications · Privacy · Safety · Subscription ·
 * Appearance · About (+ Chats & Media for the Sticker Store).
 * ============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    entitlements: Entitlements,
    themeMode: AppThemeMode,
    distanceUnit: String,
    notificationPrefs: NotificationPreferences,
    showMeOnDiscovery: Boolean,
    privacySettings: PrivacySettings,
    onBack: () -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onDistanceUnitChange: (String) -> Unit,
    onNotificationPrefChange: (String, Boolean) -> Unit,
    onShowMeOnDiscoveryChange: (Boolean) -> Unit,
    onPrivacySettingsChange: (PrivacySettings) -> Unit,
    onEditProfileClick: () -> Unit,
    onPersonalInformationClick: () -> Unit,
    onDiscoveryPreferencesClick: () -> Unit,
    onEditLocationClick: () -> Unit,
    onStickerStoreClick: () -> Unit,
    onSafetyCenterClick: () -> Unit,
    onPremiumStoreClick: () -> Unit,
    onStartVerificationClick: () -> Unit,
    onLogout: () -> Unit,
    onDownloadData: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .dismissKeyboardOnTap()
    ) {
        // --- Header ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ---------- ACCOUNT ----------
            SettingsSection(title = "Account") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Person, null, tint = QuickyPink) },
                    title = "Edit Profile & Prompts",
                    subtitle = "Name, bio, photos",
                    onClick = onEditProfileClick
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Badge, null, tint = QuickyPurple) },
                    title = "Personal Information & Visibility",
                    subtitle = "Height, education, occupation, field visibility",
                    onClick = onPersonalInformationClick
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.AlternateEmail, null, tint = QuickyPink) },
                    title = "Email Address",
                    subtitle = profile.id.take(18) + "…",
                    onClick = { infoDialog = "A verification email is sent whenever the address changes. Tap the link on the new address to confirm." }
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Password, null, tint = QuickyPurple) },
                    title = "Change Password",
                    subtitle = "We email a secure reset link",
                    onClick = { infoDialog = "Password reset link sent to your verified email. It expires in 60 minutes." }
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.PhoneIphone, null, tint = QuickyPink) },
                    title = "Phone Number",
                    subtitle = "Used for account recovery",
                    onClick = { infoDialog = "Phone verification lands with two-factor authentication in v2.2." }
                )
                SettingsRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = QuickyPurple) },
                    title = "Log Out",
                    subtitle = "Your profile, matches and chats stay safe",
                    onClick = { showLogoutDialog = true },
                    testTag = "settings_logout_row"
                )
            }

            // ---------- DISCOVERY ----------
            SettingsSection(title = "Discovery") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Tune, null, tint = QuickyPurple) },
                    title = "Default Filters",
                    subtitle = "Age, distance, interests, languages, intent",
                    onClick = onDiscoveryPreferencesClick
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Straighten, null, tint = QuickyPink) },
                    title = "Distance Unit",
                    subtitle = "Currently: ${if (distanceUnit == "mi") "Miles" else "Kilometers"}",
                    onClick = { onDistanceUnitChange(if (distanceUnit == "mi") "km" else "mi") },
                    testTag = "settings_distance_unit_row"
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Visibility, null, tint = QuickyPurple) },
                    title = "Show me on Discovery",
                    subtitle = if (showMeOnDiscovery) "Visible — you appear in decks" else "Paused — you're hidden from decks",
                    onClick = { onShowMeOnDiscoveryChange(!showMeOnDiscovery) },
                    trailing = {
                        Switch(
                            checked = showMeOnDiscovery,
                            onCheckedChange = onShowMeOnDiscoveryChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = QuickyPurple)
                        )
                    }
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.LocationOn, null, tint = QuickyPink) },
                    title = "Location",
                    subtitle = profile.city.ifBlank { "Set your city for distance matching" },
                    onClick = onEditLocationClick
                )
            }

            // ---------- NOTIFICATIONS ----------
            SettingsSection(title = "Notifications") {
                ToggleSettingsRow(
                    icon = { Icon(Icons.Outlined.FavoriteBorder, null, tint = QuickyPink) },
                    title = "New Matches",
                    checked = notificationPrefs.matches,
                    onCheckedChange = { onNotificationPrefChange("matches", it) }
                )
                ToggleSettingsRow(
                    icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, tint = QuickyPurple) },
                    title = "Messages",
                    checked = notificationPrefs.messages,
                    onCheckedChange = { onNotificationPrefChange("messages", it) }
                )
                ToggleSettingsRow(
                    icon = { Icon(Icons.Outlined.Diversity3, null, tint = QuickyGold) },
                    title = "Clubs",
                    checked = notificationPrefs.clubs,
                    onCheckedChange = { onNotificationPrefChange("clubs", it) }
                )
                ToggleSettingsRow(
                    icon = { Icon(Icons.Outlined.LocalOffer, null, tint = QuickyPink) },
                    title = "Promotions & Offers",
                    checked = notificationPrefs.promotions,
                    onCheckedChange = { onNotificationPrefChange("promotions", it) }
                )
            }

            // ---------- PRIVACY ----------
            SettingsSection(title = "Privacy") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Block, null, tint = ActionPass) },
                    title = "Blocked Users",
                    subtitle = "Blocked profiles can't see or message you",
                    onClick = { infoDialog = "You haven't blocked anyone yet. Blocked users appear here with an unblock option." }
                )
                ToggleSettingsRow(
                    icon = { Icon(Icons.Outlined.VisibilityOff, null, tint = QuickyPurple) },
                    title = "Hide Online Status",
                    subtitle = "Others see 'Active recently' instead",
                    checked = !privacySettings.showOnlineStatus,
                    onCheckedChange = { onPrivacySettingsChange(privacySettings.copy(showOnlineStatus = !it)) }
                )
                ToggleSettingsRow(
                    icon = { Icon(Icons.Outlined.LocationOff, null, tint = QuickyGold) },
                    title = "Hide Distance",
                    subtitle = "Don't show how far you are from others",
                    checked = !privacySettings.showDistance,
                    onCheckedChange = { onPrivacySettingsChange(privacySettings.copy(showDistance = !it)) }
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Download, null, tint = QuickyPurple) },
                    title = "Download My Data",
                    subtitle = "Export profile & interaction history",
                    onClick = onDownloadData
                )
            }

            // ---------- SAFETY ----------
            SettingsSection(title = "Safety") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Security, null, tint = ActionLike) },
                    title = "Safety & Protection Center",
                    subtitle = "Dating guidelines, reporting, zero-tolerance policy",
                    onClick = onSafetyCenterClick
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Flag, null, tint = QuickyGold) },
                    title = "Report History",
                    subtitle = "Reports you've submitted",
                    onClick = { infoDialog = "No reports filed. Your reports stay anonymous and are reviewed by the Trust & Safety team." }
                )
                SettingsRow(
                    icon = {
                        Icon(
                            if (profile.isVerified) Icons.Filled.VerifiedUser else Icons.Outlined.VerifiedUser,
                            null,
                            tint = if (profile.isVerified) ActionVerified else QuickyPink
                        )
                    },
                    title = "Verification Status",
                    subtitle = if (profile.isVerified) "Verified — blue badge active" else "Not verified — take the live selfie challenge",
                    onClick = { if (!profile.isVerified) onStartVerificationClick() else infoDialog = "Your Quicky Verified badge is active. 3× discovery exposure is on." }
                )
            }

            // ---------- SUBSCRIPTION ----------
            SettingsSection(title = "Subscription") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = QuickyGold.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, QuickyGold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .clickable { onPremiumStoreClick() }
                            .padding(14.dp)
                            .testTag("settings_subscription_row")
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = QuickyGold)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quicky Gold", fontWeight = FontWeight.Bold)
                                if (PremiumGate.isTestingUnlockActive) {
                                    PremiumBadge(label = "TESTING: ALL UNLOCKED")
                                } else if (entitlements.isPremium) {
                                    PremiumBadge(label = "ACTIVE")
                                }
                            }
                            Text(
                                text = if (PremiumGate.isTestingUnlockActive)
                                    "v2.1 QA — every premium feature is temporarily unlocked"
                                else if (entitlements.isPremium)
                                    "All games, voice notes, unlimited likes & filters"
                                else
                                    "Tap to upgrade — games, voice notes & more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null)
                    }
                }
            }

            // ---------- CHATS & MEDIA ----------
            SettingsSection(title = "Chats & Media") {
                SettingsRow(
                    icon = { Text("💖", fontSize = 20.sp) },
                    title = "Sticker Store",
                    subtitle = "Buy packs for Personal, Club and Ludo chats",
                    onClick = onStickerStoreClick
                )
            }

            // ---------- APPEARANCE ----------
            SettingsSection(title = "Appearance") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        AppThemeMode.LIGHT to "Light ☀️",
                        AppThemeMode.DARK to "Dark 🌙",
                        AppThemeMode.SYSTEM to "Auto"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = QuickyPurple,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ---------- ABOUT ----------
            SettingsSection(title = "About") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Info, null, tint = QuickyPink) },
                    title = "Version",
                    subtitle = "Quicky v2.1 (build ${if (BuildConfig.DEBUG) "debug" else "release"})",
                    onClick = { infoDialog = "Quicky v2.1 — Ludo Arena, chat polish, ads infrastructure, clubs lifecycle & settings." }
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Description, null, tint = QuickyPurple) },
                    title = "Terms of Service (18+)",
                    subtitle = "Community rules & obligations",
                    onClick = { infoDialog = "Quicky is for adults 18+ only. Be respectful, never share other people's data, and report anything that feels unsafe." }
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.PrivacyTip, null, tint = QuickyGold) },
                    title = "Privacy Policy",
                    subtitle = "How your data is handled",
                    onClick = { infoDialog = "Your photos, chats and location live in your Supabase project. Nothing is sold. You can export or delete everything at any time." }
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Code, null, tint = QuickyPurple) },
                    title = "Open-Source Licenses",
                    subtitle = "Jetpack Compose, Coil, OkHttp, Supabase",
                    onClick = { infoDialog = "Built with Android Jetpack Compose, Material 3, Coil, OkHttp, Play Services (Ads & Location) and ML Kit." }
                )
                SettingsRow(
                    icon = { Icon(Icons.Outlined.DeleteForever, null, tint = ActionPass) },
                    title = "Delete Account",
                    subtitle = "Permanently remove profile, matches & chats",
                    onClick = { showDeleteDialog = true },
                    testTag = "settings_delete_account_row"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Dialogs ---
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?") },
            text = { Text("You'll be signed out on this device. Your profile, matches and chats stay safe and reload when you log back in.") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple)
                ) { Text("Log Out") }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account?") },
            text = { Text("This permanently removes your profile, matches, chat history and game progress. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; onDeleteAccount() },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) { Text("Permanently Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
    infoDialog?.let { body ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text("Quicky") },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) { Text("Got it") }
            }
        )
    }
}

// -----------------------------------------------------------------
// Building blocks
// -----------------------------------------------------------------

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    testTag: String? = null
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = icon,
        trailingContent = trailing ?: { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable { onClick() }
    )
}

@Composable
private fun ToggleSettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        leadingContent = icon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = QuickyPurple)
            )
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}
