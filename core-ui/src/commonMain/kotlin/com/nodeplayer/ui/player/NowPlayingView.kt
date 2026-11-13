package com.nodeplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    audioOutputName: String = "This phone",
    sourceLabel: String = "Search",
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenNodeConnect: () -> Unit,
    onOpenPlaybackOptions: () -> Unit,
    onOpenThreeDotsMenu: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    positionProvider: () -> Long = { positionMs },
    onDragDelta: ((Float) -> Unit)? = null,
    onDragEnd: ((Float) -> Unit)? = null
) {
    if (currentTrack == null) return

    // The palette is looked up once and never refreshed by a plain remember{}: if artwork finishes
    // loading after this screen opens, the colors would stay on the orange fallback forever.
    // produceState re-runs per track, extracts the vibrant color when the cache is empty, derives the
    // full palette and caches it so other screens (lyrics, backdrops) get the same colors.
    val cachedPalette by produceState<AlbumArtPalette?>(
        initialValue = PaletteExtractor.getCachedPalette(currentTrack),
        currentTrack.id,
        currentTrack.path
    ) {
        value = PaletteExtractor.getCachedPalette(currentTrack) ?: run {
            val extracted = PlatformArtworkProvider.extractVibrantColor(currentTrack)
            extracted?.let { color ->
                PaletteExtractor.derivePaletteFromBaseColor(color).also { palette ->
                    PaletteExtractor.cachePalette(currentTrack, palette)
                }
            }
        }
    }
    val vibrantColor = cachedPalette?.vibrant?.toComposeColor() ?: Color(0xFFD4883B)
    val darkMutedColor = cachedPalette?.darkMuted?.toComposeColor() ?: Color(0xFF0A0A0C)
    val accentTint = PaletteExtractor.ensureContrastAgainstDark(vibrantColor, darkMutedColor)

    val lyrics = remember(lrcContent, currentTrack.id) {
        if (!lrcContent.isNullOrBlank()) LrcParser.parse(lrcContent, currentTrack.id) else Lyrics(trackId = currentTrack.id)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
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
        // Underlying ArtworkMeshBackdrop beneath all player layers (D-05, D-08)
        val artworkUrl = remember(currentTrack.id, currentTrack.path) {
            currentTrack.path.takeIf { it.isNotBlank() } ?: currentTrack.id.toString()
        }
        ArtworkMeshBackdrop(
            artworkUrl = artworkUrl,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle top legibility scrim so status bar and drag handle remain legible on high-luminance artwork covers
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Foreground controls layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Section: Centered drag handle & "Playing from <source>"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.45f))
                        .clickable { onClose() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Playing from $sourceLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.70f)
                )
            }

            // Hero Album Art in upper section with smooth squircle border
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(
                    track = currentTrack,
                    modifier = Modifier
                        .size(300.dp),
                    cornerRadius = 24.dp
                )
            }

            // 2. Lower Content Section: Title row, lyric preview, timeline, transport & bottom row
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title Row: Title + Artist on left, Round three-dots on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentTrack.artist,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = accentTint.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Round Three-Dots Button (Translucent Tinted Circle)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accentTint.copy(alpha = 0.22f))
                            .clickable { onOpenThreeDotsMenu() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Single preview line of current lyric (taps into full lyrics)
                NowPlayingLyricPreview(
                    lyrics = lyrics,
                    positionProvider = positionProvider,
                    onOpenLyrics = onOpenLyrics
                )

                // Custom non-M3 Timeline
                NowPlayingTimeline(
                    durationMs = durationMs,
                    accentColor = accentTint,
                    onSeek = onSeek,
                    positionProvider = positionProvider
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Large Transport Row: Previous, Large Play/Pause, Next (Filled glyphs, white)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(58.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
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

                Spacer(modifier = Modifier.height(28.dp))

                // Bottom Row: 3 round tinted buttons (Lyrics, Node Connect, Playback options)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Lyrics Button (speech-bubble)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accentTint.copy(alpha = 0.18f))
                            .clickable { onOpenLyrics() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = "Lyrics",
                            tint = Color.White.copy(alpha = if (lyrics.lines.isNotEmpty()) 0.95f else 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 2. Node Connect Button (cast icon)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accentTint.copy(alpha = 0.18f))
                            .clickable { onOpenNodeConnect() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cast,
                            contentDescription = "Node Connect",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Playback Options Button (three lines)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accentTint.copy(alpha = 0.18f))
                            .clickable { onOpenPlaybackOptions() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatListBulleted,
                            contentDescription = "Playback Options",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Centered Output Device Label
                Text(
                    text = audioOutputName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier
                        .clickable { onOpenNodeConnect() }
                        .padding(4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))
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
