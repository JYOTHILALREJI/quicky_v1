package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.model.GameCardData
import com.example.ui.theme.*

@Composable
fun ChatBubble(
    message: ChatMessage,
    onReactionClick: (String) -> Unit,
    onAnswerGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showReactionPicker by remember { mutableStateOf(false) }

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
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.isMine) 18.dp else 4.dp,
                        bottomEnd = if (message.isMine) 4.dp else 18.dp
                    ),
                    color = if (message.isMine) SparkRose else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clickable { showReactionPicker = !showReactionPicker }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )

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

@Composable
fun ChatGameCard(
    gameCard: GameCardData,
    isMine: Boolean,
    onAnswer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var answerInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .padding(vertical = 4.dp)
            .testTag("chat_game_card_${gameCard.sessionId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (gameCard.promptType == "TRUTH") Color(0xFF26183C) else Color(0xFF381525)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (gameCard.promptType == "TRUTH") SparkPurple else SparkRose
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
                        tint = if (gameCard.promptType == "TRUTH") SparkPurple else SparkRose,
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
                    color = if (gameCard.promptType == "TRUTH") SparkPurple else SparkRose
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

            // Answer state
            if (gameCard.isCompleted && !gameCard.answerText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Answer:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SparkGold
                        )
                        Text(
                            text = "“${gameCard.answerText}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            } else {
                // Interactive Answer Form for recipient
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = answerInput,
                        onValueChange = { answerInput = it },
                        placeholder = { Text("Your answer...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("game_card_answer_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SparkRose,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (answerInput.isNotBlank()) {
                                onAnswer(answerInput)
                                answerInput = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_game_answer_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gameCard.promptType == "TRUTH") SparkPurple else SparkRose
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit Answer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
