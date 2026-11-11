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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import com.nodeplayer.ui.theme.PaletteExtractor
import com.nodeplayer.ui.theme.PaletteExtractor.toComposeColor
import kotlin.math.abs

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
    positionMs: Long,
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
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val cachedPalette = remember(currentTrack.id, currentTrack.path) {
        PaletteExtractor.getCachedPalette(currentTrack)
    }
    val vibrantColor = cachedPalette?.vibrant?.toComposeColor() ?: Color(0xFFD4883B)
    val darkMutedColor = cachedPalette?.darkMuted?.toComposeColor() ?: Color(0xFF0A0A0C)
    val accentTint = PaletteExtractor.ensureContrastAgainstDark(vibrantColor, darkMutedColor)

    val lyrics = remember(lrcContent, currentTrack.id) {
        if (!lrcContent.isNullOrBlank()) LrcParser.parse(lrcContent, currentTrack.id) else Lyrics(trackId = currentTrack.id)
    }

    val activeLyricLine = remember(positionMs, lyrics) {
        if (lyrics.lines.isEmpty()) null
        else {
            val idx = lyrics.lines.indexOfLast { it.timestampMs <= positionMs }
            if (idx in lyrics.lines.indices) lyrics.lines[idx].text else null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (dragAmount.y > 35f && abs(dragAmount.y) > abs(dragAmount.x) * 1.5f) {
                        change.consume()
                        onClose()
                    }
                }
            }
    ) {
        // Underlying AlbumArtGradientBackdrop
        AlbumArtGradientBackdrop(
            track = currentTrack,
            modifier = Modifier.fillMaxSize()
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

            // Spacer to push the title row down to ~62% of the screen
            Spacer(modifier = Modifier.weight(1.0f))

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
                if (!activeLyricLine.isNullOrBlank()) {
                    Text(
                        text = activeLyricLine,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLyrics() }
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Custom non-M3 Timeline
                NowPlayingTimeline(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    accentColor = accentTint,
                    onSeek = onSeek
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
