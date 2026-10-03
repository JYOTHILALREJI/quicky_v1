package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.game.LudoEngine
import com.example.model.LudoGameResult
import com.example.model.LudoMatch
import com.example.ui.theme.QuickyPurple

/**
 * ============================================================================
 * LUDO RESULT SCREEN — Quicky v3 (PRD §26)
 *
 * Full-screen standings shown the moment the THIRD player brings all 4
 * coins home (game-end rule, PRD §18):
 *
 *   🏆 LUDO COMPLETE
 *   1st Player A  · 200 pts · 4 coins home
 *   2nd Player B  · 200 pts · 4 coins home
 *   3rd Player C  · 200 pts · 4 coins home
 *   4th Player D  · 100 pts · 2 coins home   ← progress-based rank
 *
 *   Your Score +100
 *
 *   [ PLAY AGAIN ]  [ BACK TO GAMES ]
 *
 * PLAY AGAIN never mutates the finished match — the ViewModel spins up a
 * brand-new one (solo: fresh arena; online: new match + code).
 * ============================================================================
 */
@Composable
fun LudoResultScreen(
    match: LudoMatch,
    onPlayAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val results = LudoEngine.rankedResults(match)
    val myResult = results.firstOrNull { it.playerId == match.localUserId }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("ludo_result_screen")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            Text("🏆", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "LUDO COMPLETE",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Third player brought all coins home — game over",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Standings ---
            results.forEach { result ->
                LudoResultRow(
                    result = result,
                    playerAvatarRes = match.players.firstOrNull { it.id == result.playerId }?.avatarRes
                        ?: com.example.R.drawable.img_onboarding_hero,
                    playerAvatarUrl = match.players.firstOrNull { it.id == result.playerId }?.avatarUrl,
                    isMe = result.playerId == match.localUserId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Your score ---
            if (myResult != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = QuickyPurple.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, QuickyPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 14.dp)
                    ) {
                        Text(
                            text = "Your Score",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "+${myResult.score}",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = QuickyPurple
                        )
                        Text(
                            text = "${myResult.finishedTokens} coins home · Rank #${myResult.finishPosition}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- Actions ---
            Button(
                onClick = onPlayAgain,
                colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("ludo_play_again_button")
            ) {
                Text("PLAY AGAIN", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("ludo_back_to_games_button")
            ) {
                Text("BACK TO GAMES", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
                Text("Exit", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LudoResultRow(
    result: LudoGameResult,
    playerAvatarRes: Int,
    playerAvatarUrl: String?,
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    val medal = when (result.finishPosition) {
        1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "4th"
    }
    val medalColor = when (result.finishPosition) {
        1 -> Color(0xFFFFD54F); 2 -> Color(0xFFC0C6CC); 3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isMe) QuickyPurple.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (isMe) 1.5.dp else 1.dp,
            if (isMe) QuickyPurple else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.testTag("ludo_result_row_${result.finishPosition}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Rank medal / place
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(34.dp)) {
                if (result.finishPosition == 4) {
                    Text(
                        "4th",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = medalColor
                    )
                } else {
                    Text(medal, fontSize = 24.sp)
                }
            }

            // Avatar — the real profile photo when one is available (PRD §46).
            if (!playerAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playerAvatarUrl,
                    contentDescription = result.playerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(2.dp, medalColor, CircleShape)
                )
            } else {
                Image(
                    painter = painterResource(id = playerAvatarRes),
                    contentDescription = result.playerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(2.dp, medalColor, CircleShape)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMe) "You" else result.playerName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${result.finishedTokens} coins home",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${result.score} pts",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = medalColor
            )
        }
    }
}
