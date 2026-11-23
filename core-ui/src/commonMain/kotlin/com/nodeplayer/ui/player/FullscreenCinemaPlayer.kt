package com.nodeplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.model.video.VideoMath
import kotlinx.coroutines.delay

/**
 * Immersive Fullscreen Cinema Video Player with liquid-glass overlay controls,
 * 10s double-tap seeking with animated ripple badges, and dual-zone vertical drag
 * gestures for screen brightness (left half) and system volume (right half).
 */
@Composable
fun FullscreenCinemaPlayer(
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    currentHeight: Int? = null,
    availableHeights: List<Int> = emptyList(),
    seekStepSec: Int = 10,
    controlsAutoHideSec: Int = 4,
    brightnessLevel: Float = 0.5f,
    volumeLevel: Float = 0.75f,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onSelectQuality: (Int?) -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    videoContent: (@Composable (Modifier) -> Unit),
    modifier: Modifier = Modifier
) {
    var showControls by remember { mutableStateOf(true) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var transientSeekBadge by remember { mutableStateOf<String?>(null) }
    var transientGestureZone by remember { mutableStateOf<String?>(null) } // "Brightness" or "Volume"

    // Reset controls auto-hide timer whenever user interacts
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(controlsAutoHideSec * 1000L)
            if (!showQualityMenu) {
                showControls = false
            }
        }
    }

    // Auto-clear seek badge
    LaunchedEffect(transientSeekBadge) {
        if (transientSeekBadge != null) {
            delay(900L)
            transientSeekBadge = null
        }
    }

    // Auto-clear gesture badge
    LaunchedEffect(transientGestureZone) {
        if (transientGestureZone != null) {
            delay(1200L)
            transientGestureZone = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Live Video Surface
        videoContent(Modifier.fillMaxSize())

        // 2. Gesture Detector Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            showQualityMenu = false
                        },
                        onDoubleTap = { offset ->
                            val delta = VideoMath.resolveDoubleTapSeekDeltaMs(offset.x, size.width.toFloat(), seekStepSec)
                            val target = (positionMs + delta).coerceIn(0L, durationMs.coerceAtLeast(1L))
                            onSeek(target)
                            transientSeekBadge = if (delta < 0) "-${seekStepSec}s" else "+${seekStepSec}s"
                        }
                    )
                }
                .pointerInput(Unit) {
                    var startX = 0f
                    detectVerticalDragGestures(
                        onDragStart = { startX = it.x },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / size.height.toFloat()
                            val isLeft = startX < size.width / 2f
                            if (isLeft) {
                                onBrightnessDelta(delta)
                                transientGestureZone = "Brightness: ${(brightnessLevel * 100).toInt()}%"
                            } else {
                                onVolumeDelta(delta)
                                transientGestureZone = "Volume: ${(volumeLevel * 100).toInt()}%"
                            }
                        }
                    )
                }
        )

        // 3. Transient Gesture and Seek Feedback Badges
        if (transientSeekBadge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    text = transientSeekBadge!!,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (transientGestureZone != null && transientSeekBadge == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = transientGestureZone!!,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // 4. Liquid Glass Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .padding(horizontal = 28.dp, vertical = 20.dp)
            ) {
                // Top Header: Back Button, Title/Artist, Quality Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Close Cinema",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentTrack?.title ?: "Music Video",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack?.artist ?: "",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }

                    // Quality Selector Badge Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { showQualityMenu = !showQualityMenu }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${currentHeight ?: 480}p",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Center Play/Pause Button
                Box(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Timeline & Scrubber Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val posSec = positionMs / 1000
                    val durSec = durationMs / 1000
                    val posFormatted = "${posSec / 60}:${(posSec % 60).toString().padStart(2, '0')}"
                    val durFormatted = "${durSec / 60}:${(durSec % 60).toString().padStart(2, '0')}"

                    Text(
                        text = posFormatted,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Scrubber progress bar
                    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = durFormatted,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Quality Dropdown Glass Menu
                if (showQualityMenu) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1C1D24).copy(alpha = 0.95f))
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = "Auto",
                            fontSize = 13.sp,
                            color = if (currentHeight == null) Color.Cyan else Color.White,
                            fontWeight = if (currentHeight == null) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectQuality(null)
                                    showQualityMenu = false
                                }
                                .padding(vertical = 6.dp)
                        )
                        val heights = if (availableHeights.isNotEmpty()) availableHeights else listOf(1080, 720, 480, 360)
                        heights.forEach { h ->
                            Text(
                                text = "${h}p",
                                fontSize = 13.sp,
                                color = if (currentHeight == h) Color.Cyan else Color.White,
                                fontWeight = if (currentHeight == h) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectQuality(h)
                                        showQualityMenu = false
                                    }
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
