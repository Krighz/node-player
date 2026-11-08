package com.nodeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle

private enum class BadgeStyle {
    YOUTUBE_STREAM,
    HI_RES_LOSSLESS,
    STANDARD_LOSSLESS,
    LOSSY
}

private data class BadgeData(
    val style: BadgeStyle,
    val mainText: String,
    val subText: String? = null
)

private fun resolveBadgeData(track: Track): BadgeData {
    val isYouTube = track.path.startsWith("nodeplayer://youtube/") ||
            track.path.contains("googlevideo.com") ||
            track.path.startsWith("https://rr") ||
            track.mimeType.contains("youtube", ignoreCase = true)

    if (isYouTube) {
        val isOpus = track.mimeType.contains("opus", ignoreCase = true) ||
                track.path.contains("itag=251") ||
                track.formatDescription.contains("opus", ignoreCase = true) ||
                track.bitrateKbps >= 150
        val label = if (isOpus) "OPUS • 160k" else "AAC • 128k"
        return BadgeData(
            style = BadgeStyle.YOUTUBE_STREAM,
            mainText = label
        )
    }

    // Local track checks
    val isHiRes = track.isHiRes || track.sampleRate > 48000 || track.bitDepth > 16
    if (isHiRes) {
        val sampleRateKhz = if (track.sampleRate % 1000 == 0) {
            "${track.sampleRate / 1000}kHz"
        } else {
            "${(track.sampleRate / 100) / 10.0}kHz"
        }
        val depthStr = if (track.bitDepth > 0) "${track.bitDepth}bit" else "24bit"
        return BadgeData(
            style = BadgeStyle.HI_RES_LOSSLESS,
            mainText = "HI-RES LOSSLESS",
            subText = "$sampleRateKhz / $depthStr"
        )
    }

    val isLossless = track.isLossless ||
            track.mimeType.contains("flac", ignoreCase = true) ||
            track.mimeType.contains("alac", ignoreCase = true) ||
            track.mimeType.contains("wav", ignoreCase = true) ||
            track.path.endsWith(".flac", ignoreCase = true) ||
            track.path.endsWith(".alac", ignoreCase = true) ||
            track.path.endsWith(".wav", ignoreCase = true)

    if (isLossless) {
        val fmt = when {
            track.mimeType.contains("alac", ignoreCase = true) || track.path.endsWith(".alac", ignoreCase = true) -> "ALAC"
            track.mimeType.contains("wav", ignoreCase = true) || track.path.endsWith(".wav", ignoreCase = true) -> "WAV"
            else -> "FLAC"
        }
        return BadgeData(
            style = BadgeStyle.STANDARD_LOSSLESS,
            mainText = "LOSSLESS",
            subText = "$fmt • 16/44.1"
        )
    }

    // Lossy audio (MP3, OGG, AAC, etc.)
    val fmt = when {
        track.mimeType.contains("mp3", ignoreCase = true) || track.path.endsWith(".mp3", ignoreCase = true) -> "MP3"
        track.mimeType.contains("ogg", ignoreCase = true) || track.path.endsWith(".ogg", ignoreCase = true) -> "OGG"
        track.mimeType.contains("aac", ignoreCase = true) || track.mimeType.contains("m4a", ignoreCase = true) || track.path.endsWith(".m4a", ignoreCase = true) || track.path.endsWith(".aac", ignoreCase = true) -> "AAC"
        track.mimeType.contains("opus", ignoreCase = true) || track.path.endsWith(".opus", ignoreCase = true) -> "OPUS"
        else -> "MP3"
    }
    val bitrateStr = if (track.bitrateKbps in 8..1411) {
        "${track.bitrateKbps}k"
    } else {
        "320k"
    }

    return BadgeData(
        style = BadgeStyle.LOSSY,
        mainText = "$fmt • $bitrateStr"
    )
}

/**
 * Dynamic Audio Quality & Format Badge.
 * Renders frosted optical glass pills with dynamic color accents:
 * - Cyan for YouTube Streaming (OPUS / AAC)
 * - Gold border for Hi-Res Audiophile Lossless (>48kHz or >16-bit)
 * - White frosted border for Standard Lossless (FLAC/ALAC 16-bit 44.1kHz)
 * - Monochrome pill for Lossy formats (MP3/OGG)
 */
@Composable
fun AudioFormatBadge(
    track: Track?,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    if (track == null) return

    val badgeData = remember(track.id, track.path, track.sampleRate, track.bitDepth, track.bitrateKbps, track.mimeType) {
        resolveBadgeData(track)
    }

    val shape = remember { ContinuousRoundedRectangle(if (isCompact) 4.dp else 6.dp) }

    when (badgeData.style) {
        BadgeStyle.YOUTUBE_STREAM -> {
            val cyanColor = Color(0xFF00E5FF)
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(Color(0xFF003840).copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                cyanColor.copy(alpha = 0.60f),
                                cyanColor.copy(alpha = 0.20f)
                            )
                        ),
                        shape = shape
                    )
                    .padding(
                        horizontal = if (isCompact) 5.dp else 8.dp,
                        vertical = if (isCompact) 1.5.dp else 3.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeData.mainText,
                    fontSize = if (isCompact) 9.sp else 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cyanColor,
                    maxLines = 1
                )
            }
        }
        BadgeStyle.HI_RES_LOSSLESS -> {
            val goldColor = Color(0xFFFFD700)
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(Color(0xFF261D00).copy(alpha = 0.65f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFECB3),
                                goldColor,
                                Color(0xFFB8860B).copy(alpha = 0.7f)
                            )
                        ),
                        shape = shape
                    )
                    .padding(
                        horizontal = if (isCompact) 5.dp else 8.dp,
                        vertical = if (isCompact) 1.5.dp else 3.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isCompact) "HI-RES" else badgeData.mainText,
                        fontSize = if (isCompact) 9.sp else 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = goldColor,
                        maxLines = 1
                    )
                    if (!isCompact && badgeData.subText != null) {
                        Text(
                            text = badgeData.subText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFE082).copy(alpha = 0.9f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        BadgeStyle.STANDARD_LOSSLESS -> {
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(Color(0xFF1E2333).copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        ),
                        shape = shape
                    )
                    .padding(
                        horizontal = if (isCompact) 5.dp else 8.dp,
                        vertical = if (isCompact) 1.5.dp else 3.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = badgeData.mainText,
                        fontSize = if (isCompact) 9.sp else 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 1
                    )
                    if (!isCompact && badgeData.subText != null) {
                        Text(
                            text = badgeData.subText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        BadgeStyle.LOSSY -> {
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = shape
                    )
                    .padding(
                        horizontal = if (isCompact) 5.dp else 7.dp,
                        vertical = if (isCompact) 1.5.dp else 2.5.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeData.mainText,
                    fontSize = if (isCompact) 8.5.sp else 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }
        }
    }
}
