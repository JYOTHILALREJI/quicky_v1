package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserProfile
import com.example.model.VisibilityLevel
import com.example.ui.theme.QuickyPink
import com.example.ui.theme.QuickyPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInformationSheet(
    profile: UserProfile,
    onSave: (height: String, occupation: String, education: String, intent: String, fieldVisibility: Map<String, VisibilityLevel>) -> Unit,
    onDismiss: () -> Unit
) {
    var height by remember { mutableStateOf(profile.height) }
    var occupation by remember { mutableStateOf(profile.occupation) }
    var education by remember { mutableStateOf(profile.educationLevel) }
    var relationshipIntent by remember { mutableStateOf(profile.relationshipIntent) }

    val fieldVisibility = remember {
        mutableStateMapOf<String, VisibilityLevel>().apply {
            putAll(profile.fieldVisibility)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("personal_information_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Personal Information",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Control attributes & field visibility",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Height
            PersonalFieldRow(
                icon = Icons.Outlined.Height,
                label = "Height",
                value = height,
                visibility = fieldVisibility["height"] ?: VisibilityLevel.MATCHES_ONLY,
                onValueChange = { height = it },
                onVisibilityChange = { fieldVisibility["height"] = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Occupation
            PersonalFieldRow(
                icon = Icons.Outlined.WorkOutline,
                label = "Occupation",
                value = occupation,
                visibility = fieldVisibility["occupation"] ?: VisibilityLevel.EVERYONE,
                onValueChange = { occupation = it },
                onVisibilityChange = { fieldVisibility["occupation"] = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Education
            PersonalFieldRow(
                icon = Icons.Outlined.School,
                label = "Education",
                value = education,
                visibility = fieldVisibility["education"] ?: VisibilityLevel.EVERYONE,
                onValueChange = { education = it },
                onVisibilityChange = { fieldVisibility["education"] = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Relationship Intent
            PersonalFieldRow(
                icon = Icons.Outlined.FavoriteBorder,
                label = "Relationship Intent",
                value = relationshipIntent,
                visibility = fieldVisibility["intent"] ?: VisibilityLevel.EVERYONE,
                onValueChange = { relationshipIntent = it },
                onVisibilityChange = { fieldVisibility["intent"] = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onSave(height, occupation, education, relationshipIntent, fieldVisibility.toMap())
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickyPink),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_personal_info_button")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PersonalFieldRow(
    icon: ImageVector,
    label: String,
    value: String,
    visibility: VisibilityLevel,
    onValueChange: (String) -> Unit,
    onVisibilityChange: (VisibilityLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = QuickyPurple, modifier = Modifier.size(20.dp))
                Text(text = label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            }

            // Visibility Level Dropdown
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { expanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (visibility) {
                                VisibilityLevel.EVERYONE -> "🌐 Everyone"
                                VisibilityLevel.MATCHES_ONLY -> "🔒 Matches Only"
                                VisibilityLevel.ONLY_ME -> "🚫 Hidden"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            fontSize = 11.sp
                        )
                        Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("🌐 Everyone") },
                        onClick = {
                            onVisibilityChange(VisibilityLevel.EVERYONE)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🔒 Matches Only") },
                        onClick = {
                            onVisibilityChange(VisibilityLevel.MATCHES_ONLY)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🚫 Hidden") },
                        onClick = {
                            onVisibilityChange(VisibilityLevel.ONLY_ME)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}
