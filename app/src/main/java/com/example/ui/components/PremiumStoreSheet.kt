package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Entitlements
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumStoreSheet(
    entitlements: Entitlements,
    onPurchaseSubscription: (isYearly: Boolean) -> Unit,
    onPurchaseConsumable: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf("yearly") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBg,
        modifier = Modifier.testTag("premium_store_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }

            // Glowing Crown Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(SparkGold, SparkRose))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Spark Gold",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Text(
                text = "Unlock the ultimate social discovery & dating experience with zero limits.",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Plans Selector (Monthly vs Yearly)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Yearly Option (Best Value)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedPlan == "yearly") Color(0xFF2E1C38) else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (selectedPlan == "yearly") SparkGold else Color.DarkGray
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedPlan = "yearly" }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SparkGold
                        ) {
                            Text(
                                text = "SAVE 45%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "12 Months", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "$8.33 / mo", color = SparkGold, fontWeight = FontWeight.SemiBold)
                        Text(text = "$99.99 / yr", style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
                    }
                }

                // Monthly Option
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedPlan == "monthly") Color(0xFF2E1C38) else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (selectedPlan == "monthly") SparkGold else Color.DarkGray
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedPlan = "monthly" }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(text = "1 Month", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "$14.99 / mo", color = SparkGold, fontWeight = FontWeight.SemiBold)
                        Text(text = "Billed monthly", style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Feature Checklist (PRD Section 43)
            val features = listOf(
                "See Who Liked You before swiping",
                "Unlimited Swipes & Instant Rewinds",
                "5 Free Super Likes per week",
                "1 Free Profile Boost per month (10x views)",
                "Advanced Dating Preferences Filters",
                "Incognito Mode for private browsing",
                "100% Ad-Free experience"
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                features.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = SparkGold, modifier = Modifier.size(18.dp))
                        Text(text = feature, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Google Play Billing Purchase CTA
            Button(
                onClick = {
                    onPurchaseSubscription(selectedPlan == "yearly")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("subscribe_gold_button"),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SparkGold,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = if (selectedPlan == "yearly") "Subscribe for $99.99/year" else "Subscribe for $14.99/month",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Consumable Ala-Carte Purchases (PRD Section 40)
            Text(
                text = "Ala-Carte Consumables",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onPurchaseConsumable("boost_5") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SparkPurple)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🚀 5 Boosts", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$4.99", style = MaterialTheme.typography.labelSmall, color = SparkPurple)
                    }
                }

                OutlinedButton(
                    onClick = { onPurchaseConsumable("super_like_10") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionSuperLike)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌟 10 Super Likes", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$6.99", style = MaterialTheme.typography.labelSmall, color = ActionSuperLike)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
