package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    onGamesClick: () -> Unit = {},
    onClubsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Games Hub & Clubs are now full-screen destinations reached from the
    // Profile page — they show an iOS-style back chevron before the logo.
    val showsBackChevron = currentTab == SparkTab.GAMES || currentTab == SparkTab.CLUBS

    TopAppBar(
        modifier = modifier
            .statusBarsPadding()
            .testTag("quicky_top_bar"),
        navigationIcon = {
            if (showsBackChevron) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .testTag("top_bar_back")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = QuickyBackChevron,
                        contentDescription = "Back",
                        tint = QuickyPurple,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
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
                        border = BorderStroke(1.dp, QuickyPurple)
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

            // Games Hub & Clubs live on the Profile page now (next to the
            // notification bell). They stay reachable from the hub screens
            // themselves so users can hop between them or return to Profile.
            if (currentTab == SparkTab.PROFILE || currentTab == SparkTab.GAMES || currentTab == SparkTab.CLUBS) {
                IconButton(
                    onClick = onGamesClick,
                    modifier = Modifier
                        .testTag("top_bar_games")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = QuickyGamesIcon,
                        contentDescription = "Games Hub",
                        tint = if (currentTab == SparkTab.GAMES) QuickyPurple else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onClubsClick,
                    modifier = Modifier
                        .testTag("top_bar_clubs")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Diversity3,
                        contentDescription = "Clubs",
                        tint = if (currentTab == SparkTab.CLUBS) QuickyPurple else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // v2.1 §3.9 — gear icon opens the dedicated Settings screen
            // (Profile page carries no settings rows any more).
            if (currentTab == SparkTab.PROFILE) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .testTag("top_bar_settings")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
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

// -----------------------------------------------------------------
// LIQUID GLASS BOTTOM NAVIGATION BAR
//
// A floating frosted-glass bar with rounded top corners that sits on
// top of the scrolling content (see SparkApp — the bar is overlaid in
// a Box instead of the Scaffold bottomBar slot), so cards and lists
// glide underneath the translucent surface. Games and Clubs moved to
// the Profile page header; the bar now carries the four core tabs.
// -----------------------------------------------------------------

/** Content height of the glass bar excluding the system gesture inset. */
private val GlassNavBarContentHeight = 86.dp

/**
 * Total bottom space occupied by the floating liquid-glass nav bar
 * (content height + system navigation-bar inset + breathing room).
 * Scrollable screens use this so their last items can be scrolled
 * fully into view above the glass.
 */
@Composable
fun glassNavBarOverlayHeight(extra: Dp = 12.dp): Dp =
    GlassNavBarContentHeight +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        extra

@Composable
fun SparkBottomNav(
    currentTab: SparkTab,
    unreadMessagesCount: Int,
    newMatchesCount: Int,
    onTabSelected: (SparkTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Cassy frosted glass (PRD §5.4): warm translucent surfaces with a
    // rose-gold hairline rim — the content behind keeps gliding beneath.
    val glassBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2A2632).copy(alpha = 0.66f),
                Color(0xFF141218).copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.78f),
                Color(0xFFF9F1F1).copy(alpha = 0.94f)
            )
        )
    }

    // Rose-gold hairline edge: bright champagne at the top rim, fading out
    // towards the bottom (replaces the old pure-white glass edge).
    val glassEdgeBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE2C9A2).copy(alpha = 0.55f),
                Color(0xFFB8956A).copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFD9BC8E).copy(alpha = 0.95f),
                Color(0xFFB8956A).copy(alpha = 0.10f)
            )
        )
    }

    val glassShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_nav_bar")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 22.dp,
                    shape = glassShape,
                    ambientColor = if (isDark) Color.Black else Color(0xFF4A3540).copy(alpha = 0.28f),
                    spotColor = if (isDark) Color.Black else QuickyPink.copy(alpha = 0.32f)
                )
                .clip(glassShape)
                .background(glassBrush)
                .border(BorderStroke(1.dp, glassEdgeBrush), glassShape)
        ) {
            // Rose-gold specular highlight — a thin champagne streak across
            // the top rim of the frosted bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFB8956A).copy(alpha = 0f),
                                Color(0xFFE8CFA6).copy(alpha = if (isDark) 0.75f else 1f),
                                Color(0xFFB8956A).copy(alpha = 0f)
                            )
                        )
                    )
            )

            // Soft warm inner glow right under the rim for that "liquid" depth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFE2C9A2).copy(alpha = if (isDark) 0.05f else 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassNavItem(
                    selected = currentTab == SparkTab.DISCOVER,
                    onClick = { onTabSelected(SparkTab.DISCOVER) },
                    icon = if (currentTab == SparkTab.DISCOVER) Icons.Filled.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
                    label = "Discover",
                    accent = QuickyPink,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_discover")
                )

                GlassNavItem(
                    selected = currentTab == SparkTab.MATCHES,
                    onClick = { onTabSelected(SparkTab.MATCHES) },
                    icon = if (currentTab == SparkTab.MATCHES) Icons.Filled.PeopleAlt else Icons.Outlined.PeopleAlt,
                    label = "Matches",
                    badgeCount = newMatchesCount,
                    accent = QuickyPink,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_matches")
                )

                GlassNavItem(
                    selected = currentTab == SparkTab.CHATS,
                    onClick = { onTabSelected(SparkTab.CHATS) },
                    icon = if (currentTab == SparkTab.CHATS) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                    label = "Chats",
                    badgeCount = unreadMessagesCount,
                    accent = QuickyPink,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_chats")
                )

                GlassNavItem(
                    selected = currentTab == SparkTab.PROFILE,
                    onClick = { onTabSelected(SparkTab.PROFILE) },
                    icon = if (currentTab == SparkTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    label = "Profile",
                    accent = QuickyPink,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_profile")
                )
            }
        }
    }
}

/**
 * A single perfectly-centered glass tab item: icon wrapped in an
 * animated gradient pill with the label underneath, plus a springy
 * icon scale transition when the tab is selected (Cassy motion).
 */
@Composable
private fun GlassNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    accent: Color,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "glass_nav_color"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        label = "glass_nav_pill"
    )
    // Cassy animated icon transition: the icon springs up when selected.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = 400f
        ),
        label = "glass_nav_icon_scale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Rosewood gradient pill highlight behind the active icon
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 32.dp)
                    .graphicsLayer { alpha = pillAlpha }
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                accent.copy(alpha = 0.24f),
                                QuickyPurple.copy(alpha = 0.16f)
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        BorderStroke(1.dp, accent.copy(alpha = 0.50f)),
                        RoundedCornerShape(16.dp)
                    )
            )

            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = QuickyPink,
                            contentColor = Color.White
                        ) {
                            Text(badgeCount.toString())
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}
