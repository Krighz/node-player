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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
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
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.music.ui.components.LiquidGlassBackground
import com.nodeplayer.music.ui.components.glassLensBubble
import com.nodeplayer.ui.components.LiquidGlassIconButton
import com.nodeplayer.ui.components.LiquidGlassSlider
import com.nodeplayer.ui.component.backdrop.drawBackdrop
import com.nodeplayer.ui.component.backdrop.effects.blur
import com.nodeplayer.ui.component.backdrop.effects.lens
import com.nodeplayer.ui.component.backdrop.highlight.Highlight
import com.nodeplayer.ui.component.backdrop.shadow.Shadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.nodeplayer.music.ui.components.SoundWaveVisualizer
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.music.ui.theme.pressScale
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
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
    queue: List<Track> = emptyList(),
    onPlayTrack: (Track) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    sleepTimerRemainingSec: Long? = null,
    onSetSleepTimer: (Int?) -> Unit = {},
    onOpenSignalPath: () -> Unit = {},
    themeConfig: GlassThemeConfig? = null,
    hazeState: HazeState? = null,
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
    var showSpectrogram by remember { mutableStateOf(false) }
    var showTrackDetailsSheet by remember { mutableStateOf(false) }
    var showQuickActionDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var isRepeatOne by remember { mutableStateOf(false) }

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

    val activeThemeConfig = themeConfig ?: LocalGlassThemeConfig.current
    val sheetHazeState = hazeState ?: remember { HazeState() }

    Box(
        modifier = modifier
            .fillMaxSize()
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
        // 1. Fluid Background (samples into sheetHazeState for crystal lens glass refraction)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(sheetHazeState)
        ) {
            LiquidGlassBackground(
                accentColor = accent,
                isPlaying = isPlaying,
                isPerformanceMode = isPerformanceMode,
                glassStrength = activeThemeConfig.glassStrength,
                animatedBackdrop = activeThemeConfig.animatedBackdrop,
                themeConfig = activeThemeConfig
            )
        }

        // 2. Translucent Ambient Scrim (lets fluid caustics breathe through)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.65f)
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
                    Spacer(modifier = Modifier.height(2.dp))
                    val landscapeOutputDisplay = if (audioOutputInfo.isTrackInitialized) {
                        val dev = if (audioOutputInfo.outputDeviceName.isNotBlank() && audioOutputInfo.outputDeviceName != "—") " • ${audioOutputInfo.outputDeviceName}" else ""
                        "${audioOutputInfo.format} • ${audioOutputInfo.sampleRate} Hz$dev"
                    } else "—"
                    Text(
                        text = "${track.formatBadgeText}  |  Output: $landscapeOutputDisplay",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
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
                        rmsLevel = rmsLevel,
                        isPerformanceMode = isPerformanceMode,
                        hazeState = sheetHazeState,
                        themeConfig = activeThemeConfig
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Secondary Tools Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onToggleShuffle() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (isShuffle) accent else Color.White.copy(alpha = 0.65f), modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { isRepeatOne = !isRepeatOne }, modifier = Modifier.size(48.dp)) {
                            Icon(if (isRepeatOne) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = "Repeat", tint = if (isRepeatOne) accent else Color.White.copy(alpha = 0.65f), modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { showQueueSheet = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue", tint = if (showQueueSheet) accent else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { showLyrics = !showLyrics }, modifier = Modifier.size(48.dp)) {
                            Icon(if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics, contentDescription = "Lyrics", tint = if (showLyrics) accent else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { showSleepTimerDialog = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Timer, contentDescription = "Sleep Timer", tint = if (sleepTimerRemainingSec != null) accent else Color.White.copy(alpha = 0.65f), modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { onOpenEq() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Equalizer", tint = accent, modifier = Modifier.size(24.dp))
                        }
                    }
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

                    Box(modifier = Modifier.clickable { showTrackDetailsSheet = true }) {
                        SignalPathBadge(audioOutputInfo = audioOutputInfo, track = track, accent = accent)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sleepTimerRemainingSec != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.20f))
                                    .clickable { showSleepTimerDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⏱ ${sleepTimerRemainingSec / 60}m",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showLyrics = !showLyrics
                            if (showLyrics) showSpectrogram = false
                        }) {
                            Icon(
                                if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics,
                                contentDescription = "Lyrics",
                                tint = if (showLyrics) accent else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Box {
                            IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showTopMenu = true
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false },
                                modifier = Modifier.background(Color(0xFF1E2130))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Queue (${queue.size})", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = accent
                                        )
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        showQueueSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (sleepTimerRemainingSec != null) "Sleep Timer (${sleepTimerRemainingSec / 60}m)"
                                            else "Sleep Timer",
                                            color = Color.White
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Timer,
                                            contentDescription = null,
                                            tint = if (sleepTimerRemainingSec != null) accent else Color.White.copy(alpha = 0.8f)
                                        )
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        showSleepTimerDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Spectrogram Visualizer", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = if (showSpectrogram) accent else Color.White.copy(alpha = 0.8f)
                                        )
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        showSpectrogram = !showSpectrogram
                                        if (showSpectrogram) showLyrics = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Equalizer & DSP", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Tune,
                                            contentDescription = null,
                                            tint = accent
                                        )
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        onOpenEq()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Audio Signal Path", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.8f)
                                        )
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        showTrackDetailsSheet = true
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hero Center Stage (Artwork, Spectrogram or Synced Lyrics)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (showSpectrogram) {
                        com.nodeplayer.music.ui.components.SpectrogramView(
                            isPlaying = isPlaying,
                            rmsLevel = rmsLevel,
                            isPerformanceMode = isPerformanceMode,
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .aspectRatio(1f)
                                .clickable { showSpectrogram = false }
                        )
                    } else {
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
                                    onLongPress = { showTrackDetailsSheet = true },
                                    modifier = Modifier
                                        .fillMaxWidth(0.88f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Track Title & Artist (High contrast, clean hierarchy)
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
                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. One Quality Line (Source format + Bit-perfect / Resampled sink)
                    if (LocalGlassThemeConfig.current.formatBadgeVisible) {
                        val qualitySinkLine = remember(track, audioOutputInfo) {
                            val srcFormat = if (track.formatBadgeText.isNotBlank()) track.formatBadgeText else "${track.sampleRate} Hz"
                            val sinkDesc = if (audioOutputInfo.isTrackInitialized) {
                                val sinkType = if (audioOutputInfo.isBitPerfect) "Bit-perfect" else "Resampled"
                                val dev = if (audioOutputInfo.outputDeviceName.isNotBlank() && audioOutputInfo.outputDeviceName != "—") " • ${audioOutputInfo.outputDeviceName}" else ""
                                "$sinkType$dev"
                            } else "Direct Output"
                            "$srcFormat  |  $sinkDesc"
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showTrackDetailsSheet = true
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (audioOutputInfo.isBitPerfect) Color(0xFF10B981) else accent, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = qualitySinkLine,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (audioOutputInfo.isBitPerfect) Color(0xFF34D399) else accent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 4. Seekbar with elapsed & remaining times
                PlayerSeekBar(
                    currentMs = currentMs,
                    totalMs = totalMs,
                    progress = progress,
                    accent = accent,
                    onSeek = { isSeeking = true; sliderPosition = it * totalMs },
                    onSeekFinished = { isSeeking = false; onSeekTo(sliderPosition.toLong()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Primary Transport Controls
                PlayerControlButtons(
                    isPlaying = isPlaying,
                    accent = accent,
                    view = view,
                    onPlayPause = onPlayPauseClick,
                    onSkipNext = onSkipNextClick,
                    onSkipPrevious = onSkipPreviousClick,
                    rmsLevel = rmsLevel,
                    isPerformanceMode = isPerformanceMode,
                    hazeState = sheetHazeState,
                    themeConfig = activeThemeConfig
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 6. Secondary Tools Row: Shuffle, Repeat, Queue, Lyrics, Sleep Timer, EQ (all >= 48dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onToggleShuffle()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) accent else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Repeat
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isRepeatOne = !isRepeatOne
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isRepeatOne) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isRepeatOne) accent else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Queue Bottom Sheet Trigger
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showQueueSheet = true
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Queue",
                                tint = if (showQueueSheet) accent else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (queue.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp, end = 4.dp)
                                    .size(16.dp)
                                    .background(accent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (queue.size > 99) "99+" else "${queue.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Lyrics
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showLyrics = !showLyrics
                            if (showLyrics) showSpectrogram = false
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) accent else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Sleep Timer
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showSleepTimerDialog = true
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerRemainingSec != null) accent else Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (sleepTimerRemainingSec != null) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp, end = 4.dp)
                                    .size(8.dp)
                                    .background(accent, CircleShape)
                            )
                        }
                    }

                    // EQ
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenEq()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Equalizer",
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // Queue Modal Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = Color(0xFF10121C),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "${queue.size} songs in queue",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.60f)
                            )
                        )
                    }
                    IconButton(onClick = { showQueueSheet = false }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Queue",
                            tint = Color.White.copy(alpha = 0.70f)
                        )
                    }
                }

                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Queue is empty",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(queue, key = { index, item -> "${item.id}_$index" }) { index, item ->
                            val isCurrent = item.id == track.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isCurrent) accent.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .clickable {
                                        onPlayTrack(item)
                                        showQueueSheet = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCurrent) {
                                    SoundWaveVisualizer(isPlaying = isPlaying, barColor = accent, maxHeight = 16.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.40f),
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.width(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) accent else Color.White
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.artist} • ${item.durationFormatted}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.60f)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { onMoveQueueItem(index, index - 1) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                tint = Color.White.copy(alpha = 0.60f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (index < queue.size - 1) {
                                        IconButton(
                                            onClick = { onMoveQueueItem(index, index + 1) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                tint = Color.White.copy(alpha = 0.60f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onRemoveFromQueue(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove from Queue",
                                            tint = Color.White.copy(alpha = 0.45f),
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
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            containerColor = Color(0xFF161825),
            title = {
                Text(
                    text = "Sleep Timer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sleepTimerRemainingSec != null) {
                        Text(
                            text = "Active: ${sleepTimerRemainingSec / 60}m ${sleepTimerRemainingSec % 60}s remaining",
                            style = MaterialTheme.typography.bodyMedium.copy(color = accent, fontWeight = FontWeight.SemiBold)
                        )
                        Button(
                            onClick = {
                                onSetSleepTimer(null)
                                showSleepTimerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Turn Off Timer")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    val timerDurations = listOf(15, 30, 45, 60, 90)
                    timerDurations.forEach { minutes ->
                        FilledTonalButton(
                            onClick = {
                                onSetSleepTimer(minutes)
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White
                            )
                        ) {
                            Text("$minutes minutes")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Close", color = Color.White.copy(alpha = 0.70f))
                }
            }
        )
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

    if (showTrackDetailsSheet) {
        com.nodeplayer.music.ui.components.TrackDetailsSheet(
            track = track,
            audioOutputInfo = audioOutputInfo,
            onDismiss = { showTrackDetailsSheet = false }
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
    val squircleShape = remember { ContinuousRoundedRectangle(24.dp) }
    Box(
        modifier = modifier
            .clip(squircleShape)
            .background(Color(0xFF10131E).copy(alpha = 0.65f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = squircleShape
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
            model = track,
            contentDescription = track.album,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(squircleShape)
        )
    }
}

@Composable
private fun SignalPathBadge(audioOutputInfo: AudioOutputInfo, track: Track, accent: Color) {
    val badgeShape = remember { ContinuousRoundedRectangle(12.dp) }
    Row(
        modifier = Modifier
            .clip(badgeShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), badgeShape)
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
            text = track.formatBadgeText.ifBlank { if (track.isHiRes) "Hi-Res" else if (track.isLossless) "FLAC" else "MP3" },
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
        LiquidGlassSlider(
            value = progress,
            onValueChange = onSeek,
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
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false,
    hazeState: HazeState? = null,
    themeConfig: GlassThemeConfig? = null
) {
    val cfg = themeConfig ?: LocalGlassThemeConfig.current
    val lensStrength = cfg.lensRefractionStrength
    val rimSpecular = cfg.lensRimSpecular
    val grainStrength = cfg.grainStrengthValue()
    val chromaticFringe = cfg.chromaticFringeValue()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visualizer indicator
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            SoundWaveVisualizer(
                isPlaying = isPlaying,
                barColor = accent,
                maxHeight = 22.dp,
                rmsLevel = rmsLevel,
                isPerformanceMode = isPerformanceMode
            )
        }

        // Previous
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSkipPrevious()
            },
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
        }

        // Hero Play / Pause Liquid-Glass Lens Bubble
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .size(76.dp)
                .pressScale(targetScale = 0.92f) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPlayPause()
                }
                .clip(CircleShape)
                .drawBackdrop(
                    shape = { CircleShape },
                    effects = {
                        if (!isPerformanceMode && cfg.isShaderActive(true)) {
                            lens(
                                refractionHeight = with(density) { (20f * (lensStrength / 0.85f).coerceIn(0.5f, 2.0f)).dp.toPx() },
                                refractionAmount = with(density) { (18f * (lensStrength / 0.85f).coerceIn(0.5f, 2.0f)).dp.toPx() },
                                chromaticAberration = true
                            )
                            blur(with(density) { 14f.dp.toPx() })
                        }
                    },
                    highlight = {
                        Highlight(
                            width = 1.2.dp,
                            alpha = (0.90f * rimSpecular).coerceIn(0.2f, 1.0f)
                        )
                    },
                    shadow = {
                        Shadow(color = Color.Black.copy(alpha = 0.35f))
                    }
                )
                .background(
                    if (isPerformanceMode) accent.copy(alpha = 0.90f)
                    else accent.copy(alpha = 0.78f),
                    CircleShape
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.90f * rimSpecular),
                            Color(0xFFBAE6FD).copy(alpha = 0.35f * rimSpecular),
                            Color.White.copy(alpha = 0.40f * rimSpecular)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color(0xFF131728),
                modifier = Modifier.size(38.dp)
            )
        }

        // Next
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSkipNext()
            },
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(38.dp))
        }

        // Symmetry spacer
        Spacer(modifier = Modifier.size(48.dp))
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
