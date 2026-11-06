package com.nodeplayer.music.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.music.ui.theme.MotionTokens
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.ui.component.backdrop.drawBackdrop
import com.nodeplayer.ui.component.backdrop.effects.blur
import com.nodeplayer.ui.component.backdrop.effects.lens
import com.nodeplayer.ui.component.backdrop.effects.vibrancy
import com.nodeplayer.ui.component.backdrop.highlight.Highlight
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt

data class NavTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Convx-style Liquid Glass Mini Player.
 * Floating squircle capsule with edge refraction, specular rim, audio-reactive animated equalizer bars,
 * album artwork thumbnail, track metadata, and gesture controls.
 */
@Composable
fun FrostedMiniPlayer(
    currentTrack: Track?,
    isPlaying: Boolean,
    progressFraction: Float,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    gesturesEnabled: Boolean = true,
    hazeState: HazeState? = null,
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false,
    shaderEnabled: Boolean = !isPerformanceMode,
    chromaticFringe: Float = 1.0f,
    grainStrength: Float = 0.0f,
    showWaveProgress: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val shape = RoundedCornerShape(GlassTokens.cardCornerRadius)
    val accent = LocalAccentColor.current
    val themeConfig = LocalGlassThemeConfig.current

    // Dock glass settings (Line 80: Lens toggle, Line 81: Rim toggle)
    val lensActive = themeConfig.dockGlassLensEnabled && shaderEnabled && !isPerformanceMode && themeConfig.glassStrength != "OFF"
    val rimActive = themeConfig.dockGlassRimEnabled && themeConfig.glassStrength != "OFF"

    val gestureModifier = if (gesturesEnabled) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onPlayPauseClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                var totalDragX = 0f
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        if (abs(totalDragX) > abs(totalDragY)) {
                            if (totalDragX < -40f) {
                                onSkipNextClick()
                            } else if (totalDragX > 40f) {
                                onSkipPreviousClick()
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    }
                )
            }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(gestureModifier)
            .clip(shape)
            .drawBackdrop(
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(12f.dp.toPx())
                    if (lensActive) {
                        lens(
                            refractionHeight = 16f.dp.toPx(),
                            refractionAmount = 14f.dp.toPx() * (themeConfig.lensRefractionStrength / 0.85f),
                            chromaticAberration = true,
                            chromaticAberrationAmount = 0.6f * chromaticFringe
                        )
                    }
                },
                highlight = if (rimActive) {
                    { Highlight.Default.copy(alpha = 0.85f * themeConfig.lensRimSpecular) }
                } else null,
                onDrawSurface = {
                    drawRect(Color(0xFF101424).copy(alpha = (themeConfig.effectiveTintAlpha * 0.75f).coerceIn(0.12f, 0.85f)))
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (rimActive) 0.28f else 0.12f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
    ) {
        // Linear Progress bar on top edge of mini player
        if (progressFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .height(2.5.dp)
                    .background(accent)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork thumbnail with squircle clip
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222433)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(24.dp)
                )
                AsyncImage(
                    model = currentTrack,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Real audio-reactive equalizer bars in mini player
            SoundWaveVisualizer(isPlaying = isPlaying, barColor = accent, maxHeight = 16.dp, rmsLevel = rmsLevel)
            Spacer(modifier = Modifier.width(6.dp))

            // Play / Pause
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Skip Next
            IconButton(onClick = onSkipNextClick) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Skip Next",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * Convx-style Liquid Glass Dock.
 * Features:
 * - Continuous squircle capsule container with Kyant0 SDF lens refraction & specular rim
 * - Sliding liquid selection puck under active tab with spring motion and finger-tracking highlight
 * - Active tab spring magnification and caustics glow
 * - Adjacent circular glass search button
 * - Safe fallback to plain frosted blur in performance mode or if runtime shaders are unsupported.
 */
@Composable
fun LensDock(
    selectedRoute: String,
    onRouteChange: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    hazeState: HazeState? = null,
    isPerformanceMode: Boolean = false,
    shaderEnabled: Boolean = !isPerformanceMode,
    chromaticFringe: Float = 1.0f,
    lensStrength: Float = 0.85f,
    grainStrength: Float = 0.0f,
    searchButtonVisible: Boolean = true,
    rimSpecular: Float = 0.85f,
    modifier: Modifier = Modifier
) {
    val items = remember {
        listOf(
            NavTabItem("home", "Home", Icons.Rounded.Home),
            NavTabItem("library", "Library", Icons.Rounded.LibraryMusic),
            NavTabItem("equalizer", "Equalizer", Icons.Rounded.GraphicEq),
            NavTabItem("settings", "Settings", Icons.Rounded.Settings)
        )
    }

    val view = LocalView.current
    val accent = LocalAccentColor.current
    val density = LocalDensity.current
    val themeConfig = LocalGlassThemeConfig.current

    // Dock glass settings (Line 274: Lens, Line 275: Rim, Line 276: Puck)
    val lensActive = themeConfig.dockGlassLensEnabled && shaderEnabled && !isPerformanceMode && themeConfig.glassStrength != "OFF"
    val rimActive = themeConfig.dockGlassRimEnabled && themeConfig.glassStrength != "OFF"
    val puckActive = themeConfig.dockGlassPuckEnabled

    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

    var pillWidthPx by remember { mutableIntStateOf(0) }
    val tabWidthPx = if (pillWidthPx > 0) pillWidthPx / items.size.toFloat() else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragTouchXPx by remember { mutableFloatStateOf(0f) }
    var lastHapticIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        if (!isDragging) {
            lastHapticIndex = selectedIndex
        }
    }

    val targetCenterXPx = if (isDragging && tabWidthPx > 0f) {
        dragTouchXPx.coerceIn(tabWidthPx * 0.5f, pillWidthPx - tabWidthPx * 0.5f)
    } else if (tabWidthPx > 0f) {
        (selectedIndex + 0.5f) * tabWidthPx
    } else 0f

    val activeSpringSpec = remember(themeConfig, isDragging) {
        if (isDragging) {
            spring(dampingRatio = 0.95f, stiffness = 900f)
        } else {
            MotionTokens.resolveSpringSpec(themeConfig, spring(dampingRatio = 0.72f, stiffness = 340f))
        }
    }

    val springCenterXPx by animateFloatAsState(
        targetValue = targetCenterXPx,
        animationSpec = activeSpringSpec,
        label = "LiquidPuckSpring"
    )

    val pillShape = RoundedCornerShape(32.dp)
    val puckShape = RoundedCornerShape(22.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Floating Squircle Pill Dock with Liquid Glass Refraction & Draggable Puck
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .onSizeChanged { pillWidthPx = it.width }
                .clip(pillShape)
                .drawBackdrop(
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        blur(14f.dp.toPx())
                        if (lensActive) {
                            lens(
                                refractionHeight = 20f.dp.toPx(),
                                refractionAmount = 18f.dp.toPx() * (lensStrength / 0.85f),
                                chromaticAberration = true,
                                chromaticAberrationAmount = 0.5f * chromaticFringe
                            )
                        }
                    },
                    highlight = if (rimActive) {
                        { Highlight.Default.copy(alpha = 0.85f * rimSpecular) }
                    } else null,
                    onDrawSurface = {
                        drawRect(Color(0xFF0F1424).copy(alpha = (themeConfig.effectiveTintAlpha * 0.72f).coerceIn(0.12f, 0.85f)))
                    }
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (rimActive) 0.30f else 0.12f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    shape = pillShape
                )
                .pointerInput(items, tabWidthPx) {
                    if (tabWidthPx > 0f) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragTouchXPx = offset.x
                                val idx = (offset.x / tabWidthPx).toInt().coerceIn(0, items.lastIndex)
                                if (idx != lastHapticIndex) {
                                    lastHapticIndex = idx
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onRouteChange(items[idx].route)
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                val finalIdx = (dragTouchXPx / tabWidthPx).toInt().coerceIn(0, items.lastIndex)
                                onRouteChange(items[finalIdx].route)
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragTouchXPx = (dragTouchXPx + dragAmount).coerceIn(0f, pillWidthPx.toFloat())
                                val newIdx = (dragTouchXPx / tabWidthPx).toInt().coerceIn(0, items.lastIndex)
                                if (newIdx != lastHapticIndex) {
                                    lastHapticIndex = newIdx
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onRouteChange(items[newIdx].route)
                                }
                            }
                        )
                    }
                }
        ) {
            // 2. Convx-style Liquid Sliding Selection Puck (takes effect if puckActive is true)
            if (puckActive && pillWidthPx > 0 && tabWidthPx > 0f) {
                val puckWidthDp = with(density) { (tabWidthPx - 10f).toDp().coerceAtLeast(48.dp) }
                val puckWidthPx = with(density) { puckWidthDp.toPx() }
                val puckOffsetXPx = springCenterXPx - puckWidthPx * 0.5f

                Box(
                    modifier = Modifier
                        .offset { IntOffset(puckOffsetXPx.roundToInt(), 0) }
                        .align(Alignment.CenterStart)
                        .width(puckWidthDp)
                        .height(50.dp)
                        .clip(puckShape)
                        .drawBackdrop(
                            shape = { puckShape },
                            effects = {
                                if (lensActive) {
                                    lens(
                                        refractionHeight = 10f.dp.toPx(),
                                        refractionAmount = 14f.dp.toPx() * (lensStrength / 0.85f),
                                        chromaticAberration = true,
                                        chromaticAberrationAmount = 0.8f * chromaticFringe
                                    )
                                }
                            },
                            highlight = if (rimActive) {
                                { Highlight.Ambient.copy(alpha = 0.80f * rimSpecular) }
                            } else null,
                            onDrawSurface = {
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(
                                            accent.copy(alpha = 0.18f),
                                            Color.White.copy(alpha = 0.10f),
                                            accent.copy(alpha = 0.05f)
                                        )
                                    )
                                )
                            }
                        )
                        .border(
                            width = 0.85.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (rimActive) 0.35f else 0.15f),
                                    accent.copy(alpha = 0.15f)
                                )
                            ),
                            shape = puckShape
                        )
                )
            }

            // Tab Items Row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { tab ->
                    val isSelected = selectedRoute == tab.route
                    val tabColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f)

                    val activeMag = 1.0f + 0.18f * (lensStrength / 0.85f).coerceIn(0.5f, 2.0f)
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) activeMag else 1.0f,
                        animationSpec = activeSpringSpec,
                        label = "TabScale"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onRouteChange(tab.route)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) Color.White else tabColor,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tabColor,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.3.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Separate Circular Liquid Glass Search Button
        if (searchButtonVisible) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .drawBackdrop(
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(12f.dp.toPx())
                            if (lensActive) {
                                lens(
                                    refractionHeight = 16f.dp.toPx(),
                                    refractionAmount = 14f.dp.toPx() * (lensStrength / 0.85f),
                                    chromaticAberration = true
                                )
                            }
                        },
                        highlight = if (rimActive) {
                            { Highlight.Default.copy(alpha = 0.90f * rimSpecular) }
                        } else null,
                        onDrawSurface = {
                            drawRect(Color(0xFF0F1424).copy(alpha = 0.60f))
                        }
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (rimActive) 0.38f else 0.15f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSearchClick()
                    },
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
 * Backward compatibility alias for FrostedBottomBar forwarding to the new Liquid LensDock.
 */
@Composable
fun FrostedBottomBar(
    selectedRoute: String,
    onRouteChange: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    hazeState: HazeState? = null,
    isPerformanceMode: Boolean = false,
    shaderEnabled: Boolean = !isPerformanceMode,
    chromaticFringe: Float = 1.0f,
    grainStrength: Float = 0.0f,
    searchButtonVisible: Boolean = true,
    lensStrength: Float = 0.85f,
    rimSpecular: Float = 0.85f,
    modifier: Modifier = Modifier
) {
    LensDock(
        selectedRoute = selectedRoute,
        onRouteChange = onRouteChange,
        onSearchClick = onSearchClick,
        hazeState = hazeState,
        isPerformanceMode = isPerformanceMode,
        shaderEnabled = shaderEnabled,
        chromaticFringe = chromaticFringe,
        lensStrength = lensStrength,
        grainStrength = grainStrength,
        searchButtonVisible = searchButtonVisible,
        rimSpecular = rimSpecular,
        modifier = modifier
    )
}

/** Liquid dock naming alias. */
@Composable
fun LiquidDock(
    selectedRoute: String,
    onRouteChange: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LensDock(
        selectedRoute = selectedRoute,
        onRouteChange = onRouteChange,
        onSearchClick = onSearchClick,
        modifier = modifier
    )
}
