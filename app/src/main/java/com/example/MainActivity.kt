package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.DiscoveryFilter
import com.example.ui.SparkTab
import com.example.ui.SparkViewModel
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.SparkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge with a transparent status bar. The app theme (LIGHT
        // by default) uses dark status-bar icons so the clock and icons are
        // always visible on the white background. SparkApp keeps the icon
        // appearance in sync when the user toggles light/dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            SparkApp()
        }
    }
}

@Composable
fun SparkApp(viewModel: SparkViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (state.themeMode) {
        com.example.model.AppThemeMode.DARK -> true
        com.example.model.AppThemeMode.LIGHT -> false
        com.example.model.AppThemeMode.SYSTEM -> isSystemDark
    }

    SparkTheme(darkTheme = isDark) {
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var showEditProfileSheet by remember { mutableStateOf(false) }

        // Keep the status bar icon appearance in sync with the app theme:
        // dark icons (visible) on the light background, light icons on dark.
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !isDark
            }
        }

        // Toast and message notifications
        LaunchedEffect(state.toastMessage) {
            state.toastMessage?.let { msg ->
                scope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
                viewModel.clearToast()
            }
        }

        // Back button handling per requirements
        BackHandler(enabled = state.selectedProfileDetail != null || state.selectedMatchForChat != null || state.selectedClubForDetail != null || state.isLudoActive || state.showPersonalInformationSheet || state.currentTab != SparkTab.DISCOVER) {
            when {
                state.isLudoActive -> viewModel.closeLudoGame()
                state.selectedClubForDetail != null -> viewModel.closeClubDetail()
                state.showPersonalInformationSheet -> viewModel.togglePersonalInformation(false)
                state.selectedProfileDetail != null -> viewModel.closeProfileDetail()
                state.selectedMatchForChat != null -> viewModel.closeChat()
                state.currentTab != SparkTab.DISCOVER -> viewModel.setTab(SparkTab.DISCOVER)
            }
        }

        if (!state.isOnboardingComplete) {
            OnboardingScreen(onComplete = { viewModel.completeOnboarding() })
        } else if (state.isLudoActive && state.ludoRoom != null) {
            // 2-PLAYER PREMIUM LUDO GAME ARENA (PRD Section 3 - 10)
            val activeLudoRoom = state.ludoRoom!!
            LudoGameRoomScreen(
                room = activeLudoRoom,
                isPremium = state.entitlements.isPremium,
                onBack = { viewModel.closeLudoGame() },
                onRollDice = { viewModel.rollLudoDice() },
                onMoveToken = { tokenId -> viewModel.moveLudoToken(tokenId) },
                onSendMessage = { text, replyText, replySender ->
                    viewModel.sendLudoChatMessage(text, replyToText = replyText, replyToSender = replySender)
                },
                onSendSticker = { emoji -> viewModel.sendLudoChatMessage("", stickerEmoji = emoji) },
                onSendVoiceMessage = { viewModel.sendLudoChatMessage("", isVoice = true) },
                onOpenStickerPicker = { viewModel.openStickerPicker() },
                onOpenPremiumStore = { viewModel.openPremiumStore() }
            )
        } else if (state.selectedClubForDetail != null) {
            // CLUB DETAIL & COMMON CHAT (PRD Section 18 & 28)
            val club = state.selectedClubForDetail!!
            val clubMessages = state.clubMessages[club.id] ?: emptyList()
            ClubDetailScreen(
                club = club,
                messages = clubMessages,
                isPremium = state.entitlements.isPremium,
                currentUserId = state.userProfile.id,
                activeClubId = state.activeClubId,
                myClubName = state.clubs.find { it.id == state.activeClubId }?.name,
                onBack = { viewModel.closeClubDetail() },
                onSendMessage = { text, replyText, replySender ->
                    viewModel.sendClubMessage(club.id, text, replyToText = replyText, replyToSender = replySender)
                },
                onSendVoiceMessage = { viewModel.sendClubMessage(club.id, "", isVoice = true) },
                onOpenStickerPicker = { viewModel.openStickerPicker() },
                onOpenPremiumStore = { viewModel.openPremiumStore() },
                onViewMemberProfile = { userId ->
                    val candidate = state.discoveryDeck.find { it.id == userId }
                        ?: state.matches.map { it.user }.find { it.id == userId }
                    if (candidate != null) viewModel.openProfileDetail(candidate)
                },
                onRemoveMember = { clubId, memberUserId ->
                    viewModel.removeClubMember(clubId, memberUserId)
                },
                onJoinClub = { clubId ->
                    viewModel.joinClub(clubId)
                },
                onLeaveAndJoinClub = { newClubId ->
                    viewModel.leaveAndJoinClub(newClubId)
                },
                onLeaveClub = { clubId ->
                    viewModel.leaveClub(clubId)
                }
            )
        } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (state.selectedMatchForChat == null) {
                    SparkTopBar(
                        currentTab = state.currentTab,
                        unreadNotificationsCount = state.notifications.count { !it.isRead },
                        isBoostActive = state.entitlements.isBoostActive,
                        onNotificationsClick = { viewModel.toggleNotificationsSheet(true) },
                        onFilterClick = { viewModel.toggleFilterSheet(true) },
                        onBoostClick = { viewModel.activateBoost() }
                    )
                }
            },
            bottomBar = {
                if (state.selectedMatchForChat == null) {
                    SparkBottomNav(
                        currentTab = state.currentTab,
                        unreadMessagesCount = state.matches.sumOf { it.unreadCount },
                        newMatchesCount = state.matches.count { it.isNewMatch },
                        onTabSelected = { tab -> viewModel.setTab(tab) }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            AnimatedContent(
                targetState = Pair(state.currentTab, state.selectedMatchForChat),
                label = "screen_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { (currentTab, selectedChat) ->
                if (selectedChat != null) {
                    val activeMessages = state.messages[selectedChat.id] ?: emptyList()
                    ChatDetailScreen(
                        match = selectedChat,
                        messages = activeMessages,
                        prompts = state.truthOrDarePrompts,
                        onBack = { viewModel.closeChat() },
                        onSendMessage = { text, replyText, replySender ->
                            viewModel.sendMessage(selectedChat.id, text, replyToText = replyText, replyToSender = replySender)
                        },
                        onSendPrompt = { prompt -> viewModel.sendTruthOrDareInChat(selectedChat.id, prompt, selectedChat.user.id) },
                        onAnswerGame = { messageId, answer -> viewModel.answerTruthOrDare(selectedChat.id, messageId, answer) },
                        onAddReaction = { messageId, emoji -> viewModel.addReaction(selectedChat.id, messageId, emoji) },
                        onViewProfile = { profile -> viewModel.openProfileDetail(profile) },
                        onUnmatch = { viewModel.unmatchUser(selectedChat.id) },
                        onBlock = { viewModel.blockUser(selectedChat.user.id) },
                        onReport = { reason -> viewModel.reportUser(selectedChat.user.id, reason) },
                        isPremium = state.entitlements.isPremium,
                        onOpenPremiumStore = { viewModel.openPremiumStore() },
                        onOpenLudo = { viewModel.openLudoGame() },
                        onOpenStickerPicker = { viewModel.openStickerPicker() },
                        onSendVoiceMessage = { duration -> viewModel.sendVoiceMessage(selectedChat.id, duration) }
                    )
                } else {
                    when (currentTab) {
                        SparkTab.DISCOVER -> {
                            DiscoverScreen(
                                deck = state.discoveryDeck,
                                onLike = { profile, isSuperLike -> viewModel.likeProfile(profile, isSuperLike) },
                                onPass = { profile -> viewModel.passProfile(profile) },
                                onRewind = { viewModel.rewindLastPass() },
                                onBoost = { viewModel.activateBoost() },
                                onOpenDetail = { profile -> viewModel.openProfileDetail(profile) },
                                onResetDeck = {
                                    // Deck refresh — served by Supabase once connected
                                    viewModel.resetDiscoveryDeck()
                                }
                            )
                        }

                        SparkTab.MATCHES -> {
                            MatchesScreen(
                                matches = state.matches,
                                isPremium = state.entitlements.isPremium,
                                onStartChat = { match -> viewModel.openChat(match) },
                                onPlayTruthOrDare = { match ->
                                    val prompt = state.truthOrDarePrompts.first()
                                    viewModel.openChat(match)
                                    viewModel.sendTruthOrDareInChat(match.id, prompt, match.user.id)
                                },
                                onViewProfile = { user -> viewModel.openProfileDetail(user) },
                                onUnmatch = { matchId -> viewModel.unmatchUser(matchId) },
                                onBlockUser = { userId -> viewModel.blockUser(userId) },
                                onUnlockLikesClick = { viewModel.openPremiumStore() }
                            )
                        }

                        SparkTab.CHATS -> {
                            ChatsScreen(
                                matches = state.matches,
                                onOpenChat = { match -> viewModel.openChat(match) }
                            )
                        }

                        SparkTab.GAMES -> {
                            GamesHubScreen(
                                games = state.gamesCatalog,
                                prompts = state.truthOrDarePrompts,
                                matches = state.matches,
                                isPremium = state.entitlements.isPremium,
                                onStartGameWithMatch = { match, prompt ->
                                    viewModel.openChat(match)
                                    viewModel.sendTruthOrDareInChat(match.id, prompt, match.user.id)
                                },
                                onOpenLudo = { viewModel.openLudoGame() },
                                onLockedGameClick = { viewModel.openPremiumStore() }
                            )
                        }

                        SparkTab.CLUBS -> {
                            ClubsScreen(
                                clubs = state.clubs,
                                activeClubId = state.activeClubId,
                                onOpenClub = { club -> viewModel.openClub(club) },
                                onJoinClub = { clubId -> viewModel.joinClub(clubId) },
                                onLeaveClub = { clubId -> viewModel.leaveClub(clubId) },
                                onLeaveAndJoinClub = { newClubId -> viewModel.leaveAndJoinClub(newClubId) },
                                onCreateClubClick = { viewModel.toggleCreateClubDialog(true) }
                            )
                        }

                        SparkTab.PROFILE -> {
                            ProfileScreen(
                                userProfile = state.userProfile,
                                entitlements = state.entitlements,
                                insights = state.interactionInsights,
                                isInsightsEnabled = state.privacySettings.interactionInsightsEnabled,
                                themeMode = state.themeMode,
                                onThemeChange = { mode -> viewModel.setThemeMode(mode) },
                                onEditProfileClick = { showEditProfileSheet = true },
                                onPersonalInformationClick = { viewModel.togglePersonalInformation(true) },
                                onDiscoveryPreferencesClick = { viewModel.toggleFilterSheet(true) },
                                onStartVerificationClick = { viewModel.startVerificationChallenge() },
                                onSetPrimaryPhoto = { photoRes -> viewModel.setPrimaryPhoto(photoRes) },
                                onDeletePhoto = { photoRes -> viewModel.deletePhoto(photoRes) },
                                onAddPhoto = { photoRes -> viewModel.addPhoto(photoRes) },
                                onPremiumStoreClick = { viewModel.openPremiumStore() },
                                onStickerStoreClick = { viewModel.openStickerStore() },
                                onSafetyCenterClick = { viewModel.toggleSafetyCenter(true) },
                                onPrivacyCenterClick = { viewModel.togglePrivacyCenter(true) },
                                onSettingsClick = { viewModel.toggleSettings(true) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Match Celebration Modal Overlay
    state.matchCelebration?.let { matchedProfile ->
        MatchCelebrationDialog(
            matchedProfile = matchedProfile,
            onSendMessage = { viewModel.startChatFromCelebration(matchedProfile, launchGame = false) },
            onPlayGame = { viewModel.startChatFromCelebration(matchedProfile, launchGame = true) },
            onDismiss = { viewModel.dismissMatchCelebration() }
        )
    }

    // Profile Detail Sheet
    state.selectedProfileDetail?.let { detailProfile ->
        ProfileDetailSheet(
            profile = detailProfile,
            onDismiss = { viewModel.closeProfileDetail() },
            onLike = {
                viewModel.likeProfile(detailProfile, false)
                viewModel.closeProfileDetail()
            },
            onPass = {
                viewModel.passProfile(detailProfile)
                viewModel.closeProfileDetail()
            },
            onBlock = {
                viewModel.blockUser(detailProfile.id)
            },
            onReport = {
                viewModel.reportUser(detailProfile.id, "Reported from profile sheet")
                viewModel.closeProfileDetail()
            }
        )
    }

    // Filter Sheet
    if (state.showFilterSheet) {
        FilterSheet(
            currentFilter = state.filter,
            onApply = { newFilter -> viewModel.updateFilters(newFilter) },
            onDismiss = { viewModel.toggleFilterSheet(false) }
        )
    }

    // Notifications Sheet
    if (state.showNotificationsSheet) {
        NotificationsSheet(
            notifications = state.notifications,
            onMarkAllRead = { viewModel.markNotificationsRead() },
            onDismiss = { viewModel.toggleNotificationsSheet(false) }
        )
    }

    // Premium Store Sheet
    if (state.showPremiumStore) {
        PremiumStoreSheet(
            entitlements = state.entitlements,
            onPurchaseSubscription = { isYearly -> viewModel.purchaseSubscription(isYearly) },
            onPurchaseConsumable = { itemType -> viewModel.purchaseConsumable(itemType) },
            onDismiss = { viewModel.closePremiumStore() }
        )
    }

    // Safety Center Sheet
    if (state.showSafetyCenter) {
        SafetyCenterSheet(
            onDismiss = { viewModel.toggleSafetyCenter(false) }
        )
    }

    // Privacy Center Sheet
    if (state.showPrivacyCenter) {
        PrivacyCenterSheet(
            currentSettings = state.privacySettings,
            onSaveSettings = { newSettings -> viewModel.updatePrivacySettings(newSettings) },
            onDismiss = { viewModel.togglePrivacyCenter(false) }
        )
    }

    // Settings & Legal Sheet (Account & Legal)
    if (state.showSettings) {
        SettingsSheet(
            currentTheme = state.themeMode,
            onThemeChange = { mode -> viewModel.setThemeMode(mode) },
            onDownloadData = {
                viewModel.showToast("Data export initiated. Download link sent to your verified email.")
            },
            onDeleteAccount = {
                viewModel.showToast("Account deleted.")
            },
            onDismiss = { viewModel.toggleSettings(false) }
        )
    }

    // Personal Information & Visibility Sheet (PRD Section 65 - 69)
    if (state.showPersonalInformationSheet) {
        PersonalInformationSheet(
            profile = state.userProfile,
            onSave = { height, occupation, education, intent, fieldVisibility ->
                viewModel.updatePersonalInformation(height, occupation, education, intent, fieldVisibility)
            },
            onDismiss = { viewModel.togglePersonalInformation(false) }
        )
    }

    // Edit Profile Sheet
    if (showEditProfileSheet) {
        EditProfileSheet(
            profile = state.userProfile,
            onSave = { name, bio, intent, occupation ->
                viewModel.updateProfile(name, bio, intent, occupation)
            },
            onDismiss = { showEditProfileSheet = false }
        )
    }

    // Sticker Store Sheet (PRD Section 21)
    if (state.showStickerStore) {
        StickerStoreSheet(
            stickerPacks = state.stickerPacks,
            onPurchasePack = { packId -> viewModel.purchaseStickerPack(packId) },
            onDismiss = { viewModel.closeStickerStore() }
        )
    }

    // Sticker Picker Sheet (PRD Section 22)
    if (state.showStickerPicker) {
        StickerPickerSheet(
            stickerPacks = state.stickerPacks,
            onSelectSticker = { sticker ->
                when {
                    state.isLudoActive -> viewModel.sendLudoChatMessage("", stickerEmoji = sticker.emojiRepresentation)
                    state.selectedClubForDetail != null -> viewModel.sendClubMessage(state.selectedClubForDetail!!.id, "", stickerEmoji = sticker.emojiRepresentation)
                    state.selectedMatchForChat != null -> viewModel.sendMessage(state.selectedMatchForChat!!.id, sticker.emojiRepresentation)
                }
            },
            onOpenStickerStore = { viewModel.openStickerStore() },
            onDismiss = { viewModel.closeStickerPicker() }
        )
    }

    // Create Club Sheet (PRD Section 12, 13, 14, 15)
    if (state.showCreateClubDialog) {
        CreateClubSheet(
            lastCreatedTimestamp = state.lastClubCreatedTimestamp,
            onCreateClub = { name, desc, category, emoji ->
                viewModel.createClub(name, desc, category, emoji)
            },
            onDismiss = { viewModel.toggleCreateClubDialog(false) }
        )
    }
    }
}
