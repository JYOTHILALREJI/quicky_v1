package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Particle data for the celebration confetti burst
private data class MatchParticle(
    val angleRad: Float,
    val speed: Float,
    val color: Color,
    val radius: Float,
    val isHeart: Boolean = false
)

@Composable
fun MatchCelebrationDialog(
    matchedProfile: UserProfile,
    onSendMessage: () -> Unit,
    onPlayGame: () -> Unit,
    onDismiss: () -> Unit
) {
    // Animation States
    val coroutineScope = rememberCoroutineScope()
    val backdropAlpha = remember { Animatable(0f) }
    val avatarProgress = remember { Animatable(0f) }
    val heartPopScale = remember { Animatable(0f) }
    val shockwaveProgress = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentSlideY = remember { Animatable(40f) }
    val buttonsSlideY = remember { Animatable(60f) }
    val buttonsAlpha = remember { Animatable(0f) }

    // Continuous heartbeat pulse once the heart arrives
    val infiniteTransition = rememberInfiniteTransition(label = "match_heartbeat")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_pulse"
    )

    // Halo glow pulsation around the avatars
    val haloPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    // Pre-calculate celebratory particles
    val particles = remember {
        val colors = listOf(SparkRose, SparkGold, SparkPurple, Color(0xFF38BDF8), Color(0xFFFF6584), Color.White)
        List(38) { i ->
            MatchParticle(
                angleRad = (i.toFloat() / 38f) * 2f * Math.PI.toFloat() + (Random.nextFloat() * 0.2f - 0.1f),
                speed = Random.nextFloat() * 220f + 140f,
                color = colors[i % colors.size],
                radius = Random.nextFloat() * 5f + 3f,
                isHeart = i % 4 == 0
            )
        }
    }

    // Trigger sequential choreography on entry
    LaunchedEffect(Unit) {
        // 1. Fade in backdrop immediately
        launch {
            backdropAlpha.animateTo(1f, animationSpec = tween(350, easing = LinearEasing))
        }

        // 2. Avatars slide in with dynamic spring overshoot
        launch {
            avatarProgress.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = 0.68f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        // 3. Shockwave and confetti burst when avatars meet
        delay(320)
        launch {
            shockwaveProgress.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }
        launch {
            confettiProgress.animateTo(1f, animationSpec = tween(950, easing = FastOutSlowInEasing))
        }

        // 4. Heart explodes into center with elastic bounce
        launch {
            heartPopScale.animateTo(
                1.38f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
            )
            heartPopScale.animateTo(
                1.0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
            )
        }

        // 5. Staggered reveal for headline and info
        delay(120)
        launch {
            contentAlpha.animateTo(1f, animationSpec = tween(400))
        }
        launch {
            contentSlideY.animateTo(0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        }

        // 6. Action buttons slide up from bottom
        delay(180)
        launch {
            buttonsAlpha.animateTo(1f, animationSpec = tween(350))
        }
        launch {
            buttonsSlideY.animateTo(0f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backdropAlpha.value }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0A1A).copy(alpha = 0.96f),
                            Color(0xFF320E28).copy(alpha = 0.98f),
                            Color(0xFF13091B)
                        )
                    )
                )
                .testTag("match_celebration_dialog")
        ) {
            // Dismiss icon top-right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .testTag("dismiss_match_celebration")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }

            // Confetti and Shockwave Canvas Layer
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height * 0.42f)

                // Expanding Shockwave Ripple 1
                if (shockwaveProgress.value > 0f) {
                    val radius1 = shockwaveProgress.value * 220f
                    val alpha1 = (1f - shockwaveProgress.value).coerceIn(0f, 1f) * 0.8f
                    drawCircle(
                        color = SparkRose.copy(alpha = alpha1),
                        radius = radius1,
                        center = centerOffset,
                        style = Stroke(width = 4f * (1f - shockwaveProgress.value * 0.5f))
                    )

                    // Expanding Shockwave Ripple 2 (staggered)
                    val radius2 = (shockwaveProgress.value * 280f - 30f).coerceAtLeast(0f)
                    val alpha2 = (1f - shockwaveProgress.value).coerceIn(0f, 1f) * 0.5f
                    if (radius2 > 0f) {
                        drawCircle(
                            color = SparkGold.copy(alpha = alpha2),
                            radius = radius2,
                            center = centerOffset,
                            style = Stroke(width = 2.5f)
                        )
                    }
                }

                // Burst Confetti Particles
                if (confettiProgress.value > 0f) {
                    val progress = confettiProgress.value
                    val particleAlpha = (1f - progress * 0.9f).coerceIn(0f, 1f)

                    particles.forEach { particle ->
                        val distance = particle.speed * progress
                        val px = centerOffset.x + cos(particle.angleRad) * distance
                        val py = centerOffset.y + sin(particle.angleRad) * distance + (progress * progress * 80f) // gravity arc

                        drawCircle(
                            color = particle.color.copy(alpha = particleAlpha),
                            radius = particle.radius * (1f - progress * 0.3f),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // Central Match Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Surface(
                    color = SparkRose.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SparkRose.copy(alpha = 0.8f)),
                    modifier = Modifier.graphicsLayer {
                        alpha = contentAlpha.value
                        translationY = -contentSlideY.value
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "✨",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "MUTUAL ATTRACTION",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            ),
                            color = SparkRose
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Animated Headline
                Text(
                    text = "It's a Match! 🎉",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White,
                    modifier = Modifier.graphicsLayer {
                        alpha = contentAlpha.value
                        val scale = 0.8f + (contentAlpha.value * 0.2f)
                        scaleX = scale
                        scaleY = scale
                    }
                )

                Text(
                    text = "You and ${matchedProfile.name} liked each other.\nBreak the ice before the spark fades!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .graphicsLayer { alpha = contentAlpha.value }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // DYNAMIC AVATAR COLLISION ARENA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val p = avatarProgress.value
                    // Left avatar slides from -240.dp to -38.dp
                    val leftOffsetXDp = (-240f + (p * (240f - 38f))).dp
                    // Right avatar slides from +240.dp to +38.dp
                    val rightOffsetXDp = (240f - (p * (240f - 38f))).dp
                    val leftRotation = -14f * (1f - p) - 3f
                    val rightRotation = 14f * (1f - p) + 3f

                    // Left avatar: Current User
                    val myPhoto = MockDataProvider.currentUser.photoResIds.firstOrNull() ?: R.drawable.img_onboarding_hero
                    Box(
                        modifier = Modifier
                            .offset(x = leftOffsetXDp)
                            .size(116.dp)
                            .graphicsLayer {
                                rotationZ = leftRotation
                                alpha = p.coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Halo Ring
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .border(
                                    3.dp,
                                    SparkRose.copy(alpha = haloPulseAlpha),
                                    CircleShape
                                )
                        )
                        Image(
                            painter = painterResource(id = myPhoto),
                            contentDescription = "My avatar",
                            modifier = Modifier
                                .size(108.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Right avatar: Matched Profile
                    val matchPhoto = matchedProfile.photoResIds.firstOrNull() ?: R.drawable.img_profile_sarah
                    Box(
                        modifier = Modifier
                            .offset(x = rightOffsetXDp)
                            .size(116.dp)
                            .graphicsLayer {
                                rotationZ = rightRotation
                                alpha = p.coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Halo Ring
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .border(
                                    3.dp,
                                    SparkGold.copy(alpha = haloPulseAlpha),
                                    CircleShape
                                )
                        )
                        Image(
                            painter = painterResource(id = matchPhoto),
                            contentDescription = "${matchedProfile.name}'s avatar",
                            modifier = Modifier
                                .size(108.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Floating Center Heart with Elastic Collision Explosion & Heartbeat Pulse
                    val combinedHeartScale = (heartPopScale.value * if (heartPopScale.value >= 0.95f) pulseScale else 1f)
                    if (combinedHeartScale > 0.05f) {
                        Surface(
                            shape = CircleShape,
                            color = SparkRose,
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = combinedHeartScale
                                    scaleY = combinedHeartScale
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Mutual Match Heart",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Shared interest compatibility pill
                if (matchedProfile.compatibilityHighlights.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                alpha = contentAlpha.value
                                translationY = contentSlideY.value
                            }
                    ) {
                        Text(
                            text = "💡 ${matchedProfile.compatibilityHighlights.first()}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.95f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Action Buttons with smooth upward slide-in
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = buttonsAlpha.value
                            translationY = buttonsSlideY.value
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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

                    Spacer(modifier = Modifier.height(10.dp))

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

                    Spacer(modifier = Modifier.height(8.dp))

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
}

