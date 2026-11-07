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
import com.nodeplayer.model.AudioOutputInfo
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.LiquidIconButton
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.components.WaveformSeekbar
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.pressScale
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

@Composable
fun BottomPlayerDeck(
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float = 1.0f,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    isFavorite: Boolean = false,
    audioOutputInfo: AudioOutputInfo = AudioOutputInfo(),
    cachedPeaks: List<Float>? = null,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onOpenSignalPath: () -> Unit = {},
    onOpenOutputDevicePicker: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onExpandNowPlaying: () -> Unit = {},
    sleepTimerRemainingSec: Long? = null,
    onSetSleepTimer: (Int?) -> Unit = {},
    backdrop: Backdrop = remember { emptyBackdrop() },
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    if (currentTrack == null) return

    val qualityText = remember(currentTrack, audioOutputInfo) {
        currentTrack.formatBadgeText
    }

    val deckShape = remember { ContinuousRoundedRectangle(32.dp) }

    // Outer Floating Glass Deck Pinned Above Bottom Edge
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(deckShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { deckShape },
                effects = {
                    vibrancy()
                    blur(16f.dp.toPx())
                    lens(14f.dp.toPx(), 12f.dp.toPx())
                },
                highlight = { Highlight.Ambient.copy(alpha = 0.5f) }
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF161924).copy(alpha = 0.85f),
                        Color(0xFF0F111A).copy(alpha = 0.90f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f), // Specular highlight top
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = deckShape
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Left: Track Artwork + Metadata + Favorite Button
            Row(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onExpandNowPlaying() }
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    track = currentTrack,
                    modifier = Modifier.size(46.dp),
                    cornerRadius = 10.dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentTrack.artist} • ${currentTrack.album}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Favorite Heart Toggle
                LiquidIconButton(
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onClick = onToggleFavorite,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFEF4444) else Color.White.copy(alpha = 0.50f),
                    size = 32.dp,
                    lighterStyle = true
                )
            }

            // 2. Center-Left: Transport Controls (Shuffle, Prev, Play/Pause Circle, Next, Repeat)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 14.dp)
            ) {
                LiquidIconButton(
                    icon = Icons.Rounded.Shuffle,
                    onClick = onToggleShuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) accent else Color.White.copy(alpha = 0.55f),
                    size = 32.dp,
                    lighterStyle = true
                )

                LiquidIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    onClick = onPrevious,
                    contentDescription = "Previous",
                    tint = Color.White.copy(alpha = 0.85f),
                    size = 36.dp,
                    lighterStyle = true
                )

                // Prominent Play/Pause White Circle Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0F111A),
                        modifier = Modifier.size(22.dp)
                    )
                }

                LiquidIconButton(
                    icon = Icons.Rounded.SkipNext,
                    onClick = onNext,
                    contentDescription = "Next",
                    tint = Color.White.copy(alpha = 0.85f),
                    size = 36.dp,
                    lighterStyle = true
                )

                LiquidIconButton(
                    icon = if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    onClick = onCycleRepeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode > 0) accent else Color.White.copy(alpha = 0.55f),
                    size = 32.dp,
                    lighterStyle = true
                )
            }

            // 3. Center: Integrated Waveform Seekbar (Takes majority of width)
            WaveformSeekbar(
                positionMs = positionMs,
                durationMs = durationMs,
                peaks = cachedPeaks,
                onSeek = onSeek,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )

            // 4. Right: Volume Slider + Quality Pill Badge + Output Device + Cast Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 12.dp)
            ) {
                // Volume Slider with Mute
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiquidIconButton(
                        icon = if (volume <= 0.01f) Icons.Rounded.VolumeMute else if (volume < 0.5f) Icons.Rounded.VolumeDown else Icons.Rounded.VolumeUp,
                        onClick = { onVolumeChange(if (volume > 0.01f) 0.0f else 1.0f) },
                        contentDescription = "Volume",
                        tint = Color.White.copy(alpha = 0.65f),
                        size = 32.dp,
                        lighterStyle = true
                    )

                    LiquidSlider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.width(72.dp).height(24.dp)
                    )
                }

                // Real Quality Pill Badge (Click to open Signal Path inspector)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .clickable { onOpenSignalPath() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = qualityText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }

                // Output Device Picker Button
                LiquidIconButton(
                    icon = Icons.Rounded.Speaker,
                    onClick = onOpenOutputDevicePicker,
                    contentDescription = "Output Device",
                    tint = Color.White.copy(alpha = 0.70f),
                    size = 32.dp,
                    lighterStyle = true
                )

                // Cast to... Button
                LiquidIconButton(
                    icon = Icons.Rounded.Cast,
                    onClick = onCastClick,
                    contentDescription = "Cast to...",
                    tint = accent,
                    size = 32.dp,
                    lighterStyle = true
                )

                // Sleep Timer Button
                Box {
                    var showSleepMenu by remember { mutableStateOf(false) }
                    LiquidIconButton(
                        icon = Icons.Rounded.Bedtime,
                        onClick = { showSleepMenu = true },
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerRemainingSec != null) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.70f),
                        size = 32.dp,
                        lighterStyle = true
                    )

                    DropdownMenu(
                        expanded = showSleepMenu,
                        onDismissRequest = { showSleepMenu = false }
                    ) {
                        if (sleepTimerRemainingSec != null) {
                            val mins = sleepTimerRemainingSec / 60
                            val secs = sleepTimerRemainingSec % 60
                            DropdownMenuItem(
                                text = { Text("Active: ${mins}m ${secs}s left (Turn Off)") },
                                onClick = {
                                    onSetSleepTimer(null)
                                    showSleepMenu = false
                                }
                            )
                        }
                        listOf(15, 30, 45, 60, 90).forEach { mins ->
                            DropdownMenuItem(
                                text = { Text("$mins minutes") },
                                onClick = {
                                    onSetSleepTimer(mins)
                                    showSleepMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
