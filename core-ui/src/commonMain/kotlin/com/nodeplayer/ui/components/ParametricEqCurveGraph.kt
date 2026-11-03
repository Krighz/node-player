package com.nodeplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.EqBandConfig
import kotlin.math.*

@Composable
fun ParametricEqCurveGraph(
    bands: List<EqBandConfig>,
    onBandsChange: (List<EqBandConfig>) -> Unit,
    eqEnabled: Boolean,
    accentColor: Color,
    rmsLevel: Float = 0.0f,
    showSpectrum: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var selectedBandIndex by remember { mutableStateOf<Int?>(null) }

    val minFreq = 20.0f
    val maxFreq = 20000.0f
    val minGain = -12.0f
    val maxGain = 12.0f

    val logMinFreq = log10(minFreq)
    val logMaxFreq = log10(maxFreq)

    fun freqToX(freq: Float, width: Float): Float {
        val clamped = freq.coerceIn(minFreq, maxFreq)
        val fraction = (log10(clamped) - logMinFreq) / (logMaxFreq - logMinFreq)
        return fraction * width
    }

    fun xToFreq(x: Float, width: Float): Float {
        val fraction = (x / width).coerceIn(0.0f, 1.0f)
        val logF = logMinFreq + fraction * (logMaxFreq - logMinFreq)
        return 10.0.pow(logF.toDouble()).toFloat()
    }

    fun gainToY(gainDb: Float, height: Float): Float {
        val clamped = gainDb.coerceIn(minGain, maxGain)
        val fraction = (clamped - minGain) / (maxGain - minGain)
        return height * (1.0f - fraction)
    }

    fun yToGain(y: Float, height: Float): Float {
        val fraction = 1.0f - (y / height).coerceIn(0.0f, 1.0f)
        return minGain + fraction * (maxGain - minGain)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color(0xFF0B0D16).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .pointerInput(bands, eqEnabled) {
                if (!eqEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        var closestIndex = -1
                        var minDistance = Float.MAX_VALUE
                        bands.forEachIndexed { idx, band ->
                            val bx = freqToX(band.centerFreq, width)
                            val by = gainToY(band.gainDb, height)
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
                        val currentX = freqToX(currentBand.centerFreq, width)
                        val currentY = gainToY(currentBand.gainDb, height)

                        val newX = (currentX + dragAmount.x).coerceIn(0f, width)
                        val newY = (currentY + dragAmount.y).coerceIn(0f, height)

                        val newFreq = xToFreq(newX, width)
                        val newGain = (yToGain(newY, height) * 2f).roundToInt() / 2.0f

                        val updatedBands = bands.toMutableList()
                        updatedBands[idx] = currentBand.copy(
                            centerFreq = newFreq,
                            gainDb = newGain.coerceIn(minGain, maxGain)
                        )
                        onBandsChange(updatedBands)
                        try {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (_: Exception) {}
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            // 1. Grid lines (dB lines)
            val dbSteps = listOf(12f, 6f, 0f, -6f, -12f)
            dbSteps.forEach { db ->
                val y = gainToY(db, height)
                val isZero = db == 0f
                drawLine(
                    color = if (isZero) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (isZero) 1.5f else 1f,
                    pathEffect = if (!isZero) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null
                )
            }

            // Grid lines (Frequency vertical lines)
            val freqSteps = listOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
            freqSteps.forEach { f ->
                val x = freqToX(f, width)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }

            // 2. Live Audio Spectrum Bars behind the curve
            if (showSpectrum && rmsLevel > 0.001f) {
                val numBars = 32
                val barWidth = width / numBars
                for (b in 0 until numBars) {
                    val barFrac = b.toFloat() / numBars
                    val freqWeight = sin(barFrac * Math.PI).toFloat()
                    val barHeight = (rmsLevel * 2.5f * freqWeight * (height * 0.7f)).coerceIn(2f, height * 0.85f)
                    val x = b * barWidth
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent),
                            startY = height - barHeight,
                            endY = height
                        ),
                        topLeft = Offset(x + 2f, height - barHeight),
                        size = Size(barWidth - 4f, barHeight)
                    )
                }
            }

            // 3. Smooth Parametric EQ Response Curve
            val curvePath = Path()
            val fillPath = Path()

            val stepPx = 3f
            var first = true

            var x = 0f
            while (x <= width) {
                val f = xToFreq(x, width)
                var netGainDb = 0.0f
                if (eqEnabled) {
                    for (band in bands) {
                        if (band.gainDb != 0.0f) {
                            val ratio = (f / band.centerFreq) - (band.centerFreq / f)
                            val qTerm = ratio / (1.0f / band.q)
                            val filterResponse = band.gainDb / (1.0f + qTerm * qTerm)
                            netGainDb += filterResponse
                        }
                    }
                }

                val y = gainToY(netGainDb, height)

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

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.35f), accentColor.copy(alpha = 0.05f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            drawPath(
                path = curvePath,
                color = if (eqEnabled) accentColor else Color.White.copy(alpha = 0.35f),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 4. Interactive EQ Nodes
            if (eqEnabled) {
                bands.forEachIndexed { idx, band ->
                    val bx = freqToX(band.centerFreq, width)
                    val by = gainToY(band.gainDb, height)
                    val isSelected = selectedBandIndex == idx

                    drawCircle(
                        color = accentColor.copy(alpha = if (isSelected) 0.50f else 0.25f),
                        radius = if (isSelected) 18f else 12f,
                        center = Offset(bx, by)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = if (isSelected) 8f else 6f,
                        center = Offset(bx, by)
                    )
                    drawCircle(
                        color = accentColor,
                        radius = if (isSelected) 6f else 4.5f,
                        center = Offset(bx, by)
                    )
                }
            }
        }
    }
}
