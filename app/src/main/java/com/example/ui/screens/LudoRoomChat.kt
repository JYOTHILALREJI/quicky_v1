package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.LudoChatMessage
import com.example.model.LudoMatch
import com.example.ui.components.QuickyStickerIcon
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.QuickyGold
import com.example.ui.theme.QuickyPink
import com.example.ui.theme.QuickyPurple
import kotlinx.coroutines.launch

/**
 * ============================================================================
 * LUDO ROOM CHAT — Quicky v3 (PRD §42–§55)
 *
 *   ┌──────────────────────────────────┐
 *   │ Room Chat · 4 players            │
 *   │ 🔥 😂 😮 👏 GG ❤️   (scrollable) │
 *   ├──────────────────────────────────┤
 *   │ [avatar] Alex                    │
 *   │          "Nice move!"       12:04│
 *   │                     You     12:05│
 *   │                  "🔥 GG"         │
 *   │                                  │
 *   │            [↓ 2 new messages]    │
 *   ├──────────────────────────────────┤
 *   │ [🙂][🎤][ Type a message… ][➤]  │
 *   └──────────────────────────────────┘
 *
 *  - Human messages only — game events live in the status strip (§49).
 *  - Realtime INSERTs (no polling) + optimistic pending bubbles with
 *    server-row replacement (§54/§55).
 *  - Incoming bubbles: avatar + grouped sender name; outgoing: bubble only.
 *  - Auto-scroll ONLY when already near the bottom; otherwise a
 *    "↓ N new messages" pill (§48).
 *  - Long-press any bubble to reply (§51).
 *  - Voice premium-gated; composer is keyboard- and navbar-safe (§47).
 * ============================================================================
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LudoRoomChat(
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
    var replyingTo by remember { mutableStateOf<LudoChatMessage?>(null) }
    var unreadCount by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val humanMessages = remember(match.chatMessages) { match.chatMessages.filterNot { it.isSystem } }

    // "Already near the bottom" gate for auto-scroll (PRD §48).
    val isNearBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= humanMessages.size - 2
        }
    }

    LaunchedEffect(humanMessages.size) {
        if (humanMessages.isEmpty()) return@LaunchedEffect
        if (isNearBottom) {
            listState.animateScrollToItem(humanMessages.size - 1)
            unreadCount = 0
        } else {
            // The user is reading history — never yank them down (§48).
            unreadCount += 1
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .dismissKeyboardOnTap()
    ) {

        // --- Header: title + compact scrollable quick reactions (§44/§50) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Room Chat · ${match.players.size} players",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf("🔥", "😂", "😮", "👏", "GG", "❤️").forEach { quickReact ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onSendMessage(quickReact, null, null) }
                    ) {
                        Text(
                            text = quickReact,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold, fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(humanMessages, key = { it.id }) { msg ->
                    val index = humanMessages.indexOf(msg)
                    val previous = humanMessages.getOrNull(index - 1)
                    // Group consecutive messages from the same sender (§45).
                    val showHeader = !msg.isMine &&
                            (previous == null || previous.senderId != msg.senderId)
                    LudoChatBubble(
                        message = msg,
                        showHeader = showHeader,
                        playerAvatarRes = match.players
                            .firstOrNull { it.id == msg.senderId }?.avatarRes,
                        playerAvatarUrl = match.players
                            .firstOrNull { it.id == msg.senderId }?.avatarUrl,
                        onLongPress = { replyingTo = msg },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // "↓ N new messages" pill while reading history (§48).
            if (unreadCount > 0 && !isNearBottom) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = QuickyPurple,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clickable {
                            unreadCount = 0
                            scope.launch {
                                if (humanMessages.isNotEmpty()) {
                                    listState.animateScrollToItem(humanMessages.size - 1)
                                }
                            }
                        }
                        .testTag("ludo_chat_new_messages_pill")
                ) {
                    Text(
                        text = "↓ ${unreadCount.coerceAtMost(99)} new message" +
                                if (unreadCount == 1) "" else "s",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // --- Reply preview with quote + cancel X (§51) ---
        if (replyingTo != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                // Alpha-modified container: pin the content color explicitly
                // so the preview text never falls back to black on dark theme.
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "↩ Replying to ${replyingTo!!.senderName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = replyToQuote(replyingTo!!),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // --- Composer (keyboard- + navbar-safe, §47) ---
        // Mirrors the personal-chat composer: the TextField keeps its natural
        // Material height (a forced 44dp squeezed it below the 56dp minimum and
        // pushed the text/placeholder toward the top of the pill), the sticker
        // picker lives INSIDE the field as the trailing icon, and the mic +
        // send circles sit outside at 44dp — all vertically centered.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Message the room…", style = MaterialTheme.typography.bodyMedium) },
                    trailingIcon = {
                        IconButton(
                            onClick = onOpenStickerPicker,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = QuickyStickerIcon,
                                contentDescription = "Stickers",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ludo_chat_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    singleLine = true
                )

                // Voice note (premium-gated — §53) — OUTSIDE the composer, right side.
                IconButton(
                    onClick = {
                        if (isPremium) onSendVoiceMessage() else onOpenPremiumStore()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("ludo_voice_button")
                ) {
                    Icon(
                        imageVector = if (isPremium) Icons.Outlined.Mic else Icons.Outlined.Lock,
                        contentDescription = "Voice note",
                        tint = if (isPremium) MaterialTheme.colorScheme.primary else QuickyGold,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Send — filled rose circle while there is text to send.
                if (messageText.isNotBlank()) {
                    FilledIconButton(
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
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = QuickyPink,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("ludo_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun replyToQuote(message: LudoChatMessage): String {
    val body = message.stickerEmoji ?: message.text
    return "\"${body.take(48)}\""
}

/** One chat bubble — incoming rows carry avatar + grouped sender name (§45/§46). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LudoChatBubble(
    message: LudoChatMessage,
    showHeader: Boolean,
    playerAvatarRes: Int?,
    playerAvatarUrl: String?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isMine) {
            // Incoming: avatar (real profile photo when available, §46).
            if (showHeader) {
                if (!playerAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = playerAvatarUrl,
                        contentDescription = message.senderName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 2.dp, end = 6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                } else if (playerAvatarRes != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = playerAvatarRes),
                        contentDescription = message.senderName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 2.dp, end = 6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp).padding(end = 6.dp))
                }
            } else {
                Spacer(modifier = Modifier.width(30.dp))
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (message.isMine) QuickyPink else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 250.dp)
                .alpha(if (message.isPending) 0.6f else 1f)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (!message.isMine && showHeader) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = QuickyPurple
                    )
                }
                if (message.replyToText != null) {
                    Text(
                        text = "↩ ${message.replyToSender ?: "Player"}: ${message.replyToText}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (message.isMine) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (message.stickerEmoji != null) {
                    Text(message.stickerEmoji, fontSize = 28.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (message.voiceDurationSeconds != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = if (message.isMine) Color.White else QuickyPink,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Voice · 0:0${message.voiceDurationSeconds}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (message.isMine) Color.White.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
