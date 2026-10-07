package com.example.ui.screens

// NOTE: `androidx.compose.animation.AnimatedVisibility` is intentionally
// NOT imported — the single call site below uses the fully-qualified name
// to avoid the K2 ColumnScope-extension resolution trap.
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.LudoEngine
import com.example.model.LudoMatch
import com.example.model.LudoPhase
import com.example.model.LudoPlayer
import com.example.model.LudoRules
import com.example.model.LudoToken
import com.example.ui.components.PremiumBadge
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.QuickyGold
import com.example.ui.theme.QuickyPink
import com.example.ui.theme.QuickyPurple
import kotlinx.coroutines.delay

/**
 * ============================================================================
 * LUDO ARENA — Quicky v3 (full playable multiplayer)
 *
 * Two-zone vertical layout:
 *   ┌───────────────────────────┐
 *   │ ← Ludo Arena  [code] [⚡] │   ← connection state when offline
 *   │   Player chips (4 seats)  │   ← YOUR TURN / WAITING + score
 *   │   LUDO BOARD (~60%)       │   ← step-by-step animated coins
 *   │   Turn: You · Dice: 6 · 24s│  ← server-deadline countdown strip
 *   │   Room Chat               │
 *   │   [list ~40%]             │
 *   │   [🎤][ message…  ][➤]   │   ← composer (ime-aware)
 *   └───────────────────────────┘
 *
 * v3 upgrades over v2.1:
 *  - 10s roll / 30s move countdowns driven by SERVER deadlines (PRD §5/§9).
 *  - Coins travel cell-by-cell (160ms/hop) — never teleport (PRD §12).
 *  - Capture flash + "+50" finish popup (PRD §36/§37).
 *  - Third-player-completion result screen (PRD §18/§26).
 * ============================================================================
 */
@Composable
fun LudoArenaScreen(
    match: LudoMatch?,
    isRolling: Boolean,
    isPremium: Boolean,
    joinError: String?,
    connectionOnline: Boolean,
    onBack: () -> Unit,
    onStartSoloBots: () -> Unit,
    onCreateOnline: () -> Unit,
    onJoinOnline: (String) -> Unit,
    onFillBots: () -> Unit,
    onRollDice: () -> Unit,
    onMoveToken: (Int) -> Unit,
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendSticker: (String) -> Unit,
    onSendVoiceMessage: () -> Unit,
    onOpenStickerPicker: () -> Unit,
    onOpenPremiumStore: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        match == null -> LudoArenaLobby(
            joinError = joinError,
            onBack = onBack,
            onStartSoloBots = onStartSoloBots,
            onCreateOnline = onCreateOnline,
            onJoinOnline = onJoinOnline,
            modifier = modifier
        )
        // v3 PRD §26 — the game ends when the THIRD player completes; show
        // the full standings screen instead of the board.
        match.phase == LudoPhase.FINISHED -> LudoResultScreen(
            match = match,
            onPlayAgain = onPlayAgain,
            onBack = onBack,
            modifier = modifier
        )
        else -> LudoArenaMatchScreen(
            match = match,
            isRolling = isRolling,
            isPremium = isPremium,
            connectionOnline = connectionOnline,
            onBack = onBack,
            onFillBots = onFillBots,
            onRollDice = onRollDice,
            onMoveToken = onMoveToken,
            onSendMessage = onSendMessage,
            onSendSticker = onSendSticker,
            onSendVoiceMessage = onSendVoiceMessage,
            onOpenStickerPicker = onOpenStickerPicker,
            onOpenPremiumStore = onOpenPremiumStore,
            modifier = modifier
        )
    }
}

// =====================================================================
// LOBBY — mode picker (solo vs bots / online 4-player)
// =====================================================================

@Composable
private fun LudoArenaLobby(
    joinError: String?,
    onBack: () -> Unit,
    onStartSoloBots: () -> Unit,
    onCreateOnline: () -> Unit,
    onJoinOnline: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var codeInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .dismissKeyboardOnTap()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("ludo_lobby_back")) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Ludo Arena",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            PremiumBadge(label = "GOLD")
        }

        Text(
            text = "The classic board, real rules, 4 seats. 10s to roll, 30s to move — the server rolls for you if the timer runs out. Game ends when the third player gets all 4 coins home.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // --- Solo vs bots ---
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, QuickyPurple),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = QuickyPurple)
                    Column {
                        Text("Solo vs Bots", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "You (Red) vs three bots · full rules offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onStartSoloBots,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ludo_solo_start_button")
                ) {
                    Text("Play Solo Match", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Online 4-player ---
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, QuickyPink),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.PersonAddAlt1, contentDescription = null, tint = QuickyPink)
                    Column {
                        Text("Online 4-Player", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "Create a match and share the code — or join a friend's",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onCreateOnline,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickyPink),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ludo_online_create_button")
                ) {
                    Text("Create Match & Get Code", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase().take(7) },
                    placeholder = { Text("Enter code, e.g. LA7K2QD") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ludo_join_code_input")
                )

                if (joinError != null) {
                    Text(
                        text = joinError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { onJoinOnline(codeInput.trim()) },
                    enabled = codeInput.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ludo_online_join_button")
                ) {
                    Text("Join Match", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =====================================================================
// MATCH SCREEN
// =====================================================================

@Composable
private fun LudoArenaMatchScreen(
    match: LudoMatch,
    isRolling: Boolean,
    isPremium: Boolean,
    connectionOnline: Boolean,
    onBack: () -> Unit,
    onFillBots: () -> Unit,
    onRollDice: () -> Unit,
    onMoveToken: (Int) -> Unit,
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendSticker: (String) -> Unit,
    onSendVoiceMessage: () -> Unit,
    onOpenStickerPicker: () -> Unit,
    onOpenPremiumStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    // --- Server-deadline clock (PRD §29): refresh 4x/second so the
    //     countdown ticks exactly once per second for every device. ---
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(match.phase, match.turnIndex, match.rollDeadlineAt, match.moveDeadlineAt) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(250)
        }
    }
    val rollSecondsLeft = secondsLeft(match.rollDeadlineAt, nowMs)
    val moveSecondsLeft = secondsLeft(match.moveDeadlineAt, nowMs)

    // Haptic on dice settle (value lands).
    LaunchedEffect(match.diceValue) {
        if (match.diceValue != null && !isRolling) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // ------------------------------------------------------------------
    // v3.2 TURN STATE MACHINE GATE (PRD §16–§24):
    //   IDLE → ROLLING → ROLLED → MOVING → SETTLING → HANDOFF_DELAY(500ms)
    //        → NEXT_TURN
    // The board reports [moveAnimationActive] while a coin is travelling
    // (MOVING/SETTLING). While it runs — or for exactly
    // [LudoRules.TURN_HANDOFF_MS] after it completes (HANDOFF_DELAY) — the
    // die stays showing the ROLLER's number and is NOT tappable by the next
    // player, on EVERY client: the gate is derived from the authoritative
    // seq-diff everyone receives, so all clients replay the same timeline
    // (PRD §21/§22) and nobody's dice activates while a coin still moves.
    // ------------------------------------------------------------------
    var moveAnimationActive by remember { mutableStateOf(false) }
    var rollGateOpen by remember { mutableStateOf(true) }
    var gateMatchId by remember { mutableStateOf<String?>(null) }
    var gateLastSeq by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(match.id, match.seq, match.turnIndex, moveAnimationActive) {
        if (gateMatchId != match.id) {
            // Fresh match (or rematch) — first turn is immediately playable.
            gateMatchId = match.id
            gateLastSeq = match.seq
            rollGateOpen = true
            return@LaunchedEffect
        }
        if (gateLastSeq == match.seq && !moveAnimationActive) {
            rollGateOpen = true // nothing new to settle
            return@LaunchedEffect
        }
        if (gateLastSeq != match.seq) {
            // A new authoritative event landed — close the gate (ROLLING,
            // MOVING, SETTLING or HANDOFF_DELAY all keep the die inactive).
            gateLastSeq = match.seq
            rollGateOpen = false
        }
        if (moveAnimationActive) {
            rollGateOpen = false
            return@LaunchedEffect // reopen when the coin settles (effect re-runs)
        }
        // Coin settled (or no movement at all) — the 500ms handoff delay.
        delay(LudoRules.TURN_HANDOFF_MS)
        rollGateOpen = true
    }

    // ------------------------------------------------------------------
    // v3.1 DICE FACE HOLD (PRD v3.2 §3/§18 "result ownership"): the die
    // STAYS on the number the roller rolled until the turn is fully
    // completed — extra rolls from a 6 / capture / finish included. Once
    // the turn passes on, the number stays up while the coin animation
    // finishes and then for [LudoRules.TURN_HANDOFF_MS] (500ms) before the
    // face resets to blank for its new owner (§20/§23 — never blank the
    // face at the instant the result is shown).
    // ------------------------------------------------------------------
    var diceOwnerSeat by remember { mutableIntStateOf(-1) }
    var diceShownValue by remember { mutableStateOf<Int?>(null) }
    var diceHoldMatchId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(match.id, match.seq, match.diceValue, match.turnIndex, match.phase, moveAnimationActive) {
        if (diceHoldMatchId != match.id) {
            // Fresh match (or rematch) — no die face carries over.
            diceHoldMatchId = match.id
            diceOwnerSeat = -1
            diceShownValue = null
        }
        if (match.phase == LudoPhase.FINISHED) {
            diceShownValue = null
            return@LaunchedEffect
        }
        if (match.phase == LudoPhase.AWAITING_MOVE && match.diceValue != null) {
            // A live roll just landed — the current player owns the face.
            diceOwnerSeat = match.turnIndex
            diceShownValue = match.diceValue
        } else if (match.diceValue != null) {
            if (match.turnIndex == diceOwnerSeat) {
                // Same player's extra roll pending — the face stays up.
                diceShownValue = match.diceValue
            } else {
                // Turn passed on: the rolled number belongs to the ROLLER
                // until the coin settles + the 500ms handoff elapses —
                // then the die blanks for its new owner.
                if (moveAnimationActive) return@LaunchedEffect // coin still moving → keep the face
                delay(LudoRules.TURN_HANDOFF_MS)
                diceShownValue = null
            }
        } else {
            diceShownValue = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        // v3.2 (PRD §7): the board and top bars stay STABLE when the room
        // chat's keyboard opens — the IME insets are consumed INSIDE
        // LudoRoomChat (below) instead of shrinking this whole column.
    ) {
        // --- Top bar ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .statusBarsPadding()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("ludo_back_button")) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Ludo Arena",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (match.mode == com.example.model.LudoMode.ONLINE) {
                Text(
                    text = match.id,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = QuickyPurple,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            // 4dp row inset + 8dp trailing pad = 12dp from the screen edge —
            // the same margin the board and seat chips use, so the badge
            // no longer crams against the bezel.
            PremiumBadge(label = "GOLD", modifier = Modifier.padding(end = 8.dp))
        }

        // --- Connection banner (PRD §68): realtime dropped, recovering. ---
        if (!connectionOnline) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Connection lost. Reconnecting…",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }

        // --- Player chips (4 seats) ---
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            match.players.forEachIndexed { seatIndex, player ->
                LudoSeatChip(
                    player = player,
                    isCurrentTurn = seatIndex == match.turnIndex,
                    isLocalPlayer = player.id == match.localUserId,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Board (~60%) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.60f)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            LudoArenaBoard(
                match = match,
                onTokenClick = onMoveToken,
                onMoveAnimationChanged = { moveAnimationActive = it },
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .align(Alignment.Center)
            )

            // Online + not started: host can fill empty seats with bots.
            // NOTE: fully-qualified call — with K2 + Compose 1.7 the plain
            // `AnimatedVisibility` inside a Box that is nested in the outer
            // Column resolves to the deprecated ColumnScope extension and
            // fails with "cannot be called in this context with an implicit
            // receiver". Qualifying forces the top-level overload.
            androidx.compose.animation.AnimatedVisibility(
                visible = match.mode == com.example.model.LudoMode.ONLINE &&
                        match.players.size < 4,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                OutlinedButton(
                    onClick = onFillBots,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = QuickyPurple),
                    modifier = Modifier.testTag("ludo_fill_bots_button")
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fill empty seats with bots", fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- Status strip + server countdown (PRD §38/§39/§40) ---
        // NOTE: the container color is an alpha-modified surfaceVariant, which
        // contentColorFor() can no longer resolve to onSurfaceVariant — without
        // the explicit contentColor the timer text fell back to BLACK and was
        // invisible on the dark theme.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val timerText = when {
                        !match.isStarted ->
                            "Waiting for players (${match.players.size}/4)…"
                        match.phase == LudoPhase.AWAITING_ROLL && match.isMyTurn ->
                            "YOUR TURN — roll in ${rollSecondsLeft ?: 10}s"
                        match.phase == LudoPhase.AWAITING_ROLL ->
                            "Waiting for ${match.currentPlayer.name}…"
                        match.phase == LudoPhase.AWAITING_MOVE && match.isMyTurn ->
                            "SELECT A COIN — ${moveSecondsLeft ?: 30}s"
                        else ->
                            "Turn: ${if (match.isMyTurn) "You" else match.currentPlayer.name} · Dice: ${match.diceValue ?: "–"}"
                    }
                    val urgent = (match.phase == LudoPhase.AWAITING_ROLL && match.isMyTurn &&
                            (rollSecondsLeft ?: 10) <= 3) ||
                            (match.phase == LudoPhase.AWAITING_MOVE && match.isMyTurn &&
                                    (moveSecondsLeft ?: 30) <= 3)
                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (urgent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = match.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // v3.2 (PRD §14): the die has ONE GLOBAL visual identity for
                // every player — deep-ink face, champagne-gold border + pips.
                // Whose turn it is stays communicated by the seat chips,
                // the waiting label and the board glow — NEVER by the dice
                // colors (player identity must not ride on the die).
                LudoDiceButton(
                    diceValue = diceShownValue,
                    isRolling = isRolling,
                    enabled = match.isStarted && match.isMyTurn &&
                            match.phase == LudoPhase.AWAITING_ROLL && !isRolling &&
                            rollGateOpen,
                    countdownSeconds = if (match.phase == LudoPhase.AWAITING_ROLL && match.isMyTurn)
                        rollSecondsLeft else null,
                    waitingLabel = if (match.isStarted && match.players.isNotEmpty() &&
                        match.phase != LudoPhase.FINISHED)
                        "${match.currentPlayer.name.split(" ").firstOrNull()?.take(10) ?: "PLAYER"}'S TURN"
                    else "WAITING",
                    onRoll = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRollDice()
                    }
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // --- Room chat (~40%) ---
        LudoRoomChat(
            match = match,
            isPremium = isPremium,
            onSendMessage = onSendMessage,
            onSendSticker = onSendSticker,
            onSendVoiceMessage = onSendVoiceMessage,
            onOpenStickerPicker = onOpenStickerPicker,
            onOpenPremiumStore = onOpenPremiumStore,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f)
        )
    }
}

/** Whole seconds remaining on a server deadline (PRD §29). */
private fun secondsLeft(deadlineAt: Long?, nowMs: Long): Int? =
    deadlineAt?.let { (((it - nowMs) / 1000L) + 1L).toInt().coerceIn(0, 99) }

// =====================================================================
// SEAT CHIP — YOUR TURN / WAITING + live score (PRD §38)
// =====================================================================

@Composable
private fun LudoSeatChip(
    player: LudoPlayer,
    isCurrentTurn: Boolean,
    isLocalPlayer: Boolean,
    modifier: Modifier = Modifier
) {
    val color = Color(player.color.colorHex)
    // Animated glow border for the active seat.
    val infiniteTransition = rememberInfiniteTransition(label = "seatGlow")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Surface(
        color = if (isCurrentTurn) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        // The active-turn container uses an alpha-modified color, so the
        // contentColor must be pinned explicitly (same trap as the status strip).
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isCurrentTurn) 2.dp else 1.dp,
            if (isCurrentTurn) color.copy(alpha = glow) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.testTag("ludo_seat_${player.seat}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = player.avatarRes),
                    contentDescription = player.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(2.dp, color, CircleShape)
                )
                if (player.isBot) {
                    Surface(
                        color = QuickyGold,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Text("🤖", fontSize = 7.sp)
                    }
                }
            }
            Text(
                text = if (isLocalPlayer) "You" else player.name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                text = if (isCurrentTurn) "▶ ${player.score} pts" else "${player.score} pts",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = if (isCurrentTurn) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "🏁 ${player.finishedTokens}/4",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = color
            )
        }
    }
}

// =====================================================================
// BOARD — Canvas + step-by-step animated tokens (PRD §12/§33/§34/§35)
// =====================================================================

/** Fractional grid position of a token (col, row in cell units). */
private data class TokenPosition(val col: Float, val row: Float)

private fun lerpPosition(a: TokenPosition, b: TokenPosition, t: Float): TokenPosition =
    TokenPosition(a.col + (b.col - a.col) * t, a.row + (b.row - a.row) * t)

/** Authoritative visual position of a token (mirrors the placements rules). */
private fun tokenPositionFor(seat: Int, tokenId: Int, stepCount: Int): TokenPosition = when {
    stepCount <= 0 -> {
        val (originCol, originRow) = LudoEngine.YARD_ORIGINS[seat.coerceIn(0, 3)]
        val slot = LudoEngine.YARD_SLOT_OFFSETS[tokenId.coerceIn(0, 3)]
        TokenPosition(originCol + slot.first, originRow + slot.second)
    }
    stepCount <= LudoEngine.HOME_ENTRY_STEP -> {
        val (c, r) = LudoEngine.TRACK[LudoEngine.absoluteIndex(seat, stepCount)]
        TokenPosition(c + 0.5f, r + 0.5f)
    }
    stepCount < LudoEngine.FINISH_STEP -> {
        val (c, r) = LudoEngine.HOME_COLUMNS[seat.coerceIn(0, 3)][stepCount - 52]
        TokenPosition(c + 0.5f, r + 0.5f)
    }
    else -> {
        // Finished tokens rest inside their OWN colored triangle via the
        // per-seat finish-slot system (PRD v2.3 §4/§6/§7): the destination
        // is resolved from the coin OWNER's seat→color identity — never
        // inferred from the board position — so a red coin can only ever
        // finish in the red triangle (and so on for every color).
        val (c, r) = LudoEngine.finishSlotFor(seat, tokenId)
        TokenPosition(c, r)
    }
}

@Composable
private fun LudoArenaBoard(
    match: LudoMatch,
    onTokenClick: (Int) -> Unit,
    onMoveAnimationChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val movableTokenIds = remember(match) {
        if (match.isMyTurn && match.phase == LudoPhase.AWAITING_MOVE) LudoEngine.legalMoves(match)
        else emptyList()
    }

    // ------------------------------------------------------------------
    // VISUAL ANIMATION STATE (PRD §33) — kept strictly separate from the
    // authoritative match. visualPositions overrides rendering during the
    // step-by-step hops; lastSteps is the diff base; lastRenderedSeq gates
    // gaps (reconnect / missed events snap instead of replaying, §32/§70).
    // ------------------------------------------------------------------
    var visualPositions by remember { mutableStateOf<Map<Pair<Int, Int>, TokenPosition>>(emptyMap()) }
    var lastSteps by remember { mutableStateOf<Map<Pair<Int, Int>, Int>?>(null) }
    var lastRenderedSeq by remember { mutableStateOf<Long?>(null) }
    var lastMatchId by remember { mutableStateOf<String?>(null) }
    var scorePopup by remember { mutableStateOf<String?>(null) }
    var captureFlashAt by remember { mutableStateOf<TokenPosition?>(null) }

    LaunchedEffect(match.id, match.seq) {
        if (lastRenderedSeq == match.seq) return@LaunchedEffect // no change
        // A cancelled predecessor (new event mid-animation) may have left
        // its overlays behind — clear them defensively.
        scorePopup = null
        captureFlashAt = null
        val authoritative: Map<Pair<Int, Int>, Int> = buildMap {
            match.players.forEachIndexed { seat, player ->
                player.tokens.forEach { put(seat to it.id, it.stepCount) }
            }
        }
        val previous = lastSteps
        val isNewMatch = lastMatchId != match.id
        val gap = lastRenderedSeq != null && match.seq > lastRenderedSeq!! + 1

        if (previous == null || isNewMatch || gap) {
            // First render or recovery: snap to the authoritative board.
            lastSteps = authoritative
            onMoveAnimationChanged(false)
        } else {
            val changed = authoritative.filterKeys { previous[it] != authoritative[it] }

            // MOVING / SETTLING (PRD §24): report the animation window so the
            // turn-state gate holds the dice + handoff until the coin lands.
            val mover = changed.entries.firstOrNull { (key, to) -> to > (previous[key] ?: 0) }
            val captured = changed.entries.filter { (key, to) -> to == 0 && (previous[key] ?: 0) > 0 }
            if (mover != null || captured.isNotEmpty()) onMoveAnimationChanged(true)

            // Freeze captured tokens at their PRE-move cells until the
            // attacker arrives — otherwise the authoritative yard position
            // would render a premature teleport (PRD §36 ordering).
            captured.forEach { (key, _) ->
                val holdPos = tokenPositionFor(key.first, key.second, previous[key] ?: 1)
                visualPositions = visualPositions.toMutableMap().apply { put(key, holdPos) }
            }

            if (mover != null) {
                val (key, toStep) = mover
                val seat = key.first
                val tokenId = key.second
                val fromStep = previous[key] ?: 0

                // Cell-by-cell hops (PRD §12): each intermediate cell is
                // rendered for CELL_HOP_MS with FastOutSlowIn easing.
                var currentPos = visualPositions[key]
                    ?: tokenPositionFor(seat, tokenId, fromStep)
                val pathSteps = (fromStep + 1..toStep).toList()
                for (step in pathSteps) {
                    val target = tokenPositionFor(seat, tokenId, step)
                    animate(
                        0f, 1f,
                        animationSpec = tween(
                            if (fromStep == 0) 260 else LudoRules.CELL_HOP_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) { v, _ ->
                        visualPositions = visualPositions.toMutableMap().apply {
                            put(key, lerpPosition(currentPos, target, v))
                        }
                    }
                    currentPos = target
                }

                // Finish effect: "+50" floats above the center (PRD §37).
                if (toStep >= LudoEngine.FINISH_STEP) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scorePopup = "+${LudoRules.POINTS_PER_TOKEN}"
                    delay(900)
                    scorePopup = null
                }
            }

            if (captured.isNotEmpty()) {
                // Capture sequence (PRD §36): attacker already arrived →
                // impact flash → captured coin travels back to its yard.
                val firstCaptured = captured.first()
                val flashPos = visualPositions[firstCaptured.key]
                    ?: tokenPositionFor(
                        firstCaptured.key.first, firstCaptured.key.second,
                        previous[firstCaptured.key] ?: 1
                    )
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                captureFlashAt = flashPos
                delay(650)
                captureFlashAt = null
                captured.forEach { (key, _) ->
                    val fromPos = visualPositions[key]
                        ?: tokenPositionFor(key.first, key.second, previous[key] ?: 1)
                    val yardPos = tokenPositionFor(key.first, key.second, 0)
                    animate(0f, 1f, animationSpec = tween(420, easing = FastOutSlowInEasing)) { v, _ ->
                        visualPositions = visualPositions.toMutableMap().apply {
                            put(key, lerpPosition(fromPos, yardPos, v))
                        }
                    }
                }
            }

            lastSteps = authoritative
        }
        lastRenderedSeq = match.seq
        lastMatchId = match.id
        // Clear overrides → authoritative rendering takes over.
        visualPositions = emptyMap()
        // Coin settled — release the turn gate (reopens after the 500ms
        // HANDOFF_DELAY, PRD §20).
        onMoveAnimationChanged(false)
    }

    BoxWithConstraints(modifier = modifier) {
        val cellDp = maxWidth / 15f
        val tokenSizeDp = cellDp * 0.68f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = size.width / 15f
            val boardBg = Color(0xFF1E1E2E)
            val cellStroke = Color(0xFF3A3A52)

            drawRoundRect(color = boardBg, cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f), size = size)

            fun drawCell(col: Int, row: Int, fill: Color, alpha: Float = 1f) {
                val topLeft = Offset(col * cell, row * cell)
                drawRect(color = fill.copy(alpha = alpha), topLeft = topLeft, size = androidx.compose.ui.geometry.Size(cell, cell))
                drawRect(color = cellStroke, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(cell, cell), style = Stroke(width = 1.5f))
            }

            // --- Main track cells (start cells tinted with their seat color) ---
            LudoEngine.TRACK.forEachIndexed { index, (col, row) ->
                if (index in LudoEngine.START_CELLS) {
                    drawCell(col, row, Color(LudoEngine.seatColors[index / 13]), 0.55f)
                } else {
                    drawCell(col, row, Color(0xFF2E2E44))
                }
            }

            // --- Star safe cells ---
            LudoEngine.SAFE_CELLS.forEach { index ->
                val (col, row) = LudoEngine.TRACK[index]
                val center = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
                drawCircle(color = QuickyGold.copy(alpha = 0.9f), radius = cell * 0.18f, center = center)
                drawCircle(color = boardBg, radius = cell * 0.07f, center = center)
            }

            // --- Home columns ---
            LudoEngine.HOME_COLUMNS.forEachIndexed { seat, column ->
                column.forEach { (col, row) ->
                    drawCell(col, row, Color(LudoEngine.seatColors[seat]), 0.65f)
                }
            }

            // --- Yards ---
            LudoEngine.YARD_ORIGINS.forEachIndexed { seat, (col0, row0) ->
                val c = Color(LudoEngine.seatColors[seat])
                drawRect(
                    color = c.copy(alpha = 0.22f),
                    topLeft = Offset(col0 * cell, row0 * cell),
                    size = androidx.compose.ui.geometry.Size(cell * 6, cell * 6)
                )
                drawRoundRect(
                    color = Color(0xFF272740),
                    topLeft = Offset((col0 + 1f) * cell, (row0 + 1f) * cell),
                    size = androidx.compose.ui.geometry.Size(cell * 4, cell * 4),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f)
                )
                drawRoundRect(
                    color = c.copy(alpha = 0.5f),
                    topLeft = Offset((col0 + 1.35f) * cell, (row0 + 1.35f) * cell),
                    size = androidx.compose.ui.geometry.Size(cell * 3.3f, cell * 3.3f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f)
                )
            }

            // --- Center home: 4 triangles + gold core (Games-section style) ---
            val center = Offset(size.width / 2f, size.height / 2f)
            val half = cell * 1.5f
            val topLeftCorner = Offset(center.x - half, center.y - half)
            val topRightCorner = Offset(center.x + half, center.y - half)
            val bottomRightCorner = Offset(center.x + half, center.y + half)
            val bottomLeftCorner = Offset(center.x - half, center.y + half)
            val path = androidx.compose.ui.graphics.Path()
            // RED (left triangle)
            path.moveTo(topLeftCorner.x, topLeftCorner.y)
            path.lineTo(bottomLeftCorner.x, bottomLeftCorner.y)
            path.lineTo(center.x, center.y)
            path.close()
            drawPath(path, Color(LudoEngine.seatColors[0]).copy(alpha = 0.8f))
            // GREEN (top)
            path.reset()
            path.moveTo(topLeftCorner.x, topLeftCorner.y)
            path.lineTo(topRightCorner.x, topRightCorner.y)
            path.lineTo(center.x, center.y)
            path.close()
            drawPath(path, Color(LudoEngine.seatColors[1]).copy(alpha = 0.8f))
            // YELLOW (right)
            path.reset()
            path.moveTo(topRightCorner.x, topRightCorner.y)
            path.lineTo(bottomRightCorner.x, bottomRightCorner.y)
            path.lineTo(center.x, center.y)
            path.close()
            drawPath(path, Color(LudoEngine.seatColors[2]).copy(alpha = 0.8f))
            // BLUE (bottom)
            path.reset()
            path.moveTo(bottomLeftCorner.x, bottomLeftCorner.y)
            path.lineTo(bottomRightCorner.x, bottomRightCorner.y)
            path.lineTo(center.x, center.y)
            path.close()
            drawPath(path, Color(LudoEngine.seatColors[3]).copy(alpha = 0.8f))
            // Gold core
            drawCircle(color = QuickyGold, radius = cell * 0.55f, center = center)
            drawCircle(color = boardBg, radius = cell * 0.32f, center = center)
        }

        // ---------------------------------------------------------
        // Tokens — interactive composables positioned by grid cell.
        // During animation the VISUAL position renders instead of the
        // authoritative one (PRD §33: game state stays untouched).
        // Tokens sharing the same cell fan out so both stay visible.
        // ---------------------------------------------------------
        data class TokenPlacement(
            val seat: Int,
            val token: LudoToken,
            val col: Float,
            val row: Float
        )

        val placements: List<TokenPlacement> = buildList {
            match.players.forEach { player ->
                // Final-triangle ownership hardening (PRD v2.3 §8): the
                // destination triangle is resolved from the coin OWNER's
                // color identity (player.seat → RED/GREEN/YELLOW/BLUE), not
                // from the array position — a red coin can never render in
                // another player's triangle even if a legacy board row ever
                // drifted out of seat order.
                val seat = player.seat.coerceIn(0, 3)
                player.tokens.forEach { token ->
                    val override = visualPositions[seat to token.id]
                    val pos = override ?: tokenPositionFor(seat, token.id, token.stepCount)
                    add(TokenPlacement(seat, token, pos.col, pos.row))
                }
            }
        }

        // Fan-out offsets for stacked tokens (non-yard, non-finished).
        val stackSpread: Map<Pair<Int, LudoToken>, Float> = buildMap {
            placements
                .filter { it.token.stepCount > 0 && !it.token.isFinished }
                .groupBy { it.col to it.row }
                .forEach { (_, group) ->
                    if (group.size > 1) {
                        group.forEachIndexed { index, placement ->
                            val spread = (index - (group.size - 1) / 2f) * 0.16f
                            put(placement.seat to placement.token, spread)
                        }
                    }
                }
        }

        placements.forEach { placement ->
            val isMovable = placement.seat == match.turnIndex &&
                    placement.token.id in movableTokenIds
            val spread = stackSpread[placement.seat to placement.token] ?: 0f
            LudoArenaToken(
                colorHex = match.players[placement.seat].color.colorHex,
                tokenId = placement.token.id,
                isMovable = isMovable,
                isFinished = placement.token.isFinished,
                // Parked (finished) coins render slightly smaller so all four
                // fit their own triangle's 3+1 slot grid without crowding.
                tokenSizeDp = if (placement.token.isFinished) tokenSizeDp.value * 0.88f
                else tokenSizeDp.value,
                xDp = (placement.col + spread) * cellDp.value - tokenSizeDp.value / 2f,
                yDp = (placement.row + spread) * cellDp.value - tokenSizeDp.value / 2f,
                onClick = {
                    if (isMovable) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTokenClick(placement.token.id)
                    }
                }
            )
        }

        // --- Capture impact flash (PRD §36) ---
        captureFlashAt?.let { flash ->
            Text(
                text = "💥",
                fontSize = 26.sp,
                modifier = Modifier
                    .offset(
                        x = (flash.col * cellDp.value - 12).dp,
                        y = (flash.row * cellDp.value - 12).dp
                    )
                    .testTag("ludo_capture_flash")
            )
        }

        // --- "+50" score popup over the center (PRD §37) ---
        scorePopup?.let { popup ->
            val popupAlpha by animateFloatAsState(
                targetValue = if (scorePopup != null) 1f else 0f,
                animationSpec = tween(250),
                label = "scorePopupAlpha"
            )
            Text(
                text = popup,
                color = QuickyGold,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = popupAlpha; translationY = -30f }
                    .testTag("ludo_score_popup")
            )
        }
    }
}

@Composable
private fun LudoArenaToken(
    colorHex: Long,
    tokenId: Int,
    isMovable: Boolean,
    isFinished: Boolean,
    tokenSizeDp: Float,
    xDp: Float,
    yDp: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tokenPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMovable) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Surface(
        shape = CircleShape,
        color = Color(colorHex).copy(alpha = if (isFinished) 0.75f else 1f),
        border = androidx.compose.foundation.BorderStroke(
            if (isMovable) 2.dp else 1.dp,
            if (isMovable) QuickyGold else Color.White
        ),
        shadowElevation = if (isMovable) 6.dp else 2.dp,
        modifier = Modifier
            .offset(x = xDp.dp, y = yDp.dp)
            .size(tokenSizeDp.dp)
            .scale(if (isMovable) pulse else 1f)
            .clickable(enabled = isMovable, onClick = onClick)
            .testTag("ludo_token_$tokenId")
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isFinished) {
                Text("✓", color = Color.White, fontSize = (tokenSizeDp * 0.35f).sp, fontWeight = FontWeight.ExtraBold)
            } else {
                Text(
                    "${tokenId + 1}",
                    color = Color.White,
                    fontSize = (tokenSizeDp * 0.3f).sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// =====================================================================
// DICE — roll timer, rolling state, server result (PRD §41)
// =====================================================================

@Composable
private fun LudoDiceButton(
    diceValue: Int?,
    isRolling: Boolean,
    enabled: Boolean,
    countdownSeconds: Int?,
    waitingLabel: String,
    onRoll: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "diceRoll")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRolling) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRolling) 220 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "diceRotation"
    )
    // Tumble face while rolling.
    var rollingFace by remember { mutableIntStateOf(1) }
    LaunchedEffect(isRolling) {
        while (isRolling) {
            rollingFace = (1..6).random()
            kotlinx.coroutines.delay(90)
        }
    }
    val urgent = enabled && (countdownSeconds ?: 10) <= 3
    // v3.1 (user request): while idle the die shows a BLANK face instead of
    // a stale tumble number — the face only fills with a REAL roll result
    // (live AWAITING_MOVE value or the held number during the handoff).
    val face = if (isRolling) rollingFace else diceValue

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            // v3.2 (PRD §14 — supersedes the v3.1 per-player colors): ONE
            // GLOBAL dice identity for EVERY player — deep-ink face,
            // champagne-gold border and champagne-gold pips, identical in
            // both themes, while rolling AND while idle. Player identity is
            // never expressed through the die (seat chips, board glow and
            // the waiting label carry it instead).
            color = LudoDiceInk,
            border = androidx.compose.foundation.BorderStroke(
                if (enabled) 2.5.dp else 1.5.dp,
                QuickyGold
            ),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(54.dp)
                .rotate(if (isRolling) rotation else 0f)
                .clickable(enabled = enabled, onClick = onRoll)
                .testTag("ludo_dice_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Champagne-gold pips for everyone (the default tint) —
                // rolling tumbling faces and settled results alike; no
                // face at all while idle.
                if (face != null) {
                    DiceFaceDots(value = face)
                }
            }
        }
        Text(
            text = when {
                isRolling -> "ROLLING…"
                enabled && countdownSeconds != null -> "ROLL · ${countdownSeconds}s"
                enabled -> "TAP ROLL"
                else -> waitingLabel
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
            color = when {
                urgent -> MaterialTheme.colorScheme.error
                // Theme-aware primary: the bright soft-rose dark variant keeps
                // the countdown readable on the dark strip (rosewood was too dim).
                enabled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Fixed deep-ink die face — the champagne gold pips/border stay readable on it in BOTH themes. */
private val LudoDiceInk = Color(0xFF272740)

/**
 * Real pips drawn on Canvas (PRD v2.3 §2.1): the glyph dice (⚀–⚅) could not
 * be reliably recolored across OEM fonts. v3.2 (PRD §14): the Ludo die
 * uses the DEFAULT champagne-gold tint for EVERY player — one global
 * dice identity; the [tint] override remains for any other call site.
 */
@Composable
fun DiceFaceDots(value: Int, tint: Color = Color.Unspecified) {
    val pipColor = if (tint != Color.Unspecified) tint else QuickyGold
    Canvas(modifier = Modifier.size(30.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val o = size.minDimension * 0.26f
        val r = size.minDimension * 0.095f
        val pipCenters: List<Offset> = when (value) {
            1 -> listOf(c)
            2 -> listOf(c + Offset(-o, -o), c + Offset(o, o))
            3 -> listOf(c + Offset(-o, -o), c, c + Offset(o, o))
            4 -> listOf(
                c + Offset(-o, -o), c + Offset(o, -o),
                c + Offset(-o, o), c + Offset(o, o)
            )
            5 -> listOf(
                c + Offset(-o, -o), c + Offset(o, -o), c,
                c + Offset(-o, o), c + Offset(o, o)
            )
            else -> listOf(
                c + Offset(-o, -o), c + Offset(-o, 0f), c + Offset(-o, o),
                c + Offset(o, -o), c + Offset(o, 0f), c + Offset(o, o)
            )
        }
        pipCenters.forEach { center ->
            drawCircle(color = pipColor, radius = r, center = center)
        }
    }
}
