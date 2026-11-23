package com.nodeplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Quick settings glass bottom sheet:
 * - Volume slider
 * - Output device name & format info
 * - Sleep timer selector
 * - Lyrics offset adjustment
 */
@Composable
fun QuickSettingsSheet(
    visible: Boolean,
    volume: Float = 0.75f,
    outputDeviceName: String,
    audioFormatDescription: String,
    sleepTimerRemainingSec: Long?,
    accentColor: Color,
    onVolumeChange: ((Float) -> Unit)? = null,
    onSetSleepTimer: (Int?) -> Unit,
    onOpenEqualizer: () -> Unit,
    onDismiss: () -> Unit,
    isVideoActive: Boolean = false,
    isVideoPlaying: Boolean = true,
    onToggleVideo: (() -> Unit)? = null,
    onDownloadTrack: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF14151C).copy(alpha = 0.96f))
                    .clickable(enabled = false) {}
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Output Device & Format Info Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audio Output",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.50f)
                        )
                        Text(
                            text = outputDeviceName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    if (audioFormatDescription.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.20f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = audioFormatDescription,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                }

                // Quick Actions: Download, Add to Playlist, Video Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Download Track Action
                    if (onDownloadTrack != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    onDismiss()
                                    onDownloadTrack()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "Download Song",
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Download",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Add to Playlist Action
                    if (onAddToPlaylist != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    onDismiss()
                                    onAddToPlaylist()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlaylistAdd,
                                    contentDescription = "Add to Playlist",
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Add to List",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Video Mode Action
                    if (onToggleVideo != null) {
                        val (videoLabel, videoIcon) = when {
                            !isVideoActive -> "Play Video" to Icons.Rounded.PlayArrow
                            isVideoPlaying -> "Pause Video" to Icons.Rounded.Pause
                            else -> "Resume Video" to Icons.Rounded.PlayArrow
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isVideoActive) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    onDismiss()
                                    onToggleVideo()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = videoIcon,
                                    contentDescription = videoLabel,
                                    tint = if (isVideoActive) accentColor else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = videoLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Playback Speed Selector
                Text(
                    text = "Playback Speed",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.70f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.75f to "0.75x", 1.0f to "1.0x", 1.25f to "1.25x", 1.5f to "1.5x", 2.0f to "2.0x").forEach { (spd, lbl) ->
                        val isSelected = kotlin.math.abs(playbackSpeed - spd) < 0.05f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                                .clickable { onPlaybackSpeedChange?.invoke(spd) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lbl,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Equalizer Trigger
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .clickable {
                            onDismiss()
                            onOpenEqualizer()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Equalizer & DSP Settings",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sleep Timer Options
                Text(
                    text = "Sleep Timer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.70f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(null to "Off", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (min, label) ->
                        val isSelected = if (min == null) sleepTimerRemainingSec == null else (sleepTimerRemainingSec != null && sleepTimerRemainingSec / 60 in (min - 2)..(min + 2))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                            .clickable { onSetSleepTimer(min) }
                            .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
