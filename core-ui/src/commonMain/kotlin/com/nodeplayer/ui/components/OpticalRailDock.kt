package com.nodeplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.PlayerNavDestination
import com.nodeplayer.ui.component.backdrop.drawBackdrop
import com.nodeplayer.ui.component.backdrop.effects.blur
import com.nodeplayer.ui.component.backdrop.effects.lens
import com.nodeplayer.ui.component.backdrop.effects.vibrancy
import com.nodeplayer.ui.component.backdrop.highlight.Highlight
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

    val targetY = selectedIndex * (tabHeightDp.value + tabSpacingDp.value)

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
                shape = { railShape },
                effects = {
                    vibrancy()
                    blur(16f.dp.toPx())
                    if (lensActive) {
                        val railW = 96f.dp.toPx()
                        lens(
                            refractionHeight = (railW * 0.18f).coerceAtMost(20f.dp.toPx()),
                            refractionAmount = (railW * 0.16f) * (themeConfig.lensRefractionStrength / 0.85f),
                            chromaticAberration = true,
                            chromaticAberrationAmount = 0.25f
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
                                    chromaticAberration = true,
                                    chromaticAberrationAmount = 0.30f
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
            verticalArrangement = Arrangement.spacedBy(tabSpacingDp)
        ) {
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
