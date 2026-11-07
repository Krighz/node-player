package com.nodeplayer.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.HiResStatus
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailsSheet(
    track: Track,
    audioOutputInfo: com.nodeplayer.model.AudioOutputInfo = com.nodeplayer.model.AudioOutputInfo(),
    effectConfig: com.nodeplayer.model.AudioEffectConfig = com.nodeplayer.model.AudioEffectConfig(),
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    onAnalyzeSong: ((Track) -> Unit)? = null,
    isAnalyzing: Boolean = false
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ContinuousRoundedRectangle(28.dp),
        containerColor = Color(0xFF0F111A).copy(alpha = 0.96f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ContinuousRoundedRectangle(12.dp))
                        .background(accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Truthful Signal Path (Section 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRUTHFUL SIGNAL PATH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = accent
                    )
                )
                val isDirect = audioOutputInfo.isBitPerfect || (audioOutputInfo.isTrackInitialized && audioOutputInfo.sampleRate == track.sampleRate && track.sampleRate > 0)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDirect) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isDirect) "Direct Stream" else if (audioOutputInfo.isTrackInitialized) "Resampled" else "Pending Playback",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isDirect) Color(0xFF10B981) else Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
                tintAlpha = 0.45f
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Node 1: Source
                    SignalPathStep(
                        icon = Icons.Rounded.AudioFile,
                        title = "Source Format",
                        primary = track.formatBadgeText.ifBlank { track.mimeType.substringAfterLast("/").uppercase() },
                        secondary = if (track.sampleRate > 0) "${track.sampleRate} Hz • ${if (track.bitDepth > 0) "${track.bitDepth}-bit" else "${track.bitrateKbps} kbps"}" else "Metadata parsed from file header",
                        statusColor = Color(0xFF10B981)
                    )

                    SignalPathConnector()

                    // Node 2: DSP Processing
                    SignalPathStep(
                        icon = Icons.Rounded.Tune,
                        title = "DSP Audio Engine",
                        primary = "32-bit Float Processing",
                        secondary = "EQ • Reverb & Ambience • Dynamic Limiter",
                        statusColor = accent
                    )

                    SignalPathConnector()

                    // Node 3: AudioTrack / Media3 Sink
                    val outEnc = if (audioOutputInfo.isTrackInitialized && audioOutputInfo.format.isNotBlank() && audioOutputInfo.format != "—") {
                        "${audioOutputInfo.format} • ${audioOutputInfo.sampleRate} Hz"
                    } else "Media3 AudioSink (Standby)"
                    val isBitPerfect = audioOutputInfo.isTrulyBitPerfect(track.sampleRate, track.bitDepth, effectConfig.isDspBypassed)
                    SignalPathStep(
                        icon = Icons.Rounded.VolumeUp,
                        title = "AudioTrack Sink",
                        primary = outEnc,
                        secondary = if (isBitPerfect) {
                            "Bit-perfect direct stream (DSP bypassed)"
                        } else if (audioOutputInfo.isTrackInitialized && audioOutputInfo.sampleRate == track.sampleRate) {
                            if (!effectConfig.isDspBypassed) "Direct sample rate • 32-bit float DSP active" else "Direct stream"
                        } else if (audioOutputInfo.isTrackInitialized) {
                            "System resampler active (${audioOutputInfo.sampleRate} Hz)"
                        } else {
                            "AnalyticsListener awaiting playback initialization"
                        },
                        statusColor = if (isBitPerfect) Color(0xFF10B981) else if (audioOutputInfo.isTrackInitialized) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.4f)
                    )

                    SignalPathConnector()

                    // Node 4: Output Device
                    val devName = if (audioOutputInfo.outputDeviceName.isNotBlank() && audioOutputInfo.outputDeviceName != "—") audioOutputInfo.outputDeviceName else "System Audio Endpoint"
                    SignalPathStep(
                        icon = Icons.Rounded.Speaker,
                        title = "Physical Output",
                        primary = devName,
                        secondary = "Android Audio HAL / Kernel routing",
                        statusColor = Color(0xFF38BDF8),
                        isLast = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Audio Analysis Card (Section A)
            Text(
                text = "AUDIO QUALITY ANALYSIS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = accent
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
                tintAlpha = 0.45f
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. Hi-Res Analysis Result
                    val (statusLabel, statusColor) = when (track.hiResStatus) {
                        HiResStatus.GENUINE -> "Genuine Hi-Res" to Color(0xFF10B981)
                        HiResStatus.LIKELY_LOSSY -> "Likely Lossy Source" to Color(0xFFF59E0B)
                        HiResStatus.LIKELY_UPSAMPLED -> "Likely Upsampled" to Color(0xFFEF4444)
                        HiResStatus.UNKNOWN, null -> "Analysis Pending" to Color.White.copy(alpha = 0.5f)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spectral Purity",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    if (track.hiResConfidence != null && track.hiResStatus != HiResStatus.UNKNOWN) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val cutoffText = if (track.hiResCutoffHz != null) "Cutoff: ~${track.hiResCutoffHz!! / 1000} kHz • " else ""
                        Text(
                            text = "$cutoffText${(track.hiResConfidence!! * 100).toInt()}% confidence estimate (FFT spectrum)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    )

                    // 2. Dynamic Range Meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dynamic Range",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                        val drText = if (track.dynamicRangeScore != null) "DR${track.dynamicRangeScore} (${track.dynamicRangeLabel ?: "standard"})" else "Pending"
                        val drColor = when {
                            track.dynamicRangeScore == null -> Color.White.copy(alpha = 0.5f)
                            track.dynamicRangeScore!! >= 12 -> Color(0xFF10B981)
                            track.dynamicRangeScore!! >= 8 -> Color(0xFF38BDF8)
                            else -> Color(0xFFF59E0B)
                        }
                        Text(
                            text = drText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = drColor
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    )

                    // 3. Integrated Loudness (EBU R128)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Integrated Loudness",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                        val lufsText = if (track.loudnessLufs != null) "%.1f LUFS".format(track.loudnessLufs) else "Pending"
                        Text(
                            text = lufsText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onAnalyzeSong?.invoke(track) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = accent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing Song Quality...", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (track.hiResStatus != null && track.hiResStatus != HiResStatus.UNKNOWN) "Re-Analyze This Song" else "Analyze This Song",
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Technical Specifications
            Text(
                text = "TECHNICAL SPECIFICATIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = accent
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
                tintAlpha = 0.35f
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SpecRow(Icons.Rounded.AudioFile, "Source Format", track.formatBadgeText.ifBlank { "—" })
                    if (track.isLossless && track.bitDepth > 0) {
                        SpecRow(Icons.Rounded.Layers, "Bit Depth", "${track.bitDepth}-bit")
                    }
                    if (track.bitrateKbps > 0) {
                        SpecRow(Icons.Rounded.Speed, "Bitrate", "${track.bitrateKbps} kbps")
                    }
                    SpecRow(Icons.Rounded.GraphicEq, "Sample Rate", if (track.sampleRate > 0) "${track.sampleRate} Hz (${if (track.isHiRes) "Hi-Res" else "Standard"})" else "—")
                    val outEnc = if (audioOutputInfo.isTrackInitialized && audioOutputInfo.format.isNotBlank() && audioOutputInfo.format != "—") {
                        "${audioOutputInfo.format} • ${audioOutputInfo.sampleRate} Hz"
                    } else "—"
                    SpecRow(Icons.Rounded.VolumeUp, "Media3 Output", outEnc)
                    val devName = if (audioOutputInfo.outputDeviceName.isNotBlank() && audioOutputInfo.outputDeviceName != "—") audioOutputInfo.outputDeviceName else "—"
                    SpecRow(Icons.Rounded.Speaker, "Output Device", devName)
                    SpecRow(Icons.Rounded.Timer, "Duration", track.durationFormatted)
                    SpecRow(Icons.Rounded.Storage, "File Size", "%.2f MB".format(track.size / (1024.0 * 1024.0)))
                    SpecRow(Icons.Rounded.PlayArrow, "Play Count", "${track.playCount} plays")

                    if (track.lastPlayedTimestamp > 0L) {
                        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(track.lastPlayedTimestamp))
                        SpecRow(Icons.Rounded.History, "Last Played", dateStr)
                    }

                    SpecRow(Icons.Rounded.FolderOpen, "Path", track.path, isLast = true)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SpecRow(
    icon: ImageVector,
    label: String,
    value: String,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.6f)
            ),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.weight(1f)
        )
    }
    if (!isLast) {
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun SignalPathStep(
    icon: ImageVector,
    title: String,
    primary: String,
    secondary: String,
    statusColor: Color,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Text(
                text = primary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1
            )
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SignalPathConnector() {
    Box(
        modifier = Modifier
            .padding(start = 17.dp, top = 2.dp, bottom = 2.dp)
            .width(2.dp)
            .height(14.dp)
            .background(Color.White.copy(alpha = 0.15f))
    )
}
