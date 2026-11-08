package com.nodeplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.common.AppModeState
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.PlayerNavDestination
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.MotionTokens
import kotlin.math.roundToInt

data class OpticalRailTab(
    val destination: PlayerNavDestination,
    val title: String,
    val icon: ImageVector
)

val OPTICAL_RAIL_TABS = listOf(
    OpticalRailTab(PlayerNavDestination.HOME, "Home", Icons.Rounded.Home),
    OpticalRailTab(PlayerNavDestination.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    OpticalRailTab(PlayerNavDestination.EQUALIZER, "Equalizer", Icons.Rounded.GraphicEq),
    OpticalRailTab(PlayerNavDestination.SETTINGS, "Settings", Icons.Rounded.Settings)
)

/**
 * Convx-style Liquid Glass Optical Rail Dock (Windows / Desktop).
 * Features:
 * - Vertical squircle rail with Kyant0 SDF lens refraction and specular rim
 * - Floating liquid glass puck under active tab with spring motion
 * - Responsive mouse interaction
 * - Settings (Lines 68-70: Lens, Rim, Puck toggles)
 */
@Composable
fun OpticalRailDock(
    currentNav: PlayerNavDestination,
    onNavSelect: (PlayerNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = remember { emptyBackdrop() },
    themeConfig: GlassThemeConfig = LocalGlassThemeConfig.current
) {
    val accent = LocalAccentColor.current
    val tabs = OPTICAL_RAIL_TABS

    // Dock glass settings (Line 68: Lens, Line 69: Rim, Line 70: Puck)
    val lensActive = themeConfig.dockGlassLensEnabled && !themeConfig.isPerformanceMode && themeConfig.glassStrength != "OFF"
    val rimActive = themeConfig.dockGlassRimEnabled && themeConfig.glassStrength != "OFF"
    val puckActive = themeConfig.dockGlassPuckEnabled

    val selectedIndex = tabs.indexOfFirst { it.destination == currentNav }.coerceAtLeast(0)
    val tabHeightDp = 84.dp
    val tabSpacingDp = 8.dp

    val targetY = 62.dp.value + selectedIndex * (tabHeightDp.value + tabSpacingDp.value)

    val animatedY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = MotionTokens.ConvxPuck,
        label = "OpticalBubbleY"
    )

    val railShape = remember { ContinuousRoundedRectangle(48.dp) }
    val puckShape = remember { ContinuousRoundedRectangle(24.dp) }

    // Vertical outer liquid glass pill container
    Box(
        modifier = modifier
            .width(96.dp)
            .clip(railShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { railShape },
                effects = {
                    vibrancy()
                    blur(16f.dp.toPx())
                    if (lensActive) {
                        val railW = 96f.dp.toPx()
                        lens(
                            refractionHeight = (railW * 0.18f).coerceAtMost(20f.dp.toPx()),
                            refractionAmount = (railW * 0.16f) * (themeConfig.lensRefractionStrength / 0.85f),
                            chromaticAberration = true
                        )
                    }
                },
                highlight = if (rimActive) {
                    { Highlight.Default.copy(alpha = 0.85f * themeConfig.lensRimSpecular) }
                } else null,
                onDrawSurface = {
                    drawRect(Color(0xFF0F172A).copy(alpha = (themeConfig.effectiveTintAlpha * 0.75f).coerceIn(0.12f, 0.85f)))
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (rimActive) 0.32f else 0.14f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = railShape
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // The Convex Liquid Glass Optical Lens Puck
        if (puckActive) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (animatedY * density).roundToInt()) }
                    .width(88.dp)
                    .height(tabHeightDp)
                    .scale(1.04f)
                    .clip(puckShape)
                    .drawBackdrop(
                        shape = { puckShape },
                        effects = {
                            if (lensActive) {
                                val puckW = 88f.dp.toPx()
                                lens(
                                    refractionHeight = (puckW * 0.12f).coerceAtMost(12f.dp.toPx()),
                                    refractionAmount = (puckW * 0.14f) * (themeConfig.lensRefractionStrength / 0.85f),
                                    chromaticAberration = true
                                )
                            }
                        },
                        highlight = if (rimActive) {
                            { Highlight.Ambient.copy(alpha = 0.85f * themeConfig.lensRimSpecular) }
                        } else null,
                        onDrawSurface = {
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        accent.copy(alpha = 0.30f),
                                        Color(0xFF0F172A).copy(alpha = 0.45f)
                                    ),
                                    radius = 160f
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (rimActive) 0.70f else 0.30f),
                                Color(0xFFBAE6FD).copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.25f)
                            )
                        ),
                        shape = puckShape
                    )
            )
        }

        // Tab Items Column
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(tabSpacingDp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Desktop Mode Switcher at top of rail
            DesktopModeRailSwitcher(
                modifier = Modifier.padding(bottom = 8.dp)
            )

            tabs.forEachIndexed { index, tab ->
                val isSelected = currentNav == tab.destination
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tabHeightDp)
                        .clip(puckShape)
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

/**
 * Vertical Mode Switcher for Desktop Optical Rail (Cloud / Online vs. Disk / Offline).
 */
@Composable
fun DesktopModeRailSwitcher(
    modifier: Modifier = Modifier
) {
    val isOnline by AppModeState.isOnlineMode.collectAsState()
    val capsuleShape = remember { ContinuousRoundedRectangle(16.dp) }
    val puckShape = remember { ContinuousRoundedRectangle(12.dp) }

    val puckOffsetY by animateDpAsState(
        targetValue = if (isOnline) 0.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 400f
        ),
        label = "DesktopRailPuckY"
    )

    Column(
        modifier = modifier
            .width(68.dp)
            .height(54.dp)
            .clip(capsuleShape)
            .background(if (isOnline) Color(0xFF0F232A).copy(alpha = 0.75f) else Color(0xFF14171A).copy(alpha = 0.70f))
            .border(
                width = 1.dp,
                color = if (isOnline) Color(0xFF00E5FF).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.12f),
                shape = capsuleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                AppModeState.toggleMode()
            }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Sliding puck
            Box(
                modifier = Modifier
                    .offset(y = puckOffsetY)
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(puckShape)
                    .background(
                        if (isOnline) {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.90f),
                                    Color(0xFF0097A7).copy(alpha = 0.85f)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF37474F).copy(alpha = 0.85f),
                                    Color(0xFF263238).copy(alpha = 0.90f)
                                )
                            )
                        }
                    )
                    .border(
                        width = 0.8.dp,
                        color = if (isOnline) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f),
                        shape = puckShape
                    )
            )

            // Vertical icons Column
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceAround,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = "Online Mode",
                        tint = if (isOnline) Color(0xFF004D40) else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = "Offline Mode",
                        tint = if (!isOnline) Color(0xFF81C784) else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
