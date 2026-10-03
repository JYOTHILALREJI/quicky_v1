package com.example.game

import com.example.model.LudoMatch
import com.example.model.LudoPhase
import com.example.model.LudoToken

/**
 * ============================================================================
 * LUDO ENGINE — Quicky v2.1 §3.2 (full rewrite)
 *
 * Pure, side-effect-free rules engine for a 4-player Ludo Arena match
 * (RED → GREEN → YELLOW → BLUE, clockwise). Both the local solo-vs-bots
 * mode and the server-authoritative Edge Functions (supabase/functions/
 * apply_ludo_move) implement THESE exact rules — keep them in sync.
 *
 * RULES ENFORCED
 *  1. Token release: a token leaves the home yard only on a roll of 6.
 *  2. Extra turn on a 6; three consecutive 6s forfeit the turn (the third
 *     six is not played).
 *  3. Capture: landing on a cell occupied by exactly ONE opponent token
 *     sends it back to its yard. Landing on 2+ opponent tokens is a
 *     BLOCK — the move (and passing through it) is illegal.
 *  4. Safe cells: the four star cells + the four colored start cells —
 *     no capture can happen there.
 *  5. Home column: entered only past the player's home entry; the final
 *     home cell needs an EXACT roll — overshooting is illegal.
 *  6. Extra turn is also granted after a capture or after finishing a
 *     token (classic rules).
 *  7. Win: first player to bring all 4 tokens home wins the match.
 *
 * TRACK GEOMETRY — 15x15 grid, 52-cell clockwise main path.
 * Each seat s starts at TRACK[s * 13] and enters its private home column
 * after 51 main-track steps, then 5 colored cells + the center = step 57.
 * ============================================================================
 */
object LudoEngine {

    // --------------------------------------------------------------
    // Board geometry
    // --------------------------------------------------------------

    /** 52 main-track cells as (col, row) on the 15x15 grid, clockwise. */
    val TRACK: List<Pair<Int, Int>> = buildList {
        // Top-left arm: row 6 heading right (Red's approach)
        add(1 to 6); add(2 to 6); add(3 to 6); add(4 to 6); add(5 to 6)
        // Left column going up
        add(6 to 5); add(6 to 4); add(6 to 3); add(6 to 2); add(6 to 1); add(6 to 0)
        // Top middle
        add(7 to 0)
        // Right column going down
        add(8 to 0); add(8 to 1); add(8 to 2); add(8 to 3); add(8 to 4); add(8 to 5)
        // Top-right arm: row 6 heading right (Green's run)
        add(9 to 6); add(10 to 6); add(11 to 6); add(12 to 6); add(13 to 6); add(14 to 6)
        // Right middle
        add(14 to 7)
        // Bottom-right arm: row 8 heading left (Yellow's run)
        add(14 to 8); add(13 to 8); add(12 to 8); add(11 to 8); add(10 to 8); add(9 to 8)
        // Right column going down
        add(8 to 9); add(8 to 10); add(8 to 11); add(8 to 12); add(8 to 13); add(8 to 14)
        // Bottom middle
        add(7 to 14)
        // Left column going up
        add(6 to 14); add(6 to 13); add(6 to 12); add(6 to 11); add(6 to 10); add(6 to 9)
        // Bottom-left arm: row 8 heading left (Blue's run)
        add(5 to 8); add(4 to 8); add(3 to 8); add(2 to 8); add(1 to 8); add(0 to 8)
        // Left middle + closing corner
        add(0 to 7); add(0 to 6)
    }.also { check(it.size == 52) { "Ludo track must be 52 cells, was ${it.size}" } }

    /** Seat → start index on the main track (13 cells apart, clockwise). */
    private val START_INDEX = intArrayOf(0, 13, 26, 39)

    /** Seat → the 5 colored home-column cells as (col, row). */
    val HOME_COLUMNS: List<List<Pair<Int, Int>>> = listOf(
        listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),      // RED    → from the left middle
        listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),      // GREEN  → from the top middle
        listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7),  // YELLOW → from the right middle
        listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9)   // BLUE   → from the bottom middle
    )

    /** Yards (6x6 corner blocks) as (col, row) origins: seat → (startCol, startRow). */
    val YARD_ORIGINS: List<Pair<Int, Int>> = listOf(0 to 0, 9 to 0, 9 to 9, 0 to 9)

    /** Absolute track indices of the four colored START cells (safe). */
    val START_CELLS: Set<Int> = START_INDEX.toSet()

    /** Absolute track indices of the four STAR cells (safe). */
    val STAR_CELLS: Set<Int> = setOf(8, 21, 34, 47)

    /** All safe absolute track indices (stars + starts). */
    val SAFE_CELLS: Set<Int> = START_CELLS + STAR_CELLS

    const val FINISH_STEP = 57
    const val HOME_ENTRY_STEP = 51 // last step on the shared main track

    /** Seat → ARGB color (mirrors LudoColor for Canvas drawing). */
    val seatColors: LongArray = longArrayOf(
        com.example.model.LudoColor.RED.colorHex,
        com.example.model.LudoColor.GREEN.colorHex,
        com.example.model.LudoColor.YELLOW.colorHex,
        com.example.model.LudoColor.BLUE.colorHex
    )

    /** Yard token slot centers (cell units, relative to the yard origin). */
    val YARD_SLOT_OFFSETS: List<Pair<Float, Float>> = listOf(
        1.75f to 1.75f, 3.75f to 1.75f, 1.75f to 3.75f, 3.75f to 3.75f
    )

    // --------------------------------------------------------------
    // Position math
    // --------------------------------------------------------------

    /** Absolute track index for a token of [seat] currently at [stepCount] (1..51). */
    fun absoluteIndex(seat: Int, stepCount: Int): Int =
        (START_INDEX[seat.coerceIn(0, 3)] + stepCount - 1) % 52

    /** Grid (col, row) of a token — yard, track, home column or center when finished. */
    fun cellForStep(seat: Int, stepCount: Int): Pair<Int, Int> = when {
        stepCount <= 0 -> YARD_ORIGINS[seat.coerceIn(0, 3)].let { (c, r) -> c + 2 to r + 2 }
        stepCount <= HOME_ENTRY_STEP -> TRACK[absoluteIndex(seat, stepCount)]
        stepCount < FINISH_STEP -> HOME_COLUMNS[seat.coerceIn(0, 3)][stepCount - 52]
        else -> 7 to 7 // center home
    }

    /** All main-track absolute cells a token of [seat] passes through moving to [newStep]. */
    private fun pathAbsoluteCells(seat: Int, fromStep: Int, newStep: Int): List<Int> =
        (maxOf(fromStep, 0) + 1..newStep)
            .filter { it in 1..HOME_ENTRY_STEP }
            .map { absoluteIndex(seat, it) }

    // --------------------------------------------------------------
    // Occupancy helpers
    // --------------------------------------------------------------

    /** Opponent tokens (excluding players[[seat]]) occupying [absCell] on the main track. */
    private fun opponentsOn(match: LudoMatch, seat: Int, absCell: Int): Int =
        match.players.asSequence()
            .mapIndexed { playerIndex, player -> playerIndex to player }
            .filter { (playerIndex, _) -> playerIndex != seat }
            .flatMap { (playerIndex, player) -> player.tokens.map { playerIndex to it } }
            .count { (playerIndex, token) ->
                token.isOnMainTrack && absoluteIndex(playerIndex, token.stepCount) == absCell
            }

    // --------------------------------------------------------------
    // Legality
    // --------------------------------------------------------------

    /**
     * True when [token] of the current player can legally move [dice] steps
     * (release-on-6, exact home entry, block rule included).
     */
    fun isMoveLegal(match: LudoMatch, token: LudoToken, dice: Int): Boolean {
        if (dice !in 1..6) return false
        if (token.isFinished) return false

        val seat = match.turnIndex
        val from = token.stepCount
        val newStep = if (from == 0) 1 else from + dice

        // Exact roll to finish — overshoot into home is illegal.
        if (newStep > FINISH_STEP) return false

        // Block rule: cannot PASS THROUGH (or land on) 2+ opponent tokens.
        val absCells = if (from == 0) {
            // Release: only the landing cell matters (the start cell).
            listOf(absoluteIndex(seat, 1))
        } else {
            pathAbsoluteCells(seat, from, newStep)
        }
        return absCells.all { opponentsOn(match, seat, it) < 2 }
    }

    /** Token ids of the current player that can legally move with [match.diceValue]. */
    fun legalMoves(match: LudoMatch): List<Int> {
        val dice = match.diceValue ?: return emptyList()
        val current = match.currentPlayer
        return current.tokens.filter { isMoveLegal(match, it, dice) }.map { it.id }
    }

    /** Does the current player hold ANY legal move for [dice]? */
    fun hasAnyLegalMove(match: LudoMatch, dice: Int): Boolean =
        match.currentPlayer.tokens.any { isMoveLegal(match, it, dice) }

    // --------------------------------------------------------------
    // Roll application
    // --------------------------------------------------------------

    /**
     * Applies a dice roll: stores the value, enforces the three-six rule and
     * switches to AWAITING_MOVE — or passes the turn when no legal move
     * exists. Returns the updated match.
     */
    fun applyRoll(match: LudoMatch, dice: Int): LudoMatch {
        require(dice in 1..6) { "Dice out of range: $dice" }
        if (match.phase == LudoPhase.FINISHED) return match

        val current = match.currentPlayer
        val newConsecutive = if (dice == 6) match.consecutiveSixes + 1 else 0

        // Three consecutive 6s → forfeit the turn (the third six is not played).
        if (dice == 6 && newConsecutive >= 3) {
            val forfeited = match.copy(diceValue = dice, consecutiveSixes = 0)
            val advanced = advanceTurn(forfeited)
            return advanced.copy(
                statusText = "${current.name} rolled a third 6 — turn forfeited!"
            )
        }

        val rolled = match.copy(
            diceValue = dice,
            consecutiveSixes = newConsecutive,
            phase = LudoPhase.AWAITING_MOVE
        )

        return if (hasAnyLegalMove(rolled, dice)) {
            rolled.copy(
                statusText = "${current.name} rolled $dice — pick a token" +
                        if (dice == 6) " (extra roll after)" else ""
            )
        } else {
            // No legal move: the dice is shown, then the turn passes.
            advanceTurn(rolled).copy(
                statusText = "${current.name} rolled $dice — no legal moves, turn passes."
            )
        }
    }

    // --------------------------------------------------------------
    // Move application
    // --------------------------------------------------------------

    /** Outcome of applying a move — powers haptics + status text. */
    data class MoveResult(
        val match: LudoMatch,
        val capturedOpponent: Boolean = false,
        val finishedToken: Boolean = false,
        val extraTurnGranted: Boolean = false
    )

    /**
     * Applies the chosen [tokenId] move for the current player. The caller
     * must have verified legality (dice present, phase AWAITING_MOVE).
     * Returns [MoveResult] with the next match state.
     */
    fun applyMove(match: LudoMatch, tokenId: Int): MoveResult {
        val dice = match.diceValue ?: return MoveResult(match)
        val token = match.currentPlayer.tokens.find { it.id == tokenId }
            ?: return MoveResult(match)
        if (!isMoveLegal(match, token, dice)) return MoveResult(match)

        val seat = match.turnIndex
        val from = token.stepCount
        val newStep = if (from == 0) 1 else from + dice
        val landedAbs = if (newStep <= HOME_ENTRY_STEP) absoluteIndex(seat, newStep) else -1

        var capturedOpponent = false
        var updatedPlayers = match.players

        // --- Capture: single opponent token on a non-safe landing cell ---
        if (landedAbs >= 0 && landedAbs !in SAFE_CELLS) {
            updatedPlayers = updatedPlayers.mapIndexed { playerIndex, player ->
                if (playerIndex == seat) player
                else {
                    val capturedIds = player.tokens
                        .filter {
                            it.isOnMainTrack &&
                                    absoluteIndex(playerIndex, it.stepCount) == landedAbs
                        }
                        .map { it.id }
                    if (capturedIds.size == 1) {
                        capturedOpponent = true
                        player.copy(tokens = player.tokens.map {
                            if (it.id in capturedIds) LudoToken(id = it.id, stepCount = 0) else it
                        })
                    } else player
                }
            }
        }

        // --- Move the token itself ---
        val finishedToken = newStep == FINISH_STEP
        updatedPlayers = updatedPlayers.mapIndexed { playerIndex, player ->
            if (playerIndex == seat) player.copy(
                tokens = player.tokens.map {
                    if (it.id == tokenId) LudoToken(id = it.id, stepCount = newStep) else it
                }
            ) else player
        }

        val mover = updatedPlayers[seat]
        val winner = if (mover.hasWon) mover.id else null
        val extraTurn = winner == null && (dice == 6 || capturedOpponent || finishedToken)

        val afterMove = match.copy(
            players = updatedPlayers,
            diceValue = null,
            phase = if (winner != null) LudoPhase.FINISHED else LudoPhase.AWAITING_ROLL,
            winnerId = winner ?: match.winnerId,
            consecutiveSixes = if (dice == 6) match.consecutiveSixes else 0
        )

        val nextMatch = when {
            winner != null -> afterMove.copy(
                statusText = "🏆 ${mover.name} brought all 4 tokens home — victory!"
            )
            extraTurn -> afterMove.copy(
                statusText = "${mover.name}" + when {
                    capturedOpponent -> " captured a token — extra roll!"
                    finishedToken -> " sent a token home — extra roll!"
                    else -> " rolled a 6 — extra roll!"
                }
            )
            else -> advanceTurn(afterMove).copy(
                statusText = buildString {
                    append(mover.name)
                    append(" moved")
                    if (capturedOpponent) append(" and captured a token!")
                    else if (finishedToken) append(" a token home!")
                    else append('.')
                }
            )
        }

        return MoveResult(
            match = nextMatch,
            capturedOpponent = capturedOpponent,
            finishedToken = finishedToken,
            extraTurnGranted = extraTurn && winner == null
        )
    }

    /** Moves the turn to the next seat clockwise, clearing per-turn state. */
    fun advanceTurn(match: LudoMatch): LudoMatch {
        if (match.phase == LudoPhase.FINISHED) return match
        val nextIndex = (match.turnIndex + 1) % match.players.size
        return match.copy(
            turnIndex = nextIndex,
            diceValue = null,
            phase = LudoPhase.AWAITING_ROLL,
            consecutiveSixes = 0
        )
    }

    // --------------------------------------------------------------
    // Bot AI (solo mode)
    // --------------------------------------------------------------

    /**
     * Picks the bot's move with a light heuristic:
     * capture > finish a token > release from yard > farthest advance.
     */
    fun pickBotMove(match: LudoMatch): Int? {
        val dice = match.diceValue ?: return null
        val seat = match.turnIndex
        val legal = match.currentPlayer.tokens.filter { isMoveLegal(match, it, dice) }
        if (legal.isEmpty()) return null

        // 1) Capture if possible.
        legal.firstOrNull { token ->
            val newStep = if (token.stepCount == 0) 1 else token.stepCount + dice
            val landed = if (newStep <= HOME_ENTRY_STEP) absoluteIndex(seat, newStep) else -1
            landed >= 0 && landed !in SAFE_CELLS && opponentsOn(match, seat, landed) == 1
        }?.let { return it.id }

        // 2) Finish a token.
        legal.firstOrNull { (if (it.stepCount == 0) 1 else it.stepCount + dice) == FINISH_STEP }
            ?.let { return it.id }

        // 3) Release from the yard (only legal on a 6 anyway).
        legal.firstOrNull { it.stepCount == 0 }?.let { return it.id }

        // 4) Advance the farthest token.
        return legal.maxByOrNull { it.stepCount }?.id
    }
}
