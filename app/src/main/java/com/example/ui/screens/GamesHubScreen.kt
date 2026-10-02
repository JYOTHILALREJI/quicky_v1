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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.GameDefinition
import com.example.model.MatchItem
import com.example.model.TruthOrDarePrompt
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesHubScreen(
    games: List<GameDefinition>,
    prompts: List<TruthOrDarePrompt>,
    matches: List<MatchItem>,
    isPremium: Boolean,
    onStartGameWithMatch: (MatchItem, TruthOrDarePrompt) -> Unit,
    onOpenLudo: () -> Unit,
    onLockedGameClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var currentPrompt by remember { mutableStateOf(prompts.random()) }
    var selectedPromptForMatchPicker by remember { mutableStateOf<TruthOrDarePrompt?>(null) }

    val categories = listOf("All", "Flirty", "Funny", "Deep", "First Date")
    val filteredPrompts = remember(selectedCategory) {
        if (selectedCategory == "All") prompts else prompts.filter { it.category == selectedCategory }
    }

    // The glass nav bar is hidden on this screen; keep the list clear of
    // the system gesture bar only.
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("games_hub_screen"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 24.dp + navBarBottom
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner with Quicky logo
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_truth_dare_banner),
                        contentDescription = "Truth or Dare Games",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = QuickyPink
                        ) {
                            Text(
                                text = "INTERACTIVE DATING GAMES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Play Truth or Dare",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Skip small talk. Play free Truth or Dare inside any match, or unlock Premium games.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // PRD Section 45 & 46: FREE GAME (Truth or Dare)
        item {
            Text(
                text = "Free Dating Game",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, QuickyPink),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (currentPrompt.type == "TRUTH") QuickyPurple else QuickyPink
                            ) {
                                Text(
                                    text = currentPrompt.type,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ActionLike.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "FREE TO PLAY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ActionLike,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                currentPrompt = filteredPrompts.random()
                            }
                        ) {
                            Icon(imageVector = Icons.Filled.Shuffle, contentDescription = "Shuffle prompt", tint = QuickyGold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "“${currentPrompt.text}”",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = "Category: ${currentPrompt.category} • Difficulty: ${currentPrompt.difficulty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                selectedPromptForMatchPicker = currentPrompt
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("challenge_match_button"),
                            shape = RoundedCornerShape(23.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentPrompt.type == "TRUTH") QuickyPurple else QuickyPink
                            )
                        ) {
                            Icon(imageVector = Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Challenge a Match", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                currentPrompt = filteredPrompts.random()
                            },
                            shape = RoundedCornerShape(23.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }

        // PRD Section 45 & 46: PREMIUM GAMES (Locked with 🔒, opens Subscription Drawer on tap)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quicky Premium Games",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                if (!isPremium) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = QuickyGold.copy(alpha = 0.15f),
                        modifier = Modifier.clickable { onLockedGameClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Lock, contentDescription = null, tint = QuickyGold, modifier = Modifier.size(12.dp))
                            Text(
                                text = "UNLOCK ALL",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = QuickyGold
                            )
                        }
                    }
                }
            }
        }

        // FLAGSHIP PREMIUM GAME: 2-PLAYER LUDO ARENA (PRD Section 3 - 10)
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isPremium) QuickyPurple else QuickyGold.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isPremium) onOpenLudo()
                        else onLockedGameClick()
                    }
                    .testTag("ludo_game_card")
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ludo_banner),
                            contentDescription = "2-Player Ludo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPremium) QuickyPurple else QuickyGold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (isPremium) "🎮 READY TO PLAY" else "🔒 PREMIUM ONLY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isPremium) Color.White else Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Ludo · 2-Player Arena",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Real-time 2-player board game with opposite player layout, animated dice, step-by-step token movement, and shared room chat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                if (isPremium) onOpenLudo()
                                else onLockedGameClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPremium) QuickyPurple else QuickyGold
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().testTag("enter_ludo_button")
                        ) {
                            Text(
                                text = if (isPremium) "Enter 2-Player Ludo Arena" else "Unlock Ludo with Quicky Gold",
                                color = if (isPremium) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // List Premium Games
        items(games.filter { !it.isFree }) { game ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isPremium) {
                            onLockedGameClick() // PRD Section 47: Immediately opens Premium Subscription Drawer
                        } else {
                            val prompt = prompts.random()
                            selectedPromptForMatchPicker = prompt
                        }
                    }
                    .testTag("game_item_${game.id}")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isPremium) QuickyPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPremium) Icons.Filled.SportsEsports else Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = if (isPremium) QuickyPurple else QuickyGold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = game.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            if (!isPremium) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = QuickyGold.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "🔒 PREMIUM",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = QuickyGold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = game.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Match Chooser Sheet for launching game
    if (selectedPromptForMatchPicker != null) {
        val promptToSend = selectedPromptForMatchPicker!!
        ModalBottomSheet(
            onDismissRequest = { selectedPromptForMatchPicker = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Select a Match to Challenge 🎯",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "“${promptToSend.text}”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = QuickyPink,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (matches.isEmpty()) {
                    Text("No matches yet. Discover profiles first!")
                } else {
                    matches.forEach { match ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onStartGameWithMatch(match, promptToSend)
                                    selectedPromptForMatchPicker = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val photo = match.user.photoResIds.firstOrNull() ?: R.drawable.img_profile_sarah
                                Image(
                                    painter = painterResource(id = photo),
                                    contentDescription = match.user.name,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Text(
                                    text = match.user.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )

                                Icon(imageVector = Icons.Filled.Send, contentDescription = "Send", tint = QuickyPink)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
