package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.UserProfile
import com.example.model.VisibilityLevel
import com.example.model.toDisplayLocation
import com.example.ui.theme.*
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileCard(
    profile: UserProfile,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onSuperLike: () -> Unit,
    onRewind: () -> Unit,
    onBoost: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    /** v2.1 §3.9 — distance unit from the Settings screen ("km" | "mi"). */
    distanceUnit: String = "km"
) {
    var currentPhotoIndex by remember(profile.id) { mutableIntStateOf(0) }
    // Photos may be remote URLs (server-served candidates) or bundled
    // drawables — remote wins, drawable is the fallback.
    val totalPhotos = maxOf(profile.photoUris.size, profile.photoResIds.size)
    val photoCount = totalPhotos.coerceAtLeast(1)

    // Distance label honoring the Settings > Distance unit toggle.
    val distanceLabel = if (distanceUnit == "mi") {
        "${(profile.distanceKm * 0.621371).toInt()} mi"
    } else {
        "${profile.distanceKm} km"
    }
    // Cassy micro-interaction (PRD §5.5): light haptic impact the moment
    // a decision button fires — same cue users feel crossing the swipe
    // threshold in gesture-driven decks.
    val haptics = LocalHapticFeedback.current

    // ---- Swipe-to-decide gesture state (change request #2) ----
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val swipeThresholdPx = with(density) { 120.dp.toPx() }
    val superLikeThresholdPx = with(density) { 140.dp.toPx() }
    val exitDistancePx = with(density) { 1000.dp.toPx() }
    val offsetX = remember(profile.id) { Animatable(0f) }
    val offsetY = remember(profile.id) { Animatable(0f) }
    // Named cardRotation (not rotationZ) to avoid clashing with the
    // GraphicsLayerScope.rotationZ property inside the lambda below.
    val cardRotation = remember(profile.id) { derivedStateOf { offsetX.value / 40f } }
    // Fire-once latch so a single drag can never trigger two actions.
    var swipeConsumed by remember(profile.id) { mutableStateOf(false) }

    // Card with drag-driven translation / tilt. Buttons inside the
    // bottom dock keep their own click handling — a drag that starts on
    // them is consumed by the button and never reaches this detector.
    Card(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = cardRotation.value
            }
            .testTag("profile_card_${profile.id}"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Current photo: prefer remote URL, fall back to drawable, then hero
            val currentPhotoUri = profile.photoUris.getOrNull(
                currentPhotoIndex % maxOf(1, profile.photoUris.size)
            )
            val currentPhotoRes = profile.photoResIds.getOrNull(
                currentPhotoIndex % maxOf(1, profile.photoResIds.size)
            )
            if (currentPhotoUri != null) {
                coil.compose.AsyncImage(
                    model = currentPhotoUri,
                    contentDescription = "${profile.name}'s photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(
                        id = currentPhotoRes ?: R.drawable.img_onboarding_hero
                    ),
                    contentDescription = "${profile.name}'s photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Tap to cycle photos (left = previous, right = next) + drag
            // to swipe the card away. Replaces the old clickable zones —
            // clickable would have eaten the drag gestures.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(profile.id) {
                        detectTapGestures { offset: Offset ->
                            if (offset.x < size.width / 2f) {
                                if (currentPhotoIndex > 0) currentPhotoIndex--
                            } else {
                                if (currentPhotoIndex < photoCount - 1) currentPhotoIndex++
                            }
                        }
                    }
                    .pointerInput(profile.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    offsetY.snapTo(offsetY.value + dragAmount.y)
                                }
                                // One haptic tick the moment the finger
                                // crosses the decision threshold.
                                if (!swipeConsumed && (
                                            abs(offsetX.value) > swipeThresholdPx ||
                                                    offsetY.value < -superLikeThresholdPx
                                            )
                                ) {
                                    swipeConsumed = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDragEnd = {
                                // v3.2.1 SWIPE FIX: commit by FINAL POSITION ONLY.
                                // The old `!swipeConsumed &&` guards were
                                // inverted — the haptic latch sets
                                // swipeConsumed=true the moment the finger
                                // CROSSES the threshold during the drag, so a
                                // normal deliberate swipe past the threshold
                                // skipped every commit branch and just
                                // sprang back (only a fast fling could slip
                                // past the async snapTo race). swipeConsumed
                                // now gates ONLY the haptic, never the action.
                                val x = offsetX.value
                                val y = offsetY.value
                                when {
                                    y < -superLikeThresholdPx -> scope.launch {
                                        offsetY.animateTo(-exitDistancePx, tween(220))
                                        onSuperLike()
                                        resetSwipe(offsetX, offsetY)
                                    }
                                    x > swipeThresholdPx -> scope.launch {
                                        offsetX.animateTo(exitDistancePx, tween(220))
                                        onLike()
                                        resetSwipe(offsetX, offsetY)
                                    }
                                    x < -swipeThresholdPx -> scope.launch {
                                        offsetX.animateTo(-exitDistancePx, tween(220))
                                        onPass()
                                        resetSwipe(offsetX, offsetY)
                                    }
                                    else -> scope.launch {
                                        val settle = spring<Float>(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                        offsetX.animateTo(0f, settle)
                                        offsetY.animateTo(0f, settle)
                                    }
                                }
                                swipeConsumed = false
                            },
                            onDragCancel = {
                                scope.launch {
                                    offsetX.animateTo(0f, spring())
                                    offsetY.animateTo(0f, spring())
                                }
                                swipeConsumed = false
                            }
                        )
                    }
            )

            // Top Photo Segment Indicators
            if (photoCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(photoCount) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (index == currentPhotoIndex) CassyPrimaryGradientEnd else Color.White.copy(alpha = 0.35f)
                                )
                        )
                    }
                }
            }

            // Smart-Deck compatibility badge (PRD §8.1) — a subtle champagne
            // pill at the TOP-LEFT corner showing the shared-interest score.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    CassyAccent.copy(alpha = 0.75f)
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 22.dp, start = 16.dp)
                    .testTag("compatibility_badge_${profile.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = CassyPrimaryGradientEnd,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "${profile.compatibilityScore}% MATCH",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            // PRD Section 6 & 83: Character Badge & Verified Badge at the TOP-RIGHT corner of the image
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (profile.showCharacterBadge) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, QuickyPurple.copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(text = "✦", color = QuickyGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = profile.characterBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                if (profile.isVerified) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ActionVerified,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Verified profile",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Verified",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Cinematic Cassy gradient scrim — a warm rosewood-tinted ink
            // wash keeps the profile content readable while feeling curated.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF1A0E12).copy(alpha = 0.62f),
                                Color(0xFF120A0E).copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            // Swipe feedback labels — LIKE (right), NOPE (left) and
            // SUPER LIKE (up) fade in as the finger crosses the thresholds.
            SwipeDecisionLabel(
                text = "LIKE",
                color = ActionLike,
                alpha = (offsetX.value / swipeThresholdPx).coerceIn(0f, 1f),
                rotationDegrees = -8f,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 64.dp)
            )
            SwipeDecisionLabel(
                text = "NOPE",
                color = ActionPass,
                alpha = (-offsetX.value / swipeThresholdPx).coerceIn(0f, 1f),
                rotationDegrees = 8f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp, top = 64.dp)
            )
            SwipeDecisionLabel(
                text = "SUPER LIKE",
                color = ActionSuperLike,
                alpha = (-offsetY.value / superLikeThresholdPx).coerceIn(0f, 1f),
                rotationDegrees = 0f,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
            )

            // Bottom Profile Information Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
            ) {
                // Name, Age and Detail Sheet Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${profile.name}, ${profile.age}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onOpenDetail,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .testTag("info_button_${profile.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "View full profile",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Location & Relationship Intent
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${profile.city.toDisplayLocation()} • $distanceLabel away",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }

                // Short Bio
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    modifier = Modifier.padding(top = 6.dp)
                )

                // Interest Badges arranged across multiple rows
                // (respecting the candidate's interests visibility setting)
                if (profile.fieldVisibility["interests"] != VisibilityLevel.ONLY_ME &&
                    profile.fieldVisibility["interests"] != VisibilityLevel.MATCHES_ONLY &&
                    profile.interests.isNotEmpty()
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        profile.interests.forEach { interest ->
                            Surface(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = interest,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Floating Action Dock Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRewind()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("action_rewind"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            contentColor = ActionRewind
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Replay, contentDescription = "Rewind")
                    }

                    // Pass
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPass()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("action_pass"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            contentColor = ActionPass
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Pass",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Super Like
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSuperLike()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("action_super_like"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            contentColor = ActionSuperLike
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Star, contentDescription = "Super Like")
                    }

                    // Like (Cassy rosewood gradient pill)
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLike()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("action_like"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = QuickyPink,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Like",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Boost (10x exposure)
                    FilledIconButton(
                        onClick = onBoost,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("action_boost"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            contentColor = QuickyPurple
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.RocketLaunch, contentDescription = "Profile Boost")
                    }
                }
            }
        }
    }
}

/**
 * A rubber-stamp style decision label that fades in proportionally to how
 * far the card has been dragged past its threshold (LIKE / NOPE / SUPER
 * LIKE). Alpha-driven, so it is invisible while the card rests.
 */
@Composable
private fun SwipeDecisionLabel(
    text: String,
    color: Color,
    alpha: Float,
    rotationDegrees: Float,
    modifier: Modifier = Modifier
) {
    if (alpha <= 0.01f) return
    Text(
        text = text,
        color = color.copy(alpha = alpha),
        style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        ),
        modifier = modifier
            .graphicsLayer { rotationZ = rotationDegrees }
            .border(
                width = 3.dp,
                color = color.copy(alpha = alpha),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/**
 * Snap the swipe offsets back to rest after an action fired. The deck
 * usually swaps the profile instantly, but this also covers the edge case
 * of the same card being kept (e.g. a rate-limited swipe).
 */
private suspend fun resetSwipe(
    offsetX: Animatable<Float, *>,
    offsetY: Animatable<Float, *>
) {
    offsetX.snapTo(0f)
    offsetY.snapTo(0f)
}
