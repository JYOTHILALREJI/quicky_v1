package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.model.UserProfile
import com.example.model.VisibilityLevel
import com.example.model.toDisplayLocation
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileDetailSheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    var showReportDialog by remember { mutableStateOf(false) }
    // Which of the profile's photos the hero shows — switchable from the
    // thumbnail strip (remote Storage URLs win over bundled drawables).
    var heroIndex by remember(profile.id) { mutableIntStateOf(0) }

    val remotePhotos = profile.photoUris
    val bundledPhotos = profile.photoResIds
    val totalPhotos = maxOf(remotePhotos.size, bundledPhotos.size)
    val clampedHeroIndex = heroIndex.coerceIn(0, maxOf(0, totalPhotos - 1))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("cinematic_profile_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // PRD Section 16: Cinematic Hero Image with Overlay Info.
            // Remote Supabase Storage photo first (uploaded by the user),
            // then bundled drawable, then the hero placeholder — fixes the
            // "info icon shows a placeholder" bug for server candidates.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                val heroUri = remotePhotos.getOrNull(clampedHeroIndex)
                val heroRes = bundledPhotos.getOrNull(clampedHeroIndex)
                if (heroUri != null) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(heroUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${profile.name}'s photo",
                        contentScale = ContentScale.Crop,
                        error = painterResource(R.drawable.img_onboarding_hero),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = heroRes ?: R.drawable.img_onboarding_hero),
                        contentDescription = profile.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Close Button Top-Start
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }

                // Top-Right Badges (Character + Verification)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (profile.showCharacterBadge) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, QuickyPurple)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(text = "✦", color = QuickyGold, fontSize = 13.sp)
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
                            color = ActionVerified
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "Quicky Verified",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Dark gradient overlay on the lower portion of the image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                )

                // Overlay card inside the image (PRD Section 16)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "${profile.name}, ${profile.age}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.LocationOn, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        Text(text = "${profile.city.toDisplayLocation()} • ${profile.distanceKm} km away", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                    }

                    Text(
                        text = profile.characterDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // ---------------------------------------------------------
            // PHOTO STRIP — all of the profile's photos; tapping a
            // thumbnail switches the hero above.
            // ---------------------------------------------------------
            if (totalPhotos > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    items(totalPhotos) { index ->
                        val isSelected = index == clampedHeroIndex
                        val thumbUri = remotePhotos.getOrNull(index)
                        val thumbRes = bundledPhotos.getOrNull(index)
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 2.5.dp,
                                            color = QuickyPink,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                    } else Modifier
                                )
                                .clickable { heroIndex = index }
                                .testTag("detail_photo_thumb_$index")
                        ) {
                            if (thumbUri != null) {
                                coil.compose.AsyncImage(
                                    model = thumbUri,
                                    contentDescription = "${profile.name}'s photo ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(
                                        id = thumbRes ?: R.drawable.img_onboarding_hero
                                    ),
                                    contentDescription = "${profile.name}'s photo ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // Lower Detail Section (Respects field visibility matrix)
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

                // Explainable Compatibility Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.ElectricBolt, contentDescription = null, tint = QuickyPink)
                            Text(
                                text = "${profile.compatibilityScore}% Compatibility Breakdown",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        profile.compatibilityHighlights.forEach { highlight ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "•", color = QuickyPink, fontWeight = FontWeight.Bold)
                                Text(
                                    text = highlight,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // About Me
                Text(
                    text = "About Me",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ---------------------------------------------------------
                // BASICS — every useful profile column, each row rendered
                // only when it actually has data (change request #4).
                // ---------------------------------------------------------
                val zodiacSign = zodiacFor(profile.dateOfBirth)
                val basics = buildList {
                    add("Looking For" to profile.relationshipIntent)
                    add("Zodiac" to zodiacSign)
                    add("Industry" to profile.industry)
                    add("Interested In" to profile.interestedIn.takeIf { it.isNotBlank() })
                }

                Text(
                    text = "Basics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                basics.forEach { (label, value) ->
                    if (!value.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Profile Prompts
                profile.prompts.forEach { prompt ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = QuickyPink.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, QuickyPink.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = prompt.question,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = QuickyPink
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = prompt.answer,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interests (arranged correctly across multiple rows,
                // respecting the interests field visibility setting)
                if (profile.fieldVisibility["interests"] != VisibilityLevel.ONLY_ME && profile.interests.isNotEmpty()) {
                    Text(
                        text = "Interests",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.interests.forEach { interest ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(interest) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Personal Details (Respecting field-level privacy visibility)
                val isVisibleToViewer = true // Public/Matches view
                if (profile.fieldVisibility["height"] != VisibilityLevel.ONLY_ME && profile.height.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Height", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile.height, fontWeight = FontWeight.Medium)
                    }
                }

                if (profile.fieldVisibility["occupation"] != VisibilityLevel.ONLY_ME && profile.occupation.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Occupation", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile.occupation, fontWeight = FontWeight.Medium)
                    }
                }

                if (profile.fieldVisibility["education"] != VisibilityLevel.ONLY_ME &&
                    (profile.education.isNotBlank() || profile.educationLevel.isNotBlank())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Education", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            profile.educationLevel +
                                    (profile.education.takeIf { it.isNotBlank() && it != profile.educationLevel }
                                        ?.let { " ($it)" } ?: ""),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (profile.fieldVisibility["languages"] != VisibilityLevel.ONLY_ME && profile.languages.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Languages", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile.languages.joinToString(", "), fontWeight = FontWeight.Medium)
                    }
                }

                // ---------------------------------------------------------
                // HOBBIES — chip row (only when the profile has any)
                // ---------------------------------------------------------
                if (profile.hobbies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Hobbies",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.hobbies.forEach { hobby ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(hobby) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Safety Options (Report / Block)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = { showReportDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = ActionPass)
                    ) {
                        Icon(imageVector = Icons.Outlined.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Report ${profile.name}")
                    }

                    TextButton(
                        onClick = onBlock,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Block User")
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        var selectedReason by remember { mutableStateOf("Inappropriate messages") }
        val reasons = listOf("Inappropriate content", "Harassment or bullying", "Spam or scam", "Fake profile", "Other")

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report ${profile.name}") },
            text = {
                Column {
                    Text("Select a violation reason. Quicky's trust & safety team will review this profile:")
                    Spacer(modifier = Modifier.height(8.dp))
                    reasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Text(text = reason, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        onReport()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Maps the profile's (private, never displayed raw) date of birth to a
 * zodiac sign label — one of the "more available details" surfaced on
 * the detail sheet. Returns null when the date is missing/unparseable.
 */
private fun zodiacFor(isoDate: String?): String? {
    if (isoDate.isNullOrBlank()) return null
    val parts = isoDate.split("-")
    if (parts.size < 3) return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return when (month) {
        1 -> if (day < 20) "Capricorn ♑" else "Aquarius ♒"
        2 -> if (day < 19) "Aquarius ♒" else "Pisces ♓"
        3 -> if (day < 21) "Pisces ♓" else "Aries ♈"
        4 -> if (day < 20) "Aries ♈" else "Taurus ♉"
        5 -> if (day < 21) "Taurus ♉" else "Gemini ♊"
        6 -> if (day < 21) "Gemini ♊" else "Cancer ♋"
        7 -> if (day < 23) "Cancer ♋" else "Leo ♌"
        8 -> if (day < 23) "Leo ♌" else "Virgo ♍"
        9 -> if (day < 23) "Virgo ♍" else "Libra ♎"
        10 -> if (day < 23) "Libra ♎" else "Scorpio ♏"
        11 -> if (day < 22) "Scorpio ♏" else "Sagittarius ♐"
        12 -> if (day < 22) "Sagittarius ♐" else "Capricorn ♑"
        else -> null
    }
}
