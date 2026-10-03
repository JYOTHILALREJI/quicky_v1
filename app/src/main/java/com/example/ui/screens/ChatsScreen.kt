package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import com.example.model.MatchItem
import com.example.ui.components.glassNavBarOverlayHeight
import com.example.ui.theme.*
import com.example.ui.components.dismissKeyboardOnTap

@Composable
fun ChatsScreen(
    matches: List<MatchItem>,
    onOpenChat: (MatchItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMatches = remember(searchQuery, matches) {
        if (searchQuery.isBlank()) matches
        else matches.filter { it.user.name.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("chats_screen")
            // v2.1 §3.5 — tap anywhere dismisses the keyboard.
            .dismissKeyboardOnTap(),
        // Extra bottom padding so the last chat can scroll clear above
        // the floating liquid-glass navigation bar.
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 16.dp + glassNavBarOverlayHeight()
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search Conversations Input (PRD Section 53)
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search conversations...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = QuickyPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }

        if (filteredMatches.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "💬", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No active conversations",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Send a message or play Truth or Dare with your matches to start chatting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredMatches) { match ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = if (match.unreadCount > 0) 3.dp else 1.dp,
                    border = if (match.unreadCount > 0) androidx.compose.foundation.BorderStroke(1.dp, QuickyPink.copy(alpha = 0.4f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(match) }
                        .testTag("chat_conversation_${match.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val photo = match.user.photoResIds.firstOrNull() ?: R.drawable.img_profile_sarah
                        Box(modifier = Modifier.size(56.dp)) {
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
                                        .size(14.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(ActionLike)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = match.user.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (match.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                )

                                Text(
                                    text = match.matchedAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (match.unreadCount > 0) QuickyPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (match.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (match.hasActiveGame) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = QuickyPurple.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "🎮 Game Turn",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = QuickyPurple,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = match.lastMessage ?: "Tap to start conversation...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (match.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (match.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (match.unreadCount > 0) {
                            Badge(
                                containerColor = QuickyPink,
                                contentColor = Color.White
                            ) {
                                Text(match.unreadCount.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}
