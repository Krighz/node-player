package com.nodeplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.PlayerNavDestination
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import kotlin.math.roundToInt

data class OpticalRailTab(
    val destination: PlayerNavDestination,
    val title: String,
    val icon: ImageVector
)

val OPTICAL_RAIL_TABS = listOf(
    OpticalRailTab(PlayerNavDestination.HOME, "Home", Icons.Rounded.Home),
    OpticalRailTab(PlayerNavDestination.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    OpticalRailTab(PlayerNavDestination.SETTINGS, "Settings", Icons.Rounded.Settings)
)

@Composable
fun OpticalRailDock(
    currentNav: PlayerNavDestination,
    onNavSelect: (PlayerNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    themeConfig: GlassThemeConfig = LocalGlassThemeConfig.current
) {
    val accent = LocalAccentColor.current
    val tabs = OPTICAL_RAIL_TABS

    val selectedIndex = tabs.indexOfFirst { it.destination == currentNav }.coerceAtLeast(0)
    val tabHeightDp = 84.dp
    val tabSpacingDp = 8.dp

    // Target Y for active index
    val targetY = selectedIndex * (tabHeightDp.value + tabSpacingDp.value)

    val animatedY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 380f
        ),
        label = "OpticalBubbleY"
    )

    // Vertical outer glass pill container
    Box(
        modifier = modifier
            .width(96.dp)
            .clip(RoundedCornerShape(48.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.22f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(48.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // The Convex Liquid Glass Optical Lens Bubble
        Box(
            modifier = Modifier
                .offset { IntOffset(0, (animatedY * density).roundToInt()) }
                .width(88.dp)
                .height(tabHeightDp)
                .scale(1.04f)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = accent.copy(alpha = 0.30f),
                    spotColor = Color.White.copy(alpha = 0.40f)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            accent.copy(alpha = 0.35f),
                            Color(0xFF0F172A).copy(alpha = 0.55f)
                        ),
                        center = Offset(70f, 60f),
                        radius = 160f
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.70f),
                            Color(0xFFBAE6FD).copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.25f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .drawBehind {
                    // Inner specular highlight on top edge
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = 20.dp.toPx(),
                        center = Offset(size.width * 0.35f, size.height * 0.25f)
                    )
                }
        )

        // Tab Items Column
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(tabSpacingDp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = currentNav == tab.destination
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tabHeightDp)
                        .clip(RoundedCornerShape(32.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onNavSelect(tab.destination)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(if (isSelected) 28.dp else 24.dp)
                                .scale(if (isSelected) 1.15f else 1.0f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = tab.title,
                            fontSize = if (isSelected) 12.5.sp else 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.60f)
                        )
                    }
                }
            }
        }
    }
}
