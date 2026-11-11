package com.nodeplayer.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidGlassIconButton
import com.nodeplayer.ui.components.LiquidGlassSlider
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen edge-to-edge immersive synced lyrics view.
 * Meets requirements LYR-01 through LYR-05:
 * - Edge-to-edge coverage hiding dock, mini-player, and search button.
 * - Active lyric line renders word-by-word gradient sweep fill with large bold left-aligned typography.
 * - Inactive lines dimmed, scaled down, and blurred based on distance from active line.
 * - Spring auto-scroll keeps active lyric in upper-middle; manual drag pauses auto-scroll with "Jump to current" pill.
 * - Controls and top bar fade out after 5s of inactivity; tapping screen wakes controls.
 * - Calls onKeepScreenOn(true) while active.
 */
@Composable
fun FullScreenLyricsView(
    track: Track?,
    lyrics: Lyrics?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isLoading: Boolean = false,
    isOnlineMode: Boolean = true,
    dominantColor: Color = Color(0xFF1E2433),
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
    onSearchAgain: () -> Unit = {},
    onOpenOffsetControls: () -> Unit = {},
    onKeepScreenOn: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val accentColor = LocalAccentColor.current
    val baseTextColor = Color.White

    // Keep screen on while lyrics view is active
    DisposableEffect(Unit) {
        onKeepScreenOn(true)
        onDispose {
            onKeepScreenOn(false)
        }
    }

    var userScrolledManually by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // 5-second inactivity auto-hide timer for playback controls and header
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(5000L)
            showControls = false
        }
    }

    fun registerInteraction() {
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
    }

    // Active line detection
    val lines = lyrics?.lines ?: emptyList()
    val activeIndex by remember(lines, currentPositionMs) {
        derivedStateOf {
            if (lines.isEmpty()) -1
            else lines.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)
        }
    }

    // Auto-scroll logic: spring smooth scroll to keep active lyric in upper-middle (~35% viewport)
    LaunchedEffect(activeIndex, userScrolledManually) {
        if (!userScrolledManually && activeIndex >= 0 && activeIndex < lines.size) {
            val targetScrollIndex = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScrollIndex)
        }
    }

    // Auto-resume auto-scroll after 8s of manual scroll inactivity
    LaunchedEffect(userScrolledManually, lastInteractionTime) {
        if (userScrolledManually) {
            delay(8000L)
            userScrolledManually = false
        }
    }

    val oledBackground = Color(0xFF070912)
    val ambientGradient = remember(dominantColor) {
        Brush.verticalGradient(
            0.0f to dominantColor.copy(alpha = 0.45f),
            0.45f to dominantColor.copy(alpha = 0.15f),
            0.85f to oledBackground,
            1.0f to oledBackground
        )
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragFraction by remember { mutableStateOf(0f) }
    val progressFraction = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val displayFraction = if (isDraggingSlider) sliderDragFraction else progressFraction

    fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(oledBackground)
            .background(ambientGradient)
            .pointerInput(Unit) {
                detectDragGestures { _, _ ->
                    registerInteraction()
                    userScrolledManually = true
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                registerInteraction()
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columnWidth = if (maxWidth > 600.dp) 560.dp else maxWidth

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Header Bar (Animated Visibility based on controls auto-hide)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(stiffness = 400f)) { -it },
                    exit = fadeOut(spring(stiffness = 400f)) + slideOutVertically(spring(stiffness = 400f)) { -it }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Close Lyrics",
                                tint = baseTextColor.copy(alpha = 0.85f),
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = track?.title ?: "No track playing",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = baseTextColor
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track?.artist ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = baseTextColor.copy(alpha = 0.65f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                registerInteraction()
                                onOpenOffsetControls()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Timing Offset",
                                tint = if ((lyrics?.offsetMs ?: 0L) != 0L) accentColor else baseTextColor.copy(alpha = 0.70f)
                            )
                        }
                    }
                }

                // 2. Lyrics List Stage
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = accentColor,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading synchronized lyrics…",
                                style = MaterialTheme.typography.bodyMedium.copy(color = baseTextColor.copy(alpha = 0.70f))
                            )
                        }
                    } else if (lyrics == null || (!lyrics.isSynced && lyrics.plainText.isEmpty())) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = if (!isOnlineMode) "No lyrics available offline" else "No lyrics",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = baseTextColor
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (!isOnlineMode) {
                                    "Lyrics aren't saved on this device. Switch to online mode to search."
                                } else {
                                    "We couldn't find lyrics for this song."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = baseTextColor.copy(alpha = 0.65f),
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            LiquidButton(
                                onClick = onSearchAgain,
                                enabled = isOnlineMode
                            ) {
                                Text(
                                    text = "Search again",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = baseTextColor
                                    )
                                )
                            }
                        }
                    } else if (!lyrics.isSynced && lyrics.plainText.isNotEmpty()) {
                        // Plain Text Fallback
                        Box(
                            modifier = Modifier
                                .widthIn(max = columnWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp)
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 48.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    Text(
                                        text = "Synced lyrics unavailable — showing plain text.",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = baseTextColor.copy(alpha = 0.50f)
                                        ),
                                        modifier = Modifier.padding(bottom = 24.dp)
                                    )
                                }
                                item {
                                    Text(
                                        text = lyrics.plainText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 20.sp,
                                            lineHeight = 30.sp,
                                            color = baseTextColor.copy(alpha = 0.85f)
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        // Synced Immersive Lyrics View
                        Box(
                            modifier = Modifier
                                .width(columnWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp)
                        ) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    top = if (showControls) 40.dp else 80.dp,
                                    bottom = if (showControls) 160.dp else 80.dp
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(
                                    items = lines,
                                    key = { index, line -> "${line.timestampMs}_$index" }
                                ) { index, line ->
                                    LyricLineItem(
                                        line = line,
                                        index = index,
                                        activeIndex = activeIndex,
                                        currentPositionMs = currentPositionMs,
                                        baseColor = baseTextColor,
                                        onSeek = { ts ->
                                            registerInteraction()
                                            onSeekTo(ts)
                                        },
                                        activeHighlightColor = accentColor
                                    )
                                }
                            }

                            // Floating "Jump to current" pill
                            androidx.compose.animation.AnimatedVisibility(
                                visible = userScrolledManually,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = if (showControls) 120.dp else 32.dp)
                            ) {
                                GlassControlSurface(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            registerInteraction()
                                            userScrolledManually = false
                                            if (activeIndex >= 0 && activeIndex < lines.size) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Navigation,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Jump to current",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = baseTextColor
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Bottom Playback Controls (Auto-hiding after 5s inactivity)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(stiffness = 400f)) { it },
                    exit = fadeOut(spring(stiffness = 400f)) + slideOutVertically(spring(stiffness = 400f)) { it }
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = columnWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        // Progress Scrubber
                        LiquidGlassSlider(
                            value = displayFraction.coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                registerInteraction()
                                isDraggingSlider = true
                                sliderDragFraction = frac
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val currentDisplayMs = if (isDraggingSlider) (sliderDragFraction * durationMs).toLong() else currentPositionMs
                            Text(formatTime(currentDisplayMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(formatTime(durationMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidGlassIconButton(
                                icon = Icons.Rounded.SkipPrevious,
                                onClick = {
                                    registerInteraction()
                                    onPrevious()
                                },
                                contentDescription = "Previous",
                                size = 48.dp
                            )

                            LiquidGlassIconButton(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                onClick = {
                                    registerInteraction()
                                    onPlayPause()
                                },
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                size = 60.dp
                            )

                            LiquidGlassIconButton(
                                icon = Icons.Rounded.SkipNext,
                                onClick = {
                                    registerInteraction()
                                    onNext()
                                },
                                contentDescription = "Next",
                                size = 48.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
