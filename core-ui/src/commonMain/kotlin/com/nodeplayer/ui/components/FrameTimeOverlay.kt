package com.nodeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight real-time frame time overlay for performance profiling.
 * Measures render cadence and displays FPS and average ms per frame.
 */
@Composable
fun FrameTimeOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return

    var fps by remember { mutableIntStateOf(60) }
    var frameTimeMs by remember { mutableFloatStateOf(16.6f) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFpsTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var previousFrameTime = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (previousFrameTime != 0L) {
                    val frameDeltaMs = (frameTimeNanos - previousFrameTime) / 1_000_000f
                    frameTimeMs = (frameTimeMs * 0.9f + frameDeltaMs * 0.1f)
                    frameCount++
                }
                previousFrameTime = frameTimeNanos

                if (lastFpsTimestamp == 0L) {
                    lastFpsTimestamp = frameTimeNanos
                } else if (frameTimeNanos - lastFpsTimestamp >= 1_000_000_000L) {
                    fps = frameCount
                    frameCount = 0
                    lastFpsTimestamp = frameTimeNanos
                }
            }
        }
    }

    Box(
        modifier = modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val color = if (fps >= 55) Color(0xFF4CAF50) else if (fps >= 35) Color(0xFFFFC107) else Color(0xFFFF5252)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${fps} FPS",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${(frameTimeMs * 10).toInt() / 10f} ms",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
