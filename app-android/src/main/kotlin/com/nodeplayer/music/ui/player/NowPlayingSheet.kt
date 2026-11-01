package com.nodeplayer.music.ui.player

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import kotlin.math.abs

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
    isShuffle: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val view = LocalView.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp > 600

    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQuickActionDialog by remember { mutableStateOf(false) }

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
            .pointerInput(Unit) {
                // Vertical drag down to close gesture
                detectDragGestures { change, dragAmount ->
                    if (dragAmount.y > 35f && abs(dragAmount.y) > abs(dragAmount.x) * 1.5f) {
                        change.consume()
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDismiss()
                    }
                }
            }
    ) {
        // 1. Fluid Background
        LiquidGlassBackground(
            accentColor = accent,
            isPlaying = isPlaying,
            isPerformanceMode = isPerformanceMode
        )

        // 2. High-Contrast Darkening Scrim (guarantees text contrast even for pure white album art)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color(0xFF07080E).copy(alpha = 0.70f),
                            Color(0xFF030407).copy(alpha = 0.90f)
                        )
                    )
                )
        )

        // 3. Player Layout (Adaptive Portrait vs Landscape)
        if (isLandscape) {
            // LANDSCAPE / TABLET LAYOUT: 2-Column Side-by-Side
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Hero Artwork
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    HeroArtwork(
                        track = track,
                        accent = accent,
                        view = view,
                        onSkipNext = onSkipNextClick,
                        onSkipPrevious = onSkipPreviousClick,
                        onLongPress = { showQuickActionDialog = true },
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .aspectRatio(1f)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Right Column: Info, Seekbar, Controls
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Header bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                        }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // Signal Path Badge
                        SignalPathBadge(audioOutputInfo = audioOutputInfo, track = track, accent = accent)

                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenEq()
                        }) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Equalizer", tint = accent)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.70f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Seekbar
                    PlayerSeekBar(
                        currentMs = currentMs,
                        totalMs = totalMs,
                        progress = progress,
                        accent = accent,
                        onSeek = { isSeeking = true; sliderPosition = it * totalMs },
                        onSeekFinished = { isSeeking = false; onSeekTo(sliderPosition.toLong()) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Playback Controls
                    PlayerControlButtons(
                        isPlaying = isPlaying,
                        accent = accent,
                        view = view,
                        onPlayPause = onPlayPauseClick,
                        onSkipNext = onSkipNextClick,
                        onSkipPrevious = onSkipPreviousClick,
                        isShuffle = isShuffle,
                        onToggleShuffle = onToggleShuffle,
                        rmsLevel = rmsLevel,
                        isPerformanceMode = isPerformanceMode
                    )
                }
            }
        } else {
            // PORTRAIT LAYOUT: Clean Vertical Hierarchy
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDismiss()
                    }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(34.dp))
                    }

                    SignalPathBadge(audioOutputInfo = audioOutputInfo, track = track, accent = accent)

                    Row {
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showLyrics = !showLyrics
                        }) {
                            Icon(
                                if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics,
                                contentDescription = "Lyrics",
                                tint = if (showLyrics) accent else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenEq()
                        }) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Equalizer", tint = accent, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hero Center Stage (Artwork or Synced Lyrics)
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
                            HeroArtwork(
                                track = track,
                                accent = accent,
                                view = view,
                                onSkipNext = onSkipNextClick,
                                onSkipPrevious = onSkipPreviousClick,
                                onLongPress = { showQuickActionDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Track Title & Artist (High contrast, single clear hero)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 22.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${track.artist} — ${track.album}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Seekbar
                PlayerSeekBar(
                    currentMs = currentMs,
                    totalMs = totalMs,
                    progress = progress,
                    accent = accent,
                    onSeek = { isSeeking = true; sliderPosition = it * totalMs },
                    onSeekFinished = { isSeeking = false; onSeekTo(sliderPosition.toLong()) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Playback Controls Row
                PlayerControlButtons(
                    isPlaying = isPlaying,
                    accent = accent,
                    view = view,
                    onPlayPause = onPlayPauseClick,
                    onSkipNext = onSkipNextClick,
                    onSkipPrevious = onSkipPreviousClick,
                    isShuffle = isShuffle,
                    onToggleShuffle = onToggleShuffle,
                    rmsLevel = rmsLevel,
                    isPerformanceMode = isPerformanceMode
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Quick Action Dialog (Triggered on Long Press)
    if (showQuickActionDialog) {
        AlertDialog(
            onDismissRequest = { showQuickActionDialog = false },
            title = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text("Artist: ${track.artist}", style = MaterialTheme.typography.bodyMedium)
                    Text("Album: ${track.album}", style = MaterialTheme.typography.bodyMedium)
                    Text("Format: ${track.sampleRate} Hz • ${track.bitDepth}-bit", style = MaterialTheme.typography.bodySmall.copy(color = accent))
                    Text("Path: ${track.path}", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showQuickActionDialog = false
                    onOpenEq()
                }) {
                    Text("DSP & EQ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickActionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun HeroArtwork(
    track: Track,
    accent: Color,
    view: android.view.View,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .glassSurface(
                shape = RoundedCornerShape(28.dp),
                tintAlpha = 0.25f
            )
            .pointerInput(track.id) {
                // Horizontal swipe to skip track + Long press for quick actions
                detectTapGestures(
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    }
                )
            }
            .pointerInput(track.id) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (abs(dragAmount) > 28f) {
                        change.consume()
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (dragAmount < 0) {
                            onSkipNext()
                        } else {
                            onSkipPrevious()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Fallback Obsidian placeholder for track with no artwork
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = accent.copy(alpha = 0.40f),
            modifier = Modifier.size(96.dp)
        )
        // High-resolution artwork
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

@Composable
private fun SignalPathBadge(audioOutputInfo: AudioOutputInfo, track: Track, accent: Color) {
    val rateText = if (track.sampleRate >= 1000) "${track.sampleRate / 1000} kHz" else "${track.sampleRate} Hz"
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (audioOutputInfo.isBitPerfect) Color(0xFF10B981) else accent, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${if (track.isHiRes) "Hi-Res" else "FLAC"} • $rateText",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
        )
    }
}

@Composable
private fun PlayerSeekBar(
    currentMs: Long,
    totalMs: Long,
    progress: Float,
    accent: Color,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.20f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentMs),
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp)
            )
            Text(
                text = formatTime(totalMs),
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp)
            )
        }
    }
}

@Composable
private fun PlayerControlButtons(
    isPlaying: Boolean,
    accent: Color,
    view: android.view.View,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    isShuffle: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visualizer indicator
        SoundWaveVisualizer(
            isPlaying = isPlaying,
            barColor = accent,
            maxHeight = 22.dp,
            rmsLevel = rmsLevel,
            isPerformanceMode = isPerformanceMode
        )

        // Previous
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSkipPrevious()
            },
            modifier = Modifier.size(54.dp)
        ) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Hero Play / Pause Button with tactile glow
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }

        // Next
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSkipNext()
            },
            modifier = Modifier.size(54.dp)
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Shuffle Icon
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggleShuffle()
            }
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShuffle) accent else Color.White.copy(alpha = 0.40f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SyncedLyricsGlassView(
    lyrics: Lyrics?,
    currentPositionMs: Long,
    accentColor: Color,
    onSeekTo: (Long) -> Unit
) {
    if (lyrics == null || lyrics.lines.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No lyrics available", color = Color.White.copy(alpha = 0.50f))
        }
        return
    }

    val listState = rememberLazyListState()
    val activeIndex = lyrics.lines.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)

    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 40.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (isActive) 20.sp else 16.sp,
                    color = if (isActive) accentColor else Color.White.copy(alpha = 0.40f)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onSeekTo(line.timestampMs) }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
