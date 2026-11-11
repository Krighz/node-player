package com.nodeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.EqBandConfig
import kotlin.math.*

private const val EQ_MIN_FREQ = 20.0f
private const val EQ_MAX_FREQ = 20000.0f
private const val EQ_MIN_GAIN = -12.0f
private const val EQ_MAX_GAIN = 12.0f
private val EQ_LOG_MIN_FREQ = log10(EQ_MIN_FREQ)
private val EQ_LOG_MAX_FREQ = log10(EQ_MAX_FREQ)

val EQ_DB_STEPS = floatArrayOf(12f, 6f, 0f, -6f, -12f)
val EQ_FREQ_STEPS = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_DASH_DB_EFFECT: PathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
val EQ_DASH_FREQ_EFFECT: PathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
val EQ_CURVE_STROKE = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private const val EQ_SPECTRUM_BAR_COUNT = 32
val EQ_BAR_WEIGHTS = FloatArray(EQ_SPECTRUM_BAR_COUNT) { b ->
    sin((b.toFloat() / EQ_SPECTRUM_BAR_COUNT) * Math.PI).toFloat()
}

private val EQ_BG_SHAPE = RoundedCornerShape(20.dp)
private val EQ_BG_COLOR = Color(0xFF0B0D16).copy(alpha = 0.85f)

fun eqFreqToX(freq: Float, width: Float): Float {
    val clamped = freq.coerceIn(EQ_MIN_FREQ, EQ_MAX_FREQ)
    val fraction = (log10(clamped) - EQ_LOG_MIN_FREQ) / (EQ_LOG_MAX_FREQ - EQ_LOG_MIN_FREQ)
    return fraction * width
}

fun eqXToFreq(x: Float, width: Float): Float {
    if (width <= 0f) return EQ_MIN_FREQ
    val fraction = (x / width).coerceIn(0.0f, 1.0f)
    val logF = EQ_LOG_MIN_FREQ + fraction * (EQ_LOG_MAX_FREQ - EQ_LOG_MIN_FREQ)
    return 10.0.pow(logF.toDouble()).toFloat()
}

fun eqGainToY(gainDb: Float, height: Float): Float {
    val clamped = gainDb.coerceIn(EQ_MIN_GAIN, EQ_MAX_GAIN)
    val fraction = (clamped - EQ_MIN_GAIN) / (EQ_MAX_GAIN - EQ_MIN_GAIN)
    return height * (1.0f - fraction)
}

fun eqYToGain(y: Float, height: Float): Float {
    if (height <= 0f) return 0f
    val fraction = 1.0f - (y / height).coerceIn(0.0f, 1.0f)
    return EQ_MIN_GAIN + fraction * (EQ_MAX_GAIN - EQ_MIN_GAIN)
}

fun evaluateEqResponseDb(freq: Float, bands: List<EqBandConfig>, eqEnabled: Boolean): Float {
    if (!eqEnabled || freq <= 0f) return 0.0f
    var netGainDb = 0.0f
    for (i in bands.indices) {
        val band = bands[i]
        if (band.gainDb != 0.0f) {
            val ratio = (freq / band.centerFreq) - (band.centerFreq / freq)
            val qTerm = ratio / (1.0f / band.q)
            val filterResponse = band.gainDb / (1.0f + qTerm * qTerm)
            netGainDb += filterResponse
        }
    }
    return netGainDb
}

@Composable
fun ParametricEqCurveGraph(
    bands: List<EqBandConfig>,
    onBandsChange: (List<EqBandConfig>) -> Unit,
    eqEnabled: Boolean,
    accentColor: Color,
    rmsLevel: Float = 0.0f,
    showSpectrum: Boolean = true,
    modifier: Modifier = Modifier,
    rmsProvider: () -> Float = { rmsLevel }
) {
    val hapticFeedback = LocalHapticFeedback.current
    var selectedBandIndex by remember { mutableStateOf<Int?>(null) }
    val currentRmsProvider by rememberUpdatedState(rmsProvider)
    val curvePath = remember { Path() }
    val fillPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(EQ_BG_COLOR, EQ_BG_SHAPE)
            .pointerInput(bands, eqEnabled) {
                if (!eqEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        var closestIndex = -1
                        var minDistance = Float.MAX_VALUE
                        bands.forEachIndexed { idx, band ->
                            val bx = eqFreqToX(band.centerFreq, width)
                            val by = eqGainToY(band.gainDb, height)
                            val dist = hypot(offset.x - bx, offset.y - by)
                            if (dist < minDistance && dist < 120f) {
                                minDistance = dist
                                closestIndex = idx
                            }
                        }
                        selectedBandIndex = if (closestIndex >= 0) closestIndex else null
                        if (closestIndex >= 0) {
                            try {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } catch (_: Exception) {}
                        }
                    },
                    onDragEnd = {
                        selectedBandIndex = null
                    },
                    onDragCancel = {
                        selectedBandIndex = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val idx = selectedBandIndex ?: return@detectDragGestures
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()

                        val currentBand = bands[idx]
                        val currentX = eqFreqToX(currentBand.centerFreq, width)
                        val currentY = eqGainToY(currentBand.gainDb, height)

                        val newX = (currentX + dragAmount.x).coerceIn(0f, width)
                        val newY = (currentY + dragAmount.y).coerceIn(0f, height)

                        val newFreq = eqXToFreq(newX, width)
                        val newGain = (eqYToGain(newY, height) * 2f).roundToInt() / 2.0f

                        val updatedBands = bands.toMutableList()
                        updatedBands[idx] = currentBand.copy(
                            centerFreq = newFreq,
                            gainDb = newGain.coerceIn(EQ_MIN_GAIN, EQ_MAX_GAIN)
                        )
                        onBandsChange(updatedBands.toList())
                        try {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (_: Exception) {}
                    }
                )
            }
    ) {
        if (showSpectrum) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .drawWithCache {
                        val width = size.width
                        val height = size.height
                        val maxBarHeight = (height * 0.85f).coerceAtLeast(1f)
                        val barWidth = width / EQ_SPECTRUM_BAR_COUNT
                        val barTopY = height - maxBarHeight
                        val barBrush = Brush.verticalGradient(
                            colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent),
                            startY = barTopY,
                            endY = height
                        )
                        val pivot = Offset(0f, height)
                        val rectWidth = (barWidth - 4f).coerceAtLeast(0f)

                        onDrawBehind {
                            val rms = currentRmsProvider()
                            if (rms > 0.001f && width > 0f && height > 0f) {
                                for (b in 0 until EQ_SPECTRUM_BAR_COUNT) {
                                    val barHeight = (rms * 2.5f * EQ_BAR_WEIGHTS[b] * (height * 0.7f))
                                        .coerceIn(2f, maxBarHeight)
                                    val x = b * barWidth + 2f
                                    scale(scaleX = 1f, scaleY = barHeight / maxBarHeight, pivot = pivot) {
                                        drawRect(
                                            brush = barBrush,
                                            topLeft = Offset(x, barTopY),
                                            size = Size(rectWidth, maxBarHeight)
                                        )
                                    }
                                }
                            }
                        }
                    }
            )
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    val midY = height / 2f

                    val dbYCoords = FloatArray(EQ_DB_STEPS.size) { i -> eqGainToY(EQ_DB_STEPS[i], height) }
                    val freqXCoords = FloatArray(EQ_FREQ_STEPS.size) { i -> eqFreqToX(EQ_FREQ_STEPS[i], width) }

                    curvePath.reset()
                    fillPath.reset()
                    if (width > 0f && height > 0f) {
                        val stepPx = 3f
                        var first = true
                        var x = 0f
                        while (x <= width) {
                            val f = eqXToFreq(x, width)
                            val netGainDb = evaluateEqResponseDb(f, bands, eqEnabled)
                            val y = eqGainToY(netGainDb, height)
                            if (first) {
                                curvePath.moveTo(x, y)
                                fillPath.moveTo(x, midY)
                                fillPath.lineTo(x, y)
                                first = false
                            } else {
                                curvePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            x += stepPx
                        }
                        fillPath.lineTo(width, midY)
                        fillPath.close()
                    }

                    val fillBrush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.35f),
                            accentColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = height.coerceAtLeast(1f)
                    )
                    val curveColor = if (eqEnabled) accentColor else Color.White.copy(alpha = 0.35f)
                    val zeroDbLineColor = Color.White.copy(alpha = 0.25f)
                    val gridLineColor = Color.White.copy(alpha = 0.08f)
                    val selectedIdx = selectedBandIndex
                    val activeBandOffsets = if (eqEnabled) {
                        Array(bands.size) { idx ->
                            val band = bands[idx]
                            Offset(eqFreqToX(band.centerFreq, width), eqGainToY(band.gainDb, height))
                        }
                    } else null
                    val haloColorNormal = accentColor.copy(alpha = 0.25f)
                    val haloColorSelected = accentColor.copy(alpha = 0.50f)

                    onDrawBehind {
                        for (i in EQ_DB_STEPS.indices) {
                            val y = dbYCoords[i]
                            val isZero = EQ_DB_STEPS[i] == 0f
                            drawLine(
                                color = if (isZero) zeroDbLineColor else gridLineColor,
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = if (isZero) 1.5f else 1f,
                                pathEffect = if (!isZero) EQ_DASH_DB_EFFECT else null
                            )
                        }

                        for (i in freqXCoords.indices) {
                            val x = freqXCoords[i]
                            drawLine(
                                color = gridLineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = 1f,
                                pathEffect = EQ_DASH_FREQ_EFFECT
                            )
                        }

                        drawPath(path = fillPath, brush = fillBrush)
                        drawPath(path = curvePath, color = curveColor, style = EQ_CURVE_STROKE)

                        if (activeBandOffsets != null) {
                            for (idx in activeBandOffsets.indices) {
                                val center = activeBandOffsets[idx]
                                val isSelected = selectedIdx == idx
                                drawCircle(
                                    color = if (isSelected) haloColorSelected else haloColorNormal,
                                    radius = if (isSelected) 18f else 12f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = if (isSelected) 8f else 6f,
                                    center = center
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = if (isSelected) 6f else 4.5f,
                                    center = center
                                )
                            }
                        }
                    }
                }
        )
    }
}
