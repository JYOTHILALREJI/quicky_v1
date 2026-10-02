package com.example

import com.example.model.PrivacySettings
import com.example.model.TruthOrDarePrompt
import com.example.model.UserProfile
import com.example.ui.SparkTab
import com.example.ui.SparkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SparkViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SparkViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SparkViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testProfile(id: String = "user_test") = UserProfile(
        id = id,
        name = "Test User",
        age = 25,
        bio = "Test bio",
        city = "Dubai, UAE",
        distanceKm = 1,
        relationshipIntent = "Long-term relationship"
    )

    @Test
    fun testInitialStateHasNoTestData() {
        val state = viewModel.uiState.value
        assertEquals(SparkTab.DISCOVER, state.currentTab)

        // All user-generated data starts EMPTY (served by Supabase later)
        assertTrue(state.discoveryDeck.isEmpty())
        assertTrue(state.matches.isEmpty())
        assertTrue(state.messages.isEmpty())
        assertTrue(state.clubs.isEmpty())
        assertTrue(state.clubMessages.isEmpty())
        assertTrue(state.notifications.isEmpty())
        assertTrue(state.interactionInsights.isEmpty())
        assertNull(state.activeClubId)
        assertNull(state.ludoRoom)

        // Bundled static catalogs are still available
        assertTrue(state.gamesCatalog.isNotEmpty())
        assertTrue(state.truthOrDarePrompts.isNotEmpty())
        assertTrue(state.stickerPacks.isNotEmpty())
        assertTrue(state.gamesCatalog.any { it.isFree })
        // No fake ownership bundled — purchases are per-account
        assertTrue(state.stickerPacks.none { it.isOwned })
    }

    @Test
    fun testTabNavigation() {
        viewModel.setTab(SparkTab.MATCHES)
        assertEquals(SparkTab.MATCHES, viewModel.uiState.value.currentTab)

        viewModel.setTab(SparkTab.GAMES)
        assertEquals(SparkTab.GAMES, viewModel.uiState.value.currentTab)

        viewModel.setTab(SparkTab.PROFILE)
        assertEquals(SparkTab.PROFILE, viewModel.uiState.value.currentTab)
    }

    @Test
    fun testLikeCreatesMatch() {
        val candidate = testProfile()

        viewModel.likeProfile(candidate, isSuperLike = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.matchCelebration)
        assertEquals(candidate.id, state.matchCelebration?.id)
        assertTrue(state.matches.any { it.user.id == candidate.id })
    }

    @Test
    fun testPassAndRewind() {
        val candidate = testProfile()

        viewModel.passProfile(candidate)
        val deckAfterPass = viewModel.uiState.value.discoveryDeck
        assertFalse(deckAfterPass.contains(candidate))
        assertEquals(1, viewModel.uiState.value.passedHistory.size)

        val rewindsBefore = viewModel.uiState.value.entitlements.rewindsRemaining
        viewModel.rewindLastPass()
        val deckAfterRewind = viewModel.uiState.value.discoveryDeck
        assertTrue(deckAfterRewind.any { it.id == candidate.id })
        assertEquals(rewindsBefore - 1, viewModel.uiState.value.entitlements.rewindsRemaining)
    }

    @Test
    fun testSendTruthOrDareInChat() {
        // Create a match first (no bundled matches anymore)
        viewModel.likeProfile(testProfile(), isSuperLike = false)
        testDispatcher.scheduler.advanceUntilIdle()
        val match = viewModel.uiState.value.matches.first()

        val prompt = TruthOrDarePrompt(
            id = "test_prompt",
            category = "Flirty",
            type = "TRUTH",
            text = "What is your biggest turn on?"
        )

        viewModel.sendTruthOrDareInChat(match.id, prompt, match.user.id)

        val messages = viewModel.uiState.value.messages[match.id]
        assertNotNull(messages)
        val gameMessage = messages?.findLast { it.gameCard != null }
        assertNotNull(gameMessage)
        assertEquals("TRUTH", gameMessage?.gameCard?.promptType)
        assertEquals("What is your biggest turn on?", gameMessage?.gameCard?.promptText)
        assertFalse(gameMessage?.gameCard?.isCompleted ?: true)

        // Answer game
        viewModel.answerTruthOrDare(match.id, gameMessage!!.id, "Genuine confidence and great taste in books!")
        val updatedMessages = viewModel.uiState.value.messages[match.id]
        val answeredGameMessage = updatedMessages?.find { it.id == gameMessage.id }
        assertTrue(answeredGameMessage?.gameCard?.isCompleted ?: false)
        assertEquals("Genuine confidence and great taste in books!", answeredGameMessage?.gameCard?.answerText)
    }

    @Test
    fun testPurchaseSubscriptionGrantsEntitlements() {
        assertFalse(viewModel.uiState.value.entitlements.isPremium)
        viewModel.purchaseSubscription(isYearly = true)

        val entitlements = viewModel.uiState.value.entitlements
        assertTrue(entitlements.isPremium)
        assertTrue(entitlements.unlimitedLikes)
        assertTrue(entitlements.seeWhoLikedYou)
        assertTrue(entitlements.advancedFilters)
    }

    @Test
    fun testPrivacySettingsUpdate() {
        val newSettings = PrivacySettings(
            isIncognito = true,
            isInvisible = false,
            showOnlineStatus = false,
            showReadReceipts = false,
            interactionInsightsEnabled = true
        )

        viewModel.updatePrivacySettings(newSettings)
        val updated = viewModel.uiState.value.privacySettings
        assertTrue(updated.isIncognito)
        assertFalse(updated.showOnlineStatus)
        assertFalse(updated.showReadReceipts)
    }

    @Test
    fun testBlockUserRemovesFromDeckAndMatches() {
        val candidate = testProfile()
        viewModel.likeProfile(candidate, isSuperLike = false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.blockUser(candidate.id)

        val state = viewModel.uiState.value
        assertFalse(state.discoveryDeck.any { it.id == candidate.id })
        assertFalse(state.matches.any { it.user.id == candidate.id })
    }

    @Test
    fun testLudoRequiresPremium() {
        // Free user tries to enter
        assertFalse(viewModel.uiState.value.entitlements.isPremium)
        viewModel.openLudoGame()
        assertFalse(viewModel.uiState.value.isLudoActive)
        assertNull(viewModel.uiState.value.ludoRoom)
        assertTrue(viewModel.uiState.value.showPremiumStore)

        // Upgrade to premium
        viewModel.purchaseSubscription(isYearly = true)
        assertTrue(viewModel.uiState.value.entitlements.isPremium)

        // Enter Ludo — a fresh room is created (no pre-seeded game state)
        viewModel.openLudoGame()
        assertTrue(viewModel.uiState.value.isLudoActive)
        assertNotNull(viewModel.uiState.value.ludoRoom)
        val room = viewModel.uiState.value.ludoRoom!!
        assertTrue(room.chatMessages.isEmpty())
        assertTrue(room.player1.tokens.all { it.isHome })
        assertTrue(room.player2.tokens.all { it.isHome })
    }

    @Test
    fun testLudoDiceRollAndTokenMove() {
        viewModel.purchaseSubscription(isYearly = true)
        viewModel.openLudoGame()

        // Roll dice
        viewModel.rollLudoDice()
        testDispatcher.scheduler.advanceUntilIdle()

        val room = viewModel.uiState.value.ludoRoom!!
        assertTrue(room.diceValue in 1..6)
        assertTrue(room.chatMessages.any { it.isSystem })

        // Move token 0
        if (room.canMoveToken) {
            val scoreBefore = room.player2.score
            viewModel.moveLudoToken(0)
            val updatedRoom = viewModel.uiState.value.ludoRoom!!
            assertTrue(updatedRoom.player2.score >= scoreBefore)
        }
    }

    @Test
    fun testClubCreateAndLeaveRules() {
        // Start with no club membership
        assertNull(viewModel.uiState.value.activeClubId)

        // Create a club -> becomes owner
        viewModel.createClub(
            name = "Test Club",
            description = "A club created in tests",
            category = "Gaming",
            logoEmoji = "🎮"
        )
        val myClub = viewModel.uiState.value.clubs.first()
        assertEquals(myClub.id, viewModel.uiState.value.activeClubId)
        assertEquals(1, myClub.members.size)
        assertEquals("OWNER", myClub.members.first().role)

        // Cannot belong to two clubs at once (PRD Section 15)
        viewModel.createClub("Second Club", "Should be blocked", "Music", "🎵")
        assertEquals(1, viewModel.uiState.value.clubs.size)

        // Leave the club
        viewModel.leaveClub(myClub.id)
        assertNull(viewModel.uiState.value.activeClubId)
        assertTrue(viewModel.uiState.value.clubs.first().members.isEmpty())
    }

    @Test
    fun testStickerPackPurchase() {
        val unownedPack = viewModel.uiState.value.stickerPacks.first { !it.isOwned }
        assertFalse(unownedPack.isOwned)

        viewModel.purchaseStickerPack(unownedPack.id)

        val updatedPack = viewModel.uiState.value.stickerPacks.first { it.id == unownedPack.id }
        assertTrue(updatedPack.isOwned)
    }

    @Test
    fun testThemeModeChange() {
        assertEquals(com.example.model.AppThemeMode.LIGHT, viewModel.uiState.value.themeMode)
        viewModel.setThemeMode(com.example.model.AppThemeMode.DARK)
        assertEquals(com.example.model.AppThemeMode.DARK, viewModel.uiState.value.themeMode)
        viewModel.setThemeMode(com.example.model.AppThemeMode.SYSTEM)
        assertEquals(com.example.model.AppThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
    }

    @Test
    fun testPersonalInformationToggleAndUpdate() {
        assertFalse(viewModel.uiState.value.showPersonalInformationSheet)
        viewModel.togglePersonalInformation(true)
        assertTrue(viewModel.uiState.value.showPersonalInformationSheet)

        viewModel.updatePersonalInformation(
            height = "180 cm",
            occupation = "Senior Engineer",
            education = "Master's Degree",
            intent = "Long-term relationship",
            interests = listOf("Music", "Travel"),
            fieldVisibility = mapOf("height" to com.example.model.VisibilityLevel.EVERYONE)
        )

        assertFalse(viewModel.uiState.value.showPersonalInformationSheet)
        assertEquals("180 cm", viewModel.uiState.value.userProfile.height)
        assertEquals("Senior Engineer", viewModel.uiState.value.userProfile.occupation)
        assertEquals("Master's Degree", viewModel.uiState.value.userProfile.educationLevel)
        assertEquals(listOf("Music", "Travel"), viewModel.uiState.value.userProfile.interests)
    }

    @Test
    fun testSendLudoMessageWithReply() {
        viewModel.purchaseSubscription(isYearly = true)
        viewModel.openLudoGame()

        viewModel.sendLudoChatMessage(
            text = "Nice move!",
            replyToText = "Opponent rolled a 6",
            replyToSender = "Opponent"
        )

        val lastMsg = viewModel.uiState.value.ludoRoom!!.chatMessages.last()
        assertEquals("Nice move!", lastMsg.text)
        assertEquals("Opponent rolled a 6", lastMsg.replyToText)
        assertEquals("Opponent", lastMsg.replyToSender)
    }
}
