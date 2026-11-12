package com.nodeplayer.ui.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.LyricAlignment
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.LyricsLine
import kotlin.math.abs

private val WIPE_FEATHER = 18.dp

private fun TextLayoutResult.xOn(offset: Int, visualLine: Int, inset: Float = 0f): Float {
    val lineStart = getLineStart(visualLine)
    val lineEndVisible = getLineEnd(visualLine, visibleEnd = true)
    val lineLeft = getLineLeft(visualLine) + inset
    val lineRight = getLineRight(visualLine) + inset

    val rtl = getParagraphDirection(lineStart.coerceIn(0, (layoutInput.text.length - 1).coerceAtLeast(0))) ==
              ResolvedTextDirection.Rtl

    return when {
        offset <= lineStart -> if (rtl) lineRight else lineLeft
        offset >= lineEndVisible -> if (rtl) lineLeft else lineRight
        else -> (getHorizontalPosition(offset, usePrimaryDirection = true) + inset)
            .coerceIn(lineLeft, lineRight)
    }
}

private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    visualLine: Int,
    spans: List<IntRange>,
): Float {
    val lineStart = layout.getLineStart(visualLine)
    val lineEndVisible = layout.getLineEnd(visualLine, visibleEnd = true)
    val lineEndAll = layout.getLineEnd(visualLine, visibleEnd = false)
    val word = spans.firstOrNull { chars >= it.first && chars < it.last + 1 }
    if (word != null && word.first >= lineStart && word.last + 1 <= lineEndVisible) {
        val from = layout.xOn(word.first, visualLine, 0f)
        val to = layout.xOn(word.last + 1, visualLine, 0f)
        return from + (to - from) * (chars - word.first) / (word.last + 1 - word.first)
    }
    val index = chars.toInt().coerceIn(lineStart, lineEndAll)
    val here = layout.xOn(index, visualLine, 0f)
    val rawNext = index + 1
    val textLen = layout.layoutInput.text.length
    val spills: Boolean = layout.getLineForOffset(rawNext.coerceIn(0, textLen)) != visualLine
    val next: Float = if (spills) {
        if (rawNext > lineEndVisible) {
            val rtl = layout.getParagraphDirection(
                visualLine.coerceAtMost(lineEndAll).coerceIn(0, (textLen - 1).coerceAtLeast(0))
            ) == ResolvedTextDirection.Rtl
            val farLeft = layout.getLineLeft(visualLine)
            val farRight = layout.getLineRight(visualLine)
            if (rtl) farLeft else farRight
        } else {
            layout.xOn(rawNext.coerceAtMost(lineEndVisible), visualLine, 0f)
        }
    } else {
        layout.xOn(rawNext, visualLine, 0f)
    }
    return here + (next - here) * (chars - index)
}

private fun ContentDrawScope.sweepText(
    layout: TextLayoutResult,
    revealedChars: Float,
    spans: List<IntRange>,
    feather: Boolean = true,
) {
    if (revealedChars <= 0f) return
    val textLen = layout.layoutInput.text.length
    if (revealedChars >= textLen) {
        drawContent()
        return
    }

    val offset = layout.getLineStart(0).coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
    val rtl = layout.getParagraphDirection(offset) == ResolvedTextDirection.Rtl

    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEndVisible = layout.getLineEnd(visualLine, visibleEnd = true)
        val lineEndAll = layout.getLineEnd(visualLine, visibleEnd = false)

        if (revealedChars <= lineStart) return

        val cut = revealedChars < lineEndAll
        val charPos = if (cut) {
            horizontalAt(layout, revealedChars, visualLine, spans)
        } else {
            if (rtl) layout.getLineLeft(visualLine) else layout.getLineRight(visualLine)
        }

        val lineLeft = layout.getLineLeft(visualLine)
        val lineRight = layout.getLineRight(visualLine)
        val safeCharPos = charPos.coerceIn(lineLeft, lineRight)

        val featherPx = if (feather && cut) {
            minOf(
                WIPE_FEATHER.toPx(),
                if (rtl) lineRight - safeCharPos else safeCharPos - lineLeft,
            )
        } else {
            0f
        }
        val lead = if (rtl) {
            (safeCharPos - featherPx).coerceAtLeast(lineLeft)
        } else {
            (safeCharPos + featherPx).coerceAtMost(lineRight)
        }

        val (clipLeft, clipRight) = if (rtl) {
            lead to lineRight
        } else {
            lineLeft to lead
        }

        if (clipLeft >= clipRight) continue

        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        clipRect(
            left = clipLeft,
            top = top,
            right = clipRight,
            bottom = bottom,
        ) {
            this@sweepText.drawContent()
        }

        if (featherPx >= 0.5f) {
            clipRect(top = top, bottom = bottom) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White,
                        1f to Color.Transparent,
                        startX = safeCharPos,
                        endX = if (rtl) safeCharPos - featherPx else safeCharPos + featherPx,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
}

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
     * Alpha ladder for distance from active line conforming to D-11:
     * - Active: 1.0f
     * - Distance 1: 0.58f
     * - Distance 2: 0.40f
     * - Distance >= 3: 0.28f (overall baseline ~0.35f)
     */
    fun alphaForDistance(distance: Int): Float = when (distance) {
        0 -> 1.0f
        1 -> 0.58f
        2 -> 0.40f
        else -> 0.28f
    }

    /**
     * Scale factor for distance from active line:
     * - Active: 1.0f
     * - Distance 1: 0.94f
     * - Distance 2: 0.90f
     * - Distance >= 3: 0.88f
     */
    fun scaleForDistance(distance: Int): Float = when (distance) {
        0 -> 1.0f
        1 -> 0.94f
        2 -> 0.90f
        else -> 0.88f
    }

    /**
     * Blur radius for distance from active line.
     */
    fun blurForDistance(distance: Int): Dp = when (distance) {
        0 -> 0.dp
        1 -> 1.5.dp
        2 -> 3.0.dp
        else -> 4.5.dp
    }
}

private val StartCenterOrigin = TransformOrigin(0f, 0.5f)
private val EndCenterOrigin = TransformOrigin(1f, 0.5f)

/**
 * Karaoke line item rendering Apple Music-style ExtraBold typography with high-contrast active line
 * and word-by-word / smooth syllable clip sweep fill without offscreen GPU RenderTargets or per-line blur.
 *
 * Conforms to D-11, D-13, and D-14:
 * - Active text base in Pure White (#FFFFFF) with glowing accent color sweep.
 * - Inactive lines smoothly dimmed via alpha ladder down to 0.28f (baseline ~0.35f).
 * - Duet alignment: [LyricAlignment.Start] vs [LyricAlignment.End].
 * - Backing vocal subtext [LyricsLine.background] rendered under lead line with independent sweep.
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
    accentSweepColor: Color = activeColor,
    fontSize: TextUnit = 34.sp,
    positionProvider: () -> Long = { currentPositionMs }
) {
    val distance = abs(index - activeIndex)
    val isActive = distance == 0

    val targetAlpha = KaraokeProgressMath.alphaForDistance(distance)
    val targetScale = KaraokeProgressMath.scaleForDistance(distance)

    val animatedAlpha = remember { Animatable(targetAlpha) }
    val animatedScale = remember { Animatable(targetScale) }

    LaunchedEffect(targetAlpha) {
        animatedAlpha.animateTo(targetAlpha, animationSpec = tween(durationMillis = 240))
    }
    LaunchedEffect(targetScale) {
        animatedScale.animateTo(targetScale, animationSpec = tween(durationMillis = 240))
    }

    val interactionSource = remember { MutableInteractionSource() }
    val currentPositionProvider by rememberUpdatedState(positionProvider)

    val isEndAligned = line.alignment == LyricAlignment.End
    val textAlign = if (isEndAligned) TextAlign.End else TextAlign.Start
    val boxAlignment = if (isEndAligned) Alignment.CenterEnd else Alignment.CenterStart
    val colAlignment = if (isEndAligned) Alignment.End else Alignment.Start
    val transformOrigin = if (isEndAligned) EndCenterOrigin else StartCenterOrigin

    val textStyle = remember(fontSize, isActive, textAlign) {
        TextStyle(
            fontSize = fontSize,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
            lineHeight = (fontSize.value * 1.22f).sp,
            textAlign = textAlign
        )
    }

    // Base color for active text is pure white with subtle opacity; inactive uses dimmed palette
    val activeBaseColor = remember(activeColor) { activeColor.copy(alpha = 0.35f) }
    val dimmedInactiveColor = remember(inactiveColor) { inactiveColor.copy(alpha = 0.65f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                this.alpha = animatedAlpha.value
                this.transformOrigin = transformOrigin
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onSeek(line.timestampMs)
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = colAlignment
    ) {
        // Main vocal line
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = boxAlignment
        ) {
            if (isActive) {
                var textLayout by remember(line.text) { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = boxAlignment
                ) {
                    Text(
                        text = line.text,
                        style = textStyle,
                        color = activeBaseColor,
                        onTextLayout = { textLayout = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = line.text,
                        style = textStyle,
                        color = if (accentSweepColor != Color.Unspecified) accentSweepColor else activeColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            // Offscreen layer: the feather uses BlendMode.DstIn, which must only
                            // affect this text, not the backdrop (that was the black bar).
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                val layout = textLayout
                                val pos = currentPositionProvider()
                                val revealed = line.revealedChars(pos)
                                if (layout != null && revealed > 0f) {
                                    sweepText(layout, revealed, line.sweepSpans)
                                } else if (revealed >= line.text.length) {
                                    drawContent()
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

        // Backing vocal subtext (LyricsLine.background / x-bg role)
        val bgLine = line.background
        if (bgLine != null) {
            val bgTextStyle = remember(fontSize, isActive, textAlign) {
                TextStyle(
                    fontSize = (fontSize.value * 0.62f).coerceAtLeast(18f).sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    lineHeight = ((fontSize.value * 0.62f).coerceAtLeast(18f) * 1.25f).sp,
                    textAlign = textAlign
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = boxAlignment
            ) {
            if (isActive) {
                var bgTextLayout by remember(bgLine.text) { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = boxAlignment
                ) {
                    Text(
                        text = bgLine.text,
                        style = bgTextStyle,
                        color = activeColor.copy(alpha = 0.25f),
                        onTextLayout = { bgTextLayout = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = bgLine.text,
                        style = bgTextStyle,
                        color = (if (accentSweepColor != Color.Unspecified) accentSweepColor else activeColor).copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                val layout = bgTextLayout
                                val pos = currentPositionProvider()
                                val revealed = bgLine.revealedChars(pos)
                                if (layout != null && revealed > 0f) {
                                    sweepText(layout, revealed, bgLine.sweepSpans)
                                } else if (revealed >= bgLine.text.length) {
                                    drawContent()
                                }
                            }
                    )
                }
            } else {
                    Text(
                        text = bgLine.text,
                        style = bgTextStyle,
                        color = dimmedInactiveColor.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
