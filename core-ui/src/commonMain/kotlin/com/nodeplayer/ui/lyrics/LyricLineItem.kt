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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.LyricsLine
import kotlin.math.abs

/**
 * Single lyric line item.
 * Preserves Display typography (28sp, weight 600, lineHeight 1.25) across all lines without recomposing.
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
    modifier: Modifier = Modifier
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                this.alpha = animatedAlpha.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
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
        Text(
            text = line.text,
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 35.sp,
                color = baseColor,
                textAlign = TextAlign.Start
            )
        )
    }
}
