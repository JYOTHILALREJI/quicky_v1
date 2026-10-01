package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
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
import com.example.ui.components.ReplyPreviewBanner
import com.example.ui.components.SwipeToReplyContainer
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
    onJoinClub: (String) -> Unit = {},
    onLeaveAndJoinClub: (String) -> Unit = {},
    onLeaveClub: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ClubMessage?>(null) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<ClubMember?>(null) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showSwitchClubConfirmDialog by remember { mutableStateOf(false) }
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
                                maxLines = 1
                            )
                            Text(
                                text = "${club.memberCount} / ${club.maxMembers} members",
                                style = MaterialTheme.typography.labelSmall,
                                color = QuickyPurple
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
                    IconButton(
                        onClick = { showMembersSheet = true },
                        modifier = Modifier.testTag("club_members_button")
                    ) {
                        Icon(imageVector = Icons.Outlined.People, contentDescription = "Members", tint = QuickyPurple)
                    }
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
            // Messages Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Club welcome info tile
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = QuickyPurple.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(club.logoEmoji, fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = club.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = club.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                items(messages) { msg ->
                    SwipeToReplyContainer(onSwipeToReply = { replyingToMessage = msg }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start
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

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (msg.isMine) QuickyPurple else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 270.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurface
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
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Text(
                                        text = msg.timestamp,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (msg.isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Banter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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

                // Chat Input Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRecordingVoiceNote) {
                        // In-chat Voice Recording Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = QuickyPurple.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(ActionPass)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Recording club voice note...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = QuickyPurple
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
                                modifier = Modifier.testTag("club_voice_record_cancel")
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
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = QuickyPurple, contentColor = Color.White),
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("club_voice_record_send")
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = "Send Voice Message", modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Sticker picker button
                            IconButton(
                                onClick = onOpenStickerPicker,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Text("🎮", fontSize = 20.sp)
                            }

                            // Voice Message Button (in-chat voice message)
                            IconButton(
                                onClick = {
                                    isRecordingVoiceNote = true
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("club_voice_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Voice note",
                                    tint = QuickyPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Chat with the club...", style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("club_message_input"),
                                shape = RoundedCornerShape(23.dp),
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
                                    if (inputText.isNotBlank()) {
                                        onSendMessage(
                                            inputText,
                                            replyingToMessage?.text?.take(60),
                                            if (replyingToMessage != null) (if (replyingToMessage!!.isMine) "You" else replyingToMessage!!.senderName) else null
                                        )
                                        inputText = ""
                                        replyingToMessage = null
                                    }
                                },
                                enabled = inputText.isNotBlank(),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (inputText.isNotBlank()) QuickyPurple else MaterialTheme.colorScheme.outlineVariant
                                )
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
}
