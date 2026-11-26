package com.nodeplayer.ui.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
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
import kotlinx.coroutines.Job
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

private val HeartShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(width / 2f, height * 0.84f)
            // Left lobe
            cubicTo(
                width * 0.10f, height * 0.60f,
                0f, height * 0.35f,
                width * 0.22f, height * 0.10f
            )
            cubicTo(
                width * 0.40f, 0f,
                width / 2f, height * 0.16f,
                width / 2f, height * 0.26f
            )
            // Right lobe
            cubicTo(
                width / 2f, height * 0.16f,
                width * 0.60f, 0f,
                width * 0.78f, height * 0.10f
            )
            cubicTo(
                width, height * 0.35f,
                width * 0.90f, height * 0.60f,
                width / 2f, height * 0.84f
            )
            close()
        }
        return Outline.Generic(path)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}"
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
    audioOutputName: String = "This Device",
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
    lyricsSettings: com.nodeplayer.model.LyricsSettings = com.nodeplayer.model.LyricsSettings(),
    onUpdateLyricsSettings: (com.nodeplayer.model.LyricsSettings) -> Unit = {},
    onKeepScreenOn: (Boolean) -> Unit = {},
    hoistedDominantColor: Color? = null,
    modifier: Modifier = Modifier,
    positionProvider: () -> Long = { currentPositionMs },
    isShuffle: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    repeatMode: Int = 0,
    onToggleRepeat: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val defaultAccent = LocalAccentColor.current
    val currentPositionProvider by rememberUpdatedState(positionProvider)

    var localLyricsSettings by remember(lyricsSettings) { mutableStateOf(lyricsSettings) }
    var showLyricsSettingsSheet by remember { mutableStateOf(false) }
    var layoutSeed by remember { androidx.compose.runtime.mutableLongStateOf(42L) }

    val fontResolver = LocalLyricsFontResolver.current
    val activeLyricsFontFamily = remember(localLyricsSettings.fontChoice, fontResolver) {
        fontResolver(localLyricsSettings.fontChoice)
    }

    val handleUpdateSettings: (com.nodeplayer.model.LyricsSettings) -> Unit = { newSettings ->
        val providerChanged = newSettings.preferredProvider != localLyricsSettings.preferredProvider
        localLyricsSettings = newSettings
        onUpdateLyricsSettings(newSettings)
        if (providerChanged) {
            onSearchAgain()
        }
    }

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

    // Dynamic Header Auto-Hide & Gesture States
    var headerVisible by remember { mutableStateOf(!isPlaying) }
    var isHeaderLockedOpen by remember { mutableStateOf(false) }
    var headerHideJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleHeaderHide(delayMs: Long) {
        headerHideJob?.cancel()
        if (!isHeaderLockedOpen && isPlaying) {
            headerHideJob = coroutineScope.launch {
                delay(delayMs)
                if (!isHeaderLockedOpen && isPlaying) {
                    headerVisible = false
                }
            }
        }
    }

    fun onUserScreenInteraction(isTwoFingerDown: Boolean = false) {
        headerVisible = true
        if (!isHeaderLockedOpen && isPlaying) {
            scheduleHeaderHide(if (isTwoFingerDown) 3500L else 2000L)
        }
    }

    fun onHeaderClicked() {
        isHeaderLockedOpen = true
        headerVisible = true
        headerHideJob?.cancel()
    }

    fun onHeaderDismissedOrCut() {
        isHeaderLockedOpen = false
        if (isPlaying) {
            scheduleHeaderHide(3500L)
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && !isHeaderLockedOpen) {
            scheduleHeaderHide(2000L)
        } else if (!isPlaying) {
            headerHideJob?.cancel()
            headerVisible = true
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
            .background(Color.Black)
            .pointerInput(isSelectionMode, isPlaying) {
                if (!isSelectionMode) {
                    awaitPointerEventScope {
                        var initialY1 = 0f
                        var initialY2 = 0f
                        var isTwoFingerTracking = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val changes = event.changes

                            if (changes.size >= 2) {
                                val p1 = changes[0]
                                val p2 = changes[1]
                                if (!isTwoFingerTracking) {
                                    initialY1 = p1.position.y
                                    initialY2 = p2.position.y
                                    isTwoFingerTracking = true
                                } else {
                                    val dy1 = p1.position.y - initialY1
                                    val dy2 = p2.position.y - initialY2
                                    if (dy1 > 28f && dy2 > 28f) {
                                        onUserScreenInteraction(isTwoFingerDown = true)
                                        initialY1 = p1.position.y
                                        initialY2 = p2.position.y
                                    }
                                }
                            } else {
                                isTwoFingerTracking = false
                                if (changes.size == 1) {
                                    val p = changes[0]
                                    if (p.changedToDown() || (p.pressed && (p.position - p.previousPosition).getDistanceSquared() > 8f)) {
                                        onUserScreenInteraction(isTwoFingerDown = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalLyricsFontFamily provides activeLyricsFontFamily) {
                val parentMaxHeight = maxHeight
                val parentMaxWidth = maxWidth
            val isLandscape = parentMaxWidth > parentMaxHeight && parentMaxWidth >= 600.dp
            val columnWidth = if (parentMaxWidth > 640.dp) 580.dp else parentMaxWidth

            // Landscape Hero Auto-Collapse States (cinematic kinetic mode)
            var landscapeHeroVisible by remember { mutableStateOf(!isPlaying) }
            var landscapeHeroTick by remember { mutableStateOf(0) }

            fun registerLandscapeHeroInteraction() {
                landscapeHeroVisible = true
                landscapeHeroTick += 1
                registerInteraction()
            }

            LaunchedEffect(isPlaying, landscapeHeroVisible, landscapeHeroTick, isLandscape, localLyricsSettings.style) {
                if (isLandscape && isPlaying && landscapeHeroVisible && localLyricsSettings.style == com.nodeplayer.model.LyricsStyle.CINEMATIC_KINETIC) {
                    delay(4500L)
                    landscapeHeroVisible = false
                } else if (!isPlaying) {
                    landscapeHeroVisible = true
                }
            }

            val heroCollapseProgress by animateFloatAsState(
                targetValue = if (!isLandscape || landscapeHeroVisible || !isPlaying || localLyricsSettings.style != com.nodeplayer.model.LyricsStyle.CINEMATIC_KINETIC) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "landscapeHeroCollapse"
            )

            val currentArtShape: Shape = when (localLyricsSettings.albumArtShape) {
                com.nodeplayer.model.AlbumArtShape.SQUIRCLE -> ContinuousRoundedRectangle(24.dp)
                com.nodeplayer.model.AlbumArtShape.CIRCLE -> CircleShape
                com.nodeplayer.model.AlbumArtShape.HEART -> HeartShape
            }

            // 1. Edge-to-edge Backdrop Layer
            if (localLyricsSettings.style == com.nodeplayer.model.LyricsStyle.CLASSIC_FULLSCREEN) {
                LyricsPaletteBackdrop(
                    palette = effectivePalette,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (localLyricsSettings.style == com.nodeplayer.model.LyricsStyle.CINEMATIC_KINETIC && isLandscape) {
                // In landscape: edge-to-edge cinematic starfield and radial glow covering whole display!
                CinematicKineticLyricsView(
                    lyrics = lyrics,
                    isPlaying = isPlaying,
                    settings = localLyricsSettings,
                    layoutSeed = layoutSeed,
                    modifier = Modifier.fillMaxSize(),
                    onSeekTo = { ts ->
                        registerInteraction()
                        onSeekTo(ts)
                    },
                    positionProvider = currentPositionProvider,
                    isLandscape = true
                )
            }

            if (isLandscape) {
                // Two-Column Landscape Full-Screen Lyrics View (matching user's hand-drawn specification)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (localLyricsSettings.hideStatusBar) Modifier
                            else Modifier.statusBarsPadding()
                        )
                        .navigationBarsPadding()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                registerLandscapeHeroInteraction()
                            }
                        }
                ) {
                    // Main Row: Left Column (Art + Info + Slider + Controls) | Right Column (Lyrics)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = if (localLyricsSettings.hideStatusBar) 28.dp else 20.dp,
                                end = if (localLyricsSettings.hideStatusBar) 28.dp else 20.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LEFT COLUMN: Album Art + Song Name + Length Slider + Complete Media Controls
                        Column(
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    val p = heroCollapseProgress
                                    scaleX = 0.05f + 0.95f * p
                                    scaleY = 0.05f + 0.95f * p
                                    translationX = -(1f - p) * 160.dp.toPx()
                                    alpha = p * p
                                    transformOrigin = TransformOrigin(0f, 0.5f) // Animates dynamically into/out-of the left camera cutout edge!
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val artSize = (parentMaxHeight * 0.38f).coerceIn(110.dp, 160.dp)
                            Box(
                                modifier = Modifier
                                    .size(artSize)
                                    .clip(currentArtShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.18f), currentArtShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = coverBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = track?.title ?: "Album Artwork",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Album,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.50f),
                                        modifier = Modifier.size(artSize * 0.45f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Song Name & Artist Name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f, fill = false),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = track?.title?.ifBlank { "Unknown Title" } ?: "Unknown Title",
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = track?.artist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Length of the Song: Progress Timeline Slider
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                val safeDuration = durationMs.coerceAtLeast(1L)
                                val progressFraction = (livePositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

                                Slider(
                                    value = progressFraction,
                                    onValueChange = { frac ->
                                        registerLandscapeHeroInteraction()
                                        val targetMs = (frac * safeDuration).toLong()
                                        onSeekTo(targetMs)
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = accentTint,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(livePositionMs),
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.60f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = formatTime(durationMs),
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.60f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Transport Controls: [Shuffle] [SkipPrevious] [Play/Pause] [SkipNext] [Repeat]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Shuffle Button (Active: White background + Black icon)
                                IconButton(
                                    onClick = {
                                        registerLandscapeHeroInteraction()
                                        onToggleShuffle()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isShuffle) Color.White else Color.White.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (isShuffle) Color.Black else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // 2. Previous Track
                                IconButton(
                                    onClick = {
                                        registerLandscapeHeroInteraction()
                                        onPrevious()
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous Track",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 3. Play / Pause Button (White background + Black icon)
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable {
                                            registerLandscapeHeroInteraction()
                                            onPlayPause()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // 4. Next Track
                                IconButton(
                                    onClick = {
                                        registerLandscapeHeroInteraction()
                                        onNext()
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "Next Track",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 5. Repeat Button (Active: White background + Black icon)
                                val isRepeatActive = repeatMode != 0
                                IconButton(
                                    onClick = {
                                        registerLandscapeHeroInteraction()
                                        onToggleRepeat()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isRepeatActive) Color.White else Color.White.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                        contentDescription = "Repeat",
                                        tint = if (isRepeatActive) Color.Black else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // RIGHT COLUMN: Synchronized Lyrics Pane
                        if (localLyricsSettings.style == com.nodeplayer.model.LyricsStyle.CINEMATIC_KINETIC) {
                            // In Cinematic view: NO ENCLOSING BOX!
                            // Lyrics float freely across the cinematic starfield background spreading to edges!
                            Box(
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                                } else if (lyrics == null || (!lyrics.isSynced && lyrics.plainText.isEmpty())) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = if (!isOnlineMode) "No lyrics available offline" else "No lyrics found",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (!isOnlineMode) "Connect to search and sync lyrics automatically." else "We couldn't find synchronized lyrics for this song.",
                                            fontSize = 12.5.sp,
                                            color = Color.White.copy(alpha = 0.65f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        LiquidButton(onClick = onSearchAgain, enabled = isOnlineMode) {
                                            Text("Search again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        } else {
                            // In Classic view: Enclosed in an elegant glass box!
                            Box(
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Loading synchronized lyrics…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.70f))
                                    }
                                } else if (lyrics == null || (!lyrics.isSynced && lyrics.plainText.isEmpty())) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = if (!isOnlineMode) "No lyrics available offline" else "No lyrics found",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (!isOnlineMode) "Connect to search and sync lyrics automatically." else "We couldn't find synchronized lyrics for this song.",
                                            fontSize = 12.5.sp,
                                            color = Color.White.copy(alpha = 0.65f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        LiquidButton(onClick = onSearchAgain, enabled = isOnlineMode) {
                                            Text("Search again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        }
                                    }
                                } else {
                                    SelectionContainer {
                                        LazyColumn(
                                            state = listState,
                                            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                                            verticalArrangement = Arrangement.spacedBy((16f * localLyricsSettings.commonZoom).dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            itemsIndexed(
                                                items = lines,
                                                key = { index, line -> "landscape_${line.timestampMs}_$index" }
                                            ) { index, line ->
                                                WordByWordKaraokeText(
                                                    line = line,
                                                    index = index,
                                                    activeIndex = activeIndex,
                                                    currentPositionMs = livePositionMs,
                                                    positionProvider = currentPositionProvider,
                                                    onSeek = { ts ->
                                                        registerLandscapeHeroInteraction()
                                                        onSeekTo(ts)
                                                    },
                                                    activeColor = Color(localLyricsSettings.baseColor.colorArgb),
                                                    inactiveColor = Color(localLyricsSettings.baseColor.colorArgb).copy(alpha = localLyricsSettings.inactiveOpacity),
                                                    fontSize = (26f * localLyricsSettings.commonZoom * localLyricsSettings.fontSizeScale).sp,
                                                    glowIntensity = localLyricsSettings.glowIntensity,
                                                    glowColor = Color(localLyricsSettings.palette.glowColorArgb),
                                                    isBold = localLyricsSettings.isBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Floating Top-Left Close Icon
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = if (localLyricsSettings.hideStatusBar) 20.dp else 16.dp,
                                top = if (localLyricsSettings.hideStatusBar) 14.dp else 12.dp
                            )
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Floating Left-Center Expand Button (Appears when Hero controls auto-collapse in Cinematic Kinetic mode)
                    if (localLyricsSettings.style == com.nodeplayer.model.LyricsStyle.CINEMATIC_KINETIC && heroCollapseProgress < 0.85f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .graphicsLayer {
                                    alpha = ((1f - heroCollapseProgress) / 0.85f).coerceIn(0f, 1f)
                                }
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                                .clickable { registerLandscapeHeroInteraction() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Show controls",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 3. Floating Top-Right Three-Dot Menu Icon
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                end = if (localLyricsSettings.hideStatusBar) 20.dp else 16.dp,
                                top = if (localLyricsSettings.hideStatusBar) 14.dp else 12.dp
                            )
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { showLyricsSettingsSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Lyrics Settings & Album Art Shapes",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

            } else {
            // Top Header with Smooth Animated Visibility (Auto-Hides during playback)
            AnimatedVisibility(
                visible = headerVisible && localLyricsSettings.showTopBar,
                enter = fadeIn(spring(stiffness = 500f)) + slideInVertically(spring(stiffness = 500f)) { -it },
                exit = fadeOut(spring(stiffness = 500f)) + slideOutVertically(spring(stiffness = 500f)) { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
            ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 8.dp, bottom = 4.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                onHeaderClicked()
                            },
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
                                        onHeaderClicked()
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

                            // Right: Circular translucent icon buttons: [ ▲ Collapse ] [ ⭐ Star ] [ ⋮ Unified Menu ]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Collapse Header button
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            onHeaderDismissedOrCut()
                                            headerVisible = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Collapse Header",
                                        tint = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Favorite Star button (Intact & Spared per user instruction)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .clickable {
                                            onHeaderClicked()
                                            isFavorite = !isFavorite
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFavorite) Color(0xFFFBBF24) else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Option B: Unified Overflow Menu Button (Three-Dot ⋮)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .clickable {
                                            onHeaderClicked()
                                            if (isSelectionMode) {
                                                isSelectionMode = false
                                                selectedLineIndices = emptySet()
                                            } else {
                                                showLyricsSettingsSheet = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelectionMode) Icons.Rounded.Close else Icons.Rounded.MoreVert,
                                        contentDescription = if (isSelectionMode) "Cancel Selection" else "Lyrics Settings & Menu",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

            if (!localLyricsSettings.showTopBar) {
                // Floating Restore Pill when Header is Collapsed
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 16.dp, top = 8.dp)
                        .zIndex(2f)
                        .clip(ContinuousCapsule)
                        .background(Color(0xDD18181E))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), ContinuousCapsule)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    handleUpdateSettings(localLyricsSettings.copy(showTopBar = true))
                                    headerVisible = true
                                    onHeaderClicked()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Show Header",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onHeaderClicked()
                                    showLyricsSettingsSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Lyrics Settings",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Middle Lyrics Stage
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f),
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
                                    top = 88.dp,
                                    bottom = 140.dp
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
                    } else if (localLyricsSettings.style == com.nodeplayer.model.LyricsStyle.CINEMATIC_KINETIC) {
                        CinematicKineticLyricsView(
                            lyrics = lyrics,
                            isPlaying = isPlaying,
                            settings = localLyricsSettings,
                            layoutSeed = layoutSeed,
                            modifier = Modifier.fillMaxSize(),
                            onSeekTo = { ts ->
                                registerInteraction()
                                onSeekTo(ts)
                            },
                            positionProvider = currentPositionProvider
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .widthIn(max = columnWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp)
                        ) {
                            SelectionContainer {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(
                                        top = 88.dp,
                                        bottom = 220.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy((20f * localLyricsSettings.commonZoom).dp),
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
                                            activeColor = Color(localLyricsSettings.baseColor.colorArgb),
                                            inactiveColor = Color(localLyricsSettings.baseColor.colorArgb).copy(alpha = localLyricsSettings.inactiveOpacity),
                                            fontSize = (34f * localLyricsSettings.commonZoom * localLyricsSettings.fontSizeScale).sp,
                                            glowIntensity = localLyricsSettings.glowIntensity,
                                            glowColor = Color(localLyricsSettings.palette.glowColorArgb),
                                            isBold = localLyricsSettings.isBold
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
                    exit = fadeOut(spring(stiffness = 380f)) + slideOutVertically(spring(stiffness = 380f)) { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f)
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = columnWidth)
                            .fillMaxWidth()
                            .navigationBarsPadding()
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
                    fontFamily = activeLyricsFontFamily,
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

        // 4. Frosted-Glass Lyrics Style & Settings Bottom Sheet (Option B: Unified Overflow)
        if (showLyricsSettingsSheet) {
            LyricsSettingsBottomSheet(
                settings = localLyricsSettings,
                onUpdateSettings = handleUpdateSettings,
                onDismissRequest = {
                    showLyricsSettingsSheet = false
                    onHeaderDismissedOrCut()
                },
                onShuffleLayout = {
                    layoutSeed = kotlin.random.Random.nextLong() and 0x7FFFFFFF
                },
                onShare = onShare,
                onOpenOffsetControls = onOpenOffsetControls,
                onSearchAgain = onSearchAgain,
                currentLyrics = lyrics,
                onToggleSelectionMode = {
                    isSelectionMode = true
                    selectedLineIndices = emptySet()
                }
            )
        }
    }
}
