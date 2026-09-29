package com.example

import com.example.data.MockDataProvider
import com.example.model.DiscoveryFilter
import com.example.model.PrivacySettings
import com.example.model.TruthOrDarePrompt
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

    @Test
    fun testInitialState() {
        val state = viewModel.uiState.value
        assertEquals(SparkTab.DISCOVER, state.currentTab)
        assertTrue(state.discoveryDeck.isNotEmpty())
        assertTrue(state.matches.isNotEmpty())
        assertEquals(4, state.interactionInsights.size)
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
    fun testPassAndRewind() {
        val initialDeck = viewModel.uiState.value.discoveryDeck
        val firstCandidate = initialDeck.first()

        // Pass candidate
        viewModel.passProfile(firstCandidate)
        val deckAfterPass = viewModel.uiState.value.discoveryDeck
        assertFalse(deckAfterPass.contains(firstCandidate))
        assertEquals(1, viewModel.uiState.value.passedHistory.size)

        // Rewind
        val rewindsBefore = viewModel.uiState.value.entitlements.rewindsRemaining
        viewModel.rewindLastPass()
        val deckAfterRewind = viewModel.uiState.value.discoveryDeck
        assertEquals(firstCandidate.id, deckAfterRewind.first().id)
        assertEquals(rewindsBefore - 1, viewModel.uiState.value.entitlements.rewindsRemaining)
    }

    @Test
    fun testLikeTriggersMatch() {
        val initialDeck = viewModel.uiState.value.discoveryDeck
        val candidate = initialDeck.first()

        viewModel.likeProfile(candidate, isSuperLike = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.matchCelebration)
        assertEquals(candidate.id, state.matchCelebration?.id)
        assertTrue(state.matches.any { it.user.id == candidate.id })
    }

    @Test
    fun testSendTruthOrDareInChat() {
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
        val match = viewModel.uiState.value.matches.first()
        val userId = match.user.id

        viewModel.blockUser(userId)

        val state = viewModel.uiState.value
        assertFalse(state.discoveryDeck.any { it.id == userId })
        assertFalse(state.matches.any { it.user.id == userId })
    }

    @Test
    fun testLudoRequiresPremium() {
        // Free user tries to enter
        assertFalse(viewModel.uiState.value.entitlements.isPremium)
        viewModel.openLudoGame()
        assertFalse(viewModel.uiState.value.isLudoActive)
        assertTrue(viewModel.uiState.value.showPremiumStore)

        // Upgrade to premium
        viewModel.purchaseSubscription(isYearly = true)
        assertTrue(viewModel.uiState.value.entitlements.isPremium)

        // Enter Ludo
        viewModel.openLudoGame()
        assertTrue(viewModel.uiState.value.isLudoActive)
    }

    @Test
    fun testLudoDiceRollAndTokenMove() {
        viewModel.purchaseSubscription(isYearly = true)
        viewModel.openLudoGame()

        // Roll dice
        viewModel.rollLudoDice()
        testDispatcher.scheduler.advanceUntilIdle()

        val room = viewModel.uiState.value.ludoRoom
        assertTrue(room.diceValue in 1..6)
        assertTrue(room.chatMessages.any { it.isSystem })

        // Move token 0
        if (room.canMoveToken) {
            val scoreBefore = room.player2.score
            viewModel.moveLudoToken(0)
            val updatedRoom = viewModel.uiState.value.ludoRoom
            assertTrue(updatedRoom.player2.score >= scoreBefore)
        }
    }

    @Test
    fun testClubMembershipAndCapacityRules() {
        // User starts in "club_gamers"
        assertEquals("club_gamers", viewModel.uiState.value.activeClubId)

        // Try to join another club while already in one -> blocked (PRD Section 15)
        viewModel.joinClub("club_foodies")
        assertEquals("club_gamers", viewModel.uiState.value.activeClubId)

        // Leave club
        viewModel.leaveClub("club_gamers")
        assertNull(viewModel.uiState.value.activeClubId)

        // Try to join full club (15/15 members) -> blocked (PRD Section 13)
        viewModel.joinClub("club_music")
        assertNull(viewModel.uiState.value.activeClubId)

        // Join valid club
        viewModel.joinClub("club_foodies")
        assertEquals("club_foodies", viewModel.uiState.value.activeClubId)
    }

    @Test
    fun testStickerPackPurchase() {
        val unownedPack = viewModel.uiState.value.stickerPacks.first { !it.isOwned }
        assertFalse(unownedPack.isOwned)

        viewModel.purchaseStickerPack(unownedPack.id)

        val updatedPack = viewModel.uiState.value.stickerPacks.first { it.id == unownedPack.id }
        assertTrue(updatedPack.isOwned)
    }
}
