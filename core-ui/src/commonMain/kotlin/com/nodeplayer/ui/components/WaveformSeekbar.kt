package com.nodeplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.ui.theme.LocalAccentColor

@Composable
fun WaveformSeekbar(
    positionMs: Long,
    durationMs: Long,
    peaks: List<Float>?, // Real cached peak data, or null
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val displayFraction = if (isDragging) dragFraction else progress
    val displayPositionMs = if (isDragging && durationMs > 0) (dragFraction * durationMs).toLong() else positionMs
    val remainingMs = if (durationMs > displayPositionMs) durationMs - displayPositionMs else 0L

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Elapsed Time
        Text(
            text = formatTime(displayPositionMs),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.width(36.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Center: Real Waveform Spectrum Bar Visualizer matching Reference Design
        val activePeaks = remember(peaks, durationMs) {
            if (!peaks.isNullOrEmpty()) {
                peaks
            } else {
                // Generate a natural acoustic frequency spectrum envelope matching reference UI
                val count = 72
                List(count) { i ->
                    val x = (i - count / 2f) / (count / 2.2f)
                    val gaussian = kotlin.math.exp(-2.2 * x * x).toFloat()
                    val harmonic = (0.20f * kotlin.math.sin(i * 0.42f) + 0.12f * kotlin.math.cos(i * 0.88f)).toFloat()
                    (gaussian * 0.88f + harmonic).coerceIn(0.14f, 1.0f)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onScrubberWheel { deltaY ->
                    val stepMs = 5000L
                    val newPos = if (deltaY < 0f) positionMs + stepMs else positionMs - stepMs
                    onSeek(newPos.coerceIn(0L, durationMs))
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((frac * durationMs).toLong())
                        }
                    }
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                isDragging = false
                                onSeek((dragFraction * durationMs).toLong())
                            },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, _ ->
                                change.consume()
                                dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        )
                    }
            ) {
                val barCount = activePeaks.size
                val totalWidth = size.width
                val barWidth = (totalWidth / barCount) * 0.65f
                val barSpacing = (totalWidth / barCount) * 0.35f
                val currentPlayheadX = displayFraction * totalWidth

                for (i in 0 until barCount) {
                    val barHeight = (activePeaks[i] * size.height).coerceAtLeast(3f)
                    val x = i * (barWidth + barSpacing)
                    val y = (size.height - barHeight) / 2f
                    val isPlayed = (x + barWidth / 2f) <= currentPlayheadX

                    drawRoundRect(
                        color = if (isPlayed) Color.White else Color.White.copy(alpha = 0.24f),
                        topLeft = Offset(x, y),
                        size = Size(barWidth.coerceAtLeast(1.5f), barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                // Playhead Thumb Indicator line
                drawLine(
                    color = Color.White,
                    start = Offset(currentPlayheadX, 0f),
                    end = Offset(currentPlayheadX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Remaining Time (e.g. -2:33)
        Text(
            text = "-${formatTime(remainingMs)}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.50f),
            modifier = Modifier.width(42.dp)
        )
    }
}
