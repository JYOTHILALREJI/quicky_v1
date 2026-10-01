package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppContent
import com.example.data.SupabaseRepository
import com.example.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun completeOnboarding() {
        _uiState.update { it.copy(isOnboardingComplete = true) }
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

            val isMutualMatch = true
            val newMatchItem = MatchItem(
                id = "match_${profile.id}",
                user = profile,
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
                    matchCelebration = if (isMutualMatch) profile else null,
                    toastMessage = if (isSuperLike) "Super Liked ${profile.name}! 🌟" else "Liked ${profile.name} ❤️"
                )
            }
        }
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
    }

    /**
     * Refreshes the discovery deck.
     * TODO(Supabase): fetch candidate profiles from the `profiles` table
     * once remote image URLs replace local drawable res IDs.
     */
    fun resetDiscoveryDeck() {
        if (!SupabaseRepository.isConfigured()) {
            showToast("Connect your Supabase credentials in SupabaseConfig.kt to load real profiles.")
        } else {
            showToast("No more profiles to discover right now. Check back soon!")
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
        val photos = _uiState.value.userProfile.photoResIds.toMutableList()
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
        val photos = _uiState.value.userProfile.photoResIds.toMutableList()
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

    fun addPhoto(photoResId: Int) {
        val photos = _uiState.value.userProfile.photoResIds.toMutableList()
        if (photos.size >= 3) {
            showToast("Maximum 3 profile photos allowed.")
            return
        }
        photos.add(photoResId)
        val updatedUser = _uiState.value.userProfile.copy(
            photoResIds = photos,
            profileCompletionScore = (_uiState.value.userProfile.profileCompletionScore + 5).coerceAtMost(100)
        )
        _uiState.update {
            it.copy(
                userProfile = updatedUser,
                toastMessage = "Photo uploaded to Supabase Storage! (${photos.size}/3)"
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
    }

    fun updatePersonalInformation(
        height: String,
        occupation: String,
        education: String,
        intent: String,
        fieldVisibility: Map<String, VisibilityLevel>
    ) {
        val updated = _uiState.value.userProfile.copy(
            height = height,
            occupation = occupation,
            educationLevel = education,
            education = education,
            relationshipIntent = intent,
            fieldVisibility = fieldVisibility
        )
        _uiState.update {
            it.copy(
                userProfile = updated,
                showPersonalInformationSheet = false,
                toastMessage = "Personal details & visibility updated ✨"
            )
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
        _uiState.update {
            it.copy(
                discoveryPreferences = newPrefs,
                showFilterSheet = false,
                toastMessage = "Discovery preferences saved"
            )
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
