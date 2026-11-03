package com.nodeplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SoundWaveVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White,
    maxHeight: Dp = 20.dp,
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false
) {
    if (isPerformanceMode) {
        Row(
            modifier = modifier.height(maxHeight),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(barColor.copy(alpha = 0.35f))
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "WaveAnimation")

    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(620, easing = LinearEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(540, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "h4"
    )

    val boost = if (rmsLevel > 0.01f) (1.0f + rmsLevel * 0.5f).coerceAtMost(1.3f) else 1.0f

    val heights = if (isPlaying) {
        listOf(
            (maxHeight * (h1 * boost)).coerceIn(3.dp, maxHeight),
            (maxHeight * (h2 * boost)).coerceIn(3.dp, maxHeight),
            (maxHeight * (h3 * boost)).coerceIn(3.dp, maxHeight),
            (maxHeight * (h4 * boost)).coerceIn(3.dp, maxHeight)
        )
    } else {
        listOf(4.dp, 4.dp, 4.dp, 4.dp)
    }

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}
