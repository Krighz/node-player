package com.nodeplayer.ui.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily

val LocalLyricsFontFamily = compositionLocalOf<FontFamily?> { null }

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
    spans: List<IntRange>
) {
    if (revealedChars <= 0f) return
    val textLen = layout.layoutInput.text.length
    if (revealedChars >= textLen) {
        drawContent()
        return
    }

    val rtl = layout.getParagraphDirection(0) == ResolvedTextDirection.Rtl

    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEndVisible = layout.getLineEnd(visualLine, visibleEnd = true)
        val lineEndAll = layout.getLineEnd(visualLine, visibleEnd = false)

        if (revealedChars <= lineStart) return

        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        val lineLeft = layout.getLineLeft(visualLine)
        val lineRight = layout.getLineRight(visualLine)

        if (revealedChars >= lineEndAll) {
            // Entire visual line is sung: draw full line
            clipRect(left = lineLeft, top = top, right = lineRight, bottom = bottom) {
                this@sweepText.drawContent()
            }
        } else {
            // Fractional syllable cut on this line
            val charPos = horizontalAt(layout, revealedChars, visualLine, spans)
            val safeCut = charPos.coerceIn(lineLeft, lineRight)
            val (clipLeft, clipRight) = if (rtl) safeCut to lineRight else lineLeft to safeCut

            if (clipLeft < clipRight) {
                clipRect(left = clipLeft, top = top, right = clipRight, bottom = bottom) {
                    this@sweepText.drawContent()
                }
            }
        }
    }
}

/**
 * Calculation helper for word-by-word or line-level sweep fill progress.
 */
object KaraokeProgressMath {
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

    fun alphaForDistance(distance: Int): Float = when (distance) {
        0 -> 1.0f
        1 -> 0.58f
        2 -> 0.40f
        else -> 0.28f
    }

    fun scaleForDistance(distance: Int): Float = when (distance) {
        0 -> 1.0f
        1 -> 0.94f
        2 -> 0.90f
        else -> 0.88f
    }

    fun blurForDistance(distance: Int): Dp = when (distance) {
        0 -> 0.dp
        1 -> 1.5.dp
        2 -> 3.0.dp
        else -> 4.5.dp
    }
}

private val LyricsLine.sweepSpans: List<IntRange>
    get() {
        if (words.isEmpty()) return emptyList()
        val spans = ArrayList<IntRange>(words.size)
        var offset = 0
        for (word in words) {
            val length = word.text.length
            if (length > 0) spans += offset until (offset + length)
            offset += length + 1
        }
        return spans
    }

private fun LyricsLine.revealedChars(positionMs: Long): Float {
    if (positionMs < timestampMs) return 0f
    if (words.isEmpty()) {
        val end = endTimeMs?.takeIf { it > timestampMs } ?: (timestampMs + 3500L)
        if (positionMs >= end) return text.length.toFloat()
        val fraction = (positionMs - timestampMs).toFloat() / (end - timestampMs).toFloat()
        return text.length * fraction.coerceIn(0f, 1f)
    }
    var offset = 0
    for (word in words) {
        val length = word.text.length
        if (positionMs <= word.startMs) return offset.toFloat()
        if (positionMs < word.endMs) {
            var revealedInWord = 0f
            if (word.syllables.isNotEmpty()) {
                for (syllable in word.syllables) {
                    if (positionMs >= syllable.endMs) {
                        revealedInWord = syllable.endChar.toFloat()
                    } else {
                        if (positionMs > syllable.startMs) {
                            val duration = (syllable.endMs - syllable.startMs).coerceAtLeast(1L).toFloat()
                            val fraction = ((positionMs - syllable.startMs) / duration).coerceIn(0f, 1f)
                            revealedInWord = syllable.startChar + (syllable.endChar - syllable.startChar) * fraction
                        }
                        break
                    }
                }
            } else {
                val duration = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
                revealedInWord = length * ((positionMs - word.startMs) / duration).coerceIn(0f, 1f)
            }
            return offset + revealedInWord.coerceIn(0f, length.toFloat())
        }
        offset += length + 1
    }
    return text.length.toFloat()
}

private fun LyricWord.revealedCharsInWord(positionMs: Long): Float {
    if (positionMs <= startMs) return 0f
    if (positionMs >= endMs) return text.length.toFloat()
    if (syllables.isNotEmpty()) {
        var revealed = 0f
        for (syllable in syllables) {
            if (positionMs >= syllable.endMs) {
                revealed = syllable.endChar.toFloat()
            } else {
                if (positionMs > syllable.startMs) {
                    val dur = (syllable.endMs - syllable.startMs).coerceAtLeast(1L).toFloat()
                    val frac = ((positionMs - syllable.startMs) / dur).coerceIn(0f, 1f)
                    revealed = syllable.startChar + (syllable.endChar - syllable.startChar) * frac
                }
                break
            }
        }
        return revealed.coerceIn(0f, text.length.toFloat())
    }
    val duration = (endMs - startMs).coerceAtLeast(1L).toFloat()
    return text.length * ((positionMs - startMs) / duration).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------------------------------
// Spicy Lyrics style bounce: per-word progress -> cubic splines -> damped springs.
// ---------------------------------------------------------------------------------------------

private class Spline(private val xs: FloatArray, private val ys: FloatArray) {
    private val m = FloatArray(xs.size)

    init {
        val n = xs.size
        val cp = FloatArray(n)
        val dp = FloatArray(n)
        for (i in 1 until n - 1) {
            val h0 = xs[i] - xs[i - 1]
            val h1 = xs[i + 1] - xs[i]
            val b = 2f * (h0 + h1)
            val d = 6f * ((ys[i + 1] - ys[i]) / h1 - (ys[i] - ys[i - 1]) / h0)
            val den = if (i == 1) b else b - h0 * cp[i - 1]
            cp[i] = h1 / den
            dp[i] = if (i == 1) d / den else (d - h0 * dp[i - 1]) / den
        }
        if (n > 2) {
            m[n - 2] = dp[n - 2]
            for (i in n - 3 downTo 1) m[i] = dp[i] - cp[i] * m[i + 1]
        }
    }

    fun at(x0: Float): Float {
        val n = xs.size
        val x = x0.coerceIn(xs.first(), xs.last())
        var i = 0
        while (i < n - 2 && x > xs[i + 1]) i++
        val h = xs[i + 1] - xs[i]
        val a = (xs[i + 1] - x) / h
        val b = (x - xs[i]) / h
        return a * ys[i] + b * ys[i + 1] +
            ((a * a * a - a) * m[i] + (b * b * b - b) * m[i + 1]) * h * h / 6f
    }
}

private class Spr(var pos: Float, private val freq: Float, private val damping: Float) {
    var vel = 0f

    fun step(goal: Float, dt: Float) {
        val f = freq * 2f * Math.PI.toFloat()
        val q = exp(-damping * f * dt)
        val c = sqrt(1f - damping * damping)
        val i = cos(dt * f * c)
        val j = sin(dt * f * c)
        val z = j / c
        val y = j / (f * c)
        val o = pos - goal
        val newPos = (o * (i + z * damping) + vel * y) * q + goal
        val newVel = (vel * (i - z * damping) - o * (z * f)) * q
        pos = newPos
        vel = newVel
    }
}

private val BounceScale = Spline(floatArrayOf(0f, 0.7f, 1f), floatArrayOf(0.95f, 1.0505f, 1f))
private val BounceLift = Spline(floatArrayOf(0f, 0.9f, 1f), floatArrayOf(0.01f, -1f / 60f, 0f))

private const val BOUNCE_SCALE_GAIN = 1.6f
private const val BOUNCE_LIFT_GAIN = 6f

private val StartCenterOrigin = TransformOrigin(0f, 0.5f)
private val EndCenterOrigin = TransformOrigin(1f, 0.5f)
private val WordCenterOrigin = TransformOrigin(0.5f, 0.5f)

@Composable
private fun ActiveKaraokeWord(
    word: LyricWord,
    textStyle: TextStyle,
    glowTextStyle: TextStyle = textStyle,
    baseColor: Color,
    sweepColor: Color,
    fontPx: Float,
    positionProvider: () -> Long,
) {
    var bounceScale by remember(word) { mutableFloatStateOf(1f) }
    var bounceLiftPx by remember(word) { mutableFloatStateOf(0f) }
    var framePlayPositionMs by remember(word) { androidx.compose.runtime.mutableLongStateOf(positionProvider()) }
    val provider by rememberUpdatedState(positionProvider)

    LaunchedEffect(word) {
        val sScale = Spr(1f, 0.88f, 0.64f)
        val sLift = Spr(0f, 1.45f, 0.4f)
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            last = now
            val pos = provider()
            framePlayPositionMs = pos
            var goalScale = 1f
            var goalLift = 0f
            if (pos >= word.startMs && pos < word.endMs) {
                val dur = (word.endMs - word.startMs).coerceAtLeast(1L)
                val p = (pos - word.startMs).toFloat() / dur
                val strength = (dur / 900f).coerceIn(0.4f, 1f)
                goalScale = 1f + (BounceScale.at(p) - 1f).coerceAtLeast(-0.02f) * BOUNCE_SCALE_GAIN * strength
                goalLift = BounceLift.at(p) * BOUNCE_LIFT_GAIN * fontPx * strength
            }
            sScale.step(goalScale, dt)
            sLift.step(goalLift, dt)
            bounceScale = sScale.pos
            bounceLiftPx = sLift.pos
        }
    }

    var wordLayout by remember(word.text) { mutableStateOf<TextLayoutResult?>(null) }

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = bounceScale
            scaleY = bounceScale
            translationY = bounceLiftPx
            transformOrigin = WordCenterOrigin
        }
    ) {
        Text(
            text = word.text,
            style = textStyle,
            color = baseColor,
            onTextLayout = { wordLayout = it },
        )

        Text(
            text = word.text,
            style = glowTextStyle,
            color = sweepColor,
            modifier = Modifier
                .drawWithContent {
                    val layout = wordLayout
                    val pos = framePlayPositionMs
                    val revealed = word.revealedCharsInWord(pos)
                    if (layout != null && revealed > 0f) {
                        sweepText(layout, revealed, emptyList())
                    } else if (revealed >= word.text.length) {
                        drawContent()
                    }
                }
        )
    }
}

class LyricVsyncClock {
    private var anchorMs: Long = 0L
    private var anchorNanos: Long = 0L
    private var speed: Float = 1.0f
    private var isPlaying: Boolean = false

    fun sync(positionMs: Long, playbackSpeed: Float, playing: Boolean) {
        val now = System.nanoTime()
        // If out of sync by more than 350ms (e.g. seek), snap immediately
        val expected = if (isPlaying && anchorNanos != 0L) {
            anchorMs + ((now - anchorNanos) / 1_000_000.0 * speed).toLong()
        } else {
            anchorMs
        }
        if (kotlin.math.abs(positionMs - expected) > 350L || !playing) {
            anchorMs = positionMs
            anchorNanos = now
        }
        speed = playbackSpeed
        isPlaying = playing
    }

    fun currentSmoothMs(): Long {
        if (!isPlaying || anchorNanos == 0L) return anchorMs
        val elapsed = ((System.nanoTime() - anchorNanos) / 1_000_000.0 * speed).toLong()
        return anchorMs + elapsed.coerceAtLeast(0L)
    }
}

/**
 * Karaoke line item rendering Apple Music-style ExtraBold typography with high-contrast active line
 * and per-word spring lift/bounce + syllable clip sweep fill.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        animatedAlpha.animateTo(targetAlpha, animationSpec = tween(durationMillis = 320))
    }
    LaunchedEffect(targetScale) {
        animatedScale.animateTo(
            targetScale,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 170f),
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val currentPositionProvider by rememberUpdatedState(positionProvider)
    val density = LocalDensity.current
    val fontPx = with(density) { fontSize.toPx() }
    val wordSpacing = with(density) { (fontSize * 0.26f).toDp() }

    val isEndAligned = line.alignment == LyricAlignment.End
    val textAlign = if (isEndAligned) TextAlign.End else TextAlign.Start
    val boxAlignment = if (isEndAligned) Alignment.CenterEnd else Alignment.CenterStart
    val colAlignment = if (isEndAligned) Alignment.End else Alignment.Start
    val transformOrigin = if (isEndAligned) EndCenterOrigin else StartCenterOrigin

    val lyricsFontFamily = LocalLyricsFontFamily.current ?: FontFamily.SansSerif

    val subtleGlowShadow = remember {
        Shadow(
            color = Color.White.copy(alpha = 0.52f),
            offset = Offset.Zero,
            blurRadius = 18f
        )
    }

    val textStyle = remember(fontSize, isActive, textAlign, lyricsFontFamily) {
        TextStyle(
            fontFamily = lyricsFontFamily,
            fontSize = 36.sp,
            fontWeight = if (isActive) FontWeight.Black else FontWeight.ExtraBold,
            fontSynthesis = FontSynthesis.All,
            lineHeight = 43.sp,
            letterSpacing = (-0.85).sp,
            textAlign = textAlign
        )
    }

    val activeGlowTextStyle = remember(textStyle) {
        textStyle.copy(
            fontWeight = FontWeight.Black,
            fontSynthesis = FontSynthesis.All,
            shadow = subtleGlowShadow
        )
    }

    val activeBaseColor = remember(activeColor) { activeColor.copy(alpha = 0.50f) }
    val dimmedInactiveColor = remember(inactiveColor) { inactiveColor.copy(alpha = 0.95f) }
    val sweepResolvedColor = if (accentSweepColor != Color.Unspecified) accentSweepColor else activeColor

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
            if (isActive && line.words.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        wordSpacing,
                        if (isEndAligned) Alignment.End else Alignment.Start
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    line.words.forEachIndexed { wordIdx, word ->
                        key("${word.startMs}_$wordIdx") {
                            ActiveKaraokeWord(
                                word = word,
                                textStyle = textStyle,
                                glowTextStyle = activeGlowTextStyle,
                                baseColor = activeBaseColor,
                                sweepColor = sweepResolvedColor,
                                fontPx = fontPx,
                                positionProvider = currentPositionProvider
                            )
                        }
                    }
                }
            } else if (isActive) {
                var textLayout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
                var linePlayPosMs by remember(line) { androidx.compose.runtime.mutableLongStateOf(currentPositionProvider()) }

                LaunchedEffect(line) {
                    while (true) {
                        withFrameNanos { it }
                        linePlayPosMs = currentPositionProvider()
                    }
                }

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
                        style = activeGlowTextStyle,
                        color = sweepResolvedColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                val layout = textLayout
                                val pos = linePlayPosMs
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
            val bgFontSize = (fontSize.value * 0.62f).coerceAtLeast(18f).sp
            val bgFontPx = with(density) { bgFontSize.toPx() }
            val bgWordSpacing = with(density) { (bgFontSize * 0.26f).toDp() }
            val bgTextStyle = remember(fontSize, isActive, textAlign, lyricsFontFamily) {
                TextStyle(
                    fontFamily = lyricsFontFamily,
                    fontSize = bgFontSize,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (bgFontSize.value * 1.25f).sp,
                    textAlign = textAlign
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = boxAlignment
            ) {
                if (isActive && bgLine.words.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            bgWordSpacing,
                            if (isEndAligned) Alignment.End else Alignment.Start
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        bgLine.words.forEachIndexed { wordIdx, word ->
                            key("bg_${word.startMs}_$wordIdx") {
                                ActiveKaraokeWord(
                                    word = word,
                                    textStyle = bgTextStyle,
                                    baseColor = activeColor.copy(alpha = 0.25f),
                                    sweepColor = sweepResolvedColor.copy(alpha = 0.85f),
                                    fontPx = bgFontPx,
                                    positionProvider = currentPositionProvider
                                )
                            }
                        }
                    }
                } else if (isActive) {
                    var bgTextLayout by remember(bgLine.text) { mutableStateOf<TextLayoutResult?>(null) }
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

                        var bgPlayPosMs by remember(bgLine) { androidx.compose.runtime.mutableLongStateOf(currentPositionProvider()) }

                        LaunchedEffect(bgLine) {
                            while (true) {
                                withFrameNanos { it }
                                bgPlayPosMs = currentPositionProvider()
                            }
                        }

                        Text(
                            text = bgLine.text,
                            style = bgTextStyle,
                            color = sweepResolvedColor.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    val layout = bgTextLayout
                                    val pos = bgPlayPosMs
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
