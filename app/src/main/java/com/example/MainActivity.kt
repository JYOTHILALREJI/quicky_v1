package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MockDataProvider
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
        enableEdgeToEdge()
        setContent {
            SparkTheme {
                SparkApp()
            }
        }
    }
}

@Composable
fun SparkApp(viewModel: SparkViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showEditProfileSheet by remember { mutableStateOf(false) }

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
    BackHandler(enabled = state.selectedProfileDetail != null || state.selectedMatchForChat != null || state.selectedClubForDetail != null || state.isLudoActive || state.currentTab != SparkTab.DISCOVER) {
        when {
            state.isLudoActive -> viewModel.closeLudoGame()
            state.selectedClubForDetail != null -> viewModel.closeClubDetail()
            state.selectedProfileDetail != null -> viewModel.closeProfileDetail()
            state.selectedMatchForChat != null -> viewModel.closeChat()
            state.currentTab != SparkTab.DISCOVER -> viewModel.setTab(SparkTab.DISCOVER)
        }
    }

    if (!state.isOnboardingComplete) {
        OnboardingScreen(onComplete = { viewModel.completeOnboarding() })
    } else if (state.isLudoActive) {
        // 2-PLAYER PREMIUM LUDO GAME ARENA (PRD Section 3 - 10)
        LudoGameRoomScreen(
            room = state.ludoRoom,
            isPremium = state.entitlements.isPremium,
            onBack = { viewModel.closeLudoGame() },
            onRollDice = { viewModel.rollLudoDice() },
            onMoveToken = { tokenId -> viewModel.moveLudoToken(tokenId) },
            onSendMessage = { text -> viewModel.sendLudoChatMessage(text) },
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
            onBack = { viewModel.closeClubDetail() },
            onSendMessage = { text -> viewModel.sendClubMessage(club.id, text) },
            onSendVoiceMessage = { viewModel.sendClubMessage(club.id, "", isVoice = true) },
            onOpenStickerPicker = { viewModel.openStickerPicker() },
            onOpenPremiumStore = { viewModel.openPremiumStore() },
            onViewMemberProfile = { userId ->
                val candidate = state.discoveryDeck.find { it.id == userId }
                    ?: state.matches.map { it.user }.find { it.id == userId }
                if (candidate != null) viewModel.openProfileDetail(candidate)
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
                        onSendMessage = { text -> viewModel.sendMessage(selectedChat.id, text) },
                        onSendPrompt = { prompt -> viewModel.sendTruthOrDareInChat(selectedChat.id, prompt, selectedChat.user.id) },
                        onAnswerGame = { messageId, answer -> viewModel.answerTruthOrDare(selectedChat.id, messageId, answer) },
                        onAddReaction = { messageId, emoji -> viewModel.addReaction(selectedChat.id, messageId, emoji) },
                        onViewProfile = { profile -> viewModel.openProfileDetail(profile) },
                        onUnmatch = { viewModel.unmatchUser(selectedChat.id) },
                        onBlock = { viewModel.blockUser(selectedChat.user.id) },
                        onReport = { reason -> viewModel.reportUser(selectedChat.user.id, reason) }
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
                                    // Reset deck with sample candidate profiles
                                    viewModel.likeProfile(MockDataProvider.candidateProfiles.first(), false)
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

    // Settings & Legal Sheet
    if (state.showSettings) {
        SettingsSheet(
            onDownloadData = {
                viewModel.showToast("Data export initiated. Download link sent to your verified email.")
            },
            onDeleteAccount = {
                viewModel.showToast("Account deleted.")
            },
            onDismiss = { viewModel.toggleSettings(false) }
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
