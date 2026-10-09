package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AdsManager
import com.example.data.Analytics
import com.example.data.PushNotifications
import com.example.data.QuickyPushService
import com.example.data.SupabaseAuth
import com.example.model.AuthGate
import com.example.model.DiscoveryFilter
import com.example.model.PremiumGate
import com.example.ui.SparkTab
import com.example.ui.SparkViewModel
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.SparkTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import java.io.File

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

        // Keep the status bar icon appearance in sync with what is
        // actually on screen: the auth + onboarding flows are always
        // light-themed, so they need dark status-bar icons regardless of
        // the in-app light/dark mode setting.
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                val inAuthFlow = state.authGate != AuthGate.SIGNED_IN || !state.isOnboardingComplete
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = inAuthFlow || !isDark
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

        // v2.1 §3.6 — AdMob bootstrap (idempotent).
        LaunchedEffect(Unit) { AdsManager.initialize(context) }

        // v3.3 — push notification bootstrap: create the Quicky channels
        // (matches / messages / clubs) and capture the FCM registration
        // token, mirroring it to Supabase for the signed-in account. Grab
        // the token from logcat (tag "QuickyPush") to test server-side
        // sends via the FCM console or curl.
        LaunchedEffect(Unit) {
            PushNotifications.ensureChannels(context)
            runCatching {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    PushNotifications.saveToken(context, token)
                    Log.i("QuickyPush", "FCM registration token: $token")
                    QuickyPushService.syncTokenToServer(context)
                }
            }
        }

        // v3.3 — a tapped push notification may still be waiting for the
        // session / matches / clubs to finish loading; retry on every
        // state change until it is routed (or dropped as stale).
        LaunchedEffect(
            state.pendingNotificationRoute,
            state.matches,
            state.clubs,
            state.authGate
        ) {
            if (state.pendingNotificationRoute != null) {
                viewModel.tryConsumePendingNotificationRoute()
            }
        }

        // v3.3 — POST_NOTIFICATIONS runtime permission (Android 13+):
        // asked ONCE, only once the user is actually inside the app —
        // never during auth/onboarding. Below Android 13 notifications
        // are enabled by default; the one-shot asked-flag respects the
        // user's "don't ask again" choice in the system dialog.
        val notifPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            PushNotifications.markPermissionAsked(context)
            Analytics.log(Analytics.PUSH_PERMISSION_RESULT, "granted" to granted)
        }
        LaunchedEffect(state.authGate, state.isOnboardingComplete) {
            val insideApp =
                state.authGate == AuthGate.SIGNED_IN && state.isOnboardingComplete
            if (insideApp && PushNotifications.shouldRequestPermission(context)) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // ---- Authentication session bootstrap (Auth PRD) ----
        // Restore the persisted Supabase session on cold start, then
        // route to the auth screen / onboarding / main app.
        val activity = context as? ComponentActivity
        LaunchedEffect(Unit) {
            viewModel.onAppStart(context)
            // Cold-start Google OAuth return (quicky://auth-callback#…)
            // or a tapped push notification (quicky://notify?…).
            activity?.intent?.data?.let { uri ->
                if (uri.scheme == "quicky" &&
                    !viewModel.handleOAuthRedirect(context, uri)
                ) viewModel.handleNotificationDeepLink(uri)
            }
        }
        // Warm OAuth returns while the activity is already running
        // (singleTask launchMode reuses this instance) — same for
        // notification taps while the app is backgrounded.
        DisposableEffect(activity) {
            val listener = Consumer<Intent> { intent ->
                intent?.data?.let { uri ->
                    if (uri.scheme == "quicky" &&
                        !viewModel.handleOAuthRedirect(context, uri)
                    ) viewModel.handleNotificationDeepLink(uri)
                }
            }
            activity?.addOnNewIntentListener(listener)
            onDispose { activity?.removeOnNewIntentListener(listener) }
        }

        // Back button handling per requirements. Games Hub & Clubs are now
        // reached from the Profile page, so system back returns to Profile
        // instead of Discover. Disabled entirely while signed out or
        // mid-onboarding — those flows own their back navigation.
        BackHandler(enabled = state.authGate == AuthGate.SIGNED_IN &&
                state.isOnboardingComplete &&
                (state.showSettingsScreen || state.selectedProfileDetail != null || state.selectedMatchForChat != null || state.selectedClubForDetail != null || state.isLudoActive || state.showPersonalInformationSheet || state.currentTab != SparkTab.DISCOVER)) {
            when {
                state.showSettingsScreen -> viewModel.toggleSettingsScreen(false)
                state.isLudoActive -> viewModel.closeLudoGame()
                state.selectedClubForDetail != null -> viewModel.closeClubDetail()
                state.showPersonalInformationSheet -> viewModel.togglePersonalInformation(false)
                state.selectedProfileDetail != null -> viewModel.closeProfileDetail()
                state.selectedMatchForChat != null -> viewModel.closeChat()
                state.currentTab != SparkTab.DISCOVER -> viewModel.setTab(
                    if (state.currentTab == SparkTab.GAMES || state.currentTab == SparkTab.CLUBS) SparkTab.PROFILE
                    else SparkTab.DISCOVER
                )
            }
        }

        when {
            // Restoring / validating the persisted Supabase session.
            state.authGate == AuthGate.CHECKING -> {
                AuthCheckingScreen()
            }

            // No valid session — email/password + Google OAuth entry.
            // Fresh accounts must verify their email with the 6-digit
            // OTP from the "Quicky account creation" email, entered
            // together with the login credentials.
            state.authGate == AuthGate.SIGNED_OUT -> {
                AuthScreen(
                    isLoading = state.isAuthLoading,
                    error = state.authError,
                    notice = state.authNotice,
                    needsOtp = state.authNeedsOtp,
                    onSignUp = { email, password -> viewModel.signUp(context, email, password) },
                    onSignIn = { email, password, otp -> viewModel.signIn(context, email, password, otp) },
                    onForgotPassword = { email -> viewModel.requestPasswordReset(email) },
                    onResendOtp = { email -> viewModel.resendSignupOtp(email) },
                    onGoogleSignIn = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SupabaseAuth.googleOAuthUrl()))
                            )
                        }
                    }
                )
            }

            // Progressive 4-stage onboarding (welcome + profile creation).
            !state.isOnboardingComplete -> {
                OnboardingScreen(
                    draft = state.onboardingDraft,
                    interestCatalog = state.interestCatalog,
                    hobbyCatalog = state.hobbyCatalog,
                    isUploading = state.onboardingUploading,
                    isProcessingPhoto = state.isProcessingPhoto,
                    isLocating = state.isLocating,
                    error = state.onboardingError,
                    onSaveDraft = { updated -> viewModel.updateOnboardingDraft { updated } },
                    onStageChanged = { stage -> viewModel.goToOnboardingStage(context, stage) },
                    onAddPhoto = { uri -> viewModel.addOnboardingPhoto(context, uri) },
                    onRemovePhoto = { uri -> viewModel.removeOnboardingPhoto(uri) },
                    onCaptureLocation = { viewModel.captureOnboardingLocation(context) },
                    onComplete = { viewModel.completeOnboarding(context) }
                )
            }

            state.isLudoActive -> {
            // LUDO ARENA (v3) — realtime multiplayer: lobby → match →
            // result screen (ludoMatch == null renders the mode lobby;
            // phase FINISHED renders the standings).
            LudoArenaScreen(
                match = state.ludoMatch,
                isRolling = state.isLudoRolling,
                isPremium = PremiumGate.isPremium(state.entitlements),
                joinError = state.ludoJoinError,
                connectionOnline = state.ludoConnected,
                onBack = { viewModel.closeLudoGame() },
                onStartSoloBots = { viewModel.startLudoSoloBots() },
                onCreateOnline = { viewModel.createLudoOnlineMatch() },
                onJoinOnline = { code -> viewModel.joinLudoOnlineMatch(code) },
                onFillBots = { viewModel.fillLudoSeatsWithBots() },
                onRollDice = { viewModel.rollLudoDice() },
                onMoveToken = { tokenId -> viewModel.moveLudoToken(tokenId) },
                onSendMessage = { text, replyText, replySender ->
                    viewModel.sendLudoChatMessage(text, replyToText = replyText, replyToSender = replySender)
                },
                onSendSticker = { emoji -> viewModel.sendLudoChatMessage("", stickerEmoji = emoji) },
                onSendVoiceMessage = { viewModel.sendLudoChatMessage("", isVoice = true) },
                onOpenStickerPicker = { viewModel.openStickerPicker() },
                onOpenPremiumStore = { viewModel.openPremiumStore() },
                onPlayAgain = { viewModel.playLudoAgain() }
            )
            }

            state.selectedClubForDetail != null -> {
            // CLUB DETAIL & COMMON CHAT (PRD Section 18 & 28)
            val club = state.selectedClubForDetail!!
            val clubMessages = state.clubMessages[club.id] ?: emptyList()
            ClubDetailScreen(
                club = club,
                messages = clubMessages,
                isPremium = PremiumGate.isPremium(state.entitlements),
                currentUserId = state.userProfile.id,
                activeClubId = state.activeClubId,
                myClubName = state.clubs.find { it.id == state.activeClubId }?.name,
                chatMeta = state.clubChatMeta[club.id] ?: com.example.ui.ClubChatMeta(),
                onLoadOlderMessages = { viewModel.loadOlderClubMessages(club.id) },
                onBack = { viewModel.closeClubDetail() },
                onSendMessage = { text, replyText, replySender ->
                    viewModel.sendClubMessage(club.id, text, replyToText = replyText, replyToSender = replySender)
                },
                onSendVoiceMessage = { bytes, duration ->
                    viewModel.sendClubVoiceMessage(club.id, bytes, duration)
                },
                onRequestVoiceNote = { message ->
                    viewModel.getClubVoiceNoteFile(message)
                },
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
                onToggleReaction = { messageId, emoji ->
                    viewModel.toggleClubMessageReaction(club.id, messageId, emoji)
                },
                onReportMember = { clubId, memberUserId, reason, details ->
                    viewModel.reportClubMember(clubId, memberUserId, reason, details)
                },
                onSetMemberSuspended = { clubId, memberUserId, suspendMember ->
                    viewModel.setClubMemberSuspended(clubId, memberUserId, suspendMember)
                },
                // v3.3.7 — 1:1 personal chat from the club member list.
                onOpenPersonalChat = { member ->
                    viewModel.openClubMemberChat(club.id, member)
                },
                onDeleteClub = { clubId -> viewModel.deleteClub(clubId) },
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
            }

            else -> {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (state.selectedMatchForChat == null) {
                    SparkTopBar(
                        currentTab = state.currentTab,
                        unreadNotificationsCount = state.notifications.count { !it.isRead },
                        isBoostActive = state.entitlements.isBoostActive,
                        onNotificationsClick = { viewModel.toggleNotificationsSheet(true) },
                        onFilterClick = { viewModel.toggleFilterSheet(true) },
                        onBoostClick = { viewModel.activateBoost() },
                        onGamesClick = { viewModel.setTab(SparkTab.GAMES) },
                        onClubsClick = { viewModel.setTab(SparkTab.CLUBS) },
                        onBackClick = { viewModel.setTab(SparkTab.PROFILE) },
                        onSettingsClick = { viewModel.toggleSettingsScreen(true) }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            // The liquid-glass nav bar is OVERLAID on top of the content
            // (instead of the Scaffold bottomBar slot) so cards and lists
            // visibly glide underneath the frosted translucent surface.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
            AnimatedContent(
                targetState = Pair(state.currentTab, state.selectedMatchForChat),
                label = "screen_transition",
                modifier = Modifier
                    .fillMaxSize()
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
                        isPremium = PremiumGate.isPremium(state.entitlements),
                        // PRD v2.3 §27 — chat-header banner for FREE accounts
                        // only (real entitlement, not the QA unlock).
                        showBannerAd = PremiumGate.isAdsEnabled(state.entitlements),
                        onOpenPremiumStore = { viewModel.openPremiumStore() },
                        onOpenLudo = { viewModel.openLudoGame() },
                        onOpenStickerPicker = { viewModel.openStickerPicker() },
                        onSendVoiceMessage = { duration -> viewModel.sendVoiceMessage(selectedChat.id, duration) },
                        // v3.3.7 — view-once photo snaps (camera in composer).
                        onSendSnap = { imageBytes -> viewModel.sendSnapMessage(selectedChat.id, imageBytes) },
                        onOpenSnap = { snapMessage -> viewModel.openSnapMessage(snapMessage) }
                    )
                } else {
                    when (currentTab) {
                        SparkTab.DISCOVER -> {
                            DiscoverScreen(
                                deck = state.discoveryDeck,
                                showAdCard = state.showDiscoveryAdCard,
                                onLike = { profile, isSuperLike -> viewModel.likeProfile(profile, isSuperLike) },
                                onPass = { profile -> viewModel.passProfile(profile) },
                                onDismissAdCard = { viewModel.dismissDiscoveryAdCard() },
                                onRewind = { viewModel.rewindLastPass() },
                                onBoost = { viewModel.activateBoost() },
                                onOpenDetail = { profile -> viewModel.openProfileDetail(profile) },
                                distanceUnit = state.distanceUnit,
                                onResetDeck = {
                                    // Deck refresh — served by Supabase once connected
                                    viewModel.resetDiscoveryDeck()
                                }
                            )
                        }

                        SparkTab.MATCHES -> {
                            MatchesScreen(
                                matches = state.matches,
                                isPremium = PremiumGate.isPremium(state.entitlements),
                                // v3.2.1 — inline banner under the New Matches
                                // tray, FREE accounts only (same entitlement
                                // contract as the chat-header banner).
                                showBannerAd = PremiumGate.isAdsEnabled(state.entitlements),
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
                                isPremium = PremiumGate.isPremium(state.entitlements),
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
                                isProcessingPhoto = state.isProcessingPhoto,
                                onEditProfileClick = { showEditProfileSheet = true },
                                onStartVerificationClick = { viewModel.startVerificationChallenge() },
                                onSetPrimaryPhoto = { photoRes -> viewModel.setPrimaryPhoto(photoRes) },
                                onDeletePhoto = { photoRes -> viewModel.deletePhoto(photoRes) },
                                onAddPhoto = { uri -> viewModel.addProfilePhoto(context, uri) },
                                onPremiumStoreClick = { viewModel.openPremiumStore() }
                            )
                        }
                    }
                }
            }

                // Floating liquid-glass bottom navigation (drawn above the
                // scrolling content so it frosts whatever passes beneath it).
                // HIDDEN on the full-screen Games Hub & Clubs destinations —
                // those screens use the iOS-style back chevron in the top
                // bar to return to the Profile page instead.
                val showGlassNavBar = state.selectedMatchForChat == null &&
                        state.currentTab != SparkTab.GAMES &&
                        state.currentTab != SparkTab.CLUBS
                androidx.compose.animation.AnimatedVisibility(
                    visible = showGlassNavBar,
                    enter = fadeIn(),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SparkBottomNav(
                        currentTab = state.currentTab,
                        unreadMessagesCount = state.matches.sumOf { it.unreadCount },
                        newMatchesCount = state.matches.count { it.isNewMatch },
                        onTabSelected = { tab -> viewModel.setTab(tab) }
                    )
                }
            }
        }
        }
    }

    // Dedicated Settings screen (v2.1 §3.9) — opened from the gear icon
    // in the Profile top bar; the Profile page itself is display-only now.
    if (state.showSettingsScreen) {
        SettingsScreen(
            profile = state.userProfile,
            entitlements = state.entitlements,
            themeMode = state.themeMode,
            distanceUnit = state.distanceUnit,
            notificationPrefs = state.notificationPrefs,
            showMeOnDiscovery = state.showMeOnDiscovery,
            privacySettings = state.privacySettings,
            // v3.3.7 — server-backed "club members can chat with me" flag.
            allowClubDm = state.userProfile.allowClubDm,
            // PRD §6: the email registered on the AUTHENTICATED Supabase
            // account — from the live session, never the profile UUID.
            accountEmail = state.authSession?.email.orEmpty(),
            // Blocked Users list (Settings > Privacy) + unblock action.
            blockedUsers = state.blockedUsers,
            onBack = { viewModel.toggleSettingsScreen(false) },
            onThemeChange = { mode -> viewModel.setThemeMode(mode) },
            onDistanceUnitChange = { unit -> viewModel.setDistanceUnit(unit) },
            onNotificationPrefChange = { key, value -> viewModel.updateNotificationPref(key, value) },
            onShowMeOnDiscoveryChange = { show -> viewModel.setShowMeOnDiscovery(show) },
            onPrivacySettingsChange = { settings -> viewModel.updatePrivacySettings(settings) },
            onAllowClubDmChange = { allowed -> viewModel.setAllowClubDm(allowed) },
            onUnblockUser = { userId -> viewModel.unblockUser(userId) },
            onEditProfileClick = { showEditProfileSheet = true },
            onPersonalInformationClick = { viewModel.togglePersonalInformation(true) },
            onDiscoveryPreferencesClick = { viewModel.toggleFilterSheet(true) },
            onEditLocationClick = { viewModel.toggleEditLocationSheet(true) },
            onStickerStoreClick = { viewModel.openStickerStore() },
            onSafetyCenterClick = { viewModel.toggleSafetyCenter(true) },
            onPremiumStoreClick = { viewModel.openPremiumStore() },
            onStartVerificationClick = { viewModel.startVerificationChallenge() },
            onLogout = { viewModel.signOut(context) },
            onDownloadData = {
                viewModel.showToast("Data export initiated. Download link sent to your verified email.")
            },
            onDeleteAccount = {
                viewModel.showToast("Account deletion requires email confirmation — check your inbox.")
            }
        )
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

    // v3.3.7 — Fullscreen photo-snap viewer (FLAG_SECURE: screenshots are
    // blocked while it is on screen). Closing it marks the snap viewed and
    // permanently deletes the stored image server-side.
    if (state.viewingSnap != null) {
        SnapViewerDialog(
            isLoading = state.isLoadingSnap,
            snapBytes = state.viewingSnapBytes,
            onDismiss = { viewModel.closeSnapViewer() }
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

    // Edit Location Sheet (change request #5)
    if (state.showEditLocationSheet) {
        EditLocationSheet(
            currentCity = state.userProfile.city,
            currentLatitude = state.userProfile.latitude,
            currentLongitude = state.userProfile.longitude,
            searchResults = state.locationSearchResults,
            isSearching = state.isSearchingLocation,
            isLocating = state.isLocating,
            error = state.editLocationError,
            onSearch = { query -> viewModel.searchCityLocation(query) },
            onCaptureCurrentLocation = { viewModel.captureCurrentLocation(context) },
            onSave = { latitude, longitude, city ->
                viewModel.saveLocation(latitude, longitude, city)
            },
            onDismiss = { viewModel.toggleEditLocationSheet(false) }
        )
    }

    // Filter Sheet
    if (state.showFilterSheet) {
        FilterSheet(
            currentFilter = state.filter,
            userInterests = state.userProfile.interests,
            interestCatalog = state.interestCatalog,
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

    // Live Face Verification sheet (Get Verified card → "Verify"): the
    // system camera writes into a FileProvider cache file; the captured
    // selfie is then matched on-device against the uploaded profile
    // photos (ML Kit face detection + similarity, ≥ 60% required).
    val verificationSelfieUri = remember {
        val dir = File(context.cacheDir, "verification").apply { mkdirs() }
        val file = File(dir, "live_selfie.jpg")
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
    }
    val verificationCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        if (captured) viewModel.submitVerificationSelfie(context, verificationSelfieUri)
    }
    if (state.showVerificationDialog) {
        VerificationSheet(
            isVerifying = state.isVerifyingFace,
            result = state.faceVerificationOutcome,
            isVerified = state.userProfile.isVerified,
            onOpenCamera = { verificationCameraLauncher.launch(verificationSelfieUri) },
            onDismiss = { viewModel.dismissVerificationDialog() }
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

    // (v2.1 §3.9) The old inline SettingsSheet was replaced by the
    // dedicated SettingsScreen rendered above.

    // Personal Information & Visibility Sheet (PRD Section 65 - 69)
    if (state.showPersonalInformationSheet) {
        PersonalInformationSheet(
            profile = state.userProfile,
            onSave = { height, occupation, education, intent, interests, fieldVisibility ->
                viewModel.updatePersonalInformation(height, occupation, education, intent, interests, fieldVisibility)
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
