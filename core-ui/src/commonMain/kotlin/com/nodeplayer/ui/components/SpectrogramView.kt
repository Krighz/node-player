package com.nodeplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SpectrogramView(
    isPlaying: Boolean,
    rmsLevel: Float,
    isPerformanceMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val numFreqBins = 48
    val numHistoryFrames = 64

    val waterfall = remember {
        Array(numHistoryFrames) { FloatArray(numFreqBins) }
    }
    var headIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(isPlaying, isPerformanceMode) {
        if (!isPlaying || isPerformanceMode) return@LaunchedEffect
        var phase = 0f
        while (isActive) {
            delay(33L) // 30 FPS cap

            headIndex = (headIndex + 1) % numHistoryFrames
            val frame = waterfall[headIndex]

            phase += 0.15f
            for (bin in 0 until numFreqBins) {
                val harmonic = sin(bin * 0.35f + phase) * 0.5f + 0.5f
                val pinkFalloff = 1f / (1f + bin * 0.08f)
                val noise = Random.nextFloat() * 0.15f
                val energy = (harmonic * pinkFalloff + noise) * (rmsLevel.coerceIn(0.1f, 1.0f))
                frame[bin] = energy.coerceIn(0f, 1f)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
    ) {
        val w = size.width
        val h = size.height
        val cellW = w / numFreqBins
        val cellH = h / numHistoryFrames

        for (row in 0 until numHistoryFrames) {
            val frameIdx = (headIndex - row + numHistoryFrames) % numHistoryFrames
            val frame = waterfall[frameIdx]
            val y = row * cellH

            for (col in 0 until numFreqBins) {
                val mag = frame[col]
                if (mag > 0.02f) {
                    val x = col * cellW
                    val color = accent.copy(alpha = (mag * 0.85f).coerceIn(0.05f, 0.95f))
                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH)
                    )
                }
            }
        }
    }
}
