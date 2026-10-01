package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.LudoChatMessage
import com.example.model.LudoPlayer
import com.example.model.LudoRoom
import com.example.model.LudoToken
import com.example.ui.components.ReplyPreviewBanner
import com.example.ui.components.SwipeToReplyContainer
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoGameRoomScreen(
    room: LudoRoom,
    isPremium: Boolean,
    onBack: () -> Unit,
    onRollDice: () -> Unit,
    onMoveToken: (Int) -> Unit,
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendSticker: (String) -> Unit,
    onSendVoiceMessage: () -> Unit,
    onOpenStickerPicker: () -> Unit,
    onOpenPremiumStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<LudoChatMessage?>(null) }
    val chatListState = rememberLazyListState()

    // Scroll chat to bottom when new messages arrive
    LaunchedEffect(room.chatMessages.size) {
        if (room.chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(room.chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "2-Player Ludo Arena",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            color = QuickyGold.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("✦", color = QuickyGold, fontSize = 10.sp)
                                Text(
                                    text = "PREMIUM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = QuickyGold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        text = "⏱ ${room.duration}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // =========================================================
            // TOP 60%: LUDO BOARD & PLAYERS (PRD Section 4.1)
            // =========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.60f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // PLAYER 1 (Opposite Top Player — Opponent)
                    LudoPlayerHeader(
                        player = room.player1,
                        isCurrentTurn = room.currentTurnPlayerId == room.player1.id,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // LUDO BOARD AREA
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LudoBoardCanvas(
                            player1 = room.player1,
                            player2 = room.player2,
                            canMoveToken = room.canMoveToken && room.currentTurnPlayerId == "user_me",
                            onTokenClick = { tokenId -> onMoveToken(tokenId) }
                        )

                        // Center Turn & Event Overlay Pill
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (room.currentTurnPlayerId == "user_me") QuickyPink else QuickyCyan
                            ),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = room.lastEventText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (room.currentTurnPlayerId == "user_me") QuickyPink else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // PLAYER 2 (Bottom Player — You) & Interactive Dice
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LudoPlayerHeader(
                            player = room.player2,
                            isCurrentTurn = room.currentTurnPlayerId == room.player2.id,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Interactive 3D Dice Widget
                        LudoDiceWidget(
                            diceValue = room.diceValue,
                            isRolling = room.isRolling,
                            isMyTurn = room.currentTurnPlayerId == "user_me",
                            canMoveToken = room.canMoveToken,
                            onRoll = onRollDice
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // =========================================================
            // BOTTOM 40%: ROOM COMMON CHAT (PRD Section 4.1 & 10)
            // =========================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.40f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Room Chat Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Room Chat · Shared Real-time",
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

                // Chat Messages List
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(room.chatMessages) { msg ->
                        if (msg.isSystem) {
                            // Distinct System Message (PRD Section 4.1)
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        } else {
                            // Player Chat Bubble with Swipe-to-Reply
                            SwipeToReplyContainer(onSwipeToReply = { replyingToMessage = msg }) {
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

                                            // Quoted Reply Preview
                                            if (msg.replyToText != null) {
                                                Surface(
                                                    color = if (msg.isMine) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                                                        Text(
                                                            text = msg.replyToSender ?: "Reply",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                                            color = if (msg.isMine) Color.White.copy(alpha = 0.9f) else QuickyPurple
                                                        )
                                                        Text(
                                                            text = msg.replyToText,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = if (msg.isMine) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            if (msg.stickerEmoji != null) {
                                                Text(
                                                    text = msg.stickerEmoji,
                                                    fontSize = 28.sp,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
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
                                                        imageVector = Icons.Filled.PlayArrow,
                                                        contentDescription = null,
                                                        tint = if (msg.isMine) Color.White else QuickyPink,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "Voice note · 0:0${msg.voiceDurationSeconds}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Replying To Preview Banner
                if (replyingToMessage != null) {
                    ReplyPreviewBanner(
                        replySender = if (replyingToMessage!!.isMine) "You" else replyingToMessage!!.senderName,
                        replyText = replyingToMessage!!.text.ifBlank { replyingToMessage!!.stickerEmoji ?: "Voice note" },
                        onCancel = { replyingToMessage = null }
                    )
                }

                // Chat Input Bar (Text, Stickers, Voice)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Stickers Button
                        IconButton(
                            onClick = onOpenStickerPicker,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("💖", fontSize = 18.sp)
                        }

                        // Voice Message Button (PRD Section 10: Premium entitlement required)
                        IconButton(
                            onClick = {
                                if (isPremium) onSendVoiceMessage()
                                else onOpenPremiumStore()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPremium) Icons.Outlined.Mic else Icons.Outlined.Lock,
                                contentDescription = "Voice note",
                                tint = if (isPremium) QuickyPink else QuickyGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Text Field
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Send room message...", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            singleLine = true
                        )

                        // Send Button
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(
                                        messageText,
                                        replyingToMessage?.text?.ifBlank { replyingToMessage?.stickerEmoji }?.take(60),
                                        if (replyingToMessage != null) (if (replyingToMessage!!.isMine) "You" else replyingToMessage!!.senderName) else null
                                    )
                                    messageText = ""
                                    replyingToMessage = null
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
    }
}

// -------------------------------------------------------------
// PLAYER HEADER (PRD Section 5 & 6)
// -------------------------------------------------------------
@Composable
fun LudoPlayerHeader(
    player: LudoPlayer,
    isCurrentTurn: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isCurrentTurn) QuickyGold else Color.Transparent

    Surface(
        color = if (isCurrentTurn) QuickyPurple.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(player.colorHex), CircleShape)
            ) {
                Image(
                    painter = painterResource(id = player.avatarRes),
                    contentDescription = player.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCurrentTurn) {
                        Text(
                            text = "• TURN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = QuickyGold,
                            fontSize = 10.sp
                        )
                    }
                }
                Text(
                    text = "${player.colorName} • ${player.score} pts",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(player.colorHex),
                    fontWeight = FontWeight.Bold
                )
            }

            // Tokens remaining count
            Surface(
                color = Color(player.colorHex).copy(alpha = 0.2f),
                shape = CircleShape
            ) {
                Text(
                    text = "🏁 ${player.finishedTokensCount}/4",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(player.colorHex),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// RESPONSIVE LUDO BOARD CANVAS & TOKENS (PRD Section 4, 5, 7)
// -------------------------------------------------------------
@Composable
fun LudoBoardCanvas(
    player1: LudoPlayer,
    player2: LudoPlayer,
    canMoveToken: Boolean,
    onTokenClick: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E1E2E))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        val boardSize = maxWidth
        val cellSize = boardSize / 15f

        // Draw classic board track and safe spots
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cellW = w / 15f
            val cellH = h / 15f

            // Player 1 Base (Top)
            drawRect(
                color = Color(0xFF06B6D4).copy(alpha = 0.25f),
                topLeft = Offset(0f, 0f),
                size = Size(cellW * 6, cellH * 6)
            )
            // Player 2 Base (Bottom)
            drawRect(
                color = Color(0xFFFF2A6D).copy(alpha = 0.25f),
                topLeft = Offset(cellW * 9, cellH * 9),
                size = Size(cellW * 6, cellH * 6)
            )

            // Center Victory Triangle
            drawRect(
                color = Color(0xFFFFB300).copy(alpha = 0.35f),
                topLeft = Offset(cellW * 6, cellH * 6),
                size = Size(cellW * 3, cellH * 3)
            )

            // Center winning star
            drawCircle(
                color = Color(0xFFFFB300),
                radius = cellW * 0.8f,
                center = Offset(w / 2f, h / 2f)
            )
        }

        // Render Player 1 Tokens (Top Area)
        player1.tokens.forEach { token ->
            LudoTokenWidget(
                token = token,
                colorHex = player1.colorHex,
                isMyToken = false,
                canMove = false,
                onClick = {},
                modifier = Modifier.align(
                    if (token.isHome) Alignment.TopStart
                    else if (token.isFinished) Alignment.Center
                    else Alignment.TopCenter
                ).padding(
                    start = if (token.isHome) (16 + token.id * 24).dp else 0.dp,
                    top = if (token.isHome) (16 + (token.id % 2) * 24).dp else 0.dp
                )
            )
        }

        // Render Player 2 Tokens (User's Tokens)
        player2.tokens.forEach { token ->
            val isMovable = canMoveToken && !token.isFinished
            LudoTokenWidget(
                token = token,
                colorHex = player2.colorHex,
                isMyToken = true,
                canMove = isMovable,
                onClick = { if (isMovable) onTokenClick(token.id) },
                modifier = Modifier.align(
                    if (token.isHome) Alignment.BottomEnd
                    else if (token.isFinished) Alignment.Center
                    else Alignment.BottomCenter
                ).padding(
                    end = if (token.isHome) (16 + token.id * 24).dp else 0.dp,
                    bottom = if (token.isHome) (16 + (token.id % 2) * 24).dp else 0.dp
                )
            )
        }
    }
}

// -------------------------------------------------------------
// LUDO TOKEN WIDGET (PRD Section 7)
// -------------------------------------------------------------
@Composable
fun LudoTokenWidget(
    token: LudoToken,
    colorHex: Long,
    isMyToken: Boolean,
    canMove: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tokenPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (canMove) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Surface(
        shape = CircleShape,
        color = Color(colorHex),
        border = androidx.compose.foundation.BorderStroke(
            if (canMove) 2.5.dp else 1.dp,
            if (canMove) QuickyGold else Color.White
        ),
        shadowElevation = if (canMove) 8.dp else 2.dp,
        modifier = modifier
            .size(28.dp)
            .scale(if (canMove) pulseScale else 1f)
            .clickable(enabled = canMove, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${token.id + 1}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// -------------------------------------------------------------
// INTERACTIVE 3D ANIMATED DICE (PRD Section 7)
// -------------------------------------------------------------
@Composable
fun LudoDiceWidget(
    diceValue: Int,
    isRolling: Boolean,
    isMyTurn: Boolean,
    canMoveToken: Boolean,
    onRoll: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "diceRoll")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRolling) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "diceRotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isMyTurn && !canMoveToken) QuickyPink else MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                if (isMyTurn && !canMoveToken) QuickyGold else Color.Transparent
            ),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(54.dp)
                .rotate(if (isRolling) rotation else 0f)
                .clickable(enabled = isMyTurn && !canMoveToken && !isRolling, onClick = onRoll)
                .testTag("ludo_dice_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isRolling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    // Render Dice Face Dots
                    DiceFaceDots(value = diceValue)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (isMyTurn && !canMoveToken) "TAP ROLL" else if (canMoveToken) "MOVE TOKEN" else "WAITING",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
            color = if (isMyTurn) QuickyPink else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DiceFaceDots(value: Int) {
    when (value) {
        1 -> Text("⚀", fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
        2 -> Text("⚁", fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
        3 -> Text("⚂", fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
        4 -> Text("⚃", fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
        5 -> Text("⚄", fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
        6 -> Text("⚅", fontSize = 34.sp, color = QuickyGold, fontWeight = FontWeight.ExtraBold)
        else -> Text("$value", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
