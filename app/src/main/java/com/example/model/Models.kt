package com.example.model

enum class VisibilityLevel {
    EVERYONE,
    MATCHES_ONLY,
    ONLY_ME
}

// -------------------------------------------------------------
// AUTHENTICATION & ONBOARDING (Appearance/Auth/Logout PRD)
// -------------------------------------------------------------

/** Top-level routing gate driven by the Supabase Auth session. */
enum class AuthGate {
    /** Restoring / validating a persisted session on cold start. */
    CHECKING,
    /** No valid session — show the authentication screen. */
    SIGNED_OUT,
    /** Valid session — route by onboarding state. */
    SIGNED_IN
}

/** A photo picked during onboarding with its client-side validation state. */
data class OnboardingPhoto(
    val uri: String,
    val mimeType: String = "image/jpeg",
    val fileSizeBytes: Long = 0L,
    /** True when ML Kit detected a clearly visible face in this photo. */
    val faceValidated: Boolean = false,
    /** Public Storage URL once uploaded to Supabase (null until uploaded). */
    val remoteUrl: String? = null
)

/**
 * Progressive four-stage onboarding draft (PRD stages 1-4).
 * Persisted locally after every completed stage so an interrupted
 * onboarding can resume exactly where it stopped.
 */
data class OnboardingDraft(
    val step: Int = 1,
    val fullName: String = "",
    val dateOfBirthEpochDay: Long? = null,
    val gender: String = "",
    val customGender: String = "",
    val bio: String = "",
    val interests: List<String> = emptyList(),
    val interestedIn: String = "",
    val lookingFor: List<String> = emptyList(),
    val qualification: String = "",
    val occupation: String = "",
    val hobbies: List<String> = emptyList(),
    // Languages the user speaks — multi-select chips in stage 3; powers
    // the language-overlap discovery filter (both client and RPC sides).
    val languages: List<String> = emptyList(),
    val heightCm: Int? = null,
    val weightKg: Float? = null,
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photos: List<OnboardingPhoto> = emptyList()
) {
    /** Calculated age from the picked date of birth (never manually editable). */
    val calculatedAge: Int?
        get() = dateOfBirthEpochDay?.let { epochDay ->
            java.time.LocalDate.ofEpochDay(epochDay).until(java.time.LocalDate.now()).years
        }

    val displayGender: String
        get() = if (gender == "Other") customGender.trim().ifEmpty { "Other" } else gender

    // --- Stage validation gates (Next button enabled only when valid) ---
    val isStage1Valid: Boolean
        get() = fullName.trim().length in 2..60 &&
                calculatedAge != null && calculatedAge!! >= 18 &&
                (gender.isNotEmpty() && (gender != "Other" || customGender.trim().isNotEmpty()))

    val isStage2Valid: Boolean
        get() = bio.trim().length >= 10 &&
                interests.isNotEmpty() &&
                interestedIn.isNotEmpty() &&
                lookingFor.isNotEmpty()

    val isStage3Valid: Boolean
        get() = qualification.isNotEmpty() && languages.isNotEmpty()

    val isStage4Valid: Boolean
        get() = photos.isNotEmpty() && photos.any { it.faceValidated }
}

/**
 * Builds the locally rendered [UserProfile] from a completed onboarding
 * draft. [photoUrls] are the Storage URLs when the upload succeeded, or
 * the local content uris as an offline fallback.
 */
fun OnboardingDraft.toUserProfile(userId: String, photoUrls: List<String>): UserProfile = UserProfile(
    id = userId,
    name = fullName.trim(),
    age = calculatedAge ?: 18,
    bio = bio.trim(),
    city = city.trim(),
    distanceKm = 0,
    relationshipIntent = lookingFor.firstOrNull() ?: "Open to Anything",
    gender = displayGender.ifEmpty { "Prefer not to say" },
    interestedIn = interestedIn.ifEmpty { "Everyone" },
    educationLevel = qualification,
    occupation = occupation.trim(),
    height = heightCm?.let { "$it cm" } ?: "",
    isVerified = false,
    isOnline = true,
    photoResIds = emptyList(),
    photoUris = photoUrls,
    interests = interests,
    hobbies = hobbies,
    languages = languages.ifEmpty { listOf("English") },
    lookingFor = lookingFor,
    heightCm = heightCm,
    weightKg = weightKg,
    latitude = latitude,
    longitude = longitude,
    dateOfBirth = dateOfBirthEpochDay?.let { java.time.LocalDate.ofEpochDay(it).toString() },
    profileCompletionScore = 100,
    missingCompletionItems = emptyList(),
    compatibilityHighlights = emptyList()
)

/**
 * Location display normalization (Settings & Profile PRD §5).
 *
 * Strips trailing postal/ZIP-code segments from a stored location string so
 * "Kanjirapally, 686507" displays as "Kanjirapally". SAFE by construction:
 * a segment is only removed when it is ENTIRELY postal-shaped (3–8 chars,
 * digits with at most one space or hyphen, e.g. "686507", "WC1B 4AB" is NOT
 * matched because it has letters, "123 45" is). Place names that merely
 * CONTAIN digits ("District 9", "Area 51") always keep their letters and
 * are never touched. The raw value stays untouched in the database — this
 * is display-only.
 */
fun String.toDisplayLocation(): String {
    if (isBlank()) return this
    val parts = split(",").map { it.trim() }
    // Drop ANY comma-separated segment that is entirely postal-shaped —
    // trailing ("…, 686507") or mid-string ("…, 686507, India").
    val kept = parts.filter { part ->
        !(part.length in 3..8 &&
                part.all { it.isDigit() || it == ' ' || it == '-' } &&
                part.count { it == ' ' } <= 1 && part.count { it == '-' } <= 1 &&
                part.replace(" ", "").replace("-", "").let { it.isNotEmpty() && it.all { d -> d.isDigit() } })
    }
    // Never strip everything — fall back to the original value.
    val result = kept.filter { it.isNotBlank() }.joinToString(", ").trim()
    return result.ifBlank { this }
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
    val photoResIds: List<Int> = emptyList(), // Maximum 3 photos (bundled assets)
    val photoUris: List<String> = emptyList(), // Uploaded/local photo URLs (max 3)
    val interests: List<String> = emptyList(),
    val hobbies: List<String> = emptyList(),
    val lookingFor: List<String> = emptyList(),
    val heightCm: Int? = null,
    val weightKg: Float? = null, // Optional, independent visibility control
    // Last captured GPS coordinates — used by the distance-based discovery
    // filter, never displayed raw on the profile.
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dateOfBirth: String? = null, // ISO date — NEVER exposed publicly
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
    /** Occupation keyword filter — blank means "any occupation". */
    val occupation: String = "",
    /** Language filter — empty means "any language"; otherwise candidates must share at least one. */
    val languages: List<String> = emptyList(),
    val verifiedOnly: Boolean = false,
    val withPhotosOnly: Boolean = true,
    val interests: List<String> = emptyList() // Match profiles sharing at least one of these interests
)

// Backward compatibility alias for DiscoveryFilter
typealias DiscoveryFilter = DiscoveryPreferences

enum class AppThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Push notification toggles for the dedicated Settings screen (v2.1 §3.9).
 */
data class NotificationPreferences(
    val matches: Boolean = true,
    val messages: Boolean = true,
    val clubs: Boolean = true,
    val promotions: Boolean = false
)

/** A geocoding search suggestion (Nominatim) for the Edit Location sheet. */
data class GeoSuggestion(
    val latitude: Double,
    val longitude: Double,
    /** Full display name, e.g. "Kochi, Ernakulam District, Kerala, India". */
    val label: String,
    /** Shortened city/area label, e.g. "Kochi, Kerala". */
    val city: String
)

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
// LUDO ARENA MODELS (v2.1 §3.2 — 4-player rewrite)
//
// Turn order is CLOCKWISE: RED → GREEN → YELLOW → BLUE.
// A token's [LudoToken.stepCount] encodes its full journey:
//     0      = in the home yard
//     1..51  = on the shared 52-cell main track
//     52..56 = inside the player's private colored home column
//     57     = finished (center home)
// Tokens leave the yard ONLY on a roll of 6 and need an exact
// roll to enter the final home cell (overshoot is illegal).
// -------------------------------------------------------------
enum class LudoColor(val colorHex: Long, val label: String) {
    RED(0xFFFF5A5F, "Red"),
    GREEN(0xFF4CD964, "Green"),
    YELLOW(0xFFFFC93C, "Yellow"),
    BLUE(0xFF4DA6FF, "Blue")
}

enum class LudoMode { SOLO_VS_BOTS, ONLINE }

/** Which input the match is waiting for. */
enum class LudoPhase { AWAITING_ROLL, AWAITING_MOVE, FINISHED }

// -------------------------------------------------------------
// v3 MULTIPLAYER CONSTANTS (PRD §5/§9/§12/§18/§20)
// -------------------------------------------------------------
object LudoRules {
    /** Seconds a player has to press Roll Dice (PRD §5). */
    const val ROLL_WINDOW_MS: Long = 10_000L
    /** Seconds a player has to select + move a coin after the roll (PRD §9). */
    const val MOVE_WINDOW_MS: Long = 30_000L
    /** Points awarded per coin brought home (PRD §20). */
    const val POINTS_PER_TOKEN: Int = 50
    /** The game ends when this many players have all 4 coins home (PRD §18). */
    const val GAME_END_COMPLETED_PLAYERS: Int = 3
    /** Per-cell animation duration for step-by-step coin movement (PRD §12). */
    const val CELL_HOP_MS: Int = 160
}

data class LudoToken(
    val id: Int, // 0..3
    val stepCount: Int = 0 // see encoding above
) {
    val isInYard: Boolean get() = stepCount == 0
    val isOnMainTrack: Boolean get() = stepCount in 1..51
    val isFinished: Boolean get() = stepCount >= 57
}

data class LudoPlayer(
    val id: String, // "user_me" for the local user, "bot_1"…, or a remote user id
    val name: String,
    val avatarRes: Int,
    val seat: Int, // 0=RED, 1=GREEN, 2=YELLOW, 3=BLUE
    val isBot: Boolean = false,
    val tokens: List<LudoToken> = (0..3).map { LudoToken(id = it) },
    /** Server-awarded points: 50 per finished coin (v3 PRD §20). */
    val score: Int = 0,
    /** 1..4 — assigned the moment this player brings all 4 coins home (v3 PRD §19). */
    val finishPosition: Int? = null,
    /** Remote profile photo (preferred over the bundled seat avatar in chat, v3 PRD §46). */
    val avatarUrl: String? = null
) {
    val color: LudoColor get() = LudoColor.entries.getOrElse(seat) { LudoColor.RED }
    val finishedTokens: Int get() = tokens.count { it.isFinished }
    val hasWon: Boolean get() = tokens.all { it.isFinished }
    /** Sum of step progress — tie-breaker for the final 4th-place ranking. */
    val progressSteps: Int get() = tokens.sumOf { it.stepCount }
}

/**
 * Full server-authoritative match state. Serialized as JSON into
 * `ludo_game_state.board_state` for online matches; drives the board UI
 * in every mode.
 */
data class LudoMatch(
    val id: String,
    val mode: LudoMode = LudoMode.SOLO_VS_BOTS,
    val players: List<LudoPlayer>, // exactly 4 seats (bots fill empty seats)
    val turnIndex: Int = 0,
    val diceValue: Int? = null,
    val phase: LudoPhase = LudoPhase.AWAITING_ROLL,
    /** Consecutive sixes rolled by the current player in THIS turn streak. */
    val consecutiveSixes: Int = 0,
    val winnerId: String? = null,
    val chatMessages: List<LudoChatMessage> = emptyList(),
    /** Short human status for the strip under the board (no chat spam). */
    val statusText: String = "Roll the dice to start",
    /** Player id owned by THIS device — "user_me" for solo, the Supabase uid online. */
    val localUserId: String = LOCAL_USER_ID,
    // --- v3 multiplayer fields (PRD §5/§9/§28/§69) ---
    /** Monotonic per-match event sequence — every authoritative mutation +1. */
    val seq: Long = 0,
    /** Epoch-ms deadline to press Roll Dice for the current turn (server clock). */
    val rollDeadlineAt: Long? = null,
    /** Epoch-ms deadline to move a coin for the current turn (server clock). */
    val moveDeadlineAt: Long? = null,
    /** How many players have already brought all 4 coins home (0..3, PRD §61). */
    val completedPlayers: Int = 0,
    /** Player ids in finishing order — index 0 is 1st place (PRD §19). */
    val finishOrder: List<String> = emptyList()
) {
    val currentPlayer: LudoPlayer get() = players[turnIndex.coerceIn(0, players.lastIndex)]
    val isMyTurn: Boolean get() = currentPlayer.id == localUserId
    /** True once every seat is taken — rolling is blocked before that (online). */
    val isStarted: Boolean get() = players.size >= 4

    companion object {
        /** Marker id for the local user inside a solo Ludo match. */
        const val LOCAL_USER_ID = "user_me"
    }
}

/** One row of the final standings (result screen + ludo_game_results, PRD §23). */
data class LudoGameResult(
    val playerId: String,
    val playerName: String,
    val seat: Int,
    val score: Int,
    val finishedTokens: Int,
    val finishPosition: Int
)

/** A player seat description for joining an online match. */
data class LudoSeatInfo(
    val seat: Int,
    val playerId: String,
    val playerName: String,
    val isBot: Boolean
)

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
    val replyToSender: String? = null,
    /** Optimistic UI flag — replaced when the server row arrives (PRD §55). */
    val isPending: Boolean = false
)

// (v2.1) The old 2-player `LudoRoom` was removed — Ludo Arena is a
// 4-player match described by [LudoMatch] above.

// -------------------------------------------------------------
// STICKERS STORE & OWNERSHIP MODELS (PRD Section 20 - 24, 32; v2.1 §3.1)
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
    /** Real-money price label, e.g. "$0.99" — empty means coin-priced or free. */
    val price: String, // e.g. "$0.99"
    val googleProductId: String,
    val isOwned: Boolean = false,
    val category: String = "Reactions",
    /** Quicky-Gold coin price (v2.1 §3.1 CTA: "Get · 250 🪙"). Null = real-money/free. */
    val priceCoins: Int? = null,
    /** Premium-gated pack — locked (lock icon + price) until Quicky Gold. */
    val isPremiumGated: Boolean = false,
    val stickers: List<StickerItem> = emptyList()
) {
    /** CTA label for the corner chip: "Get · 250 🪙", "Get · Free" or "Get · $0.99". */
    val ctaLabel: String
        get() = when {
            priceCoins != null -> "Get · $priceCoins 🪙"
            price.isBlank() || price == "0" -> "Get · Free"
            else -> "Get · $price"
        }
}
