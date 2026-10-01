package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.components.QuickyGamesIcon
import com.example.ui.components.QuickyStickerIcon
import com.example.ui.components.ReplyPreviewBanner
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    match: MatchItem,
    messages: List<ChatMessage>,
    prompts: List<TruthOrDarePrompt>,
    onBack: () -> Unit,
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendPrompt: (TruthOrDarePrompt) -> Unit,
    onAnswerGame: (String, String) -> Unit, // messageId, answerText
    onAddReaction: (String, String) -> Unit, // messageId, emoji
    onViewProfile: (UserProfile) -> Unit,
    onUnmatch: () -> Unit,
    onBlock: () -> Unit,
    onReport: (String) -> Unit,
    isPremium: Boolean = false,
    onOpenPremiumStore: () -> Unit = {},
    onOpenLudo: () -> Unit = {},
    onOpenStickerPicker: () -> Unit = {},
    onSendVoiceMessage: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showGamePicker by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var isRecordingVoiceNote by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecordingVoiceNote) {
        if (isRecordingVoiceNote) {
            voiceRecordSeconds = 0
            while (isRecordingVoiceNote) {
                delay(1000)
                voiceRecordSeconds++
            }
        }
    }

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
                    val isDark = MaterialTheme.colorScheme.background == DarkBg || MaterialTheme.colorScheme.surface == DarkSurface

                    // Games section in chat (custom SVG gamepad icon, next to the 3-dots menu)
                    IconButton(
                        onClick = { showGamePicker = true },
                        modifier = Modifier.testTag("chat_top_game_button")
                    ) {
                        Icon(
                            imageVector = QuickyGamesIcon,
                            contentDescription = "Chat Games Section",
                            tint = if (isDark) Color.White else Color.Black
                        )
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
                        onAnswerGame = { answer -> onAnswerGame(msg.id, answer) },
                        onSwipeToReply = { replyingToMessage = msg }
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

            // Replying To Preview Banner
            if (replyingToMessage != null) {
                ReplyPreviewBanner(
                    replySender = if (replyingToMessage!!.isMine) "You" else match.user.name,
                    replyText = replyingToMessage!!.text,
                    onCancel = { replyingToMessage = null }
                )
            }

            // Bottom Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRecordingVoiceNote) {
                    // Active In-Chat Voice Note Recording Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SparkRose.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(SparkRose)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recording voice message...",
                                style = MaterialTheme.typography.labelSmall,
                                color = SparkRose
                            )
                            Text(
                                text = "0:0$voiceRecordSeconds",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Cancel / Discard
                        IconButton(
                            onClick = {
                                isRecordingVoiceNote = false
                                voiceRecordSeconds = 0
                            },
                            modifier = Modifier.testTag("chat_voice_cancel_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Discard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Send Voice Note
                        FilledIconButton(
                            onClick = {
                                val dur = voiceRecordSeconds.coerceAtLeast(3)
                                onSendVoiceMessage(dur)
                                isRecordingVoiceNote = false
                                voiceRecordSeconds = 0
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = SparkRose,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("chat_voice_send_button")
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send voice message", modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Message text input (Sticker picker INSIDE the composer, right end)
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Message ${match.user.name}...", style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                IconButton(
                                    onClick = onOpenStickerPicker,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("chat_sticker_button")
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

                        // Mic stays OUTSIDE the composer, on its right (voice message option)
                        IconButton(
                            onClick = {
                                isRecordingVoiceNote = true
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (inputText.isBlank()) SparkPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("chat_voice_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Record Voice Message",
                                tint = if (inputText.isBlank()) SparkPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Send Button
                        if (inputText.isNotBlank()) {
                            FilledIconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        onSendMessage(
                                            inputText,
                                            replyingToMessage?.text?.take(60),
                                            if (replyingToMessage != null) (if (replyingToMessage!!.isMine) "You" else match.user.name) else null
                                        )
                                        inputText = ""
                                        replyingToMessage = null
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
        }
    }

    // Chat Games Section Modal Sheet
    if (showGamePicker) {
        var gamesSectionTab by remember { mutableIntStateOf(0) } // 0: Truth or Dare, 1: Premium Games
        var todMode by remember { mutableStateOf("SYSTEM") } // "SYSTEM" or "CUSTOM"
        var selectedCategory by remember { mutableStateOf("All") }
        var customPromptType by remember { mutableStateOf("TRUTH") } // "TRUTH" or "DARE"
        var customQuestionText by remember { mutableStateOf("") }

        val filteredPrompts = remember(selectedCategory, prompts) {
            if (selectedCategory == "All") prompts else prompts.filter { it.category == selectedCategory }
        }

        ModalBottomSheet(
            onDismissRequest = { showGamePicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Chat Games with ${match.user.name} 🎮",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Free Truth or Dare & Premium interactive games",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showGamePicker = false }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section Tabs (Truth or Dare vs Premium Games)
                TabRow(
                    selectedTabIndex = gamesSectionTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = gamesSectionTab == 0,
                        onClick = { gamesSectionTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Truth or Dare", fontWeight = FontWeight.Bold)
                                Surface(shape = CircleShape, color = ActionLike.copy(alpha = 0.2f)) {
                                    Text("FREE", fontSize = 10.sp, color = ActionLike, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    )
                    Tab(
                        selected = gamesSectionTab == 1,
                        onClick = { gamesSectionTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Premium Games", fontWeight = FontWeight.Bold)
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = SparkGold, modifier = Modifier.size(14.dp))
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (gamesSectionTab) {
                    0 -> {
                        // TRUTH OR DARE SECTION
                        // Toggle between System Prompt & Custom Question
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = todMode == "SYSTEM",
                                onClick = { todMode = "SYSTEM" },
                                label = { Text("✨ System Prompts") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SparkPurple,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = todMode == "CUSTOM",
                                onClick = { todMode = "CUSTOM" },
                                label = { Text("✍️ Custom Question") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SparkRose,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (todMode == "SYSTEM") {
                            // Category filter chips
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val cats = listOf("All", "Flirty", "Funny", "Deep", "First Date")
                                items(cats) { cat ->
                                    SuggestionChip(
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat, fontSize = 12.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (selectedCategory == cat) QuickyPurple.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = if (selectedCategory == cat) QuickyPurple else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick random buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val truthPrompt = prompts.filter { it.type == "TRUTH" }.randomOrNull() ?: prompts.first()
                                        onSendPrompt(truthPrompt)
                                        showGamePicker = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SparkPurple),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎲 Random Truth", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val darePrompt = prompts.filter { it.type == "DARE" }.randomOrNull() ?: prompts.last()
                                        onSendPrompt(darePrompt)
                                        showGamePicker = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SparkRose),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🔥 Random Dare", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(
                                modifier = Modifier.height(260.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredPrompts) { prompt ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSendPrompt(prompt)
                                                showGamePicker = false
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (prompt.type == "TRUTH") SparkPurple else SparkRose
                                                    ) {
                                                        Text(
                                                            text = prompt.type,
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "• ${prompt.category}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = prompt.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                            IconButton(onClick = {
                                                onSendPrompt(prompt)
                                                showGamePicker = false
                                            }) {
                                                Icon(Icons.Filled.Send, contentDescription = "Send", tint = SparkRose)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // CUSTOM QUESTION INPUT SECTION
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Write your own question or challenge:",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = customPromptType == "TRUTH",
                                            onClick = { customPromptType = "TRUTH" },
                                            label = { Text("Truth 🎲") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SparkPurple,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                        FilterChip(
                                            selected = customPromptType == "DARE",
                                            onClick = { customPromptType = "DARE" },
                                            label = { Text("Dare 🔥") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SparkRose,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = customQuestionText,
                                        onValueChange = { customQuestionText = it },
                                        placeholder = {
                                            Text(
                                                if (customPromptType == "TRUTH")
                                                    "e.g. What's the wildest adventure you've ever had?"
                                                else
                                                    "e.g. Send a 5-second voice impression of a celebrity!"
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        minLines = 3,
                                        maxLines = 4
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            if (customQuestionText.isNotBlank()) {
                                                val customPrompt = TruthOrDarePrompt(
                                                    id = "custom_${System.currentTimeMillis()}",
                                                    category = "Custom",
                                                    type = customPromptType,
                                                    text = customQuestionText.trim()
                                                )
                                                onSendPrompt(customPrompt)
                                                customQuestionText = ""
                                                showGamePicker = false
                                            }
                                        },
                                        enabled = customQuestionText.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (customPromptType == "TRUTH") SparkPurple else SparkRose
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Send Custom $customPromptType Challenge", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // PREMIUM GAMES SECTION (Locked for free users!)
                        LazyColumn(
                            modifier = Modifier.height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SparkGold.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Stars, contentDescription = null, tint = SparkGold, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = if (isPremium) "Quicky Gold Active: All multiplayer games unlocked!" else "Premium Games are locked for free users. Upgrade to unlock all!",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // 1. 2-Player Ludo Arena
                            item {
                                PremiumGameCard(
                                    title = "2-Player Ludo Arena 🎲",
                                    description = "Real-time dice rolling showdown right inside chat. 57 steps to victory!",
                                    isLocked = !isPremium,
                                    onClick = {
                                        if (isPremium) {
                                            showGamePicker = false
                                            onOpenLudo()
                                        } else {
                                            onOpenPremiumStore()
                                        }
                                    }
                                )
                            }

                            // 2. Speed Trivia 1v1
                            item {
                                PremiumGameCard(
                                    title = "Speed Trivia 1v1 🧠",
                                    description = "60-second rapid-fire knowledge faceoff with live score tracking.",
                                    isLocked = !isPremium,
                                    onClick = {
                                        if (isPremium) {
                                            onSendMessage("Let's play Speed Trivia 1v1! 🧠", null, null)
                                            showGamePicker = false
                                        } else {
                                            onOpenPremiumStore()
                                        }
                                    }
                                )
                            }

                            // 3. Couples Dilemma
                            item {
                                PremiumGameCard(
                                    title = "Couples Dilemma 🤔",
                                    description = "Intriguing ethical and relationship dilemmas to test your synergy.",
                                    isLocked = !isPremium,
                                    onClick = {
                                        if (isPremium) {
                                            onSendMessage("I picked Couples Dilemma for us! 🤔", null, null)
                                            showGamePicker = false
                                        } else {
                                            onOpenPremiumStore()
                                        }
                                    }
                                )
                            }

                            // 4. Would You Rather (Spicy)
                            item {
                                PremiumGameCard(
                                    title = "Would You Rather (Exclusive) 🎭",
                                    description = "Unfiltered moral and humorous hypothetical questions.",
                                    isLocked = !isPremium,
                                    onClick = {
                                        if (isPremium) {
                                            onSendMessage("Starting Would You Rather! 🎭", null, null)
                                            showGamePicker = false
                                        } else {
                                            onOpenPremiumStore()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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

@Composable
fun PremiumGameCard(
    title: String,
    description: String,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isLocked) androidx.compose.foundation.BorderStroke(1.dp, SparkGold.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (isLocked) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SparkGold.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = SparkGold, modifier = Modifier.size(11.dp))
                                Text("LOCKED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SparkGold)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ActionLike.copy(alpha = 0.2f)
                        ) {
                            Text("UNLOCKED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ActionLike, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            IconButton(onClick = onClick) {
                if (isLocked) {
                    Icon(Icons.Filled.Lock, contentDescription = "Locked game", tint = SparkGold)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play game", tint = QuickyPurple)
                }
            }
        }
    }
}
