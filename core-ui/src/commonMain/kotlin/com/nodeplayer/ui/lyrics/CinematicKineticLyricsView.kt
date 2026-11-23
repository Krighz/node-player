package com.nodeplayer.ui.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsSettings
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedural PRNG matching Javascript Mulberry32 implementation.
 */
class Mulberry32(seed: Long) {
    private var state = seed.toInt()

    fun nextFloat(): Float {
        state += 0x6D2B79F5.toInt()
        var z = state
        z = ((z xor (z ushr 15)) * (z or 1))
        z = z xor (z + ((z xor (z ushr 7)) * (z or 61)))
        val unsigned = (z xor (z ushr 14)).toLong() and 0xFFFFFFFFL
        return (unsigned.toDouble() / 4294967296.0).toFloat()
    }
}

data class KineticWordAnchors(
    val entryPt: Offset,
    val exitPt: Offset,
    val centerPt: Offset,
    val baselineY: Float
)

data class KineticWordNode(
    val clean: String,
    val isHero: Boolean,
    val startMs: Long,
    val endMs: Long,
    val gapAfter: Boolean,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val waveY: Float,
    val fontFamily: FontFamily,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle,
    val fontSizeSp: Float,
    val anchors: KineticWordAnchors,
    val layoutResult: TextLayoutResult
)

sealed interface KineticBlock {
    val startMs: Long
    val endMs: Long

    data class PhraseBlock(
        override val startMs: Long,
        override val endMs: Long,
        val words: List<KineticWordNode>
    ) : KineticBlock

    data class InstrumentalBlock(
        override val startMs: Long,
        override val endMs: Long,
        val cx: Float,
        val cy: Float
    ) : KineticBlock
}

private data class StarParticle(
    val layer: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val parallax: Float,
    val phase: Float,
    val speed: Float,
    val driftX: Float,
    val driftY: Float
)

/**
 * High-refresh-rate Cinematic Kinetic lyrics engine.
 */
@Composable
fun CinematicKineticLyricsView(
    lyrics: Lyrics?,
    isPlaying: Boolean,
    settings: LyricsSettings,
    layoutSeed: Long = 42L,
    modifier: Modifier = Modifier,
    onSeekTo: (Long) -> Unit = {},
    positionProvider: () -> Long = { 0L },
    isLandscape: Boolean = false
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val currentPositionProvider by rememberUpdatedState(positionProvider)
    val currentIsPlaying by rememberUpdatedState(isPlaying)

    var widthPx by remember { mutableFloatStateOf(1080f) }
    var heightPx by remember { mutableFloatStateOf(1920f) }

    // Critically-damped 2D camera state
    var camX by remember { mutableFloatStateOf(0f) }
    var camY by remember { mutableFloatStateOf(0f) }
    var camZoom by remember { mutableFloatStateOf(1f) }
    var camVx by remember { mutableFloatStateOf(0f) }
    var camVy by remember { mutableFloatStateOf(0f) }
    var camVz by remember { mutableFloatStateOf(0f) }
    var smoothCommonZoom by remember { mutableFloatStateOf(settings.commonZoom) }

    // Measured layout blocks
    var layoutBlocks by remember { mutableStateOf<List<KineticBlock>>(emptyList()) }
    var allWords by remember { mutableStateOf<List<KineticWordNode>>(emptyList()) }

    // Starfield particles
    val stars = remember {
        val rand = Mulberry32(1337L)
        List(180) { i ->
            val layer = if (i < 95) 0 else if (i < 150) 1 else 2
            StarParticle(
                layer = layer,
                x = rand.nextFloat() * 2000f - 1000f,
                y = rand.nextFloat() * 2000f - 1000f,
                size = when (layer) {
                    0 -> 0.9f + rand.nextFloat() * 0.9f
                    1 -> 1.4f + rand.nextFloat() * 1.3f
                    else -> 2.6f + rand.nextFloat() * 2.2f
                },
                parallax = when (layer) {
                    0 -> 0.08f
                    1 -> 0.22f
                    else -> 0.42f
                },
                phase = rand.nextFloat() * (PI.toFloat() * 2f),
                speed = 0.8f + rand.nextFloat() * 1.8f,
                driftX = (rand.nextFloat() - 0.5f) * 6f,
                driftY = -3f - rand.nextFloat() * 8f
            )
        }
    }

    // Rebuild procedural layout when lyrics, seed, or layout parameters change
    LaunchedEffect(lyrics, layoutSeed, settings.lrSwing, settings.verticalSpacing, widthPx, isLandscape) {
        if (lyrics == null || lyrics.lines.isEmpty()) {
            layoutBlocks = emptyList()
            allWords = emptyList()
            return@LaunchedEffect
        }

        val rand = Mulberry32(layoutSeed)
        val vScale = settings.verticalSpacing
        val maxLineW = if (isLandscape) min(520f * density.density, max(280f * density.density, widthPx * 0.52f))
                       else min(540f * density.density, max(360f * density.density, widthPx * 0.92f))
        val swingPx = (if (isLandscape) 90f else 140f) * settings.lrSwing * density.density

        var cursorY = 0f
        val newBlocks = mutableListOf<KineticBlock>()
        val newAllWords = mutableListOf<KineticWordNode>()
        var lastAlign = -1
        var streak = 0

        val lines = lyrics.lines
        for (i in lines.indices) {
            val curLine = lines[i]
            val prevLine = if (i > 0) lines[i - 1] else null

            // Instrumental break detection: gap >= 2800ms with generous clearance & centering
            if (prevLine != null) {
                val prevEndMs = prevLine.endTimeMs ?: (prevLine.timestampMs + 3000L)
                val gap = curLine.timestampMs - prevEndMs
                if (gap >= 2800L) {
                    cursorY += 160f * vScale * density.density
                    val instCx = 0f // Cleanly centered, never colliding with words
                    val instCy = cursorY
                    newBlocks.add(
                        KineticBlock.InstrumentalBlock(
                            startMs = prevEndMs + 250L,
                            endMs = curLine.timestampMs - 200L,
                            cx = instCx,
                            cy = instCy
                        )
                    )
                    cursorY += 240f * vScale * density.density
                } else if (gap >= 1500L) {
                    cursorY += (340f + rand.nextFloat() * 70f) * vScale * (density.density * 0.75f)
                } else {
                    cursorY += (195f + rand.nextFloat() * 55f) * vScale * (density.density * 0.75f)
                }
            }

            // Word collection with strict monotonic timeline sanitization
            var lastWordEnd = if (newAllWords.isNotEmpty()) newAllWords.last().endMs else 0L

            val rawWords: List<com.nodeplayer.model.LyricWord> = if (curLine.words.isNotEmpty()) {
                var wordCursor = max(curLine.timestampMs, lastWordEnd)
                curLine.words.map { w ->
                    val s = max(w.startMs, wordCursor)
                    val dur = max(180L, w.endMs - w.startMs)
                    val e = max(s + 180L, max(w.endMs, s + dur))
                    wordCursor = e
                    w.copy(startMs = s, endMs = e)
                }
            } else {
                val tokens = curLine.text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (tokens.isEmpty()) emptyList()
                else {
                    val lineStart = max(curLine.timestampMs, lastWordEnd)
                    val curEndMs = curLine.endTimeMs
                    val lineDur = if (curEndMs != null && curEndMs > lineStart) {
                        curEndMs - lineStart
                    } else if (i < lines.size - 1 && lines[i + 1].timestampMs > lineStart) {
                        (lines[i + 1].timestampMs - lineStart).coerceIn(1200L, 7000L)
                    } else 3500L

                    val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
                    var runningStart = lineStart
                    tokens.mapIndexed { idx, token ->
                        val isLast = idx == tokens.lastIndex
                        val wordDur = if (isLast) max(200L, (lineStart + lineDur) - runningStart)
                                      else max(180L, (token.length.toFloat() / totalChars * lineDur).toLong())
                        val w = com.nodeplayer.model.LyricWord(
                            text = token,
                            startMs = runningStart,
                            endMs = runningStart + wordDur
                        )
                        runningStart += wordDur
                        w
                    }
                }
            }

            if (rawWords.isEmpty()) continue

            // Alignment selection
            val alignMode: Int
            if (streak < 2 && rand.nextFloat() < 0.36f && lastAlign != -1) {
                alignMode = lastAlign
                streak++
            } else {
                val roll = rand.nextFloat()
                alignMode = if (roll < 0.44f) 0 else if (roll < 0.88f) 2 else 1 // 0=Left, 1=Center, 2=Right
                streak = 1
            }
            lastAlign = alignMode

            // Font specifications & layout measurement
            val measuredWords = rawWords.mapIndexed { wIdx, word ->
                val isHero = word.text.startsWith("*") && word.text.endsWith("*")
                val clean = if (isHero) word.text.removeSurrounding("*") else word.text

                val fontStyle: FontStyle
                val fontWeight: FontWeight
                val fontFamily: FontFamily
                val fontSizeSp: Float

                when {
                    isHero -> {
                        fontSizeSp = 84f
                        fontFamily = if ((wIdx + i) % 2 == 0) FontFamily.SansSerif else FontFamily.Serif
                        fontWeight = FontWeight.ExtraBold
                        fontStyle = if ((wIdx + i) % 2 != 0) FontStyle.Italic else FontStyle.Normal
                    }
                    clean.length <= 3 -> {
                        fontSizeSp = 58f
                        fontFamily = FontFamily.Serif
                        fontWeight = FontWeight.Medium
                        fontStyle = FontStyle.Italic
                    }
                    else -> {
                        fontSizeSp = 64f
                        fontFamily = FontFamily.SansSerif
                        fontWeight = FontWeight.ExtraBold
                        fontStyle = FontStyle.Normal
                    }
                }

                val layoutResult = textMeasurer.measure(
                    text = clean,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        fontSize = fontSizeSp.sp
                    )
                )

                Pair(
                    layoutResult,
                    KineticWordNode(
                        clean = clean,
                        isHero = isHero,
                        startMs = word.startMs,
                        endMs = word.endMs,
                        gapAfter = clean.endsWith(",") || clean.endsWith(".") || clean.endsWith("!"),
                        x = 0f,
                        y = 0f,
                        width = layoutResult.size.width.toFloat(),
                        height = layoutResult.size.height.toFloat(),
                        waveY = (rand.nextFloat() - 0.5f) * 12f * density.density,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        fontSizeSp = fontSizeSp,
                        anchors = KineticWordAnchors(Offset.Zero, Offset.Zero, Offset.Zero, 0f),
                        layoutResult = layoutResult
                    )
                )
            }

            // Word wrap into max 2 lines
            val baseWordGap = 52f * density.density
            val wrappedLines = mutableListOf<MutableList<KineticWordNode>>()
            wrappedLines.add(mutableListOf())
            var currentLineWidth = 0f

            for ((_, node) in measuredWords) {
                val extraGap = if (node.gapAfter) 38f * density.density else 0f
                val lastLine = wrappedLines.last()
                if (lastLine.isNotEmpty() && currentLineWidth + node.width > maxLineW && wrappedLines.size < 2) {
                    val newLine = mutableListOf<KineticWordNode>()
                    newLine.add(node)
                    wrappedLines.add(newLine)
                    currentLineWidth = node.width + baseWordGap + extraGap
                } else {
                    lastLine.add(node)
                    currentLineWidth += node.width + baseWordGap + extraGap
                }
            }

            // Position words with alignment offset
            val phraseNodes = mutableListOf<KineticWordNode>()
            for (lIdx in wrappedLines.indices) {
                val lineNodes = wrappedLines[lIdx]
                if (lIdx > 0) {
                    cursorY += (108f + rand.nextFloat() * 20f) * vScale * density.density
                }

                var totalLineWidth = 0f
                for (nIdx in lineNodes.indices) {
                    val n = lineNodes[nIdx]
                    totalLineWidth += n.width + if (nIdx < lineNodes.size - 1) baseWordGap + (if (n.gapAfter) 38f * density.density else 0f) else 0f
                }

                val startX = when (alignMode) {
                    0 -> -swingPx - totalLineWidth * 0.15f
                    2 -> swingPx - totalLineWidth * 0.85f
                    else -> -totalLineWidth * 0.5f
                }

                var curX = startX
                for (node in lineNodes) {
                    val wordY = cursorY + node.waveY
                    val placedNode = node.copy(
                        x = curX,
                        y = wordY,
                        anchors = KineticWordAnchors(
                            entryPt = Offset(curX, wordY),
                            exitPt = Offset(curX + node.width, wordY),
                            centerPt = Offset(curX + node.width * 0.5f, wordY),
                            baselineY = wordY + node.height * 0.34f
                        )
                    )
                    phraseNodes.add(placedNode)
                    newAllWords.add(placedNode)
                    curX += node.width + baseWordGap + (if (node.gapAfter) 38f * density.density else 0f)
                }
            }

            if (phraseNodes.isNotEmpty()) {
                newBlocks.add(
                    KineticBlock.PhraseBlock(
                        startMs = phraseNodes.first().startMs,
                        endMs = phraseNodes.last().endMs,
                        words = phraseNodes
                    )
                )
            }
        }

        layoutBlocks = newBlocks
        allWords = newAllWords
    }

    // Frame-synced camera physics & animation ticker (60-120fps)
    var frameTimeSec by remember { mutableFloatStateOf(0f) }
    var currentPlaybackMs by remember { mutableLongStateOf(0L) }
    var smoothPlaybackMs by remember { mutableFloatStateOf(0f) }
    var lastRawPositionMs by remember { mutableLongStateOf(-1L) }
    val scratchDiamondPath = remember { Path() }

    LaunchedEffect(Unit) {
        var prevNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                if (prevNanos == 0L) prevNanos = nowNanos
                val dt = min(0.05f, ((nowNanos - prevNanos) / 1_000_000_000f))
                prevNanos = nowNanos

                val rawMs = currentPositionProvider()
                if (currentIsPlaying) {
                    frameTimeSec += dt
                    if (rawMs != lastRawPositionMs) {
                        lastRawPositionMs = rawMs
                        val delta = rawMs - smoothPlaybackMs
                        if (delta < -250f || delta > 450f) {
                            smoothPlaybackMs = rawMs.toFloat()
                        } else {
                            smoothPlaybackMs += delta * 0.45f
                        }
                    } else {
                        smoothPlaybackMs += dt * 1000f
                    }
                } else {
                    lastRawPositionMs = rawMs
                    smoothPlaybackMs = rawMs.toFloat()
                }
                val nowMs = smoothPlaybackMs.toLong()
                currentPlaybackMs = nowMs

                // Focus target calculation
                var inInstrumental = false
                var focusX = camX
                var focusY = camY
                var baseZoom = 0.88f

                val activeInst = layoutBlocks.find {
                    it is KineticBlock.InstrumentalBlock && nowMs >= it.startMs && nowMs <= it.endMs
                } as? KineticBlock.InstrumentalBlock

                if (activeInst != null) {
                    inInstrumental = true
                    focusX = activeInst.cx
                    focusY = activeInst.cy
                    baseZoom = 0.92f
                } else if (allWords.isNotEmpty()) {
                    val activeIdx = allWords.indexOfFirst { nowMs in it.startMs..it.endMs }
                    val activeWord = if (activeIdx >= 0) allWords[activeIdx]
                        else allWords.find { it.startMs > nowMs }
                        ?: allWords.last()

                    val targetWordX = activeWord.x + activeWord.width * 0.5f
                    val targetWordY = activeWord.y

                    // Anticipatory lookahead: if active word is in its final 240ms, blend smoothly toward next word
                    if (activeIdx >= 0 && activeIdx < allWords.lastIndex) {
                        val nextWord = allWords[activeIdx + 1]
                        val remainMs = activeWord.endMs - nowMs
                        if (remainMs in 0..240L) {
                            val blend = ((240L - remainMs).toFloat() / 240f).coerceIn(0f, 1f)
                            val nextWordX = nextWord.x + nextWord.width * 0.5f
                            focusX = targetWordX + (nextWordX - targetWordX) * blend
                            focusY = targetWordY + (nextWord.y - targetWordY) * blend
                        } else {
                            focusX = targetWordX
                            focusY = targetWordY
                        }
                    } else {
                        focusX = targetWordX
                        focusY = targetWordY
                    }

                    val fitZoom = min(1.02f, (widthPx * 0.72f) / max(220f * density.density, activeWord.width + 90f * density.density))
                    baseZoom = if (activeWord.isHero) min(0.86f, fitZoom) else min(0.96f, fitZoom)
                }

                // Smooth common zoom interpolation
                smoothCommonZoom += (settings.commonZoom - smoothCommonZoom) * min(1f, dt * 8f)
                val targetZoom = baseZoom * smoothCommonZoom

                // Distance-adaptive dynamic spring stiffness: accelerates dynamically across large leaps
                val travelDist = kotlin.math.hypot(focusX - camX, focusY - camY)
                val leapBoost = (travelDist / (140f * density.density)).coerceIn(0f, 3.5f)
                val dynamicOmega = (5.2f * settings.cameraGlide * (1f + leapBoost)).coerceIn(4.8f, 16.5f)

                // Critically-damped spring step with dynamic omega
                val omega = dynamicOmega
                val f = 1f + 2f * dt * omega
                val oo = omega * omega
                val hoo = dt * oo
                val hhoo = dt * hoo
                val detInv = 1f / (f + hhoo)

                // Update X
                camX = (f * camX + dt * camVx + hhoo * focusX) * detInv
                camVx = (camVx + hoo * (focusX - camX)) * detInv

                // Update Y
                camY = (f * camY + dt * camVy + hhoo * focusY) * detInv
                camVy = (camVy + hoo * (focusY - camY)) * detInv

                // Update Zoom
                camZoom = (f * camZoom + dt * camVz + hhoo * targetZoom) * detInv
                camVz = (camVz + hoo * (targetZoom - camZoom)) * detInv
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val curWords = allWords
                    val cX = camX
                    val cY = camY
                    val cZoom = camZoom
                    val wPx = widthPx
                    val hPx = heightPx

                    // Unproject screen tap coordinate to world space
                    val originX = if (isLandscape) wPx * 0.68f else wPx * 0.50f
                    val originY = if (isLandscape) hPx * 0.50f else hPx * 0.52f
                    val worldX = (tapOffset.x - originX) / cZoom + cX
                    val worldY = (tapOffset.y - originY) / cZoom + cY

                    val tappedWord = curWords.find { word ->
                        val tapPadding = 24f * density.density
                        worldX >= (word.x - tapPadding) &&
                            worldX <= (word.x + word.width + tapPadding) &&
                            worldY >= (word.y - word.height * 0.6f - tapPadding) &&
                            worldY <= (word.y + word.height * 0.6f + tapPadding)
                    }

                    if (tappedWord != null) {
                        onSeekTo(tappedWord.startMs)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            widthPx = size.width
            heightPx = size.height

            val W = size.width
            val H = size.height
            val nowMs = currentPlaybackMs
            val tSec = frameTimeSec

            // 1. Pure Pitch-Black OLED background
            drawRect(Color.Black, size = size)

            // 2. Subtle Volumetric Moving Ambient Radial Glow
            val pal = settings.palette
            val ambientColor = Color(pal.ambientColorArgb)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ambientColor, Color.Transparent),
                    center = Offset(W * 0.5f, H * 0.48f),
                    radius = max(W, H) * 0.55f
                ),
                radius = max(W, H) * 0.55f,
                center = Offset(W * 0.5f, H * 0.48f)
            )

            // 3. 3-Layer Parallax Floating Starfield (Zero-allocation using scratch path)
            val starCount = (stars.size * settings.sparkleDensity.coerceIn(0f, 1f)).toInt()
            if (starCount > 0) {
                val activeInst = layoutBlocks.any {
                    it is KineticBlock.InstrumentalBlock && nowMs >= it.startMs && nowMs <= it.endMs
                }
                val boost = if (activeInst) 1.35f else 1.0f

                for (sIdx in 0 until starCount) {
                    val s = stars[sIdx]
                    val sx = ((s.x - camX * s.parallax + tSec * s.driftX) % W + W) % W
                    val sy = ((s.y - camY * s.parallax + tSec * s.driftY) % H + H) % H

                    val twinkle = (0.35f + 0.65f * (0.5f + 0.5f * sin(tSec * s.speed + s.phase))) * boost
                    val alpha = min(1f, twinkle * if (s.layer == 2) 0.85f else 0.65f)

                    if (s.layer == 2) {
                        // 4-Point Diamond Glint
                        val r = s.size * density.density
                        scratchDiamondPath.rewind()
                        scratchDiamondPath.moveTo(sx, sy - r * 1.9f)
                        scratchDiamondPath.lineTo(sx + r * 0.45f, sy - r * 0.45f)
                        scratchDiamondPath.lineTo(sx + r * 1.9f, sy)
                        scratchDiamondPath.lineTo(sx + r * 0.45f, sy + r * 0.45f)
                        scratchDiamondPath.lineTo(sx, sy + r * 1.9f)
                        scratchDiamondPath.lineTo(sx - r * 0.45f, sy + r * 0.45f)
                        scratchDiamondPath.lineTo(sx - r * 1.9f, sy)
                        scratchDiamondPath.lineTo(sx - r * 0.45f, sy - r * 0.45f)
                        scratchDiamondPath.close()
                        drawPath(scratchDiamondPath, color = Color(pal.midColorArgb).copy(alpha = alpha))
                    } else {
                        // Round Pinpoint / Mid Star
                        drawCircle(
                            color = Color.White.copy(alpha = alpha),
                            radius = s.size * density.density * 0.5f,
                            center = Offset(sx, sy)
                        )
                    }
                }
            }

            // 4. World Camera Transform with Viewport Culling
            val viewHalfH = (H * 0.65f) / max(0.2f, camZoom)
            val minVisibleY = camY - viewHalfH
            val maxVisibleY = camY + viewHalfH

            val originX = if (isLandscape) W * 0.68f else W * 0.50f
            val originY = if (isLandscape) H * 0.50f else H * 0.52f
            translate(left = originX, top = originY) {
                scale(scale = camZoom, pivot = Offset.Zero) {
                    translate(left = -camX, top = -camY) {
                        for (block in layoutBlocks) {
                            when (block) {
                                is KineticBlock.InstrumentalBlock -> {
                                    if (block.cy in (minVisibleY - 120f)..(maxVisibleY + 120f)) {
                                        drawInstrumentalDots(block, nowMs, tSec, pal, density.density)
                                    }
                                }
                                is KineticBlock.PhraseBlock -> {
                                    val isBlockActive = nowMs in block.startMs..block.endMs
                                    val isBlockPast = nowMs > block.endMs
                                    for (word in block.words) {
                                        if (word.y in (minVisibleY - 150f)..(maxVisibleY + 150f)) {
                                            drawWordSinglePass(
                                                word = word,
                                                nowMs = nowMs,
                                                isParentBlockActive = isBlockActive,
                                                isParentBlockPast = isBlockPast,
                                                pal = pal,
                                                bounce = settings.bounce,
                                                density = density.density
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Instrumental Floating Three Dots (• • •) with sinusoidal bobbing and progressive illumination.
 * Dedicated clearance, clean centering, and active-only fade prevents collision with text lines.
 */
private fun DrawScope.drawInstrumentalDots(
    blk: KineticBlock.InstrumentalBlock,
    nowMs: Long,
    tSec: Float,
    pal: com.nodeplayer.model.LyricsGlowPalette,
    density: Float
) {
    if (nowMs < blk.startMs - 800L || nowMs > blk.endMs + 500L) {
        return // Do not render floating dots when vocal lines are playing
    }
    val active = nowMs in blk.startMs..blk.endMs
    val p = if (active) {
        ((nowMs - blk.startMs).toFloat() / max(1f, (blk.endMs - blk.startMs).toFloat())).coerceIn(0f, 1f)
    } else 0f

    val fadeAlpha = if (nowMs < blk.startMs) {
        ((nowMs - (blk.startMs - 800L)).toFloat() / 800f).coerceIn(0f, 1f)
    } else if (nowMs > blk.endMs) {
        (1f - ((nowMs - blk.endMs).toFloat() / 500f)).coerceIn(0f, 1f)
    } else 1f

    translate(left = blk.cx, top = blk.cy) {
        val spacing = 36f * density
        for (i in 0 until 3) {
            val dx = (i - 1) * spacing
            val wave = if (active) sin(tSec * 4.2f - i * 0.9f) * 8f * density else 0f
            val lit = active && p >= (i + 0.12f) / 3f
            val radius = (if (lit) 9.5f else 7.5f) * density

            val dotColor = if (lit) {
                Color(pal.litColorArgb).copy(alpha = fadeAlpha)
            } else {
                Color.White.copy(alpha = 0.28f * fadeAlpha)
            }

            if (lit) {
                // Soft halo behind lit dot
                drawCircle(
                    color = Color(pal.glowColorArgb).copy(alpha = 0.55f * fadeAlpha),
                    radius = radius * 1.8f,
                    center = Offset(dx, wave)
                )
            }

            drawCircle(
                color = dotColor,
                radius = radius,
                center = Offset(dx, wave)
            )
        }
    }
}

/**
 * Glitch-Free Single-Pass Word Sweep with 4-tier visual states:
 * 1. Active word: feathered gradient sweep + bloom glow
 * 2. Sung words in current active phrase: clean solid white (0.88f)
 * 3. Sung words in past phrases: gentle ambient dim (0.32f)
 * 4. Upcoming words: deep unlit dim (0.18f), zero pre-highlighting
 */
private fun DrawScope.drawWordSinglePass(
    word: KineticWordNode,
    nowMs: Long,
    isParentBlockActive: Boolean,
    isParentBlockPast: Boolean,
    pal: com.nodeplayer.model.LyricsGlowPalette,
    bounce: Float,
    density: Float
) {
    // If the parent phrase hasn't started yet, all its words are strictly in the future!
    val isPast = if (!isParentBlockActive && !isParentBlockPast) false else (isParentBlockPast || nowMs >= word.endMs)
    val isFuture = !isParentBlockPast && (!isParentBlockActive || nowMs < word.startMs)
    val isActive = isParentBlockActive && !isPast && !isFuture

    val progress = if (isPast) 1f else if (isFuture) 0f else {
        ((nowMs - word.startMs).toFloat() / max(1f, (word.endMs - word.startMs).toFloat())).coerceIn(0f, 1f)
    }

    // 4-tier visual hierarchy
    val baseAlpha = when {
        isActive -> 1.0f
        isParentBlockActive && isPast -> 0.88f
        isPast -> 0.32f
        else -> 0.18f
    }
    val popScale = if (isActive) (1f + 0.055f * bounce * sin(progress * PI.toFloat())) else 1.0f
    val liftY = if (isActive) (-5f * bounce * sin(progress * PI.toFloat()) * density) else 0f

    val wordCenterX = word.x + word.width * 0.5f
    val wordCenterY = word.y + liftY

    translate(left = wordCenterX, top = wordCenterY) {
        scale(scale = popScale, pivot = Offset.Zero) {
            val leftX = -word.width * 0.5f
            val rightX = word.width * 0.5f

            if (isActive) {
                val litColor = Color(pal.litColorArgb)
                val midColor = Color(pal.midColorArgb)
                val unlitColor = Color.White.copy(alpha = 0.26f)
                val glowHaloColor = Color(pal.glowColorArgb)
                val glowBlur = 18f * (0.4f + 0.6f * sin(progress * PI.toFloat())) * density

                // Feathered gradient sweep across the active word
                val p = progress.coerceIn(0f, 1f)
                val sweepBrush = if (p <= 0.005f) {
                    Brush.horizontalGradient(listOf(unlitColor, unlitColor), startX = leftX, endX = rightX)
                } else if (p >= 0.995f) {
                    Brush.horizontalGradient(listOf(litColor, litColor), startX = leftX, endX = rightX)
                } else {
                    val halfFeather = 0.08f
                    val s0 = (p - halfFeather).coerceIn(0.001f, 0.996f)
                    val s1 = p.coerceIn(s0 + 0.001f, 0.998f)
                    val s2 = (p + halfFeather).coerceIn(s1 + 0.001f, 0.999f)

                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to litColor,
                            s0 to litColor,
                            s1 to midColor,
                            s2 to unlitColor,
                            1.0f to unlitColor
                        ),
                        startX = leftX,
                        endX = rightX
                    )
                }

                drawText(
                    textLayoutResult = word.layoutResult,
                    brush = sweepBrush,
                    topLeft = Offset(leftX, -word.height * 0.5f),
                    shadow = Shadow(
                        color = glowHaloColor,
                        blurRadius = glowBlur,
                        offset = Offset.Zero
                    )
                )
            } else {
                // Inactive or completed word: zero measuring, instantaneous pre-cached draw
                drawText(
                    textLayoutResult = word.layoutResult,
                    color = Color.White.copy(alpha = baseAlpha),
                    topLeft = Offset(leftX, -word.height * 0.5f)
                )
            }
        }
    }
}
