package com.nodeplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.nodeplayer.ui.PlayerNavDestination
import com.nodeplayer.ui.theme.LocalAccentColor

data class LiquidDockTab(
    val destination: PlayerNavDestination,
    val title: String,
    val icon: ImageVector
)

val LIQUID_DOCK_TABS = listOf(
    LiquidDockTab(PlayerNavDestination.HOME, "Home", Icons.Rounded.Home),
    LiquidDockTab(PlayerNavDestination.NEW, "New", Icons.Rounded.Explore),
    LiquidDockTab(PlayerNavDestination.LIBRARY, "Library", Icons.Rounded.LibraryMusic)
)

/**
 * Floating Liquid Dock combining:
 * 1. Upstream LiquidBottomTabs 64dp capsule bar with 56dp sliding puck and DampedDragAnimation.
 * 2. Companion 56dp circular LiquidButton for Search sharing pageBackdrop.
 */
@Composable
fun LiquidDock(
    currentNav: PlayerNavDestination,
    onNavSelect: (PlayerNavDestination) -> Unit,
    backdrop: Backdrop,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current
) {
    val selectedIndex = remember(currentNav) {
        val idx = LIQUID_DOCK_TABS.indexOfFirst { it.destination == currentNav }
        if (idx >= 0) idx else 0
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main 64dp LiquidBottomTabs capsule bar
        LiquidBottomTabs(
            selectedTabIndex = { selectedIndex },
            onTabSelected = { index ->
                if (index in LIQUID_DOCK_TABS.indices) {
                    onNavSelect(LIQUID_DOCK_TABS[index].destination)
                }
            },
            backdrop = backdrop,
            tabsCount = LIQUID_DOCK_TABS.size,
            accentColor = accentColor,
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 340.dp)
        ) {
            LIQUID_DOCK_TABS.forEachIndexed { index, tab ->
                val isSelected = selectedIndex == index
                LiquidBottomTab(
                    onClick = { onNavSelect(tab.destination) }
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Companion 56dp circular Search LiquidButton
        LiquidButton(
            onClick = onSearchClick,
            backdrop = backdrop,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
