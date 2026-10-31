package com.nodeplayer.music.ui.components

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
    maxHeight: Dp = 20.dp
) {
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
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.70f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(710, easing = LinearEasing), RepeatMode.Reverse),
        label = "h4"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val heights = if (isPlaying) listOf(h1, h2, h3, h4) else listOf(0.4f, 0.6f, 0.3f, 0.5f)
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(maxHeight * frac)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
