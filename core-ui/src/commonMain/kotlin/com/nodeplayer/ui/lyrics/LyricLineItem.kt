package com.nodeplayer.ui.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.LyricsLine
import kotlin.math.abs

private val StartCenterOrigin = TransformOrigin(0f, 0.5f)

private val BaseLyricTextStyle = TextStyle(
    fontSize = 28.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 35.sp,
    textAlign = TextAlign.Start
)

/**
 * Single lyric line item.
 * Preserves Display typography (28sp, weight 600, lineHeight 35sp) across all lines without recomposing.
 * Supports active word-by-word horizontal gradient sweep fill (Apple Music style) when distance == 0.
 * Modulates alpha, scale, and subtle blur via graphicsLayer based on line distance from active.
 */
@Composable
fun LyricLineItem(
    line: LyricsLine,
    index: Int,
    activeIndex: Int,
    currentPositionMs: Long,
    baseColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeHighlightColor: Color = Color.White
) {
    val distance = abs(index - activeIndex)
    val isActive = distance == 0

    // Alpha ladder based on UI-SPEC:
    // Active: 1.0f
    // Distance 1: 0.58f
    // Distance 2: 0.40f
    // Distance >= 3: 0.28f
    val targetAlpha = when (distance) {
        0 -> 1.0f
        1 -> 0.58f
        2 -> 0.40f
        else -> 0.28f
    }

    // Scale ladder:
    // Active: 1.00f
    // Inactive: 0.94f for dist 1, 0.90f for dist 2, 0.88f for dist >= 3
    val targetScale = when (distance) {
        0 -> 1.00f
        1 -> 0.94f
        2 -> 0.90f
        else -> 0.88f
    }

    // Blur ladder:
    // Active: 0dp
    // Distance 1: 2dp
    // Distance >= 2: 4dp
    val blurRadius = when (distance) {
        0 -> 0.dp
        1 -> 2.dp
        else -> 4.dp
    }

    val animatedAlpha = remember { Animatable(targetAlpha) }
    val animatedScale = remember { Animatable(targetScale) }

    LaunchedEffect(targetAlpha, targetScale) {
        animatedAlpha.animateTo(targetAlpha, animationSpec = tween(durationMillis = 260))
    }
    LaunchedEffect(targetScale) {
        animatedScale.animateTo(targetScale, animationSpec = tween(durationMillis = 260))
    }

    val interactionSource = remember { MutableInteractionSource() }

    // Line progress for gradient sweep
    val lineEndTime = line.endTimeMs
    val lineDuration = if (lineEndTime != null && lineEndTime > line.timestampMs) {
        lineEndTime - line.timestampMs
    } else {
        3500L
    }

    val lineProgress = if (isActive && currentPositionMs >= line.timestampMs) {
        ((currentPositionMs - line.timestampMs).toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
    } else if (isActive && currentPositionMs < line.timestampMs) {
        0f
    } else {
        1f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                this.alpha = animatedAlpha.value
                transformOrigin = StartCenterOrigin
            }
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onSeek(line.timestampMs)
            }
            .padding(vertical = 6.dp)
    ) {
        if (isActive && lineProgress in 0.001f..0.999f) {
            // Active line horizontal sweep gradient brush
            val sweepBrush = Brush.horizontalGradient(
                0.0f to activeHighlightColor,
                (lineProgress - 0.04f).coerceAtLeast(0f) to activeHighlightColor,
                (lineProgress + 0.04f).coerceAtMost(1f) to baseColor.copy(alpha = 0.40f),
                1.0f to baseColor.copy(alpha = 0.40f)
            )

            Text(
                text = line.text,
                style = BaseLyricTextStyle.copy(brush = sweepBrush)
            )
        } else {
            val textColor = if (isActive) activeHighlightColor else baseColor
            Text(
                text = line.text,
                style = BaseLyricTextStyle.copy(color = textColor)
            )
        }
    }
}
