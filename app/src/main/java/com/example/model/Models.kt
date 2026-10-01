package com.example.model

enum class VisibilityLevel {
    EVERYONE,
    MATCHES_ONLY,
    ONLY_ME
}

data class UserProfile(
    val id: String,
    val name: String,
    val age: Int,
    val bio: String,
    val city: String,
    val distanceKm: Int,
    val relationshipIntent: String,
    val occupation: String = "",
    val industry: String = "",
    val education: String = "",
    val educationLevel: String = "Bachelor's Degree",
    val height: String = "172 cm",
    val gender: String = "Female",
    val interestedIn: String = "Everyone",
    val languages: List<String> = listOf("English"),
    val isVerified: Boolean = true,
    val isOnline: Boolean = true,
    val characterBadge: String = "The Explorer", // Exactly ONE character badge per user
    val characterDescription: String = "Driven by curiosity, finding unique experiences and spontaneous adventures.",
    val showCharacterBadge: Boolean = true,
    val photoResIds: List<Int> = emptyList(), // Maximum 3 photos
    val interests: List<String> = emptyList(),
    val lifestyle: Map<String, String> = emptyMap(),
    val fieldVisibility: Map<String, VisibilityLevel> = mapOf(
        "height" to VisibilityLevel.MATCHES_ONLY,
        "education" to VisibilityLevel.EVERYONE,
        "occupation" to VisibilityLevel.EVERYONE,
        "lifestyle" to VisibilityLevel.EVERYONE,
        "languages" to VisibilityLevel.EVERYONE
    ),
    val prompts: List<ProfilePrompt> = emptyList(),
    val compatibilityScore: Int = 85,
    val compatibilityHighlights: List<String> = emptyList(),
    val profileCompletionScore: Int = 85,
    val missingCompletionItems: List<String> = listOf("Add 3rd photo", "Answer audio prompt")
)

data class ProfilePrompt(
    val question: String,
    val answer: String
)

enum class DiscoveryAction {
    LIKE,
    PASS,
    SUPER_LIKE
}

data class MatchItem(
    val id: String,
    val user: UserProfile,
    val matchedAt: String,
    val lastMessage: String? = null,
    val unreadCount: Int = 0,
    val hasActiveGame: Boolean = false,
    val isNewMatch: Boolean = false
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val timestamp: String,
    val isMine: Boolean,
    val isRead: Boolean = true,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val reactions: List<String> = emptyList(),
    val gameCard: GameCardData? = null,
    val imageResId: Int? = null,
    val isVoiceMessage: Boolean = false,
    val voiceDurationSeconds: Int? = null
)

data class GameCardData(
    val sessionId: String,
    val gameType: String = "Truth or Dare",
    val category: String = "Flirty",
    val promptType: String = "TRUTH", // "TRUTH" or "DARE" or "QUESTION"
    val promptText: String,
    val targetPlayerId: String,
    val answerText: String? = null,
    val isCompleted: Boolean = false,
    val isPremiumOnly: Boolean = false,
    val responseType: String = "TEXT", // "TEXT", "VOICE", "CAMERA"
    val cameraPhotoResId: Int? = null,
    val voiceDurationSeconds: Int? = null
)

data class GameDefinition(
    val id: String,
    val name: String,
    val description: String,
    val isFree: Boolean, // Truth or Dare = true, all others = false
    val tag: String,
    val playersCount: String = "2 Players"
)

data class TruthOrDarePrompt(
    val id: String,
    val category: String, // "Flirty", "Funny", "Deep", "First Date", "Wild"
    val type: String, // "TRUTH" or "DARE"
    val text: String,
    val difficulty: String = "Medium"
)

data class Entitlements(
    val isPremium: Boolean = false,
    val boostsRemaining: Int = 2,
    val superLikesRemaining: Int = 5,
    val rewindsRemaining: Int = 3,
    val unlimitedLikes: Boolean = false,
    val seeWhoLikedYou: Boolean = false,
    val advancedFilters: Boolean = false,
    val isIncognito: Boolean = false,
    val isBoostActive: Boolean = false,
    val hasVoiceChat: Boolean = false
)

data class PrivacySettings(
    val isIncognito: Boolean = false,
    val isInvisible: Boolean = false,
    val showOnlineStatus: Boolean = true,
    val showReadReceipts: Boolean = true,
    val showDistance: Boolean = true,
    val showCharacterBadge: Boolean = true,
    val interactionInsightsEnabled: Boolean = true,
    val messagePreviews: Boolean = true
)

data class InteractionInsight(
    val title: String,
    val score: Int,
    val description: String,
    val traitLabel: String
)

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val type: String, // "MATCH", "MESSAGE", "GAME", "LIKE", "SYSTEM"
    val timeAgo: String,
    val isRead: Boolean = false
)

data class DiscoveryPreferences(
    val whoDoYouWantToSee: String = "Everyone", // "Men", "Women", "Everyone"
    val minAge: Int = 20,
    val maxAge: Int = 35,
    val minHeightCm: Int = 155,
    val maxHeightCm: Int = 195,
    val distanceKm: Int = 50, // 5, 10, 25, 50, 100
    val educationPreference: String = "Any",
    val relationshipIntent: String = "Any",
    val verifiedOnly: Boolean = false,
    val withPhotosOnly: Boolean = true
)

// Backward compatibility alias for DiscoveryFilter
typealias DiscoveryFilter = DiscoveryPreferences

enum class AppThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

// -------------------------------------------------------------
// CLUBS & SOCIAL COMMUNITY MODELS (PRD Section 11 - 18, 32)
// -------------------------------------------------------------
data class Club(
    val id: String,
    val ownerId: String,
    val name: String,
    val description: String,
    val logoEmoji: String = "🎮",
    val logoResId: Int? = null,
    val maxMembers: Int = 15, // Strictly max 15 members
    val status: String = "ACTIVE", // "ACTIVE", "SUSPENDED", "CLOSED"
    val createdAt: String = "Recently",
    val category: String = "Casual Gaming",
    val members: List<ClubMember> = emptyList()
) {
    val memberCount: Int get() = members.size
    val isFull: Boolean get() = memberCount >= maxMembers
}

data class ClubMember(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatarRes: Int? = null,
    val characterBadge: String = "The Explorer",
    val isVerified: Boolean = true,
    val role: String = "MEMBER", // "OWNER" or "MEMBER"
    val status: String = "ACTIVE",
    val joinedAt: String = "1d ago"
)

data class ClubMessage(
    val id: String,
    val clubId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarRes: Int? = null,
    val senderBadge: String? = null,
    val messageType: String = "TEXT", // "TEXT", "STICKER", "VOICE", "SYSTEM"
    val text: String = "",
    val stickerId: String? = null,
    val stickerEmoji: String? = null,
    val voiceDurationSeconds: Int? = null,
    val timestamp: String = "Just now",
    val isMine: Boolean = false,
    val replyToText: String? = null,
    val replyToSender: String? = null
)

// -------------------------------------------------------------
// 2-PLAYER PREMIUM LUDO MODELS (PRD Section 3 - 9, 32)
// -------------------------------------------------------------
data class LudoToken(
    val id: Int, // 0..3
    val playerId: String,
    val stepCount: Int = 0, // 0 = in base (-1), 1..56 = on track/safe path, 57 = Home center
    val isHome: Boolean = true, // Still in starting base
    val isFinished: Boolean = false // Reached home goal
)

data class LudoPlayer(
    val id: String,
    val name: String,
    val avatarRes: Int,
    val colorHex: Long, // e.g. 0xFFFF2A6D (Pink) vs 0xFF06B6D4 (Cyan) or Red vs Green
    val colorName: String, // "Red" vs "Green"
    val score: Int = 100,
    val characterBadge: String = "The Explorer",
    val isVerified: Boolean = true,
    val tokens: List<LudoToken> = (0..3).map { LudoToken(id = it, playerId = id) }
) {
    val finishedTokensCount: Int get() = tokens.count { it.isFinished }
}

data class LudoChatMessage(
    val id: String,
    val roomId: String,
    val senderId: String?,
    val senderName: String,
    val isSystem: Boolean = false,
    val text: String,
    val stickerEmoji: String? = null,
    val voiceDurationSeconds: Int? = null,
    val timestamp: String = "Just now",
    val isMine: Boolean = false,
    val replyToText: String? = null,
    val replyToSender: String? = null
)

data class LudoRoom(
    val id: String,
    val player1: LudoPlayer, // Opposite side top
    val player2: LudoPlayer, // Opposite side bottom
    val currentTurnPlayerId: String,
    val diceValue: Int = 6,
    val isRolling: Boolean = false,
    val canMoveToken: Boolean = false,
    val status: String = "IN_PROGRESS", // "WAITING", "IN_PROGRESS", "COMPLETED"
    val winnerId: String? = null,
    val lastEventText: String = "Game started! Roll dice to begin.",
    val chatMessages: List<LudoChatMessage> = emptyList(),
    val startedAt: String = "Just now",
    val duration: String = "02:45"
)

// -------------------------------------------------------------
// STICKERS STORE & OWNERSHIP MODELS (PRD Section 20 - 24, 32)
// -------------------------------------------------------------
data class StickerItem(
    val id: String,
    val packId: String,
    val name: String,
    val emojiRepresentation: String,
    val caption: String
)

data class StickerPack(
    val id: String,
    val name: String,
    val description: String,
    val previewEmoji: String,
    val price: String, // e.g. "$0.99"
    val googleProductId: String,
    val isOwned: Boolean = false,
    val category: String = "Reactions",
    val stickers: List<StickerItem> = emptyList()
)
