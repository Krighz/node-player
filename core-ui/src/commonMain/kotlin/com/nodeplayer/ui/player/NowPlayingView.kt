package com.nodeplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.nodeplayer.ui.theme.MotionTokens
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import com.nodeplayer.ui.components.SongSectionMarkersBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.ui.components.ArtworkCache
import com.nodeplayer.ui.components.PlatformArtworkProvider
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.Lyrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.Track
import com.nodeplayer.model.TrackExtendedInfo
import com.nodeplayer.ui.lyrics.findActiveLyricIndex
import com.nodeplayer.ui.theme.PaletteExtractor
import com.nodeplayer.ui.theme.PaletteExtractor.toComposeColor
import kotlin.math.abs

fun findActiveLyricText(lines: List<LyricsLine>, positionMs: Long): String? {
    val idx = findActiveLyricIndex(lines, positionMs)
    return if (idx in lines.indices) lines[idx].text else null
}

fun findActiveLyricLine(lines: List<LyricsLine>, positionMs: Long): LyricsLine? {
    val idx = findActiveLyricIndex(lines, positionMs)
    return if (idx in lines.indices) lines[idx] else null
}

fun calculateLyricLineCharsSungProgress(line: LyricsLine, currentPositionMs: Long): Float {
    if (currentPositionMs < line.timestampMs) return 0f
    val words = line.words
    if (words.isEmpty()) {
        val end = line.endTimeMs ?: (line.timestampMs + 3500L)
        val duration = (end - line.timestampMs).coerceAtLeast(1L)
        if (currentPositionMs >= end) return 1f
        return ((currentPositionMs - line.timestampMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    val totalChars = words.sumOf { it.text.length }.coerceAtLeast(1)
    var sungChars = 0f
    for (word in words) {
        sungChars += word.charsSungAt(currentPositionMs)
    }
    return (sungChars / totalChars.toFloat()).coerceIn(0f, 1f)
}

/**
 * State 1: Now Playing Screen (full-screen, edge-to-edge, matching reference `nowplaying-1.png`).
 *
 * Visual hierarchy:
 * - Top drag handle & "Playing from <source>" context label.
 * - Huge album art bleeding smoothly into the gradient backdrop behind all content.
 * - Title row at ~62% screen height with song title, tinted artist, and round translucent three-dots button.
 * - Single-line lyric preview line directly above the timeline (opens State 2 on tap).
 * - Custom NowPlayingTimeline with elapsed and remaining minus time.
 * - Large transport glyphs (Previous, large Play/Pause, Next) with zero volume bar on screen.
 * - Bottom row: 3 round tinted buttons (Lyrics, Node Connect, Playback options) + "This phone" label.
 */
@Composable
fun NowPlayingView(
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    durationMs: Long,
    queue: List<Track> = emptyList(),
    currentQueueIndex: Int = 0,
    lrcContent: String? = null,
    audioOutputName: String = "This Device",
    sourceLabel: String = "Music videos",
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenNodeConnect: () -> Unit,
    onOpenPlaybackOptions: () -> Unit,
    onOpenThreeDotsMenu: () -> Unit,
    onClose: () -> Unit,
    videoContent: (@Composable (Modifier) -> Unit)? = null,
    palette: AlbumArtPalette? = null,
    onPaletteChanged: ((AlbumArtPalette) -> Unit)? = null,
    onDominantColorExtracted: ((Color) -> Unit)? = null,
    volume: Float = 0.75f,
    onVolumeChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    positionProvider: () -> Long = { positionMs },
    onDragDelta: ((Float) -> Unit)? = null,
    onDragEnd: ((Float) -> Unit)? = null,
    isVideoActive: Boolean = false,
    isVideoAvailable: Boolean = false,
    hasRenderedFirstFrame: Boolean = false,
    currentHeight: Int? = null,
    availableHeights: List<Int> = emptyList(),
    videoAspect: Float = 16f / 9f,
    onToggleVideo: ((Boolean) -> Unit)? = null,
    onSelectQuality: ((Int?) -> Unit)? = null,
    onOpenFullscreenCinema: (() -> Unit)? = null,
    artZoomScale: Float = 1.00f,
    artVerticalOffsetDp: Int = 0,
    extendedInfo: TrackExtendedInfo? = null,
    onPlayTrack: ((Track) -> Unit)? = null,
    onArtistClick: ((String, String?) -> Unit)? = null,
    onTrackOptionsClick: ((Track) -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    if (currentTrack == null) return

    val coverBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        currentTrack.id,
        currentTrack.path
    ) {
        value = PlatformArtworkProvider.loadArtwork(currentTrack, ArtworkCache.HERO_TARGET_SIZE_PX)
    }

    val cachedPalette by produceState<AlbumArtPalette?>(
        initialValue = palette ?: PaletteExtractor.getCachedPalette(currentTrack),
        currentTrack.id,
        currentTrack.path,
        coverBitmap,
        palette
    ) {
        val existing = palette ?: PaletteExtractor.getCachedPalette(currentTrack)
        value = if (existing != null && !existing.isFallback) {
            existing
        } else {
            coverBitmap?.let { bmp ->
                PaletteExtractor.extractFromBitmap(bmp).also { extracted ->
                    PaletteExtractor.cachePalette(currentTrack, extracted)
                    onPaletteChanged?.invoke(extracted)
                }
            } ?: existing ?: PlatformArtworkProvider.extractVibrantColor(currentTrack)?.let { color ->
                PaletteExtractor.derivePaletteFromBaseColor(color).also { derived ->
                    PaletteExtractor.cachePalette(currentTrack, derived)
                    onPaletteChanged?.invoke(derived)
                }
            }
        }
    }
    val rawVibrant = cachedPalette?.vibrant?.toComposeColor() ?: Color(0xFF1E293B)
    val rawDominant = cachedPalette?.dominant?.toComposeColor() ?: Color(0xFF0F172A)
    val rawDarkMuted = cachedPalette?.darkMuted?.toComposeColor() ?: Color(0xFF090A0F)
    val rawAccent = cachedPalette?.accent?.toComposeColor() ?: rawVibrant

    val animatedVibrant by animateColorAsState(
        targetValue = rawVibrant,
        animationSpec = spring(stiffness = 180f, dampingRatio = 0.92f),
        label = "VibrantColor"
    )
    val animatedDominant by animateColorAsState(
        targetValue = rawDominant,
        animationSpec = spring(stiffness = 180f, dampingRatio = 0.92f),
        label = "DominantColor"
    )
    val animatedDarkMuted by animateColorAsState(
        targetValue = rawDarkMuted,
        animationSpec = spring(stiffness = 180f, dampingRatio = 0.92f),
        label = "DarkMutedColor"
    )
    val animatedAccent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = spring(stiffness = 180f, dampingRatio = 0.92f),
        label = "AccentColor"
    )

    val accentTint = PaletteExtractor.ensureContrastAgainstDark(animatedVibrant, animatedDarkMuted)

    LaunchedEffect(animatedVibrant) {
        onDominantColorExtracted?.invoke(animatedVibrant)
    }

    val isMusicVideo = remember(currentTrack.id, currentTrack.mimeType, currentTrack.formatDescription, sourceLabel, isVideoActive) {
        isVideoActive ||
        currentTrack.mimeType.contains("video", ignoreCase = true) ||
        currentTrack.formatDescription.contains("video", ignoreCase = true) ||
        sourceLabel.contains("video", ignoreCase = true) ||
        currentTrack.path.contains("video", ignoreCase = true) ||
        currentTrack.genre?.contains("video", ignoreCase = true) == true
    }

    val heroAspectRatio by animateFloatAsState(
        targetValue = if (isVideoActive) videoAspect else (4f / 3f),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "HeroAspectMorph"
    )

    var localFavorite by remember(currentTrack.id, isFavorite) { mutableStateOf(isFavorite || currentTrack.isFavorite) }
    val effectiveFavorite = onToggleFavorite?.let { isFavorite } ?: localFavorite
    var internalVolume by remember { mutableFloatStateOf(volume) }
    val effectiveVolume = onVolumeChange?.let { volume } ?: internalVolume

    val lyrics = remember(lrcContent, currentTrack.id) {
        if (!lrcContent.isNullOrBlank()) LrcParser.parse(lrcContent, currentTrack.id) else Lyrics(trackId = currentTrack.id)
    }

    val ambientGlowColor = remember(animatedVibrant, animatedAccent, animatedDominant, rawVibrant, rawAccent, rawDominant) {
        val vibrantLuma = 0.2126f * rawVibrant.red + 0.7152f * rawVibrant.green + 0.0722f * rawVibrant.blue
        val accentLuma = 0.2126f * rawAccent.red + 0.7152f * rawAccent.green + 0.0722f * rawAccent.blue
        if (rawVibrant != Color.Unspecified && vibrantLuma > 0.03f) {
            animatedVibrant
        } else if (rawAccent != Color.Unspecified && accentLuma > 0.03f) {
            animatedAccent
        } else {
            animatedDominant
        }
    }

    val bottomScrimBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.50f to Color.Black.copy(alpha = 0.20f),
            1.0f to Color.Black.copy(alpha = 0.55f)
        )
    }

    val gradientBlurAlphaBrush = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.40f to Color.Transparent,
                0.58f to Color.Black.copy(alpha = 0.40f),
                0.74f to Color.Black.copy(alpha = 0.85f),
                0.86f to Color.Black,
                1.00f to Color.Black
            )
        )
    }

    val heroArtworkAlphaBrush = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Black,
                0.50f to Color.Black,
                0.68f to Color.Black.copy(alpha = 0.88f),
                0.82f to Color.Black.copy(alpha = 0.45f),
                0.92f to Color.Black.copy(alpha = 0.10f),
                0.98f to Color.Transparent,
                1.00f to Color.Transparent
            )
        )
    }

    val textContrastScrimBrush = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.40f to Color.Transparent,
                0.55f to Color.Black.copy(alpha = 0.16f),
                0.70f to Color.Black.copy(alpha = 0.38f),
                0.84f to Color.Black.copy(alpha = 0.58f),
                0.94f to Color.Black.copy(alpha = 0.20f),
                1.00f to Color.Transparent
            )
        )
    }

    val topStatusBarScrimBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.70f),
            0.45f to Color.Black.copy(alpha = 0.30f),
            1.0f to Color.Transparent
        )
    }

    val lazyListState = rememberLazyListState()
    val nestedScrollConnection = remember(onDragDelta, onDragEnd) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0 && available.y > 0) {
                    if (onDragDelta != null) {
                        onDragDelta.invoke(available.y)
                        return available
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0 && available.y > 800f) {
                    if (onDragEnd != null) {
                        onDragEnd.invoke(available.y)
                    } else {
                        onClose()
                    }
                    return available
                }
                return super.onPreFling(available)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        val screenHeight = maxHeight

        // Layer 1: Replicated Blurred Artwork Backdrop in the bottom portion (expanded scale so vibrant accents radiate downward)
        coverBitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                        alpha = 0.70f
                    }
                    .blur(90.dp)
            )
        }

        // Layer 2: Radiant Vibrant Ambient Mesh Glow centered at ~68% behind playback controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val radialBrush = Brush.radialGradient(
                        colors = listOf(
                            ambientGlowColor.copy(alpha = 0.65f),
                            ambientGlowColor.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.68f),
                        radius = size.width * 0.85f
                    )
                    onDrawBehind {
                        drawRect(radialBrush)
                    }
                }
        )
        // Soft single contrast scrim ensuring crisp white text legibility without extinguishing glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bottomScrimBrush)
        )

        // Calculate available hero height: spans from y=0 down behind the top of the controls
        val bottomControlsReservedHeight = 310.dp
        val availableHeroHeight = (screenHeight - bottomControlsReservedHeight).coerceAtLeast(260.dp)
        val effectiveHeroHeight = (availableHeroHeight + artVerticalOffsetDp.dp).coerceAtLeast(200.dp)
        // Album art aspect ratio 4:3 with height increased by 20% (approx 55% of screen height)
        val audioHeroHeight = (screenHeight * 0.55f).coerceIn(408.dp, 528.dp)
        val heroHeight = if (isVideoActive) effectiveHeroHeight else audioHeroHeight

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            item(key = "np_hero_player", contentType = "hero_player") {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .fillMaxWidth()
                ) {
                    // Layer 3: Top Full-Bleed Artwork / Video with Gesture Support
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .align(Alignment.TopCenter)
                            .pointerInput(onDragDelta, onDragEnd) {
                                if (onDragDelta != null) {
                                    var accumulatedVelocity = 0f
                                    detectVerticalDragGestures(
                                        onDragEnd = { onDragEnd?.invoke(accumulatedVelocity) },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (dragAmount > 0f || accumulatedVelocity > 0f) {
                                                change.consume()
                                                accumulatedVelocity = dragAmount
                                                onDragDelta.invoke(dragAmount)
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        AnimatedContent(
                            targetState = isVideoActive,
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                            transitionSpec = {
                                (fadeIn(spring(stiffness = 380f, dampingRatio = 0.85f)) + scaleIn(
                                    initialScale = 0.94f,
                                    animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f)
                                )).togetherWith(
                                    fadeOut(spring(stiffness = 380f, dampingRatio = 0.85f)) + scaleOut(
                                        targetScale = 0.94f,
                                        animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f)
                                    )
                                )
                            },
                            label = "HeroArtworkVideoTransition"
                        ) { activeVideo ->
                            if (!activeVideo) {
                                // Directional Cover Art Glide in NowPlayingView
                                AnimatedContent(
                                    targetState = currentTrack,
                                    transitionSpec = {
                                        val direction = 1
                                        (slideInHorizontally(
                                            initialOffsetX = { (it * 0.20f * direction).toInt() },
                                            animationSpec = spring(stiffness = 380f, dampingRatio = 0.82f)
                                        ) + fadeIn(tween(220)) + scaleIn(
                                            initialScale = 0.92f,
                                            animationSpec = spring(stiffness = 380f, dampingRatio = 0.82f)
                                        )).togetherWith(
                                            slideOutHorizontally(
                                                targetOffsetX = { (-it * 0.20f * direction).toInt() },
                                                animationSpec = spring(stiffness = 380f, dampingRatio = 0.82f)
                                            ) + fadeOut(tween(180)) + scaleOut(
                                                targetScale = 0.92f,
                                                animationSpec = spring(stiffness = 380f, dampingRatio = 0.82f)
                                            )
                                        )
                                    },
                                    label = "HeroArtworkMorph"
                                ) { targetTrack ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = heroArtworkAlphaBrush,
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                    ) {
                                        // 1. Base Layer: Sharp Album Art (Always exists underneath)
                                        TrackArtwork(
                                            track = targetTrack,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    translationY = artVerticalOffsetDp.dp.toPx()
                                                },
                                            contentScale = ContentScale.Crop,
                                            cornerRadius = 0.dp,
                                            targetSizePx = ArtworkCache.HERO_TARGET_SIZE_PX,
                                            scale = artZoomScale
                                        )

                                        // 2. Overlaid Gradient Blur: Progressively blurs the artwork in the shaded zone
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    compositingStrategy = CompositingStrategy.Offscreen
                                                    translationY = artVerticalOffsetDp.dp.toPx()
                                                }
                                                .blur(28.dp)
                                                .drawWithContent {
                                                    drawContent()
                                                    drawRect(
                                                        brush = gradientBlurAlphaBrush,
                                                        blendMode = BlendMode.DstIn
                                                    )
                                                }
                                        ) {
                                            TrackArtwork(
                                                track = targetTrack,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                cornerRadius = 0.dp,
                                                targetSizePx = ArtworkCache.HERO_TARGET_SIZE_PX,
                                                scale = artZoomScale
                                            )
                                        }

                                        // 3. Soft Text-Contrast Scrim over the shaded area
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(textContrastScrimBrush)
                                        )
                                    }
                                }
                            } else {
                                // Video Mode: 16:9 Video window floating cleanly centered (Reference image layout)
                                var showQualityMenu by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .statusBarsPadding()
                                        .padding(top = 48.dp, bottom = 12.dp)
                                        .aspectRatio(videoAspect)
                                        .clip(RoundedCornerShape(16.dp))
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        videoContent?.invoke(Modifier.fillMaxSize())
                                    }

                                    // Top-right window overlay badges: 4K quality pill + Fullscreen icon
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 10.dp, end = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val badgeText = when {
                                            currentHeight != null && currentHeight >= 2160 -> "4K"
                                            currentHeight != null -> "${currentHeight}p"
                                            else -> "4K"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.55f))
                                                .clickable { showQualityMenu = !showQualityMenu }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.55f))
                                                .clickable { onOpenFullscreenCinema?.invoke() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Fullscreen,
                                                contentDescription = "Fullscreen",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    if (showQualityMenu && availableHeights.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 44.dp, end = 10.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.Black.copy(alpha = 0.88f))
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Column {
                                                availableHeights.forEach { h ->
                                                    val isSelected = currentHeight == h
                                                    Text(
                                                        text = "${h}p",
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) Color(0xFFFF4D6D) else Color.White,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                onSelectQuality?.invoke(h)
                                                                showQualityMenu = false
                                                            }
                                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

        // Layer 4: Top Status Bar & Header Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(topStatusBarScrimBrush)
        )

        // Layer 5A: Top Header (Drag handle + "Playing from ...") & Top-Right Video Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .pointerInput(onDragDelta, onDragEnd) {
                        if (onDragDelta != null) {
                            var accumulatedVelocity = 0f
                            detectDragGestures(
                                onDragEnd = { onDragEnd?.invoke(accumulatedVelocity) },
                                onDrag = { change, dragAmount ->
                                    if (abs(dragAmount.y) > abs(dragAmount.x) * 1.2f) {
                                        change.consume()
                                        accumulatedVelocity = dragAmount.y
                                        onDragDelta?.invoke(dragAmount.y)
                                    }
                                }
                            )
                        } else {
                            detectDragGestures { change, dragAmount ->
                                if (dragAmount.y > 35f && abs(dragAmount.y) > abs(dragAmount.x) * 1.5f) {
                                    change.consume()
                                    onClose()
                                }
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.45f))
                        .clickable { onClose() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isMusicVideo) "Playing from Music videos" else "Playing from $sourceLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.70f)
                )
            }

            // Top-right video controls (placed in Layer 5A on top of Layer 4 scrim)
            if (!isVideoActive && isVideoAvailable && onToggleVideo != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onToggleVideo.invoke(true) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Video",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else if (isVideoActive && onToggleVideo != null) {
                // Return to Audio / Album Art button
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onToggleVideo.invoke(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Return to Album Art",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Layer 5B: Bottom Controls (Title, Artist, Star, Menu, Lyrics, Timeline, Transport, Volume, Bottom Row, Device)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Title Row: Title & Artist on left, Star + 3-dots on right (Match Screenshot 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentTrack.artist,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circular Star (favorite) button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C2C2E).copy(alpha = 0.75f))
                                .clickable {
                                    if (onToggleFavorite != null) {
                                        onToggleFavorite.invoke()
                                    } else {
                                        localFavorite = !localFavorite
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (effectiveFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (effectiveFavorite) Color(0xFFFBBF24) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Circular ••• button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C2C2E).copy(alpha = 0.75f))
                                .clickable { onOpenThreeDotsMenu() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = "Menu",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Single-line live lyric preview ("{activeLyricLine} >")
                NowPlayingLyricPreview(
                    lyrics = lyrics,
                    positionProvider = positionProvider,
                    onOpenLyrics = onOpenLyrics
                )

                if (lyrics.sections.isNotEmpty()) {
                    SongSectionMarkersBar(
                        sections = lyrics.sections,
                        positionProvider = positionProvider,
                        accentColor = accentTint,
                        onSeek = onSeek,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Scrubber with elapsed (0:40) and negative remaining (-3:02) timestamps
                NowPlayingTimeline(
                    durationMs = durationMs,
                    accentColor = Color.White,
                    onSeek = onSeek,
                    positionProvider = positionProvider
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Large Transport Row: Previous, Large Play/Pause, Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable { onPrevious() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sleek Volume Bar with low and high speaker icons (Match Screenshot 3)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeDown,
                        contentDescription = "Volume Low",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )

                    // Volume slider bar with interactive touch/drag
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .pointerInput(onVolumeChange) {
                                detectTapGestures { offset ->
                                    val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    if (onVolumeChange != null) {
                                        onVolumeChange(newFraction)
                                    } else {
                                        internalVolume = newFraction
                                    }
                                }
                            }
                            .pointerInput(onVolumeChange) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    if (onVolumeChange != null) {
                                        onVolumeChange(newFraction)
                                    } else {
                                        internalVolume = newFraction
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(effectiveVolume.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color.White)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = "Volume High",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Row: Lyrics quote button, Cast button, Queue button, and Device label (Match Screenshot 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Lyrics quote button
                    IconButton(
                        onClick = onOpenLyrics,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatQuote,
                            contentDescription = "Lyrics",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 2. Cast button
                    IconButton(
                        onClick = onOpenNodeConnect,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cast,
                            contentDescription = "Node Connect Cast",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 3. Queue button
                    IconButton(
                        onClick = onOpenPlaybackOptions,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Centered Output Device Label: "This Device"
                Text(
                    text = audioOutputName.ifBlank { "This Device" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clickable { onOpenNodeConnect() }
                        .padding(4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

            // Item 1: Credits Bar
            if (extendedInfo != null && extendedInfo.credits.isNotEmpty()) {
                item(key = "np_credits", contentType = "credits") {
                    var showAllCredits by remember(currentTrack.id) { mutableStateOf(false) }
                    val displayedCredits = if (showAllCredits) extendedInfo.credits else extendedInfo.credits.take(3)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Credits",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (extendedInfo.credits.size > 3) {
                                    Text(
                                        text = if (showAllCredits) "Show less" else "Show all",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showAllCredits = !showAllCredits }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                displayedCredits.forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (onArtistClick != null) {
                                                    Modifier.clickable { onArtistClick.invoke(entry.name, entry.browseId) }
                                                } else Modifier
                                            )
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = entry.role,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color.White.copy(alpha = 0.65f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (onArtistClick != null) {
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.35f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Item 2: About the Artist
            if (extendedInfo != null && (!extendedInfo.artistBio.isNullOrBlank() || !extendedInfo.artistBannerUrl.isNullOrBlank())) {
                item(key = "np_about_artist", contentType = "about_artist") {
                    var isBioExpanded by remember(currentTrack.id) { mutableStateOf(false) }
                    val bannerUrl = extendedInfo.artistBannerUrl
                    val bannerBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                        initialValue = null,
                        bannerUrl
                    ) {
                        if (!bannerUrl.isNullOrBlank()) {
                            value = PlatformArtworkProvider.loadArtwork(bannerUrl, 800)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            .clickable {
                                onArtistClick?.invoke(extendedInfo.artistName, extendedInfo.artistBrowseId)
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top banner / cover image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                if (bannerBitmap != null) {
                                    Image(
                                        bitmap = bannerBitmap!!,
                                        contentDescription = "Artist banner",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    TrackArtwork(
                                        track = currentTrack,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        cornerRadius = 0.dp
                                    )
                                }

                                // Bottom dark scrim
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0.0f to Color.Transparent,
                                                0.50f to Color.Black.copy(alpha = 0.25f),
                                                1.0f to Color.Black.copy(alpha = 0.85f)
                                            )
                                        )
                                )

                                // Pill badge in top-left
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(14.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "About the artist",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Below banner info
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = extendedInfo.artistName.ifBlank { currentTrack.artist },
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!extendedInfo.artistSubscribers.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = extendedInfo.artistSubscribers!!,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!extendedInfo.artistBio.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = extendedInfo.artistBio!!,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        lineHeight = 18.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isBioExpanded) "Show less" else "Read more",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.95f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { isBioExpanded = !isBioExpanded }
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Item 3: More by [Artist]
            if (extendedInfo != null && extendedInfo.moreByArtist.isNotEmpty()) {
                item(key = "np_more_by_artist", contentType = "more_by_artist") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "More by ${extendedInfo.artistName.ifBlank { currentTrack.artist }}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = extendedInfo.moreByArtist,
                                key = { "more_art_${it.id}_${it.title}" },
                                contentType = { "artist_track" }
                            ) { trackItem ->
                                Column(
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                        .clickable { onPlayTrack?.invoke(trackItem) }
                                        .padding(8.dp)
                                ) {
                                    TrackArtwork(
                                        track = trackItem,
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop,
                                        cornerRadius = 10.dp,
                                        targetSizePx = 256
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = trackItem.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = if (trackItem.album.isNotBlank() && trackItem.album != "YouTube Music") trackItem.album else trackItem.artist,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Item 4: More Like This Song
            if (extendedInfo != null && extendedInfo.moreLikeThis.isNotEmpty()) {
                item(key = "np_more_like_this", contentType = "more_like_this") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "More like this",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.28f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                extendedInfo.moreLikeThis.take(8).forEach { similarTrack ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPlayTrack?.invoke(similarTrack) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TrackArtwork(
                                            track = similarTrack,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop,
                                            cornerRadius = 8.dp,
                                            targetSizePx = 128
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = similarTrack.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = similarTrack.artist,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color.White.copy(alpha = 0.65f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = { onTrackOptionsClick?.invoke(similarTrack) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = "Options",
                                                tint = Color.White.copy(alpha = 0.70f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Spacer
            item(key = "np_bottom_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
            }
        }
    }
}

@Composable
fun NowPlayingLyricPreview(
    lyrics: Lyrics,
    positionProvider: () -> Long,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPositionProvider by androidx.compose.runtime.rememberUpdatedState(positionProvider)

    val currentPos by produceState(
        initialValue = currentPositionProvider(),
        positionProvider
    ) {
        while (isActive) {
            value = currentPositionProvider()
            delay(100L)
        }
    }

    val activeLine = remember(lyrics, currentPos) {
        if (lyrics.lines.isEmpty()) null
        else findActiveLyricLine(lyrics.lines, currentPos)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onOpenLyrics)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val line = activeLine
        if (line != null && line.text.isNotBlank()) {
            if (line.isWordSynced) {
                // Word-synced karaoke progress wipe
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Base inactive text rendered dimmed
                    Text(
                        text = line.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.50f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Overlay sung text rendered in pure white clipped to sung progress fraction
                    Text(
                        text = line.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.drawWithContent {
                            val pos = currentPos
                            val progress = calculateLyricLineCharsSungProgress(line, pos)
                            if (progress >= 1f) {
                                drawContent()
                            } else if (progress > 0f) {
                                clipRect(
                                    left = 0f,
                                    top = 0f,
                                    right = size.width * progress,
                                    bottom = size.height
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                    )
                }
            } else {
                // Standard LRC line-synced preview
                Text(
                    text = line.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // Instrumental break / intro placeholder
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Instrumental",
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Instrumental",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.40f),
                    maxLines = 1
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}
