package com.example.data

import com.example.model.LudoChatMessage
import com.example.model.LudoMatch
import com.example.model.LudoPhase
import com.example.model.LudoPlayer
import com.example.model.LudoRules
import com.example.model.LudoToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================================
 * LUDO MATCH REPOSITORY — Quicky v3 (full playable multiplayer)
 *
 * Persists Ludo Arena matches to Supabase:
 *
 *   ludo_matches        (id, host_id, status, winner_id, completed_at)
 *   ludo_players       (match_id, user_id, name, seat, is_bot, avatar_url)
 *   ludo_game_state     (match_id, turn, dice_value, board_state jsonb,
 *                        roll_deadline_at, move_deadline_at, updated_at)
 *   ludo_moves         (match_id, user_id, from_cell, to_cell, dice, …)
 *   ludo_chat_messages  (match_id, sender_id, sender_name, text, …)
 *   ludo_game_results   (match_id, user_id, seat, score, finish_position, …)
 *   ludo_user_stats     (user_id, games_played, last_score, best_score, …)
 *
 * SYNCHRONIZATION (v3 PRD §14): Supabase Realtime is the PRIMARY sync path
 * during normal play ([subscribeRealtime]); the 10s [fetchMatch] poll in the
 * ViewModel is a RECOVERY FALLBACK only. Dice rolls and moves flow through
 * the server-authoritative Edge Functions (`roll_ludo_dice`,
 * `apply_ludo_move`) which validate timers, award scores and end the match
 * when the THIRD player brings all 4 coins home.
 *
 * CLOCK SYNC (v3 PRD §29): deadlines are absolute server timestamps; this
 * repository maintains [serverClockOffsetMs] from the HTTP `Date` header so
 * every device renders the same countdown.
 * ============================================================================
 */
object LudoMatchRepository {

    private fun isConfigured() = SupabaseRepository.isConfigured()

    // --------------------------------------------------------------
    // Server clock offset (PRD §29) — deadlines must agree across devices
    // --------------------------------------------------------------

    /** serverNow - localNow, refreshed on every fetch/subscribe. */
    @Volatile
    internal var serverClockOffsetMs: Long = 0L

    suspend fun refreshServerClock() {
        SupabaseClient.serverTime()?.let { serverNow ->
            serverClockOffsetMs = serverNow - System.currentTimeMillis()
        }
    }

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
                        .put("score", player.score)
                        .put("finish_position", player.finishPosition ?: JSONObject.NULL)
                        .put(
                            "avatar_url",
                            if (player.avatarUrl.isNullOrBlank()) JSONObject.NULL else player.avatarUrl
                        )
                )
            }
        })
        put("turn_index", turnIndex)
        put("dice_value", diceValue ?: JSONObject.NULL)
        put("phase", phase.name)
        put("consecutive_sixes", consecutiveSixes)
        put("winner_id", winnerId ?: JSONObject.NULL)
        put("status_text", statusText)
        put("seq", seq)
        put("completed_players", completedPlayers)
        put("finish_order", JSONArray(finishOrder))
        // Absolute server-clock deadlines — round-trip exactly through the
        // offset applied in [fromJson].
        put(
            "roll_deadline_at",
            rollDeadlineAt?.let { LudoTime.formatIso(it - serverClockOffsetMs) } ?: JSONObject.NULL
        )
        put(
            "move_deadline_at",
            moveDeadlineAt?.let { LudoTime.formatIso(it - serverClockOffsetMs) } ?: JSONObject.NULL
        )
    }

    fun fromJson(matchId: String, json: JSONObject, chat: List<LudoChatMessage> = emptyList()): LudoMatch {
        val playersJson = json.optJSONArray("players") ?: JSONArray()
        val players = buildList {
            for (i in 0 until playersJson.length()) {
                val p = playersJson.optJSONObject(i) ?: continue
                // Seat-ownership hardening (PRD v2.3 §5): the server treats
                // the array INDEX as the seat (Edge Functions index players
                // by position), so the client normalizes seat = index — the
                // seat is the immutable coin COLOR identity (0=RED, 1=GREEN,
                // 2=YELLOW, 3=BLUE) and drives which home triangle the coins
                // finish in. Never trust a drifted `seat` field in the row.
                val seat = i
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
                        },
                        score = p.optInt("score", 0),
                        finishPosition = if (p.isNull("finish_position")) null
                        else p.optInt("finish_position").takeIf { it > 0 },
                        avatarUrl = p.optString("avatar_url")
                            .takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
        val phase = when (json.optString("phase")) {
            LudoPhase.AWAITING_MOVE.name -> LudoPhase.AWAITING_MOVE
            LudoPhase.FINISHED.name -> LudoPhase.FINISHED
            else -> LudoPhase.AWAITING_ROLL
        }
        val finishOrder = json.optJSONArray("finish_order")?.let { arr ->
            buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
        } ?: emptyList()
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
            localUserId = currentUserId(),
            seq = json.optLong("seq", 0L),
            rollDeadlineAt = LudoTime.parseIsoToEpochMs(json.optString("roll_deadline_at"))
                ?.plus(serverClockOffsetMs),
            moveDeadlineAt = LudoTime.parseIsoToEpochMs(json.optString("move_deadline_at"))
                ?.plus(serverClockOffsetMs),
            completedPlayers = json.optInt("completed_players", 0),
            finishOrder = finishOrder
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

    /** True when the board is mid-game — new joins must be rejected. */
    private fun isStarted(board: JSONObject): Boolean =
        (board.optJSONArray("players")?.length() ?: 0) >= 4

    /**
     * Host flow: creates the match row, takes seat 0 and seeds the fresh
     * board state. The 10s roll timer arms only once all 4 seats fill.
     * Returns the match code.
     */
    suspend fun createOnlineMatch(
        hostUserId: String,
        hostName: String,
        accessToken: String?,
        avatarUrl: String? = null
    ): String = withContext(Dispatchers.IO) {
        refreshServerClock()
        val code = newMatchCode()
        val fresh = JSONObject()
            .put("players", JSONArray().put(
                JSONObject()
                    .put("id", hostUserId)
                    .put("name", hostName)
                    .put("seat", 0)
                    .put("is_bot", false)
                    .put("tokens", JSONArray(listOf(0, 0, 0, 0)))
                    .put("score", 0)
                    .put("finish_position", JSONObject.NULL)
                    .put("avatar_url", avatarUrl ?: JSONObject.NULL)
            ))
            .put("turn_index", 0)
            .put("dice_value", JSONObject.NULL)
            .put("phase", "AWAITING_ROLL")
            .put("consecutive_sixes", 0)
            .put("winner_id", JSONObject.NULL)
            .put("status_text", "Waiting for players — share the code to fill the seats")
            .put("seq", 1)
            .put("completed_players", 0)
            .put("finish_order", JSONArray())

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
                .put("avatar_url", avatarUrl ?: JSONObject.NULL)
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
     * when the match does not exist, is already full / completed, or has
     * already started. Arming the roll timer happens exactly once — when
     * the 4th seat is claimed.
     */
    suspend fun joinOnlineMatch(
        matchCode: String,
        userId: String,
        playerName: String,
        accessToken: String?,
        avatarUrl: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        refreshServerClock()
        val state = fetchRawState(matchCode)
        val board = state.optJSONObject("board_state") ?: JSONObject()
        if (isStarted(board)) {
            throw IllegalStateException("Match $matchCode has already started.")
        }
        val playersJson = board.optJSONArray("players") ?: JSONArray()
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
                .put("avatar_url", avatarUrl ?: JSONObject.NULL)
                .toString(),
            accessToken = accessToken,
            prefer = "return=minimal"
        )

        // Mirror the seat into the board state so every client sees it, and
        // bump seq; the 4th seat arms the MATCH_STARTED moment (first roll
        // deadline, PRD §15).
        playersJson.put(
            JSONObject()
                .put("id", userId)
                .put("name", playerName)
                .put("seat", freeSeat)
                .put("is_bot", false)
                .put("tokens", JSONArray(listOf(0, 0, 0, 0)))
                .put("score", 0)
                .put("finish_position", JSONObject.NULL)
                .put("avatar_url", avatarUrl ?: JSONObject.NULL)
        )
        board.put("players", playersJson)
        board.put("seq", board.optLong("seq", 0L) + 1L)
        if (playersJson.length() >= 4) {
            board.put(
                "roll_deadline_at",
                LudoTime.formatIso(System.currentTimeMillis() + LudoRules.ROLL_WINDOW_MS - serverClockOffsetMs)
            )
            board.put("status_text", "All seats filled — roll to start!")
        }
        updateBoardState(matchCode, board, accessToken)
    }

    // --------------------------------------------------------------
    // State fetch (realtime primary / poll recovery, PRD §14)
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

    /** Full match snapshot for a code — state + chat (recovery path). */
    suspend fun fetchMatch(matchCode: String, accessToken: String? = null): LudoMatch =
        withContext(Dispatchers.IO) {
            refreshServerClock()
            val row = fetchRawState(matchCode, accessToken)
            val chat = fetchChat(matchCode, accessToken)
            fromJson(matchCode, row.optJSONObject("board_state") ?: JSONObject(), chat)
        }

    /** Pushes a new board state (fallback write path when Edge Functions are absent).
     *  When [expectedServerSeq] is provided the PATCH is version-guarded —
     *  it only lands while the server still holds that seq, so a late push
     *  can never roll back a newer authoritative write (PRD §31 CAS-style). */
    suspend fun updateBoardState(
        matchCode: String,
        boardState: JSONObject,
        accessToken: String?,
        expectedServerSeq: Long? = null
    ): Unit = withContext(Dispatchers.IO) {
        val rollIso = boardState.optString("roll_deadline_at")
            .takeIf { it.isNotBlank() && it != "null" }
        val moveIso = boardState.optString("move_deadline_at")
            .takeIf { it.isNotBlank() && it != "null" }
        val query = if (expectedServerSeq != null) {
            mapOf(
                "match_id" to "eq.$matchCode",
                "board_state->>seq" to "eq.$expectedServerSeq"
            )
        } else {
            mapOf("match_id" to "eq.$matchCode")
        }
        SupabaseClient.rest(
            method = "PATCH",
            path = "/rest/v1/${SupabaseConfig.TABLE_LUDO_GAME_STATE}",
            query = query,
            body = JSONObject()
                .put("board_state", boardState)
                // Mirror the core fields into their table columns so
                // conditional updates + realtime consumers stay consistent.
                .put("turn", boardState.optInt("turn_index", 0))
                .put(
                    "dice_value",
                    if (boardState.isNull("dice_value")) JSONObject.NULL
                    else boardState.opt("dice_value")
                )
                .put("roll_deadline_at", rollIso ?: JSONObject.NULL)
                .put("move_deadline_at", moveIso ?: JSONObject.NULL)
                .put("updated_at", JSONObject.NULL)
                .toString(),
            accessToken = accessToken
        ).let { /* response ignored */ }
    }

    // --------------------------------------------------------------
    // Realtime subscription (PRIMARY sync path, v3 PRD §14)
    // --------------------------------------------------------------

    /**
     * Subscribes to the authoritative game-state + room-chat streams for
     * [matchCode]. Callbacks arrive on OkHttp threads — marshal before
     * touching UI state.
     */
    fun subscribeRealtime(
        matchCode: String,
        accessToken: String?,
        onGameState: (JSONObject) -> Unit,
        onChatMessage: (JSONObject) -> Unit,
        onConnection: (Boolean) -> Unit
    ): LudoRealtime.Subscription = LudoRealtime.subscribe(
        matchCode = matchCode,
        accessToken = accessToken,
        listener = object : LudoRealtime.Listener {
            override fun onGameState(row: JSONObject) = onGameState(row)
            override fun onChatMessage(row: JSONObject) = onChatMessage(row)
            override fun onConnection(online: Boolean) = onConnection(online)
        }
    )

    /** Parses a raw ludo_game_state ROW (realtime payload or REST fetch). */
    fun stateRowToMatch(matchCode: String, row: JSONObject): LudoMatch {
        // Realtime usually delivers jsonb as a nested object, but some
        // transports stringify it — accept both shapes.
        val boardRaw = row.opt("board_state")
        val board = when (boardRaw) {
            is JSONObject -> boardRaw
            is String -> runCatching { JSONObject(boardRaw) }.getOrNull()
            else -> null
        } ?: JSONObject()
        return fromJson(matchCode, board)
    }

    /** Parses a raw ludo_chat_messages row (realtime INSERT or REST fetch). */
    fun chatRowToMessage(row: JSONObject): LudoChatMessage = LudoChatMessage(
        id = row.optString("id"),
        roomId = row.optString("match_id"),
        senderId = row.optString("sender_id").ifBlank { null },
        senderName = row.optString("sender_name").ifBlank { "Player" },
        isSystem = false, // room chat carries human messages only (PRD §49)
        text = row.optString("text"),
        stickerEmoji = row.optString("sticker_emoji")
            .takeIf { it.isNotBlank() && it != "null" },
        voiceDurationSeconds = if (row.isNull("voice_duration_seconds"))
            null else row.optInt("voice_duration_seconds"),
        timestamp = LudoTime.isoToClock(row.optString("created_at")) ?: "now",
        isMine = row.optString("sender_id") == currentUserId()
    )

    // --------------------------------------------------------------
    // Server-authoritative actions (Edge Functions)
    // --------------------------------------------------------------

    /**
     * Fills every empty seat of [matchCode] with a bot (host convenience so
     * a match can start while humans trickle in). The last filled seat arms
     * the first roll deadline (MATCH_STARTED). Returns the seat count
     * after filling.
     */
    suspend fun fillEmptySeatsWithBots(
        matchCode: String,
        botNames: List<String>,
        accessToken: String?
    ): Int = withContext(Dispatchers.IO) {
        refreshServerClock()
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
                    .put("score", 0)
                    .put("finish_position", JSONObject.NULL)
                    .put("avatar_url", JSONObject.NULL)
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
        board.put("seq", board.optLong("seq", 0L) + 1L)
        if (playersJson.length() >= 4) {
            board.put(
                "roll_deadline_at",
                LudoTime.formatIso(System.currentTimeMillis() + LudoRules.ROLL_WINDOW_MS - serverClockOffsetMs)
            )
            board.put("status_text", "All seats filled — roll to start!")
        }
        updateBoardState(matchCode, board, accessToken)
        playersJson.length()
    }

    /**
     * Calls the `roll_ludo_dice` Edge Function. When [auto] is true the call
     * is a timer-expiry trigger — the server rolls and marks it automatic
     * (PRD §6). Returns the rolled value.
     */
    suspend fun rollDiceRemote(matchCode: String, accessToken: String?, auto: Boolean = false): Int =
        withContext(Dispatchers.IO) {
            val response = SupabaseClient.functions(
                name = "roll_ludo_dice",
                payload = mapOf("match_id" to matchCode, "auto" to auto),
                accessToken = accessToken
            )
            response.optInt("dice", 0).also { check(it in 1..6) { "Invalid dice from server: $it" } }
        }

    /**
     * Calls the `apply_ludo_move` Edge Function with the client's candidate
     * move; the server validates it against the SAME rules as the local
     * engine and returns the authoritative new board state. `auto = true`
     * requests the deterministic timeout move (PRD §10).
     */
    suspend fun applyMoveRemote(
        matchCode: String,
        tokenId: Int?,
        accessToken: String?,
        auto: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        SupabaseClient.functions(
            name = "apply_ludo_move",
            payload = mapOf(
                "match_id" to matchCode,
                "token_id" to tokenId,
                "auto" to auto
            ),
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
    // Room chat (realtime INSERT primary — PRD §54)
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
                        add(chatRowToMessage(row))
                    }
                }
            }.getOrDefault(emptyList())
        }

    /** Placeholder identity — replaced by the ViewModel with the signed-in user id. */
    internal var currentUserIdProvider: () -> String = { "user_me" }

    fun currentUserId(): String = currentUserIdProvider()
}
