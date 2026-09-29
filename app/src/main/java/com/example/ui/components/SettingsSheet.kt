package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ActionPass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDownloadData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("settings_sheet")
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
                Text(
                    text = "Settings & Legal",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Account & Data Section
            Text("DATA & PRIVACY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            ListItem(
                headlineContent = { Text("Download My Data") },
                supportingContent = { Text("Export a copy of your profile and interaction history") },
                leadingContent = { Icon(Icons.Outlined.Download, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDownloadData() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Legal & Community
            Text("LEGAL & POLICIES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Community Guidelines") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Privacy Policy") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Terms of Service (18+)") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Danger Zone: Delete Account
            Text("DANGER ZONE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ActionPass)
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionPass),
                border = androidx.compose.foundation.BorderStroke(1.dp, ActionPass),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_account_button")
            ) {
                Icon(imageVector = Icons.Outlined.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Account & All Data")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Account?") },
            text = { Text("This will permanently remove your profile, matches, chat history, and game progress from Quicky. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteAccount()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionPass)
                ) {
                    Text("Permanently Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
