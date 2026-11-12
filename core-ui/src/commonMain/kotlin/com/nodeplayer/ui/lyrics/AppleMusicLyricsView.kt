package com.nodeplayer.ui.lyrics

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.player.ArtworkMeshBackdrop
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 3-Dot pulsing instrumental countdown indicator for musical interludes exceeding 4.0 seconds (D-12).
 *
 * Parameters per D-12:
 * - GAP_DOTS = 3
 * - GAP_DOT_SIZE = 13.dp
 * - GAP_DOT_GAP = 5.dp
 * - Sequentially lights up each dot according to break elapsed time:
 *   val through = ((clockMs - breakStartMs).toFloat() / breakDurationMs).coerceIn(0f, 1f)
 *   val lit = (through * 3 - dot).coerceIn(0f, 1f)
 *   Color.White.copy(alpha = 0.25f + 0.75f * lit)
 */
@Composable
fun InstrumentalBreakCountdown(
    clockMs: Long,
    breakStartMs: Long,
    breakEndMs: Long,
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White
) {
    val breakDurationMs = (breakEndMs - breakStartMs).coerceAtLeast(1L)
    val through = ((clockMs - breakStartMs).toFloat() / breakDurationMs.toFloat()).coerceIn(0f, 1f)

    val dotSize = 13.dp
    val dotGap = 5.dp
    val totalWidth = (dotSize * 3) + (dotGap * 2)

    Canvas(
        modifier = modifier
            .padding(vertical = 14.dp)
            .size(width = totalWidth, height = dotSize)
    ) {
        val radiusPx = (dotSize.toPx()) / 2f
        val gapPx = dotGap.toPx()

        for (dot in 0 until 3) {
            val lit = (through * 3f - dot.toFloat()).coerceIn(0f, 1f)
            val alpha = (0.25f + 0.75f * lit).coerceIn(0f, 1f)
            val cx = radiusPx + dot * (radiusPx * 2f + gapPx)
            val cy = size.height / 2f

            drawCircle(
                color = dotColor.copy(alpha = alpha),
                radius = radiusPx,
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * Apple Music-style fullscreen glass lyrics view.
 *
 * Conforms to D-11, D-12, D-13, and D-14:
 * - Overhauled with BitChord 1-to-1 lyrics engine.
 * - Mounts [ArtworkMeshBackdrop] as the hardware-accelerated ambient background.
 * - Word-by-word active glow sweep with dimmed inactive lines.
 * - Duet call-and-response alignment (Start vs End).
 * - Backing vocal subtext with independent sweep.
 * - 3-Dot pulsing countdown during instrumental breaks > 4.0s.
 * - Spring-damped auto-centering with drag-to-pause logic.
 * - Strictly preserves public signature and default parameter overloads per D-14.
 */
@Composable
fun AppleMusicLyricsView(
    track: Track?,
    lyrics: Lyrics?,
    currentPositionMs: Long,
    isLoading: Boolean,
    isOnlineMode: Boolean,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
    onSearchAgain: () -> Unit = {},
    onOpenOffsetControls: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val accentColor = LocalAccentColor.current
    val baseTextColor = MaterialTheme.colorScheme.onBackground

    var userScrolledManually by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(0L) }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrolledManually = true
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // Active line detection: binary search or indexOfLast
    val lines = lyrics?.lines ?: emptyList()
    val activeIndex by remember(lines, currentPositionMs) {
        derivedStateOf {
            if (lines.isEmpty()) -1
            else lines.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)
        }
    }

    // Auto-scroll loop with spring positioning, pausing for ~4s upon user touch
    LaunchedEffect(userScrolledManually, lastInteractionTime) {
        if (userScrolledManually) {
            delay(4000L)
            userScrolledManually = false
        }
    }

    LaunchedEffect(activeIndex, userScrolledManually) {
        if (!userScrolledManually && activeIndex >= 0 && activeIndex < lines.size) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(
                index = targetScroll,
                scrollOffset = 0
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val isWide = maxWidth >= 600.dp
        val columnWidth = if (isWide) maxWidth * 0.60f else maxWidth

        // 1. Zero-allocation hardware GPU mesh backdrop per D-05 & D-09
        ArtworkMeshBackdrop(
            artworkUrl = track?.path ?: track?.id?.toString(),
            fallback = accentColor.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize()
        )

        // Subtle dark translucent scrim for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Close Lyrics",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track?.title ?: "No track playing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenOffsetControls, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Timing Offset",
                            tint = if ((lyrics?.offsetMs ?: 0L) != 0L) accentColor else Color.White.copy(alpha = 0.70f)
                        )
                    }
                }
            }

            // Center Stage: Lyrics or Empty/Loading State
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
                            text = "Looking for lyrics…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.70f))
                        )
                    }
                } else if (lyrics == null || (!lyrics.isSynced && lyrics.plainText.isEmpty())) {
                    // Empty / Not Found State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = if (!isOnlineMode) "No lyrics available offline" else "No lyrics",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (!isOnlineMode) {
                                "Lyrics for this track aren't saved on this device. Turn on online mode, then search again."
                            } else {
                                "We couldn't find lyrics for this track. Check that the title and artist are correct, or search again."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.65f),
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
                                    color = Color.White
                                )
                            )
                        }
                    }
                } else if (!lyrics.isSynced && lyrics.plainText.isNotEmpty()) {
                    // Plain Text Lyrics Fallback
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
                                        color = Color.White.copy(alpha = 0.50f)
                                    ),
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                            item {
                                Text(
                                    text = lyrics.plainText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 18.sp,
                                        lineHeight = 28.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Synced Lyrics Apple Music View
                    Box(
                        modifier = Modifier
                            .width(columnWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 80.dp, bottom = 140.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = lines,
                                key = { index, line -> "${line.timestampMs}_$index" }
                            ) { index, line ->
                                WordByWordKaraokeText(
                                    line = line,
                                    index = index,
                                    activeIndex = activeIndex,
                                    currentPositionMs = currentPositionMs,
                                    onSeek = onSeekTo,
                                    activeColor = Color.White,
                                    inactiveColor = Color(0xFFC0C0C0),
                                    accentSweepColor = accentColor,
                                    fontSize = 34.sp
                                )

                                // Check for instrumental interlude break > 4.0s (D-12)
                                val nextLine = lines.getOrNull(index + 1)
                                val lineEnd = line.endTimeMs ?: (line.timestampMs + 3500L)
                                val gapDuration = if (nextLine != null) {
                                    nextLine.timestampMs - lineEnd
                                } else {
                                    0L
                                }

                                if (gapDuration > 4000L && index == activeIndex && currentPositionMs >= lineEnd && currentPositionMs < nextLine!!.timestampMs) {
                                    InstrumentalBreakCountdown(
                                        clockMs = currentPositionMs,
                                        breakStartMs = lineEnd,
                                        breakEndMs = nextLine.timestampMs,
                                        dotColor = Color.White
                                    )
                                }
                            }
                        }

                        // Floating "Jump to current" pill
                        androidx.compose.animation.AnimatedVisibility(
                            visible = userScrolledManually,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                        ) {
                            GlassControlSurface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        userScrolledManually = false
                                        if (activeIndex >= 0 && activeIndex < lines.size) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
