package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.VoiceNoteRecorder
import com.example.model.Club
import com.example.model.ClubMember
import com.example.model.ClubMessage
import com.example.model.UserProfile
import com.example.ui.ClubChatMeta
import com.example.ui.components.ClubChatBannerAd
import com.example.ui.components.QuickyStickerIcon
import com.example.ui.components.ReplyPreviewBanner
import com.example.ui.components.SwipeToReplyContainer
import com.example.ui.components.VoiceNotePlayerHolder
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Emoji palette offered on long-press (WhatsApp-style quick reactions). */
private val ReactionPalette = listOf("❤️", "😂", "😮", "😢", "😡", "👍", "🔥")

/** Report reasons — must match the club_reports reason CHECK constraint. */
private val ReportReasons = listOf(
    "TOXIC_LANGUAGE" to "Toxic or abusive language",
    "HARASSMENT" to "Harassment or bullying",
    "SPAM" to "Spam or scam links",
    "OTHER" to "Something else"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClubDetailScreen(
    club: Club,
    messages: List<ClubMessage>,
    isPremium: Boolean,
    currentUserId: String = "user_me",
    activeClubId: String? = null,
    myClubName: String? = null,
    chatMeta: ClubChatMeta = ClubChatMeta(),
    onLoadOlderMessages: () -> Unit = {},
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendVoiceMessage: (audioBytes: ByteArray, durationSeconds: Int) -> Unit,
    onRequestVoiceNote: suspend (ClubMessage) -> File? = { null },
    onOpenStickerPicker: () -> Unit,
    onOpenPremiumStore: () -> Unit,
    onViewMemberProfile: (String) -> Unit,
    onRemoveMember: (clubId: String, memberUserId: String) -> Unit,
    onToggleReaction: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onReportMember: (clubId: String, memberUserId: String, reason: String, details: String) -> Unit = { _, _, _, _ -> },
    onSetMemberSuspended: (clubId: String, memberUserId: String, suspendMember: Boolean) -> Unit = { _, _, _ -> },
    onDeleteClub: (String) -> Unit = {},
    onJoinClub: (String) -> Unit = {},
    onLeaveAndJoinClub: (String) -> Unit = {},
    onLeaveClub: (String) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ClubMessage?>(null) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<ClubMember?>(null) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showSwitchClubConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteClubConfirmDialog by remember { mutableStateOf(false) }
    var isRecordingVoiceNote by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableIntStateOf(0) }
    var reactionTargetMessage by remember { mutableStateOf<ClubMessage?>(null) }
    var reportTargetMember by remember { mutableStateOf<ClubMember?>(null) }
    var memberModerationMenu by remember { mutableStateOf<ClubMember?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ---- Real voice-note recording (v3.3.4 — free for ALL club members) ----
    val voiceRecorder = remember { VoiceNoteRecorder(context) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (voiceRecorder.start()) isRecordingVoiceNote = true
            else Toast.makeText(context, "Microphone busy — try again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is needed to record club voice messages.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---- One shared player for every voice bubble on screen ----
    val voicePlayer = remember { VoiceNotePlayerHolder() }
    var playerState by remember { mutableStateOf<VoiceNotePlayerHolder.PlayerState?>(null) }
    DisposableEffect(Unit) {
        voicePlayer.onStateChange = { playerState = it }
        onDispose {
            voicePlayer.onStateChange = null
            voicePlayer.release()
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

    val isOwner = club.ownerId == currentUserId || club.members.find { it.userId == currentUserId }?.role == "OWNER"
    val isMember = club.members.any { it.userId == currentUserId }
    val myStatus = club.members.find { it.userId == currentUserId }?.status
    val isSuspended = myStatus == "SUSPENDED"

    // ---- Chunked history: auto-load the next older page on scroll-up ----
    // v3.3.4 — history is NEVER fully downloaded; each swipe to the top
    // pulls one 30-message chunk, keeping the previous scroll position.
    val canLoadOlder = chatMeta.hasMoreMessages && !chatMeta.isLoadingOlder && messages.isNotEmpty()
    LaunchedEffect(listState.firstVisibleItemIndex, canLoadOlder) {
        if (canLoadOlder && listState.firstVisibleItemIndex <= 1) {
            onLoadOlderMessages()
        }
    }
    // Jump to the newest message on open / on new arrivals — but keep the
    // reader anchored when an OLDER page prepends above them.
    var firstItemId by remember { mutableStateOf<String?>(null) }
    var listSize by remember { mutableStateOf(0) }
    LaunchedEffect(messages.size) {
        val grew = messages.size > listSize
        val currentFirst = messages.firstOrNull()?.id
        if (listSize > 0 && grew && currentFirst != null && currentFirst != firstItemId) {
            // Older chunk prepended — keep the SAME messages under the thumb.
            val offset = listState.firstVisibleItemIndex
            listState.scrollToItem(messages.size - listSize + offset)
        } else if (grew) {
            // New message arrived at the end (or first page loaded) — follow
            // it only when the reader is already near the bottom.
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (listSize == 0 || lastVisible >= listSize - 3 || listSize == messages.size - 1) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
        firstItemId = currentFirst
        listSize = messages.size
    }

    // ---- "@mention" autocomplete (WhatsApp-style) ----
    val mentionQuery = remember(inputText) {
        val idx = inputText.lastIndexOf('@')
        if (idx < 0) null
        else if (idx > 0 && !inputText[idx - 1].isWhitespace()) null
        else {
            val q = inputText.substring(idx + 1)
            if (q.contains(' ')) null else q
        }
    }
    val mentionSuggestions = if (mentionQuery == null) emptyList() else {
        club.members
            .filter { it.userId != currentUserId && it.userName.isNotBlank() }
            .filter { it.userName.contains(mentionQuery, ignoreCase = true) }
            .take(4)
    }

    fun applyMention(member: ClubMember) {
        val idx = inputText.lastIndexOf('@')
        if (idx >= 0) {
            inputText = inputText.substring(0, idx) + "@${member.userName} "
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // v2.1 §3.3/§3.4 — keyboard pushes only the composer up;
            // the pinned header stays put.
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = QuickyPurple.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(club.logoEmoji, fontSize = 20.sp)
                            }
                        }
                        Column {
                            Text(
                                text = club.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // v2.1 §3.4 — the description lives ONLY in the
                            // header now (1 line). Long descriptions marquee.
                            Text(
                                text = "${club.memberCount}/${club.maxMembers} members · ${club.description}",
                                style = MaterialTheme.typography.labelSmall,
                                color = QuickyPurple,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    velocity = 20.dp
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Members of the club
                    IconButton(
                        onClick = { showMembersSheet = true },
                        modifier = Modifier.testTag("club_members_button")
                    ) {
                        Icon(imageVector = Icons.Outlined.People, contentDescription = "Members", tint = QuickyPurple)
                    }

                    // 3-dots options menu (with Leave the Club option)
                    if (isMember) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("club_menu_button")
                            ) {
                                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Options")
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Members") },
                                    onClick = {
                                        showMenu = false
                                        showMembersSheet = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.People, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Leave the Club", color = ActionPass, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showMenu = false
                                        showLeaveConfirmDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = ActionPass)
                                    }
                                )
                                // v2.1 §3.8 — owner-only club deletion.
                                if (isOwner) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Club", color = ActionPass, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteClubConfirmDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = ActionPass)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // v2.1 §3.6.2 — AdMob banner pinned between the club header and
            // the message list (fixed slot, never scrolls, 60s refresh).
            ClubChatBannerAd()

            if (!isMember) {
                // ---------------------------------------------------------
                // NON-MEMBER PREVIEW (v3.3.4): "View Club" shows the member
                // list, NOT the chat. Club chat is a members-only room —
                // join first, then read + post.
                // ---------------------------------------------------------
                NonMemberClubPreview(
                    club = club,
                    currentUserId = currentUserId,
                    onViewMemberProfile = onViewMemberProfile,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // ---------------------------------------------------------
                // MEMBER CHAT (retained history, chunked on scroll)
                // ---------------------------------------------------------
                if (chatMeta.isLoadingInitial && messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = QuickyPurple)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Loading club chat…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .dismissKeyboardOnTap(),
                        reverseLayout = false
                    ) {
                        // Older-chunk loader slot (auto-triggered on scroll).
                        item(key = "older_loader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    chatMeta.isLoadingOlder -> CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = QuickyPurple
                                    )
                                    chatMeta.hasMoreMessages && messages.isNotEmpty() -> Text(
                                        "↑ Scroll for older messages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    messages.isNotEmpty() -> Text(
                                        "This is the beginning of the club",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        items(messages, key = { it.id }) { msg ->
                            ClubMessageBubble(
                                message = msg,
                                clubMembers = club.members,
                                currentUserId = currentUserId,
                                isPlayingThis = playerState?.messageKey == msg.id && playerState?.isPlaying == true,
                                isPausedThis = playerState?.messageKey == msg.id && playerState?.isPlaying == false,
                                playbackPositionMs = if (playerState?.messageKey == msg.id) playerState?.positionMs ?: 0 else 0,
                                onSwipeToReply = { replyingToMessage = msg },
                                onLongPress = { reactionTargetMessage = msg },
                                onToggleReaction = { emoji -> onToggleReaction(msg.id, emoji) },
                                onPlayVoice = {
                                    scope.launch {
                                        val file = onRequestVoiceNote(msg)
                                        if (file == null) {
                                            Toast.makeText(
                                                context,
                                                "Voice note unavailable — check your connection.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            voicePlayer.toggle(context, msg.id, file)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Quick Banter Chips (same bar styling as the personal chat)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("🎲 Let's play Ludo!", "👋 Hey everyone", "🎉 GG WP", "🔥 Awesome")) { chip ->
                        SuggestionChip(
                            onClick = { onSendMessage(chip, null, null) },
                            label = { Text(chip, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }

            if (!isMember) {
                // Non-member footer: join if space available
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!club.isFull) {
                            if (activeClubId != null && activeClubId != club.id) {
                                Text(
                                    text = "You are in ${myClubName ?: "another club"}. A user can belong to only 1 club at a time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showSwitchClubConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("detail_leave_and_join_club_button")
                                ) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Leave Existing Club & Join ${club.name}", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    text = "Join to unlock the club chat — you're viewing the member list (${club.memberCount}/${club.maxMembers} members)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onJoinClub(club.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("detail_join_club_button")
                                ) {
                                    Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Join ${club.name} (Space Available)", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Surface(
                                color = ActionPass.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Club is full (15/15 members). You can join when a slot frees up.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ActionPass,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else if (isSuspended) {
                // v3.3.4 — the owner suspended this member from messaging:
                // they still SEE the chat, but cannot post.
                Surface(
                    color = ActionPass.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.VoiceOverOff,
                            contentDescription = null,
                            tint = ActionPass,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "The club Owner suspended you from messaging here. You can still read the chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ActionPass
                        )
                    }
                }
            } else {
                // Replying To Banner
                if (replyingToMessage != null) {
                    ReplyPreviewBanner(
                        replySender = if (replyingToMessage!!.isMine) "You" else replyingToMessage!!.senderName,
                        replyText = replyingToMessage!!.text.ifBlank { replyingToMessage!!.stickerEmoji ?: "Voice message" },
                        onCancel = { replyingToMessage = null }
                    )
                }

                // Mention autocomplete — sits just above the composer.
                if (mentionSuggestions.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            mentionSuggestions.forEach { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { applyMention(member) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(QuickyPurple.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(member.userName.take(1), fontWeight = FontWeight.Bold, color = QuickyPurple)
                                    }
                                    Column {
                                        Text(
                                            member.userName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            member.characterBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                // Chat Input Bar — same layout as the personal chat
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRecordingVoiceNote) {
                        // Active In-Chat Voice Note Recording Bar (REAL mic)
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
                                    text = "Recording club voice message...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SparkRose
                                )
                                Text(
                                    text = formatVoiceSeconds(voiceRecordSeconds),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Discard button
                            IconButton(
                                onClick = {
                                    voiceRecorder.discard()
                                    isRecordingVoiceNote = false
                                    voiceRecordSeconds = 0
                                },
                                modifier = Modifier.testTag("club_voice_cancel_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Discard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            // Send Voice Message button
                            FilledIconButton(
                                onClick = {
                                    val note = voiceRecorder.stop()
                                    isRecordingVoiceNote = false
                                    voiceRecordSeconds = 0
                                    if (note != null) {
                                        onSendVoiceMessage(note.bytes, note.durationSeconds)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Recording too short — hold the chat a bit longer!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = SparkRose,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("club_voice_record_send")
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = "Send Voice Message", modifier = Modifier.size(20.dp))
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
                                placeholder = { Text("Message ${club.name}...", style = MaterialTheme.typography.bodyMedium) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = onOpenStickerPicker,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("club_sticker_button")
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
                                    .testTag("club_message_input"),
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                maxLines = 4
                            )

                            // Mic stays OUTSIDE the composer, on its right —
                            // FREE for every club member (v3.3.4).
                            IconButton(
                                onClick = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (inputText.isBlank()) SparkPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                                    .testTag("club_voice_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Record Voice Message",
                                    tint = if (inputText.isBlank()) SparkPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Send Button (same as the personal chat)
                            if (inputText.isNotBlank()) {
                                FilledIconButton(
                                    onClick = {
                                        if (inputText.isNotBlank()) {
                                            onSendMessage(
                                                inputText,
                                                replyingToMessage?.text?.ifBlank { replyingToMessage?.stickerEmoji }?.take(60),
                                                if (replyingToMessage != null) (if (replyingToMessage!!.isMine) "You" else replyingToMessage!!.senderName) else null
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
                                        .testTag("club_send_button")
                                ) {
                                    Icon(Icons.Filled.Send, contentDescription = "Send message", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =====================================================================
    //  SHEETS & DIALOGS
    // =====================================================================

    // Reaction picker (long-press on any message)
    reactionTargetMessage?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { reactionTargetMessage = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "React to message",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    target.text.ifBlank { target.stickerEmoji ?: "Voice message" }.take(60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReactionPalette.forEach { emoji ->
                        val mine = target.reactions.any { it.userId == currentUserId && it.emoji == emoji }
                        Surface(
                            shape = CircleShape,
                            color = if (mine) QuickyPink.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (mine) BorderStroke(1.dp, QuickyPink) else null,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.clickable {
                                    onToggleReaction(target.id, emoji)
                                    reactionTargetMessage = null
                                }
                            ) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Members Bottom Sheet (PRD Section 17) — with v3.3.4 moderation:
    // anyone can REPORT a member; the owner can SUSPEND / REMOVE one.
    if (showMembersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMembersSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("club_members_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Club Members (${club.memberCount} / ${club.maxMembers})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showMembersSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(club.members) { member ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onViewMemberProfile(member.userId) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (member.userAvatarRes != null) {
                                        Image(
                                            painter = painterResource(id = member.userAvatarRes),
                                            contentDescription = member.userName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(member.userName.take(1), fontWeight = FontWeight.Bold, color = QuickyPurple)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = member.userName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (member.isVerified) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = ActionVerified,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        if (member.role == "OWNER") {
                                            Surface(
                                                color = QuickyGold.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "👑 OWNER",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                                    color = QuickyGold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (member.status == "SUSPENDED") {
                                            Surface(
                                                color = ActionPass.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "🔇 SUSPENDED",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                                    color = ActionPass,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "${member.characterBadge} • Joined ${member.joinedAt}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Per-member moderation menu (v3.3.4).
                                // v3.3.5: the dropdown is anchored to THIS
                                // row's button — composed at the screen root
                                // before, it floated UNDER the members sheet.
                                if (member.userId != currentUserId) {
                                    Box {
                                        IconButton(
                                            onClick = { memberModerationMenu = member },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = "Member options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (memberModerationMenu?.id == member.id) {
                                            DropdownMenu(
                                                expanded = true,
                                                onDismissRequest = { memberModerationMenu = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("🚩 Report Member") },
                                                    onClick = {
                                                        memberModerationMenu = null
                                                        reportTargetMember = member
                                                    },
                                                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = ActionPass) }
                                                )
                                                if (isOwner) {
                                                    if (member.status == "SUSPENDED") {
                                                        DropdownMenuItem(
                                                            text = { Text("✅ Allow messaging again", color = ActionVerified, fontWeight = FontWeight.Bold) },
                                                            onClick = {
                                                                onSetMemberSuspended(club.id, member.userId, false)
                                                                memberModerationMenu = null
                                                            },
                                                            leadingIcon = { Icon(Icons.Filled.VoiceOverOff, contentDescription = null, tint = ActionVerified) }
                                                        )
                                                    } else {
                                                        DropdownMenuItem(
                                                            text = { Text("🔇 Suspend from messaging", color = ActionPass, fontWeight = FontWeight.Bold) },
                                                            onClick = {
                                                                onSetMemberSuspended(club.id, member.userId, true)
                                                                memberModerationMenu = null
                                                            },
                                                            leadingIcon = { Icon(Icons.Outlined.VoiceOverOff, contentDescription = null, tint = ActionPass) }
                                                        )
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text("Remove from club", color = ActionPass) },
                                                        onClick = {
                                                            memberModerationMenu = null
                                                            memberToRemove = member
                                                        },
                                                        leadingIcon = { Icon(Icons.Outlined.PersonRemove, contentDescription = null, tint = ActionPass) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Leave Club Option for members
                    if (isMember) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    showMembersSheet = false
                                    showLeaveConfirmDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionPass),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("detail_leave_club_button")
                            ) {
                                Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = ActionPass, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Leave Club")
                            }
                        }
                    }
                }
            }
        }
    }

    // Report member dialog (v3.3.4). The per-member moderation dropdown now
    // lives anchored inside each member row of the members sheet (v3.3.5).
    reportTargetMember?.let { target ->
        var reportReason by remember(target.id) { mutableStateOf("TOXIC_LANGUAGE") }
        var reportDetails by remember(target.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reportTargetMember = null },
            title = { Text("Report ${target.userName}?") },
            text = {
                Column {
                    Text(
                        "The club Owner is notified and can suspend or remove this member.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ReportReasons.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = value }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = reportReason == value,
                                onClick = { reportReason = value }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it },
                        placeholder = { Text("Optional details…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportMember(club.id, target.userId, reportReason, reportDetails)
                        reportTargetMember = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTargetMember = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for switching club (One club at a time rule)
    if (showSwitchClubConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchClubConfirmDialog = false },
            title = { Text("Leave & Join New Club?") },
            text = {
                Text("A user can join in only ONE club at a time. In order to join \"${club.name}\", you must first leave your current club (\"${myClubName ?: "Existing Club"}\"). Would you like to leave your current club and join ${club.name}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSwitchClubConfirmDialog = false
                        onLeaveAndJoinClub(club.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple)
                ) {
                    Text("Leave & Join ${club.name}")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchClubConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for leaving club
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("Leave ${club.name}?") },
            text = {
                Text("Are you sure you want to leave ${club.name}? Once you leave, you can join any other club.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveClub(club.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) {
                    Text("Leave Club")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for removing a member
    memberToRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove Member?") },
            text = { Text("Are you sure you want to remove ${target.userName} from ${club.name}? The owner can remove members at any desired time.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveMember(club.id, target.userId)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // v2.1 §3.8 — owner-only club deletion with explicit cascade warning.
    if (showDeleteClubConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteClubConfirmDialog = false },
            title = { Text("Delete this club?") },
            text = {
                Text(
                    "All ${club.memberCount} members will be removed and every club message " +
                            "will be permanently deleted. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteClubConfirmDialog = false
                        onDeleteClub(club.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) {
                    Text("Delete Club")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteClubConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================================================
//  MESSAGE BUBBLE (v3.3.4) — reply preview, sticker, text with @mention
//  highlights, REAL voice playback, reaction chips + long-press picker.
// ==========================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClubMessageBubble(
    message: ClubMessage,
    clubMembers: List<ClubMember>,
    currentUserId: String,
    isPlayingThis: Boolean,
    isPausedThis: Boolean,
    playbackPositionMs: Int,
    onSwipeToReply: () -> Unit,
    onLongPress: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onPlayVoice: () -> Unit
) {
    SwipeToReplyContainer(onSwipeToReply = onSwipeToReply) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!message.isMine && message.messageType != "SYSTEM") {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (message.senderAvatarRes != null) {
                        Image(
                            painter = painterResource(id = message.senderAvatarRes),
                            contentDescription = message.senderName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(message.senderName.take(1), fontWeight = FontWeight.Bold, color = QuickyPurple)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Bubble styled exactly like the personal chat
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isMine) 18.dp else 4.dp,
                    bottomEnd = if (message.isMine) 4.dp else 18.dp
                ),
                color = when {
                    message.messageType == "SYSTEM" -> QuickyPurple.copy(alpha = 0.10f)
                    message.isMine -> SparkRose
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (!message.isMine && message.messageType != "SYSTEM") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = message.senderName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = QuickyPink
                            )
                            if (message.senderBadge != null) {
                                Text(
                                    text = "• ${message.senderBadge}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Reply preview inside message
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

                    if (message.stickerEmoji != null) {
                        Text(
                            text = message.stickerEmoji,
                            fontSize = 36.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (message.text.isNotBlank()) {
                        MentionHighlightedText(
                            text = message.text,
                            memberNames = clubMembers.map { it.userName },
                            isMine = message.isMine || message.messageType == "SYSTEM"
                        )
                    }

                    // REAL voice note (v3.3.4): play/pause + progress bar.
                    if (message.voiceDurationSeconds != null) {
                        VoiceNoteBubble(
                            message = message,
                            isPlaying = isPlayingThis,
                            isPaused = isPausedThis,
                            positionMs = playbackPositionMs,
                            onPlayToggle = onPlayVoice
                        )
                    }

                    // Reaction chips under the bubble (v3.3.4)
                    if (message.reactions.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            message.reactions
                                .groupBy { it.emoji }
                                .entries
                                .sortedByDescending { it.value.size }
                                .forEach { (emoji, reactions) ->
                                    val mineToggle = reactions.any { it.userId == currentUserId }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (mineToggle) QuickyPink.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                        border = if (mineToggle) BorderStroke(1.dp, QuickyPink) else null,
                                        modifier = Modifier.clickable { onToggleReaction(emoji) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(emoji, fontSize = 12.sp)
                                            if (reactions.size > 1) {
                                                Text(
                                                    "${reactions.size}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                        }
                    }

                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

// ==========================================================================
//  VOICE BUBBLE — play / pause + linear progress (v3.3.4)
// ==========================================================================
@Composable
private fun VoiceNoteBubble(
    message: ClubMessage,
    isPlaying: Boolean,
    isPaused: Boolean,
    positionMs: Int,
    onPlayToggle: () -> Unit
) {
    val duration = message.voiceDurationSeconds ?: 0
    val progress = if (duration > 0 && positionMs > 0) {
        (positionMs.toFloat() / (duration * 1000f)).coerceIn(0f, 1f)
    } else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        IconButton(onClick = onPlayToggle, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (isPlaying || isPaused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
                tint = if (message.isMine) Color.White else QuickyPurple,
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Text(
                text = "🎙 Voice message • ${formatVoiceSeconds(duration)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPlaying) {
                LinearProgressIndicator(
                    progress = { if (progress > 0f) progress else 0.05f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    color = if (message.isMine) Color.White else QuickyPurple,
                    trackColor = if (message.isMine) Color.White.copy(alpha = 0.3f) else QuickyPurple.copy(alpha = 0.2f)
                )
            }
        }
    }
}

// ==========================================================================
//  NON-MEMBER PREVIEW (v3.3.4) — "View Club" shows members, not the chat.
// ==========================================================================
@Composable
private fun NonMemberClubPreview(
    club: Club,
    currentUserId: String,
    onViewMemberProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Club identity card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = QuickyPurple.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(club.logoEmoji, fontSize = 40.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    club.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    club.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = QuickyPink.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "${club.category} • ${club.memberCount}/${club.maxMembers} members",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = QuickyPink,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "🔒 Club chat is members-only — join to see and send messages.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Members (${club.memberCount})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(club.members) { member ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewMemberProfile(member.userId) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (member.userAvatarRes != null) {
                                Image(
                                    painter = painterResource(id = member.userAvatarRes),
                                    contentDescription = member.userName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(member.userName.take(1), fontWeight = FontWeight.Bold, color = QuickyPurple)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    member.userName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                if (member.role == "OWNER") {
                                    Surface(
                                        color = QuickyGold.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "👑 OWNER",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                            color = QuickyGold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "${member.characterBadge} • Joined ${member.joinedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================================
//  HELPERS
// ==========================================================================

/** "0:07" style clock for voice durations. */
private fun formatVoiceSeconds(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * Body text with WhatsApp-style "@Name" highlighting — every mention that
 * matches a real member name renders pink + bold.
 */
@Composable
private fun MentionHighlightedText(
    text: String,
    memberNames: List<String>,
    isMine: Boolean
) {
    val mentionColor = if (isMine) Color.White else QuickyPink
    val baseColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(text, memberNames, isMine) {
        buildAnnotatedString {
            append(text)
            memberNames
                .filter { it.isNotBlank() }
                .forEach { name ->
                    var from = 0
                    while (true) {
                        val idx = text.indexOf("@$name", from, ignoreCase = true)
                        if (idx < 0) break
                        addStyle(
                            SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold),
                            idx,
                            idx + name.length + 1
                        )
                        from = idx + name.length + 1
                    }
                }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = baseColor
    )
}
