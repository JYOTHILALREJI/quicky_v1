package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppContent
import com.example.data.SupabaseAuth
import com.example.data.SupabaseRepository
import com.example.model.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

enum class SparkTab {
    DISCOVER,
    MATCHES,
    CHATS,
    GAMES,
    CLUBS,
    PROFILE
}

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
    val toastMessage: String? = null,
    val selectedProfileDetail: UserProfile? = null,
    val showFilterSheet: Boolean = false,
    val showPremiumStore: Boolean = false,
    val showSafetyCenter: Boolean = false,
    val showPrivacyCenter: Boolean = false,
    val showPersonalInformationSheet: Boolean = false,
    val showVerificationDialog: Boolean = false,
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
    val lastClubCreatedTimestamp: Long = 0L,
    val showCreateClubDialog: Boolean = false,

    // 2-Player Ludo state (created fresh in openLudoGame())
    val ludoRoom: LudoRoom? = null,
    val isLudoActive: Boolean = false,

    // Sticker Store state
    val stickerPacks: List<StickerPack> = AppContent.stickerPackCatalog,
    val showStickerStore: Boolean = false,
    val showStickerPicker: Boolean = false
) {
    val filter: DiscoveryFilter get() = discoveryPreferences
}

class SparkViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SparkUiState())
    val uiState: StateFlow<SparkUiState> = _uiState.asStateFlow()

    init {
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
     * Signs out: revokes the session server-side, clears local tokens
     * and resets all in-memory app state. Profile, matches and chat
     * history live in the database and reload on the next sign-in.
     */
    fun signOut(context: Context) {
        viewModelScope.launch {
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
                    discoveryDeck = emptyList(),
                    passedHistory = emptyList(),
                    matchCelebration = null,
                    matches = emptyList(),
                    selectedMatchForChat = null,
                    messages = emptyMap(),
                    selectedProfileDetail = null,
                    clubs = emptyList(),
                    activeClubId = null,
                    selectedClubForDetail = null,
                    clubMessages = emptyMap(),
                    notifications = emptyList(),
                    interactionInsights = emptyList(),
                    ludoRoom = null,
                    isLudoActive = false
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
                        val parts = listOfNotNull(
                            address.thoroughfare?.takeIf { it.isNotBlank() },
                            (address.locality ?: address.subAdminArea ?: address.adminArea)
                                ?.takeIf { it.isNotBlank() }
                        )
                        val combined = parts.joinToString(", ")
                        when {
                            combined.isNotBlank() -> combined
                            else -> address.getAddressLine(0)?.takeIf { it.isNotBlank() }
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
            if (isSuperLike && _uiState.value.entitlements.superLikesRemaining <= 0 && !_uiState.value.entitlements.isPremium) {
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
                    }
                )
            }

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
                passedHistory = updatedPassed
            )
        }

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
                else -> _uiState.update { it.copy(discoveryDeck = candidates) }
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
            sharedInterests = prefs.interests
        )
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

        if (_uiState.value.entitlements.rewindsRemaining <= 0 && !_uiState.value.entitlements.isPremium) {
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

    fun activateBoost() {
        if (!_uiState.value.entitlements.isPremium && _uiState.value.entitlements.boostsRemaining <= 0) {
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
                    text = text
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
        _uiState.update { it.copy(showVerificationDialog = true) }
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

    fun dismissVerificationDialog() {
        _uiState.update { it.copy(showVerificationDialog = false) }
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

    fun blockUser(userId: String) {
        val updatedDeck = _uiState.value.discoveryDeck.filter { it.id != userId }
        val updatedMatches = _uiState.value.matches.filter { it.user.id != userId }

        _uiState.update {
            it.copy(
                discoveryDeck = updatedDeck,
                matches = updatedMatches,
                selectedMatchForChat = if (it.selectedMatchForChat?.user?.id == userId) null else it.selectedMatchForChat,
                selectedProfileDetail = null,
                toastMessage = "User has been blocked. They cannot discover you, view your profile, or message you."
            )
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
     * preferences: age window, distance, occupation keyword,
     * verification and shared interests (a profile matches when it
     * shares at least one).
     */
    private fun applyDiscoveryFilters(
        deck: List<UserProfile>,
        prefs: DiscoveryPreferences
    ): List<UserProfile> {
        val occupationQuery = prefs.occupation.trim()
        return deck.filter { profile ->
            profile.age in prefs.minAge..prefs.maxAge &&
                profile.distanceKm <= prefs.distanceKm &&
                (occupationQuery.isBlank() ||
                        profile.occupation.contains(occupationQuery, ignoreCase = true)) &&
                (!prefs.verifiedOnly || profile.isVerified) &&
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

    fun toggleNotificationsSheet(show: Boolean) {
        _uiState.update { it.copy(showNotificationsSheet = show) }
    }

    fun markNotificationsRead() {
        val updated = _uiState.value.notifications.map { it.copy(isRead = true) }
        _uiState.update { it.copy(notifications = updated) }
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
    fun openClub(club: Club) {
        _uiState.update { it.copy(selectedClubForDetail = club) }
    }

    fun closeClubDetail() {
        _uiState.update { it.copy(selectedClubForDetail = null) }
    }

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
    }

    fun leaveClub(clubId: String) {
        val updatedClubs = _uiState.value.clubs.map { club ->
            if (club.id == clubId) {
                club.copy(members = club.members.filter { m -> m.userId != _uiState.value.userProfile.id })
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
    }

    fun sendClubMessage(
        clubId: String,
        text: String,
        stickerEmoji: String? = null,
        isVoice: Boolean = false,
        replyToText: String? = null,
        replyToSender: String? = null
    ) {
        if (isVoice && !_uiState.value.entitlements.isPremium) {
            showToast("🔒 Voice messages are a Quicky Gold feature. Upgrade to unlock!")
            openPremiumStore()
            return
        }

        val currentList = _uiState.value.clubMessages[clubId] ?: emptyList()
        val newMessage = ClubMessage(
            id = "cmsg_${System.currentTimeMillis()}",
            clubId = clubId,
            senderId = _uiState.value.userProfile.id,
            senderName = _uiState.value.userProfile.name,
            senderAvatarRes = R.drawable.img_onboarding_hero,
            senderBadge = _uiState.value.userProfile.characterBadge,
            messageType = when {
                isVoice -> "VOICE"
                stickerEmoji != null -> "STICKER"
                else -> "TEXT"
            },
            text = text,
            stickerEmoji = stickerEmoji,
            voiceDurationSeconds = if (isVoice) 7 else null,
            timestamp = "Just now",
            isMine = true,
            replyToText = replyToText,
            replyToSender = replyToSender
        )
        val updatedMap = _uiState.value.clubMessages.toMutableMap().apply {
            put(clubId, currentList + newMessage)
        }
        _uiState.update { it.copy(clubMessages = updatedMap) }

        // Persist to Supabase when configured (fire-and-forget)
        if (SupabaseRepository.isConfigured()) {
            viewModelScope.launch {
                SupabaseRepository.insertClubMessage(
                    clubId = clubId,
                    senderId = newMessage.senderId,
                    senderName = newMessage.senderName,
                    messageType = newMessage.messageType,
                    text = text
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
    }

    // -------------------------------------------------------------
    // 2-PLAYER PREMIUM LUDO METHODS (PRD Section 3 - 10)
    // -------------------------------------------------------------
    fun openLudoGame() {
        if (!_uiState.value.entitlements.isPremium) {
            showToast("🔒 Ludo is a Quicky Premium Game. Unlock Quicky Gold to play!")
            openPremiumStore()
            return
        }
        _uiState.update {
            it.copy(
                isLudoActive = true,
                ludoRoom = AppContent.freshLudoRoom(playerName = it.userProfile.name)
            )
        }
    }

    fun closeLudoGame() {
        _uiState.update { it.copy(isLudoActive = false) }
    }

    fun rollLudoDice() {
        val currentRoom = _uiState.value.ludoRoom ?: return
        if (currentRoom.currentTurnPlayerId != "user_me" || currentRoom.isRolling) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(ludoRoom = it.ludoRoom?.copy(isRolling = true, canMoveToken = false))
            }
            delay(400) // Animated dice roll

            val rolledValue = (1..6).random()
            val isSix = rolledValue == 6

            val p2Tokens = currentRoom.player2.tokens
            val hasMovable = p2Tokens.any { token ->
                if (token.isFinished) false
                else if (token.isHome) isSix
                else (token.stepCount + rolledValue) <= 57
            }

            val systemMsg = LudoChatMessage(
                id = "lmsg_${System.currentTimeMillis()}",
                roomId = currentRoom.id,
                senderId = null,
                senderName = "SYSTEM",
                isSystem = true,
                text = "🎲 You rolled a $rolledValue! ${if (isSix) "(Bonus Turn ⭐)" else ""}"
            )

            if (!hasMovable) {
                // Pass turn to opponent
                _uiState.update {
                    it.copy(
                        ludoRoom = it.ludoRoom?.copy(
                            diceValue = rolledValue,
                            isRolling = false,
                            canMoveToken = false,
                            currentTurnPlayerId = currentRoom.player1.id,
                            lastEventText = "You rolled $rolledValue, but no legal moves. Opponent's turn.",
                            chatMessages = it.ludoRoom?.chatMessages.orEmpty() + systemMsg
                        )
                    )
                }
                triggerOpponentTurn()
            } else {
                _uiState.update {
                    it.copy(
                        ludoRoom = it.ludoRoom?.copy(
                            diceValue = rolledValue,
                            isRolling = false,
                            canMoveToken = true,
                            lastEventText = "🎲 You rolled a $rolledValue! Tap a glowing token to advance.",
                            chatMessages = it.ludoRoom?.chatMessages.orEmpty() + systemMsg
                        )
                    )
                }
            }
        }
    }

    fun moveLudoToken(tokenId: Int) {
        val currentRoom = _uiState.value.ludoRoom ?: return
        if (!currentRoom.canMoveToken || currentRoom.currentTurnPlayerId != "user_me") return

        val token = currentRoom.player2.tokens.find { it.id == tokenId } ?: return
        val dice = currentRoom.diceValue

        if (token.isHome && dice != 6) {
            showToast("Need a 6 to release token from base!")
            return
        }

        val newStepCount = if (token.isHome) 1 else token.stepCount + dice
        if (newStepCount > 57) {
            showToast("Roll exceeds distance to home goal!")
            return
        }

        val isFinished = newStepCount == 57
        val updatedToken = token.copy(
            isHome = false,
            stepCount = newStepCount,
            isFinished = isFinished
        )
        val updatedP2Tokens = currentRoom.player2.tokens.map { if (it.id == tokenId) updatedToken else it }
        var p2Score = currentRoom.player2.score + (dice * 2)
        if (isFinished) p2Score += 50

        // Check capture opponent token:
        var capturedOpponent = false
        var updatedP1Tokens = currentRoom.player1.tokens
        if (!isFinished && newStepCount < 50) {
            val opponentCapturedToken = currentRoom.player1.tokens.find { !it.isHome && !it.isFinished && it.stepCount == ((newStepCount + 26) % 52) }
            if (opponentCapturedToken != null) {
                capturedOpponent = true
                p2Score += 25
                updatedP1Tokens = currentRoom.player1.tokens.map {
                    if (it.id == opponentCapturedToken.id) it.copy(isHome = true, stepCount = 0) else it
                }
            }
        }

        val updatedP1 = currentRoom.player1.copy(tokens = updatedP1Tokens)
        val updatedP2 = currentRoom.player2.copy(tokens = updatedP2Tokens, score = p2Score)

        val hasWon = updatedP2Tokens.all { it.isFinished } || updatedP2Tokens.count { it.isFinished } >= 2
        val bonusTurn = dice == 6 || capturedOpponent

        val moveMsg = LudoChatMessage(
            id = "lmsg_mv_${System.currentTimeMillis()}",
            roomId = currentRoom.id,
            senderId = null,
            senderName = "SYSTEM",
            isSystem = true,
            text = "🚀 You moved Token #${tokenId + 1} ${if (capturedOpponent) "💥 and CAPTURED ${currentRoom.player1.name}'s token! (+25 pts)" else ""}"
        )

        val nextPlayerId = if (bonusTurn && !hasWon) "user_me" else currentRoom.player1.id
        val nextText = if (hasWon) "🏆 VICTORY! You won the Ludo match!"
        else if (bonusTurn) "⭐ Bonus roll awarded! Roll again."
        else "${currentRoom.player1.name}'s turn to roll."

        _uiState.update {
            it.copy(
                ludoRoom = it.ludoRoom?.copy(
                    player1 = updatedP1,
                    player2 = updatedP2,
                    canMoveToken = false,
                    currentTurnPlayerId = nextPlayerId,
                    winnerId = if (hasWon) "user_me" else null,
                    status = if (hasWon) "COMPLETED" else "IN_PROGRESS",
                    lastEventText = nextText,
                    chatMessages = it.ludoRoom?.chatMessages.orEmpty() + moveMsg
                )
            )
        }

        if (!hasWon && nextPlayerId == currentRoom.player1.id) {
            triggerOpponentTurn()
        }
    }

    private fun triggerOpponentTurn() {
        viewModelScope.launch {
            delay(1200)
            val currentRoom = _uiState.value.ludoRoom ?: return@launch
            if (currentRoom.status == "COMPLETED" || currentRoom.currentTurnPlayerId != currentRoom.player1.id) return@launch

            val rolled = (1..6).random()
            val p1Tokens = currentRoom.player1.tokens
            val tokenToMove = p1Tokens.find { !it.isFinished && (!it.isHome || rolled == 6) }

            val oppRollMsg = LudoChatMessage(
                id = "lmsg_opp_${System.currentTimeMillis()}",
                roomId = currentRoom.id,
                senderId = null,
                senderName = "SYSTEM",
                isSystem = true,
                text = "🎲 ${currentRoom.player1.name} rolled a $rolled"
            )

            if (tokenToMove != null) {
                val newSteps = if (tokenToMove.isHome) 1 else (tokenToMove.stepCount + rolled).coerceAtMost(57)
                val updatedTokens = p1Tokens.map {
                    if (it.id == tokenToMove.id) it.copy(isHome = false, stepCount = newSteps, isFinished = newSteps == 57)
                    else it
                }
                val updatedP1 = currentRoom.player1.copy(
                    tokens = updatedTokens,
                    score = currentRoom.player1.score + (rolled * 2)
                )
                _uiState.update {
                    it.copy(
                        ludoRoom = it.ludoRoom?.copy(
                            player1 = updatedP1,
                            diceValue = rolled,
                            currentTurnPlayerId = "user_me",
                            lastEventText = "${currentRoom.player1.name} rolled $rolled and moved. Your turn!",
                            chatMessages = it.ludoRoom?.chatMessages.orEmpty() + oppRollMsg
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        ludoRoom = it.ludoRoom?.copy(
                            diceValue = rolled,
                            currentTurnPlayerId = "user_me",
                            lastEventText = "${currentRoom.player1.name} rolled $rolled with no moves. Your turn!",
                            chatMessages = it.ludoRoom?.chatMessages.orEmpty() + oppRollMsg
                        )
                    )
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
        if (isVoice && !_uiState.value.entitlements.isPremium) {
            showToast("Voice notes in Ludo require Quicky Gold. Upgrade to unlock!")
            openPremiumStore()
            return
        }

        val currentRoom = _uiState.value.ludoRoom ?: return
        val newMsg = LudoChatMessage(
            id = "lmsg_user_${System.currentTimeMillis()}",
            roomId = currentRoom.id,
            senderId = _uiState.value.userProfile.id,
            senderName = _uiState.value.userProfile.name,
            isSystem = false,
            text = text,
            stickerEmoji = stickerEmoji,
            voiceDurationSeconds = if (isVoice) 5 else null,
            timestamp = "Just now",
            isMine = true,
            replyToText = replyToText,
            replyToSender = replyToSender
        )
        _uiState.update {
            it.copy(
                ludoRoom = it.ludoRoom?.copy(
                    chatMessages = it.ludoRoom?.chatMessages.orEmpty() + newMsg
                )
            )
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

        // Simulate Google Play Billing Purchase & Server Entitlement Verification
        val updatedPacks = _uiState.value.stickerPacks.map {
            if (it.id == packId) it.copy(isOwned = true) else it
        }
        _uiState.update {
            it.copy(
                stickerPacks = updatedPacks,
                toastMessage = "🎉 Google Play purchase successful! '${pack.name}' unlocked for all chats & Ludo rooms."
            )
        }
    }
}
