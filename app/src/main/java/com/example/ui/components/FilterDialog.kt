package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppContent
import com.example.model.DiscoveryFilter
import com.example.ui.theme.QuickyPink

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    currentFilter: DiscoveryFilter,
    onApply: (DiscoveryFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var minAge by remember { mutableFloatStateOf(currentFilter.minAge.toFloat()) }
    var maxAge by remember { mutableFloatStateOf(currentFilter.maxAge.toFloat()) }
    var distance by remember { mutableFloatStateOf(currentFilter.distanceKm.toFloat()) }
    var verifiedOnly by remember { mutableStateOf(currentFilter.verifiedOnly) }
    var intent by remember { mutableStateOf(currentFilter.relationshipIntent) }

    // Interest filters — show profiles sharing at least one of the
    // selected interests (same catalog used on the profile).
    var selectedInterests by remember { mutableStateOf(currentFilter.interests) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("filter_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // Age Range Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Age Range", style = MaterialTheme.typography.titleMedium)
                Text("${minAge.toInt()} - ${maxAge.toInt()}", color = QuickyPink, fontWeight = FontWeight.Bold)
            }
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
                modifier = Modifier.testTag("age_range_slider")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Maximum Distance Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Maximum Distance", style = MaterialTheme.typography.titleMedium)
                Text("${distance.toInt()} km", color = QuickyPink, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = distance,
                onValueChange = { distance = it },
                valueRange = 5f..150f,
                steps = 28,
                colors = SliderDefaults.colors(
                    thumbColor = QuickyPink,
                    activeTrackColor = QuickyPink
                ),
                modifier = Modifier.testTag("distance_slider")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Verified Profiles Only
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Verified Profiles Only", style = MaterialTheme.typography.titleMedium)
                    Text("Only show users with a blue photo verification badge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = verifiedOnly,
                    onCheckedChange = { verifiedOnly = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = QuickyPink, checkedTrackColor = QuickyPink.copy(alpha = 0.5f))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---------------------------------------------------------
            // INTERESTS FILTER
            // ---------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shared Interests", style = MaterialTheme.typography.titleMedium)
                if (selectedInterests.isNotEmpty()) {
                    TextButton(
                        onClick = { selectedInterests = emptyList() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.testTag("clear_interests_button")
                    ) {
                        Text("Clear (${selectedInterests.size})", color = QuickyPink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                text = "Show profiles that love at least one of your selected interests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppContent.interestCatalog.forEach { interest ->
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
                        border = null,
                        modifier = Modifier.testTag("filter_interest_chip_${interest.replace(" ", "_").lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Apply Button
            Button(
                onClick = {
                    onApply(
                        DiscoveryFilter(
                            minAge = minAge.toInt(),
                            maxAge = maxAge.toInt(),
                            distanceKm = distance.toInt(),
                            relationshipIntent = intent,
                            verifiedOnly = verifiedOnly,
                            interests = selectedInterests
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("apply_filters_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuickyPink)
            ) {
                Text("Apply Filters", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
