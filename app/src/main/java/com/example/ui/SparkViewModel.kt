package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.BuildConfig
import com.example.data.AppContent
import com.example.data.Analytics
import com.example.data.FaceVerifier
import com.example.data.LudoMatchRepository
import com.example.data.LudoRealtime
import com.example.data.LudoTime
import com.example.data.QuickyPushService
import com.example.data.SupabaseAuth
import com.example.data.SupabaseRepository
import com.example.game.LudoEngine
import com.example.model.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import com.example.data.PushNotifications

enum class SparkTab {
    DISCOVER,
    MATCHES,
    CHATS,
    GAMES,
    CLUBS,
    PROFILE
}

/**
 * Per-club chat synchronization state (v3.3.4 chunked history + polling).
 * Lives in [SparkUiState.clubChatMeta] keyed by club id.
 */
data class ClubChatMeta(
    /** True while the FIRST page of history loads (chat open spinner). */
    val isLoadingInitial: Boolean = false,
    /** True while an OLDER page is being fetched (scroll-up spinner). */
    val isLoadingOlder: Boolean = false,
    /** False once an older page came back short — no more history above. */
    val hasMoreMessages: Boolean = false,
    /** Cursor for the next older page (oldest `created_at` loaded). */
    val oldestLoadedIso: String? = null,
    /** Cursor for live polling (newest `created_at` loaded). */
    val newestLoadedIso: String? = null,
    /** True while the open-chat poll loop runs for this club. */
    val isPolling: Boolean = false
)

data class SparkUiState(
    val isOnboardingComplete: Boolean = true,

    // ---- Authentication (Auth & Onboarding PRD) ----
    val authGate: AuthGate = AuthGate.CHECKING,
    val authSession: SupabaseAuth.AuthSession? = null,
    val isAuthLoading: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    /** True while the account exists but its email is still unverified — the sign-in form then asks for the 6-digit emailed OTP alongside the credentials. */
    val authNeedsOtp: Boolean = false,

    // ---- Progressive onboarding draft (PRD stages 1-4) ----
    val onboardingDraft: OnboardingDraft = OnboardingDraft(),
    val onboardingUploading: Boolean = false,
    val onboardingError: String? = null,
    val isProcessingPhoto: Boolean = false,
    /** True while the onboarding location field is resolving the user's GPS position. */
    val isLocating: Boolean = false,

    // ---- Admin-manageable catalogs (Supabase `interests` / `hobbies`) ----
    val interestCatalog: List<String> = AppContent.interestCatalog,
    val hobbyCatalog: List<String> = AppContent.hobbyCatalog,

    val currentTab: SparkTab = SparkTab.DISCOVER,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT, // PRD Section 3: Default is LIGHT
    val userProfile: UserProfile = AppContent.currentUser,
    val discoveryDeck: List<UserProfile> = emptyList(),
    val passedHistory: List<UserProfile> = emptyList(),
    val matchCelebration: UserProfile? = null,
    val matches: List<MatchItem> = emptyList(),
    val selectedMatchForChat: MatchItem? = null,
    val messages: Map<String, List<ChatMessage>> = emptyMap(),
    val truthOrDarePrompts: List<TruthOrDarePrompt> = AppContent.truthOrDarePrompts,
    val gamesCatalog: List<GameDefinition> = AppContent.gamesCatalog,
    val entitlements: Entitlements = Entitlements(),
    val privacySettings: PrivacySettings = PrivacySettings(),
    val discoveryPreferences: DiscoveryPreferences = DiscoveryPreferences(),
    val interactionInsights: List<InteractionInsight> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    /**
     * v3.3: quicky://notify deep link from a tapped push notification that
     * couldn't be routed yet (e.g. cold start — matches/clubs still loading
     * from the server). Retried as that data lands; see
     * [SparkViewModel.tryConsumePendingNotificationRoute].
     */
    val pendingNotificationRoute: String? = null,
    val toastMessage: String? = null,
    val selectedProfileDetail: UserProfile? = null,
    val showFilterSheet: Boolean = false,
    val showPremiumStore: Boolean = false,
    val showSafetyCenter: Boolean = false,
    val showPrivacyCenter: Boolean = false,
    val showPersonalInformationSheet: Boolean = false,
    val showVerificationDialog: Boolean = false,
    /** Get Verified: busy while the live selfie is being matched on-device. */
    val isVerifyingFace: Boolean = false,
    /** Get Verified: outcome of the last live-selfie match attempt (null = fresh). */
    val faceVerificationOutcome: FaceVerifier.Result? = null,
    val showSettings: Boolean = false,
    val showNotificationsSheet: Boolean = false,
    val activeVoiceCallMatch: MatchItem? = null,
    val isClubVoiceChatActive: Boolean = false,
    val replyToMessage: ChatMessage? = null,

    // Clubs & Social Communities state
    val clubs: List<Club> = emptyList(),
    val activeClubId: String? = null,
    val selectedClubForDetail: Club? = null,
    val clubMessages: Map<String, List<ClubMessage>> = emptyMap(),
    /** v3.3.4 chunked-history + polling cursors, keyed by club id. */
    val clubChatMeta: Map<String, ClubChatMeta> = emptyMap(),
    val lastClubCreatedTimestamp: Long = 0L,
    val showCreateClubDialog: Boolean = false,

    // Ludo Arena state (v2.1 §3.2 — ludoMatch == null shows the mode lobby)
    val isLudoActive: Boolean = false,
    val ludoMatch: LudoMatch? = null,
    val isLudoRolling: Boolean = false,
    val ludoJoinError: String? = null,
    val isCreatingLudoMatch: Boolean = false,
    /** v3: Realtime socket health — drives the "Connection lost" chip. */
    val ludoConnected: Boolean = true,

    // Sticker Store state
    val stickerPacks: List<StickerPack> = AppContent.stickerPackCatalog,
    val showStickerStore: Boolean = false,
    val showStickerPicker: Boolean = false,

    // Dedicated Settings screen (v2.1 §3.9) + its persisted prefs
    val showSettingsScreen: Boolean = false,
    val distanceUnit: String = "km", // "km" | "mi"
    val notificationPrefs: NotificationPreferences = NotificationPreferences(),
    val showMeOnDiscovery: Boolean = true,

    // Blocked profiles (Settings > Privacy > Blocked Users). Snapshots
    // recorded at block time; persisted locally so the list survives
    // restarts, and every deck/conversation fetch excludes these ids.
    val blockedUsers: List<BlockedUser> = emptyList(),

    // Discovery native-ad cadence (v2.1 §3.6.1; v2.3 §17–§19 random 1–6)
    val showDiscoveryAdCard: Boolean = false,
    val discoverySwipesSinceAd: Int = 0,
    /** Random 1–6 swipe threshold for the NEXT ad; regenerated after each ad. */
    val discoveryAdThreshold: Int = (1..6).random(),

    // Edit Location sheet (change request #5)
    val showEditLocationSheet: Boolean = false,
    val locationSearchResults: List<GeoSuggestion> = emptyList(),
    val isSearchingLocation: Boolean = false,
    val editLocationError: String? = null
) {
    val filter: DiscoveryFilter get() = discoveryPreferences
}

class SparkViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SparkUiState())
    val uiState: StateFlow<SparkUiState> = _uiState.asStateFlow()

    init {
        // Ludo room chat ownership: polled remote messages compare sender
        // ids against the signed-in user to decide "mine vs theirs".
        LudoMatchRepository.currentUserIdProvider = { _uiState.value.userProfile.id }

        // Load remote content from Supabase when credentials are configured.
        // Until then the app runs on the bundled static catalog in AppContent.
        if (SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                val remoteGames = SupabaseRepository.fetchGamesCatalog()
                if (remoteGames.isNotEmpty()) {
                    _uiState.update { it.copy(gamesCatalog = remoteGames) }
                }
                val remotePrompts = SupabaseRepository.fetchTruthOrDarePrompts()
                if (remotePrompts.isNotEmpty()) {
                    _uiState.update { it.copy(truthOrDarePrompts = remotePrompts) }
                }
            }
        }
    }

    fun setTab(tab: SparkTab) {
        // v3.2.2: opening the Clubs tab re-fetches the server list so
        // clubs created by other accounts since sign-in show up without
        // a logout/login cycle (search + interest-tag filter see them).
        if (tab == SparkTab.CLUBS) {
            _uiState.value.authSession?.let { session ->
                if (SupabaseRepository.isConfigured()) loadClubsFromServer(session)
            }
        }
        _uiState.update { it.copy(currentTab = tab, selectedMatchForChat = null) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode, toastMessage = "Theme set to ${mode.name.lowercase().replaceFirstChar { c -> c.uppercase() }}") }
    }

    // ================================================================
    // AUTHENTICATION & ONBOARDING (Auth & Onboarding PRD)
    // ================================================================

    /** Application context for draft persistence (never leaks an Activity). */
    private var appContext: Context? = null

    /** Raw bytes of picked onboarding photos, keyed by content uri. */
    private val onboardingPhotoBytes = mutableMapOf<String, ByteArray>()

    private val onboardingPrefs = "quicky_onboarding"

    /**
     * Cold-start entry point: restores the persisted Supabase session
     * (validating/refreshing tokens in the background) and routes the
     * app to the auth screen, onboarding or the main experience.
     * Call exactly once from the UI.
     */
    fun onAppStart(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        // Blocked users restore FIRST (device-local, synchronous) so the
        // block list is already in state before any deck or conversation
        // fetch runs — blocked profiles can never flash back into the app.
        loadPersistedBlockedUsers(context)
        // Remote catalogs are non-critical content: load them in the
        // background so a slow fetch can never delay (or block) the auth
        // gate on the cold-start critical path.
        viewModelScope.launch {
            if (SupabaseRepository.isConfigured()) {
                // Admins can extend the catalogs without an app release.
                SupabaseRepository.fetchInterestCatalog().takeIf { it.isNotEmpty() }?.let { remote ->
                    _uiState.update { it.copy(interestCatalog = remote) }
                }
                SupabaseRepository.fetchHobbyCatalog().takeIf { it.isNotEmpty() }?.let { remote ->
                    _uiState.update { it.copy(hobbyCatalog = remote) }
                }
            }
        }
        // Restore the persisted Supabase session (validating/refreshing
        // tokens) and route the app to the auth screen, onboarding or the
        // main experience. Sessions survive normal app closure — only an
        // explicit sign-out or a server-side revocation signs the user out.
        viewModelScope.launch {
            val session = SupabaseAuth.restoreSession(context)
            if (session == null) {
                _uiState.update { it.copy(authGate = AuthGate.SIGNED_OUT) }
            } else {
                adoptSession(session)
            }
        }
    }

    /**
     * Signs in with email + password. When the account's email is not
     * verified yet, the optional [otp] (the 6-digit code from the
     * "Quicky account creation" email) is redeemed right after the
     * credentials check — a freshly created account can therefore only
     * sign in with credentials AND the emailed OTP.
     */
    fun signIn(context: Context, email: String, password: String, otp: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null, authNotice = null) }
            val result = SupabaseAuth.signIn(context, email, password)
            when {
                // Credentials OK and email already verified.
                result.success && result.session != null -> adoptSession(result.session)

                // Credentials OK but email unverified, and the user typed
                // the emailed code — verify the account and adopt the
                // session the verify endpoint returns.
                !result.success && result.isEmailNotConfirmed && !otp.isNullOrBlank() -> {
                    val verified = SupabaseAuth.verifyOtp(context, email, otp)
                    if (verified.success && verified.session != null) {
                        adoptSession(verified.session)
                    } else {
                        _uiState.update {
                            it.copy(
                                authError = verified.errorMessage
                                    ?: "That code didn't match — double-check the 6 digits and try again."
                            )
                        }
                    }
                }

                // Credentials OK but email unverified and no code given —
                // ask for the OTP (kept on screen until verification or a
                // different account is used).
                !result.success && result.isEmailNotConfirmed ->
                    _uiState.update {
                        it.copy(
                            authNeedsOtp = true,
                            authNotice = "Your email isn't verified yet. Enter the 6-digit code " +
                                    "from your Quicky account-creation email together with your " +
                                    "password to sign in."
                        )
                    }

                else ->
                    _uiState.update { it.copy(authError = result.errorMessage ?: "Authentication failed.") }
            }
            _uiState.update { it.copy(isAuthLoading = false) }
        }
    }

    /** Re-sends the signup verification email with a fresh 6-digit OTP. */
    fun resendSignupOtp(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null, authNotice = null) }
            val result = SupabaseAuth.resendOtp(email)
            _uiState.update {
                it.copy(
                    isAuthLoading = false,
                    authNotice = if (result.success)
                        "A fresh 6-digit code is on its way to your inbox — it may take a minute to arrive."
                    else result.errorMessage
                )
            }
        }
    }

    /** Creates a Supabase Auth account with email + password. */
    fun signUp(context: Context, email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null, authNotice = null) }
            handleAuthResult(SupabaseAuth.signUp(context, email, password))
        }
    }

    /** Sends the password-recovery email. */
    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(authError = null, authNotice = null) }
            val result = SupabaseAuth.requestPasswordReset(email)
            _uiState.update {
                it.copy(
                    authNotice = if (result.success)
                        "Reset link sent — check your inbox (and spam folder)."
                    else result.errorMessage
                )
            }
        }
    }

    /**
     * Handles the Google OAuth return deep link
     * (quicky://auth-callback#access_token=…). Returns true when the
     * link carried a session.
     */
    fun handleOAuthRedirect(context: Context, uri: Uri): Boolean {
        val session = SupabaseAuth.parseOAuthRedirect(uri) ?: return false
        viewModelScope.launch {
            SupabaseAuth.writeSession(context, session)
            adoptSession(session)
        }
        return true
    }

    /**
     * v3.3: routes a quicky://notify deep link (a tapped push
     * notification — see PushNotifications.buildDeepLink). The route is
     * always stashed first: on cold start the session / matches / clubs
     * are still loading, so routing is retried by
     * [tryConsumePendingNotificationRoute] as that data lands. Returns
     * true when the link was a notify link (handled now or queued).
     */
    fun handleNotificationDeepLink(uri: Uri): Boolean {
        if (uri.scheme != "quicky" || uri.host != "notify") return false
        _uiState.update { it.copy(pendingNotificationRoute = uri.toString()) }
        Analytics.log(
            Analytics.PUSH_NOTIFICATION_TAPPED,
            "target" to (uri.getQueryParameter("target") ?: "app")
        )
        tryConsumePendingNotificationRoute()
        return true
    }

    /**
     * Attempts to route the pending notification deep link. Called from
     * the UI whenever the pending route / matches / clubs change (a
     * LaunchedEffect in SparkApp) — cheap no-op when nothing is pending.
     * Waits while the needed list is still empty (server fetch in
     * flight), and falls back to the owning tab once data arrived
     * without the referenced id.
     */
    fun tryConsumePendingNotificationRoute() {
        val state = _uiState.value
        val route = state.pendingNotificationRoute ?: return
        if (state.authGate != AuthGate.SIGNED_IN || !state.isOnboardingComplete) return
        val uri = Uri.parse(route)
        when (uri.getQueryParameter("target")) {
            "chat", "message" -> {
                val chatId =
                    uri.getQueryParameter("chat_id") ?: uri.getQueryParameter("match_id")
                val match = chatId?.let { id -> state.matches.firstOrNull { it.id == id } }
                when {
                    match != null -> {
                        openChat(match)
                        _uiState.update { it.copy(pendingNotificationRoute = null) }
                    }
                    // Matches still loading (cold start) — retry on arrival.
                    state.matches.isEmpty() -> Unit
                    // Loaded, but the id is stale — land on the Chats tab.
                    else -> {
                        setTab(SparkTab.CHATS)
                        _uiState.update { it.copy(pendingNotificationRoute = null) }
                    }
                }
            }
            "match" -> {
                setTab(SparkTab.MATCHES)
                _uiState.update { it.copy(pendingNotificationRoute = null) }
            }
            "club" -> {
                val clubId = uri.getQueryParameter("club_id")
                val club = clubId?.let { id -> state.clubs.firstOrNull { it.id == id } }
                when {
                    club != null -> {
                        setTab(SparkTab.CLUBS)
                        openClub(club)
                        _uiState.update { it.copy(pendingNotificationRoute = null) }
                    }
                    // Clubs still loading — retry on arrival (the signed-in
                    // bootstrap / setTab(CLUBS) fetch populates them).
                    state.clubs.isEmpty() -> Unit
                    else -> {
                        setTab(SparkTab.CLUBS)
                        _uiState.update { it.copy(pendingNotificationRoute = null) }
                    }
                }
            }
            // No target — plain "open the app" notification tap.
            else -> _uiState.update { it.copy(pendingNotificationRoute = null) }
        }
    }

    /**
     * Signs out: revokes the session server-side, clears local tokens
     * and resets all in-memory app state. Profile, matches and chat
     * history live in the database and reload on the next sign-in.
     */
    fun signOut(context: Context) {
        viewModelScope.launch {
            sessionRefreshJob?.cancel()
            boostExpiryJob?.cancel()
            stopNotificationPolling()
            shownNotificationIds.clear()
            _uiState.value.clubChatMeta.filterValues { it.isPolling }.keys.forEach {
                stopClubChatPolling(it)
            }
            clubVoiceCache.clear()
            SupabaseAuth.signOut(context)
            onboardingPhotoBytes.clear()
            _uiState.update {
                it.copy(
                    authGate = AuthGate.SIGNED_OUT,
                    authSession = null,
                    authError = null,
                    authNotice = null,
                    authNeedsOtp = false,
                    isAuthLoading = false,
                    isOnboardingComplete = false,
                    onboardingDraft = OnboardingDraft(),
                    onboardingUploading = false,
                    onboardingError = null,
                    isProcessingPhoto = false,
                    isLocating = false,
                    userProfile = AppContent.currentUser,
                    currentTab = SparkTab.DISCOVER,
                    // v3.3.1 fix: every overlay/dialog/sheet flag closes on
                    // sign-out. SettingsScreen (and the sheets it opens)
                    // render OUTSIDE the authGate when-block in SparkApp, so
                    // a stale showSettingsScreen=true kept the Settings page
                    // stacked on top of the login screen after logging out.
                    showSettingsScreen = false,
                    showSettings = false,
                    showFilterSheet = false,
                    showPremiumStore = false,
                    showSafetyCenter = false,
                    showPrivacyCenter = false,
                    showPersonalInformationSheet = false,
                    showVerificationDialog = false,
                    showNotificationsSheet = false,
                    showCreateClubDialog = false,
                    showStickerStore = false,
                    showStickerPicker = false,
                    showEditLocationSheet = false,
                    replyToMessage = null,
                    // v2.3 §13/§46 — every account-scoped value resets on
                    // sign-out: the NEXT account starts from its own
                    // server-side state, never from what this device cached.
                    entitlements = Entitlements(),
                    discoveryDeck = emptyList(),
                    passedHistory = emptyList(),
                    // v2.3 §45 — Discovery ad session state never leaks
                    // across accounts (fresh random threshold for B).
                    showDiscoveryAdCard = false,
                    discoverySwipesSinceAd = 0,
                    discoveryAdThreshold = (1..6).random(),
                    matchCelebration = null,
                    matches = emptyList(),
                    selectedMatchForChat = null,
                    messages = emptyMap(),
                    selectedProfileDetail = null,
                    clubs = emptyList(),
                    activeClubId = null,
                    selectedClubForDetail = null,
                    clubMessages = emptyMap(),
                    clubChatMeta = emptyMap(),
                    notifications = emptyList(),
                    pendingNotificationRoute = null,
                    interactionInsights = emptyList(),
                    ludoMatch = null,
                    isLudoActive = false,
                    isLudoRolling = false,
                    ludoJoinError = null,
                    ludoConnected = true
                )
            }
        }
    }

    private suspend fun handleAuthResult(result: SupabaseAuth.AuthResult) {
        when {
            result.success && result.session != null -> adoptSession(result.session)
            result.success && result.needsEmailConfirmation ->
                _uiState.update {
                    it.copy(
                        authNeedsOtp = true,
                        authNotice = "Account created! We emailed a 6-digit verification code " +
                                "to your inbox. Enter it on the Log In tab together with your " +
                                "password to activate your account."
                    )
                }
            else ->
                _uiState.update { it.copy(authError = result.errorMessage ?: "Authentication failed.") }
        }
        _uiState.update { it.copy(isAuthLoading = false) }
    }

    /**
     * Loads the signed-in user's profiles row and routes accordingly:
     * completed onboarding straight into the app, otherwise into the
     * onboarding flow — resuming a locally persisted draft if present.
     */
    private suspend fun adoptSession(session: SupabaseAuth.AuthSession) {
        val remote = SupabaseRepository.fetchProfile(session.userId, session.accessToken)
        if (remote == null) {
            _uiState.update {
                it.copy(
                    authGate = AuthGate.SIGNED_IN,
                    authSession = session,
                    authNeedsOtp = false,
                    isOnboardingComplete = false,
                    onboardingDraft = appContext?.let { ctx -> readPersistedDraft(ctx) } ?: OnboardingDraft()
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    authGate = AuthGate.SIGNED_IN,
                    authSession = session,
                    authNeedsOtp = false,
                    userProfile = remote.profile,
                    isOnboardingComplete = remote.onboardingCompleted,
                    // The shared-interest filter follows the user's own
                    // (onboarding-chosen) interests — one source of truth.
                    discoveryPreferences = it.discoveryPreferences.copy(
                        interests = remote.profile.interests
                    ),
                    onboardingDraft = if (remote.onboardingCompleted) OnboardingDraft()
                    else appContext?.let { ctx -> readPersistedDraft(ctx) } ?: OnboardingDraft()
                )
            }
            // v2: fill the discovery deck from the server-side ranked RPC.
            if (remote.onboardingCompleted) autoLoadDiscoveryDeck()
            // v3.1: restore this account's conversations (chats + message
            // history) from the server so the Chats tab survives restarts
            // and seeded demo chats show up right after sign-in.
            if (remote.onboardingCompleted) loadServerConversations(session)
            // v3.2.2: club discovery — load EVERY account's clubs (with
            // member lists) so the Clubs tab lists and searches across
            // all users, and re-attach this account's own membership.
            if (remote.onboardingCompleted) loadClubsFromServer(session)
        }
        // v2.3 §12 — after login, this account's Boost loads from ITS OWN
        // Supabase row (auth user id), never from the previous account's
        // in-memory state: A active → B inactive → A active again (Test A).
        loadAccountBoost(session)
        // Keep the JWT fresh while the app stays open (Supabase access
        // tokens expire ~1h) — the session itself lives in SharedPreferences
        // and survives app closure until the user explicitly signs out.
        scheduleSessionRefresh(session)
        // v3.3: link this device's FCM registration token to the freshly
        // signed-in account (device_tokens upsert) so server-side pushes
        // can address it. Covers token-before-sign-in and account switches;
        // fire-and-forget — offline just logs, retried next sign-in.
        appContext?.let { ctx -> QuickyPushService.syncTokenToServer(ctx) }
        // v3.3.4: poll notification rows (mentions / member reports) while
        // the session is alive and render the unseen ones as real system
        // notifications (clubs channel + quicky:// club deep link).
        startNotificationPolling()
    }

    /** Hourly session keeper — refreshes the JWT shortly before it expires. */
    private var sessionRefreshJob: Job? = null

    /**
     * v3.1: loads the signed-in account's conversations (match rows +
     * message history + other-side profiles) from Supabase into the Chats
     * tab. Chats used to be session-only; now they survive restarts, and
     * seeded demo conversations (supabase/seed_data.sql) appear here too.
     *
     * Merge rules: conversations created locally THIS session (e.g. a
     * just-celebrated match) always win — the server list only fills gaps.
     */
    private fun loadServerConversations(session: SupabaseAuth.AuthSession) {
        if (!SupabaseRepository.isConfigured()) return
        viewModelScope.launch {
            val fetched = SupabaseRepository.fetchConversations(
                session.userId, session.accessToken
            ) ?: return@launch
            // A blocked profile never comes back through the server's
            // conversation restore — the block list wins over the remote
            // match rows (also covers blocks made in a previous session).
            val blockedIds = _uiState.value.blockedUsers.map { it.id }.toSet()
            val remote = fetched.filter { it.profile.id !in blockedIds }
            if (remote.isEmpty()) return@launch

            _uiState.update { state ->
                val serverMatches = remote.map { conv ->
                    MatchItem(
                        id = conv.conversationId,
                        user = conv.profile,
                        matchedAt = LudoTime.parseIsoToEpochMs(conv.matchedAtIso)
                            ?.let { matchedAtLabel(it) } ?: "Recently",
                        lastMessage = conv.lastMessageText,
                        unreadCount = conv.unreadCount,
                        hasActiveGame = conv.hasActiveGame,
                        isNewMatch = conv.isNewMatch && conv.unreadCount > 0
                    )
                }
                // Locally created conversations (this session) keep their
                // place; the server list only adds what is missing.
                val localOnly = state.matches.filter { local ->
                    remote.none { it.conversationId == local.id }
                }
                val messageMap = state.messages.toMutableMap()
                remote.forEach { conv ->
                    if (!messageMap.containsKey(conv.conversationId)) {
                        messageMap[conv.conversationId] = conv.messages.map { msg ->
                            ChatMessage(
                                id = msg.id,
                                conversationId = msg.conversationId,
                                senderId = msg.senderId,
                                text = msg.text,
                                timestamp = LudoTime.isoToClock(msg.createdAtIso) ?: "",
                                isMine = msg.senderId == "user_me" ||
                                        msg.senderId == session.userId,
                                isRead = msg.isRead
                            )
                        }
                    }
                }
                state.copy(
                    matches = localOnly + serverMatches,
                    messages = messageMap
                )
            }
        }
    }

    /** "Oct 5"-style label for a matched-at epoch (ChatsScreen right meta). */
    private fun matchedAtLabel(epochMs: Long): String =
        java.text.SimpleDateFormat("MMM d", Locale.US).format(java.util.Date(epochMs))

    /**
     * v3.2.2: loads ALL active clubs + member lists from Supabase into
     * the Clubs tab. Until now clubs were session-only in-memory state —
     * a club created on one account never appeared for any other account
     * (search / interest-tag filters found nothing) and disappeared on
     * sign-out. The server list wins; clubs created locally THIS session
     * (e.g. just now, offline) keep their place. activeClubId is re-derived
     * from this account's own membership row.
     */
    private fun loadClubsFromServer(session: SupabaseAuth.AuthSession) {
        if (!SupabaseRepository.isConfigured()) return
        viewModelScope.launch {
            val remoteClubs = SupabaseRepository.fetchClubs(
                accessToken = session.accessToken
            ) ?: return@launch

            _uiState.update { state ->
                // Locally created clubs this session that the server hasn't
                // seen yet stay on top (their server writes are in flight).
                val localOnly = state.clubs.filter { local ->
                    remoteClubs.none { it.id == local.id }
                }
                val merged = localOnly + remoteClubs
                val myMembershipClubId = merged.firstOrNull { club ->
                    club.members.any { it.userId == session.userId }
                }?.id
                state.copy(
                    clubs = merged,
                    activeClubId = myMembershipClubId
                        ?: state.activeClubId?.takeIf { id -> merged.any { it.id == id } },
                    selectedClubForDetail = state.selectedClubForDetail?.let { sel ->
                        merged.firstOrNull { it.id == sel.id } ?: sel
                    }
                )
            }
        }
    }


    /** Flips an active boost off at its exact expiry (PRD v2.3 §10). */
    private var boostExpiryJob: Job? = null

    /**
     * v2.3 §12: loads the AUTHENTICATED user's boost from Supabase and
     * mirrors it into the UI. The authoritative state is the user-scoped
     * `boosts` row — local in-memory state is only a reflection of it.
     */
    private fun loadAccountBoost(session: SupabaseAuth.AuthSession) {
        boostExpiryJob?.cancel()
        if (!SupabaseRepository.isConfigured()) return
        viewModelScope.launch {
            val boost = SupabaseRepository.fetchActiveBoost(session)
            val active = boost?.isActive == true
            _uiState.update {
                it.copy(entitlements = it.entitlements.copy(isBoostActive = active))
            }
            if (active && boost != null) watchBoostExpiry(boost.expiresAtMs)
        }
    }

    /** Clears the boost flag the moment its window ends while the app is open. */
    private fun watchBoostExpiry(expiresAtMs: Long) {
        boostExpiryJob?.cancel()
        boostExpiryJob = viewModelScope.launch {
            val remaining = expiresAtMs - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            if (_uiState.value.entitlements.isBoostActive) {
                _uiState.update {
                    it.copy(entitlements = it.entitlements.copy(isBoostActive = false))
                }
            }
        }
    }

    private fun scheduleSessionRefresh(session: SupabaseAuth.AuthSession) {
        sessionRefreshJob?.cancel()
        val msLeft = session.expiresAtMillis - System.currentTimeMillis()
        // Refresh ~90s before expiry; tokens already expiring retry after
        // 30s (gives a cold-start network time to come up first).
        val delayMs = if (msLeft <= 90_000L) 30_000L else msLeft - 90_000L
        sessionRefreshJob = viewModelScope.launch {
            delay(delayMs)
            val fresh = appContext?.let { ctx ->
                runCatching { SupabaseAuth.restoreSession(ctx) }.getOrNull()
            }
            if (fresh != null && _uiState.value.authGate == AuthGate.SIGNED_IN) {
                _uiState.update { it.copy(authSession = fresh) }
                scheduleSessionRefresh(fresh)
            }
        }
    }

    /** Field-level draft updates from the onboarding screens. */
    fun updateOnboardingDraft(update: (OnboardingDraft) -> OnboardingDraft) {
        _uiState.update { it.copy(onboardingDraft = update(it.onboardingDraft)) }
    }

    /** Stage navigation + local persistence of the draft. */
    fun goToOnboardingStage(context: Context, stage: Int) {
        val draft = _uiState.value.onboardingDraft.copy(step = stage.coerceIn(1, 4))
        _uiState.update { it.copy(onboardingDraft = draft, onboardingError = null) }
        persistDraft(context, draft)
    }

    /**
     * Picks up a photo from the system photo picker, runs on-device
     * face validation (ML Kit) and appends it to the draft (max 3).
     */
    fun addOnboardingPhoto(context: Context, uri: Uri) {
        val current = _uiState.value
        if (current.onboardingDraft.photos.size >= 3) {
            _uiState.update { it.copy(onboardingError = "You can add up to 3 photos.") }
            return
        }
        if (current.onboardingDraft.photos.any { it.uri == uri.toString() }) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingPhoto = true, onboardingError = null) }
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
            }.getOrNull()

            if (bytes == null || bytes.isEmpty()) {
                _uiState.update {
                    it.copy(isProcessingPhoto = false, onboardingError = "Couldn't read that photo — try another one.")
                }
                return@launch
            }
            if (bytes.size > 10 * 1024 * 1024) {
                _uiState.update {
                    it.copy(isProcessingPhoto = false, onboardingError = "That photo is too large (max 10 MB).")
                }
                return@launch
            }

            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val faceValidated = detectFace(context, uri)
            onboardingPhotoBytes[uri.toString()] = bytes
            _uiState.update {
                it.copy(
                    isProcessingPhoto = false,
                    onboardingDraft = it.onboardingDraft.copy(
                        photos = it.onboardingDraft.photos + OnboardingPhoto(
                            uri = uri.toString(),
                            mimeType = mime,
                            fileSizeBytes = bytes.size.toLong(),
                            faceValidated = faceValidated
                        )
                    )
                )
            }
        }
    }

    /** Removes a picked photo from the draft. */
    fun removeOnboardingPhoto(uriString: String) {
        onboardingPhotoBytes.remove(uriString)
        _uiState.update {
            it.copy(
                onboardingDraft = it.onboardingDraft.copy(
                    photos = it.onboardingDraft.photos.filterNot { p -> p.uri == uriString }
                )
            )
        }
    }

    /**
     * Captures the user's current GPS position for the onboarding
     * location/street-name field. Requires the location permission to be
     * granted (the UI launches the permission request before calling).
     * The resolved coordinates feed the distance-based discovery filter;
     * a reverse-geocoded "Street, City" label fills the field itself.
     */
    fun captureOnboardingLocation(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            val location = fetchCurrentLocation(context)
            if (location == null) {
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        onboardingError = "Couldn't get your location. Make sure location is " +
                                "on and the permission is granted, then tap the locator again."
                    )
                }
                return@launch
            }
            val label = reverseGeocode(context, location.latitude, location.longitude)
            _uiState.update {
                it.copy(
                    isLocating = false,
                    onboardingError = null,
                    onboardingDraft = it.onboardingDraft.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        city = label ?: it.onboardingDraft.city
                    )
                )
            }
        }
    }

    /**
     * One-shot GPS fix via Google Play services (FusedLocationProvider).
     * Returns null when the permission is missing or no fix is available.
     */
    private suspend fun fetchCurrentLocation(context: Context): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        return runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancellationToken = CancellationTokenSource().token
            suspendCancellableCoroutine<Location?> { cont ->
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken)
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { cont.resume(null) }
                    .addOnCanceledListener { cont.resume(null) }
            }
        }.getOrNull()
    }

    /**
     * Reverse-geocodes coordinates into a "Street, City" label for the
     * location field. Best-effort: returns null when the geocoder has no
     * answer so the existing text is kept.
     */
    @Suppress("DEPRECATION") // sync Geocoder is fine on older APIs and still works on 33+
    private suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val addresses = Geocoder(context, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull()
                when {
                    address == null -> null
                    else -> {
                        // Location PRD §5: prefer the locality itself; the
                        // thoroughfare keeps the label recognizable. The
                        // getAddressLine(0) fallback can carry a trailing
                        // postal code — toDisplayLocation() strips it.
                        val parts = listOfNotNull(
                            address.thoroughfare?.takeIf { it.isNotBlank() },
                            (address.locality ?: address.subAdminArea ?: address.adminArea)
                                ?.takeIf { it.isNotBlank() }
                        )
                        val combined = parts.joinToString(", ")
                        when {
                            combined.isNotBlank() -> combined
                            else -> address.getAddressLine(0)
                                ?.takeIf { it.isNotBlank() }
                                ?.toDisplayLocation()
                        }
                    }
                }
            }.getOrNull()
        }

    /**
     * On-device face validation with ML Kit. Returns false when no
     * clearly visible face is found or the detector is unavailable —
     * never throws, so a missing model never blocks onboarding.
     */
    private suspend fun detectFace(context: Context, uri: Uri): Boolean = runCatching {
        val image = InputImage.fromFilePath(context, uri)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
        try {
            suspendCancellableCoroutine { cont ->
                detector.process(image)
                    .addOnSuccessListener { faces -> cont.resume(faces.isNotEmpty()) }
                    .addOnFailureListener { cont.resume(false) }
            }
        } finally {
            detector.close()
        }
    }.getOrDefault(false)

    /**
     * Completes onboarding: uploads photos to Supabase Storage, saves
     * the profiles row (+ normalized user_interests) and enters the app.
     * Falls back to local photo uris when the upload is not possible so
     * the user is never blocked offline.
     */
    fun completeOnboarding(context: Context) {
        val state = _uiState.value
        val session = state.authSession
        if (session == null) {
            _uiState.update { it.copy(onboardingError = "Your session expired — please log in again.") }
            return
        }
        val draft = state.onboardingDraft
        if (!draft.isStage4Valid) {
            _uiState.update { it.copy(onboardingError = "Add at least one photo with a clearly visible face.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(onboardingUploading = true, onboardingError = null) }

            val remoteUrls = mutableListOf<String>()
            for (photo in draft.photos) {
                val bytes = onboardingPhotoBytes[photo.uri]
                    ?: runCatching {
                        context.contentResolver.openInputStream(Uri.parse(photo.uri))?.use { s -> s.readBytes() }
                    }.getOrNull()
                val url = if (bytes != null && SupabaseRepository.isConfigured()) {
                    SupabaseRepository.uploadProfilePhoto(
                        userId = session.userId,
                        bytes = bytes,
                        contentType = photo.mimeType.ifEmpty { "image/jpeg" },
                        accessToken = session.accessToken
                    )
                } else null
                remoteUrls.add(url ?: photo.uri)
            }

            val saved = SupabaseRepository.saveOnboardingProfile(
                session = session,
                draft = draft,
                photoUrls = remoteUrls,
                systemInterests = _uiState.value.interestCatalog
            )

            val profile = draft.toUserProfile(session.userId, remoteUrls)
            onboardingPhotoBytes.clear()
            clearPersistedDraft(context)
            _uiState.update {
                it.copy(
                    onboardingUploading = false,
                    isOnboardingComplete = true,
                    userProfile = profile,
                    toastMessage = if (saved) "Welcome to Quicky, ${profile.name}! 🎉"
                    else "Profile saved locally — will sync when you're back online."
                )
            }
            // v2: start the ranked server-side discovery deck right away.
            autoLoadDiscoveryDeck()
        }
    }

    // --- Draft persistence (resume an interrupted onboarding) ---

    private fun persistDraft(context: Context, draft: OnboardingDraft) {
        runCatching {
            val photos = JSONArray()
            draft.photos.forEach { photo ->
                photos.put(
                    JSONObject()
                        .put("uri", photo.uri)
                        .put("mimeType", photo.mimeType)
                        .put("fileSizeBytes", photo.fileSizeBytes)
                        .put("faceValidated", photo.faceValidated)
                )
            }
            context.getSharedPreferences(onboardingPrefs, Context.MODE_PRIVATE)
                .edit()
                .putInt("step", draft.step)
                .putString("fullName", draft.fullName)
                .putLong("dateOfBirthEpochDay", draft.dateOfBirthEpochDay ?: -1L)
                .putString("gender", draft.gender)
                .putString("customGender", draft.customGender)
                .putString("bio", draft.bio)
                .putString("interests", JSONArray(draft.interests).toString())
                .putString("interestedIn", draft.interestedIn)
                .putString("lookingFor", JSONArray(draft.lookingFor).toString())
                .putString("qualification", draft.qualification)
                .putString("occupation", draft.occupation)
                .putString("hobbies", JSONArray(draft.hobbies).toString())
                .putString("languages", JSONArray(draft.languages).toString())
                .putInt("heightCm", draft.heightCm ?: -1)
                .putFloat("weightKg", draft.weightKg ?: -1f)
                .putString("city", draft.city)
                .putString("photos", photos.toString())
                .apply()
        }
    }

    private fun readPersistedDraft(context: Context): OnboardingDraft? = runCatching {
        val prefs = context.getSharedPreferences(onboardingPrefs, Context.MODE_PRIVATE)
        if (!prefs.contains("step")) return null

        fun stringList(key: String): List<String> = runCatching {
            val arr = JSONArray(prefs.getString(key, "[]"))
            buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
        }.getOrDefault(emptyList())

        OnboardingDraft(
            step = prefs.getInt("step", 1),
            fullName = prefs.getString("fullName", "").orEmpty(),
            dateOfBirthEpochDay = prefs.getLong("dateOfBirthEpochDay", -1L).takeIf { it > 0 },
            gender = prefs.getString("gender", "").orEmpty(),
            customGender = prefs.getString("customGender", "").orEmpty(),
            bio = prefs.getString("bio", "").orEmpty(),
            interests = stringList("interests"),
            interestedIn = prefs.getString("interestedIn", "").orEmpty(),
            lookingFor = stringList("lookingFor"),
            qualification = prefs.getString("qualification", "").orEmpty(),
            occupation = prefs.getString("occupation", "").orEmpty(),
            hobbies = stringList("hobbies"),
            languages = stringList("languages").ifEmpty { listOf("English") },
            heightCm = prefs.getInt("heightCm", -1).takeIf { it > 0 },
            weightKg = prefs.getFloat("weightKg", -1f).takeIf { it > 0f },
            city = prefs.getString("city", "").orEmpty(),
            photos = runCatching {
                val arr = JSONArray(prefs.getString("photos", "[]"))
                buildList {
                    for (i in 0 until arr.length()) {
                        val p = arr.optJSONObject(i) ?: continue
                        add(
                            OnboardingPhoto(
                                uri = p.optString("uri"),
                                mimeType = p.optString("mimeType", "image/jpeg"),
                                fileSizeBytes = p.optLong("fileSizeBytes", 0L),
                                faceValidated = p.optBoolean("faceValidated", false)
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        )
    }.getOrNull()

    private fun clearPersistedDraft(context: Context) {
        runCatching {
            context.getSharedPreferences(onboardingPrefs, Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }

    fun openProfileDetail(profile: UserProfile) {
        _uiState.update { it.copy(selectedProfileDetail = profile) }
    }

    fun closeProfileDetail() {
        _uiState.update { it.copy(selectedProfileDetail = null) }
    }

    fun likeProfile(profile: UserProfile, isSuperLike: Boolean = false) {
        viewModelScope.launch {
            if (isSuperLike && _uiState.value.entitlements.superLikesRemaining <= 0 &&
                !PremiumGate.isPremium(_uiState.value.entitlements)) {
                showToast("No Super Likes remaining. Refill in Store!")
                openPremiumStore()
                return@launch
            }

            val updatedDeck = _uiState.value.discoveryDeck.filter { it.id != profile.id }

            val updatedEntitlements = if (isSuperLike) {
                _uiState.value.entitlements.copy(
                    superLikesRemaining = (_uiState.value.entitlements.superLikesRemaining - 1).coerceAtLeast(0)
                )
            } else _uiState.value.entitlements

            // Shared-interest matching: profiles that love the same things
            // surface their common interests on the match celebration and
            // compatibility highlights.
            val sharedInterests = sharedInterestsBetween(_uiState.value.userProfile, profile)
            val matchedProfile = if (sharedInterests.isEmpty()) profile else profile.copy(
                compatibilityHighlights = listOf(
                    "You both love ${sharedInterests.take(3).joinToString(" · ")}"
                )
            )

            val isMutualMatch = true
            val newMatchItem = MatchItem(
                id = "match_${profile.id}",
                user = matchedProfile,
                matchedAt = "Just now",
                lastMessage = if (isSuperLike) "Super Liked your profile! ✨" else "You both liked each other! Start chatting.",
                unreadCount = 0,
                hasActiveGame = false,
                isNewMatch = true
            )

            val updatedMatches = listOf(newMatchItem) + _uiState.value.matches.filter { it.user.id != profile.id }

            _uiState.update {
                it.copy(
                    discoveryDeck = updatedDeck,
                    entitlements = updatedEntitlements,
                    matches = updatedMatches,
                    matchCelebration = if (isMutualMatch) matchedProfile else null,
                    toastMessage = when {
                        isSuperLike -> "Super Liked ${profile.name}! 🌟"
                        sharedInterests.isNotEmpty() ->
                            "It's a match! You & ${profile.name} both love ${sharedInterests.take(2).joinToString(" & ")} ❤️"
                        else -> "Liked ${profile.name} ❤️"
                    },
                    // v2.1 §3.6.1 — native-ad cadence counter.
                    discoverySwipesSinceAd = it.discoverySwipesSinceAd + 1
                )
            }
            maybeTriggerDiscoveryAd()

            // v2 hardening: persist the swipe server-side — the record_swipe
            // RPC is rate-limited and creates the match row atomically when
            // the like is mutual (optimistic UI above stays as-is).
            _uiState.value.authSession?.let { session ->
                val result = SupabaseRepository.recordSwipe(
                    session = session,
                    targetUserId = profile.id,
                    action = if (isSuperLike) "SUPER_LIKE" else "LIKE"
                )
                if (result == "RATE_LIMITED_HOURLY" || result == "RATE_LIMITED_DAILY") {
                    _uiState.update {
                        it.copy(toastMessage = "Slow down — swipe limit reached. Take a breather! ⏳")
                    }
                }
            }
        }
    }

    /** Case-insensitive intersection of two profiles' interests. */
    private fun sharedInterestsBetween(a: UserProfile, b: UserProfile): List<String> {
        val bInterests = b.interests.map { it.trim().lowercase() }
        return a.interests.filter { it.trim().lowercase() in bInterests }
    }

    fun passProfile(profile: UserProfile) {
        val updatedDeck = _uiState.value.discoveryDeck.filter { it.id != profile.id }
        val updatedPassed = listOf(profile) + _uiState.value.passedHistory

        _uiState.update {
            it.copy(
                discoveryDeck = updatedDeck,
                passedHistory = updatedPassed,
                // v2.1 §3.6.1 — native-ad cadence counter.
                discoverySwipesSinceAd = it.discoverySwipesSinceAd + 1
            )
        }
        maybeTriggerDiscoveryAd()

        // v2 hardening: passes are persisted server-side too (feeds the
        // already-swiped exclusion in get_discovery_profiles).
        _uiState.value.authSession?.let { session ->
            viewModelScope.launch {
                SupabaseRepository.recordSwipe(
                    session = session,
                    targetUserId = profile.id,
                    action = "PASS"
                )
            }
        }
    }

    /**
     * v2.3 §16–§19: injects the Sponsored card after a RANDOM 1–6 swipe
     * threshold — freshly generated after every ad, never a fixed cadence.
     * Quicky Gold accounts are ad-free (real entitlement, not the QA
     * unlock); only COMPLETED profile swipes feed the counter; the counter
     * resets between ads so two ads can never appear back-to-back (the
     * minimum gap of one full swipe always leaves a real profile between
     * them, PRD §24).
     */
    private fun maybeTriggerDiscoveryAd() {
        val state = _uiState.value
        if (state.showDiscoveryAdCard) return
        if (!PremiumGate.isAdsEnabled(state.entitlements)) return
        if (state.discoverySwipesSinceAd >= state.discoveryAdThreshold &&
            state.discoveryDeck.isNotEmpty()
        ) {
            _uiState.update {
                it.copy(
                    showDiscoveryAdCard = true,
                    discoverySwipesSinceAd = 0,
                    // Fresh independent threshold for the NEXT ad (§17).
                    discoveryAdThreshold = (1..6).random()
                )
            }
        }
    }

    /** Swipe-left dismissal of the Sponsored card (no profile consumed). */
    fun dismissDiscoveryAdCard() {
        _uiState.update { it.copy(showDiscoveryAdCard = false) }
    }

    /**
     * Refreshes the discovery deck. When Supabase is configured and the
     * v2 RPC is deployed, candidates come from `get_discovery_profiles` —
     * filtered AND ranked server-side (age, distance, gender, intent,
     * verification, occupation, shared interests, already-swiped
     * exclusion, compatibility score).
     */
    fun resetDiscoveryDeck() {
        val state = _uiState.value
        val session = state.authSession
        if (!SupabaseRepository.isConfigured() || session == null) {
            showToast("Connect your Supabase credentials in SupabaseConfig.kt to load real profiles.")
            return
        }
        viewModelScope.launch {
            val candidates = fetchRankedDiscoveryCandidates(session)
            when {
                candidates == null -> showToast(
                    "Discovery service not reachable — run the updated supabase/schema.sql (v2 section)."
                )
                candidates.isEmpty() ->
                    showToast("No more profiles to discover right now. Check back soon!")
                else -> _uiState.update {
                    it.copy(
                        discoveryDeck = candidates,
                        showDiscoveryAdCard = false,
                        discoverySwipesSinceAd = 0,
                        // Fresh random cadence for the new session (§17).
                        discoveryAdThreshold = (1..6).random()
                    )
                }
            }
        }
    }

    /**
     * Fetches ranked candidates for the CURRENT discovery preferences via
     * the server-side RPC. Returns null when the RPC is unavailable so
     * callers can keep the existing deck untouched.
     */
    private suspend fun fetchRankedDiscoveryCandidates(
        session: SupabaseAuth.AuthSession
    ): List<UserProfile>? {
        val prefs = _uiState.value.discoveryPreferences
        // Blocked ids never re-enter the deck, even after the server-side
        // RPC ranking already ran (blocks are enforced client-side).
        val blockedIds = _uiState.value.blockedUsers.map { it.id }.toSet()
        return SupabaseRepository.fetchDiscoveryCandidates(
            session = session,
            minAge = prefs.minAge,
            maxAge = prefs.maxAge,
            maxDistanceKm = prefs.distanceKm,
            gender = when (prefs.whoDoYouWantToSee) {
                "Men" -> "Male"
                "Women" -> "Female"
                else -> null
            },
            intent = prefs.relationshipIntent.takeUnless {
                it.isBlank() || it.equals("Any", ignoreCase = true)
            },
            verifiedOnly = prefs.verifiedOnly,
            occupation = prefs.occupation.takeIf { it.isNotBlank() },
            sharedInterests = prefs.interests,
            languages = prefs.languages
        )?.filter { it.id !in blockedIds }
    }

    /**
     * Auto-fills an empty deck from the server after sign-in or
     * onboarding completion (no toast — silent best-effort).
     */
    private fun autoLoadDiscoveryDeck() {
        val state = _uiState.value
        val session = state.authSession ?: return
        if (!SupabaseRepository.isConfigured() || !state.isOnboardingComplete) return
        if (state.discoveryDeck.isNotEmpty()) return
        viewModelScope.launch {
            fetchRankedDiscoveryCandidates(session)?.let { candidates ->
                if (candidates.isNotEmpty()) {
                    _uiState.update { it.copy(discoveryDeck = candidates) }
                }
            }
        }
    }

    fun rewindLastPass() {
        val history = _uiState.value.passedHistory
        if (history.isEmpty()) {
            showToast("No previous passes to rewind.")
            return
        }

        if (_uiState.value.entitlements.rewindsRemaining <= 0 &&
            !PremiumGate.isPremium(_uiState.value.entitlements)) {
            showToast("Rewinds depleted. Upgrade to Quicky Gold!")
            openPremiumStore()
            return
        }

        val restoredProfile = history.first()
        val remainingHistory = history.drop(1)
        val updatedDeck = listOf(restoredProfile) + _uiState.value.discoveryDeck
        val updatedEntitlements = _uiState.value.entitlements.copy(
            rewindsRemaining = (_uiState.value.entitlements.rewindsRemaining - 1).coerceAtLeast(0)
        )

        _uiState.update {
            it.copy(
                discoveryDeck = updatedDeck,
                passedHistory = remainingHistory,
                entitlements = updatedEntitlements,
                toastMessage = "Rewound: ${restoredProfile.name} is back! ↩️"
            )
        }
    }

    /**
     * v2.3 §9–§15: Boost state belongs to the AUTHENTICATED user's Supabase
     * row (activated through the SECURITY DEFINER RPC) — never to the
     * device. Logging out and back in re-derives it from the server, so
     * Account B can never inherit Account A's boost (PRD Test A).
     */
    fun activateBoost() {
        if (!PremiumGate.isPremium(_uiState.value.entitlements) &&
            _uiState.value.entitlements.boostsRemaining <= 0) {
            showToast("Profile Boost is a Quicky Premium feature.")
            openPremiumStore()
            return
        }

        val updatedEntitlements = _uiState.value.entitlements.copy(
            boostsRemaining = (_uiState.value.entitlements.boostsRemaining - 1).coerceAtLeast(0),
            isBoostActive = true
        )

        _uiState.update {
            it.copy(
                entitlements = updatedEntitlements,
                toastMessage = "🚀 Profile Boost active! 10x discovery exposure for the next 30 minutes!"
            )
        }

        // Persist to THIS account's row in Supabase (authoritative source of
        // truth). Offline / un-configured installs keep the local mirror
        // above until the next sign-in reload — never another account's data.
        _uiState.value.authSession?.let { session ->
            viewModelScope.launch {
                val boost = SupabaseRepository.activateBoostRpc(session)
                if (boost != null) watchBoostExpiry(boost.expiresAtMs)
            }
        }
    }

    fun dismissMatchCelebration() {
        _uiState.update { it.copy(matchCelebration = null) }
    }

    fun startChatFromCelebration(profile: UserProfile, launchGame: Boolean = false) {
        val match = _uiState.value.matches.find { it.user.id == profile.id }
            ?: MatchItem(
                id = "match_${profile.id}",
                user = profile,
                matchedAt = "Just now",
                lastMessage = "Connected!",
                isNewMatch = false
            )

        _uiState.update {
            it.copy(
                matchCelebration = null,
                selectedProfileDetail = null,
                currentTab = SparkTab.CHATS,
                selectedMatchForChat = match
            )
        }

        if (launchGame) {
            val randomPrompt = _uiState.value.truthOrDarePrompts.random()
            sendTruthOrDareInChat(match.id, randomPrompt, profile.id)
        }
    }

    fun openChat(match: MatchItem) {
        val updatedMatches = _uiState.value.matches.map {
            if (it.id == match.id) it.copy(unreadCount = 0, isNewMatch = false) else it
        }

        _uiState.update {
            it.copy(
                selectedMatchForChat = match,
                matches = updatedMatches,
                currentTab = SparkTab.CHATS
            )
        }

        // v3.1: clear the unread badge server-side too — the chat history
        // (and its unread counts) is loaded from Supabase on every sign-in,
        // so a local-only reset would resurface after the next restart.
        if (SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                SupabaseRepository.markConversationRead(
                    conversationId = match.id,
                    accessToken = _uiState.value.authSession?.accessToken
                )
            }
        }
    }

    fun closeChat() {
        _uiState.update { it.copy(selectedMatchForChat = null, replyToMessage = null) }
    }

    fun setReplyToMessage(message: ChatMessage?) {
        _uiState.update { it.copy(replyToMessage = message) }
    }

    fun sendMessage(
        conversationId: String,
        text: String,
        gameCard: GameCardData? = null,
        replyToText: String? = null,
        replyToSender: String? = null
    ) {
        if (text.isBlank() && gameCard == null) return

        val replyingTo = _uiState.value.replyToMessage
        val resolvedReplyText = replyToText ?: replyingTo?.text?.take(60)
        val resolvedReplySender = replyToSender ?: if (replyingTo != null) (if (replyingTo.isMine) "You" else "Partner") else null

        val newMessage = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            conversationId = conversationId,
            senderId = "user_me",
            text = text,
            timestamp = "Just now",
            isMine = true,
            isRead = false,
            replyToText = resolvedReplyText,
            replyToSender = resolvedReplySender,
            gameCard = gameCard
        )

        val currentList = _uiState.value.messages[conversationId] ?: emptyList()
        val updatedMap = _uiState.value.messages.toMutableMap().apply {
            put(conversationId, currentList + newMessage)
        }

        val updatedMatches = _uiState.value.matches.map {
            if (it.id == conversationId) {
                it.copy(
                    lastMessage = if (gameCard != null) "Sent a ${gameCard.gameType} challenge 🎲" else text,
                    hasActiveGame = gameCard != null || it.hasActiveGame
                )
            } else it
        }

        _uiState.update {
            it.copy(
                messages = updatedMap,
                matches = updatedMatches,
                replyToMessage = null
            )
        }

        // Persist to Supabase when configured (fire-and-forget)
        if (SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                SupabaseRepository.insertChatMessage(
                    conversationId = conversationId,
                    senderId = newMessage.senderId,
                    text = text,
                    accessToken = _uiState.value.authSession?.accessToken
                )
            }
        }
    }

    fun sendVoiceMessage(
        conversationId: String,
        durationSeconds: Int = 5,
        replyToText: String? = null,
        replyToSender: String? = null
    ) {
        // v2.1 §3.7 — voice notes are premium-gated through PremiumGate.
        if (!PremiumGate.isPremium(_uiState.value.entitlements)) {
            showToast("Voice messages are a Quicky Gold feature. Upgrade to unlock!")
            openPremiumStore()
            return
        }
        val replyingTo = _uiState.value.replyToMessage
        val resolvedReplyText = replyToText ?: replyingTo?.text?.take(60)
        val resolvedReplySender = replyToSender ?: if (replyingTo != null) (if (replyingTo.isMine) "You" else "Partner") else null

        val newMessage = ChatMessage(
            id = "msg_voice_${System.currentTimeMillis()}",
            conversationId = conversationId,
            senderId = "user_me",
            text = "Voice message (0:0${durationSeconds})",
            timestamp = "Just now",
            isMine = true,
            isRead = false,
            replyToText = resolvedReplyText,
            replyToSender = resolvedReplySender,
            isVoiceMessage = true,
            voiceDurationSeconds = durationSeconds
        )

        val currentList = _uiState.value.messages[conversationId] ?: emptyList()
        val updatedMap = _uiState.value.messages.toMutableMap().apply {
            put(conversationId, currentList + newMessage)
        }

        val updatedMatches = _uiState.value.matches.map {
            if (it.id == conversationId) {
                it.copy(
                    lastMessage = "🎙️ Voice message (0:0${durationSeconds})"
                )
            } else it
        }

        _uiState.update {
            it.copy(
                messages = updatedMap,
                matches = updatedMatches,
                replyToMessage = null
            )
        }
    }

    fun addReaction(conversationId: String, messageId: String, emoji: String) {
        val currentList = _uiState.value.messages[conversationId] ?: return
        val updatedList = currentList.map { msg ->
            if (msg.id == messageId) {
                val updatedReactions = if (msg.reactions.contains(emoji)) {
                    msg.reactions - emoji
                } else {
                    msg.reactions + emoji
                }
                msg.copy(reactions = updatedReactions)
            } else msg
        }

        val updatedMap = _uiState.value.messages.toMutableMap().apply {
            put(conversationId, updatedList)
        }

        _uiState.update { it.copy(messages = updatedMap) }
    }

    fun sendTruthOrDareInChat(conversationId: String, prompt: TruthOrDarePrompt, targetPlayerId: String) {
        val gameCard = GameCardData(
            sessionId = "session_${System.currentTimeMillis()}",
            gameType = "Truth or Dare",
            category = prompt.category,
            promptType = prompt.type,
            promptText = prompt.text,
            targetPlayerId = targetPlayerId,
            answerText = null,
            isCompleted = false,
            isPremiumOnly = false
        )

        sendMessage(
            conversationId = conversationId,
            text = "I challenge you to a round of Truth or Dare! 🎭",
            gameCard = gameCard
        )
    }

    fun answerTruthOrDare(
        conversationId: String,
        messageId: String,
        answer: String,
        responseType: String = "TEXT",
        photoResId: Int? = null,
        voiceDurationSeconds: Int? = null
    ) {
        val currentList = _uiState.value.messages[conversationId] ?: return
        val updatedList = currentList.map { msg ->
            if (msg.id == messageId && msg.gameCard != null) {
                msg.copy(
                    gameCard = msg.gameCard.copy(
                        answerText = answer,
                        isCompleted = true,
                        responseType = responseType,
                        cameraPhotoResId = photoResId,
                        voiceDurationSeconds = voiceDurationSeconds
                    )
                )
            } else msg
        }

        val updatedMap = _uiState.value.messages.toMutableMap().apply {
            put(conversationId, updatedList)
        }

        _uiState.update {
            it.copy(
                messages = updatedMap,
                toastMessage = "Truth or Dare answer submitted! 🎯"
            )
        }
    }

    fun initiateVoiceCall(match: MatchItem) {
        _uiState.update { it.copy(activeVoiceCallMatch = match) }
    }

    fun endVoiceCall() {
        _uiState.update {
            it.copy(
                activeVoiceCallMatch = null,
                toastMessage = "Voice call ended."
            )
        }
    }

    fun toggleClubVoiceChat(active: Boolean) {
        _uiState.update { it.copy(isClubVoiceChatActive = active) }
    }

    fun startVerificationChallenge() {
        _uiState.update {
            it.copy(
                showVerificationDialog = true,
                isVerifyingFace = false,
                faceVerificationOutcome = null
            )
        }
    }

    /**
     * Get Verified (PRD §34–§39) — the user completed the live camera task;
     * match the captured selfie against every uploaded profile photo and
     * grant the badge only when at least one photo hits the 60% threshold.
     * The selfie file is a temporary FileProvider capture — it is deleted
     * once the match finishes so nothing extra is ever stored.
     */
    fun submitVerificationSelfie(context: Context, selfieUri: Uri) {
        val references = _uiState.value.userProfile.photoUris
        _uiState.update { it.copy(isVerifyingFace = true, faceVerificationOutcome = null) }
        viewModelScope.launch {
            val result = FaceVerifier.verifyLiveSelfie(context, selfieUri, references)
            if (result.verified) {
                completeVerificationChallenge()
                _uiState.update {
                    it.copy(
                        isVerifyingFace = false,
                        faceVerificationOutcome = result
                    )
                }
                // Keep the row in sync so the badge survives a re-login.
                persistProfileFields(JSONObject().put("is_verified", true))
            } else {
                _uiState.update {
                    it.copy(isVerifyingFace = false, faceVerificationOutcome = result)
                }
            }
            // The capture was a temp cache file — best-effort cleanup.
            runCatching { context.contentResolver.delete(selfieUri, null, null) }
        }
    }

    fun dismissVerificationDialog() {
        _uiState.update {
            it.copy(
                showVerificationDialog = false,
                isVerifyingFace = false,
                faceVerificationOutcome = null
            )
        }
    }

    fun completeVerificationChallenge() {
        val updatedUser = _uiState.value.userProfile.copy(
            isVerified = true,
            profileCompletionScore = (_uiState.value.userProfile.profileCompletionScore + 10).coerceAtMost(100)
        )
        _uiState.update {
            it.copy(
                userProfile = updatedUser,
                showVerificationDialog = false,
                toastMessage = "🎉 Verified! You now have the Quicky Verified badge and a 3× discovery exposure multiplier!"
            )
        }
    }

    fun setPrimaryPhoto(photoIndex: Int) {
        val profile = _uiState.value.userProfile

        // Uploaded (remote/local uri) photos — the post-onboarding case.
        if (profile.photoUris.isNotEmpty()) {
            val uris = profile.photoUris.toMutableList()
            if (photoIndex in uris.indices && photoIndex != 0) {
                val selected = uris.removeAt(photoIndex)
                uris.add(0, selected)
                val ordered = uris.take(3)
                _uiState.update {
                    it.copy(
                        userProfile = it.userProfile.copy(photoUris = ordered),
                        toastMessage = "Main profile photo updated! ★"
                    )
                }
                persistProfileFields(JSONObject().put("photo_urls", JSONArray(ordered)))
            }
            return
        }

        val photos = profile.photoResIds.toMutableList()
        if (photoIndex in photos.indices) {
            val selected = photos.removeAt(photoIndex)
            photos.add(0, selected)
            val updatedUser = _uiState.value.userProfile.copy(photoResIds = photos.take(3))
            _uiState.update {
                it.copy(
                    userProfile = updatedUser,
                    toastMessage = "Main profile photo updated! ★"
                )
            }
        }
    }

    fun deletePhoto(photoIndex: Int) {
        val profile = _uiState.value.userProfile

        // Uploaded (remote/local uri) photos — the post-onboarding case.
        if (profile.photoUris.isNotEmpty()) {
            if (profile.photoUris.size <= 1) {
                showToast("At least one profile photo is required.")
                return
            }
            if (photoIndex in profile.photoUris.indices) {
                val uris = profile.photoUris.toMutableList()
                uris.removeAt(photoIndex)
                _uiState.update {
                    it.copy(
                        userProfile = it.userProfile.copy(photoUris = uris),
                        toastMessage = "Photo removed."
                    )
                }
                persistProfileFields(JSONObject().put("photo_urls", JSONArray(uris)))
            }
            return
        }

        val photos = profile.photoResIds.toMutableList()
        if (photos.size > 1 && photoIndex in photos.indices) {
            photos.removeAt(photoIndex)
            val updatedUser = _uiState.value.userProfile.copy(photoResIds = photos)
            _uiState.update {
                it.copy(
                    userProfile = updatedUser,
                    toastMessage = "Photo removed."
                )
            }
        } else {
            showToast("At least one profile photo is required.")
        }
    }

    /**
     * Adds a real profile photo from the system picker: reads the bytes,
     * runs the on-device face check, uploads to Supabase Storage and
     * appends the public URL to the user's `profiles.photo_urls` (max 3).
     */
    fun addProfilePhoto(context: Context, uri: Uri) {
        val current = _uiState.value
        if (current.userProfile.photoUris.size >= 3) {
            showToast("You can add up to 3 photos.")
            return
        }
        if (current.userProfile.photoUris.any { it == uri.toString() }) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingPhoto = true) }
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
            }.getOrNull()

            if (bytes == null || bytes.isEmpty()) {
                _uiState.update {
                    it.copy(isProcessingPhoto = false, toastMessage = "Couldn't read that photo — try another one.")
                }
                return@launch
            }
            if (bytes.size > 10 * 1024 * 1024) {
                _uiState.update {
                    it.copy(isProcessingPhoto = false, toastMessage = "That photo is too large (max 10 MB).")
                }
                return@launch
            }

            val faceValidated = detectFace(context, uri)

            val session = _uiState.value.authSession
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val remoteUrl = if (session != null && SupabaseRepository.isConfigured()) {
                SupabaseRepository.uploadProfilePhoto(
                    userId = session.userId,
                    bytes = bytes,
                    contentType = mime.ifEmpty { "image/jpeg" },
                    accessToken = session.accessToken
                )
            } else null

            val updatedUris = _uiState.value.userProfile.photoUris + (remoteUrl ?: uri.toString())
            if (remoteUrl != null && session != null) {
                SupabaseRepository.updateProfileFields(
                    userId = session.userId,
                    fields = JSONObject().put("photo_urls", JSONArray(updatedUris.take(3))),
                    accessToken = session.accessToken
                )
            }

            _uiState.update {
                it.copy(
                    isProcessingPhoto = false,
                    userProfile = it.userProfile.copy(
                        photoUris = updatedUris.take(3),
                        profileCompletionScore = (it.userProfile.profileCompletionScore + 5).coerceAtMost(100)
                    ),
                    toastMessage = when {
                        remoteUrl == null ->
                            "Photo added — will upload when you're back online. (${updatedUris.size}/3)"
                        !faceValidated ->
                            "Photo uploaded, but no clear face was found — consider a clearer one. (${updatedUris.size}/3)"
                        else -> "Photo uploaded! (${updatedUris.size}/3)"
                    }
                )
            }
        }
    }

    /**
     * Fire-and-forget PATCH of the signed-in user's `profiles` columns so
     * profile edits (photos, occupation, bio…) survive a re-login.
     */
    private fun persistProfileFields(fields: JSONObject) {
        val session = _uiState.value.authSession ?: return
        if (!SupabaseRepository.isConfigured()) return
        viewModelScope.launch {
            SupabaseRepository.updateProfileFields(
                userId = session.userId,
                fields = fields,
                accessToken = session.accessToken
            )
        }
    }

    fun updateFieldVisibility(field: String, visibility: VisibilityLevel) {
        val current = _uiState.value.userProfile.fieldVisibility.toMutableMap()
        current[field] = visibility
        val updatedUser = _uiState.value.userProfile.copy(fieldVisibility = current)
        _uiState.update {
            it.copy(
                userProfile = updatedUser,
                toastMessage = "Updated visibility for $field to ${visibility.name.lowercase().replace('_', ' ')}"
            )
        }
    }

    fun toggleCharacterBadge(show: Boolean) {
        val updatedUser = _uiState.value.userProfile.copy(showCharacterBadge = show)
        val updatedPrivacy = _uiState.value.privacySettings.copy(showCharacterBadge = show)
        _uiState.update {
            it.copy(
                userProfile = updatedUser,
                privacySettings = updatedPrivacy,
                toastMessage = if (show) "Character badge visible on your profile and discovery." else "Character badge hidden from public view."
            )
        }
    }

    fun updateProfile(name: String, bio: String, relationshipIntent: String, occupation: String, height: String = "172 cm") {
        val updated = _uiState.value.userProfile.copy(
            name = name,
            bio = bio,
            relationshipIntent = relationshipIntent,
            occupation = occupation,
            height = height,
            profileCompletionScore = 90
        )
        _uiState.update {
            it.copy(
                userProfile = updated,
                toastMessage = "Profile updated successfully ✨"
            )
        }
        // Keep the database row in sync so edits survive a re-login.
        persistProfileFields(
            JSONObject()
                .put("name", name.trim())
                .put("bio", bio.trim())
                .put("relationship_intent", relationshipIntent)
                .put("occupation", occupation.trim())
                .put("height", height)
        )
    }

    fun updatePersonalInformation(
        height: String,
        occupation: String,
        education: String,
        intent: String,
        interests: List<String>,
        fieldVisibility: Map<String, VisibilityLevel>
    ) {
        val previous = _uiState.value.userProfile
        val updated = previous.copy(
            height = height,
            occupation = occupation,
            educationLevel = education,
            education = education,
            relationshipIntent = intent,
            interests = interests,
            fieldVisibility = fieldVisibility,
            // Adding interests strengthens the profile (and matching) —
            // nudge the completion score upwards.
            profileCompletionScore = if (interests.isNotEmpty()) {
                previous.profileCompletionScore.coerceAtLeast(60)
            } else {
                previous.profileCompletionScore
            }
        )
        _uiState.update {
            it.copy(
                userProfile = updated,
                showPersonalInformationSheet = false,
                toastMessage = "Personal details & visibility updated ✨"
            )
        }
        // Persist the editable fields to Supabase (fire-and-forget).
        persistProfileFields(
            JSONObject()
                .put("height", height)
                .put("occupation", occupation.trim())
                .put("education", education)
                .put("education_level", education)
                .put("relationship_intent", intent)
        )
        if (interests != previous.interests) {
            syncInterestsToDatabase(interests)
        }
    }

    fun updatePrivacySettings(settings: PrivacySettings) {
        _uiState.update {
            it.copy(
                privacySettings = settings,
                userProfile = it.userProfile.copy(showCharacterBadge = settings.showCharacterBadge),
                toastMessage = "Privacy preferences saved 🔒"
            )
        }
    }

    // -------------------------------------------------------------
    // EDIT LOCATION (change request #5)
    // -------------------------------------------------------------

    fun toggleEditLocationSheet(show: Boolean) {
        _uiState.update {
            it.copy(
                showEditLocationSheet = show,
                locationSearchResults = if (show) it.locationSearchResults else emptyList(),
                editLocationError = if (show) it.editLocationError else null
            )
        }
    }

    /** Nominatim city/area search for the Edit Location sheet. */
    fun searchCityLocation(query: String) {
        if (query.trim().length < 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingLocation = true, editLocationError = null) }
            val results = SupabaseRepository.searchCity(query)
            _uiState.update {
                it.copy(
                    isSearchingLocation = false,
                    locationSearchResults = results,
                    editLocationError = if (results.isEmpty())
                        "No places found for \"${query.trim()}\" — try a nearby city name."
                    else null
                )
            }
        }
    }

    /**
     * GPS path of the Edit Location sheet: grabs the current position,
     * reverse-geocodes it into a street/city label and saves it straight
     * to the profile + database.
     */
    fun captureCurrentLocation(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, editLocationError = null) }
            val location = fetchCurrentLocation(context)
            if (location == null) {
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        editLocationError = "Couldn't get your location. Make sure location " +
                                "is on and the permission is granted, then try again."
                    )
                }
                return@launch
            }
            val label = reverseGeocode(context, location.latitude, location.longitude)
            saveLocationInternal(
                latitude = location.latitude,
                longitude = location.longitude,
                city = label ?: _uiState.value.userProfile.city
            )
        }
    }

    /** Search-selection path of the Edit Location sheet. */
    fun saveLocation(latitude: Double, longitude: Double, city: String) {
        saveLocationInternal(latitude, longitude, city)
    }

    private fun saveLocationInternal(latitude: Double, longitude: Double, city: String) {
        _uiState.update {
            it.copy(
                isLocating = false,
                showEditLocationSheet = false,
                userProfile = it.userProfile.copy(
                    latitude = latitude,
                    longitude = longitude,
                    city = city
                ),
                toastMessage = "Location updated — distance matching uses it right away 📍"
            )
        }
        // Keep the database row in sync so the server-side distance
        // filter follows immediately.
        persistProfileFields(
            JSONObject()
                .put("latitude", latitude)
                .put("longitude", longitude)
                .put("city", city.trim())
        )
    }

    fun purchaseSubscription(isYearly: Boolean) {
        val updated = _uiState.value.entitlements.copy(
            isPremium = true,
            unlimitedLikes = true,
            seeWhoLikedYou = true,
            advancedFilters = true,
            hasVoiceChat = true,
            boostsRemaining = _uiState.value.entitlements.boostsRemaining + if (isYearly) 10 else 3,
            superLikesRemaining = _uiState.value.entitlements.superLikesRemaining + if (isYearly) 25 else 10
        )
        _uiState.update {
            it.copy(
                entitlements = updated,
                showPremiumStore = false,
                // Quicky Gold is ad-free from this moment on (§16/§27): drop
                // any Sponsored card that is currently on screen.
                showDiscoveryAdCard = false,
                toastMessage = "🎉 Welcome to Quicky Premium! All filters, voice chat, and dating games unlocked."
            )
        }
    }

    fun purchaseConsumable(itemType: String) {
        val current = _uiState.value.entitlements
        val updated = when (itemType) {
            "boost_5" -> current.copy(boostsRemaining = current.boostsRemaining + 5)
            "super_like_10" -> current.copy(superLikesRemaining = current.superLikesRemaining + 10)
            "rewind_unlimited" -> current.copy(rewindsRemaining = 999)
            else -> current
        }
        _uiState.update {
            it.copy(
                entitlements = updated,
                showPremiumStore = false,
                toastMessage = "Purchase successful via Google Play! Inventory updated."
            )
        }
    }

    /**
     * Blocks a profile (PRD §12 Safety): records a display snapshot for
     * Settings > Privacy > Blocked Users, removes the profile from every
     * live surface (deck, matches, open chat) and persists the block so it
     * survives restarts and excludes the id from future fetches.
     */
    fun blockUser(userId: String) {
        val state = _uiState.value
        // Snapshot for the Blocked Users list: search every surface the
        // profile could currently be on (deck card, match row, open chat,
        // open profile sheet). Without this the list would have nothing
        // left to show — the removals below delete all live references.
        val profile = state.discoveryDeck.firstOrNull { it.id == userId }
            ?: state.matches.firstOrNull { it.user.id == userId }?.user
            ?: state.selectedMatchForChat?.user?.takeIf { it.id == userId }
            ?: state.selectedProfileDetail?.takeIf { it.id == userId }

        val existing = state.blockedUsers.firstOrNull { it.id == userId }
        val recorded = existing ?: profile?.let {
            BlockedUser(
                id = it.id,
                name = it.name,
                age = it.age,
                city = it.city,
                photoUri = it.photoUris.firstOrNull(),
                photoResId = it.photoResIds.firstOrNull(),
                blockedAtEpochMs = System.currentTimeMillis()
            )
        }

        val updatedDeck = state.discoveryDeck.filter { it.id != userId }
        val updatedMatches = state.matches.filter { it.user.id != userId }

        _uiState.update {
            it.copy(
                discoveryDeck = updatedDeck,
                matches = updatedMatches,
                selectedMatchForChat = if (it.selectedMatchForChat?.user?.id == userId) null else it.selectedMatchForChat,
                selectedProfileDetail = null,
                blockedUsers = if (recorded != null) {
                    listOf(recorded) + it.blockedUsers.filter { b -> b.id != recorded.id }
                } else it.blockedUsers,
                toastMessage = "User has been blocked. They cannot discover you, view your profile, or message you."
            )
        }
        Analytics.log(
            Analytics.SAFETY_USER_BLOCKED,
            "blocked_id" to userId,
            "blocked_list_size" to _uiState.value.blockedUsers.size
        )
        persistBlockedUsers()
    }

    /**
     * Removes a profile from the Blocked Users list (Settings > Privacy).
     * The profile becomes discoverable / matchable again on the next
     * deck or conversation fetch.
     */
    fun unblockUser(userId: String) {
        val target = _uiState.value.blockedUsers.firstOrNull { it.id == userId } ?: return
        _uiState.update {
            it.copy(
                blockedUsers = it.blockedUsers.filter { b -> b.id != userId },
                toastMessage = "${target.name} has been unblocked. You may see each other in Discovery again."
            )
        }
        Analytics.log(
            Analytics.SAFETY_USER_UNBLOCKED,
            "unblocked_id" to userId,
            "blocked_list_size" to _uiState.value.blockedUsers.size
        )
        persistBlockedUsers()
    }

    // -----------------------------------------------------------------
    // Blocked-user persistence (device-local SharedPreferences; no
    // server round-trip — blocking works fully offline).
    // -----------------------------------------------------------------

    private val blockedUsersPrefs = "quicky_blocked_users"

    /** Writes the current Blocked Users list to device storage. */
    private fun persistBlockedUsers() {
        val context = appContext ?: return
        val entries = JSONArray()
        _uiState.value.blockedUsers.forEach { b ->
            entries.put(
                JSONObject()
                    .put("id", b.id)
                    .put("name", b.name)
                    .put("age", b.age)
                    .put("city", b.city)
                    .put("photoUri", b.photoUri ?: "")
                    .put("photoResId", b.photoResId ?: -1)
                    .put("blockedAt", b.blockedAtEpochMs)
            )
        }
        runCatching {
            context.getSharedPreferences(blockedUsersPrefs, Context.MODE_PRIVATE)
                .edit()
                .putString("blocked_users", entries.toString())
                .apply()
        }
    }

    /** Restores the persisted Blocked Users list on cold start. */
    private fun loadPersistedBlockedUsers(context: Context) {
        val restored = runCatching {
            val raw = context.getSharedPreferences(blockedUsersPrefs, Context.MODE_PRIVATE)
                .getString("blocked_users", null) ?: return
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        BlockedUser(
                            id = o.optString("id"),
                            name = o.optString("name").ifBlank { "Blocked user" },
                            age = o.optInt("age", 0),
                            city = o.optString("city"),
                            photoUri = o.optString("photoUri").takeUnless { it.isBlank() },
                            photoResId = o.optInt("photoResId", -1).takeIf { it > 0 },
                            blockedAtEpochMs = o.optLong("blockedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        if (restored.isNotEmpty()) {
            _uiState.update { it.copy(blockedUsers = restored) }
        }
    }

    fun reportUser(userId: String, reason: String) {
        _uiState.update {
            it.copy(
                toastMessage = "Report received for investigation ($reason). Quicky maintains zero tolerance for harassment."
            )
        }
    }

    fun unmatchUser(matchId: String) {
        val updatedMatches = _uiState.value.matches.filter { it.id != matchId }
        _uiState.update {
            it.copy(
                matches = updatedMatches,
                selectedMatchForChat = null,
                toastMessage = "Unmatched successfully."
            )
        }
    }

    fun updateDiscoveryPreferences(newPrefs: DiscoveryPreferences) {
        val state = _uiState.value
        // The shared-interest selection IS the user's own interest set —
        // keep the profile, the filter and the database in lock-step.
        val interestsChanged = newPrefs.interests != state.userProfile.interests

        val filteredDeck = applyDiscoveryFilters(state.discoveryDeck, newPrefs)
        val interestNote = if (newPrefs.interests.isEmpty()) ""
            else " with ${newPrefs.interests.size} shared interest${if (newPrefs.interests.size == 1) "" else "s"}"
        _uiState.update {
            it.copy(
                userProfile = if (interestsChanged) {
                    it.userProfile.copy(interests = newPrefs.interests)
                } else it.userProfile,
                discoveryPreferences = newPrefs,
                discoveryDeck = filteredDeck,
                showFilterSheet = false,
                toastMessage = "Discovery preferences saved$interestNote"
            )
        }
        if (interestsChanged) {
            syncInterestsToDatabase(newPrefs.interests)
        }
    }

    /** Writes the interest selection to profiles.interests + user_interests. */
    private fun syncInterestsToDatabase(interests: List<String>) {
        val session = _uiState.value.authSession ?: return
        if (!SupabaseRepository.isConfigured()) return
        viewModelScope.launch {
            SupabaseRepository.saveUserInterests(
                session = session,
                interests = interests,
                systemInterests = _uiState.value.interestCatalog
            )
        }
    }

    /**
     * Filters a set of candidate profiles by the user's discovery
     * preferences: gender ("Show Me"), age window, distance, occupation
     * keyword, verification and shared interests (a profile matches when
     * it shares at least one).
     */
    private fun applyDiscoveryFilters(
        deck: List<UserProfile>,
        prefs: DiscoveryPreferences
    ): List<UserProfile> {
        val occupationQuery = prefs.occupation.trim()
        val preferredLanguages = prefs.languages.map { it.trim().lowercase() }
        // "Show Me" gender filter — null means "Everyone" (no restriction).
        // Candidate genders are stored as "Male"/"Female" (same mapping the
        // server-side RPC uses), so a non-binary/blank gender only shows
        // under "Everyone", never under Men/Women.
        val wantedGender = when (prefs.whoDoYouWantToSee) {
            "Men" -> "Male"
            "Women" -> "Female"
            else -> null
        }
        return deck.filter { profile ->
            (wantedGender == null || profile.gender.equals(wantedGender, ignoreCase = true)) &&
                profile.age in prefs.minAge..prefs.maxAge &&
                profile.distanceKm <= prefs.distanceKm &&
                (occupationQuery.isBlank() ||
                        profile.occupation.contains(occupationQuery, ignoreCase = true)) &&
                (!prefs.verifiedOnly || profile.isVerified) &&
                (preferredLanguages.isEmpty() || profile.languages.any { candidate ->
                    candidate.trim().lowercase() in preferredLanguages
                }) &&
                (prefs.interests.isEmpty() || profile.interests.any { candidate ->
                    candidate.trim().lowercase() in prefs.interests.map { it.trim().lowercase() }
                })
        }
    }

    fun updateFilters(newFilter: DiscoveryFilter) {
        updateDiscoveryPreferences(newFilter)
    }

    fun toggleFilterSheet(show: Boolean) {
        _uiState.update { it.copy(showFilterSheet = show) }
    }

    fun openPremiumStore() {
        _uiState.update { it.copy(showPremiumStore = true) }
    }

    fun closePremiumStore() {
        _uiState.update { it.copy(showPremiumStore = false) }
    }

    fun toggleSafetyCenter(show: Boolean) {
        _uiState.update { it.copy(showSafetyCenter = show) }
    }

    fun togglePrivacyCenter(show: Boolean) {
        _uiState.update { it.copy(showPrivacyCenter = show) }
    }

    fun togglePersonalInformation(show: Boolean) {
        _uiState.update { it.copy(showPersonalInformationSheet = show) }
    }

    fun toggleSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    // -------------------------------------------------------------
    // DEDICATED SETTINGS SCREEN (v2.1 §3.9)
    // -------------------------------------------------------------
    fun toggleSettingsScreen(show: Boolean) {
        _uiState.update { it.copy(showSettingsScreen = show) }
    }

    /** Distance unit toggle (km / mi) — affects profile distance labels. */
    fun setDistanceUnit(unit: String) {
        _uiState.update { it.copy(distanceUnit = if (unit == "mi") "mi" else "km") }
    }

    fun updateNotificationPref(key: String, value: Boolean) {
        _uiState.update {
            it.copy(
                notificationPrefs = when (key) {
                    "matches" -> it.notificationPrefs.copy(matches = value)
                    "messages" -> it.notificationPrefs.copy(messages = value)
                    "clubs" -> it.notificationPrefs.copy(clubs = value)
                    "promotions" -> it.notificationPrefs.copy(promotions = value)
                    else -> it.notificationPrefs
                }
            )
        }
    }

    /** Show/pause the profile on the Discovery deck of others. */
    fun setShowMeOnDiscovery(show: Boolean) {
        _uiState.update { it.copy(showMeOnDiscovery = show) }
        // Mirror into the privacy settings so Incognito/visibility stays coherent.
        _uiState.update {
            it.copy(privacySettings = it.privacySettings.copy(isInvisible = !show))
        }
        showToast(if (show) "You are visible on Discovery again." else "You're hidden from Discovery decks.")
    }

    fun toggleNotificationsSheet(show: Boolean) {
        _uiState.update { it.copy(showNotificationsSheet = show) }
    }

    fun markNotificationsRead() {
        val updated = _uiState.value.notifications.map { it.copy(isRead = true) }
        _uiState.update { it.copy(notifications = updated) }
    }

    // -------------------------------------------------------------
    // v3.3.4 NOTIFICATION POLLING — mention & report pushes.
    //
    // The server drops notification rows (DB triggers) for every
    // "@mention" and every member report; this loop surfaces them as
    // REAL system notifications (channel + deep link) while the app is
    // alive, and keeps the in-app notification sheet in sync. The first
    // pass after sign-in only seeds the "already seen" set — history
    // never re-pops.
    // -------------------------------------------------------------
    private var notificationPollJob: Job? = null
    private val shownNotificationIds = mutableSetOf<String>()

    fun startNotificationPolling() {
        if (notificationPollJob?.isActive == true) return
        if (!SupabaseRepository.isConfigured()) return
        notificationPollJob = viewModelScope.launch {
            var firstPass = true
            while (isActive) {
                val s = _uiState.value
                val session = s.authSession ?: break
                val fresh = SupabaseRepository.fetchNotifications(
                    session.userId, accessToken = session.accessToken
                )
                if (fresh != null) {
                    if (firstPass) {
                        // Seed — existing rows are "known", not "pushed".
                        shownNotificationIds.addAll(fresh.map { it.id })
                        firstPass = false
                    } else {
                        fresh
                            .filter { it.id !in shownNotificationIds && !it.isRead }
                            .forEach { n ->
                                shownNotificationIds.add(n.id)
                                appContext?.let { ctx ->
                                    PushNotifications.show(
                                        context = ctx,
                                        type = if (n.clubId != null) PushNotifications.TYPE_CLUB
                                        else PushNotifications.TYPE_MESSAGE,
                                        title = n.title,
                                        body = n.message,
                                        chatId = null,
                                        clubId = n.clubId
                                    )
                                }
                            }
                    }
                    _uiState.update { st ->
                        st.copy(notifications = mergeNotifications(st.notifications, fresh))
                    }
                }
                delay(NOTIFICATION_POLL_MS)
            }
        }
    }

    fun stopNotificationPolling() {
        notificationPollJob?.cancel()
        notificationPollJob = null
    }

    /** Server rows first (newest first), local-only items kept below. */
    private fun mergeNotifications(
        local: List<NotificationItem>,
        remote: List<NotificationItem>
    ): List<NotificationItem> {
        val remoteIds = remote.map { it.id }.toHashSet()
        val localOnly = local.filter { it.id !in remoteIds }
        return remote + localOnly
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // -------------------------------------------------------------
    // CLUBS & SOCIAL COMMUNITY METHODS (PRD Section 11 - 18, 30)
    // -------------------------------------------------------------

    companion object {
        /** One page of club-chat history (chunk-by-chunk scroll loading). */
        private const val CLUB_CHAT_PAGE_SIZE = 30
        /** How often the OPEN club chat polls for fresh messages. */
        private const val CLUB_CHAT_POLL_MS = 7_000L
        /** Global poll for mention/report notifications. */
        private const val NOTIFICATION_POLL_MS = 20_000L
    }

    fun openClub(club: Club) {
        _uiState.update { it.copy(selectedClubForDetail = club) }
        // v3.3.4: members opening the chat get chunked history + a live
        // poll loop; non-members see the member list preview instead —
        // the server's RLS keeps club_messages readable for authenticated
        // users, but the product rule is "join first, read second".
        val me = _uiState.value.userProfile.id
        val isMember = club.members.any { it.userId == me }
        if (isMember && SupabaseRepository.isConfigured()) {
            loadClubChatInitial(club.id)
            startClubChatPolling(club.id)
        }
    }

    fun closeClubDetail() {
        // Freeze the poll loops — history + cursors stay cached for the
        // next open of the same club this session.
        _uiState.value.clubChatMeta.filterValues { it.isPolling }.keys.forEach {
            stopClubChatPolling(it)
        }
        _uiState.update { it.copy(selectedClubForDetail = null) }
    }

    /**
     * Loads the FIRST page of a club's history (newest 30) and replaces
     * the optimistic local list with the server truth (local-only
     * messages this session — e.g. sends that never reached the server —
     * are preserved and de-duplicated by content).
     */
    private fun loadClubChatInitial(clubId: String) {
        val state = _uiState.value
        if (state.clubChatMeta[clubId]?.isLoadingInitial == true) return
        if ((state.clubMessages[clubId]?.size ?: 0) > 0) {
            // Already loaded this session — just catch up on new ones.
            viewModelScope.launch { pollClubChatOnce(clubId) }
            return
        }
        _uiState.update { s ->
            val meta = s.clubChatMeta[clubId] ?: ClubChatMeta()
            s.copy(clubChatMeta = s.clubChatMeta + (clubId to meta.copy(isLoadingInitial = true)))
        }
        viewModelScope.launch {
            val s = _uiState.value
            val page = SupabaseRepository.fetchClubMessagesPage(
                clubId = clubId,
                currentUserId = s.userProfile.id,
                limit = CLUB_CHAT_PAGE_SIZE,
                accessToken = s.authSession?.accessToken
            )
            _uiState.update { s2 ->
                val meta = s2.clubChatMeta[clubId] ?: ClubChatMeta()
                if (page == null) {
                    s2.copy(clubChatMeta = s2.clubChatMeta + (clubId to meta.copy(isLoadingInitial = false)))
                } else {
                    val merged = mergeClubMessages(s2.clubMessages[clubId] ?: emptyList(), page)
                    val newMeta = meta.copy(
                        isLoadingInitial = false,
                        hasMoreMessages = page.size >= CLUB_CHAT_PAGE_SIZE,
                        oldestLoadedIso = merged.firstOrNull()?.createdAtIso,
                        newestLoadedIso = merged.lastOrNull()?.createdAtIso
                    )
                    s2.copy(
                        clubMessages = s2.clubMessages + (clubId to merged),
                        clubChatMeta = s2.clubChatMeta + (clubId to newMeta)
                    )
                }
            }
        }
    }

    /**
     * Scroll-to-top pagination: loads the NEXT OLDER chunk of messages
     * before the oldest one currently on screen (v3.3.4 — history is
     * NEVER fully downloaded, only 30-message pages on demand).
     */
    fun loadOlderClubMessages(clubId: String) {
        val state = _uiState.value
        val meta = state.clubChatMeta[clubId] ?: return
        if (meta.isLoadingOlder || !meta.hasMoreMessages) return
        val before = meta.oldestLoadedIso ?: return
        _uiState.update { s ->
            s.copy(clubChatMeta = s.clubChatMeta + (clubId to meta.copy(isLoadingOlder = true)))
        }
        viewModelScope.launch {
            val s = _uiState.value
            val older = SupabaseRepository.fetchClubMessagesPage(
                clubId = clubId,
                currentUserId = s.userProfile.id,
                limit = CLUB_CHAT_PAGE_SIZE,
                beforeIso = before,
                accessToken = s.authSession?.accessToken
            )
            _uiState.update { s2 ->
                val m = s2.clubChatMeta[clubId] ?: ClubChatMeta()
                if (older == null) {
                    s2.copy(clubChatMeta = s2.clubChatMeta + (clubId to m.copy(isLoadingOlder = false)))
                } else {
                    val existing = s2.clubMessages[clubId] ?: emptyList()
                    val existingIds = existing.map { it.id }.toHashSet()
                    val newOnes = older.filter { it.id !in existingIds }
                    val merged = sortClubMessages(newOnes + existing)
                    s2.copy(
                        clubMessages = s2.clubMessages + (clubId to merged),
                        clubChatMeta = s2.clubChatMeta + (clubId to m.copy(
                            isLoadingOlder = false,
                            hasMoreMessages = older.size >= CLUB_CHAT_PAGE_SIZE,
                            oldestLoadedIso = merged.firstOrNull()?.createdAtIso
                        ))
                    )
                }
            }
        }
    }

    /** Starts the 7s refresh loop while a club chat stays open. */
    private fun startClubChatPolling(clubId: String) {
        if (clubPollJobs[clubId]?.isActive == true) return
        clubPollJobs[clubId] = viewModelScope.launch {
            while (isActive && _uiState.value.selectedClubForDetail?.id == clubId) {
                delay(CLUB_CHAT_POLL_MS)
                pollClubChatOnce(clubId)
            }
            _uiState.update { s ->
                val meta = s.clubChatMeta[clubId] ?: return@update s
                s.copy(clubChatMeta = s.clubChatMeta + (clubId to meta.copy(isPolling = false)))
            }
        }
        _uiState.update { s ->
            val meta = s.clubChatMeta[clubId] ?: ClubChatMeta()
            s.copy(clubChatMeta = s.clubChatMeta + (clubId to meta.copy(isPolling = true)))
        }
    }

    private fun stopClubChatPolling(clubId: String) {
        clubPollJobs.remove(clubId)?.cancel()
    }

    /** One incremental poll: only rows NEWER than the newest loaded. */
    private suspend fun pollClubChatOnce(clubId: String) {
        val s = _uiState.value
        if (!SupabaseRepository.isConfigured()) return
        val fresh = SupabaseRepository.fetchClubMessagesPage(
            clubId = clubId,
            currentUserId = s.userProfile.id,
            limit = CLUB_CHAT_PAGE_SIZE,
            afterIso = s.clubChatMeta[clubId]?.newestLoadedIso,
            accessToken = s.authSession?.accessToken
        ) ?: return
        if (fresh.isEmpty()) return
        _uiState.update { s2 ->
            val existing = s2.clubMessages[clubId] ?: emptyList()
            val ids = existing.map { it.id }.toHashSet()
            val newOnes = fresh.filter { it.id !in ids }
            if (newOnes.isEmpty()) return@update s2
            val merged = sortClubMessages(existing + newOnes)
            val m = s2.clubChatMeta[clubId] ?: ClubChatMeta()
            s2.copy(
                clubMessages = s2.clubMessages + (clubId to merged),
                clubChatMeta = s2.clubChatMeta + (clubId to m.copy(
                    newestLoadedIso = merged.lastOrNull()?.createdAtIso ?: m.newestLoadedIso,
                    oldestLoadedIso = merged.firstOrNull()?.createdAtIso ?: m.oldestLoadedIso,
                    hasMoreMessages = m.hasMoreMessages || m.oldestLoadedIso == null
                ))
            )
        }
    }

    /** Server truth order: by created_at (un-persisted locals sort as "now"). */
    private fun sortClubMessages(list: List<ClubMessage>): List<ClubMessage> =
        list.sortedBy { LudoTime.parseIsoToEpochMs(it.createdAtIso) ?: System.currentTimeMillis() }

    /**
     * Merges a fetched page into the current list: server rows win, local
     * duplicates (same sender + text + type inside 90s) collapse so an
     * optimistic send that already reached the server never shows twice.
     */
    private fun mergeClubMessages(
        existing: List<ClubMessage>,
        incoming: List<ClubMessage>
    ): List<ClubMessage> {
        val serverIds = incoming.map { it.id }.toHashSet()
        val keptLocal = existing.filter { local ->
            local.id !in serverIds && incoming.none { server ->
                server.senderId == local.senderId &&
                        server.messageType == local.messageType &&
                        server.text == local.text &&
                        local.id.startsWith("cmsg_") &&
                        abs(
                            (LudoTime.parseIsoToEpochMs(server.createdAtIso) ?: 0L) -
                                    (LudoTime.parseIsoToEpochMs(local.createdAtIso)
                                        ?: System.currentTimeMillis())
                        ) < 90_000L
            }
        }
        return sortClubMessages(keptLocal + incoming)
    }

    /** Cached playback files for downloaded voice notes (keyed by message id). */
    private val clubVoiceCache = mutableMapOf<String, File>()

    /**
     * Ensures a club voice note is on disk, downloading it from the
     * private `voice-notes` bucket on first play (v3.3.4).
     */
    suspend fun getClubVoiceNoteFile(message: ClubMessage): File? {
        val path = message.voiceUrl ?: return null
        clubVoiceCache[message.id]?.let { if (it.exists()) return it }
        val ctx = appContext ?: return null
        val bytes = SupabaseRepository.downloadClubVoiceNote(
            path, _uiState.value.authSession?.accessToken
        ) ?: return null
        return runCatching {
            val file = File(ctx.cacheDir, "clubvoice_${message.id}.m4a")
            file.writeBytes(bytes)
            clubVoiceCache[message.id] = file
            file
        }.getOrNull()
    }

    private val clubPollJobs = mutableMapOf<String, Job>()

    fun toggleCreateClubDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateClubDialog = show) }
    }

    fun joinClub(clubId: String) {
        // Enforce 1 Club membership restriction (PRD Section 15)
        val currentClubId = _uiState.value.activeClubId
        if (currentClubId != null) {
            showToast("You are already in a Club. You may belong to only one Club at a time.")
            return
        }
        val targetClub = _uiState.value.clubs.find { it.id == clubId } ?: return
        // Enforce 15 members capacity (PRD Section 13)
        if (targetClub.isFull) {
            showToast("Club Full! Maximum capacity of 15 members reached.")
            return
        }

        val newMember = ClubMember(
            id = "cm_me_${System.currentTimeMillis()}",
            userId = _uiState.value.userProfile.id,
            userName = _uiState.value.userProfile.name,
            userAvatarRes = R.drawable.img_onboarding_hero,
            characterBadge = _uiState.value.userProfile.characterBadge,
            isVerified = _uiState.value.userProfile.isVerified,
            role = "MEMBER",
            status = "ACTIVE",
            joinedAt = "Just now"
        )
        val updatedClubs = _uiState.value.clubs.map { club ->
            if (club.id == clubId) club.copy(members = club.members + newMember) else club
        }
        _uiState.update {
            it.copy(
                clubs = updatedClubs,
                activeClubId = clubId,
                selectedClubForDetail = updatedClubs.find { c -> c.id == clubId },
                toastMessage = "Joined ${targetClub.name}! Welcome to the community 🎉"
            )
        }
        // v3.2.2: mirror the membership row so the join survives restarts
        // and shows for every other account.
        if (SupabaseRepository.isConfigured()) {
            val memberToSync = newMember
            viewModelScope.launch {
                SupabaseRepository.joinClub(
                    clubId = clubId,
                    member = memberToSync,
                    accessToken = _uiState.value.authSession?.accessToken
                )
            }
        }
    }

    fun leaveClub(clubId: String) {
        val myUserId = _uiState.value.userProfile.id
        val updatedClubs = _uiState.value.clubs.map { club ->
            if (club.id == clubId) {
                club.copy(members = club.members.filter { m -> m.userId != myUserId })
            } else club
        }
        _uiState.update {
            it.copy(
                clubs = updatedClubs,
                activeClubId = null,
                selectedClubForDetail = null,
                toastMessage = "You left the Club."
            )
        }
        if (SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                SupabaseRepository.removeClubMembership(
                    clubId = clubId,
                    userId = myUserId,
                    accessToken = _uiState.value.authSession?.accessToken
                )
            }
        }
    }

    fun leaveAndJoinClub(newClubId: String) {
        val currentClubId = _uiState.value.activeClubId
        val targetClub = _uiState.value.clubs.find { it.id == newClubId } ?: return
        if (targetClub.isFull) {
            showToast("Club Full! Maximum capacity of 15 members reached.")
            return
        }

        val myUserId = _uiState.value.userProfile.id
        val newMember = ClubMember(
            id = "cm_me_${System.currentTimeMillis()}",
            userId = myUserId,
            userName = _uiState.value.userProfile.name,
            userAvatarRes = R.drawable.img_onboarding_hero,
            characterBadge = _uiState.value.userProfile.characterBadge,
            isVerified = _uiState.value.userProfile.isVerified,
            role = "MEMBER",
            status = "ACTIVE",
            joinedAt = "Just now"
        )

        val updatedClubs = _uiState.value.clubs.map { club ->
            when (club.id) {
                currentClubId -> club.copy(members = club.members.filter { m -> m.userId != myUserId })
                newClubId -> club.copy(members = club.members.filter { m -> m.userId != myUserId } + newMember)
                else -> club
            }
        }

        _uiState.update {
            it.copy(
                clubs = updatedClubs,
                activeClubId = newClubId,
                selectedClubForDetail = updatedClubs.find { c -> c.id == newClubId },
                toastMessage = "Left previous club and joined ${targetClub.name}! Welcome 🎉"
            )
        }
        // v3.2.2: mirror the switch on the server (remove old membership,
        // add the new one) so it survives restarts.
        if (SupabaseRepository.isConfigured()) {
            val memberToSync = newMember
            val token = _uiState.value.authSession?.accessToken
            viewModelScope.launch {
                if (currentClubId != null) {
                    SupabaseRepository.removeClubMembership(
                        clubId = currentClubId,
                        userId = myUserId,
                        accessToken = token
                    )
                }
                SupabaseRepository.joinClub(
                    clubId = newClubId,
                    member = memberToSync,
                    accessToken = token
                )
            }
        }
    }

    fun createClub(name: String, description: String, category: String, logoEmoji: String) {
        // Enforce 1 Club membership restriction (PRD Section 15)
        if (_uiState.value.activeClubId != null) {
            showToast("You can belong to only one Club at a time. Leave your current Club first.")
            return
        }
        // Enforce 1 Club per 7-day cooldown (PRD Section 14)
        val now = System.currentTimeMillis()
        val cooldownMillis = 7L * 24 * 60 * 60 * 1000 // 7 days
        val timeSinceLast = now - _uiState.value.lastClubCreatedTimestamp
        if (_uiState.value.lastClubCreatedTimestamp > 0 && timeSinceLast < cooldownMillis) {
            val daysRemaining = ((cooldownMillis - timeSinceLast) / (24 * 60 * 60 * 1000) + 1).coerceAtLeast(1)
            showToast("Cooldown active: You can create only 1 Club per 7 days. ($daysRemaining days remaining)")
            return
        }

        val newClubId = "club_${System.currentTimeMillis()}"
        val ownerMember = ClubMember(
            id = "cm_owner_$newClubId",
            userId = _uiState.value.userProfile.id,
            userName = _uiState.value.userProfile.name,
            userAvatarRes = R.drawable.img_onboarding_hero,
            characterBadge = _uiState.value.userProfile.characterBadge,
            isVerified = _uiState.value.userProfile.isVerified,
            role = "OWNER",
            status = "ACTIVE",
            joinedAt = "Just now"
        )
        val newClub = Club(
            id = newClubId,
            ownerId = _uiState.value.userProfile.id,
            name = name,
            description = description,
            category = category,
            logoEmoji = logoEmoji,
            maxMembers = 15,
            createdAt = "Just now",
            members = listOf(ownerMember)
        )
        _uiState.update {
            it.copy(
                clubs = listOf(newClub) + it.clubs,
                activeClubId = newClubId,
                selectedClubForDetail = newClub,
                lastClubCreatedTimestamp = now,
                showCreateClubDialog = false,
                toastMessage = "🎉 Club '$name' created! You are the Owner."
            )
        }
        // v3.2.2: persist the club so OTHER accounts can discover it
        // (search + interest-tag filter). Fire-and-forget — the local
        // club stays usable even when the write fails.
        if (SupabaseRepository.isConfigured()) {
            val clubToSync = newClub
            viewModelScope.launch {
                val synced = SupabaseRepository.createClub(
                    club = clubToSync,
                    accessToken = _uiState.value.authSession?.accessToken
                )
                // v3.3.1: this write is silent by design (local-first UX),
                // but a silent FAILURE is how the "club vanished after
                // logout" bug hid — always leave a logcat breadcrumb.
                if (!synced) {
                    Log.w(
                        "QuickyClubs",
                        "Club '${clubToSync.name}' created locally but NOT saved to Supabase — " +
                                "it will disappear on sign-out. Check network / RLS policies."
                    )
                }
            }
        }
    }

    /**
     * Sends a text / sticker club message.
     *
     * v3.3.4 changes:
     *  - club voice notes are FREE for every member (the premium gate only
     *    applies to 1:1 chat voice notes) — see [sendClubVoiceMessage];
     *  - "@mentions" are parsed by the caller and arrive as user ids;
     *    the database trigger turns each into a tailored push;
     *  - the insert echoes the server row (id + created_at) which patches
     *    the optimistic message, so reloads never duplicate it;
     *  - failures are no longer silent — toast + logcat breadcrumb.
     */
    fun sendClubMessage(
        clubId: String,
        text: String,
        stickerEmoji: String? = null,
        replyToText: String? = null,
        replyToSender: String? = null,
        mentions: List<String> = emptyList()
    ) {
        val me = _uiState.value.userProfile
        // Local mirror of the server's membership gate (the DB trigger is
        // the real enforcement; this keeps the UX honest without a round trip).
        val myStatus = _uiState.value.clubs
            .find { it.id == clubId }?.members
            ?.find { it.userId == me.id }?.status
        if (myStatus == "SUSPENDED") {
            showToast("You are suspended from messaging in this club by the Owner.")
            return
        }

        val newMessage = ClubMessage(
            id = "cmsg_${System.currentTimeMillis()}",
            clubId = clubId,
            senderId = me.id,
            senderName = me.name,
            senderAvatarRes = R.drawable.img_onboarding_hero,
            senderBadge = me.characterBadge,
            messageType = if (stickerEmoji != null) "STICKER" else "TEXT",
            text = text,
            stickerEmoji = stickerEmoji,
            timestamp = "Just now",
            isMine = true,
            replyToText = replyToText,
            replyToSender = replyToSender,
            mentions = mentions
        )
        val currentList = _uiState.value.clubMessages[clubId] ?: emptyList()
        val updatedMap = _uiState.value.clubMessages.toMutableMap().apply {
            put(clubId, currentList + newMessage)
        }
        _uiState.update { it.copy(clubMessages = updatedMap) }

        // Persist to Supabase when configured
        if (SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                val result = SupabaseRepository.insertClubMessage(
                    clubId = clubId,
                    senderId = newMessage.senderId,
                    senderName = newMessage.senderName,
                    messageType = newMessage.messageType,
                    text = text,
                    stickerEmoji = stickerEmoji,
                    replyToText = replyToText,
                    replyToSender = replyToSender,
                    mentions = mentions,
                    accessToken = _uiState.value.authSession?.accessToken
                )
                if (result != null) {
                    reconcileLocalClubMessage(clubId, newMessage.id, result)
                } else {
                    Log.w(
                        "QuickyClubs",
                        "Club message NOT saved to Supabase (club=$clubId) — " +
                                "it will vanish on reload. Check network / RLS / suspension."
                    )
                    showToast("⚠️ Message not delivered — check your connection.")
                }
            }
        }
    }

    /**
     * Sends a REAL recorded club voice note (v3.3.4 — free for all club
     * members): uploads the audio to the private `voice-notes` bucket
     * under `club-voice/<club>/…`, then posts the message row that
     * references it.
     */
    fun sendClubVoiceMessage(
        clubId: String,
        audioBytes: ByteArray,
        durationSeconds: Int
    ) {
        val me = _uiState.value.userProfile
        val myStatus = _uiState.value.clubs
            .find { it.id == clubId }?.members
            ?.find { it.userId == me.id }?.status
        if (myStatus == "SUSPENDED") {
            showToast("You are suspended from messaging in this club by the Owner.")
            return
        }

        val newMessage = ClubMessage(
            id = "cmsg_${System.currentTimeMillis()}",
            clubId = clubId,
            senderId = me.id,
            senderName = me.name,
            senderAvatarRes = R.drawable.img_onboarding_hero,
            senderBadge = me.characterBadge,
            messageType = "VOICE",
            text = "",
            voiceDurationSeconds = durationSeconds,
            timestamp = "Just now",
            isMine = true
        )
        val currentList = _uiState.value.clubMessages[clubId] ?: emptyList()
        _uiState.update {
            it.copy(clubMessages = it.clubMessages.toMutableMap().apply {
                put(clubId, currentList + newMessage)
            })
        }

        if (!SupabaseRepository.isConfigured()) return
        viewModelScope.launch {
            val token = _uiState.value.authSession?.accessToken
            val voicePath = SupabaseRepository.uploadClubVoiceNote(
                clubId = clubId,
                senderId = me.id,
                bytes = audioBytes,
                accessToken = token
            )
            if (voicePath == null) {
                showToast("⚠️ Voice note not uploaded — check your connection.")
                return@launch
            }
            val result = SupabaseRepository.insertClubMessage(
                clubId = clubId,
                senderId = me.id,
                senderName = me.name,
                messageType = "VOICE",
                text = "",
                voiceDurationSeconds = durationSeconds,
                voiceUrl = voicePath,
                accessToken = token
            )
            if (result != null) {
                // Point the optimistic bubble at the uploaded audio so the
                // sender can replay their own note immediately.
                _uiState.update { s ->
                    val list = s.clubMessages[clubId] ?: emptyList()
                    s.copy(clubMessages = s.clubMessages.toMutableMap().apply {
                        put(clubId, list.map {
                            if (it.id == newMessage.id) {
                                it.copy(id = result.id, voiceUrl = voicePath, createdAtIso = result.createdAtIso)
                            } else it
                        })
                    })
                }
            } else {
                showToast("⚠️ Voice note not delivered — check your connection.")
            }
        }
    }

    /** Swaps an optimistic message's temp id for the server row's. */
    private fun reconcileLocalClubMessage(
        clubId: String,
        localId: String,
        result: SupabaseRepository.ClubMessageInsertResult
    ) {
        _uiState.update { s ->
            val list = s.clubMessages[clubId] ?: return@update s
            val meta = s.clubChatMeta[clubId]
            s.copy(
                clubMessages = s.clubMessages.toMutableMap().apply {
                    put(clubId, list.map {
                        if (it.id == localId) {
                            it.copy(id = result.id, createdAtIso = result.createdAtIso)
                        } else it
                    })
                },
                clubChatMeta = if (meta == null) s.clubChatMeta else s.clubChatMeta.toMutableMap().apply {
                    put(clubId, meta.copy(
                        newestLoadedIso = maxOf(
                            meta.newestLoadedIso ?: "",
                            result.createdAtIso
                        ).ifBlank { null } ?: result.createdAtIso
                    ))
                }
            )
        }
    }

    /** Parses "@Name" mentions in a draft into member ids (excludes self). */
    fun resolveClubMentions(clubId: String, text: String): List<String> {
        val club = _uiState.value.clubs.find { it.id == clubId } ?: return emptyList()
        val me = _uiState.value.userProfile.id
        return club.members
            .filter { it.userId != me && it.userName.isNotBlank() }
            .filter { member ->
                text.contains("@${member.userName}", ignoreCase = true)
            }
            .map { it.userId }
            .distinct()
    }

    /**
     * Toggles the signed-in user's emoji reaction on a club message
     * (v3.3.4). Optimistic local state + server write; the server write
     * is idempotent per (message, user, emoji).
     */
    fun toggleClubMessageReaction(clubId: String, messageId: String, emoji: String) {
        val me = _uiState.value.userProfile
        val list = _uiState.value.clubMessages[clubId] ?: return
        val msg = list.find { it.id == messageId } ?: return
        val mine = msg.reactions.find { it.userId == me.id && it.emoji == emoji }

        val patched = if (mine != null) {
            msg.copy(reactions = msg.reactions.filter { it !== mine })
        } else {
            msg.copy(
                reactions = msg.reactions + ClubMessageReaction(
                    id = "local_${System.currentTimeMillis()}",
                    messageId = messageId,
                    userId = me.id,
                    userName = me.name,
                    emoji = emoji
                )
            )
        }
        val before = msg.reactions
        _uiState.update { s ->
            s.copy(clubMessages = s.clubMessages.toMutableMap().apply {
                put(clubId, list.map { if (it.id == messageId) patched else it })
            })
        }

        if (!SupabaseRepository.isConfigured()) return
        viewModelScope.launch {
            val token = _uiState.value.authSession?.accessToken
            val ok = if (mine != null) {
                SupabaseRepository.removeClubReaction(messageId, me.id, emoji, token)
            } else {
                SupabaseRepository.addClubReaction(clubId, messageId, me.id, me.name, emoji, token)
            }
            if (!ok) {
                // Roll the bubble back — offline toggles shouldn't stick.
                _uiState.update { s ->
                    val l = s.clubMessages[clubId] ?: return@update s
                    s.copy(clubMessages = s.clubMessages.toMutableMap().apply {
                        put(clubId, l.map { if (it.id == messageId) it.copy(reactions = before) else it })
                    })
                }
            }
        }
    }

    /**
     * Reports a club member to the owner (v3.3.4 — toxic language etc.).
     * The DB trigger drops a notification on the owner's account.
     */
    fun reportClubMember(
        clubId: String,
        memberUserId: String,
        reason: String,
        details: String = ""
    ) {
        val me = _uiState.value.userProfile
        val club = _uiState.value.clubs.find { it.id == clubId }
        val target = club?.members?.find { it.userId == memberUserId }
        viewModelScope.launch {
            val ok = SupabaseRepository.insertClubReport(
                clubId = clubId,
                reporterId = me.id,
                reporterName = me.name,
                reportedUserId = memberUserId,
                reportedUserName = target?.userName ?: "Member",
                reason = reason,
                details = details,
                accessToken = _uiState.value.authSession?.accessToken
            )
            showToast(
                if (ok) "🚩 Report submitted — the club Owner has been notified."
                else "Report could not be sent. Check your connection."
            )
        }
    }

    /**
     * OWNER action (v3.3.4): suspends / restores a member's right to post
     * in this club. Enforced server-side by the insert trigger; only the
     * owner can flip member rows (RLS).
     */
    fun setClubMemberSuspended(clubId: String, memberUserId: String, suspendMember: Boolean) {
        val club = _uiState.value.clubs.find { it.id == clubId } ?: return
        val target = club.members.find { it.userId == memberUserId } ?: return
        val updatedMembers = club.members.map {
            if (it.userId == memberUserId) it.copy(status = if (suspendMember) "SUSPENDED" else "ACTIVE") else it
        }
        val updatedClubs = _uiState.value.clubs.map {
            if (it.id == clubId) it.copy(members = updatedMembers) else it
        }
        _uiState.update {
            it.copy(
                clubs = updatedClubs,
                selectedClubForDetail = updatedClubs.find { c -> c.id == clubId },
                toastMessage = if (suspendMember)
                    "${target.userName} is suspended from messaging in this club."
                else "${target.userName} can message in this club again."
            )
        }
        if (SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                val ok = SupabaseRepository.updateClubMemberStatus(
                    clubId = clubId,
                    userId = memberUserId,
                    status = if (suspendMember) "SUSPENDED" else "ACTIVE",
                    accessToken = _uiState.value.authSession?.accessToken
                )
                if (!ok) Log.w(
                    "QuickyClubs",
                    "Member status update failed (club=$clubId user=$memberUserId)"
                )
            }
        }
    }

    fun removeClubMember(clubId: String, memberUserId: String) {
        val club = _uiState.value.clubs.find { it.id == clubId } ?: return
        val memberToRemove = club.members.find { it.userId == memberUserId }
        val memberName = memberToRemove?.userName ?: "Member"
        val updatedMembers = club.members.filter { it.userId != memberUserId }
        val updatedClubs = _uiState.value.clubs.map {
            if (it.id == clubId) it.copy(members = updatedMembers) else it
        }
        val systemMsg = ClubMessage(
            id = "cmsg_sys_${System.currentTimeMillis()}",
            clubId = clubId,
            senderId = "system",
            senderName = "SYSTEM",
            messageType = "SYSTEM",
            text = "⚠️ $memberName was removed from the club by the Owner.",
            timestamp = "Just now"
        )
        val currentMessages = _uiState.value.clubMessages[clubId] ?: emptyList()
        val updatedMessages = _uiState.value.clubMessages.toMutableMap().apply {
            put(clubId, currentMessages + systemMsg)
        }
        _uiState.update {
            it.copy(
                clubs = updatedClubs,
                selectedClubForDetail = updatedClubs.find { c -> c.id == clubId },
                clubMessages = updatedMessages,
                toastMessage = "$memberName has been removed from the club."
            )
        }
        // v3.2.2: mirror the owner's removal on the server, and post the
        // SYSTEM notice as a REAL club message (v3.3.4) so every member —
        // including on their next sign-in — sees who was removed.
        if (SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                val token = _uiState.value.authSession?.accessToken
                SupabaseRepository.removeClubMembership(
                    clubId = clubId,
                    userId = memberUserId,
                    accessToken = token
                )
                SupabaseRepository.insertClubMessage(
                    clubId = clubId,
                    senderId = "system",
                    senderName = "SYSTEM",
                    messageType = "SYSTEM",
                    text = systemMsg.text,
                    accessToken = token
                )
            }
        }
    }

    // -------------------------------------------------------------
    // CLUB DELETION — owner only (v2.1 §3.8)
    // Server-side: FK cascades wipe members/messages/events, and the
    // notify_club_deletion trigger pushes a notification to every member.
    // -------------------------------------------------------------
    fun deleteClub(clubId: String) {
        val club = _uiState.value.clubs.find { it.id == clubId } ?: return
        viewModelScope.launch {
            val session = _uiState.value.authSession
            // Offline/QA fallback: without Supabase the deletion happens
            // locally only (member notifications are server-side anyway).
            val deleted = if (!SupabaseRepository.isConfigured()) true
            else runCatching {
                SupabaseRepository.deleteClub(clubId, session?.accessToken)
            }.getOrElse { false }

            if (!deleted) {
                showToast("Could not delete the club — check your connection.")
                return@launch
            }

            val updatedClubs = _uiState.value.clubs.filter { it.id != clubId }
            val updatedMessages = _uiState.value.clubMessages.toMutableMap().apply { remove(clubId) }
            _uiState.update {
                it.copy(
                    clubs = updatedClubs,
                    clubMessages = updatedMessages,
                    selectedClubForDetail = null,
                    activeClubId = if (it.activeClubId == clubId) null else it.activeClubId,
                    toastMessage = "The club \"${club.name}\" was deleted. All ${club.memberCount} members were notified."
                )
            }
        }
    }

    // -------------------------------------------------------------
    // LUDO ARENA METHODS (v3 — full playable multiplayer)
    // Solo-vs-bots plays fully offline through LudoEngine; online
    // matches sync through ludo_* tables + Edge Functions with
    // SUPABASE REALTIME as the primary path (v3 PRD §14) and a slow
    // 10s poll as the recovery fallback ONLY.
    // -------------------------------------------------------------
    private var ludoRealtimeSub: LudoRealtime.Subscription? = null
    private var ludoRecoveryJob: Job? = null
    private var ludoTimerJob: Job? = null
    private var ludoBotDriverJob: Job? = null
    /** Highest authoritative seq this device has applied (ordering, PRD §69). */
    private var ludoLastAppliedSeq: Long = 0L
    /** In-flight auto action guard (PRD §31 — one expiry action per turn). */
    private val ludoAutoActionInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Host-side id of the local user inside online matches. */
    private val ludoLocalUserId: String get() = _uiState.value.userProfile.id

    // -------------------------------------------------------------
    // v3.2 TURN STATE MACHINE (PRD §16–§26): the deterministic board
    // coin-travel duration for the most recent move, so the BOT DRIVER
    // mirrors exactly what the UI gate enforces for humans — never act
    // while the coin is still moving, then wait the 500ms handoff
    // (§19/§20/§23). Set by [noteLudoMoveApplied] on every locally applied
    // move AND on realtime-adopted moves (the host drives bots even for
    // remote players' moves).
    // -------------------------------------------------------------
    /** Epoch-ms when the last applied move's board animation finishes. */
    private var ludoMoveAnimationEndsAt: Long = 0L
    /** True once per applied move — consumed by the bot driver's settle. */
    private var ludoMoveAwaitingSettle: Boolean = false

    /** Records the deterministic coin-animation window for a just-applied move. */
    private fun noteLudoMoveApplied(previous: LudoMatch, next: LudoMatch) {
        // Mover: the token whose stepCount increased.
        var moverFrom = -1; var moverTo = -1; var finished = false
        var captured = 0
        next.players.forEachIndexed { seat, player ->
            val prevTokens = previous.players.getOrNull(seat)?.tokens
            player.tokens.forEach { token ->
                val before = prevTokens?.getOrNull(token.id)?.stepCount ?: 0
                if (token.stepCount > before) {
                    moverFrom = before; moverTo = token.stepCount
                    finished = token.isFinished
                } else if (token.stepCount == 0 && before > 0) captured++
            }
        }
        if (moverTo <= moverFrom) return // no movement (pure roll / timeout skip)
        val duration = LudoRules.moveAnimationMs(moverFrom, moverTo, captured, finished)
        ludoMoveAnimationEndsAt = System.currentTimeMillis() + duration
        ludoMoveAwaitingSettle = true
        Analytics.log(
            Analytics.LUDO_COIN_MOVE_STARTED,
            "game_id" to next.id,
            "player_id" to (next.players.getOrNull(previous.turnIndex)?.id ?: "?"),
            "movement_duration" to duration
        )
    }

    /**
     * Suspending settle for drivers: waits the remaining animation + 500ms.
     * @return true when a settle wait actually RAN (move animation + handoff);
     *         false when no move was pending — callers acting on a fresh turn
     *         that advanced WITHOUT a move (no-legal-move roll, timeout
     *         advance) must apply the 500ms handoff themselves (PRD §20/§23).
     */
    private suspend fun awaitLudoMoveSettled(): Boolean {
        if (!ludoMoveAwaitingSettle) return false
        ludoMoveAwaitingSettle = false
        val remaining = ludoMoveAnimationEndsAt - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
        // PRD §20 — the 500ms turn handoff AFTER the coin settles.
        delay(LudoRules.TURN_HANDOFF_MS)
        val match = _uiState.value.ludoMatch
        if (match != null) {
            Analytics.log(
                Analytics.LUDO_TURN_HANDOFF,
                "game_id" to match.id,
                "turn_handoff_delay" to LudoRules.TURN_HANDOFF_MS,
                "player_id" to (match.players.getOrNull(match.turnIndex)?.id ?: "?")
            )
        }
        return true
    }

    fun openLudoGame() {
        // v2.1 §3.7: gates route through PremiumGate (unlocked for QA on
        // debug builds; LUDO_FREE stays as the release-side freebie flag).
        val ludoUnlocked = BuildConfig.LUDO_FREE || PremiumGate.isPremium(_uiState.value.entitlements)
        if (!ludoUnlocked) {
            showToast("🔒 Ludo is a Quicky Premium Game. Unlock Quicky Gold to play!")
            openPremiumStore()
            return
        }
        _uiState.update { it.copy(isLudoActive = true, ludoMatch = null, ludoJoinError = null) }
    }

    fun closeLudoGame() {
        ludoRealtimeSub?.close(); ludoRealtimeSub = null
        ludoRecoveryJob?.cancel(); ludoRecoveryJob = null
        ludoTimerJob?.cancel(); ludoTimerJob = null
        ludoBotDriverJob?.cancel(); ludoBotDriverJob = null
        ludoLastAppliedSeq = 0L
        ludoMoveAnimationEndsAt = 0L
        ludoMoveAwaitingSettle = false
        _uiState.update {
            it.copy(
                isLudoActive = false,
                ludoMatch = null,
                isLudoRolling = false,
                ludoJoinError = null,
                ludoConnected = true
            )
        }
    }

    fun startLudoSoloBots() {
        ludoRealtimeSub?.close(); ludoRealtimeSub = null
        ludoRecoveryJob?.cancel(); ludoRecoveryJob = null
        ludoBotDriverJob?.cancel(); ludoBotDriverJob = null
        // v3.2.1 (user request — solo must match online timing EXACTLY):
        // clear any stale move-settle window carried over from a previous
        // match; otherwise the first bot action of the new game would wait
        // out a coin animation that never plays on this board.
        ludoLastAppliedSeq = 0L
        ludoMoveAnimationEndsAt = 0L
        ludoMoveAwaitingSettle = false
        _uiState.update {
            it.copy(
                isLudoActive = true,
                ludoMatch = AppContent.freshSoloLudoMatch(playerName = it.userProfile.name),
                isLudoRolling = false,
                ludoJoinError = null
            )
        }
        startLudoTimerWatch()
        maybeDriveBotTurn()
    }

    /** "Play again" from the winner dialog — fresh solo match. */
    fun restartLudoSolo() = startLudoSoloBots()

    /**
     * "Play Again" from the result screen (PRD §26): solo → fresh arena;
     * online → a BRAND-NEW match (the finished one is immutable history).
     */
    fun playLudoAgain() {
        val match = _uiState.value.ludoMatch ?: return
        if (match.mode == LudoMode.ONLINE) createLudoOnlineMatch() else restartLudoSolo()
    }

    /** Remote profile photo for room-chat avatars (PRD §46), if any. */
    private fun ludoAvatarUrl(): String? =
        _uiState.value.userProfile.photoUris.firstOrNull { it.startsWith("http") }

    /** Creates an online 4-player match and shows the shareable code. */
    fun createLudoOnlineMatch() {
        if (!SupabaseRepository.isConfigured()) {
            showToast("Online matches need Supabase — check SupabaseConfig.kt")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingLudoMatch = true, ludoJoinError = null) }
            val session = _uiState.value.authSession
            val code = runCatching {
                LudoMatchRepository.createOnlineMatch(
                    hostUserId = ludoLocalUserId,
                    hostName = _uiState.value.userProfile.name,
                    accessToken = session?.accessToken,
                    avatarUrl = ludoAvatarUrl()
                )
            }.getOrElse { e ->
                _uiState.update { it.copy(isCreatingLudoMatch = false, ludoJoinError = e.message) }
                showToast("Could not create the match: ${e.message}")
                return@launch
            }
            val match = runCatching {
                LudoMatchRepository.fetchMatch(code, session?.accessToken)
            }.getOrNull() ?: LudoMatch(
                id = code,
                mode = LudoMode.ONLINE,
                players = listOf(
                    LudoPlayer(
                        id = ludoLocalUserId,
                        name = _uiState.value.userProfile.name,
                        avatarRes = R.drawable.img_onboarding_hero,
                        seat = 0,
                        avatarUrl = ludoAvatarUrl()
                    )
                ),
                statusText = "Share code $code — fill seats or wait for players",
                localUserId = ludoLocalUserId
            )
            ludoLastAppliedSeq = match.seq
            _uiState.update { it.copy(isCreatingLudoMatch = false, ludoMatch = match, isLudoActive = true) }
            showToast("Match created! Code: $code")
            startLudoRealtime()
        }
    }

    /** Joins an existing online match by its shareable code. */
    fun joinLudoOnlineMatch(code: String) {
        if (code.isBlank()) return
        if (!SupabaseRepository.isConfigured()) {
            showToast("Online matches need Supabase — check SupabaseConfig.kt")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(ludoJoinError = null, isCreatingLudoMatch = true) }
            val session = _uiState.value.authSession
            val joined = runCatching {
                LudoMatchRepository.joinOnlineMatch(
                    matchCode = code,
                    userId = ludoLocalUserId,
                    playerName = _uiState.value.userProfile.name,
                    accessToken = session?.accessToken,
                    avatarUrl = ludoAvatarUrl()
                )
            }
            if (joined.isFailure) {
                _uiState.update {
                    it.copy(
                        isCreatingLudoMatch = false,
                        ludoJoinError = joined.exceptionOrNull()?.message ?: "Could not join."
                    )
                }
                return@launch
            }
            refreshLudoMatchNow()
            _uiState.update { it.copy(isCreatingLudoMatch = false, isLudoActive = true) }
            startLudoRealtime()
        }
    }

    /** Host convenience: fills every empty seat with a bot. */
    fun fillLudoSeatsWithBots() {
        val match = _uiState.value.ludoMatch ?: return
        if (match.mode != LudoMode.ONLINE) return
        viewModelScope.launch {
            val session = _uiState.value.authSession
            runCatching {
                LudoMatchRepository.fillEmptySeatsWithBots(
                    matchCode = match.id,
                    botNames = AppContent.ludoBotNames,
                    accessToken = session?.accessToken
                )
            }.onSuccess {
                refreshLudoMatchNow()
                maybeDriveBotTurn()
            }.onFailure {
                showToast("Could not add bots: ${it.message}")
            }
        }
    }

    /**
     * REALTIME SUBSCRIPTION (v3 PRD §14) — the primary sync path for online
     * play. One socket per active match; callbacks marshal through the
     * seq-guarded appliers below. A slow 10s poll runs alongside purely as
     * a recovery fallback (gap healing + host-owned bot driving).
     */
    private fun startLudoRealtime() {
        val match = _uiState.value.ludoMatch ?: return
        if (match.mode != LudoMode.ONLINE) return
        ludoRealtimeSub?.close()
        ludoLastAppliedSeq = maxOf(ludoLastAppliedSeq, match.seq)
        val session = _uiState.value.authSession
        ludoRealtimeSub = LudoMatchRepository.subscribeRealtime(
            matchCode = match.id,
            accessToken = session?.accessToken,
            onGameState = { row -> onRemoteLudoState(row) },
            onChatMessage = { row -> onRemoteLudoChat(row) },
            onConnection = { online -> _uiState.update { it.copy(ludoConnected = online) } }
        )
        ludoRecoveryJob?.cancel()
        ludoRecoveryJob = viewModelScope.launch {
            while (isActive &&
                _uiState.value.isLudoActive &&
                _uiState.value.ludoMatch?.mode == LudoMode.ONLINE
            ) {
                delay(10_000)
                if (!isActive) break
                refreshLudoMatchNow(quiet = true)
                maybeDriveBotTurn()
            }
        }
        startLudoTimerWatch()
    }

    /** Applies a realtime game-state row (seq-guarded, PRD §69/§70). */
    private fun onRemoteLudoState(row: JSONObject) {
        val current = _uiState.value.ludoMatch ?: return
        if (current.mode != LudoMode.ONLINE) return
        val remote = runCatching {
            LudoMatchRepository.stateRowToMatch(current.id, row)
        }.getOrNull() ?: return
        if (remote.seq < ludoLastAppliedSeq) return // stale duplicate — drop
        if (remote.seq > ludoLastAppliedSeq + 1) {   // gap → full recovery fetch
            refreshLudoMatchNow(quiet = true)
            return
        }
        applyRemoteLudoMatch(remote)
    }

    /** Merges a single realtime chat INSERT (dedupe + pending replace). */
    private fun onRemoteLudoChat(row: JSONObject) {
        val current = _uiState.value.ludoMatch ?: return
        if (current.mode != LudoMode.ONLINE) return
        val message = runCatching { LudoMatchRepository.chatRowToMessage(row) }.getOrNull() ?: return
        _uiState.update { state ->
            val chat = mergeLudoChat(state.ludoMatch?.chatMessages.orEmpty(), listOf(message))
            state.copy(ludoMatch = state.ludoMatch?.copy(chatMessages = chat))
        }
    }

    /** Seq-guarded application of an authoritative online snapshot. */
    private fun applyRemoteLudoMatch(remote: LudoMatch) {
        // Diff base = whatever this device last showed (local apply OR an
        // earlier remote adoption) — see the v3.2 note below.
        val previous = _uiState.value.ludoMatch
        _uiState.update { state ->
            val current = state.ludoMatch
            if (current != null && remote.seq < current.seq) {
                return@update state // an old event after a newer local apply
            }
            val chat = mergeLudoChat(current?.chatMessages.orEmpty(), remote.chatMessages)
            // Don't kill the dice tumble animation mid-flight when the roll
            // event is exactly what the animation is portraying.
            val stillRolling = state.isLudoRolling && remote.phase == LudoPhase.AWAITING_MOVE
            state.copy(
                ludoMatch = remote.copy(mode = LudoMode.ONLINE, chatMessages = chat),
                isLudoRolling = stillRolling
            )
        }
        val applied = _uiState.value.ludoMatch ?: return
        // v3.2 (PRD §21/§22): a remote MOVE arrives with the turn already
        // advanced — record its deterministic coin-animation window so the
        // host's bot driver (and this client's own UI gate) hold the dice
        // until the coin visibly settles + the 500ms handoff elapses. Only a
        // seq+1 transition is treated as a move: an echo of this device's
        // OWN state (same seq) never re-arms the gate, and a recovery fetch
        // (gap > 1) snaps instead of animating. All clients derive the SAME
        // timeline from the same authoritative diff they receive.
        if (previous != null && applied.seq == previous.seq + 1) {
            noteLudoMoveApplied(previous, applied)
        }
        ludoLastAppliedSeq = maxOf(ludoLastAppliedSeq, applied.seq)
        maybeDriveBotTurn()
    }

    /**
     * Chat merge (PRD §55): server rows win; my optimistic pending bubble
     * disappears the moment its server counterpart (same sender + text)
     * lands — the message never shows twice.
     */
    private fun mergeLudoChat(
        current: List<LudoChatMessage>,
        incoming: List<LudoChatMessage>
    ): List<LudoChatMessage> {
        if (incoming.isEmpty()) return current
        val result = current.toMutableList()
        incoming.forEach { msg ->
            if (msg.senderId == ludoLocalUserId) {
                val pendingIdx = result.indexOfFirst {
                    it.isPending && it.senderId == ludoLocalUserId &&
                            it.text == msg.text && it.stickerEmoji == msg.stickerEmoji
                }
                if (pendingIdx >= 0) result.removeAt(pendingIdx)
            }
            if (result.none { it.id == msg.id }) result.add(msg)
        }
        return result
    }

    /** Pulls the authoritative match state (recovery path, PRD §14). */
    private fun refreshLudoMatchNow(quiet: Boolean = false) {
        val match = _uiState.value.ludoMatch ?: return
        if (match.mode != LudoMode.ONLINE) return
        val session = _uiState.value.authSession
        viewModelScope.launch {
            runCatching {
                LudoMatchRepository.fetchMatch(match.id, session?.accessToken)
            }.onSuccess { remote ->
                // Never clobber a mid-roll animation with a stale snapshot.
                if (!_uiState.value.isLudoRolling) {
                    applyRemoteLudoMatch(remote)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // TURN TIMERS (v3 PRD §5/§6/§9/§10) — 10s roll / 30s move,
    // server-timestamp based; expiry triggers the AUTOMATIC roll or
    // the deterministic timeout move (the player is never skipped).
    // -------------------------------------------------------------

    private fun startLudoTimerWatch() {
        ludoTimerJob?.cancel()
        ludoTimerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isLudoActive) {
                val match = _uiState.value.ludoMatch
                if (match == null) break
                if (match.phase != LudoPhase.FINISHED && match.isStarted) {
                    val now = System.currentTimeMillis()
                    val rollExpired = match.phase == LudoPhase.AWAITING_ROLL &&
                            (match.rollDeadlineAt ?: Long.MAX_VALUE) < now - 1500L
                    val moveExpired = match.phase == LudoPhase.AWAITING_MOVE &&
                            (match.moveDeadlineAt ?: Long.MAX_VALUE) < now - 1500L
                    if (rollExpired || moveExpired) {
                        triggerLudoTimeoutAction(rollPhase = rollExpired)
                    }
                }
                delay(500)
            }
        }
    }

    /** Fires exactly one timeout action per expiry window (PRD §31). */
    private fun triggerLudoTimeoutAction(rollPhase: Boolean) {
        if (!ludoAutoActionInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val match = _uiState.value.ludoMatch ?: return@launch
                if (match.phase == LudoPhase.FINISHED) return@launch
                val mineOrBots = match.isMyTurn || (match.currentPlayer.isBot && iAmLudoHost(match))
                when {
                    // Solo arena — this device IS the authority (LudoEngine).
                    match.mode == LudoMode.SOLO_VS_BOTS && mineOrBots && rollPhase ->
                        autoRollSolo(match)
                    match.mode == LudoMode.SOLO_VS_BOTS && mineOrBots ->
                        autoMoveSolo()
                    // Online — nudge the server; the Edge Function performs
                    // and MARKS the automatic action (idempotent validation).
                    match.mode == LudoMode.ONLINE && rollPhase -> {
                        val session = _uiState.value.authSession
                        runCatching {
                            LudoMatchRepository.rollDiceRemote(match.id, session?.accessToken, auto = true)
                        }.onSuccess { dice ->
                            val base = _uiState.value.ludoMatch
                            if (base != null && base.phase == LudoPhase.AWAITING_ROLL &&
                                base.id == match.id && base.isMyTurn
                            ) {
                                val rolled = LudoEngine.applyRoll(base, dice)
                                _uiState.update { it.copy(ludoMatch = rolled) }
                                maybeDriveBotTurn()
                            }
                        }
                    }
                    match.mode == LudoMode.ONLINE -> {
                        val session = _uiState.value.authSession
                        runCatching {
                            LudoMatchRepository.applyMoveRemote(
                                match.id, null, session?.accessToken, auto = true
                            )
                        }.onSuccess {
                            refreshLudoMatchNow(quiet = true)
                        }
                    }
                }
            } finally {
                delay(3000) // give the server + realtime broadcast time to land
                ludoAutoActionInFlight.set(false)
            }
        }
    }

    /** Solo roll timeout → engine rolls for the player (PRD §6). */
    private suspend fun autoRollSolo(match: LudoMatch) {
        _uiState.update { it.copy(isLudoRolling = true) }
        delay((800..1200).random().toLong())
        val base = _uiState.value.ludoMatch ?: return
        if (base.phase != LudoPhase.AWAITING_ROLL) {
            _uiState.update { it.copy(isLudoRolling = false) }
            return
        }
        val rolled = LudoEngine.applyRoll(base, (1..6).random())
        _uiState.update { it.copy(isLudoRolling = false, ludoMatch = rolled) }
        maybeDriveBotTurn()
    }

    /** Solo move timeout → deterministic priority move (PRD §10). */
    private suspend fun autoMoveSolo() {
        val base = _uiState.value.ludoMatch ?: return
        if (base.phase != LudoPhase.AWAITING_MOVE) return
        val tokenId = LudoEngine.pickTimeoutMove(base)
        if (tokenId == null) {
            _uiState.update { it.copy(ludoMatch = LudoEngine.advanceTurn(base)) }
            return
        }
        val result = LudoEngine.applyMove(base, tokenId)
        noteLudoMoveApplied(base, result.match)
        _uiState.update { it.copy(ludoMatch = result.match) }
        maybeDriveBotTurn()
    }

    /**
     * Rolls the dice for the CURRENT player.
     *  - Solo / bots       → local engine roll (800–1200ms animation).
     *  - Online, my seat   → server-authoritative `roll_ludo_dice` Edge
     *                        Function (10s timer enforced server-side),
     *                        falling back to a local roll + state push
     *                        when the function is not deployed.
     *  - Online, bot seat  → only the match host drives bots.
     */
    fun rollLudoDice() {
        val match = _uiState.value.ludoMatch ?: return
        if (_uiState.value.isLudoRolling) return
        if (match.phase != LudoPhase.AWAITING_ROLL) return
        // v3 PRD §15: no rolls until every seat is filled (MATCH_STARTED).
        if (!match.isStarted) {
            showToast("Waiting for all 4 players — share the code or fill bots.")
            return
        }

        val isBotTurn = match.currentPlayer.isBot
        val mineOrBots = match.isMyTurn || (isBotTurn && iAmLudoHost(match))
        if (!mineOrBots) return

        viewModelScope.launch {
            Analytics.log(
                Analytics.LUDO_DICE_ROLL_STARTED,
                "game_id" to match.id,
                "player_id" to match.currentPlayer.id
            )
            _uiState.update { it.copy(isLudoRolling = true) }
            delay((800..1200).random().toLong()) // 3D-ish tumble animation window

            val dice = when {
                match.mode == LudoMode.ONLINE && match.isMyTurn -> {
                    val session = _uiState.value.authSession
                    runCatching {
                        LudoMatchRepository.rollDiceRemote(match.id, session?.accessToken)
                    }.getOrElse {
                        // Edge Function not deployed — roll locally; the
                        // shared path below applies the engine state once
                        // and pushes it to every realtime/polling client.
                        (1..6).random()
                    }
                }
                else -> (1..6).random()
            }

            // v3: the realtime broadcast may have applied the server's roll
            // while the tumble animation played — never apply a second roll
            // on top of it (PRD §31 anti-double-application).
            val base = _uiState.value.ludoMatch ?: return@launch
            if (base.phase != LudoPhase.AWAITING_ROLL) {
                _uiState.update { it.copy(isLudoRolling = false) }
                return@launch
            }
            val rolled = LudoEngine.applyRoll(base, dice)
            _uiState.update { it.copy(isLudoRolling = false, ludoMatch = rolled) }
            ludoLastAppliedSeq = maxOf(ludoLastAppliedSeq, rolled.seq)
            Analytics.log(
                Analytics.LUDO_DICE_ROLL_COMPLETED,
                "game_id" to rolled.id,
                "player_id" to base.currentPlayer.id,
                "roll_result" to dice
            )
            if (rolled.mode == LudoMode.ONLINE) pushOnlineLudoState(rolled)

            // If the turn passed to a bot (or the roll had no legal move and
            // the next seat is a bot), drive it.
            maybeDriveBotTurn()
        }
    }

    /**
     * Applies the chosen token move (PRD §8/§59). The tap is only legal on
     * a highlighted token; the SERVER re-validates everything. Optimistic
     * local application animates instantly, then the authoritative response
     * overwrites it (same rules → same state, seq-aligned).
     */
    fun moveLudoToken(tokenId: Int) {
        val match = _uiState.value.ludoMatch ?: return
        if (match.phase != LudoPhase.AWAITING_MOVE) return
        // v3: only the CURRENT player moves (PRD §16) — bot seats are driven
        // by the bot driver, not by manual taps.
        if (!match.isMyTurn) return

        val result = LudoEngine.applyMove(match, tokenId)
        if (result.match == match) return // illegal move — unchanged

        noteLudoMoveApplied(match, result.match)
        Analytics.log(
            Analytics.LUDO_COIN_MOVE_COMPLETED,
            "game_id" to result.match.id,
            "player_id" to match.currentPlayer.id
        )
        _uiState.update { it.copy(ludoMatch = result.match) }
        ludoLastAppliedSeq = maxOf(ludoLastAppliedSeq, result.match.seq)
        maybeDriveBotTurn()

        if (match.mode == LudoMode.ONLINE) {
            viewModelScope.launch {
                val session = _uiState.value.authSession
                runCatching {
                    LudoMatchRepository.applyMoveRemote(match.id, tokenId, session?.accessToken)
                }.onSuccess { response ->
                    // The server response is authoritative — adopt it wholesale.
                    val remoteBoard = response.optJSONObject("board_state")
                    if (remoteBoard != null) {
                        val current = _uiState.value.ludoMatch
                        val remote = runCatching {
                            LudoMatchRepository.fromJson(
                                match.id, remoteBoard, current?.chatMessages.orEmpty()
                            )
                        }.getOrNull()
                        if (remote != null && (current == null || remote.seq >= current.seq)) {
                            ludoLastAppliedSeq = maxOf(ludoLastAppliedSeq, remote.seq)
                            _uiState.update {
                                it.copy(ludoMatch = remote.copy(mode = LudoMode.ONLINE))
                            }
                        }
                    }
                }.getOrElse {
                    // Function absent → push the engine-computed state directly.
                    pushOnlineLudoState(result.match)
                }
                LudoMatchRepository.logMove(
                    matchCode = match.id,
                    userId = match.currentPlayer.id,
                    fromCell = result.fromStep,
                    toCell = result.toStep,
                    dice = match.diceValue ?: 0,
                    accessToken = session?.accessToken
                )
            }
        }

        maybeDriveBotTurn()
    }

    /** True when this device owns the bot seats (host, or any solo arena). */
    private fun iAmLudoHost(match: LudoMatch): Boolean =
        match.mode == LudoMode.SOLO_VS_BOTS ||
                match.players.getOrNull(0)?.id == match.localUserId ||
                match.players.getOrNull(0)?.id == ludoLocalUserId

    /** Pushes a locally computed board state to the server (online mode).
     *  CAS-guarded on the PREVIOUS server seq — a late push loses cleanly
     *  instead of rolling back a newer authoritative write (PRD §31).
     *  Suspend + inline-call so consecutive pushes from one coroutine
     *  (e.g. the bot driver's roll→move) stay ordered. */
    private suspend fun pushOnlineLudoState(match: LudoMatch) {
        val session = _uiState.value.authSession
        runCatching {
            LudoMatchRepository.updateBoardState(
                matchCode = match.id,
                boardState = LudoMatchRepository.encodeBoardState(match),
                accessToken = session?.accessToken,
                expectedServerSeq = match.seq - 1
            )
        }
    }

    /**
     * Bot driver: when the current turn belongs to a bot this device owns,
     * roll + pick a move after a short "thinking" delay, then repeat while
     * the extra-turn rules keep it the bot's turn.
     *
     * v3.2 TURN STATE MACHINE (PRD §16–§26): bots go through the SAME gates
     * as humans — after every applied move the driver waits for the
     * deterministic coin animation to COMPLETE and then the exact 500ms
     * handoff ([awaitLudoMoveSettled]) before the next roll, so a bot never
     * cuts a moving coin short and never "activates" early (§22). The
     * tumble window and the post-roll "thinking" beat stay human-like.
     */
    private fun maybeDriveBotTurn() {
        val match = _uiState.value.ludoMatch ?: return
        if (match.phase == LudoPhase.FINISHED) return
        if (!match.currentPlayer.isBot) return
        if (!iAmLudoHost(match)) return
        if (ludoBotDriverJob?.isActive == true) return

        ludoBotDriverJob = viewModelScope.launch {
            var current = _uiState.value.ludoMatch ?: return@launch
            var guard = 0
            var drivenSeat = -1
            while (current.currentPlayer.isBot && current.phase != LudoPhase.FINISHED && guard < 40) {
                guard++
                // §19/§20: if a move was just applied (by anyone — the
                // previous player, a remote client or this bot itself), let
                // the coin animation finish and hold the 500ms handoff before
                // the dice moves again.
                val settledByMove = awaitLudoMoveSettled()
                current = _uiState.value.ludoMatch ?: return@launch
                if (!current.currentPlayer.isBot || current.phase == LudoPhase.FINISHED) return@launch
                if (current.turnIndex != drivenSeat) {
                    // Fresh turn handoff — a different player just received the
                    // dice. When a MOVE preceded it, the settle above already
                    // played the full animation + 500ms handoff; when the turn
                    // advanced WITHOUT a move (a roll with no legal move, or a
                    // timeout advance), nothing waited — apply the 500ms
                    // handoff here so SOLO and ONLINE share one identical
                    // timeline (PRD §20/§23 — the result is shown, then a
                    // short beat, then the next player's die activates).
                    drivenSeat = current.turnIndex
                    if (!settledByMove) delay(LudoRules.TURN_HANDOFF_MS)
                    continue
                }
                if (current.phase == LudoPhase.AWAITING_ROLL) {
                    Analytics.log(
                        Analytics.LUDO_DICE_ROLL_STARTED,
                        "game_id" to current.id,
                        "player_id" to current.currentPlayer.id
                    )
                    _uiState.update { it.copy(isLudoRolling = true) }
                    delay((LudoRules.BOT_ROLL_MIN_MS..LudoRules.BOT_ROLL_MAX_MS).random().toLong())
                    val rolled = LudoEngine.applyRoll(_uiState.value.ludoMatch ?: return@launch, (1..6).random())
                    _uiState.update { it.copy(isLudoRolling = false, ludoMatch = rolled) }
                    ludoLastAppliedSeq = maxOf(ludoLastAppliedSeq, rolled.seq)
                    Analytics.log(
                        Analytics.LUDO_DICE_ROLL_COMPLETED,
                        "game_id" to rolled.id,
                        "player_id" to (rolled.players.getOrNull(drivenSeat)?.id ?: "?"),
                        "roll_result" to (rolled.diceValue ?: 0)
                    )
                    if (rolled.mode == LudoMode.ONLINE) pushOnlineLudoState(rolled)
                    current = rolled
                }
                if (current.phase == LudoPhase.AWAITING_MOVE) {
                    delay((LudoRules.BOT_THINK_MIN_MS..LudoRules.BOT_THINK_MAX_MS).random().toLong())
                    val tokenId = LudoEngine.pickBotMove(current)
                    if (tokenId == null) {
                        // No legal bot move — engine already advanced on roll.
                        current = _uiState.value.ludoMatch ?: return@launch
                        continue
                    }
                    val base = _uiState.value.ludoMatch ?: return@launch
                    val result = LudoEngine.applyMove(base, tokenId)
                    noteLudoMoveApplied(base, result.match)
                    Analytics.log(
                        Analytics.LUDO_COIN_MOVE_COMPLETED,
                        "game_id" to result.match.id,
                        "player_id" to base.currentPlayer.id
                    )
                    _uiState.update { it.copy(ludoMatch = result.match) }
                    ludoLastAppliedSeq = maxOf(ludoLastAppliedSeq, result.match.seq)
                    if (result.match.mode == LudoMode.ONLINE) pushOnlineLudoState(result.match)
                    current = result.match
                }
            }
        }
    }

    fun sendLudoChatMessage(
        text: String,
        stickerEmoji: String? = null,
        isVoice: Boolean = false,
        replyToText: String? = null,
        replyToSender: String? = null
    ) {
        if (isVoice && !PremiumGate.isPremium(_uiState.value.entitlements)) {
            showToast("Voice notes in Ludo require Quicky Gold. Upgrade to unlock!")
            openPremiumStore()
            return
        }

        val currentMatch = _uiState.value.ludoMatch ?: return
        val newMsg = LudoChatMessage(
            id = "lmsg_user_${System.currentTimeMillis()}",
            roomId = currentMatch.id,
            senderId = ludoLocalUserId,
            senderName = _uiState.value.userProfile.name,
            isSystem = false,
            text = text,
            stickerEmoji = stickerEmoji,
            voiceDurationSeconds = if (isVoice) 5 else null,
            timestamp = "Just now",
            isMine = true,
            replyToText = replyToText,
            replyToSender = replyToSender,
            // v3 PRD §55 — optimistic bubble, replaced by the server row
            // the moment the realtime INSERT lands (never shown twice).
            isPending = true
        )
        _uiState.update {
            it.copy(
                ludoMatch = it.ludoMatch?.copy(
                    chatMessages = it.ludoMatch?.chatMessages.orEmpty() + newMsg
                )
            )
        }

        // Online matches persist room chat server-side.
        if (currentMatch.mode == LudoMode.ONLINE && SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                val session = _uiState.value.authSession
                runCatching {
                    LudoMatchRepository.sendChatMessage(
                        matchCode = currentMatch.id,
                        senderId = ludoLocalUserId,
                        senderName = newMsg.senderName,
                        text = text,
                        stickerEmoji = stickerEmoji,
                        voiceDurationSeconds = if (isVoice) 5 else null,
                        accessToken = session?.accessToken
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // STICKER STORE & PICKER METHODS (PRD Section 20 - 24)
    // -------------------------------------------------------------
    fun openStickerStore() {
        _uiState.update { it.copy(showStickerStore = true) }
    }

    fun closeStickerStore() {
        _uiState.update { it.copy(showStickerStore = false) }
    }

    fun openStickerPicker() {
        _uiState.update { it.copy(showStickerPicker = true) }
    }

    fun closeStickerPicker() {
        _uiState.update { it.copy(showStickerPicker = false) }
    }

    fun purchaseStickerPack(packId: String) {
        val pack = _uiState.value.stickerPacks.find { it.id == packId } ?: return
        if (pack.isOwned) {
            showToast("You already own '${pack.name}'!")
            return
        }
        // v2.1 §3.1: premium-gated packs stay locked without Quicky Gold
        // (routed through PremiumGate — unlocked for QA on debug builds).
        if (pack.isPremiumGated && !PremiumGate.isPremium(_uiState.value.entitlements)) {
            showToast("🔒 '${pack.name}' is a Quicky Gold exclusive pack.")
            openPremiumStore()
            return
        }

        // Simulate Google Play Billing / coin-wallet purchase & server
        // entitlement verification.
        val updatedPacks = _uiState.value.stickerPacks.map {
            if (it.id == packId) it.copy(isOwned = true) else it
        }
        _uiState.update {
            it.copy(
                stickerPacks = updatedPacks,
                toastMessage = if (pack.priceCoins != null) {
                    "🎉 Paid ${pack.priceCoins} 🪙 — '${pack.name}' unlocked for all chats & Ludo rooms."
                } else {
                    "🎉 Google Play purchase successful! '${pack.name}' unlocked for all chats & Ludo rooms."
                }
            )
        }
    }
}
