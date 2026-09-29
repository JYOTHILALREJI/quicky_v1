package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserProfile
import com.example.ui.components.ProfileCard
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.SparkRose

@Composable
fun DiscoverScreen(
    deck: List<UserProfile>,
    onLike: (UserProfile, isSuperLike: Boolean) -> Unit,
    onPass: (UserProfile) -> Unit,
    onRewind: () -> Unit,
    onBoost: () -> Unit,
    onOpenDetail: (UserProfile) -> Unit,
    onResetDeck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("discover_screen")
    ) {
        if (deck.isNotEmpty()) {
            val topProfile = deck.first()
            ProfileCard(
                profile = topProfile,
                onLike = { onLike(topProfile, false) },
                onPass = { onPass(topProfile) },
                onSuperLike = { onLike(topProfile, true) },
                onRewind = onRewind,
                onBoost = onBoost,
                onOpenDetail = { onOpenDetail(topProfile) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Polished Empty Deck State
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "✨", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You're All Caught Up!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You've explored all active profiles matching your current filters. Adjust your radius or reset your deck to see profiles again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onResetDeck,
                        colors = ButtonDefaults.buttonColors(containerColor = SparkRose),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("reset_deck_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Deck", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
