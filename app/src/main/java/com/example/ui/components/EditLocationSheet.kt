package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.model.GeoSuggestion
import com.example.ui.theme.QuickyPink
import com.example.ui.theme.QuickyPurple

/**
 * EDIT LOCATION SHEET (change request #5).
 *
 * Three ways to update the saved location that powers distance-based
 * discovery:
 *  1. "Use my current location" — runtime permission + GPS fix +
 *     reverse geocode (all handled by the ViewModel, same pipeline as
 *     onboarding).
 *  2. Search by city/area name — Nominatim geocoding, pick a result.
 *  3. Keep whatever is currently saved.
 *
 * Saving writes latitude / longitude / city to the signed-in user's
 * profiles row so the server-side distance filter follows immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLocationSheet(
    currentCity: String,
    currentLatitude: Double?,
    currentLongitude: Double?,
    searchResults: List<GeoSuggestion>,
    isSearching: Boolean,
    isLocating: Boolean,
    error: String?,
    onSearch: (String) -> Unit,
    onCaptureCurrentLocation: () -> Unit,
    onSave: (latitude: Double, longitude: Double, city: String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Selection made on one of the search results.
    var selected by remember { mutableStateOf<GeoSuggestion?>(null) }

    val context = LocalContext.current

    fun hasLocationPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.any { it }) onCaptureCurrentLocation() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("edit_location_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------------------------------------------------------
            // Header + current location
            // ---------------------------------------------------------
            Text(
                text = "Update your location",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = QuickyPurple
                    )
                    Column {
                        Text(
                            text = currentCity.ifBlank { "No location saved yet" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (currentLatitude != null && currentLongitude != null) {
                                "Used for distance-based matching"
                            } else {
                                "Add a location to unlock distance filters"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------------------------------------------------
            // Option 1 — GPS auto-detect
            // ---------------------------------------------------------
            OutlinedButton(
                onClick = {
                    if (hasLocationPermission()) onCaptureCurrentLocation()
                    else permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                enabled = !isLocating,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_location_gps_button")
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(if (isLocating) "Getting your location…" else "Use my current location")
            }

            // ---------------------------------------------------------
            // Option 2 — search by city / area name
            // ---------------------------------------------------------
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                placeholder = { Text("Search a city or area") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (query.trim().length >= 2 && !isSearching) onSearch(query.trim()) }
                ),
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_location_search_field")
            )

            Button(
                onClick = { onSearch(query.trim()) },
                enabled = query.trim().length >= 2 && !isSearching,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuickyPink),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_location_search_button")
            ) {
                Text(if (isSearching) "Searching…" else "Search")
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ---------------------------------------------------------
            // Search results
            // ---------------------------------------------------------
            if (searchResults.isNotEmpty()) {
                Text(
                    text = "Results",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                searchResults.forEach { suggestion ->
                    val isSelected = selected == suggestion
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) QuickyPink.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(
                            1.5.dp, QuickyPink
                        ) else androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outlineVariant
                        ),
                        onClick = { selected = suggestion },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_location_result")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                tint = if (isSelected) QuickyPink else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = suggestion.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------
            // Save
            // ---------------------------------------------------------
            Button(
                onClick = {
                    selected?.let { onSave(it.latitude, it.longitude, it.city) }
                },
                enabled = selected != null,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuickyPink),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("edit_location_save_button")
            ) {
                Text("Save Location", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
