package com.nodeplayer.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.SoundWaveVisualizer
import com.nodeplayer.ui.theme.LocalAccentColor

@Composable
fun BottomPlayerDock(
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float = 1.0f,
    isShuffle: Boolean = false,
    repeatMode: Int = 0, // 0 = Off, 1 = All, 2 = One
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onExpandNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    if (currentTrack == null) return

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        isDark = true,
        onClick = onExpandNowPlaying
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Scrubber Progress Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(positionMs),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.width(36.dp)
                )

                Slider(
                    value = progress,
                    onValueChange = { frac ->
                        onSeek((frac * durationMs).toLong())
                    },
                    modifier = Modifier.weight(1f).height(16.dp),
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
                    modifier = Modifier.width(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Bar: Track Info (Left) + Center Playback Controls + Right Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Left Track Metadata
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            SoundWaveVisualizer(isPlaying = true, barColor = accent, maxHeight = 22.dp)
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                        }
                    }

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
                            tint = if (isShuffle) accent else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accent)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    IconButton(onClick = onCycleRepeat) {
                        val icon = if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                        Icon(
                            icon,
                            contentDescription = "Repeat",
                            tint = if (repeatMode > 0) accent else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3. Right Volume Control
                Row(
                    modifier = Modifier.width(130.dp).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (volume > 0.01f) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        contentDescription = "Volume",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).height(14.dp),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }
            }
        }
    }
}
