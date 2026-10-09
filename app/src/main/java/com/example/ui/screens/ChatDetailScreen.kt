package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.components.CameraPermissionRationaleDialog
import com.example.ui.components.SnapCameraDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.AppContent
import com.example.data.Analytics
import com.example.model.*
import com.example.ui.components.ChatBubble
import com.example.ui.components.ChatHeaderBannerAd
import com.example.ui.components.QuickyGamesIcon
import com.example.ui.components.QuickyStickerIcon
import com.example.ui.components.ReplyPreviewBanner
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    match: MatchItem,
    messages: List<ChatMessage>,
    prompts: List<TruthOrDarePrompt>,
    onBack: () -> Unit,
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendPrompt: (TruthOrDarePrompt) -> Unit,
    onAnswerGame: (messageId: String, answerText: String, responseType: String, voiceDurationSeconds: Int?) -> Unit,
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
    /** v3.3.7: send a captured camera photo as a view-once snap. */
    onSendSnap: (ByteArray) -> Unit = {},
    /** v3.3.7: open a received unviewed snap in the fullscreen viewer. */
    onOpenSnap: (ChatMessage) -> Unit = {},
    /** PRD v2.3 §26/§27 — free users see the fixed banner under the header. */
    showBannerAd: Boolean = false,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showGamePicker by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var isRecordingVoiceNote by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableIntStateOf(0) }
    // v2.1 §3.3 — tracked list state so new messages auto-scroll into view.
    val chatListState = rememberLazyListState()

    // ---- Snapchat-style Snap camera with filters & editing ----
    var showSnapCameraDialog by remember { mutableStateOf(false) }
    var showCameraRationaleDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showSnapCameraDialog = true
        } else {
            showCameraRationaleDialog = true
        }
    }

    LaunchedEffect(isRecordingVoiceNote) {
        if (isRecordingVoiceNote) {
            voiceRecordSeconds = 0
            while (isRecordingVoiceNote) {
                delay(1000)
                voiceRecordSeconds++
            }
        }
    }

    // ------------------------------------------------------------------
    // v2.3 §36–§39 — WhatsApp-style arrival behavior:
    //  · OUTGOING message  → ALWAYS scroll to the newest message.
    //  · INCOMING message   → auto-scroll only when the user is already
    //    near the bottom; otherwise a floating "N new messages ↓" pill lets
    //    them jump down on demand (reading history is never interrupted).
    // Only COMPLETED list changes trigger effects — never recomposition.
    // ------------------------------------------------------------------
    val isNearBottom by remember {
        derivedStateOf {
            val info = chatListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 2
        }
    }
    var unseenMessages by remember { mutableIntStateOf(0) }
    var lastKnownCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) {
            lastKnownCount = 0
            unseenMessages = 0
            return@LaunchedEffect
        }
        val previousCount = lastKnownCount
        lastKnownCount = messages.size
        if (previousCount >= messages.size) return@LaunchedEffect // no new arrivals

        if (previousCount == 0) {
            // Fresh open: land instantly on the newest message (WhatsApp).
            chatListState.scrollToItem(messages.size) // +1: header tile at index 0
            unseenMessages = 0
        } else {
            val newestIsMine = messages.last().isMine
            if (newestIsMine || isNearBottom) {
                unseenMessages = 0
                chatListState.animateScrollToItem(messages.size)
            } else {
                // Reading history: count it, never yank the user down (§38).
                unseenMessages += messages.size - previousCount
            }
        }
    }

    // §40 — with the keyboard open, the newest message stays visible: the
    // composer already rides above the IME (imePadding below); this keeps
    // the bottom of the conversation pinned whenever the IME appears while
    // the user is at/near the bottom (or just sent a message).
    val density = LocalDensity.current
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeOpen, messages.size) {
        if (imeOpen && messages.isNotEmpty() &&
            (messages.last().isMine || isNearBottom)
        ) {
            chatListState.animateScrollToItem(messages.size)
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
                        // v3.1: remote (seeded / uploaded) photo URLs render
                        // through Coil; bundled drawables stay the fallback.
                        val remotePhoto = match.user.photoUris.firstOrNull()
                        Box(modifier = Modifier.size(40.dp)) {
                            if (remotePhoto != null) {
                                AsyncImage(
                                    model = remotePhoto,
                                    contentDescription = match.user.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                val photo = match.user.photoResIds.firstOrNull() ?: R.drawable.img_profile_sarah
                                Image(
                                    painter = painterResource(id = photo),
                                    contentDescription = match.user.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
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
                // v3.2 (PRD §7 — chat architecture): FIXED header (TopAppBar,
                // outside this Column) + independently scrollable message list
                // + composer pinned to the keyboard. navigationBarsPadding FIRST,
                // imePadding SECOND: keyboard closed → composer clears the
                // gesture bar; keyboard open → composer rides flush against
                // the IME (the two inset modifiers share consumption, so this
                // NEVER double-pads — the old composer-level
                // navigationBarsPadding below imePadding was what left the
                // large blank strip between the composer and the keyboard).
                .navigationBarsPadding()
                .imePadding()
        ) {
            // PRD v2.3 §26/§43 — advertising banner pinned DIRECTLY under the
            // chat header, as a fixed chat-header element (never inside the
            // scrolling message list). Free users only — the caller resolves
            // eligibility through the centralized entitlement check (§27).
            if (showBannerAd) {
                ChatHeaderBannerAd()
            }

            // Chat Messages List — tap on the background dismisses the
            // keyboard (v2.1 §3.5) and the bottom padding keeps the last
            // bubble clear of the composer.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .dismissKeyboardOnTap(),
                    reverseLayout = false,
                    // v2.1 §3.3 spacing grid: 6dp between bubbles, 4dp for
                    // consecutive same-sender bubbles, 12dp between sender
                    // blocks (handled per-item below).
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
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

                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                        val previous = messages.getOrNull(index - 1)
                        val sameSenderAsPrevious = previous != null && previous.isMine == msg.isMine
                        ChatBubble(
                            message = msg,
                            modifier = if (sameSenderAsPrevious) Modifier.padding(top = 2.dp) else Modifier.padding(top = 8.dp),
                            onReactionClick = { emoji -> onAddReaction(msg.id, emoji) },
                            onAnswerGame = { answer, responseType, voiceDuration -> onAnswerGame(msg.id, answer, responseType, voiceDuration) },
                            onSwipeToReply = { replyingToMessage = msg },
                            onOpenSnap = onOpenSnap
                        )
                    }
                }

                // v2.3 §38 — "N new messages ↓" jump pill: appears only
                // while newer messages sit BELOW the current viewport; one
                // tap scrolls to the newest (never auto-interrupts reading).
                if (unseenMessages > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(50),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .testTag("chat_new_messages_pill"),
                        onClick = {
                            unseenMessages = 0
                            coroutineScope.launch {
                                chatListState.animateScrollToItem(messages.size)
                            }
                        }
                    ) {
                        Text(
                            text = "$unseenMessages new message${if (unseenMessages == 1) "" else "s"} ↓",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
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
                items(AppContent.icebreakerSuggestions.take(2)) { suggestion ->
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    // v3.2: NO navigationBarsPadding here — the root Column's
                    // navigationBarsPadding().imePadding() already seats the
                    // composer flush above the gesture bar / keyboard. A local
                    // navbar pad here double-padded and left a large blank
                    // strip between the composer and the IME (PRD §6 Problem A).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Message text input — camera (v3.3.7 snap capture)
                        // + sticker picker both INSIDE the composer, right end.
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Message ${match.user.name}...", style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Snapchat-style Camera: opens live viewfinder with filters & editing
                                    IconButton(
                                        onClick = {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPermission) {
                                                showSnapCameraDialog = true
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("chat_snap_camera_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PhotoCamera,
                                            contentDescription = "Send photo snap",
                                            tint = SparkRose,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
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
        Analytics.log(Analytics.TRUTH_OR_DARE_OPENED, "match_id" to match.id)
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

                // ----------------------------------------------------------
                // v3.2 (PRD §10) — section tabs as two EQUAL selector cards:
                // the FREE status is a compact pill attached to the Truth or
                // Dare card (never a floating element squeezed between
                // tabs), Premium Games carries the gold lock. Same width,
                // same height, same radius, clearly different selected state.
                // ----------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GameSectionCard(
                        title = "Truth or Dare",
                        badge = "FREE",
                        selected = gamesSectionTab == 0,
                        onClick = { gamesSectionTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    GameSectionCard(
                        title = "Premium Games",
                        lockIcon = true,
                        selected = gamesSectionTab == 1,
                        onClick = {
                            if (gamesSectionTab != 1) {
                                Analytics.log(Analytics.PREMIUM_GAMES_OPENED, "match_id" to match.id)
                            }
                            gamesSectionTab = 1
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (gamesSectionTab) {
                    0 -> {
                        // TRUTH OR DARE SECTION
                        // ------------------------------------------------------
                        // v3.2 (PRD §11/§12) — content-source selectors as two
                        // EQUAL cards (icon + title + caption), replacing the
                        // tall, unevenly-wrapping filter chips. Selected state
                        // is unmistakable: tinted container + colored border.
                        // ------------------------------------------------------
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PromptSourceCard(
                                icon = { tint ->
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                                },
                                title = "System Prompts",
                                caption = "Automatically",
                                selected = todMode == "SYSTEM",
                                accent = QuickyPurple,
                                onClick = { todMode = "SYSTEM" },
                                modifier = Modifier.weight(1f)
                            )
                            PromptSourceCard(
                                icon = { tint ->
                                    Icon(Icons.Filled.Edit, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                                },
                                title = "Custom Question",
                                caption = "Write your own",
                                selected = todMode == "CUSTOM",
                                accent = SparkRose,
                                onClick = {
                                    if (todMode != "CUSTOM") {
                                        Analytics.log(Analytics.CUSTOM_QUESTION_SELECTED, "match_id" to match.id)
                                    }
                                    todMode = "CUSTOM"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (todMode == "SYSTEM") {
                            // Category filter pills — horizontally scrollable
                            // (v3.2 PRD §13): consistent height + vertical
                            // alignment, a clearly distinct selected state
                            // (solid purple + check) and no clipping; long
                            // category names stay readable and never wrap.
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val cats = listOf("All", "Flirty", "Funny", "Deep", "First Date")
                                items(cats) { cat ->
                                    val catSelected = selectedCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (catSelected) QuickyPurple
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (catSelected) QuickyPurple
                                            else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 34.dp)
                                            .clickable { selectedCategory = cat }
                                            .testTag("tod_category_${cat.replace(" ", "_").lowercase()}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            if (catSelected) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Text(
                                                text = cat,
                                                fontSize = 12.sp,
                                                fontWeight = if (catSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (catSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                    }
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
                                        Analytics.log(
                                            Analytics.TRUTH_OR_DARE_PROMPT_SELECTED,
                                            "match_id" to match.id,
                                            "source" to "random_truth"
                                        )
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
                                        Analytics.log(
                                            Analytics.TRUTH_OR_DARE_PROMPT_SELECTED,
                                            "match_id" to match.id,
                                            "source" to "random_dare"
                                        )
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
                                                Analytics.log(
                                                    Analytics.TRUTH_OR_DARE_PROMPT_SELECTED,
                                                    "match_id" to match.id,
                                                    "source" to "list",
                                                    "category" to prompt.category
                                                )
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

    // Snapchat-style Fullscreen In-App Camera Dialog
    if (showSnapCameraDialog) {
        SnapCameraDialog(
            recipientName = match.user.name,
            onDismiss = { showSnapCameraDialog = false },
            onSendSnap = { bytes ->
                onSendSnap(bytes)
                showSnapCameraDialog = false
            }
        )
    }

    // Camera Permission Rationale Dialog
    if (showCameraRationaleDialog) {
        CameraPermissionRationaleDialog(
            onDismiss = { showCameraRationaleDialog = false },
            onRequestPermission = {
                showCameraRationaleDialog = false
                val activity = context as? Activity
                val permanentlyDenied = activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                if (permanentlyDenied) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )
    }
}

// =====================================================================
// GAMES DRAWER SELECTORS (v3.2 PRD §10–§12)
// =====================================================================

/**
 * Section tab as an EQUAL selector card (PRD §10): "Truth or Dare" with a
 * compact FREE status pill and "Premium Games" with the gold lock. Both
 * cards share identical width (weight), height (IntrinsicSize row), corner
 * radius, padding and text hierarchy — the selected state is unmistakable
 * (tinted container + colored border + check), and the FREE badge never
 * floats between tabs or collides with a divider.
 */
@Composable
private fun GameSectionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    lockIcon: Boolean = false
) {
    val accent = if (badge != null) CassySuccess else SparkGold
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("game_section_${title.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (badge != null) {
                // Compact status pill attached to the card (PRD §10 Badge).
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.18f),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            } else if (lockIcon) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Premium",
                    tint = SparkGold,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Content-source selector card (PRD §11/§12): "System Prompts" / "Custom
 * Question" as equal, compact cards — icon + title + one-line caption.
 * Same width, same height, same radius, same padding, same icon treatment;
 * the selected card is clearly distinguishable (tinted container + accent
 * border + tinted icon).
 */
@Composable
private fun PromptSourceCard(
    icon: @Composable (tint: Color) -> Unit,
    title: String,
    caption: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("prompt_source_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                icon(tint)
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
