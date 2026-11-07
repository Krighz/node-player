package com.nodeplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.ui.component.backdrop.drawBackdrop
import com.nodeplayer.ui.component.backdrop.effects.blur
import com.nodeplayer.ui.component.backdrop.effects.lens
import com.nodeplayer.ui.component.backdrop.highlight.Highlight
import com.nodeplayer.ui.component.backdrop.shadow.Shadow
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.LiquidGlassIconButton
import com.nodeplayer.ui.components.LiquidGlassSlider
import com.nodeplayer.ui.components.SoundWaveVisualizer
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.components.onScrubberWheel
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.pressScale

@Composable
fun NowPlayingView(
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    queue: List<Track> = emptyList(),
    currentQueueIndex: Int = 0,
    lrcContent: String? = null,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectQueueItem: (Int) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var showQueue by remember { mutableStateOf(false) }
    val lyrics = remember(lrcContent) {
        if (!lrcContent.isNullOrBlank()) LrcParser.parse(lrcContent) else Lyrics(trackId = 0L)
    }

    if (currentTrack == null) return

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val displayFraction = if (isDragging) dragFraction else progress

    fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070912).copy(alpha = 0.94f))
            .padding(24.dp)
    ) {
        // Top Header: Close button, Now Playing title, Queue toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAYING FROM QUEUE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = currentTrack.album,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { showQueue = !showQueue }) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    tint = if (showQueue) accent else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showQueue) {
            // Queue View
            Text(
                text = "Up Next (${queue.size} tracks)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(queue) { index, track ->
                    val isCurrent = index == currentQueueIndex
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectQueueItem(index) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                color = if (isCurrent) accent else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.width(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) accent else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(track.durationFormatted, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        } else if (lyrics.lines.isNotEmpty()) {
            // Live Synced Lyrics View
            val currentLineIndex = remember(positionMs, lyrics) {
                if (lyrics.lines.isEmpty()) -1
                else {
                    val idx = lyrics.lines.indexOfLast { it.timestampMs <= positionMs }
                    if (idx >= 0) idx else 0
                }
            }
            val listState = rememberLazyListState()

            LaunchedEffect(currentLineIndex) {
                if (currentLineIndex in lyrics.lines.indices) {
                    listState.animateScrollToItem((currentLineIndex - 2).coerceAtLeast(0))
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isCurrent = index == currentLineIndex
                    Text(
                        text = line.text,
                        fontSize = if (isCurrent) 22.sp else 16.sp,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeek(line.timestampMs) }
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            // Artwork Centerpiece
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(
                    track = currentTrack,
                    modifier = Modifier.size(280.dp),
                    cornerRadius = 24.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Track Details & Format Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.7f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (currentTrack.isHiRes || currentTrack.sampleRate >= 88200 || currentTrack.bitDepth > 16) {
                GlassBadge(
                    text = if (currentTrack.bitDepth > 16) "${currentTrack.bitDepth}b/${currentTrack.sampleRate / 1000}k" else "HI-RES",
                    color = Color(0xFFF59E0B)
                )
            } else if (currentTrack.isLossless) {
                GlassBadge(text = "LOSSLESS", color = accent)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scrubber Slider — Liquid Glass
        Column {
            LiquidGlassSlider(
                value = displayFraction.coerceIn(0f, 1f),
                onValueChange = { frac ->
                    isDragging = true
                    dragFraction = frac
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onScrubberWheel { deltaY ->
                        val stepMs = 5000L
                        val newPos = if (deltaY < 0f) positionMs + stepMs else positionMs - stepMs
                        onSeek(newPos.coerceIn(0L, durationMs))
                    }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentDisplayMs = if (isDragging) (dragFraction * durationMs).toLong() else positionMs
                Text(formatTime(currentDisplayMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                Text(formatTime(durationMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Playback Control Buttons — Liquid Glass
        val density = androidx.compose.ui.platform.LocalDensity.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassIconButton(
                icon = Icons.Rounded.SkipPrevious,
                onClick = onPrevious,
                contentDescription = "Previous",
                size = 56.dp
            )

            // Hero Play/Pause — large liquid glass lens bubble
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .drawBackdrop(
                        shape = { CircleShape },
                        effects = {
                            lens(
                                refractionHeight = with(density) { 20f.dp.toPx() },
                                refractionAmount = with(density) { 18f.dp.toPx() },
                                chromaticAberration = true
                            )
                            blur(with(density) { 14f.dp.toPx() })
                        },
                        highlight = { Highlight(width = 1.2.dp, alpha = 0.9f) },
                        shadow = { Shadow(color = Color.Black.copy(alpha = 0.35f)) }
                    )
                    .background(accent.copy(alpha = 0.85f), CircleShape)
                    .pressScale(targetScale = 0.92f, onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color(0xFF131728),
                    modifier = Modifier.size(36.dp)
                )
            }

            LiquidGlassIconButton(
                icon = Icons.Rounded.SkipNext,
                onClick = onNext,
                contentDescription = "Next",
                size = 56.dp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
