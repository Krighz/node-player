package com.nodeplayer.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.common.AppModeState
import com.nodeplayer.model.GlassThemeConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import androidx.compose.animation.core.spring
import com.nodeplayer.model.GlassComponentType
import com.nodeplayer.ui.theme.LocalGlassControlsConfig
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.MotionTokens
import com.nodeplayer.ui.debug.GlassDebug
import com.nodeplayer.ui.theme.pressScale

data class ConvxNavTabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

val DEFAULT_CONVX_NAV_TABS = listOf(
    ConvxNavTabItem("home", "Home", Icons.Rounded.Home),
    ConvxNavTabItem("library", "Library", Icons.Rounded.LibraryMusic),
    ConvxNavTabItem("equalizer", "Equalizer", Icons.Rounded.GraphicEq),
    ConvxNavTabItem("settings", "Settings", Icons.Rounded.Settings)
)

/**
 * Convx-style Floating Liquid Glass Capsule Dock.
 * Features:
 * - Continuous squircle capsule shape with Kyant0 SDF lens refraction & specular rim.
 * - Sliding lens puck with spring physics and finger tracking.
 * - Layer separation: text and icons rendered strictly in a clean pass above glass (no torn text).
 * - Circular glass Search button on the right edge.
 */
@Composable
fun ConvxLiquidDock(
    selectedRoute: String,
    onRouteChange: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    items: List<ConvxNavTabItem> = DEFAULT_CONVX_NAV_TABS,
    searchButtonVisible: Boolean = true,
    backdrop: Backdrop = remember { emptyBackdrop() },
    isPerformanceMode: Boolean = false,
    shaderEnabled: Boolean = true,
    chromaticFringe: Float = 0.25f,
    lensStrength: Float = 0.85f,
    rimSpecular: Float = 0.85f,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val themeConfig = LocalGlassThemeConfig.current
    val controlsConfig = LocalGlassControlsConfig.current
    val resolvedDock = controlsConfig.resolve(GlassComponentType.DOCK)
    val density = LocalDensity.current
    val isDark = themeConfig.themeMode != "LIGHT"

    val lensActive = themeConfig.dockGlassLensEnabled && shaderEnabled && !isPerformanceMode && themeConfig.glassStrength != "OFF" && !resolvedDock.isPlainSquircle && controlsConfig.masterEnabled
    val rimActive = themeConfig.dockGlassRimEnabled && themeConfig.glassStrength != "OFF"
    val puckActive = themeConfig.dockGlassPuckEnabled

    GlassDebug.logThrottled(
        "ConvxLiquidDock",
        "lensActive=$lensActive (dockGlassLens=${themeConfig.dockGlassLensEnabled}, shaderEnabled=$shaderEnabled, !perf=${!isPerformanceMode}, strength=${themeConfig.glassStrength}, !plain=${!resolvedDock.isPlainSquircle}, master=${controlsConfig.masterEnabled}), " +
        "rimActive=$rimActive, puckActive=$puckActive, blurDp=${resolvedDock.blurDp}, lensH=${resolvedDock.lensHeightDp}, lensAmt=${resolvedDock.lensAmountDp}, backdrop=${backdrop::class.simpleName}"
    )

    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    var pillWidthPx by remember { mutableIntStateOf(0) }
    val tabWidthPx = if (pillWidthPx > 0 && items.isNotEmpty()) pillWidthPx.toFloat() / items.size.toFloat() else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragTouchXPx by remember { mutableFloatStateOf(0f) }

    val activeTargetIndex = if (isDragging && tabWidthPx > 0f) {
        (dragTouchXPx / tabWidthPx).toInt().coerceIn(0, items.size - 1)
    } else {
        selectedIndex
    }

    val dockShape = remember { ContinuousCapsule() }
    val puckShape = remember { ContinuousRoundedRectangle(resolvedDock.cornerShapeDp.dp) }
    val searchShape = CircleShape

    val tabWidthDp = if (tabWidthPx > 0f) with(density) { tabWidthPx.toDp() } else 64.dp
    val targetPuckOffsetDp = tabWidthDp * activeTargetIndex

    val animatedPuckOffsetDp by animateDpAsState(
        targetValue = targetPuckOffsetDp,
        animationSpec = spring(
            stiffness = resolvedDock.springStiffness,
            dampingRatio = resolvedDock.springDamping
        ),
        label = "ConvxPuckOffset"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main tabs capsule container
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .height(60.dp)
                .onSizeChanged { pillWidthPx = it.width }
                .then(
                    if (lensActive) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { dockShape },
                            effects = {
                                blur(resolvedDock.blurDp.dp.toPx())
                                lens(
                                    resolvedDock.lensHeightDp.dp.toPx(),
                                    resolvedDock.lensAmountDp.dp.toPx(),
                                    chromaticAberration = resolvedDock.chromaticAberration > 0.05f
                                )
                                vibrancy()
                            },
                            highlight = {
                                if (rimActive) {
                                    Highlight.Ambient.copy(
                                        width = 1.2f.dp,
                                        alpha = (0.75f * resolvedDock.highlightStrength).coerceIn(0.1f, 1f)
                                    )
                                } else Highlight.Ambient.copy(alpha = 0f)
                            },
                            shadow = {
                                Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.28f * resolvedDock.shadowStrength))
                            },
                            onDrawSurface = {
                                val surfaceAlpha = (resolvedDock.tintOpacity * (if (isDark) 0.55f else 0.40f)).coerceIn(0.15f, 0.75f)
                                val surfaceColor = if (isDark) Color(0xFF141923) else Color.White
                                drawRect(surfaceColor.copy(alpha = surfaceAlpha))
                            }
                        )
                    } else {
                        val fallbackAlpha = (resolvedDock.tintOpacity * (if (isDark) 0.75f else 0.60f)).coerceIn(0.25f, 0.85f)
                        val fallbackColor = if (isDark) Color(0xFF141923) else Color.White
                        Modifier
                            .clip(dockShape)
                            .background(fallbackColor.copy(alpha = fallbackAlpha))
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (rimActive) 0.35f else 0.12f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                ),
                                shape = dockShape
                            )
                    }
                )
                .pointerInput(items.size) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragTouchXPx = offset.x
                        },
                        onDragEnd = {
                            if (tabWidthPx > 0f) {
                                val target = (dragTouchXPx / tabWidthPx).toInt().coerceIn(0, items.size - 1)
                                onRouteChange(items[target].route)
                            }
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragTouchXPx = change.position.x
                        }
                    )
                }
        ) {
            // Layer 1: Sliding lens puck (under foreground labels/icons)
            if (puckActive && pillWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = animatedPuckOffsetDp + 4.dp, y = 4.dp)
                        .width(tabWidthDp - 8.dp)
                        .height(52.dp)
                        .then(
                            if (lensActive) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { puckShape },
                                    effects = {
                                        lens(
                                            resolvedDock.lensHeightDp.dp.toPx() * 0.75f,
                                            resolvedDock.lensAmountDp.dp.toPx() * 0.75f,
                                            chromaticAberration = resolvedDock.chromaticAberration > 0.05f
                                        )
                                    },
                                    highlight = {
                                        Highlight.Ambient.copy(
                                            width = 1.dp,
                                            alpha = (0.7f * resolvedDock.highlightStrength).coerceIn(0.1f, 1f)
                                        )
                                    },
                                    onDrawSurface = {
                                        drawRect(accent.copy(alpha = 0.32f))
                                    }
                                )
                            } else {
                                Modifier
                                    .clip(puckShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                accent.copy(alpha = if (isPerformanceMode) 0.22f else 0.28f),
                                                Color.White.copy(alpha = 0.10f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                accent.copy(alpha = 0.60f),
                                                Color.White.copy(alpha = 0.15f)
                                            )
                                        ),
                                        shape = puckShape
                                    )
                            }
                        )
                )
            }

            // Layer 2: Foreground tabs (strictly un-refracted above glass)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == activeTargetIndex
                    val interactionSource = remember { MutableInteractionSource() }

                    val itemScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = MotionTokens.Snappy,
                        label = "DockTabScale"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(itemScale)
                            .selectable(
                                selected = isSelected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onRouteChange(item.route) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) accent else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Circular Glass Search Button
        if (searchButtonVisible) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .pressScale(onClick = onSearchClick)
                    .then(
                        if (lensActive) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { searchShape },
                                effects = {
                                    blur(14.dp.toPx())
                                    lens(14.dp.toPx(), 12.dp.toPx(), chromaticAberration = chromaticFringe > 0.05f)
                                    vibrancy()
                                },
                                highlight = {
                                    if (rimActive) {
                                        Highlight.Ambient.copy(width = 1.dp, alpha = 0.85f * rimSpecular)
                                    } else Highlight.Ambient.copy(alpha = 0f)
                                },
                                shadow = {
                                    Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.25f))
                                },
                                onDrawSurface = {
                                    val surfaceAlpha = (resolvedDock.tintOpacity * (if (isDark) 0.55f else 0.40f)).coerceIn(0.15f, 0.75f)
                                    val surfaceColor = if (isDark) Color(0xFF141923) else Color.White
                                    drawRect(surfaceColor.copy(alpha = surfaceAlpha))
                                }
                            )
                        } else {
                            val fallbackAlpha = (resolvedDock.tintOpacity * (if (isDark) 0.75f else 0.60f)).coerceIn(0.25f, 0.85f)
                            val fallbackColor = if (isDark) Color(0xFF141923) else Color.White
                            Modifier
                                .clip(searchShape)
                                .background(fallbackColor.copy(alpha = fallbackAlpha))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.35f),
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                    ),
                                    shape = searchShape
                                )
                        }
                    ),
                contentAlignment = Alignment.Center
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
}

/**
 * Tactical Mode Switcher Capsule (Online vs. Offline) with smooth sliding puck and haptic feedback.
 */
@Composable
fun ModeToggleCapsule(
    modifier: Modifier = Modifier
) {
    val isOnline by AppModeState.isOnlineMode.collectAsState()
    val haptic = LocalHapticFeedback.current
    val capsuleShape = remember { ContinuousCapsule() }
    val puckShape = remember { ContinuousCapsule() }

    val puckOffset by animateDpAsState(
        targetValue = if (isOnline) 34.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 400f
        ),
        label = "ModePuckOffset"
    )

    Box(
        modifier = modifier
            .width(72.dp)
            .height(34.dp)
            .clip(capsuleShape)
            .background(if (isOnline) Color(0xFF0F232A).copy(alpha = 0.78f) else Color(0xFF14171A).copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = if (isOnline) Color(0xFF00E5FF).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                shape = capsuleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                AppModeState.toggleMode()
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Sliding dual-state puck
        Box(
            modifier = Modifier
                .offset(x = puckOffset)
                .size(width = 34.dp, height = 30.dp)
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

        // Dual icons row
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = "Offline Mode",
                    tint = if (!isOnline) Color(0xFF81C784) else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(15.dp)
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = "Online Mode",
                    tint = if (isOnline) Color(0xFF004D40) else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
