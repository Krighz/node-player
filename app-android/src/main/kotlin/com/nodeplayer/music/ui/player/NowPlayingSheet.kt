package com.nodeplayer.music.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.AudioOutputInfo
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.components.LiquidGlassBackground
import com.nodeplayer.music.ui.components.SoundWaveVisualizer
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface
import java.io.File

@Composable
fun NowPlayingSheet(
    track: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    audioOutputInfo: AudioOutputInfo,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit,
    onOpenEq: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }

    val currentMs = if (isSeeking) sliderPosition.toLong() else currentPositionMs
    val totalMs = track.durationMs.coerceAtLeast(1L)
    val progress = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    // Load local LRC if exists in the same directory
    val lyrics: Lyrics? = remember(track.path) {
        try {
            val lrcFile = File(track.path.substringBeforeLast(".") + ".lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                LrcParser.parse(lrcFile.readText(), track.id, "local_lrc")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080E))
    ) {
        // 1. Dribbble-inspired Liquid Glass Organic Chrome & Obsidian Background
        LiquidGlassBackground(
            accentColor = accent,
            isPlaying = isPlaying
        )

        // 2. Fluid Ambient Album Art Overlay (soft 30% blend)
        AsyncImage(
            model = "content://media/external/audio/albumart/${track.albumId}",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.28f,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Subtle Liquid Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color(0xFF090A12).copy(alpha = 0.50f),
                            Color(0xFF040508).copy(alpha = 0.75f)
                        )
                    )
                )
        )

        // 4. Main Player Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                )

                Row {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics,
                            contentDescription = "Toggle Lyrics",
                            tint = if (showLyrics) accent else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = onOpenEq) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Equalizer",
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Upper View: Hero Album Art OR Synced Glass Lyrics
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = showLyrics, label = "HeroCrossfade") { isLyrics ->
                    if (isLyrics) {
                        SyncedLyricsGlassView(
                            lyrics = lyrics,
                            currentPositionMs = currentMs,
                            accentColor = accent,
                            onSeekTo = onSeekTo
                        )
                    } else {
                        // Hero Artwork with Liquid Glass framing
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .glassSurface(
                                    shape = RoundedCornerShape(28.dp),
                                    tintAlpha = 0.20f
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = "content://media/external/audio/albumart/${track.albumId}",
                                contentDescription = track.album,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(28.dp))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. FLOATING LIQUID GLASS PLAYER CARD (Faithful to Dribbble reference)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(
                        shape = RoundedCornerShape(26.dp),
                        tintAlpha = 0.65f
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header inside card: Artwork thumbnail + Title/Artist + SoundWaveVisualizer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1B1D28)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = "content://media/external/audio/albumart/${track.albumId}",
                                contentDescription = track.album,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.70f),
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Animated soundwave visualizer bars
                        SoundWaveVisualizer(isPlaying = isPlaying, barColor = accent, maxHeight = 22.dp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Liquid Glass Seekbar with inline time labels (0:48 ---O--- 2:32)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentMs),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Slider(
                            value = progress,
                            onValueChange = { frac ->
                                isSeeking = true
                                sliderPosition = frac * totalMs
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                onSeekTo(sliderPosition.toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.85f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = formatTime(totalMs),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Playback Controls Row (Liquid Glass style)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* Shuffle */ }) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White.copy(alpha = 0.50f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Previous
                        IconButton(onClick = onSkipPreviousClick) {
                            Icon(
                                imageVector = Icons.Rounded.FastRewind,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Circular Liquid Glass Play/Pause Disc with illuminated specular border
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF2E3146),
                                            Color(0xFF161827),
                                            Color(0xFF0C0D18)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.3.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color(0xCCFFFFFF),
                                            Color(0x35FFFFFF),
                                            Color(0x08FFFFFF)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .clickable(onClick = onPlayPauseClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Next
                        IconButton(onClick = onSkipNextClick) {
                            Icon(
                                imageVector = Icons.Rounded.FastForward,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        IconButton(onClick = { /* Repeat */ }) {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = "Repeat",
                                tint = Color.White.copy(alpha = 0.50f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Live Signal Path Badge
            SignalPathBadge(audioOutputInfo)

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun SyncedLyricsGlassView(
    lyrics: Lyrics?,
    currentPositionMs: Long,
    accentColor: Color,
    onSeekTo: (Long) -> Unit
) {
    if (lyrics == null || !lyrics.isSynced || lyrics.lines.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .glassSurface(shape = RoundedCornerShape(26.dp), tintAlpha = 0.35f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Synced Lyrics Found",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Place a matching .lrc file in the music folder or enable LRCLIB in Settings.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.60f),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        return
    }

    val activeIndex = remember(lyrics, currentPositionMs) {
        lyrics.lines.indexOfLast { it.timestampMs <= currentPositionMs }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .glassSurface(shape = RoundedCornerShape(26.dp), tintAlpha = 0.40f)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isActive = index == activeIndex

                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = if (isActive) 22.sp else 16.sp,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.40f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekTo(line.timestampMs) }
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
fun SignalPathBadge(info: AudioOutputInfo) {
    val badgeText = buildString {
        append(info.format)
        append(" • ")
        append("${info.originalSampleRate / 1000}kHz/${info.originalBitDepth}bit")
        append(" ➔ ")
        if (info.isBitPerfect) {
            append("Bit-Perfect Direct")
        } else if (info.isResampled) {
            append("Resampled (${info.sampleRate / 1000}kHz)")
        } else {
            append(info.outputDeviceName)
        }
    }

    Box(
        modifier = Modifier
            .glassSurface(
                shape = RoundedCornerShape(14.dp),
                tintAlpha = 0.40f
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (info.isBitPerfect) Color(0xFF10B981) else Color(0xFFFFB800))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.90f)
                )
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
