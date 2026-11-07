package com.nodeplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.components.onScrubberWheel
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig

/**
 * Convx-style Liquid Glass Bottom Player Dock (Windows / Desktop).
 * Features:
 * - Continuous squircle container with Kyant0 SDF lens refraction and specular rim
 * - Scrubber slider with glowing glass track
 * - Track metadata, artwork thumbnail, audio-reactive animated controls
 * - Settings (Lines 60-61: Lens, Rim toggles)
 */
@Composable
fun BottomPlayerDock(
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float = 1.0f,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onExpandNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val themeConfig = LocalGlassThemeConfig.current

    if (currentTrack == null) return

    // Dock glass settings (Line 60: Lens, Line 61: Rim)
    val lensActive = themeConfig.dockGlassLensEnabled && !themeConfig.isPerformanceMode && themeConfig.glassStrength != "OFF"
    val rimActive = themeConfig.dockGlassRimEnabled && themeConfig.glassStrength != "OFF"

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val displayFraction = if (isDragging) dragFraction else progress
    val displayPositionMs = if (isDragging && durationMs > 0) (dragFraction * durationMs).toLong() else positionMs

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    val dockShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(dockShape)
            .drawBackdrop(
                shape = { dockShape },
                effects = {
                    vibrancy()
                    blur(16f.dp.toPx())
                    if (lensActive) {
                        lens(
                            refractionHeight = 20f.dp.toPx(),
                            refractionAmount = 18f.dp.toPx() * (themeConfig.lensRefractionStrength / 0.85f),
                            chromaticAberration = true
                        )
                    }
                },
                highlight = if (rimActive) {
                    { Highlight.Default.copy(alpha = 0.85f * themeConfig.lensRimSpecular) }
                } else null,
                onDrawSurface = {
                    drawRect(Color(0xFF0F172A).copy(alpha = (themeConfig.effectiveTintAlpha * 0.78f).coerceIn(0.12f, 0.85f)))
                }
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (rimActive) 0.32f else 0.12f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = dockShape
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Scrubber Progress Slider with smooth drag tracking
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(displayPositionMs),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.width(38.dp)
                )

                Slider(
                    value = displayFraction,
                    onValueChange = { frac ->
                        isDragging = true
                        dragFraction = frac
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        if (durationMs > 0) {
                            onSeek((dragFraction * durationMs).toLong())
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .onScrubberWheel { deltaY ->
                            val stepMs = 5000L
                            val newPos = if (deltaY < 0f) positionMs + stepMs else positionMs - stepMs
                            onSeek(newPos.coerceIn(0L, durationMs))
                        },
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                Text(
                    text = formatTime(durationMs),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.width(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Bar: Track Info + Center Playback Controls + Right Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Left Track Metadata
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onExpandNowPlaying() }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(
                        track = currentTrack,
                        modifier = Modifier.size(44.dp),
                        cornerRadius = 10.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (currentTrack.isHiRes || currentTrack.sampleRate >= 88200 || currentTrack.bitDepth > 16) {
                                Spacer(modifier = Modifier.width(6.dp))
                                GlassBadge(
                                    text = if (currentTrack.bitDepth > 16) "${currentTrack.bitDepth}b/${currentTrack.sampleRate / 1000}k" else "HI-RES",
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                        Text(
                            text = "${currentTrack.artist} • ${currentTrack.album}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. Playback Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) accent else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    // Play/Pause Liquid Bubble
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .drawBackdrop(
                                shape = { CircleShape },
                                effects = {
                                    if (lensActive) {
                                        lens(refractionHeight = 8f.dp.toPx(), refractionAmount = 10f.dp.toPx())
                                    }
                                },
                                highlight = if (rimActive) {
                                    { Highlight.Default.copy(alpha = 0.9f) }
                                } else null,
                                onDrawSurface = { drawRect(accent) }
                            )
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF131728),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            imageVector = if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (repeatMode > 0) accent else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 3. Right Volume Control & Expand Fullscreen Button
                Row(
                    modifier = Modifier.weight(0.7f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (volume <= 0.01f) Icons.Rounded.VolumeMute else if (volume < 0.5f) Icons.Rounded.VolumeDown else Icons.Rounded.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.width(90.dp).height(16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onExpandNowPlaying) {
                        Icon(
                            Icons.Rounded.OpenInFull,
                            contentDescription = "Expand Fullscreen",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
