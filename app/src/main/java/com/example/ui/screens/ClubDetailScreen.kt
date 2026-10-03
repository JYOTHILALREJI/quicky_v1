package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonRemove
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Club
import com.example.model.ClubMember
import com.example.model.ClubMessage
import com.example.model.UserProfile
import com.example.ui.components.ClubChatBannerAd
import com.example.ui.components.QuickyStickerIcon
import com.example.ui.components.ReplyPreviewBanner
import com.example.ui.components.SwipeToReplyContainer
import com.example.ui.components.dismissKeyboardOnTap
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClubDetailScreen(
    club: Club,
    messages: List<ClubMessage>,
    isPremium: Boolean,
    currentUserId: String = "user_me",
    activeClubId: String? = null,
    myClubName: String? = null,
    onBack: () -> Unit,
    onSendMessage: (text: String, replyToText: String?, replyToSender: String?) -> Unit,
    onSendVoiceMessage: () -> Unit,
    onOpenStickerPicker: () -> Unit,
    onOpenPremiumStore: () -> Unit,
    onViewMemberProfile: (String) -> Unit,
    onRemoveMember: (clubId: String, memberUserId: String) -> Unit,
    onDeleteClub: (String) -> Unit = {},
    onJoinClub: (String) -> Unit = {},
    onLeaveAndJoinClub: (String) -> Unit = {},
    onLeaveClub: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
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
    val listState = rememberLazyListState()

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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
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
                            // header now (1 line). Long descriptions marquee
                            // slowly instead of pushing content off-screen.
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
                                // v2.1 §3.8 — owner-only club deletion. Cascades
                                // wipe members/messages/events server-side and
                                // every member gets notified by the DB trigger.
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

            // Messages Feed — same bubble styling as the personal chat.
            // (v2.1 §3.4: club name/description live ONLY in the pinned
            // header — nothing is duplicated inside the scroll area.)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .dismissKeyboardOnTap(),
                reverseLayout = false
            ) {
                items(messages, key = { it.id }) { msg ->
                    SwipeToReplyContainer(onSwipeToReply = { replyingToMessage = msg }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!msg.isMine) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (msg.senderAvatarRes != null) {
                                        Image(
                                            painter = painterResource(id = msg.senderAvatarRes),
                                            contentDescription = msg.senderName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(msg.senderName.take(1), fontWeight = FontWeight.Bold, color = QuickyPurple)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Bubble styled exactly like the personal chat
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (msg.isMine) 18.dp else 4.dp,
                                    bottomEnd = if (msg.isMine) 4.dp else 18.dp
                                ),
                                color = if (msg.isMine) SparkRose else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    if (!msg.isMine) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = msg.senderName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = QuickyPink
                                            )
                                            if (msg.senderBadge != null) {
                                                Text(
                                                    text = "• ${msg.senderBadge}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // Reply preview inside message
                                    if (msg.replyToText != null) {
                                        Surface(
                                            color = if (msg.isMine) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                Text(
                                                    text = msg.replyToSender ?: "Reply",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                    color = if (msg.isMine) Color.White.copy(alpha = 0.9f) else QuickyPurple
                                                )
                                                Text(
                                                    text = msg.replyToText,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
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
                                            fontSize = 36.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }

                                    if (msg.text.isNotBlank()) {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (msg.voiceDurationSeconds != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = if (msg.isMine) Color.White else QuickyPurple,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Voice message • 0:0${msg.voiceDurationSeconds}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = msg.timestamp,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (msg.isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
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

            if (!isMember) {
                // Non-member preview footer: can view club, and join if space available
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
                                    text = "Previewing Club • Space available (${club.memberCount}/${club.maxMembers} members)",
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
                                    text = "Club is full (15/15 members). Chat is view-only for non-members.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ActionPass,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
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

                // Chat Input Bar — same layout as the personal chat
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRecordingVoiceNote) {
                        // Active In-Chat Voice Note Recording Bar (same as personal chat)
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
                                    text = "0:0$voiceRecordSeconds",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Discard button
                            IconButton(
                                onClick = {
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
                                    onSendVoiceMessage()
                                    isRecordingVoiceNote = false
                                    voiceRecordSeconds = 0
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

                            // Mic stays OUTSIDE the composer, on its right (voice message option)
                            IconButton(
                                onClick = {
                                    isRecordingVoiceNote = true
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

    // Members Bottom Sheet (PRD Section 17)
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
                                modifier = Modifier.padding(12.dp),
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
                                    }

                                    Text(
                                        text = "${member.characterBadge} • Joined ${member.joinedAt}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isOwner && member.userId != currentUserId) {
                                    IconButton(
                                        onClick = { memberToRemove = member },
                                        modifier = Modifier.size(36.dp).testTag("remove_member_${member.userId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PersonRemove,
                                            contentDescription = "Remove member",
                                            tint = ActionPass
                                        )
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
