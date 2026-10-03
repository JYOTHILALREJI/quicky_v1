package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.QuickyGold
import com.example.ui.theme.QuickyPurple
import com.example.ui.components.dismissKeyboardOnTap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClubSheet(
    lastCreatedTimestamp: Long,
    onCreateClub: (name: String, description: String, category: String, logoEmoji: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Gaming & Ludo") }
    var selectedEmoji by remember { mutableStateOf("🎮") }

    val emojis = listOf("🎮", "🕹️", "☕", "🎧", "🍕", "⚽", "⚔️", "🎨", "🚀", "🌸")
    val categories = listOf("Gaming & Ludo", "Food & Lifestyle", "Music & Arts", "Entertainment", "Sports & Fitness")

    // Cooldown check: 7 days cooldown (PRD Section 14)
    val now = System.currentTimeMillis()
    val cooldownMillis = 7L * 24 * 60 * 60 * 1000
    val timeSinceLast = now - lastCreatedTimestamp
    val isCooldownActive = lastCreatedTimestamp > 0 && timeSinceLast < cooldownMillis
    val daysRemaining = if (isCooldownActive) {
        ((cooldownMillis - timeSinceLast) / (24 * 60 * 60 * 1000) + 1).coerceAtLeast(1)
    } else 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("create_club_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // v2.1 §3.5 — tap outside any field dismisses the keyboard.
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create a Club",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cooldown / Rules Notice (PRD Section 13, 14, 15)
            Surface(
                color = QuickyGold.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, QuickyGold.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = QuickyGold, modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            text = "Club Rules & Restrictions",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = QuickyGold
                        )
                        Text(
                            text = "• Maximum 15 members capacity\n• Users can belong to only 1 Club at a time\n• 1 Club creation per 7-day period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (isCooldownActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⏳ Cooldown Active: You can create another Club in $daysRemaining days.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emoji Logo Picker
            Text("Select Club Logo", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(emojis) { emoji ->
                    Surface(
                        shape = CircleShape,
                        color = if (selectedEmoji == emoji) QuickyPurple else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selectedEmoji == emoji) androidx.compose.foundation.BorderStroke(2.dp, QuickyGold) else null,
                        modifier = Modifier
                            .size(50.dp)
                            .clickable { selectedEmoji = emoji }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Club Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Club Name") },
                placeholder = { Text("e.g. Weekend Gamers") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(200) },
                label = { Text("Description") },
                placeholder = { Text("What is this club about?") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Chips
            Text("Category", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = QuickyPurple,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotBlank() && description.isNotBlank()) {
                        onCreateClub(name, description, selectedCategory, selectedEmoji)
                    }
                },
                enabled = !isCooldownActive && name.isNotBlank() && description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_create_club_button")
            ) {
                Text("Create Club (Max 15 Members)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
