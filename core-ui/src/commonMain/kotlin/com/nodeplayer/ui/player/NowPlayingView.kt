package com.nodeplayer.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MoreHoriz
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
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.Track
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
    audioOutputName: String = "krighz's Phone",
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
    artZoomScale: Float = 1.03f,
    artVerticalOffsetDp: Int = 0
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
        animationSpec = tween(450),
        label = "NPVibrant"
    )
    val animatedDominant by animateColorAsState(
        targetValue = rawDominant,
        animationSpec = tween(450),
        label = "NPDominant"
    )
    val animatedDarkMuted by animateColorAsState(
        targetValue = rawDarkMuted,
        animationSpec = tween(450),
        label = "NPDarkMuted"
    )
    val animatedAccent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(450),
        label = "NPAccent"
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
        targetValue = if (isVideoActive) videoAspect else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "HeroAspectMorph"
    )

    var isFavorite by remember(currentTrack.id) { mutableStateOf(currentTrack.isFavorite) }
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

    val topArtworkFadeBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.60f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = 0.88f)
        )
    }

    val topStatusBarScrimBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.70f),
            0.45f to Color.Black.copy(alpha = 0.30f),
            1.0f to Color.Transparent
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .pointerInput(onDragDelta, onDragEnd) {
                if (onDragDelta != null) {
                    var accumulatedVelocity = 0f
                    detectDragGestures(
                        onDragEnd = {
                            onDragEnd?.invoke(accumulatedVelocity)
                        },
                        onDrag = { change, dragAmount ->
                            if (abs(dragAmount.y) > abs(dragAmount.x) * 1.2f) {
                                change.consume()
                                accumulatedVelocity = dragAmount.y
                                onDragDelta(dragAmount.y)
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
            }
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

        // Calculate available hero height: spans from y=0 down to the top of the title row
        val bottomControlsReservedHeight = 340.dp
        val availableHeroHeight = (screenHeight - bottomControlsReservedHeight).coerceAtLeast(260.dp)
        val effectiveHeroHeight = (availableHeroHeight + artVerticalOffsetDp.dp).coerceAtLeast(200.dp)

        // Layer 3: Top Full-Bleed Artwork / Video
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(effectiveHeroHeight)
                .align(Alignment.TopCenter)
        ) {
            if (!isVideoActive) {
                // Still Artwork: 100% full-width, zero horizontal padding, zero corner clipping, starting at y = 0
                TrackArtwork(
                    track = currentTrack,
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

                // Smooth gradient dissolve at the bottom edge of the artwork: blends into blurred glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .align(Alignment.BottomCenter)
                        .background(topArtworkFadeBrush)
                )
            } else {
                // Video Mode: 16:9 Video centered in hero area cleanly above track title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(videoAspect)
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = 1.02f
                            scaleY = 1.02f
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        videoContent?.invoke(Modifier.fillMaxSize())
                    }

                    // Dissolve gradient at the bottom edge of video
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.60f)
                                )
                            )
                    )
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
                modifier = Modifier.align(Alignment.Center),
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
            } else if (isVideoActive) {
                var showQualityMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quality badge pill ("480p" etc.)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { showQualityMenu = !showQualityMenu }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentHeight != null) "${currentHeight}p" else "Auto",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Fullscreen expand pill
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
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

                    // Dedicated Stop Video button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { onToggleVideo?.invoke(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Stop Video",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showQualityMenu) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 40.dp, end = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.90f))
                            .padding(6.dp)
                    ) {
                        Column {
                            Text(
                                text = "Auto",
                                fontSize = 12.sp,
                                color = if (currentHeight == null) Color.Cyan else Color.White,
                                fontWeight = if (currentHeight == null) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectQuality?.invoke(null)
                                        showQualityMenu = false
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                            val heights = if (availableHeights.isNotEmpty()) availableHeights else listOf(1080, 720, 480, 360)
                            heights.forEach { h ->
                                Text(
                                    text = "${h}p",
                                    fontSize = 12.sp,
                                    color = if (currentHeight == h) Color.Cyan else Color.White,
                                    fontWeight = if (currentHeight == h) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectQuality?.invoke(h)
                                            showQualityMenu = false
                                        }
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
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
                                .clickable { isFavorite = !isFavorite },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFBBF24) else Color.White,
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

                // Centered Output Device Label: "krighz's Phone"
                Text(
                    text = audioOutputName.ifBlank { "krighz's Phone" },
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

@Composable
fun NowPlayingLyricPreview(
    lyrics: Lyrics,
    positionProvider: () -> Long,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPositionProvider = androidx.compose.runtime.rememberUpdatedState(positionProvider)

    // Fast-path index lookup driven by positionProvider
    val activeLine by remember(lyrics) {
        derivedStateOf {
            if (lyrics.lines.isEmpty()) null
            else findActiveLyricLine(lyrics.lines, currentPositionProvider.value())
        }
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
                            val pos = currentPositionProvider.value()
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
