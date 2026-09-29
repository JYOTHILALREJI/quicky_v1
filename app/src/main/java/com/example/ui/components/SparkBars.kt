package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.ui.SparkTab
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkTopBar(
    currentTab: SparkTab,
    unreadNotificationsCount: Int,
    isBoostActive: Boolean,
    onNotificationsClick: () -> Unit,
    onFilterClick: () -> Unit,
    onBoostClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier
            .statusBarsPadding()
            .testTag("quicky_top_bar"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Official Quicky Logo Asset
                Image(
                    painter = painterResource(id = R.drawable.quicky_logo),
                    contentDescription = "Quicky Logo",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = when (currentTab) {
                        SparkTab.DISCOVER -> "Quicky"
                        SparkTab.MATCHES -> "Matches"
                        SparkTab.CHATS -> "Chats"
                        SparkTab.GAMES -> "Games Hub"
                        SparkTab.CLUBS -> "Clubs"
                        SparkTab.PROFILE -> "My Profile"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (isBoostActive && currentTab == SparkTab.DISCOVER) {
                    Surface(
                        color = QuickyPurple.copy(alpha = 0.15f),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, QuickyPurple)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RocketLaunch,
                                contentDescription = null,
                                tint = QuickyPurple,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "10× BOOST",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = QuickyPurple
                            )
                        }
                    }
                }
            }
        },
        actions = {
            if (currentTab == SparkTab.DISCOVER) {
                IconButton(
                    onClick = onBoostClick,
                    modifier = Modifier
                        .testTag("boost_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.RocketLaunch,
                        contentDescription = "Profile Boost",
                        tint = if (isBoostActive) QuickyPurple else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onFilterClick,
                    modifier = Modifier
                        .testTag("filter_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Discovery Filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            BadgedBox(
                badge = {
                    if (unreadNotificationsCount > 0) {
                        Badge(
                            containerColor = QuickyPink,
                            contentColor = Color.White
                        ) {
                            Text(unreadNotificationsCount.toString())
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier
                        .testTag("notifications_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun SparkBottomNav(
    currentTab: SparkTab,
    unreadMessagesCount: Int,
    newMatchesCount: Int,
    onTabSelected: (SparkTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("bottom_nav_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        NavigationBarItem(
            selected = currentTab == SparkTab.DISCOVER,
            onClick = { onTabSelected(SparkTab.DISCOVER) },
            icon = {
                Icon(
                    imageVector = if (currentTab == SparkTab.DISCOVER) Icons.Filled.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
                    contentDescription = "Discover"
                )
            },
            label = { Text("Discover", fontWeight = if (currentTab == SparkTab.DISCOVER) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = QuickyPink,
                selectedTextColor = QuickyPink,
                indicatorColor = QuickyPink.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_discover")
        )

        NavigationBarItem(
            selected = currentTab == SparkTab.MATCHES,
            onClick = { onTabSelected(SparkTab.MATCHES) },
            icon = {
                BadgedBox(
                    badge = {
                        if (newMatchesCount > 0) {
                            Badge(containerColor = QuickyPink) { Text(newMatchesCount.toString()) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentTab == SparkTab.MATCHES) Icons.Filled.PeopleAlt else Icons.Outlined.PeopleAlt,
                        contentDescription = "Matches"
                    )
                }
            },
            label = { Text("Matches", fontWeight = if (currentTab == SparkTab.MATCHES) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = QuickyPink,
                selectedTextColor = QuickyPink,
                indicatorColor = QuickyPink.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_matches")
        )

        NavigationBarItem(
            selected = currentTab == SparkTab.CHATS,
            onClick = { onTabSelected(SparkTab.CHATS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadMessagesCount > 0) {
                            Badge(containerColor = QuickyPink) { Text(unreadMessagesCount.toString()) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentTab == SparkTab.CHATS) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Chats"
                    )
                }
            },
            label = { Text("Chats", fontWeight = if (currentTab == SparkTab.CHATS) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = QuickyPink,
                selectedTextColor = QuickyPink,
                indicatorColor = QuickyPink.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_chats")
        )

        NavigationBarItem(
            selected = currentTab == SparkTab.GAMES,
            onClick = { onTabSelected(SparkTab.GAMES) },
            icon = {
                Icon(
                    imageVector = if (currentTab == SparkTab.GAMES) Icons.Filled.SportsEsports else Icons.Outlined.SportsEsports,
                    contentDescription = "Games"
                )
            },
            label = { Text("Games", fontWeight = if (currentTab == SparkTab.GAMES) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = QuickyPurple,
                selectedTextColor = QuickyPurple,
                indicatorColor = QuickyPurple.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_games")
        )

        NavigationBarItem(
            selected = currentTab == SparkTab.CLUBS,
            onClick = { onTabSelected(SparkTab.CLUBS) },
            icon = {
                Icon(
                    imageVector = if (currentTab == SparkTab.CLUBS) Icons.Filled.Diversity3 else Icons.Outlined.Diversity3,
                    contentDescription = "Clubs"
                )
            },
            label = { Text("Clubs", fontWeight = if (currentTab == SparkTab.CLUBS) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = QuickyPurple,
                selectedTextColor = QuickyPurple,
                indicatorColor = QuickyPurple.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_clubs")
        )

        NavigationBarItem(
            selected = currentTab == SparkTab.PROFILE,
            onClick = { onTabSelected(SparkTab.PROFILE) },
            icon = {
                Icon(
                    imageVector = if (currentTab == SparkTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile"
                )
            },
            label = { Text("Profile", fontWeight = if (currentTab == SparkTab.PROFILE) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = QuickyPink,
                selectedTextColor = QuickyPink,
                indicatorColor = QuickyPink.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_profile")
        )
    }
}
