package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Female
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Male
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.model.DiscoveryFilter
import com.example.data.AppContent
import com.example.ui.theme.QuickyPink
import com.example.ui.components.dismissKeyboardOnTap

/**
 * Discovery Preferences bottom sheet.
 *
 * Every preference group lives in its own bordered box so the sections read
 * as clearly separated cards. The Shared Interests box is seeded with the
 * interests the user picked during onboarding — that single selection is
 * both the shared-interest filter AND the user's profile interests, so any
 * change here is persisted back to the database on Apply.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    currentFilter: DiscoveryFilter,
    userInterests: List<String>,
    interestCatalog: List<String>,
    onApply: (DiscoveryFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var minAge by remember { mutableFloatStateOf(currentFilter.minAge.toFloat()) }
    var maxAge by remember { mutableFloatStateOf(currentFilter.maxAge.toFloat()) }
    var distance by remember { mutableFloatStateOf(currentFilter.distanceKm.toFloat()) }
    var verifiedOnly by remember { mutableStateOf(currentFilter.verifiedOnly) }
    var intent by remember { mutableStateOf(currentFilter.relationshipIntent) }
    var occupationInput by remember { mutableStateOf(currentFilter.occupation) }
    // Gender filter ("Show Me") — "Women", "Men" or "Everyone".
    var showMe by remember { mutableStateOf(currentFilter.whoDoYouWantToSee) }
    // Language filter (change request #7) — empty means "any language".
    var selectedLanguages by remember { mutableStateOf(currentFilter.languages) }

    // Shared interests — seeded with the user's own (onboarding-chosen)
    // interests. Toggling any chip changes both the filter and the
    // profile's interests, which sync to the database on Apply.
    var selectedInterests by remember { mutableStateOf(userInterests) }

    // Catalog plus any custom interests the user added at onboarding.
    val allInterestOptions = remember(interestCatalog, userInterests) {
        (interestCatalog + userInterests).distinctBy { it.trim().lowercase() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("filter_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // v2.1 §3.5 — tap outside any field dismisses the keyboard.
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------------------------------------------------------
            // Header
            // ---------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discovery Preferences",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // ---------------------------------------------------------
            // SHOW ME — gender filter (Women / Men / Everyone)
            // ---------------------------------------------------------
            FilterSectionBox(
                icon = Icons.Outlined.Wc,
                title = "Show Me",
                valueText = showMe,
                testTag = "filter_show_me_box"
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple("Women", "Women", Icons.Outlined.Female),
                            Triple("Men", "Men", Icons.Outlined.Male),
                            Triple("Everyone", "Everyone", Icons.Outlined.Groups)
                        ).forEach { (option, label, optionIcon) ->
                            val selected = showMe == option
                            val segmentColor by animateColorAsState(
                                targetValue = if (selected) QuickyPink else Color.Transparent,
                                animationSpec = tween(200),
                                label = "showMeSegment"
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = segmentColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("filter_show_me_${option.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .clickable { showMe = option }
                                        .padding(horizontal = 6.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = optionIcon,
                                        contentDescription = null,
                                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "Pick who appears in your deck — Everyone keeps discovery open to all genders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ---------------------------------------------------------
            // AGE RANGE
            // ---------------------------------------------------------
            FilterSectionBox(
                icon = Icons.Outlined.Cake,
                title = "Age Range",
                valueText = "${minAge.toInt()} - ${maxAge.toInt()}",
                testTag = "filter_age_box"
            ) {
                RangeSlider(
                    value = minAge..maxAge,
                    onValueChange = { range ->
                        minAge = range.start
                        maxAge = range.endInclusive
                    },
                    valueRange = 18f..60f,
                    steps = 41,
                    colors = SliderDefaults.colors(
                        thumbColor = QuickyPink,
                        activeTrackColor = QuickyPink
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("age_range_slider")
                )
            }

            // ---------------------------------------------------------
            // MAXIMUM DISTANCE
            // ---------------------------------------------------------
            FilterSectionBox(
                icon = Icons.Outlined.Place,
                title = "Maximum Distance",
                valueText = "${distance.toInt()} km",
                testTag = "filter_distance_box"
            ) {
                Slider(
                    value = distance,
                    onValueChange = { distance = it },
                    valueRange = 5f..150f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = QuickyPink,
                        activeTrackColor = QuickyPink
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("distance_slider")
                )
            }

            // ---------------------------------------------------------
            // OCCUPATION
            // ---------------------------------------------------------
            FilterSectionBox(
                icon = Icons.Outlined.Work,
                title = "Occupation",
                valueText = occupationInput.trim().takeIf { it.isNotEmpty() } ?: "Any",
                testTag = "filter_occupation_box"
            ) {
                OutlinedTextField(
                    value = occupationInput,
                    onValueChange = { occupationInput = it.take(40) },
                    placeholder = { Text("e.g. Designer, Doctor, Engineer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Work,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QuickyPink,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("filter_occupation_field")
                )
                Text(
                    text = "Leave empty to see every occupation — matches any part of the job title.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ---------------------------------------------------------
            // LANGUAGES — match people who speak at least one of these
            // ---------------------------------------------------------
            FilterSectionBox(
                icon = Icons.Outlined.Language,
                title = "Languages",
                valueText = if (selectedLanguages.isEmpty()) "Any"
                else "${selectedLanguages.size} picked",
                testTag = "filter_languages_box"
            ) {
                Text(
                    text = "Leave everything unselected to see every language, or pick the ones you'd like in common.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppContent.languageCatalog.forEach { language ->
                        val isSelected = language in selectedLanguages
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedLanguages = if (isSelected) {
                                    selectedLanguages - language
                                } else {
                                    selectedLanguages + language
                                }
                            },
                            label = {
                                Text(
                                    text = language,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = QuickyPink.copy(alpha = 0.14f),
                                selectedLabelColor = QuickyPink,
                                selectedLeadingIconColor = QuickyPink
                            ),
                            modifier = Modifier.testTag(
                                "filter_language_chip_${language.replace(" ", "_").lowercase()}"
                            )
                        )
                    }
                }
            }

            // ---------------------------------------------------------
            // VERIFIED PROFILES ONLY
            // ---------------------------------------------------------
            FilterSectionBox(
                icon = Icons.Outlined.VerifiedUser,
                title = "Verified Profiles Only",
                valueText = if (verifiedOnly) "On" else "Off",
                testTag = "filter_verified_box"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Only show users with a blue photo verification badge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                    Switch(
                        checked = verifiedOnly,
                        onCheckedChange = { verifiedOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = QuickyPink,
                            checkedTrackColor = QuickyPink.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("verified_only_switch")
                    )
                }
            }

            // ---------------------------------------------------------
            // SHARED INTERESTS (seeded from the onboarding selection)
            // ---------------------------------------------------------
            FilterSectionBox(
                icon = Icons.Outlined.FavoriteBorder,
                title = "Shared Interests",
                valueText = "${selectedInterests.size} picked",
                testTag = "filter_interests_box"
            ) {
                Text(
                    text = "Your onboarding interests power shared-interest matching. " +
                            "Every change here updates your profile everywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allInterestOptions.forEach { interest ->
                        val isSelected = interest.lowercase() in selectedInterests.map { it.lowercase() }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedInterests = if (isSelected) {
                                    selectedInterests.filter { it.lowercase() != interest.lowercase() }
                                } else {
                                    selectedInterests + interest
                                }
                            },
                            label = {
                                Text(
                                    text = interest,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = QuickyPink.copy(alpha = 0.14f),
                                selectedLabelColor = QuickyPink,
                                selectedLeadingIconColor = QuickyPink
                            ),
                            modifier = Modifier.testTag("filter_interest_chip_${interest.replace(" ", "_").lowercase()}")
                        )
                    }
                }
            }

            // ---------------------------------------------------------
            // Apply
            // ---------------------------------------------------------
            Button(
                onClick = {
                    onApply(
                        DiscoveryFilter(
                            whoDoYouWantToSee = showMe,
                            minAge = minAge.toInt(),
                            maxAge = maxAge.toInt(),
                            distanceKm = distance.toInt(),
                            relationshipIntent = intent,
                            verifiedOnly = verifiedOnly,
                            occupation = occupationInput.trim(),
                            languages = selectedLanguages,
                            interests = selectedInterests
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("apply_filters_button"),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuickyPink)
            ) {
                Text("Apply Filters", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * A single preference "card": leading icon + title + optional trailing
 * value, all wrapped in a rounded, bordered surface so every option is
 * clearly separated from its neighbours.
 */
@Composable
private fun FilterSectionBox(
    icon: ImageVector,
    title: String,
    valueText: String?,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = QuickyPink,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                if (valueText != null) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = QuickyPink
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
