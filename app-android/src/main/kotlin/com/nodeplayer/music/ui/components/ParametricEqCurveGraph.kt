package com.nodeplayer.music.ui.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.EqBandConfig
import com.nodeplayer.ui.components.EQ_BAR_WEIGHTS
import com.nodeplayer.ui.components.EQ_CURVE_STROKE
import com.nodeplayer.ui.components.EQ_DASH_DB_EFFECT
import com.nodeplayer.ui.components.EQ_DASH_FREQ_EFFECT
import com.nodeplayer.ui.components.EQ_DB_STEPS
import com.nodeplayer.ui.components.EQ_FREQ_STEPS
import com.nodeplayer.ui.components.eqFreqToX
import com.nodeplayer.ui.components.eqGainToY
import com.nodeplayer.ui.components.eqXToFreq
import com.nodeplayer.ui.components.eqYToGain
import com.nodeplayer.ui.components.evaluateEqResponseDb
import kotlin.math.*

private val EQ_BG_SHAPE = RoundedCornerShape(20.dp)
private val EQ_BG_COLOR = Color(0xFF0B0D16).copy(alpha = 0.85f)

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
    val view = LocalView.current
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
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
                            gainDb = newGain.coerceIn(-12.0f, 12.0f)
                        )
                        onBandsChange(updatedBands.toList())
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
                        val barCount = EQ_BAR_WEIGHTS.size
                        val barWidth = width / barCount
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
                                for (b in 0 until barCount) {
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
