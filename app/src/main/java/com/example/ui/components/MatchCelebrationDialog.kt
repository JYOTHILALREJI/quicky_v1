package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.MockDataProvider
import com.example.model.UserProfile
import com.example.ui.theme.*

@Composable
fun MatchCelebrationDialog(
    matchedProfile: UserProfile,
    onSendMessage: () -> Unit,
    onPlayGame: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBg.copy(alpha = 0.95f),
                            Color(0xFF2E1026).copy(alpha = 0.98f),
                            DarkBg
                        )
                    )
                )
                .padding(24.dp)
                .testTag("match_celebration_dialog")
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .testTag("dismiss_match_celebration")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Surface(
                    color = SparkRose.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SparkRose)
                ) {
                    Text(
                        text = "MUTUAL ATTRACTION",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                        color = SparkRose,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "It's a Match! 🎉",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White
                )

                Text(
                    text = "You and ${matchedProfile.name} liked each other.\nBreak the ice before the spark fades!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Overlapping Profile Avatars with glowing heart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Left avatar: Current User
                    val myPhoto = MockDataProvider.currentUser.photoResIds.firstOrNull() ?: R.drawable.img_onboarding_hero
                    Image(
                        painter = painterResource(id = myPhoto),
                        contentDescription = "My avatar",
                        modifier = Modifier
                            .offset(x = (-42).dp)
                            .size(110.dp)
                            .clip(CircleShape)
                            .border(3.dp, SparkRose, CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    // Right avatar: Matched Profile
                    val matchPhoto = matchedProfile.photoResIds.firstOrNull() ?: R.drawable.img_profile_sarah
                    Image(
                        painter = painterResource(id = matchPhoto),
                        contentDescription = "${matchedProfile.name}'s avatar",
                        modifier = Modifier
                            .offset(x = 42.dp)
                            .size(110.dp)
                            .clip(CircleShape)
                            .border(3.dp, SparkGold, CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    // Center Floating Heart Badge
                    Surface(
                        shape = CircleShape,
                        color = SparkRose,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Shared interest highlight
                if (matchedProfile.compatibilityHighlights.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "💡 ${matchedProfile.compatibilityHighlights.first()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Primary CTA: Send Message
                Button(
                    onClick = onSendMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("celebration_send_message"),
                    colors = ButtonDefaults.buttonColors(containerColor = SparkRose),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Send Message",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary CTA: Play Truth or Dare Game
                OutlinedButton(
                    onClick = onPlayGame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("celebration_play_game"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SparkPurple),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, SparkPurple),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(imageVector = Icons.Filled.SportsEsports, contentDescription = null, tint = SparkPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play Truth or Dare 🎲",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("celebration_keep_swiping")
                ) {
                    Text(
                        text = "Keep Discovering",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkTextMuted
                    )
                }
            }
        }
    }
}
