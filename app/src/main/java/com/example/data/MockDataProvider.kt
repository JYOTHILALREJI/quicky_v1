package com.example.data

import com.example.R
import com.example.model.*

object MockDataProvider {

    val currentUser = UserProfile(
        id = "user_me",
        name = "Jordan",
        age = 26,
        bio = "Architect by day, vinyl hunter and amateur sourdough baker by night. Always down for spontaneous road trips and finding hidden espresso spots.",
        city = "Dubai, UAE",
        distanceKm = 0,
        relationshipIntent = "Long-term relationship",
        occupation = "Architectural Designer",
        industry = "Design & Architecture",
        education = "Pratt Institute",
        educationLevel = "Master's Degree",
        height = "178 cm",
        gender = "Male",
        interestedIn = "Women",
        languages = listOf("English", "Arabic"),
        isVerified = true,
        isOnline = true,
        characterBadge = "The Explorer", // Single character badge
        characterDescription = "Driven by curiosity, finding unique experiences and spontaneous adventures.",
        showCharacterBadge = true,
        photoResIds = listOf(R.drawable.img_onboarding_hero, R.drawable.img_profile_alex), // Max 3 photos
        interests = listOf("Architecture", "Coffee", "Vinyl Records", "Hiking", "Indie Cinema", "Bouldering"),
        lifestyle = mapOf(
            "Exercise" to "4-5x a week",
            "Drinking" to "Socially",
            "Smoking" to "Never",
            "Pets" to "Has a Golden Retriever 🐕",
            "Zodiac" to "Gemini ♊"
        ),
        fieldVisibility = mapOf(
            "height" to VisibilityLevel.MATCHES_ONLY,
            "education" to VisibilityLevel.EVERYONE,
            "occupation" to VisibilityLevel.EVERYONE,
            "lifestyle" to VisibilityLevel.EVERYONE,
            "languages" to VisibilityLevel.EVERYONE
        ),
        prompts = listOf(
            ProfilePrompt(
                question = "My ideal Sunday morning looks like...",
                answer = "Freshly pulled espresso, slow jazz on the turntable, and reading without looking at my phone until noon."
            ),
            ProfilePrompt(
                question = "The quickest way to win me over...",
                answer = "Recommending an underrated hole-in-the-wall taco spot or challenging me to Truth or Dare."
            )
        ),
        profileCompletionScore = 85,
        missingCompletionItems = listOf("Add 3rd profile photo", "Record 10-second voice intro")
    )

    val candidateProfiles = listOf(
        UserProfile(
            id = "user_sarah",
            name = "Sarah",
            age = 27,
            bio = "Gallery curator & weekend ceramicist. In search of someone who appreciates witty banter, gallery openings, and late-night noodle runs 🍜",
            city = "Dubai, UAE",
            distanceKm = 4,
            relationshipIntent = "Long-term relationship",
            occupation = "Contemporary Art Curator",
            industry = "Arts & Culture",
            education = "Columbia University",
            educationLevel = "Master's Degree",
            height = "168 cm",
            gender = "Female",
            interestedIn = "Men",
            languages = listOf("English", "French"),
            isVerified = true,
            isOnline = true,
            characterBadge = "The Playful One",
            characterDescription = "Brings humor, vibrant energy, and high game engagement to every interaction.",
            showCharacterBadge = true,
            photoResIds = listOf(R.drawable.img_profile_sarah, R.drawable.img_onboarding_hero), // Max 3
            interests = listOf("Art Galleries", "Coffee", "Ceramics", "Indie Cinema", "Travel", "Natural Wine"),
            lifestyle = mapOf(
                "Exercise" to "Yoga & Pilates",
                "Drinking" to "Natural Wine & Cocktails",
                "Smoking" to "Never",
                "Pets" to "Cat person 🐈",
                "Zodiac" to "Libra ♎"
            ),
            prompts = listOf(
                ProfilePrompt(
                    question = "My most controversial food opinion...",
                    answer = "Pineapple on pizza is genuinely good, and iced matcha lattes should never have sugar syrups added."
                ),
                ProfilePrompt(
                    question = "Together we could...",
                    answer = "Find the best secret speakeasy in Dubai and make questionable pottery together."
                )
            ),
            compatibilityScore = 94,
            compatibilityHighlights = listOf(
                "Both looking for Long-term relationship",
                "Shared passions: Coffee, Indie Cinema, Travel",
                "High interaction playfulness alignment"
            )
        ),
        UserProfile(
            id = "user_alex",
            name = "Alex",
            age = 28,
            bio = "Product designer & outdoor enthusiast. Always planning the next camping expedition or trying to master latte art.",
            city = "Dubai, UAE",
            distanceKm = 6,
            relationshipIntent = "Long-term relationship",
            occupation = "UX Design Lead",
            industry = "Technology",
            education = "NYU",
            educationLevel = "Bachelor's Degree",
            height = "182 cm",
            gender = "Male",
            interestedIn = "Women",
            languages = listOf("English", "Arabic"),
            isVerified = true,
            isOnline = true,
            characterBadge = "The Conversationalist",
            characterDescription = "Engages deeply with thoughtful questions and consistent, genuine communication.",
            showCharacterBadge = true,
            photoResIds = listOf(R.drawable.img_profile_alex, R.drawable.img_onboarding_hero),
            interests = listOf("Hiking", "Architecture", "Photography", "Bouldering", "Espresso", "Live Music"),
            lifestyle = mapOf(
                "Exercise" to "Rock climbing & Trail running",
                "Drinking" to "Craft beer occasionally",
                "Smoking" to "Never",
                "Pets" to "Rescue dog owner 🦮",
                "Zodiac" to "Sagittarius ♐"
            ),
            prompts = listOf(
                ProfilePrompt(
                    question = "A non-negotiable for me is...",
                    answer = "Being curious about the world and knowing how to laugh when things go completely off itinerary."
                )
            ),
            compatibilityScore = 89,
            compatibilityHighlights = listOf(
                "Common interests: Hiking, Bouldering, Architecture",
                "Both value spontaneous outdoor weekends"
            )
        ),
        UserProfile(
            id = "user_maya",
            name = "Maya",
            age = 26,
            bio = "Biotech researcher & salsa dancer. Life's too short for boring small talk—let's play a game and find out what really makes you tick!",
            city = "Abu Dhabi, UAE",
            distanceKm = 28,
            relationshipIntent = "Casual dating & see where it goes",
            occupation = "Biotech Research Fellow",
            industry = "Healthcare & Science",
            education = "Cornell University",
            educationLevel = "Doctorate",
            height = "170 cm",
            gender = "Female",
            interestedIn = "Men",
            languages = listOf("English", "Spanish"),
            isVerified = true,
            isOnline = false,
            characterBadge = "The Social Spark",
            characterDescription = "Brings people together with warm social energy, dynamic stories, and quick laughs.",
            showCharacterBadge = true,
            photoResIds = listOf(R.drawable.img_profile_sarah, R.drawable.img_profile_alex),
            interests = listOf("Dancing", "Travel", "Board Games", "Cooking", "Sci-Fi", "Cocktails"),
            lifestyle = mapOf(
                "Exercise" to "Salsa 3x week",
                "Drinking" to "Socially",
                "Smoking" to "Never",
                "Pets" to "Loves all animals 🐾",
                "Zodiac" to "Aries ♈"
            ),
            prompts = listOf(
                ProfilePrompt(
                    question = "The secret to my heart is...",
                    answer = "Teaching me your favorite recipe or letting me drag you onto the salsa dance floor."
                )
            ),
            compatibilityScore = 86,
            compatibilityHighlights = listOf(
                "High game participation preference",
                "Shared curiosity for cooking & travel"
            )
        ),
        UserProfile(
            id = "user_marcus",
            name = "Marcus",
            age = 29,
            bio = "Sound engineer and jazz enthusiast. Big fan of vintage thrift store gems, analog cameras, and honest midnight talks.",
            city = "Dubai, UAE",
            distanceKm = 9,
            relationshipIntent = "Long-term relationship",
            occupation = "Audio Producer",
            industry = "Music & Entertainment",
            education = "Berklee College of Music",
            educationLevel = "Bachelor's Degree",
            height = "185 cm",
            gender = "Male",
            interestedIn = "Women",
            languages = listOf("English"),
            isVerified = false,
            isOnline = true,
            characterBadge = "The Deep Thinker",
            characterDescription = "Prefers meaningful philosophical chats over superficial banter.",
            showCharacterBadge = true,
            photoResIds = listOf(R.drawable.img_profile_alex, R.drawable.img_onboarding_hero),
            interests = listOf("Vinyl Records", "Live Music", "Analog Photography", "Indie Cinema", "Coffee"),
            lifestyle = mapOf(
                "Exercise" to "Cycling",
                "Drinking" to "Socially",
                "Smoking" to "Never"
            ),
            prompts = listOf(
                ProfilePrompt(
                    question = "A shower thought I recently had...",
                    answer = "Every book you've ever read is just 26 letters arranged in different ways."
                )
            ),
            compatibilityScore = 91,
            compatibilityHighlights = listOf(
                "Shared passion: Vinyl Records & Indie Cinema",
                "Both value thoughtful communication"
            )
        )
    )

    val initialMatches = listOf(
        MatchItem(
            id = "match_sarah",
            user = candidateProfiles[0],
            matchedAt = "2 hours ago",
            lastMessage = "You answered the Dare! That was hilarious 😂",
            unreadCount = 1,
            hasActiveGame = true,
            isNewMatch = false
        ),
        MatchItem(
            id = "match_alex",
            user = candidateProfiles[1],
            matchedAt = "Yesterday",
            lastMessage = "Let's check out that bouldering gym this Saturday!",
            unreadCount = 0,
            hasActiveGame = false,
            isNewMatch = false
        ),
        MatchItem(
            id = "match_maya",
            user = candidateProfiles[2],
            matchedAt = "Just now",
            lastMessage = "Sent you a Truth or Dare invite! 🎮",
            unreadCount = 2,
            hasActiveGame = true,
            isNewMatch = true
        )
    )

    val initialSarahMessages = listOf(
        ChatMessage(
            id = "msg_1",
            conversationId = "match_sarah",
            senderId = "user_sarah",
            text = "Hey Jordan! That ceramic mug in your photo is gorgeous. Did you make it yourself? 👋",
            timestamp = "10:14 AM",
            isMine = false,
            reactions = listOf("❤️")
        ),
        ChatMessage(
            id = "msg_2",
            conversationId = "match_sarah",
            senderId = "user_me",
            text = "Guilty as charged! Took a pottery workshop last month and ended up hooked. How long have you been into ceramics?",
            timestamp = "10:18 AM",
            isMine = true
        ),
        ChatMessage(
            id = "msg_3",
            conversationId = "match_sarah",
            senderId = "user_sarah",
            text = "About two years! Instead of basic small talk, why don't we play a round of Truth or Dare? I'll let you pick first! 🎲",
            timestamp = "10:22 AM",
            isMine = false
        ),
        ChatMessage(
            id = "msg_4",
            conversationId = "match_sarah",
            senderId = "user_sarah",
            text = "",
            timestamp = "10:23 AM",
            isMine = false,
            gameCard = GameCardData(
                sessionId = "game_sarah_1",
                gameType = "Truth or Dare",
                category = "Flirty",
                promptType = "TRUTH",
                promptText = "What was your very first impression of my profile that made you swipe right?",
                targetPlayerId = "user_me",
                answerText = "Your gallery curator bio + the art gallery photo in golden hour! You seemed effortlessly cool and authentic.",
                isCompleted = true
            )
        ),
        ChatMessage(
            id = "msg_5",
            conversationId = "match_sarah",
            senderId = "user_sarah",
            text = "Aww that's so sweet 🥰 Now it's my turn, hit me with a Dare!",
            timestamp = "10:30 AM",
            isMine = false,
            reactions = listOf("🔥")
        )
    )

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

    val interactionInsights = listOf(
        InteractionInsight(
            title = "Playful Charm",
            score = 92,
            description = "High engagement with Truth or Dare games, witty icebreakers, and creative banter.",
            traitLabel = "The Playful One"
        ),
        InteractionInsight(
            title = "Conversation Depth",
            score = 84,
            description = "Thoughtful message length, meaningful follow-ups, and genuine curiosity about your matches.",
            traitLabel = "The Conversationalist"
        ),
        InteractionInsight(
            title = "Response Consistency",
            score = 89,
            description = "Reliable response times and respectful interaction rhythms that build natural rapport.",
            traitLabel = "Consistent"
        ),
        InteractionInsight(
            title = "Curiosity & Openness",
            score = 95,
            description = "Frequent participation in prompts and interest discovery conversations.",
            traitLabel = "The Explorer"
        )
    )

    val initialNotifications = listOf(
        NotificationItem(
            id = "notif_1",
            title = "New Match on Quicky! 🎉",
            message = "You and Maya liked each other! Start the conversation or send a game challenge.",
            type = "MATCH",
            timeAgo = "10m ago",
            isRead = false
        ),
        NotificationItem(
            id = "notif_2",
            title = "Truth or Dare Turn 🎲",
            message = "Sarah answered your Truth prompt! Check out what she said.",
            type = "GAME",
            timeAgo = "35m ago",
            isRead = false
        ),
        NotificationItem(
            id = "notif_3",
            title = "New Like Received ❤️",
            message = "Someone nearby liked your profile! Upgrade to Quicky Gold to see who.",
            type = "LIKE",
            timeAgo = "2h ago",
            isRead = true
        )
    )

    // -------------------------------------------------------------
    // STICKER PACKS (PRD Section 20 - 24)
    // -------------------------------------------------------------
    val sampleStickerPacks = listOf(
        StickerPack(
            id = "pack_gamers",
            name = "Gamer Vibes & Memes",
            description = "High-energy reactions, rage quits, victory dances and dice rolls.",
            previewEmoji = "🎮",
            price = "$0.99",
            googleProductId = "quicky.stickers.gamers",
            isOwned = true,
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
    // CLUBS & COMMUNITIES (PRD Section 11 - 18)
    // -------------------------------------------------------------
    val sampleClubs = listOf(
        Club(
            id = "club_gamers",
            ownerId = "user_me",
            name = "Weekend Gamers 🎲",
            description = "Passionate casual gamers who love Ludo showdowns, multiplayer banter, and spontaneous gaming nights.",
            logoEmoji = "🕹️",
            maxMembers = 15,
            status = "ACTIVE",
            createdAt = "3 days ago",
            category = "Gaming & Ludo",
            members = listOf(
                ClubMember("cm_1", "user_me", "Jordan (You)", R.drawable.img_onboarding_hero, "The Explorer", true, "OWNER", "ACTIVE", "3d ago"),
                ClubMember("cm_2", "user_alex", "Alex", R.drawable.img_profile_alex, "The Adventurer", true, "MEMBER", "ACTIVE", "2d ago"),
                ClubMember("cm_3", "user_sarah", "Sarah", R.drawable.img_profile_sarah, "The Playful One", true, "MEMBER", "ACTIVE", "2d ago"),
                ClubMember("cm_4", "user_maya", "Maya", R.drawable.img_profile_alex, "The Conversationalist", true, "MEMBER", "ACTIVE", "1d ago"),
                ClubMember("cm_5", "user_liam", "Liam", null, "The Deep Thinker", false, "MEMBER", "ACTIVE", "1d ago"),
                ClubMember("cm_6", "user_elena", "Elena", null, "The Social Spark", true, "MEMBER", "ACTIVE", "12h ago"),
                ClubMember("cm_7", "user_tariq", "Tariq", null, "The Connector", true, "MEMBER", "ACTIVE", "8h ago")
            )
        ),
        Club(
            id = "club_foodies",
            ownerId = "user_sarah",
            name = "Specialty Coffee & Bites ☕",
            description = "Hunting hidden hole-in-the-wall roasteries, sourdough bakeries, and weekend brunch spot discovery.",
            logoEmoji = "🥐",
            maxMembers = 15,
            status = "ACTIVE",
            createdAt = "5 days ago",
            category = "Food & Lifestyle",
            members = listOf(
                ClubMember("cm_f1", "user_sarah", "Sarah", R.drawable.img_profile_sarah, "The Playful One", true, "OWNER", "ACTIVE", "5d ago"),
                ClubMember("cm_f2", "user_nora", "Nora", null, "The Explorer", true, "MEMBER", "ACTIVE", "4d ago"),
                ClubMember("cm_f3", "user_omar", "Omar", null, "The Curious One", true, "MEMBER", "ACTIVE", "3d ago")
            )
        ),
        Club(
            id = "club_music",
            ownerId = "user_lucas",
            name = "Late Night Vinyl Snobs 🎵",
            description = "Sharing indie treasures, synthwave playlists, and obscure jazz records. Strictly good music vibes.",
            logoEmoji = "🎧",
            maxMembers = 15,
            status = "ACTIVE",
            createdAt = "1 week ago",
            category = "Music & Arts",
            members = (1..15).map { idx ->
                ClubMember("cm_m$idx", "user_m$idx", if (idx == 1) "Lucas" else "Member $idx", null, "The Deep Thinker", true, if (idx == 1) "OWNER" else "MEMBER", "ACTIVE", "${idx}d ago")
            } // Exactly 15/15 (Full Club for testing PRD capacity restriction)
        ),
        Club(
            id = "club_anime",
            ownerId = "user_hana",
            name = "Anime & Cosplay Guild ⚔️",
            description = "Weekly episode watch parties, manga discussions, and convention planning.",
            logoEmoji = "🌸",
            maxMembers = 15,
            status = "ACTIVE",
            createdAt = "4 days ago",
            category = "Entertainment",
            members = listOf(
                ClubMember("cm_a1", "user_hana", "Hana", null, "The Creative One", true, "OWNER", "ACTIVE", "4d ago"),
                ClubMember("cm_a2", "user_sam", "Sam", null, "The Adventurer", false, "MEMBER", "ACTIVE", "3d ago")
            )
        )
    )

    val sampleClubMessages = mapOf(
        "club_gamers" to listOf(
            ClubMessage("cmsg_1", "club_gamers", "user_alex", "Alex", R.drawable.img_profile_alex, "The Adventurer", "TEXT", "Welcome to Weekend Gamers everyone! Who's ready for a Ludo battle tonight? 🎲", timestamp = "10:00 AM", isMine = false),
            ClubMessage("cmsg_2", "club_gamers", "user_sarah", "Sarah", R.drawable.img_profile_sarah, "The Playful One", "TEXT", "Count me in! I've been practicing my token roll strategies all week 😉", timestamp = "10:05 AM", isMine = false),
            ClubMessage("cmsg_3", "club_gamers", "user_me", "Jordan (You)", R.drawable.img_onboarding_hero, "The Explorer", "TEXT", "I'm in! Let's set up a 2-player Ludo room right now. Highest score takes the crown.", timestamp = "10:10 AM", isMine = true),
            ClubMessage("cmsg_4", "club_gamers", "user_alex", "Alex", R.drawable.img_profile_alex, "The Adventurer", "STICKER", "", stickerId = "stk_g1", stickerEmoji = "🏆", timestamp = "10:12 AM", isMine = false),
            ClubMessage("cmsg_5", "club_gamers", "user_maya", "Maya", R.drawable.img_profile_alex, "The Conversationalist", "TEXT", "Rooting for Jordan on this one! Don't let Alex get a 6 on the first roll haha", timestamp = "10:15 AM", isMine = false)
        )
    )

    // -------------------------------------------------------------
    // INITIAL 2-PLAYER PREMIUM LUDO ROOM (PRD Section 3 - 10)
    // -------------------------------------------------------------
    val initialLudoRoom: LudoRoom by lazy {
        val player1 = LudoPlayer(
            id = "user_alex",
            name = "Alex",
            avatarRes = R.drawable.img_profile_alex,
            colorHex = 0xFF06B6D4, // Cyan/Blue
            colorName = "Blue",
            score = 125,
            characterBadge = "The Adventurer",
            isVerified = true,
            tokens = listOf(
                LudoToken(id = 0, playerId = "user_alex", stepCount = 14, isHome = false),
                LudoToken(id = 1, playerId = "user_alex", stepCount = 4, isHome = false),
                LudoToken(id = 2, playerId = "user_alex", stepCount = 0, isHome = true),
                LudoToken(id = 3, playerId = "user_alex", stepCount = 0, isHome = true)
            )
        )

        val player2 = LudoPlayer(
            id = "user_me",
            name = "Jordan (You)",
            avatarRes = R.drawable.img_onboarding_hero,
            colorHex = 0xFFFF2A6D, // Quicky Pink/Red
            colorName = "Pink",
            score = 110,
            characterBadge = "The Explorer",
            isVerified = true,
            tokens = listOf(
                LudoToken(id = 0, playerId = "user_me", stepCount = 22, isHome = false),
                LudoToken(id = 1, playerId = "user_me", stepCount = 8, isHome = false),
                LudoToken(id = 2, playerId = "user_me", stepCount = 0, isHome = true),
                LudoToken(id = 3, playerId = "user_me", stepCount = 0, isHome = true)
            )
        )

        LudoRoom(
            id = "ludo_room_1",
            player1 = player1,
            player2 = player2,
            currentTurnPlayerId = "user_me", // It's user's turn
            diceValue = 6,
            isRolling = false,
            canMoveToken = true,
            status = "IN_PROGRESS",
            winnerId = null,
            lastEventText = "🎲 You rolled a 6! Select a token to move.",
            chatMessages = listOf(
                LudoChatMessage("lmsg_1", "ludo_room_1", "user_alex", "Alex", false, "Good luck Jordan! Let's see who reaches home center first 🔥", timestamp = "10:20 AM", isMine = false),
                LudoChatMessage("lmsg_2", "ludo_room_1", null, "SYSTEM", true, "🎮 Game Started · 2 Players · 57 Steps to Victory", timestamp = "10:20 AM", isMine = false),
                LudoChatMessage("lmsg_3", "ludo_room_1", "user_alex", "Alex", false, "Rolling first...", timestamp = "10:21 AM", isMine = false),
                LudoChatMessage("lmsg_4", "ludo_room_1", null, "SYSTEM", true, "🎲 Alex rolled a 4 and advanced Token 1", timestamp = "10:21 AM", isMine = false),
                LudoChatMessage("lmsg_5", "ludo_room_1", "user_me", "Jordan", false, "Watch this roll! 😎", timestamp = "10:22 AM", isMine = true),
                LudoChatMessage("lmsg_6", "ludo_room_1", null, "SYSTEM", true, "🎲 Jordan rolled a 6! (Bonus Turn Granted)", timestamp = "10:22 AM", isMine = false)
            ),
            startedAt = "2m ago",
            duration = "02:15"
        )
    }
}
