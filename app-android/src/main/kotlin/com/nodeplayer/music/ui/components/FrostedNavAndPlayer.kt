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
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt

data class NavTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

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
                        } else {
                            if (totalDragY < -40f) {
                                onClick() // Swipe up opens Now Playing
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
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .glassRefraction(
                shape = shape,
                enabled = shaderEnabled && !isPerformanceMode,
                refractionIndex = 0.65f,
                chromaticFringe = chromaticFringe,
                grainStrength = grainStrength
            )
            .glassSurface(hazeState = hazeState, shape = shape, tintAlpha = 0.65f)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            )
            .then(gestureModifier)
    ) {
        // Progress track at bottom
        if (showWaveProgress) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
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
            // Artwork thumbnail with fallback
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

            // Soundwave visualizer in mini player (real audio reactive)
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
 * Optical Magnifying Lens Dock.
 * Features a floating glass pill with an AGSL magnifying lens bubble that springs between tabs
 * and can be dragged across tabs with a light haptic tick, plus an adjacent circular glass search button.
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

    val themeConfig = LocalGlassThemeConfig.current
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
        label = "LensBubbleSpring"
    )

    val pillShape = RoundedCornerShape(32.dp)
    val lensShape = RoundedCornerShape(24.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Floating Pill Dock with Draggable Magnifying Lens Bubble
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .onSizeChanged { pillWidthPx = it.width }
                .glassSurface(hazeState = hazeState, shape = pillShape, tintAlpha = (themeConfig.effectiveTintAlpha * 0.75f).coerceIn(0.08f, 0.85f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f)
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
            // Lens Bubble Indicator
            if (pillWidthPx > 0 && tabWidthPx > 0f) {
                val bubbleWidthDp = with(density) { (tabWidthPx - 10f).toDp().coerceAtLeast(48.dp) }
                val bubbleWidthPx = with(density) { bubbleWidthDp.toPx() }
                val bubbleOffsetXPx = springCenterXPx - bubbleWidthPx * 0.5f

                Box(
                    modifier = Modifier
                        .offset { IntOffset(bubbleOffsetXPx.roundToInt(), 0) }
                        .align(Alignment.CenterStart)
                        .width(bubbleWidthDp)
                        .height(50.dp)
                        .glassLensBubble(
                            shape = lensShape,
                            enabled = shaderEnabled && !isPerformanceMode,
                            magnification = 0.22f * (lensStrength / 0.85f).coerceIn(0.5f, 2.0f),
                            refractionIndex = 0.65f * (lensStrength / 0.85f).coerceIn(0.5f, 2.0f),
                            chromaticFringe = chromaticFringe,
                            rimStrength = rimSpecular,
                            rimColor = Color.White.copy(alpha = 0.90f * rimSpecular),
                            grainStrength = grainStrength
                        )
                        .glassSurface(
                            hazeState = hazeState,
                            shape = lensShape,
                            tintAlpha = (themeConfig.effectiveTintAlpha * 0.22f).coerceIn(0.02f, 0.35f)
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.15f * rimSpecular),
                                    Color.White.copy(alpha = 0.02f),
                                    Color.White.copy(alpha = 0.08f * rimSpecular)
                                )
                            ),
                            shape = lensShape
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.90f * rimSpecular),
                                    Color(0xFFBAE6FD).copy(alpha = 0.35f * rimSpecular),
                                    Color.White.copy(alpha = 0.50f * rimSpecular)
                                )
                            ),
                            shape = lensShape
                        )
                )
            }

            // Tab Items
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

                    val activeMag = 1.0f + 0.22f * (lensStrength / 0.85f).coerceIn(0.5f, 2.0f)
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

        // 2. Separate Circular Glass Search Button
        if (searchButtonVisible) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .glassSurface(hazeState = hazeState, shape = CircleShape, tintAlpha = 0.60f)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
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
 * Backward compatibility alias for FrostedBottomBar forwarding to LensDock.
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
