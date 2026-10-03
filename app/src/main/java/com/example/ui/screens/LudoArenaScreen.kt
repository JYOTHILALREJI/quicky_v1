package com.example.ui.screens

// NOTE: `androidx.compose.animation.AnimatedVisibility` is intentionally
// NOT imported — the single call site below uses the fully-qualified name
// to avoid the K2 ColumnScope-extension resolution trap.
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.example.model.LudoToken
import com.example.ui.components.PremiumBadge
import com.example.ui.components.QuickyStickerIcon
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.QuickyGold
import com.example.ui.theme.QuickyPink
import com.example.ui.theme.QuickyPurple

/**
 * ============================================================================
 * LUDO ARENA — Quicky v2.1 §3.2 (complete rewrite)
 *
 * Two-zone vertical layout (PRD §3.2.3):
 *   ┌───────────────────────────┐
 *   │ ← Ludo Arena      [badge] │
 *   │   Player chips (4 seats)  │
 *   │   LUDO BOARD (~60%)       │
 *   │   Turn: You · Dice: 6     │ ← status strip
 *   │   Room Chat               │
 *   │   [list ~40%]             │
 *   │   [🎤][ message…  ][➤]   │ ← composer (ime-aware)
 *   └───────────────────────────┘
 *
 * The room chat carries HUMAN messages only — system logs were removed.
 * Voice messages are premium-gated (routed through PremiumGate).
 * ============================================================================
 */
@Composable
fun LudoArenaScreen(
    match: LudoMatch?,
    isRolling: Boolean,
    isPremium: Boolean,
    joinError: String?,
    onBack: () -> Unit,
    onStartSoloBots: () -> Unit,
    onCreateOnline: () -> Unit,
    onJoinOnline: (String) -> Unit,
    onFillBots: () -> Unit,
    onRestartSolo: () -> Unit,
    onRollDice: () -> Unit,
    onMoveToken: (Int) -> Unit,
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendSticker: (String) -> Unit,
    onSendVoiceMessage: () -> Unit,
    onOpenStickerPicker: () -> Unit,
    onOpenPremiumStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (match == null) {
        LudoArenaLobby(
            joinError = joinError,
            onBack = onBack,
            onStartSoloBots = onStartSoloBots,
            onCreateOnline = onCreateOnline,
            onJoinOnline = onJoinOnline,
            modifier = modifier
        )
    } else {
        LudoArenaMatchScreen(
            match = match,
            isRolling = isRolling,
            isPremium = isPremium,
            onBack = onBack,
            onFillBots = onFillBots,
            onRestartSolo = onRestartSolo,
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Ludo Arena",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
            )
            PremiumBadge(label = "GOLD")
        }

        Text(
            text = "The classic board, real rules, 4 seats. Roll a 6 to release a token, capture rivals, race all 4 tokens home.",
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
    onBack: () -> Unit,
    onFillBots: () -> Unit,
    onRestartSolo: () -> Unit,
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
    var showWinner by remember { mutableStateOf(false) }

    // Haptic on dice settle (value lands).
    LaunchedEffect(match.diceValue) {
        if (match.diceValue != null && !isRolling) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    // Winner overlay once the match completes.
    LaunchedEffect(match.phase) { showWinner = match.phase == LudoPhase.FINISHED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Ludo Arena",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
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
            PremiumBadge(label = "GOLD")
        }

        // --- Player chips (4 seats) ---
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            match.players.forEach { player ->
                LudoSeatChip(
                    player = player,
                    isCurrentTurn = match.players.indexOf(player) == match.turnIndex,
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
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .align(Alignment.Center)
            )

            // Online: host can fill empty seats with bots before/while playing.
            // NOTE: fully-qualified call — with K2 + Compose 1.7 the plain
            // `AnimatedVisibility` inside a Box that is nested in the outer
            // Column resolves to the deprecated ColumnScope extension and
            // fails with "cannot be called in this context with an implicit
            // receiver". Qualifying forces the top-level overload.
            androidx.compose.animation.AnimatedVisibility(
                visible = match.mode == com.example.model.LudoMode.ONLINE &&
                        match.players.size < 4 &&
                        match.phase != LudoPhase.FINISHED,
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

        // --- Status strip ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Turn: ${if (match.isMyTurn) "You" else match.currentPlayer.name}  ·  Dice: ${match.diceValue ?: "–"}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = match.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LudoDiceButton(
                    diceValue = match.diceValue,
                    isRolling = isRolling,
                    enabled = match.isMyTurn && match.phase == LudoPhase.AWAITING_ROLL && !isRolling,
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

    // --- Winner dialog ---
    if (showWinner) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("🏆 Victory!") },
            text = {
                val winner = match.players.find { it.id == match.winnerId } ?: match.currentPlayer
                Text("${winner.name} brought all 4 tokens home first.")
            },
            confirmButton = {
                if (match.mode == com.example.model.LudoMode.SOLO_VS_BOTS) {
                    Button(
                        onClick = {
                            showWinner = false
                            onRestartSolo()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple)
                    ) { Text("Play Again") }
                } else {
                    Button(onClick = onBack) { Text("Leave Match") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showWinner = false; onBack() }) { Text("Exit") }
            }
        )
    }
}

// =====================================================================
// SEAT CHIP
// =====================================================================

@Composable
private fun LudoSeatChip(
    player: LudoPlayer,
    isCurrentTurn: Boolean,
    isLocalPlayer: Boolean,
    modifier: Modifier = Modifier
) {
    val color = Color(player.color.colorHex)
    Surface(
        color = if (isCurrentTurn) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isCurrentTurn) 1.5.dp else 1.dp,
            if (isCurrentTurn) color else MaterialTheme.colorScheme.outlineVariant
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
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
// BOARD
// =====================================================================

@Composable
private fun LudoArenaBoard(
    match: LudoMatch,
    onTokenClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val movableTokenIds = remember(match) {
        if (match.isMyTurn && match.phase == LudoPhase.AWAITING_MOVE) LudoEngine.legalMoves(match)
        else emptyList()
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
        // Tokens sharing the same cell fan out so both stay visible.
        // ---------------------------------------------------------
        data class TokenPlacement(val seat: Int, val token: LudoToken, val col: Float, val row: Float)

        val placements: List<TokenPlacement> = buildList {
            match.players.forEachIndexed { seat, player ->
                player.tokens.forEach { token ->
                    val (col, row) = when {
                        token.isInYard -> {
                            val (originCol, originRow) = LudoEngine.YARD_ORIGINS[seat]
                            val slot = LudoEngine.YARD_SLOT_OFFSETS[token.id]
                            (originCol + slot.first) to (originRow + slot.second)
                        }
                        else -> {
                            val (c, r) = LudoEngine.cellForStep(seat, token.stepCount)
                            if (token.isFinished) {
                                // Finished tokens rest inside their seat's center
                                // triangle, fanned along its edge.
                                val along = (token.id - 1.5f) * 0.22f
                                when (seat) {
                                    0 -> 6.1f to (7f + along)          // RED left triangle
                                    1 -> (7f + along) to 6.1f          // GREEN top triangle
                                    2 -> 7.9f to (7f + along)          // YELLOW right triangle
                                    else -> (7f + along) to 7.9f        // BLUE bottom triangle
                                }
                            } else (c + 0.5f) to (r + 0.5f)
                        }
                    }
                    add(TokenPlacement(seat, token, col, row))
                }
            }
        }

        // Fan-out offsets for stacked tokens (non-yard, non-finished).
        val stackSpread: Map<Pair<Int, LudoToken>, Float> = buildMap {
            placements
                .filter { !it.token.isInYard && !it.token.isFinished }
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
                tokenSizeDp = tokenSizeDp.value,
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
    }
}

private fun yardSlotOffset(tokenId: Int): Pair<Float, Float> = when (tokenId) {
    0 -> 1.75f to 1.75f
    1 -> 3.75f to 1.75f
    2 -> 1.75f to 3.75f
    else -> 3.75f to 3.75f
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
// DICE
// =====================================================================

@Composable
private fun LudoDiceButton(
    diceValue: Int?,
    isRolling: Boolean,
    enabled: Boolean,
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (enabled) QuickyPink else MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(
                2.dp, if (enabled) QuickyGold else Color.Transparent
            ),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(54.dp)
                .rotate(if (isRolling) rotation else 0f)
                .clickable(enabled = enabled, onClick = onRoll)
                .testTag("ludo_dice_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isRolling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    DiceFaceDots(value = diceValue ?: rollingFace)
                }
            }
        }
        Text(
            text = when {
                isRolling -> "ROLLING…"
                enabled -> "TAP ROLL"
                else -> "WAITING"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
            color = if (enabled) QuickyPink else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun DiceFaceDots(value: Int) {
    val emoji = when (value) {
        1 -> "⚀"; 2 -> "⚁"; 3 -> "⚂"; 4 -> "⚃"; 5 -> "⚄"
        else -> "⚅"
    }
    Text(
        emoji,
        fontSize = 34.sp,
        color = if (value == 6) QuickyGold else Color.White,
        fontWeight = FontWeight.ExtraBold
    )
}

// =====================================================================
// ROOM CHAT (human messages only — system logs removed per §3.2.3)
// =====================================================================

@Composable
private fun LudoRoomChat(
    match: LudoMatch,
    isPremium: Boolean,
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendSticker: (String) -> Unit,
    onSendVoiceMessage: () -> Unit,
    onOpenStickerPicker: () -> Unit,
    onOpenPremiumStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<com.example.model.LudoChatMessage?>(null) }
    val listState = rememberLazyListState()
    val humanMessages = remember(match.chatMessages) { match.chatMessages.filterNot { it.isSystem } }

    LaunchedEffect(humanMessages.size) {
        if (humanMessages.isNotEmpty()) {
            listState.animateScrollToItem(humanMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .dismissKeyboardOnTap()
    ) {
        // Header: "Room Chat" only.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Room Chat",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("🎲 Roll!", "🔥 GG", "👀 Nice", "🏆 Crown").forEach { quickReact ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onSendMessage(quickReact, null, null) }
                    ) {
                        Text(
                            text = quickReact,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(humanMessages, key = { it.id }) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (msg.isMine) QuickyPink else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 260.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = msg.senderName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (msg.isMine) Color.White.copy(alpha = 0.85f) else QuickyPurple
                            )
                            if (msg.replyToText != null) {
                                Text(
                                    text = "↩ ${msg.replyToSender ?: "Player"}: ${msg.replyToText}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = if (msg.isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            if (msg.stickerEmoji != null) {
                                Text(msg.stickerEmoji, fontSize = 28.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            if (msg.text.isNotBlank()) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (msg.voiceDurationSeconds != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = if (msg.isMine) Color.White else QuickyPink,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Voice · 0:0${msg.voiceDurationSeconds}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Text(
                                text = msg.timestamp,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = if (msg.isMine) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        // Reply preview.
        if (replyingTo != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "↩ Replying to ${replyingTo!!.senderName}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Composer.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onOpenStickerPicker, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = QuickyStickerIcon,
                        contentDescription = "Stickers",
                        tint = QuickyPink,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Voice message (premium-gated — currently unlocked for QA, §3.7).
                IconButton(
                    onClick = {
                        if (isPremium) onSendVoiceMessage() else onOpenPremiumStore()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("ludo_voice_button")
                ) {
                    Icon(
                        imageVector = if (isPremium) Icons.Outlined.Mic else Icons.Outlined.Lock,
                        contentDescription = "Voice note",
                        tint = if (isPremium) QuickyPink else QuickyGold,
                        modifier = Modifier.size(20.dp)
                    )
                }

                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Message the room…", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("ludo_chat_input"),
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(
                                messageText,
                                replyingTo?.text?.take(60),
                                if (replyingTo != null) replyingTo!!.senderName else null
                            )
                            messageText = ""
                            replyingTo = null
                        }
                    },
                    enabled = messageText.isNotBlank(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank()) QuickyPink else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
