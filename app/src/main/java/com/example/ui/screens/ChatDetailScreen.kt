package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.MockDataProvider
import com.example.model.*
import com.example.ui.components.ChatBubble
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    match: MatchItem,
    messages: List<ChatMessage>,
    prompts: List<TruthOrDarePrompt>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendPrompt: (TruthOrDarePrompt) -> Unit,
    onAnswerGame: (String, String) -> Unit, // messageId, answerText
    onAddReaction: (String, String) -> Unit, // messageId, emoji
    onViewProfile: (UserProfile) -> Unit,
    onUnmatch: () -> Unit,
    onBlock: () -> Unit,
    onReport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showGamePicker by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_detail_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable { onViewProfile(match.user) }
                    ) {
                        val photo = match.user.photoResIds.firstOrNull() ?: R.drawable.img_profile_sarah
                        Box(modifier = Modifier.size(40.dp)) {
                            Image(
                                painter = painterResource(id = photo),
                                contentDescription = match.user.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            if (match.user.isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(ActionLike)
                                )
                            }
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = match.user.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (match.user.isVerified) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (match.user.isOnline) "Active now" else "Active recently",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (match.user.isOnline) ActionLike else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showGamePicker = true },
                        modifier = Modifier.testTag("chat_top_game_button")
                    ) {
                        Icon(imageVector = Icons.Filled.SportsEsports, contentDescription = "Play Truth or Dare", tint = SparkPurple)
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Options")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Profile") },
                                onClick = {
                                    showMenu = false
                                    onViewProfile(match.user)
                                },
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Unmatch") },
                                onClick = {
                                    showMenu = false
                                    onUnmatch()
                                },
                                leadingIcon = { Icon(Icons.Outlined.HeartBroken, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Report User") },
                                onClick = {
                                    showMenu = false
                                    showReportDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.Report, contentDescription = null, tint = ActionPass) }
                            )
                            DropdownMenuItem(
                                text = { Text("Block User") },
                                onClick = {
                                    showMenu = false
                                    onBlock()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, tint = ActionPass) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chat Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false
            ) {
                // Header notice
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SparkRose.copy(alpha = 0.15f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "🎉 You matched with ${match.user.name}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SparkRose,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            text = "Send a thoughtful message or challenge them to Truth or Dare!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(messages) { msg ->
                    ChatBubble(
                        message = msg,
                        onReactionClick = { emoji -> onAddReaction(msg.id, emoji) },
                        onAnswerGame = { answer -> onAnswerGame(msg.id, answer) }
                    )
                }
            }

            // Quick Icebreaker Chips Bar (PRD Section 24)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SuggestionChip(
                        onClick = {
                            val prompt = prompts.filter { it.type == "TRUTH" }.random()
                            onSendPrompt(prompt)
                        },
                        label = { Text("🎲 Send Truth") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = SparkPurple.copy(alpha = 0.2f),
                            labelColor = SparkPurple
                        )
                    )
                }
                item {
                    SuggestionChip(
                        onClick = {
                            val prompt = prompts.filter { it.type == "DARE" }.random()
                            onSendPrompt(prompt)
                        },
                        label = { Text("🔥 Send Dare") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = SparkRose.copy(alpha = 0.2f),
                            labelColor = SparkRose
                        )
                    )
                }
                items(MockDataProvider.icebreakerSuggestions.take(2)) { suggestion ->
                    val cleanText = suggestion.substringAfter("Ask: ").replace("\"", "")
                    SuggestionChip(
                        onClick = {
                            inputText = cleanText
                        },
                        label = { Text("💡 $cleanText", maxLines = 1) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            // Bottom Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Play Game CTA Button
                    FilledIconButton(
                        onClick = { showGamePicker = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SparkPurple.copy(alpha = 0.2f),
                            contentColor = SparkPurple
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("chat_bottom_game_button")
                    ) {
                        Icon(imageVector = Icons.Filled.SportsEsports, contentDescription = "Play game")
                    }

                    // Message text input
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message ${match.user.name}...", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        maxLines = 4
                    )

                    // Send Button
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SparkRose,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("chat_send_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Send, contentDescription = "Send message", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    // Truth or Dare Picker Sheet
    if (showGamePicker) {
        ModalBottomSheet(
            onDismissRequest = { showGamePicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Challenge ${match.user.name} 🎮",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showGamePicker = false }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Pick a prompt category to send directly into the chat:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                val categories = listOf("Flirty", "Funny", "Deep", "First Date")
                categories.forEach { cat ->
                    val catPrompts = prompts.filter { it.category == cat }
                    val randomPrompt = catPrompts.randomOrNull() ?: prompts.first()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onSendPrompt(randomPrompt)
                                showGamePicker = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (randomPrompt.type == "TRUTH") SparkPurple else SparkRose
                                    ) {
                                        Text(
                                            text = randomPrompt.type,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = randomPrompt.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Icon(imageVector = Icons.Filled.Send, contentDescription = "Send", tint = SparkRose)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report ${match.user.name}") },
            text = { Text("Our safety team reviews all reports to maintain a respectful and safe dating environment.") },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        onReport("User reported from chat")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }
}
