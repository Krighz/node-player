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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.PlatformArtworkProvider
import com.nodeplayer.ui.player.NowPlayingTimeline
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.PaletteExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 * - Slowly drifting radial ambient gradients from the whole album art palette (LYR-01).
 * - Header: thumbnail, title, artist, top drag handle, and three-dots button.
 * - Active line word-by-word sweep fill with soft edge glow and per-word bounce (LYR-02, LYR-03).
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
    palette: AlbumArtPalette? = null,
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
    onShareCard: ((lines: List<String>, track: Track?, palette: AlbumArtPalette) -> Unit)? = null,
    onSaveCard: ((lines: List<String>, track: Track?, palette: AlbumArtPalette) -> Unit)? = null,
    lyricsConfig: com.nodeplayer.model.LyricsViewConfig = com.nodeplayer.model.LyricsViewConfig(),
    onKeepScreenOn: (Boolean) -> Unit = {},
    hoistedDominantColor: Color? = null,
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

    // Load album artwork once so both the palette extractor and header thumbnail share it
    val coverBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        track?.id,
        track?.path
    ) {
        value = track?.let { PlatformArtworkProvider.loadArtwork(it) }
    }

    // Dynamic multi-swatch palette resolution directly from the album artwork bitmap
    val cachedPalette by produceState<AlbumArtPalette?>(
        initialValue = palette ?: PaletteExtractor.getCachedPalette(track),
        track?.id,
        track?.path,
        coverBitmap,
        palette
    ) {
        val existing = palette ?: PaletteExtractor.getCachedPalette(track)
        value = if (existing != null && !existing.isFallback) {
            existing
        } else {
            coverBitmap?.let { bmp ->
                PaletteExtractor.extractFromBitmap(bmp).also { extracted ->
                    track?.let { PaletteExtractor.cachePalette(it, extracted) }
                }
            } ?: existing ?: track?.let { PlatformArtworkProvider.extractVibrantColor(it) }?.let { color ->
                PaletteExtractor.derivePaletteFromBaseColor(color).also { derived ->
                    PaletteExtractor.cachePalette(track, derived)
                }
            }
        }
    }
    val effectivePalette = cachedPalette ?: palette ?: AlbumArtPalette.DEFAULT_OBSIDIAN
    val accentTint = remember(effectivePalette, defaultAccent) {
        PaletteExtractor.ensureContrastAgainstDark(Color(effectivePalette.accent), Color(effectivePalette.darkMuted))
    }

    var userScrolledManually by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var interactionTick by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var isFavorite by remember(track?.id) { mutableStateOf(track?.isFavorite ?: false) }

    // Lyric Card selection & generator states
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedLineIndices by remember { mutableStateOf(setOf<Int>()) }
    var showCardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(track?.id) {
        selectedLineIndices = emptySet()
        isSelectionMode = false
        showCardDialog = false
    }

    fun registerInteraction() {
        if (controlsVisible) {
            interactionTick++
        }
    }

    // Auto-collapse playback controls after 3.5 seconds when playing
    LaunchedEffect(isPlaying, controlsVisible, interactionTick) {
        if (isPlaying && controlsVisible && !isSelectionMode) {
            delay(3500L)
            controlsVisible = false
        }
    }

    val livePositionMs by produceState(
        initialValue = if (currentPositionMs > 0L) currentPositionMs else currentPositionProvider(),
        positionProvider
    ) {
        while (isActive) {
            value = currentPositionProvider()
            delay(100L)
        }
    }

    // Active line detection — O(log N) binary search via remember reading livePositionMs
    val lines = lyrics?.lines ?: emptyList()
    val activeIndex = remember(lines, livePositionMs) {
        if (lines.isEmpty()) -1
        else findActiveLyricIndex(lines, livePositionMs).coerceAtLeast(0)
    }

    fun enterSelectionMode() {
        isSelectionMode = true
        controlsVisible = false
        val currentLines = lyrics?.lines ?: emptyList()
        if (currentLines.isNotEmpty()) {
            val start = activeIndex.coerceIn(currentLines.indices)
            val end = (start + 3).coerceAtMost(currentLines.lastIndex)
            selectedLineIndices = (start..end).toSet()
        } else {
            selectedLineIndices = emptySet()
        }
    }

    // Auto-scroll logic: spring smooth scroll only when !listState.isScrollInProgress so it never fights user touch
    LaunchedEffect(activeIndex, isSelectionMode) {
        if (!isSelectionMode && !userScrolledManually && !listState.isScrollInProgress && activeIndex >= 0 && activeIndex < lines.size) {
            val targetScrollIndex = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetScrollIndex)
        }
    }

    // Auto-resume auto-scroll after 6s of manual scroll inactivity
    LaunchedEffect(userScrolledManually) {
        if (userScrolledManually) {
            delay(6000L)
            userScrolledManually = false
        }
    }

    // Monitor list scrolling: pause auto-scroll during manual drag (NEVER brings controls back up)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
            if (inProgress) {
                userScrolledManually = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(effectivePalette.darkMuted).copy(alpha = 1f))
            .pointerInput(isSelectionMode) {
                if (!isSelectionMode) {
                    detectTapGestures {
                        // Tapping anywhere on the lyrics view toggles controls
                        controlsVisible = !controlsVisible
                        if (controlsVisible) {
                            interactionTick++
                        }
                    }
                }
            }
    ) {
        // 1. Single Backdrop Layer: 100% Opaque Ambient Drifting Album Art Palette Gradient
        LyricsPaletteBackdrop(
            palette = effectivePalette,
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
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag pill handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                            .clickable { onClose() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .widthIn(max = columnWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Rounded album/video thumbnail with instant Share Card trigger (~30% bigger: 62x55dp)
                        Box(
                            modifier = Modifier
                                .size(width = 62.dp, height = 55.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable {
                                    if (isSelectionMode) {
                                        isSelectionMode = false
                                        selectedLineIndices = emptySet()
                                    } else {
                                        enterSelectionMode()
                                    }
                                },
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
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.60f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Share icon indicator on album art thumbnail
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (isSelectionMode) 0.50f else 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share Lyric Card",
                                    tint = if (isSelectionMode) Color.White else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Center: Song title above artist name (~30% bigger with bold weight)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = track?.title ?: "No track playing",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = track?.artist ?: "",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right: Two circular translucent icon buttons (~30% bigger: 48.dp, CircleShape)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Favorite Star button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        isFavorite = !isFavorite
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color(0xFFFBBF24) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Action button: In selection mode -> Close ("X"); Otherwise -> Menu or Share
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        if (isSelectionMode) {
                                            isSelectionMode = false
                                            selectedLineIndices = emptySet()
                                        } else if (controlsVisible) {
                                            onOpenMenu()
                                        } else {
                                            enterSelectionMode()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        isSelectionMode -> Icons.Rounded.Close
                                        controlsVisible -> Icons.Rounded.MoreHoriz
                                        else -> Icons.Rounded.Share
                                    },
                                    contentDescription = when {
                                        isSelectionMode -> "Cancel Selection"
                                        controlsVisible -> "More Options"
                                        else -> "Share Lyrics"
                                    },
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
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
                    } else if (isSelectionMode) {
                        // Selection Mode (matching Image 2)
                        Box(
                            modifier = Modifier
                                .widthIn(max = columnWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 20.dp)
                        ) {
                            val cardShape = remember { ContinuousRoundedRectangle(16.dp) }
                            val selectionListState = rememberLazyListState(
                                initialFirstVisibleItemIndex = (activeIndex - 1).coerceAtLeast(0)
                            )
                            LazyColumn(
                                state = selectionListState,
                                contentPadding = PaddingValues(
                                    top = 20.dp,
                                    bottom = 120.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(
                                    items = lines,
                                    key = { index, line -> "sel_${line.timestampMs}_$index" }
                                ) { index, line ->
                                    val isSelected = selectedLineIndices.contains(index)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(cardShape)
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.22f)
                                                else Color.White.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.06f),
                                                shape = cardShape
                                            )
                                            .clickable {
                                                selectedLineIndices = if (isSelected) {
                                                    selectedLineIndices - index
                                                } else {
                                                    if (selectedLineIndices.size < 6) {
                                                        selectedLineIndices + index
                                                    } else {
                                                        selectedLineIndices
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 18.dp)
                                    ) {
                                        Text(
                                            text = line.text,
                                            fontSize = 24.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                            letterSpacing = (-0.4).sp,
                                            lineHeight = 32.sp,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.70f)
                                        )
                                    }
                                }
                            }

                            // Bottom Floating Pill Bar: [ X Cancel | Share (capsule) ] (matching Image 2)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 28.dp)
                                    .clip(ContinuousCapsule)
                                    .background(Color(0xFF141418).copy(alpha = 0.94f))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), ContinuousCapsule)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 1. Cancel button
                                    Row(
                                        modifier = Modifier
                                            .clip(ContinuousCapsule)
                                            .clickable {
                                                isSelectionMode = false
                                                selectedLineIndices = emptySet()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Cancel",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Cancel",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }

                                    // 2. Share button
                                    val canShare = selectedLineIndices.isNotEmpty()
                                    Row(
                                        modifier = Modifier
                                            .clip(ContinuousCapsule)
                                            .background(if (canShare) Color.White else Color.White.copy(alpha = 0.22f))
                                            .clickable(enabled = canShare) {
                                                showCardDialog = true
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = "Share",
                                            tint = if (canShare) Color.Black else Color.White.copy(alpha = 0.55f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Share",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (canShare) Color.Black else Color.White.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .widthIn(max = columnWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp)
                        ) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    top = 28.dp,
                                    bottom = 200.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
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
                                        currentPositionMs = livePositionMs,
                                        positionProvider = currentPositionProvider,
                                        onSeek = { ts ->
                                            registerInteraction()
                                            onSeekTo(ts)
                                        },
                                        activeColor = Color.White,
                                        inactiveColor = Color.White.copy(alpha = 0.70f),
                                        fontSize = 34.sp
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

                            // Small white icon button anchored in bottom-left corner (Task 1.4)
                            if (userScrolledManually && activeIndex >= 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 20.dp, bottom = 16.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .clickable {
                                            userScrolledManually = false
                                            if (activeIndex >= 0 && activeIndex < lines.size) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDownward,
                                        contentDescription = "Jump to current",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Playback Controls (Auto-collapses after 3.5s when playing)
                AnimatedVisibility(
                    visible = controlsVisible,
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

        // 3. High-Quality Lyric Card Generator Dialog (Image 3)
        if (showCardDialog) {
            val sortedLines = remember(selectedLineIndices, lines) {
                selectedLineIndices.sorted().mapNotNull { lines.getOrNull(it)?.text }.filter { it.isNotBlank() }
            }
            if (sortedLines.isNotEmpty()) {
                LyricCardGeneratorDialog(
                    track = track,
                    lines = sortedLines,
                    palette = effectivePalette,
                    coverBitmap = coverBitmap,
                    onDismiss = { showCardDialog = false },
                    onShareImage = {
                        showCardDialog = false
                        if (onShareCard != null) {
                            onShareCard(sortedLines, track, effectivePalette)
                        } else {
                            onShare()
                        }
                    },
                    onSaveImage = {
                        if (onSaveCard != null) {
                            onSaveCard(sortedLines, track, effectivePalette)
                        }
                    },
                    onCopyText = {
                        // Formatted text
                    }
                )
            }
        }
    }
}
