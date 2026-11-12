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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.LyricsLine
import kotlin.math.abs
import kotlin.math.floor

private val StartCenterOrigin = TransformOrigin(0f, 0.5f)

private val BaseLyricTextStyle = TextStyle(
    fontSize = 28.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 35.sp,
    textAlign = TextAlign.Start
)

/** Opacity of the not-yet-sung part of the active line (the semi-translucent white of Apple Music). */
private const val UNSUNG_ALPHA = 0.35f

/** Width of the soft edge that travels across the active line. */
private val FeatherWidth = 22.dp

/**
 * Single lyric line item.
 *
 * Active line (Apple Music style): the whole line is drawn as dim translucent white, and a bright
 * copy of the same text is revealed on top of it by a feathered mask that glides continuously across
 * the text, following wrapped lines. The mask is drawn into an offscreen layer with
 * [BlendMode.DstIn], so nothing outside the glyphs is ever painted (no black boxes).
 *
 * Other lines: alpha / scale / blur ladder by distance from the active line.
 *
 * [positionProvider] is optional; when given, the sweep reads it in the draw phase so it stays
 * smooth between recompositions. Otherwise [currentPositionMs] is used.
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
    activeHighlightColor: Color = Color.White,
    positionProvider: (() -> Long)? = null
) {
    val distance = abs(index - activeIndex)
    val isActive = distance == 0

    val targetAlpha = when (distance) {
        0 -> 1.0f
        1 -> 0.58f
        2 -> 0.40f
        else -> 0.28f
    }
    val targetScale = when (distance) {
        0 -> 1.00f
        1 -> 0.94f
        2 -> 0.90f
        else -> 0.88f
    }
    val blurRadius = when (distance) {
        0 -> 0.dp
        1 -> 2.dp
        else -> 4.dp
    }

    val animatedAlpha = remember { Animatable(targetAlpha) }
    val animatedScale = remember { Animatable(targetScale) }

    LaunchedEffect(targetAlpha) {
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
        if (isActive) {
            ActiveSweepText(
                line = line,
                currentPositionMs = currentPositionMs,
                positionProvider = positionProvider,
                highlightColor = activeHighlightColor
            )
        } else {
            Text(
                text = line.text,
                style = BaseLyricTextStyle.copy(color = baseColor),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActiveSweepText(
    line: LyricsLine,
    currentPositionMs: Long,
    positionProvider: (() -> Long)?,
    highlightColor: Color
) {
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Unsung layer: semi-translucent white.
        Text(
            text = line.text,
            style = BaseLyricTextStyle.copy(color = highlightColor.copy(alpha = UNSUNG_ALPHA)),
            modifier = Modifier.fillMaxWidth()
        )

        // Sung layer: same text, bright, revealed by the feathered mask.
        Text(
            text = line.text,
            style = BaseLyricTextStyle.copy(color = highlightColor),
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val result = layout ?: return@drawWithContent
                    val position = positionProvider?.invoke() ?: currentPositionMs
                    val chars = sungCharPosition(line, position)
                    drawSweepMask(result, chars, line.text.length, FeatherWidth.toPx())
                }
        )
    }
}

/** Multiplies the already drawn bright text by the sweep mask (DstIn keeps only masked pixels). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSweepMask(
    layout: TextLayoutResult,
    sungChars: Float,
    textLength: Int,
    feather: Float
) {
    val width = size.width
    if (width <= 0f || layout.lineCount == 0) return

    val allSung = sungChars >= textLength
    val currentLine = if (allSung) layout.lineCount else {
        var found = layout.lineCount - 1
        for (i in 0 until layout.lineCount) {
            if (sungChars < layout.getLineEnd(i)) { found = i; break }
        }
        found
    }

    for (i in 0 until layout.lineCount) {
        val top = layout.getLineTop(i)
        val rectSize = Size(width, layout.getLineBottom(i) - top)
        when {
            i < currentLine -> drawRect(Color.Black, Offset(0f, top), rectSize, blendMode = BlendMode.DstIn)
            i > currentLine -> drawRect(Color.Transparent, Offset(0f, top), rectSize, blendMode = BlendMode.DstIn)
            else -> {
                val lineStart = layout.getLineStart(i)
                val lineEnd = layout.getLineEnd(i)
                val left = layout.getLineLeft(i)
                val right = layout.getLineRight(i)
                val lo = floor(sungChars).toInt().coerceIn(lineStart, lineEnd)
                val hi = (lo + 1).coerceAtMost(lineEnd)
                val xLo = layout.getHorizontalPosition(lo, true)
                val xHi = layout.getHorizontalPosition(hi, true)
                val x = xLo + (xHi - xLo) * (sungChars - lo).coerceIn(0f, 1f)
                val span = (right - left).coerceAtLeast(1f)
                val lineFraction = ((x - left) / span).coerceIn(0f, 1f)
                // The soft edge starts fully off the left of the line and ends fully off its right.
                val edge = left + (span + feather) * lineFraction
                val brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    ((edge - feather) / width).coerceIn(0f, 1f) to Color.Black,
                    (edge / width).coerceIn(0f, 1f) to Color.Transparent,
                    1f to Color.Transparent,
                    startX = 0f,
                    endX = width
                )
                drawRect(brush, Offset(0f, top), rectSize, blendMode = BlendMode.DstIn)
            }
        }
    }
}

/**
 * How many characters of [LyricsLine.text] have been sung at [positionMs], as a fraction-aware
 * position. Uses word timings when present (text is the words joined by one space), otherwise
 * spreads the line's duration evenly over its characters.
 */
private fun sungCharPosition(line: LyricsLine, positionMs: Long): Float {
    val words = line.words
    if (words.isEmpty()) {
        val end = line.endTimeMs?.takeIf { it > line.timestampMs } ?: (line.timestampMs + 3500L)
        val fraction = ((positionMs - line.timestampMs).toFloat() / (end - line.timestampMs).toFloat())
            .coerceIn(0f, 1f)
        return fraction * line.text.length
    }
    var offset = 0
    var result = 0f
    for (word in words) {
        val length = word.text.length
        when {
            positionMs >= word.endMs -> result = (offset + length).toFloat()
            positionMs > word.startMs -> {
                val duration = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
                return offset + length * ((positionMs - word.startMs) / duration).coerceIn(0f, 1f)
            }
            else -> return result
        }
        offset += length + 1
    }
    return result
}
