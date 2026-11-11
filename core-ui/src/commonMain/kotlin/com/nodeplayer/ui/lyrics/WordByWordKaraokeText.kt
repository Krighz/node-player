package com.nodeplayer.ui.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.LyricsLine
import kotlin.math.abs

/**
 * Calculation helper for word-by-word or line-level sweep fill progress.
 */
object KaraokeProgressMath {
    /**
     * Calculates the horizontal sweep progress [0f..1f] for an active lyric line.
     */
    fun calculateLineSweepProgress(
        currentPositionMs: Long,
        timestampMs: Long,
        endTimeMs: Long?,
        words: List<LyricWord>
    ): Float {
        if (currentPositionMs < timestampMs) return 0f

        if (words.isNotEmpty()) {
            val lastWord = words.last()
            val totalStart = words.first().startMs.coerceAtMost(timestampMs)
            val totalEnd = (lastWord.endMs).coerceAtLeast(endTimeMs ?: (totalStart + 3000L))
            val lineDuration = (totalEnd - totalStart).coerceAtLeast(1L)

            if (currentPositionMs >= totalEnd) return 1f
            return ((currentPositionMs - totalStart).toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
        }

        val end = endTimeMs ?: (timestampMs + 3500L)
        val duration = (end - timestampMs).coerceAtLeast(1L)
        if (currentPositionMs < timestampMs) return 0f
        if (currentPositionMs >= end) return 1f
        return ((currentPositionMs - timestampMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Alpha ladder for distance from active line.
     */
    fun alphaForDistance(distance: Int): Float = when (distance) {
        0 -> 1.0f
        1 -> 0.55f
        2 -> 0.35f
        else -> 0.22f
    }

    /**
     * Scale factor for distance from active line.
     */
    fun scaleForDistance(distance: Int): Float = when (distance) {
        0 -> 1.0f
        1 -> 0.95f
        2 -> 0.91f
        else -> 0.88f
    }

    /**
     * Blur radius for distance from active line.
     */
    fun blurForDistance(distance: Int): Dp = when (distance) {
        0 -> 0.dp
        1 -> 1.5.dp
        2 -> 3.5.dp
        else -> 5.dp
    }
}

/**
 * Karaoke line item rendering large bold typography with high-contrast active line
 * and word-by-word / smooth clip sweep fill without offscreen GPU RenderTargets or per-line blur.
 */
@Composable
fun WordByWordKaraokeText(
    line: LyricsLine,
    index: Int,
    activeIndex: Int,
    currentPositionMs: Long = 0L,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color(0xFFC0C0C0),
    fontSize: TextUnit = 34.sp,
    positionProvider: () -> Long = { currentPositionMs }
) {
    val distance = abs(index - activeIndex)
    val isActive = distance == 0

    val targetAlpha = KaraokeProgressMath.alphaForDistance(distance)
    val targetScale = KaraokeProgressMath.scaleForDistance(distance)

    val interactionSource = remember { MutableInteractionSource() }
    val currentPositionProvider by rememberUpdatedState(positionProvider)

    val textStyle = remember(fontSize, isActive) {
        TextStyle(
            fontSize = fontSize,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            lineHeight = (fontSize.value * 1.25f).sp,
            textAlign = TextAlign.Start
        )
    }

    val activeBaseColor = remember(activeColor) { activeColor.copy(alpha = 0.45f) }
    val dimmedInactiveColor = remember(inactiveColor) { inactiveColor.copy(alpha = 0.70f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = targetScale
                scaleY = targetScale
                this.alpha = targetAlpha
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onSeek(line.timestampMs)
            }
            .padding(vertical = 10.dp)
    ) {
        if (isActive) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = line.text,
                    style = textStyle,
                    color = activeBaseColor,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = line.text,
                    style = textStyle,
                    color = activeColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            val pos = currentPositionProvider()
                            val sweepProgress = KaraokeProgressMath.calculateLineSweepProgress(
                                currentPositionMs = pos,
                                timestampMs = line.timestampMs,
                                endTimeMs = line.endTimeMs,
                                words = line.words
                            )
                            if (sweepProgress >= 1f) {
                                drawContent()
                            } else if (sweepProgress > 0f) {
                                clipRect(right = size.width * sweepProgress) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                )
            }
        } else {
            Text(
                text = line.text,
                style = textStyle,
                color = dimmedInactiveColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
