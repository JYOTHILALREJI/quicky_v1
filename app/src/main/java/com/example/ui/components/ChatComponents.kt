package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ChatMessage
import com.example.model.GameCardData
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SwipeToReplyContainer(
    onSwipeToReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "swipeReply")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(offsetX) > 50f) {
                            onSwipeToReply()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-100f, 100f)
                    }
                )
            }
    ) {
        if (kotlin.math.abs(animatedOffsetX) > 8f) {
            val isRightDrag = animatedOffsetX > 0
            Box(
                modifier = Modifier
                    .align(if (isRightDrag) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(QuickyPurple.copy(alpha = (kotlin.math.abs(animatedOffsetX) / 60f).coerceIn(0.3f, 1f))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (animatedOffsetX * 0.4f).dp)
        ) {
            content()
        }
    }
}

@Composable
fun ReplyPreviewBanner(
    replySender: String,
    replyText: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(QuickyPurple)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replying to $replySender",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = QuickyPurple
                )
                Text(
                    text = replyText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel reply",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onReactionClick: (String) -> Unit,
    onAnswerGame: (String) -> Unit,
    onSwipeToReply: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showReactionPicker by remember { mutableStateOf(false) }

    val bubbleContent = @Composable {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 12.dp),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
        ) {
        // If message has embedded Game Card (Truth or Dare)
        if (message.gameCard != null) {
            ChatGameCard(
                gameCard = message.gameCard,
                isMine = message.isMine,
                onAnswer = onAnswerGame,
                modifier = Modifier.widthIn(max = 320.dp)
            )
        } else if (message.text.isNotBlank()) {
            Box {
                // Cassy chat bubbles (PRD §5.4): asymmetric corners with a
                // soft rosewood→blush gradient on outgoing messages and a
                // subtly elevated surface on incoming ones.
                val bubbleShape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isMine) 18.dp else 4.dp,
                    bottomEnd = if (message.isMine) 4.dp else 18.dp
                )
                if (message.isMine) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .shadow(
                                elevation = 3.dp,
                                shape = bubbleShape,
                                spotColor = CassyPrimary.copy(alpha = 0.35f)
                            )
                            .clip(bubbleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CassyPrimary, CassyPrimaryGradientEnd)
                                )
                            )
                            .clickable { showReactionPicker = !showReactionPicker }
                    ) {
                        ChatBubbleContent(message = message)
                    }
                } else {
                    Surface(
                        shape = bubbleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clickable { showReactionPicker = !showReactionPicker }
                    ) {
                        ChatBubbleContent(message = message)
                    }
                }
            }
        }

        // Active Reactions Row
        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .offset(y = (-4).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.reactions.forEach { emoji ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clickable { onReactionClick(emoji) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Quick Reaction Picker Bar
        if (showReactionPicker) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val quickEmojis = listOf("❤️", "🔥", "😂", "👏", "😮")
                    quickEmojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clickable {
                                    onReactionClick(emoji)
                                    showReactionPicker = false
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
    }

    if (onSwipeToReply != null) {
        SwipeToReplyContainer(onSwipeToReply = onSwipeToReply) {
            bubbleContent()
        }
    } else {
        bubbleContent()
    }
}

/**
 * The inner payload of a chat bubble — reply quote, voice player, message
 * text and the timestamp/read-status row. Shared by the gradient
 * (outgoing) and elevated (incoming) Cassy bubble surfaces.
 */
@Composable
private fun ChatBubbleContent(message: ChatMessage) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (message.replyToText != null) {
            Surface(
                color = if (message.isMine) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        text = message.replyToSender ?: "Reply",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = if (message.isMine) Color.White.copy(alpha = 0.9f) else QuickyPurple
                    )
                    Text(
                        text = message.replyToText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (message.isMine) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (message.isVoiceMessage || message.voiceDurationSeconds != null) {
            VoiceMessagePlayer(
                durationSeconds = message.voiceDurationSeconds ?: 5,
                isMine = message.isMine
            )
        } else {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = if (message.isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (message.isMine) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = "Read status",
                    tint = if (message.isRead) Color.White else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun ChatGameCard(
    gameCard: GameCardData,
    isMine: Boolean,
    onAnswer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var responseMode by remember { mutableStateOf("TEXT") } // "TEXT", "VOICE", "CAMERA"
    var answerInput by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceSeconds by remember { mutableIntStateOf(0) }
    var hasVoiceRecorded by remember { mutableStateOf(false) }
    var isCameraSnapped by remember { mutableStateOf(false) }

    val isTruth = gameCard.promptType == "TRUTH"

    Card(
        modifier = modifier
            .padding(vertical = 4.dp)
            .testTag("chat_game_card_${gameCard.sessionId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTruth) Color(0xFF26183C) else Color(0xFF381525)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isTruth) SparkPurple else SparkRose
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with game badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = null,
                        tint = if (isTruth) SparkPurple else SparkRose,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TRUTH OR DARE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isTruth) SparkPurple else SparkRose
                ) {
                    Text(
                        text = gameCard.promptType,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // The Prompt Question
            Text(
                text = gameCard.promptText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Completed Answer State
            if (gameCard.isCompleted && !gameCard.answerText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = ActionLike,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isMine) "Receiver's Answer:" else "Your Answer:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SparkGold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (gameCard.responseType == "VOICE" || gameCard.answerText.startsWith("🎤")) {
                            // Voice Answer Player
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SparkPurple.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play voice note",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Voice Answer (0:0${gameCard.voiceDurationSeconds ?: 5})",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Audio recorded for ${gameCard.promptType.lowercase()}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        } else if (gameCard.responseType == "CAMERA" || gameCard.answerText.startsWith("📸")) {
                            // Camera Photo Proof
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                ) {
                                    Image(
                                        painter = painterResource(id = gameCard.cameraPhotoResId ?: R.drawable.img_truth_dare_banner),
                                        contentDescription = "Dare Photo Proof",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = null, tint = SparkRose, modifier = Modifier.size(12.dp))
                                            Text(text = "Dare Photo Proof", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    text = "“${gameCard.answerText}”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        } else {
                            // Text Answer
                            Text(
                                text = "“${gameCard.answerText}”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            } else if (isMine) {
                // SENDER VIEW: The sender MUST NOT see the answer input field or submit button!
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SparkGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.HourglassTop,
                                    contentDescription = "Waiting for answer",
                                    tint = SparkGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Challenge Sent!",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = if (isTruth)
                                    "Waiting for recipient's text or voice answer..."
                                else
                                    "Waiting for recipient's text, voice, or camera dare proof...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            } else {
                // RECEIVER VIEW: Receiver can choose response method!
                // Truth: only text or voice
                // Dare: text, voice, or camera input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose how to respond:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Text Option (available for Truth & Dare)
                        FilterChip(
                            selected = responseMode == "TEXT",
                            onClick = { responseMode = "TEXT" },
                            label = { Text("✍️ Text", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (isTruth) SparkPurple else SparkRose,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Voice Option (available for Truth & Dare)
                        FilterChip(
                            selected = responseMode == "VOICE",
                            onClick = { responseMode = "VOICE" },
                            label = { Text("🎙️ Voice", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (isTruth) SparkPurple else SparkRose,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Camera Option (ONLY FOR DARE!)
                        if (!isTruth) {
                            FilterChip(
                                selected = responseMode == "CAMERA",
                                onClick = { responseMode = "CAMERA" },
                                label = { Text("📸 Camera", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SparkRose,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    when (responseMode) {
                        "TEXT" -> {
                            OutlinedTextField(
                                value = answerInput,
                                onValueChange = { answerInput = it },
                                placeholder = {
                                    Text(
                                        text = if (isTruth) "Type your honest truth..." else "Type your dare response...",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("game_card_answer_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isTruth) SparkPurple else SparkRose,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = false,
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (answerInput.isNotBlank()) {
                                        onAnswer(answerInput)
                                        answerInput = ""
                                    }
                                },
                                enabled = answerInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("submit_game_answer_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isTruth) SparkPurple else SparkRose
                                )
                            ) {
                                Icon(imageVector = Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Submit Answer", fontWeight = FontWeight.Bold)
                            }
                        }

                        "VOICE" -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (!hasVoiceRecorded && !isRecordingVoice) {
                                        Text(
                                            text = "Record your voice response 🎙️",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                isRecordingVoice = true
                                                coroutineScope.launch {
                                                    for (sec in 1..5) {
                                                        delay(600)
                                                        voiceSeconds = sec
                                                    }
                                                    isRecordingVoice = false
                                                    hasVoiceRecorded = true
                                                }
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SparkPurple)
                                        ) {
                                            Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Voice Recording", color = Color.White)
                                        }
                                    } else if (isRecordingVoice) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(SparkRose)
                                            )
                                            Text(
                                                text = "Recording Voice Note... 0:0$voiceSeconds",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { voiceSeconds / 5f },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = SparkRose
                                        )
                                    } else {
                                        // Voice Note recorded
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ActionLike, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "Voice response captured (0:05)",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                onAnswer("🎤 Voice answer submitted (0:05)")
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if (isTruth) SparkPurple else SparkRose)
                                        ) {
                                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Submit Voice Response", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        "CAMERA" -> {
                            // Camera Input (Available only for Dare)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (!isCameraSnapped) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.Black.copy(alpha = 0.6f))
                                                .border(1.dp, SparkRose.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Filled.CameraAlt,
                                                    contentDescription = "Camera viewfinder",
                                                    tint = SparkRose,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Dare Photo Proof Camera",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { isCameraSnapped = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = SparkRose)
                                        ) {
                                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Snap Dare Photo Proof", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.img_onboarding_hero),
                                                contentDescription = "Dare photo snapshot",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color.Black.copy(alpha = 0.75f),
                                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                            ) {
                                                Text(
                                                    text = "✓ Snapped",
                                                    color = ActionLike,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { isCameraSnapped = false },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Retake")
                                            }
                                            Button(
                                                onClick = {
                                                    onAnswer("📸 Photo Dare Proof completed!")
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = SparkRose),
                                                modifier = Modifier.weight(2f)
                                            ) {
                                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Submit Photo", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceMessagePlayer(
    durationSeconds: Int,
    isMine: Boolean,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var currentSeconds by remember { mutableIntStateOf(0) }
    val waveformHeights = remember { listOf(6, 12, 18, 14, 22, 16, 26, 20, 14, 18, 24, 12, 8, 15) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            currentSeconds = 0
            while (currentSeconds < durationSeconds && isPlaying) {
                delay(1000)
                currentSeconds++
            }
            isPlaying = false
            currentSeconds = 0
        }
    }

    Row(
        modifier = modifier
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play / Pause Icon Button
        FilledIconButton(
            onClick = { isPlaying = !isPlaying },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isMine) Color.White.copy(alpha = 0.25f) else SparkPurple.copy(alpha = 0.15f),
                contentColor = if (isMine) Color.White else SparkPurple
            ),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(20.dp)
            )
        }

        // Waveform Visualizer & Duration
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                modifier = Modifier.height(26.dp)
            ) {
                waveformHeights.forEachIndexed { index, height ->
                    val playedFraction = if (durationSeconds > 0) currentSeconds.toFloat() / durationSeconds else 0f
                    val barFraction = index.toFloat() / waveformHeights.size
                    val isBarPlayed = isPlaying && barFraction <= playedFraction

                    val barColor = when {
                        isBarPlayed -> if (isMine) Color.White else SparkRose
                        isMine -> Color.White.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }

                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(height.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(barColor)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "🎙️",
                    fontSize = 11.sp
                )
                Text(
                    text = if (isPlaying) "0:0$currentSeconds / 0:0$durationSeconds" else "Voice note (0:0$durationSeconds)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = if (isMine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

