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
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.components.WaveformSeekbar
import com.nodeplayer.ui.theme.LocalAccentColor

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
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    if (currentTrack == null) return

    val qualityText = remember(currentTrack, audioOutputInfo) {
        currentTrack.formatBadgeText
    }

    // Outer Floating Glass Deck Pinned Above Bottom Edge
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF161924).copy(alpha = 0.90f),
                        Color(0xFF0F111A).copy(alpha = 0.95f)
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
                shape = RoundedCornerShape(32.dp)
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
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFEF4444) else Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 2. Center-Left: Transport Controls (Shuffle, Prev, Play/Pause Circle, Next, Repeat)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 14.dp)
            ) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accent else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }

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

                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onCycleRepeat, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode > 0) accent else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(17.dp)
                    )
                }
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
                    IconButton(
                        onClick = { onVolumeChange(if (volume > 0.01f) 0.0f else 1.0f) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (volume <= 0.01f) Icons.Rounded.VolumeMute else if (volume < 0.5f) Icons.Rounded.VolumeDown else Icons.Rounded.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.width(72.dp).height(14.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.8f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                        )
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
                IconButton(
                    onClick = onOpenOutputDevicePicker,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speaker,
                        contentDescription = "Output Device",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Cast to... Button
                IconButton(
                    onClick = onCastClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cast,
                        contentDescription = "Cast to...",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
