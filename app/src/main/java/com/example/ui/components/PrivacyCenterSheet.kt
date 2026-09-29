package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PrivacySettings
import com.example.ui.theme.SparkPurple
import com.example.ui.theme.SparkRose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyCenterSheet(
    currentSettings: PrivacySettings,
    onSaveSettings: (PrivacySettings) -> Unit,
    onDismiss: () -> Unit
) {
    var incognito by remember { mutableStateOf(currentSettings.isIncognito) }
    var invisible by remember { mutableStateOf(currentSettings.isInvisible) }
    var onlineStatus by remember { mutableStateOf(currentSettings.showOnlineStatus) }
    var readReceipts by remember { mutableStateOf(currentSettings.showReadReceipts) }
    var showDistance by remember { mutableStateOf(currentSettings.showDistance) }
    var insightsEnabled by remember { mutableStateOf(currentSettings.interactionInsightsEnabled) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("privacy_center_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = SparkPurple)
                    Text(
                        text = "Privacy & Visibility",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Incognito Mode
            PrivacyToggleRow(
                title = "Incognito Mode",
                description = "Only profiles you actively like will be able to see you in discovery.",
                checked = incognito,
                onCheckedChange = { incognito = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Invisible Mode
            PrivacyToggleRow(
                title = "Snooze / Invisible Mode",
                description = "Temporarily hide your profile from new discovery while keeping existing chats intact.",
                checked = invisible,
                onCheckedChange = { invisible = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Online Activity Status
            PrivacyToggleRow(
                title = "Show Online Status",
                description = "Allow matches to see when you were recently active on Quicky.",
                checked = onlineStatus,
                onCheckedChange = { onlineStatus = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Read Receipts
            PrivacyToggleRow(
                title = "Message Read Receipts",
                description = "Show when messages have been seen with double checkmarks in chat.",
                checked = readReceipts,
                onCheckedChange = { readReceipts = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Interaction Insights Toggle (PRD Section 26)
            PrivacyToggleRow(
                title = "Interaction Style & Insights",
                description = "Enable non-sensitive behavioral dimensions (playfulness, curiosity, consistency) on your profile.",
                checked = insightsEnabled,
                onCheckedChange = { insightsEnabled = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onSaveSettings(
                        PrivacySettings(
                            isIncognito = incognito,
                            isInvisible = invisible,
                            showOnlineStatus = onlineStatus,
                            showReadReceipts = readReceipts,
                            showDistance = showDistance,
                            interactionInsightsEnabled = insightsEnabled
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_privacy_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SparkRose)
            ) {
                Text("Save Privacy Settings", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = SparkRose, checkedTrackColor = SparkRose.copy(alpha = 0.5f))
        )
    }
}
