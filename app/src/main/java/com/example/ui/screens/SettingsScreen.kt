package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.model.AppThemeMode
import com.example.model.BlockedUser
import com.example.model.Entitlements
import com.example.model.NotificationPreferences
import com.example.model.PremiumGate
import com.example.model.PrivacySettings
import com.example.model.UserProfile
import com.example.model.toDisplayLocation
import com.example.ui.components.PremiumBadge
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.*

/**
 * ============================================================================
 * DEDICATED SETTINGS SCREEN — Quicky v2.2 (Settings & Profile PRD redesign)
 *
 * Opened from the gear icon in the Profile top bar (the Profile page
 * itself is now a pure display surface). Categories follow the PRD §3.2
 * order: Account · Discovery · Notifications · Privacy · Safety ·
 * Subscription · Chats & Media · Appearance · About & Legal.
 *
 * Design tokens (PRD §2.2): 20dp screen padding, 24dp section spacing,
 * 20dp card radius, 40dp icon containers, aligned text/trailing controls,
 * 68dp minimum row heights (rows grow with content/font scale).
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
    /** v3.3.7: server-backed flag — whether club members may open a 1:1 chat with this user. */
    allowClubDm: Boolean = true,
    accountEmail: String,
    blockedUsers: List<BlockedUser>,
    onBack: () -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onDistanceUnitChange: (String) -> Unit,
    onNotificationPrefChange: (String, Boolean) -> Unit,
    onShowMeOnDiscoveryChange: (Boolean) -> Unit,
    onPrivacySettingsChange: (PrivacySettings) -> Unit,
    /** v3.3.7: mirrors profiles.allow_club_dm to the server. */
    onAllowClubDmChange: (Boolean) -> Unit = {},
    onUnblockUser: (String) -> Unit,
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
    // Settings > Privacy > Blocked Users opens an in-place management
    // sub-page (list + Unblock) instead of a static info dialog.
    var showBlockedUsersPage by remember { mutableStateOf(false) }

    // System back inside the Blocked Users sub-page returns to Settings
    // instead of closing the whole screen (this BackHandler is deeper in
    // the tree than MainActivity's, so it takes precedence when enabled).
    BackHandler(enabled = showBlockedUsersPage) { showBlockedUsersPage = false }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .dismissKeyboardOnTap()
    ) {
        if (showBlockedUsersPage) {
            BlockedUsersPage(
                blockedUsers = blockedUsers,
                onUnblockUser = onUnblockUser,
                onBack = { showBlockedUsersPage = false }
            )
        } else {
        // --- Header (PRD §3.1): compact, stable, same pattern as the
        // clubs/games secondary pages (back chevron + title baseline). ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // ---------- A. ACCOUNT ----------
            SettingsSection(title = "Account") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Person,
                    iconTint = QuickyPink,
                    title = "Edit Profile & Prompts",
                    subtitle = "Name, bio, photos",
                    onClick = onEditProfileClick
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.Badge,
                    iconTint = QuickyPurple,
                    title = "Personal Information & Visibility",
                    subtitle = "Height, education, occupation, field visibility",
                    onClick = onPersonalInformationClick
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.AlternateEmail,
                    iconTint = QuickyPink,
                    title = "Email Address",
                    // PRD §6: the REGISTERED Supabase Auth account email —
                    // never the profile UUID/id.
                    subtitle = accountEmail.ifBlank { "No email associated with this account" },
                    onClick = {
                        infoDialog = "A verification email is sent whenever the address changes. Tap the link on the new address to confirm."
                    },
                    subtitleMaxLines = 2
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.Password,
                    iconTint = QuickyPurple,
                    title = "Change Password",
                    subtitle = "We email a secure reset link",
                    onClick = { infoDialog = "Password reset link sent to your verified email. It expires in 60 minutes." }
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.PhoneIphone,
                    iconTint = QuickyPink,
                    title = "Phone Number",
                    subtitle = "Used for account recovery",
                    onClick = { infoDialog = "Phone verification lands with two-factor authentication in v2.2." }
                )
                SettingsNavigationRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Log Out",
                    subtitle = "Your profile, matches and chats stay safe",
                    onClick = { showLogoutDialog = true },
                    showDivider = false,
                    testTag = "settings_logout_row"
                )
            }

            // ---------- B. DISCOVERY ----------
            SettingsSection(title = "Discovery") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Tune,
                    iconTint = QuickyPurple,
                    title = "Default Filters",
                    subtitle = "Age, distance, interests, languages, intent",
                    onClick = onDiscoveryPreferencesClick
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.Straighten,
                    iconTint = QuickyPink,
                    title = "Distance Unit",
                    subtitle = "Currently: ${if (distanceUnit == "mi") "Miles" else "Kilometers"}",
                    onClick = { onDistanceUnitChange(if (distanceUnit == "mi") "km" else "mi") },
                    testTag = "settings_distance_unit_row"
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.Visibility,
                    iconTint = QuickyPurple,
                    title = "Show me on Discovery",
                    subtitle = if (showMeOnDiscovery) "Visible — you appear in decks" else "Paused — you're hidden from decks",
                    checked = showMeOnDiscovery,
                    onCheckedChange = onShowMeOnDiscoveryChange
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.LocationOn,
                    iconTint = QuickyPink,
                    title = "Location",
                    // PRD §5: locality only — the postal code never shows.
                    subtitle = profile.city.toDisplayLocation()
                        .ifBlank { "Set your city for distance matching" },
                    onClick = onEditLocationClick,
                    showDivider = false
                )
            }

            // ---------- C. NOTIFICATIONS ----------
            SettingsSection(title = "Notifications") {
                SettingsToggleRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    iconTint = QuickyPink,
                    title = "New Matches",
                    subtitle = "When someone matches back with you",
                    checked = notificationPrefs.matches,
                    onCheckedChange = { onNotificationPrefChange("matches", it) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    iconTint = QuickyPurple,
                    title = "Personal Messages",
                    subtitle = "Chat messages from your matches",
                    checked = notificationPrefs.messages,
                    onCheckedChange = { onNotificationPrefChange("messages", it) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.Favorite,
                    iconTint = QuickyPink,
                    title = "Likes",
                    subtitle = "When someone likes your profile",
                    checked = notificationPrefs.likes,
                    onCheckedChange = { onNotificationPrefChange("likes", it) }
                )
                SettingsToggleRow(
                    icon = Icons.Filled.Stars,
                    iconTint = QuickyGold,
                    title = "Super Likes",
                    subtitle = "When you receive a Super Like",
                    checked = notificationPrefs.superLikes,
                    onCheckedChange = { onNotificationPrefChange("superLikes", it) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.Diversity3,
                    iconTint = QuickyPurple,
                    title = "Club Activity",
                    subtitle = "Club invites and general activity",
                    checked = notificationPrefs.clubs,
                    onCheckedChange = { onNotificationPrefChange("clubs", it) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.AlternateEmail,
                    iconTint = QuickyPink,
                    title = "Club Mentions",
                    subtitle = "When someone @mentions you in a club chat",
                    checked = notificationPrefs.clubMentions,
                    onCheckedChange = { onNotificationPrefChange("clubMentions", it) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.SportsEsports,
                    iconTint = QuickyPurple,
                    title = "Truth or Dare Challenges",
                    subtitle = "In-app overlay when a challenge arrives",
                    checked = notificationPrefs.truthOrDare,
                    onCheckedChange = { onNotificationPrefChange("truthOrDare", it) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.LocalOffer,
                    iconTint = QuickyGold,
                    title = "Promotions & Offers",
                    subtitle = "Quicky Gold deals and feature news",
                    checked = notificationPrefs.promotions,
                    onCheckedChange = { onNotificationPrefChange("promotions", it) },
                    showDivider = false
                )
            }

            // ---------- D. PRIVACY ----------
            SettingsSection(title = "Privacy") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Block,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Blocked Users",
                    subtitle = if (blockedUsers.isEmpty()) {
                        "Blocked profiles can't see or message you"
                    } else {
                        "${blockedUsers.size} blocked ${if (blockedUsers.size == 1) "profile" else "profiles"}"
                    },
                    onClick = { showBlockedUsersPage = true },
                    testTag = "settings_blocked_users_row"
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.VisibilityOff,
                    iconTint = QuickyPurple,
                    title = "Hide Online Status",
                    subtitle = "Others see 'Active recently' instead",
                    checked = !privacySettings.showOnlineStatus,
                    onCheckedChange = { onPrivacySettingsChange(privacySettings.copy(showOnlineStatus = !it)) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.LocationOff,
                    iconTint = QuickyGold,
                    title = "Hide Distance",
                    subtitle = "Don't show how far you are from others",
                    checked = !privacySettings.showDistance,
                    onCheckedChange = { onPrivacySettingsChange(privacySettings.copy(showDistance = !it)) }
                )
                // v3.3.7 — club personal chats: when ON, other club members
                // see a "Chat personally" entry in the member 3-dot menu;
                // when OFF, they can't start a 1:1 chat from a club. Stored
                // server-side (profiles.allow_club_dm) so every device sees
                // the receiver's live choice.
                SettingsToggleRow(
                    icon = Icons.Outlined.Forum,
                    iconTint = QuickyPurple,
                    title = "Club Members Can Chat With Me",
                    subtitle = if (allowClubDm) {
                        "Clubs member list offers a personal chat with you"
                    } else {
                        "Your club profile won't offer personal chats"
                    },
                    checked = allowClubDm,
                    onCheckedChange = onAllowClubDmChange
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.Download,
                    iconTint = QuickyPurple,
                    title = "Download My Data",
                    subtitle = "Export profile & interaction history",
                    onClick = onDownloadData,
                    showDivider = false
                )
            }

            // ---------- E. SAFETY ----------
            SettingsSection(title = "Safety") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Security,
                    iconTint = ActionLike,
                    title = "Safety & Protection Center",
                    subtitle = "Dating guidelines, reporting, zero-tolerance policy",
                    onClick = onSafetyCenterClick
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.Flag,
                    iconTint = QuickyGold,
                    title = "Report History",
                    subtitle = "Reports you've submitted",
                    onClick = { infoDialog = "No reports filed. Your reports stay anonymous and are reviewed by the Trust & Safety team." }
                )
                SettingsNavigationRow(
                    icon = if (profile.isVerified) Icons.Filled.VerifiedUser else Icons.Outlined.VerifiedUser,
                    iconTint = if (profile.isVerified) ActionVerified else QuickyPink,
                    title = "Verification Status",
                    subtitle = if (profile.isVerified) "Verified — blue badge active" else "Not verified — take the live selfie challenge",
                    onClick = { if (!profile.isVerified) onStartVerificationClick() else infoDialog = "Your Quicky Verified badge is active. 3× discovery exposure is on." },
                    showDivider = false
                )
            }

            // ---------- F. SUBSCRIPTION ----------
            SettingsSection(title = "Subscription") {
                SubscriptionSettingsCard(
                    entitlements = entitlements,
                    onClick = onPremiumStoreClick
                )
            }

            // ---------- G. CHATS & MEDIA ----------
            SettingsSection(title = "Chats & Media") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.EmojiEmotions,
                    iconTint = QuickyPink,
                    title = "Sticker Store",
                    subtitle = "Buy packs for Personal, Club and Ludo chats",
                    onClick = onStickerStoreClick,
                    showDivider = false
                )
            }

            // ---------- H. APPEARANCE ----------
            SettingsSection(title = "Appearance") {
                AppearanceSegmentedControl(
                    themeMode = themeMode,
                    onThemeChange = onThemeChange
                )
            }

            // ---------- I. ABOUT & LEGAL ----------
            SettingsSection(title = "About & Legal") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Info,
                    iconTint = QuickyPink,
                    title = "Version",
                    subtitle = "Quicky v2.1 (build ${if (BuildConfig.DEBUG) "debug" else "release"})",
                    onClick = { infoDialog = "Quicky v2.1 — Ludo Arena, chat polish, ads infrastructure, clubs lifecycle & settings." }
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.Description,
                    iconTint = QuickyPurple,
                    title = "Terms of Service (18+)",
                    subtitle = "Community rules & obligations",
                    onClick = { infoDialog = "Quicky is for adults 18+ only. Be respectful, never share other people's data, and report anything that feels unsafe." }
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.PrivacyTip,
                    iconTint = QuickyGold,
                    title = "Privacy Policy",
                    subtitle = "How your data is handled",
                    onClick = { infoDialog = "Your photos, chats and location live in your Supabase project. Nothing is sold. You can export or delete everything at any time." }
                )
                SettingsNavigationRow(
                    icon = Icons.Outlined.DeleteForever,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Delete Account",
                    subtitle = "Permanently remove profile, matches & chats",
                    onClick = { showDeleteDialog = true },
                    showDivider = false,
                    testTag = "settings_delete_account_row"
                )
            }

            // PRD §3.9: enough trailing padding for the final card to scroll
            // fully clear of the system navigation bar.
            Spacer(modifier = Modifier.height(24.dp))
        }
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
// Building blocks (PRD §3.3/§3.4 + §7 reusable architecture)
// -----------------------------------------------------------------

/**
 * A category: small uppercase heading + ONE grouped card holding all the
 * category's options (PRD §3.3 — no per-setting cards).
 */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

/**
 * Standard settings row (PRD §3.4): leading icon in a 40dp tinted
 * container, title + optional description on a shared text start, trailing
 * chevron. Rows center single-line titles and grow naturally for multiline
 * descriptions or larger font scales.
 */
@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    subtitleMaxLines: Int = 2,
    testTag: String? = null
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable { onClick() }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SettingsIconContainer(icon = icon, tint = iconTint)
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        if (showDivider) SettingsRowDivider()
    }
}

/**
 * Toggle row (PRD §3.5): the switch sits at the same trailing alignment in
 * every row, reflects the persisted value and drives the real setting.
 */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SettingsIconContainer(icon = icon, tint = iconTint)
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = QuickyPurple),
                modifier = Modifier.semantics { contentDescription = title }
            )
        }
        if (showDivider) SettingsRowDivider()
    }
}

/** 40dp rounded-square icon container with a restrained brand tint (PRD §2.2). */
@Composable
private fun SettingsIconContainer(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Inset hairline between rows inside a grouped card (aligned to the text). */
@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 70.dp, end = 16.dp)
    )
}

/**
 * Light / Dark / Auto as one compact segmented control (PRD §3.6): three
 * equal segments, clear selected state (rose-pink fill, white label —
 * readable on both themes), restrained 200ms color animation, immediate
 * preview + persistence via the existing theme preference.
 */
@Composable
private fun AppearanceSegmentedControl(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple(AppThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                Triple(AppThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
                Triple(AppThemeMode.SYSTEM, "Auto", Icons.Outlined.BrightnessAuto)
            ).forEach { (mode, label, icon) ->
                val selected = themeMode == mode
                val segmentColor by animateColorAsState(
                    targetValue = if (selected) QuickyPink else Color.Transparent,
                    animationSpec = tween(200),
                    label = "appearanceSegment"
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = segmentColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("appearance_${mode.name.lowercase()}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable { onThemeChange(mode) }
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Quicky Gold card (PRD §3.7): reduced prominence relative to the
 * account rows; small icon + title + one-line description + chevron. The
 * testing badge only ever appears for the development build's QA state.
 */
@Composable
private fun SubscriptionSettingsCard(
    entitlements: Entitlements,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_subscription_row")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(QuickyGold.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = QuickyGold,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Quicky Gold",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
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
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

// -----------------------------------------------------------------
// Blocked Users sub-page (Settings > Privacy) — real list + Unblock
// -----------------------------------------------------------------

/**
 * In-place management page for blocked profiles. Mirrors the parent
 * screen's header pattern (back chevron + 26sp title) and the grouped
 * card list style of the settings sections.
 */
@Composable
private fun BlockedUsersPage(
    blockedUsers: List<BlockedUser>,
    onUnblockUser: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // --- Header: same baseline as the Settings header. ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("blocked_users_back_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Blocked Users",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        if (blockedUsers.isEmpty()) {
            // --- Empty state (same pattern as the Chats empty state). ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    text = "No blocked users",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Profiles you block from their card, a chat, or a match row appear here with an unblock option.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            // --- Grouped card, one row per blocked profile (settings style). ---
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_blocked_list_container")
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    blockedUsers.forEachIndexed { index, blocked ->
                        BlockedUserRow(
                            blocked = blocked,
                            onUnblockUser = onUnblockUser,
                            showDivider = index < blockedUsers.lastIndex
                        )
                    }
                }
            }
            Text(
                text = "Blocked profiles can't discover you, view your profile, or message you. Unblocking makes you visible to each other again on the next refresh.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp)
            )
        }

        // PRD §3.9: trailing padding clears the system navigation bar.
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * One blocked profile: avatar snapshot, name + age + city, block date,
 * and the red Unblock action (PRD §12 Safety).
 */
@Composable
private fun BlockedUserRow(
    blocked: BlockedUser,
    onUnblockUser: (String) -> Unit,
    showDivider: Boolean
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            BlockedUserAvatar(blocked = blocked)
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = blocked.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaLine = listOfNotNull(
                    blocked.age.takeIf { it > 0 }?.toString(),
                    blocked.city.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (blocked.blockedAtEpochMs > 0L) {
                    Text(
                        text = "Blocked " + java.text.SimpleDateFormat(
                            "MMM d, yyyy",
                            java.util.Locale.US
                        ).format(java.util.Date(blocked.blockedAtEpochMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
            OutlinedButton(
                onClick = { onUnblockUser(blocked.id) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.testTag("unblock_button_${blocked.id}")
            ) {
                Text(text = "Unblock")
            }
        }
        if (showDivider) SettingsRowDivider()
    }
}

/**
 * 52dp circular avatar snapshot taken at block time: remote photo via
 * Coil, else the bundled drawable, else the profile initial.
 */
@Composable
private fun BlockedUserAvatar(blocked: BlockedUser) {
    Box(modifier = Modifier.size(52.dp)) {
        when {
            !blocked.photoUri.isNullOrBlank() -> AsyncImage(
                model = blocked.photoUri,
                contentDescription = blocked.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
            blocked.photoResId != null && blocked.photoResId > 0 -> Image(
                painter = painterResource(id = blocked.photoResId),
                contentDescription = blocked.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
            else -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            ) {
                Text(
                    text = blocked.name.trim()
                        .take(1)
                        .uppercase(java.util.Locale.US)
                        .ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
