package com.example.data

import com.example.model.LudoChatMessage
import com.example.model.LudoMatch
import com.example.model.LudoPhase
import com.example.model.LudoPlayer
import com.example.model.LudoToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================================
 * LUDO MATCH REPOSITORY — Quicky v2.1 §3.2.4 (online 4-player)
 *
 * Persists Ludo Arena matches to Supabase:
 *
 *   ludo_matches        (id, host_id, status, created_at, winner_id)
 *   ludo_players        (match_id, user_id, name, seat, is_bot)
 *   ludo_game_state     (match_id, turn, dice_value, board_state jsonb, updated_at)
 *   ludo_moves          (match_id, user_id, from_cell, to_cell, dice, created_at)
 *   ludo_chat_messages  (match_id, sender_id, sender_name, text, …)
 *
 * Dice rolls and moves are intended to flow through the server-authoritative
 * Edge Functions (`roll_ludo_dice`, `apply_ludo_move`). When those functions
 * are not deployed (e.g. local dev), callers fall back to the client-side
 * engine and push the resulting state through `updateBoardState` so
 * two-device play still works — see SparkViewModel.
 * ============================================================================
 */
object LudoMatchRepository {

    private fun isConfigured() = SupabaseRepository.isConfigured()

    // --------------------------------------------------------------
    // JSON (de)serialization — matches the Edge Function contract
    // --------------------------------------------------------------

    /** Public encoder — member extensions aren't importable, so expose this. */
    fun encodeBoardState(match: LudoMatch): JSONObject = match.toJson()

    private fun LudoMatch.toJson(): JSONObject = JSONObject().apply {
        put("players", JSONArray().apply {
            players.forEach { player ->
                put(
                    JSONObject()
                        .put("id", player.id)
                        .put("name", player.name)
                        .put("seat", player.seat)
                        .put("is_bot", player.isBot)
                        .put("tokens", JSONArray(player.tokens.map { it.stepCount }))
                )
            }
        })
        put("turn_index", turnIndex)
        put("dice_value", diceValue ?: JSONObject.NULL)
        put("phase", phase.name)
        put("consecutive_sixes", consecutiveSixes)
        put("winner_id", winnerId ?: JSONObject.NULL)
        put("status_text", statusText)
    }

    fun fromJson(matchId: String, json: JSONObject, chat: List<LudoChatMessage> = emptyList()): LudoMatch {
        val playersJson = json.optJSONArray("players") ?: JSONArray()
        val players = buildList {
            for (i in 0 until playersJson.length()) {
                val p = playersJson.optJSONObject(i) ?: continue
                val seat = p.optInt("seat", 0)
                val tokensJson = p.optJSONArray("tokens") ?: JSONArray()
                add(
                    LudoPlayer(
                        id = p.optString("id"),
                        name = p.optString("name").ifBlank { "Player" },
                        avatarRes = avatarFor(p.optString("id"), seat),
                        seat = seat,
                        isBot = p.optBoolean("is_bot", false),
                        tokens = List(4) { tokenIndex ->
                            LudoToken(
                                id = tokenIndex,
                                stepCount = tokensJson.optInt(tokenIndex.coerceIn(0, 3))
                            )
                        }
                    )
                )
            }
        }
        val phase = when (json.optString("phase")) {
            LudoPhase.AWAITING_MOVE.name -> LudoPhase.AWAITING_MOVE
            LudoPhase.FINISHED.name -> LudoPhase.FINISHED
            else -> LudoPhase.AWAITING_ROLL
        }
        return LudoMatch(
            id = matchId,
            mode = com.example.model.LudoMode.ONLINE,
            players = players,
            turnIndex = json.optInt("turn_index", 0),
            diceValue = if (json.isNull("dice_value")) null else json.optInt("dice_value"),
            phase = phase,
            consecutiveSixes = json.optInt("consecutive_sixes", 0),
            winnerId = json.optString("winner_id").takeIf { it.isNotBlank() && it != "null" },
            chatMessages = chat,
            statusText = json.optString("status_text").ifBlank { "Roll the dice to start" },
            localUserId = currentUserId()
        )
    }

    /** Local avatar mapping — remote players get a bundled portrait. */
    private fun avatarFor(playerId: String, seat: Int): Int = when (seat) {
        0 -> com.example.R.drawable.img_onboarding_hero
        1 -> com.example.R.drawable.img_profile_sarah
        2 -> com.example.R.drawable.img_profile_alex
        else -> com.example.R.drawable.img_profile_alex_1790673648998
    }

    // --------------------------------------------------------------
    // Match lifecycle
    // --------------------------------------------------------------

    /** Generates a short human-shareable match code, e.g. "LA7K2QD". */
    fun newMatchCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val suffix = buildString {
            repeat(5) { append(alphabet[(Math.random() * alphabet.length).toInt()]) }
        }
        return "LA$suffix"
    }

    /**
     * Host flow: creates the match row, takes seat 0 and seeds the fresh
     * board state. Returns the match code.
     */
    suspend fun createOnlineMatch(
        hostUserId: String,
        hostName: String,
        accessToken: String?
    ): String = withContext(Dispatchers.IO) {
        val code = newMatchCode()
        val fresh = JSONObject()
            .put("players", JSONArray().put(
                JSONObject()
                    .put("id", hostUserId)
                    .put("name", hostName)
                    .put("seat", 0)
                    .put("is_bot", false)
                    .put("tokens", JSONArray(listOf(0, 0, 0, 0)))
            ))
            .put("turn_index", 0)
            .put("dice_value", JSONObject.NULL)
            .put("phase", "AWAITING_ROLL")
            .put("consecutive_sixes", 0)
            .put("winner_id", JSONObject.NULL)
            .put("status_text", "Waiting for players — roll to start once seats fill")

        SupabaseClient.rest(
            method = "POST",
            path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_MATCHES}",
            body = JSONObject()
                .put("id", code)
                .put("host_id", hostUserId)
                .put("status", "IN_PROGRESS")
                .toString(),
            accessToken = accessToken,
            prefer = "return=minimal"
        )
        SupabaseClient.rest(
            method = "POST",
            path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_PLAYERS}",
            body = JSONObject()
                .put("match_id", code)
                .put("user_id", hostUserId)
                .put("name", hostName)
                .put("seat", 0)
                .put("is_bot", false)
                .toString(),
            accessToken = accessToken,
            prefer = "return=minimal"
        )
        SupabaseClient.rest(
            method = "POST",
            path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_GAME_STATE}",
            body = JSONObject()
                .put("match_id", code)
                .put("turn", 0)
                .put("dice_value", JSONObject.NULL)
                .put("board_state", fresh)
                .toString(),
            accessToken = accessToken,
            prefer = "return=minimal"
        )
        code
    }

    /**
     * Join flow: claims the lowest free seat for this user. Fails (throws)
     * when the match does not exist or is already full / completed.
     */
    suspend fun joinOnlineMatch(
        matchCode: String,
        userId: String,
        playerName: String,
        accessToken: String?
    ): Unit = withContext(Dispatchers.IO) {
        val state = fetchRawState(matchCode)
        val playersJson = state.optJSONObject("board_state")?.optJSONArray("players") ?: JSONArray()
        val taken = buildSet {
            for (i in 0 until playersJson.length()) {
                add(playersJson.optJSONObject(i)?.optInt("seat", -1) ?: -1)
            }
        }
        val freeSeat = (0..3).firstOrNull { it !in taken }
            ?: throw IllegalStateException("Match $matchCode is already full.")

        SupabaseClient.rest(
            method = "POST",
            path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_PLAYERS}",
            body = JSONObject()
                .put("match_id", matchCode)
                .put("user_id", userId)
                .put("name", playerName)
                .put("seat", freeSeat)
                .put("is_bot", false)
                .toString(),
            accessToken = accessToken,
            prefer = "return=minimal"
        )

        // Mirror the seat into the board state so every client sees it.
        playersJson.put(
            JSONObject()
                .put("id", userId)
                .put("name", playerName)
                .put("seat", freeSeat)
                .put("is_bot", false)
                .put("tokens", JSONArray(listOf(0, 0, 0, 0)))
        )
        state.optJSONObject("board_state")?.put("players", playersJson)
        updateBoardState(matchCode, state.getJSONObject("board_state"), accessToken)
    }

    // --------------------------------------------------------------
    // State polling (Realtime substitute — 1.2s cadence in the ViewModel)
    // --------------------------------------------------------------

    /** Raw `ludo_game_state` row for [matchCode] (throws when missing). */
    private suspend fun fetchRawState(matchCode: String, accessToken: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_GAME_STATE}",
                query = mapOf("select" to "*", "match_id" to "eq.$matchCode", "limit" to "1"),
                accessToken = accessToken
            )
            SupabaseClient.parseArray(raw).optJSONObject(0)
                ?: throw IllegalStateException("No ludo_game_state row for $matchCode")
        }

    /** Full match snapshot for a code — state + chat (for polling). */
    suspend fun fetchMatch(matchCode: String, accessToken: String? = null): LudoMatch =
        withContext(Dispatchers.IO) {
            val row = fetchRawState(matchCode, accessToken)
            val chat = fetchChat(matchCode, accessToken)
            fromJson(matchCode, row.optJSONObject("board_state") ?: JSONObject(), chat)
        }

    /** Pushes a new board state (fallback write path when Edge Functions are absent). */
    suspend fun updateBoardState(
        matchCode: String,
        boardState: JSONObject,
        accessToken: String?
    ): Unit = withContext(Dispatchers.IO) {
        SupabaseClient.rest(
            method = "PATCH",
            path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_GAME_STATE}",
            query = mapOf("match_id" to "eq.$matchCode"),
            body = JSONObject()
                .put("board_state", boardState)
                .put("updated_at", JSONObject.NULL)
                .toString(),
            accessToken = accessToken
        ).let { /* response ignored */ }
    }

    // --------------------------------------------------------------
    // Server-authoritative actions (Edge Functions)
    // --------------------------------------------------------------

    /**
     * Fills every empty seat of [matchCode] with a bot (host convenience so
     * a match can start while humans trickle in). Returns the seat count
     * after filling.
     */
    suspend fun fillEmptySeatsWithBots(
        matchCode: String,
        botNames: List<String>,
        accessToken: String?
    ): Int = withContext(Dispatchers.IO) {
        val state = fetchRawState(matchCode)
        val board = state.optJSONObject("board_state") ?: JSONObject()
        val playersJson = board.optJSONArray("players") ?: JSONArray()
        val taken = buildSet {
            for (i in 0 until playersJson.length()) {
                add(playersJson.optJSONObject(i)?.optInt("seat", -1) ?: -1)
            }
        }
        var nameIndex = 0
        for (seat in 0..3) {
            if (seat in taken) continue
            val botName = botNames.getOrElse(nameIndex++) { "Bot ${seat + 1}" }
            playersJson.put(
                JSONObject()
                    .put("id", "bot_seat_$seat")
                    .put("name", "$botName (Bot)")
                    .put("seat", seat)
                    .put("is_bot", true)
                    .put("tokens", JSONArray(listOf(0, 0, 0, 0)))
            )
            runCatching {
                SupabaseClient.rest(
                    method = "POST",
                    path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_PLAYERS}",
                    body = JSONObject()
                        .put("match_id", matchCode)
                        .put("user_id", "bot_seat_$seat")
                        .put("name", "$botName (Bot)")
                        .put("seat", seat)
                        .put("is_bot", true)
                        .toString(),
                    accessToken = accessToken,
                    prefer = "return=minimal"
                )
            }
        }
        board.put("players", playersJson)
        board.put("status_text", "All seats filled — roll to start!")
        updateBoardState(matchCode, board, accessToken)
        playersJson.length()
    }

    /** Calls the `roll_ludo_dice` Edge Function; returns the rolled value. */
    suspend fun rollDiceRemote(matchCode: String, accessToken: String?): Int =
        withContext(Dispatchers.IO) {
            val response = SupabaseClient.functions(
                name = "roll_ludo_dice",
                payload = mapOf("match_id" to matchCode),
                accessToken = accessToken
            )
            response.optInt("dice", 0).also { check(it in 1..6) { "Invalid dice from server: $it" } }
        }

    /**
     * Calls the `apply_ludo_move` Edge Function with the client's candidate
     * move; the server validates it against the SAME rules as the local
     * engine and returns the authoritative new board state.
     */
    suspend fun applyMoveRemote(
        matchCode: String,
        tokenId: Int,
        accessToken: String?
    ): JSONObject = withContext(Dispatchers.IO) {
        SupabaseClient.functions(
            name = "apply_ludo_move",
            payload = mapOf("match_id" to matchCode, "token_id" to tokenId),
            accessToken = accessToken
        )
    }

    /** Appends a move to the server-side audit log (best effort). */
    suspend fun logMove(
        matchCode: String,
        userId: String,
        fromCell: Int,
        toCell: Int,
        dice: Int,
        accessToken: String?
    ): Unit = withContext(Dispatchers.IO) {
        runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_MOVES}",
                body = JSONObject()
                    .put("match_id", matchCode)
                    .put("user_id", userId)
                    .put("from_cell", fromCell)
                    .put("to_cell", toCell)
                    .put("dice", dice)
                    .toString(),
                accessToken = accessToken,
                prefer = "return=minimal"
            )
        }.let { /* audit is best-effort */ }
    }

    // --------------------------------------------------------------
    // Room chat
    // --------------------------------------------------------------

    suspend fun sendChatMessage(
        matchCode: String,
        senderId: String,
        senderName: String,
        text: String,
        stickerEmoji: String? = null,
        voiceDurationSeconds: Int? = null,
        accessToken: String?
    ): Unit = withContext(Dispatchers.IO) {
        SupabaseClient.rest(
            method = "POST",
            path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_CHAT_MESSAGES}",
            body = JSONObject()
                .put("match_id", matchCode)
                .put("sender_id", senderId)
                .put("sender_name", senderName)
                .put("text", text)
                .put("sticker_emoji", stickerEmoji ?: JSONObject.NULL)
                .put("voice_duration_seconds", voiceDurationSeconds ?: JSONObject.NULL)
                .toString(),
            accessToken = accessToken,
            prefer = "return=minimal"
        ).let { /* 201 created — body ignored */ }
    }

    private suspend fun fetchChat(matchCode: String, accessToken: String?): List<LudoChatMessage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = SupabaseClient.rest(
                    method = "GET",
                    path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_CHAT_MESSAGES}",
                    query = mapOf(
                        "select" to "*",
                        "match_id" to "eq.$matchCode",
                        "order" to "created_at.asc",
                        "limit" to "200"
                    ),
                    accessToken = accessToken
                )
                val rows = SupabaseClient.parseArray(raw)
                buildList {
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        add(
                            LudoChatMessage(
                                id = row.optString("id"),
                                roomId = matchCode,
                                senderId = row.optString("sender_id").ifBlank { null },
                                senderName = row.optString("sender_name").ifBlank { "Player" },
                                isSystem = false, // room chat carries human messages only
                                text = row.optString("text"),
                                stickerEmoji = row.optString("sticker_emoji")
                                    .takeIf { it.isNotBlank() && it != "null" },
                                voiceDurationSeconds = if (row.isNull("voice_duration_seconds"))
                                    null else row.optInt("voice_duration_seconds"),
                                timestamp = row.optString("created_at")
                                    .substringAfter('T', "")
                                    .take(5)
                                    .ifBlank { "now" },
                                isMine = row.optString("sender_id") == currentUserId()
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    /** Placeholder identity — replaced by the ViewModel with the signed-in user id. */
    internal var currentUserIdProvider: () -> String = { "user_me" }

    fun currentUserId(): String = currentUserIdProvider()
}
