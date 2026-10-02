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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.model.UserProfile
import com.example.ui.components.glassNavBarOverlayHeight
import com.example.ui.theme.*

@Composable
fun MatchesScreen(
    matches: List<MatchItem>,
    isPremium: Boolean,
    onStartChat: (MatchItem) -> Unit,
    onPlayTruthOrDare: (MatchItem) -> Unit,
    onViewProfile: (UserProfile) -> Unit,
    onUnmatch: (String) -> Unit,
    onBlockUser: (String) -> Unit,
    onUnlockLikesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("matches_screen"),
        // Extra bottom padding so the last card can scroll clear above
        // the floating liquid-glass navigation bar.
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 16.dp + glassNavBarOverlayHeight()
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: New Matches Stories Tray
        item {
            Text(
                text = "New Matches",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Gold "Likes You" Card
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onUnlockLikesClick() }
                            .testTag("who_liked_you_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(listOf(QuickyGold, QuickyPink, QuickyGold))
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (isPremium) Icons.Filled.Favorite else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = QuickyGold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = if (isPremium) "8 Likes" else "8+",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = QuickyGold
                                )
                            }
                        }
                        Text(
                            text = "Likes You",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Match Avatars
                items(matches) { match ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onViewProfile(match.user) }
                            .testTag("match_story_${match.id}")
                    ) {
                        val avatarRes = match.user.photoResIds.firstOrNull() ?: R.drawable.img_profile_sarah
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(
                                    2.5.dp,
                                    if (match.isNewMatch) QuickyPink else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                                .padding(3.dp)
                        ) {
                            Image(
                                painter = painterResource(id = avatarRes),
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

                        Text(
                            text = match.user.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Section 2: Matches Relationship Cards (PRD Section 51 & 52)
        item {
            Text(
                text = "Your Connections (${matches.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (matches.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "💫", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No matches yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Discover and like people to spark mutual matches and play Truth or Dare together.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(matches) { match ->
                var showOptions by remember { mutableStateOf(false) }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("match_card_${match.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val photo = match.user.photoResIds.firstOrNull() ?: R.drawable.img_profile_sarah
                            Box(modifier = Modifier.size(68.dp)) {
                                Image(
                                    painter = painterResource(id = photo),
                                    contentDescription = match.user.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .clickable { onViewProfile(match.user) },
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${match.user.name}, ${match.user.age}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (match.user.isVerified) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = ActionVerified,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Box {
                                        IconButton(onClick = { showOptions = true }) {
                                            Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "Options")
                                        }
                                        DropdownMenu(
                                            expanded = showOptions,
                                            onDismissRequest = { showOptions = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("View Profile") },
                                                onClick = {
                                                    showOptions = false
                                                    onViewProfile(match.user)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Unmatch") },
                                                onClick = {
                                                    showOptions = false
                                                    onUnmatch(match.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Block User") },
                                                onClick = {
                                                    showOptions = false
                                                    onBlockUser(match.user.id)
                                                }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${match.user.city} • Matched ${match.matchedAt}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (match.user.showCharacterBadge) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = QuickyPurple.copy(alpha = 0.1f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "✦ ${match.user.characterBadge}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = QuickyPurple,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (match.user.compatibilityHighlights.isNotEmpty()) {
                            Text(
                                text = "💡 ${match.user.compatibilityHighlights.first()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: Chat Now & Play Truth or Dare (Free)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onStartChat(match) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QuickyPink),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chat Now", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onPlayTruthOrDare(match) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = QuickyPurple),
                                border = androidx.compose.foundation.BorderStroke(1.dp, QuickyPurple),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Truth or Dare", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
