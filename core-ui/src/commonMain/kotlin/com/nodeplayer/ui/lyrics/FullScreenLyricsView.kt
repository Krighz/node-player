package com.nodeplayer.ui.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.PlatformArtworkProvider
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.player.NowPlayingTimeline
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.PaletteExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Binary-search upper-bound lookup matching `lines.indexOfLast { it.timestampMs <= positionMs }`
 * in O(log N) for sorted LRC lines.
 */
fun findActiveLyricIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var low = 0
    var high = lines.size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].timestampMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

/**
 * Full-screen synchronized lyrics view (State 2 & State 3).
 *
 * Conforms to LYR-01..LYR-06 and ARCH-01..ARCH-04:
 * - Slowly drifting radial ambient gradients from album art palette (LYR-01).
 * - Header: thumbnail, title, artist, top drag handle, and three-dots button.
 * - Active line word-by-word sweep fill with soft edge glow (LYR-02, LYR-03).
 * - Instrumental gap indicator with 3 softly pulsing rhythmic dots (LYR-04).
 * - Smooth auto-scroll with manual drag pause and "Jump to current" button (LYR-05).
 * - Tap to seek on any line (LYR-05).
 * - Clean provider attribution line ("Lyrics by <provider>") under content (LYR-06).
 * - Controls stay at bottom in State 2 (timeline, transport, bottom row with "This phone" label).
 */
@Composable
fun FullScreenLyricsView(
    track: Track?,
    lyrics: Lyrics?,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    durationMs: Long,
    isLoading: Boolean = false,
    isOnlineMode: Boolean = true,
    audioOutputName: String = "This phone",
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
    onOpenMenu: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpenNodeConnect: () -> Unit = {},
    onOpenPlaybackOptions: () -> Unit = {},
    onSearchAgain: () -> Unit = {},
    onOpenOffsetControls: () -> Unit = {},
    lyricsConfig: com.nodeplayer.model.LyricsViewConfig = com.nodeplayer.model.LyricsViewConfig(),
    onKeepScreenOn: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    positionProvider: () -> Long = { currentPositionMs }
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val defaultAccent = LocalAccentColor.current
    val currentPositionProvider by rememberUpdatedState(positionProvider)

    // Keep screen on while lyrics view is active
    DisposableEffect(Unit) {
        onKeepScreenOn(true)
        onDispose {
            onKeepScreenOn(false)
        }
    }

    // Dynamic palette resolution
    val cachedPalette = remember(track) { PaletteExtractor.getCachedPalette(track) }
    val palette = cachedPalette ?: AlbumArtPalette.DEFAULT_OBSIDIAN
    val accentTint = remember(palette, defaultAccent) {
        PaletteExtractor.ensureContrastAgainstDark(Color(palette.accent), Color(palette.darkMuted))
    }

    var userScrolledManually by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Inactivity auto-hide timer for controls (State 2 -> State 3)
    LaunchedEffect(showControls, lastInteractionTime, lyricsConfig.autoHideDelaySec) {
        if (showControls && lyricsConfig.autoHideDelaySec > 0) {
            delay(lyricsConfig.autoHideDelaySec * 1000L)
            showControls = false
        }
    }

    fun registerInteraction() {
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
    }

    // Active line detection — O(log N) binary search via derivedStateOf reading currentPositionProvider
    val lines = lyrics?.lines ?: emptyList()
    val activeIndex by remember(lines) {
        derivedStateOf {
            if (lines.isEmpty()) -1
            else findActiveLyricIndex(lines, currentPositionProvider()).coerceAtLeast(0)
        }
    }

    // Auto-scroll logic: spring smooth scroll to keep active lyric in upper third
    LaunchedEffect(activeIndex, userScrolledManually) {
        if (!userScrolledManually && activeIndex >= 0 && activeIndex < lines.size) {
            val targetScrollIndex = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetScrollIndex)
        }
    }

    // Auto-resume auto-scroll after 6s of manual scroll inactivity
    LaunchedEffect(userScrolledManually, lastInteractionTime) {
        if (userScrolledManually) {
            delay(6000L)
            userScrolledManually = false
        }
    }

    // Monitor list scrolling: restore controls when at top or when scrolling back to active line (COL-04)
    LaunchedEffect(listState, activeIndex) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (firstIndex, offset) ->
            if (firstIndex == 0 && offset == 0) {
                showControls = true
                lastInteractionTime = System.currentTimeMillis()
            } else if (activeIndex >= 0 && (firstIndex == activeIndex || firstIndex == (activeIndex - 1).coerceAtLeast(0))) {
                if (!showControls && userScrolledManually) {
                    showControls = true
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    // Tap on screen toggles / restores controls
                    showControls = !showControls
                    if (showControls) {
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        // 1. Single Backdrop Layer: Ambient Drifting Gradient
        AmbientLyricsBackdrop(
            palette = palette,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Foreground Content Layer
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columnWidth = if (maxWidth > 640.dp) 580.dp else maxWidth

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header (Always at top, persistent in State 2 & State 3)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag pill handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.40f))
                            .clickable { onClose() }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .widthIn(max = columnWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            // Rounded album thumbnail
                            var coverBitmap by remember(track) { mutableStateOf<ImageBitmap?>(null) }
                            LaunchedEffect(track) {
                                if (track != null) {
                                    coverBitmap = PlatformArtworkProvider.loadArtwork(track)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = coverBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = "Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track?.title ?: "No track playing",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track?.artist ?: "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Round translucent action button (Three-dots in State 2, Share in State 3 - COL-03)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    registerInteraction()
                                    if (showControls) {
                                        onOpenMenu()
                                    } else {
                                        onShare()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = showControls,
                                transitionSpec = {
                                    fadeIn(spring(stiffness = 400f)) togetherWith fadeOut(spring(stiffness = 400f))
                                },
                                label = "HeaderActionTransition"
                            ) { isState2 ->
                                Icon(
                                    imageVector = if (isState2) Icons.Rounded.MoreHoriz else Icons.Rounded.Share,
                                    contentDescription = if (isState2) "More Options" else "Share Lyrics",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Middle Lyrics Stage
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
                                color = accentTint,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading synchronized lyrics…",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.70f)
                            )
                        }
                    } else if (lyrics == null || (!lyrics.isSynced && lyrics.plainText.isEmpty())) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = if (!isOnlineMode) "No lyrics available offline" else "No lyrics found",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (!isOnlineMode) {
                                    "Connect to search and sync lyrics automatically."
                                } else {
                                    "We couldn't find synchronized lyrics for this song."
                                },
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            LiquidButton(
                                onClick = onSearchAgain,
                                enabled = isOnlineMode
                            ) {
                                Text(
                                    text = "Search again",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        // Synced Immersive Lyrics View — fixed contentPadding + lightweight scrim overlay (P03)
                        val scrimColor = remember(palette.darkMuted) {
                            Color(palette.darkMuted)
                        }
                        val scrimBrush = remember(scrimColor) {
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, scrimColor.copy(alpha = 0.85f))
                            )
                        }
                        val jumpPillOffsetPx by animateFloatAsState(
                            targetValue = if (showControls) 0f else with(LocalDensity.current) { 94.dp.toPx() },
                            animationSpec = spring(stiffness = 380f),
                            label = "JumpPillOffset"
                        )

                        Box(
                            modifier = Modifier
                                .widthIn(max = columnWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp)
                        ) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    top = 32.dp,
                                    bottom = 200.dp
                                ),
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
                                        positionProvider = currentPositionProvider,
                                        onSeek = { ts ->
                                            registerInteraction()
                                            onSeekTo(ts)
                                        },
                                        activeColor = Color.White,
                                        inactiveColor = Color.White.copy(alpha = 0.55f),
                                        fontSize = lyricsConfig.fontSizeSp.sp
                                    )

                                    // Instrumental gap indicator check
                                    val nextLine = lines.getOrNull(index + 1)
                                    val lineEnd = line.endTimeMs
                                    val gapDuration = if (nextLine != null && lineEnd != null) {
                                        nextLine.timestampMs - lineEnd
                                    } else {
                                        0L
                                    }

                                    if (gapDuration >= 4000L && index == activeIndex) {
                                        InstrumentalGapIndicator(
                                            color = accentTint
                                        )
                                    }
                                }

                                // Provider Attribution Line (LYR-06)
                                item {
                                    val providerName = lyrics.source.ifBlank { "LRCLIB" }
                                    Spacer(modifier = Modifier.height(28.dp))
                                    Text(
                                        text = "Lyrics by $providerName",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                }
                            }

                            // Lightweight bottom edge gradient overlay instead of offscreen DstIn layer
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(scrimBrush)
                            )

                            // Floating "Jump to current" pill
                            if (userScrolledManually) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 130.dp)
                                        .graphicsLayer { translationY = jumpPillOffsetPx }
                                ) {
                                    GlassControlSurface(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                registerInteraction()
                                                userScrolledManually = false
                                                if (activeIndex >= 0 && activeIndex < lines.size) {
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
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
                                                tint = accentTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Jump to current",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Playback Controls (State 2 visible, auto-collapsing in State 3)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(spring(stiffness = 380f)) + slideInVertically(spring(stiffness = 380f)) { it },
                    exit = fadeOut(spring(stiffness = 380f)) + slideOutVertically(spring(stiffness = 380f)) { it }
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = columnWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Timeline Scrubber
                        NowPlayingTimeline(
                            positionMs = currentPositionMs,
                            positionProvider = currentPositionProvider,
                            durationMs = durationMs,
                            accentColor = accentTint,
                            onSeek = { targetMs ->
                                registerInteraction()
                                onSeekTo(targetMs)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Large Transport Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        registerInteraction()
                                        onPrevious()
                                    }
                                    .padding(4.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        registerInteraction()
                                        onPlayPause()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        registerInteraction()
                                        onNext()
                                    }
                                    .padding(4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Bottom Row: 3 Round Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Lyrics Button (Highlighted active in State 2)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.28f))
                                    .clickable { onClose() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubble,
                                    contentDescription = "Close Lyrics",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 2. Node Connect Button
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(accentTint.copy(alpha = 0.18f))
                                    .clickable {
                                        registerInteraction()
                                        onOpenNodeConnect()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cast,
                                    contentDescription = "Node Connect",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 3. Playback Options Button
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(accentTint.copy(alpha = 0.18f))
                                    .clickable {
                                        registerInteraction()
                                        onOpenPlaybackOptions()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                    contentDescription = "Playback Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Output device label
                        Text(
                            text = audioOutputName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.70f),
                            modifier = Modifier
                                .clickable {
                                    registerInteraction()
                                    onOpenNodeConnect()
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}
