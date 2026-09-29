package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DiscoveryFilter
import com.example.ui.theme.QuickyPink

@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("filter_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                            verifiedOnly = verifiedOnly
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
