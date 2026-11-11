package com.nodeplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private class WaveMotionStates(
    val h1: State<Float>,
    val h2: State<Float>,
    val h3: State<Float>,
    val h4: State<Float>
)

@Composable
private fun rememberAnimatedWaveHeights(): WaveMotionStates {
    val transition = rememberInfiniteTransition(label = "WaveAnimation")
    val h1 = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 = transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(620, easing = LinearEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 = transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(540, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 = transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "h4"
    )
    return remember(h1, h2, h3, h4) { WaveMotionStates(h1, h2, h3, h4) }
}

@Composable
fun SoundWaveVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White,
    maxHeight: Dp = 20.dp,
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false,
    rmsProvider: () -> Float = { rmsLevel }
) {
    val currentRmsProvider by rememberUpdatedState(rmsProvider)
    val motion = if (!isPerformanceMode && isPlaying) rememberAnimatedWaveHeights() else null
    val activeColor = if (isPerformanceMode) barColor.copy(alpha = 0.35f) else barColor

    Canvas(
        modifier = modifier
            .width(19.5.dp)
            .height(maxHeight)
    ) {
        val barWidthPx = 3.dp.toPx()
        val gapPx = 2.5.dp.toPx()
        val cornerPx = 1.5.dp.toPx()
        val cornerRadius = CornerRadius(cornerPx, cornerPx)
        val minBarPx = if (isPlaying && !isPerformanceMode) 3.dp.toPx() else 4.dp.toPx()
        val maxBarPx = size.height

        if (motion == null) {
            val h = 4.dp.toPx().coerceAtMost(maxBarPx)
            val top = (maxBarPx - h) / 2f
            for (i in 0 until 4) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(i * (barWidthPx + gapPx), top),
                    size = Size(barWidthPx, h),
                    cornerRadius = cornerRadius
                )
            }
            return@Canvas
        }

        val rms = currentRmsProvider()
        val boost = if (rms > 0.01f) (1.0f + rms * 0.5f).coerceAtMost(1.3f) else 1.0f
        val fractions = floatArrayOf(motion.h1.value, motion.h2.value, motion.h3.value, motion.h4.value)
        for (i in 0 until 4) {
            val barH = (maxBarPx * (fractions[i] * boost)).coerceIn(minBarPx, maxBarPx)
            val top = (maxBarPx - barH) / 2f
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(i * (barWidthPx + gapPx), top),
                size = Size(barWidthPx, barH),
                cornerRadius = cornerRadius
            )
        }
    }
}
