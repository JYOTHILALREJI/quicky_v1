package com.example.ui.screens

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Club
import com.example.ui.theme.*
import com.example.ui.components.dismissKeyboardOnTap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubsScreen(
    clubs: List<Club>,
    activeClubId: String?,
    onOpenClub: (Club) -> Unit,
    onJoinClub: (String) -> Unit,
    onLeaveClub: (String) -> Unit,
    onCreateClubClick: () -> Unit,
    onLeaveAndJoinClub: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showLeaveConfirmDialog by remember { mutableStateOf<Club?>(null) }
    var showSwitchClubConfirmDialog by remember { mutableStateOf<Club?>(null) }

    val myClub = remember(clubs, activeClubId) {
        clubs.find { it.id == activeClubId }
    }

    val categories = listOf("All", "Gaming & Ludo", "Food & Lifestyle", "Music & Arts", "Entertainment")
    val filteredClubs = remember(selectedCategory, searchQuery, clubs) {
        clubs.filter { club ->
            val matchesCategory = selectedCategory == "All" || club.category.contains(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    club.name.contains(searchQuery, ignoreCase = true) ||
                    club.description.contains(searchQuery, ignoreCase = true) ||
                    club.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            if (activeClubId == null) {
                ExtendedFloatingActionButton(
                    onClick = onCreateClubClick,
                    containerColor = QuickyPurple,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Create Club", fontWeight = FontWeight.Bold) },
                    // Clear the system gesture bar so the button is always
                    // fully visible (the glass nav bar is hidden here).
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("create_club_fab")
                )
            }
        },
        // Zero insets: the outer app-level Scaffold already handles the
        // status bar — default insets here duplicated it as blank space.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("clubs_screen"),
                // v2.1 §3.5 — tap anywhere dismisses the keyboard.
                .dismissKeyboardOnTap(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header / Hero Section (PRD Section 1, 11, 19: Friendship & Social Communities)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = QuickyPurple.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, QuickyPurple.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🛡️", fontSize = 18.sp)
                        Column {
                            Text(
                                text = "Quicky Clubs & Communities",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Small 15-member groups for games, friendship & shared chats",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // =========================================================
            // MY CLUB SECTION (PRD Section 15 & 26)
            // =========================================================
            item {
                Text(
                    text = "My Club",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            item {
                if (myClub != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, QuickyPurple.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = QuickyPurple.copy(alpha = 0.15f),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(myClub.logoEmoji, fontSize = 26.sp)
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = myClub.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "${myClub.memberCount} / ${myClub.maxMembers} Members",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = QuickyPurple,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Surface(
                                    color = ActionLike.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ActionLike,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = myClub.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onOpenClub(myClub) },
                                    colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.weight(1f).testTag("open_my_club_button")
                                ) {
                                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Club Chat", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showLeaveConfirmDialog = myClub },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionPass),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ActionPass.copy(alpha = 0.5f))
                                ) {
                                    Text("Leave")
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("✨", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You haven't joined a Club yet",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Join an existing community or start your own to meet friends and play Ludo!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onCreateClubClick,
                                colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create a Club")
                            }
                        }
                    }
                }
            }

            // =========================================================
            // DISCOVER CLUBS SECTION (PRD Section 26 & 27)
            // =========================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discover Clubs",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Max 15 members",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Club Name Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search club by name or interest...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = QuickyPurple) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QuickyPurple,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clubs_search_input")
                )
            }

            // Category Filter Pills
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = QuickyPurple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (filteredClubs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔍", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No clubs match '$searchQuery'",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Try searching a different name or browse categories above",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Clubs List
            items(filteredClubs) { club ->
                val isMyClub = club.id == activeClubId
                val userAlreadyInOtherClub = activeClubId != null && !isMyClub
                val isFull = club.isFull

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onOpenClub(club) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = QuickyPurple.copy(alpha = 0.12f),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(club.logoEmoji, fontSize = 24.sp)
                                    }
                                }
                                Column {
                                    Text(
                                        text = club.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = club.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = QuickyPurple
                                    )
                                }
                            }

                            // Member Count Pill with capacity warning
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isFull) ActionPass.copy(alpha = 0.15f) else QuickyPurple.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isFull) "FULL (15/15)" else "${club.memberCount}/15",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isFull) ActionPass else QuickyPurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = club.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: user can always View Club, and Join if not joined and has space
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // View Club Button (always available)
                            OutlinedButton(
                                onClick = { onOpenClub(club) },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View Club")
                            }

                            when {
                                isMyClub -> {
                                    Button(
                                        onClick = { onOpenClub(club) },
                                        colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Open Chat", fontWeight = FontWeight.Bold)
                                    }
                                }
                                userAlreadyInOtherClub -> {
                                    if (!isFull) {
                                        Button(
                                            onClick = { showSwitchClubConfirmDialog = club },
                                            colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple.copy(alpha = 0.85f)),
                                            shape = RoundedCornerShape(18.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("switch_to_club_${club.id}")
                                        ) {
                                            Text("Leave & Join", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        Surface(
                                            color = ActionPass.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text(
                                                text = "Club Full",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = ActionPass,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                                isFull -> {
                                    Surface(
                                        color = ActionPass.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text(
                                            text = "Club Full (15 max)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ActionPass,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = { onJoinClub(club.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("join_club_${club.id}")
                                    ) {
                                        Text("Join Club", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Leave Club confirmation dialog
    showLeaveConfirmDialog?.let { clubToLeave ->
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = null },
            title = { Text("Leave ${clubToLeave.name}?") },
            text = { Text("You will no longer receive club chat notifications or be able to participate in member games.") },
            confirmButton = {
                Button(
                    onClick = {
                        onLeaveClub(clubToLeave.id)
                        showLeaveConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) {
                    Text("Leave Club")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Switch Club confirmation dialog (One club at a time enforcement)
    showSwitchClubConfirmDialog?.let { targetClub ->
        AlertDialog(
            onDismissRequest = { showSwitchClubConfirmDialog = null },
            title = { Text("Leave & Join New Club?") },
            text = {
                Text("A user can join in only ONE club at a time. In order to join \"${targetClub.name}\", you must first leave your current club (\"${myClub?.name ?: "Existing Club"}\"). Would you like to leave your current club and join ${targetClub.name}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLeaveAndJoinClub(targetClub.id)
                        showSwitchClubConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple)
                ) {
                    Text("Leave & Join ${targetClub.name}")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchClubConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
