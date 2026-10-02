package com.example.data

import com.example.R
import com.example.model.*

/**
 * Static, bundled application content.
 *
 * This file intentionally contains NO demo/test user data.
 * All user-generated data (profiles, matches, chat messages, clubs,
 * notifications, insights, ludo rooms) must come from Supabase at
 * runtime — see [SupabaseConfig] and [SupabaseRepository].
 */
object AppContent {

    /**
     * Minimal shell profile for the local user.
     * TODO(Supabase): Replace with the authenticated user's row from the
     * `profiles` table once Supabase Auth + profile sync is wired up.
     */
    val currentUser = UserProfile(
        id = "user_me",
        name = "You",
        age = 0,
        bio = "",
        city = "",
        distanceKm = 0,
        relationshipIntent = "",
        isVerified = false,
        isOnline = false,
        photoResIds = emptyList(),
        interests = emptyList(),
        lifestyle = emptyMap(),
        prompts = emptyList(),
        compatibilityHighlights = emptyList(),
        profileCompletionScore = 0,
        missingCompletionItems = listOf(
            "Add your first profile photo",
            "Write a short bio",
            "Set your relationship intent"
        )
    )

    // -------------------------------------------------------------
    // INTEREST CATALOG (system-given, selectable interests)
    // Used by: Personal Information & Visibility sheet (profile
    // interests), the matching engine (shared interests) and the
    // Discovery filters sheet. Users may also add custom interests.
    // -------------------------------------------------------------
    val interestCatalog = listOf(
        "Music", "Movies", "Travel", "Fitness", "Cooking", "Gaming",
        "Photography", "Art", "Dancing", "Reading", "Hiking", "Coffee",
        "Pets", "Sports", "Fashion", "Technology", "Yoga", "Cycling",
        "Foodie", "Volunteering", "Comedy", "Theatre", "Writing", "Singing",
        "Swimming", "Running", "Camping", "Astrology", "Board Games",
        "Anime", "Podcasts", "Wine & Dine", "Motorcycles", "Gardening",
        "Startups", "DIY & Crafts"
    )

    // -------------------------------------------------------------
    // ONBOARDING CATALOGS (Auth & Onboarding PRD stages 1-3).
    // All lists can be overridden by the Supabase `interests` and
    // `hobbies` catalog tables so admins can extend them without an
    // app release (see SupabaseRepository.fetchInterestCatalog).
    // -------------------------------------------------------------

    /** Stage 2 — maximum selectable interests (custom ones included). */
    const val MAX_INTERESTS = 20

    val genderOptions = listOf("Male", "Female", "Non-binary", "Prefer not to say", "Other")

    val interestedInOptions = listOf("Men", "Women", "Everyone")

    val lookingForOptions = listOf(
        "Friendship", "Dating", "Relationship", "Casual Connection",
        "Long-term Relationship", "Networking", "Gaming Friends",
        "Socializing", "Open to Anything"
    )

    val hobbyCatalog = listOf(
        "Photography", "Playing Cricket", "Painting", "Hiking", "Cooking",
        "Playing Musical Instruments", "Reading", "Football", "Dancing", "Gaming"
    )

    val qualificationOptions = listOf(
        "High School", "Diploma", "Bachelor's Degree", "Master's Degree",
        "Doctorate", "Professional Qualification", "Other", "Prefer Not to Say"
    )

    // -------------------------------------------------------------
    // GAME CATALOG (product content — can also be served from
    // Supabase via SupabaseRepository.fetchGamesCatalog())
    // -------------------------------------------------------------
    val gamesCatalog = listOf(
        GameDefinition(
            id = "game_truth_or_dare",
            name = "Truth or Dare",
            description = "The ultimate classic playful dating icebreaker. Pick Truth for intimate revelations or Dare for spontaneous fun.",
            isFree = true,
            tag = "FREE",
            playersCount = "2 Players"
        ),
        GameDefinition(
            id = "game_would_you_rather",
            name = "Would You Rather",
            description = "Hilarious and intriguing moral dilemma questions that reveal true priorities and red/green flags.",
            isFree = false,
            tag = "PREMIUM",
            playersCount = "2 Players"
        ),
        GameDefinition(
            id = "game_this_or_that",
            name = "This or That",
            description = "Rapid-fire preference showdowns (Night owl vs Early bird, Beach vs Mountains, Books vs Movies).",
            isFree = false,
            tag = "PREMIUM",
            playersCount = "2 Players"
        ),
        GameDefinition(
            id = "game_two_truths_and_lie",
            name = "Two Truths & A Lie",
            description = "Spot the bluff! Three outrageous claims, but only two actually happened. Can you guess the lie?",
            isFree = false,
            tag = "PREMIUM",
            playersCount = "2 Players"
        ),
        GameDefinition(
            id = "game_rapid_questions",
            name = "Rapid Questions",
            description = "Timed lightning round: answer 5 personal questions in 60 seconds with zero hesitation.",
            isFree = false,
            tag = "PREMIUM",
            playersCount = "2 Players"
        ),
        GameDefinition(
            id = "game_conversation_cards",
            name = "Conversation Cards",
            description = "Thought-provoking curated decks for deeper emotional connection and core values discovery.",
            isFree = false,
            tag = "PREMIUM",
            playersCount = "2 Players"
        ),
        GameDefinition(
            id = "game_couples_challenge",
            name = "Couples Challenge",
            description = "Interactive compatibility challenges designed to see how well you sync under playful pressure.",
            isFree = false,
            tag = "PREMIUM",
            playersCount = "2 Players"
        ),
        GameDefinition(
            id = "game_deep_questions",
            name = "Deep Questions",
            description = "Skip the surface: explore dreams, existential theories, and transformative life lessons.",
            isFree = false,
            tag = "PREMIUM",
            playersCount = "2 Players"
        )
    )

    // -------------------------------------------------------------
    // TRUTH OR DARE PROMPTS (game content — can also be served from
    // Supabase via SupabaseRepository.fetchTruthOrDarePrompts())
    // -------------------------------------------------------------
    val truthOrDarePrompts = listOf(
        TruthOrDarePrompt("tod_1", "Flirty", "TRUTH", "What's the most romantic thing someone has ever done for you?", "Mild"),
        TruthOrDarePrompt("tod_2", "Flirty", "TRUTH", "What is an instant green flag that makes your heart skip a beat?", "Mild"),
        TruthOrDarePrompt("tod_3", "Flirty", "TRUTH", "If our first date was tonight, what would be the ideal vibe?", "Medium"),
        TruthOrDarePrompt("tod_4", "Flirty", "DARE", "Send a voice note giving your best sincere compliment in 5 seconds.", "Spicy"),
        TruthOrDarePrompt("tod_5", "Flirty", "DARE", "Send an emoji combo that secretly describes our chemistry so far.", "Mild"),
        TruthOrDarePrompt("tod_6", "Funny", "TRUTH", "What's the most embarrassing fashion phase you went through as a teenager?", "Mild"),
        TruthOrDarePrompt("tod_7", "Funny", "TRUTH", "What is the weirdest habit you have when you're completely alone?", "Medium"),
        TruthOrDarePrompt("tod_8", "Funny", "DARE", "Type out the last 3 items in your recent search history without context.", "Medium"),
        TruthOrDarePrompt("tod_9", "Deep", "TRUTH", "What is a life lesson you had to learn the hard way that you're grateful for?", "Medium"),
        TruthOrDarePrompt("tod_10", "Deep", "TRUTH", "What is a passion or dream you haven't shared with most people?", "Deep"),
        TruthOrDarePrompt("tod_11", "First Date", "TRUTH", "What's your golden rule for what makes a great first date?", "Mild"),
        TruthOrDarePrompt("tod_12", "First Date", "DARE", "Choose our theoretical first date cocktail/drink order right now.", "Mild")
    )

    val icebreakerSuggestions = listOf(
        "Ask: What's the best hidden-gem coffee spot you've discovered?",
        "Ask: If we could travel anywhere tomorrow, where are we heading?",
        "Ask: What's your go-to comfort food after a chaotic day?",
        "Ask: What song is permanently stuck on repeat for you right now?"
    )

    val characterDefinitions = listOf(
        Pair("The Explorer", "Driven by curiosity, finding unique experiences and spontaneous adventures."),
        Pair("The Conversationalist", "Engages deeply with thoughtful questions and consistent, genuine communication."),
        Pair("The Playful One", "Brings humor, vibrant energy, and high game engagement to every interaction."),
        Pair("The Curious One", "Loves learning new perspectives and asking questions that uncover unexpected stories."),
        Pair("The Social Spark", "Brings people together with warm social energy, dynamic stories, and quick laughs."),
        Pair("The Deep Thinker", "Prefers meaningful philosophical chats over superficial banter."),
        Pair("The Adventurer", "Always ready to try something new, from outdoor quests to eccentric date spots."),
        Pair("The Connector", "Naturally finds common ground and builds fast, genuine rapport.")
    )

    // -------------------------------------------------------------
    // STICKER STORE CATALOG (in-app purchase inventory).
    // Ownership is NOT bundled — purchases are per-account and must be
    // verified/persisted server-side (Google Play + Supabase).
    // -------------------------------------------------------------
    val stickerPackCatalog = listOf(
        StickerPack(
            id = "pack_gamers",
            name = "Gamer Vibes & Memes",
            description = "High-energy reactions, rage quits, victory dances and dice rolls.",
            previewEmoji = "🎮",
            price = "$0.99",
            googleProductId = "quicky.stickers.gamers",
            isOwned = false,
            category = "Gaming",
            stickers = listOf(
                StickerItem("stk_g1", "pack_gamers", "GG Well Played", "🏆", "GG WP!"),
                StickerItem("stk_g2", "pack_gamers", "Rage Quit", "🤬", "NO WAY!!"),
                StickerItem("stk_g3", "pack_gamers", "Lucky Six", "🎲", "Rolled a 6!"),
                StickerItem("stk_g4", "pack_gamers", "Thinking Face", "🤔", "Calculating move..."),
                StickerItem("stk_g5", "pack_gamers", "Victory Dance", "💃", "Unstoppable!"),
                StickerItem("stk_g6", "pack_gamers", "Cry Laugh", "🤣", "I can't even")
            )
        ),
        StickerPack(
            id = "pack_flirty",
            name = "Flirty Sparks & Hearts",
            description = "Cute blushy emojis, butterfly moments and smooth banter.",
            previewEmoji = "💖",
            price = "$1.49",
            googleProductId = "quicky.stickers.flirty",
            isOwned = false,
            category = "Love & Romance",
            stickers = listOf(
                StickerItem("stk_f1", "pack_flirty", "Heart Eyes", "😍", "Hypnotized"),
                StickerItem("stk_f2", "pack_flirty", "Sneaky Wink", "😉", "You know it"),
                StickerItem("stk_f3", "pack_flirty", "Blush Overload", "🫣", "Too sweet"),
                StickerItem("stk_f4", "pack_flirty", "Fire Chemistry", "🔥", "100% Match"),
                StickerItem("stk_f5", "pack_flirty", "Spontaneous Coffee", "☕", "Coffee date?"),
                StickerItem("stk_f6", "pack_flirty", "Sparkle Cheers", "🥂", "Cheers to us")
            )
        ),
        StickerPack(
            id = "pack_desi",
            name = "Malayalam & Desi Masala",
            description = "Iconic mass dialogues, tea time banter, and vibrant desi expressions.",
            previewEmoji = "🌶️",
            price = "$0.99",
            googleProductId = "quicky.stickers.desi",
            isOwned = false,
            category = "Regional Vibes",
            stickers = listOf(
                StickerItem("stk_d1", "pack_desi", "Sadhanam Kayyilundo", "👀", "Sadhanam Kayyilundo?"),
                StickerItem("stk_d2", "pack_desi", "Scene Mone", "🔥", "Scene Mone!"),
                StickerItem("stk_d3", "pack_desi", "Chai Time", "☕", "Chaya Kudi?"),
                StickerItem("stk_d4", "pack_desi", "Mass Entry", "🕶️", "Mass Da!"),
                StickerItem("stk_d5", "pack_desi", "Namaste", "🙏", "Namaskaram"),
                StickerItem("stk_d6", "pack_desi", "Mindblown", "🤯", "Ente Ponno!")
            )
        ),
        StickerPack(
            id = "pack_cute",
            name = "Cute Puppies & Cats",
            description = "Adorable paws, wholesome hugs and playful cheekiness.",
            previewEmoji = "🐾",
            price = "$0.99",
            googleProductId = "quicky.stickers.cute",
            isOwned = false,
            category = "Animals",
            stickers = listOf(
                StickerItem("stk_c1", "pack_cute", "Paw Wave", "🐾", "Henlo friend"),
                StickerItem("stk_c2", "pack_cute", "Puppy Eyes", "🥺", "Pretty please"),
                StickerItem("stk_c3", "pack_cute", "Cat Nap", "😴", "Zzz sleep time"),
                StickerItem("stk_c4", "pack_cute", "Zoomies", "💨", "Excited!"),
                StickerItem("stk_c5", "pack_cute", "Snuggle Hug", "🫂", "Sending hugs"),
                StickerItem("stk_c6", "pack_cute", "Chef Doggo", "👨‍🍳", "Cooking delicious")
            )
        )
    )

    // -------------------------------------------------------------
    // LUDO — fresh 2-player room factory (no pre-seeded game state)
    // -------------------------------------------------------------
    fun freshLudoRoom(playerName: String): LudoRoom {
        val me = LudoPlayer(
            id = "user_me",
            name = playerName.ifBlank { "You" },
            avatarRes = R.drawable.img_onboarding_hero,
            colorHex = 0xFFFF2A6D, // Quicky Pink
            colorName = "Pink",
            score = 0
        )
        val opponent = LudoPlayer(
            id = "user_opponent",
            name = "Opponent",
            avatarRes = R.drawable.img_profile_alex,
            colorHex = 0xFF06B6D4, // Cyan/Blue
            colorName = "Blue",
            score = 0
        )
        return LudoRoom(
            id = "ludo_${System.currentTimeMillis()}",
            player1 = opponent,
            player2 = me,
            currentTurnPlayerId = me.id,
            diceValue = 6,
            isRolling = false,
            canMoveToken = false,
            status = "IN_PROGRESS",
            winnerId = null,
            lastEventText = "🎲 Game started! Roll the dice to begin.",
            chatMessages = emptyList(),
            startedAt = "Just now",
            duration = "00:00"
        )
    }
}
