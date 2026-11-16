package com.nodeplayer.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.ui.components.imageBitmapOf
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
    val pixelBuffer = remember { IntArray(numHistoryFrames * numFreqBins) }
    var frameTick by remember { mutableIntStateOf(0) }
    var headIndex by remember { mutableIntStateOf(0) }

    val peakArgb = remember { Color(0xFFF43F5E).toArgb() }
    val midArgb = remember(accent) { accent.toArgb() }
    val lowMidArgb = remember { Color(0xFF38BDF8).toArgb() }
    val floorArgb = remember { Color(0xFF6366F1).toArgb() }

    LaunchedEffect(isPlaying, isPerformanceMode) {
        if (!isPlaying || isPerformanceMode) return@LaunchedEffect
        var phase = 0f
        while (isActive) {
            delay(33L) // Max 30 FPS cap

            headIndex = (headIndex + 1) % numHistoryFrames
            val frame = waterfall[headIndex]

            phase += 0.15f
            for (bin in 0 until numFreqBins) {
                val harmonic = sin(bin * 0.35f + phase) * 0.5f + 0.5f
                val pinkFalloff = 1f / (1f + bin * 0.08f)
                val noise = Random.nextFloat() * 0.15f
                val amp = (rmsLevel * 2.5f * harmonic * pinkFalloff + noise).coerceIn(0f, 1f)
                frame[bin] = amp
            }
            frameTick++
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF090A10).copy(alpha = 0.95f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            @Suppress("UNUSED_VARIABLE")
            val tick = frameTick
            pixelBuffer.fill(0)

            for (col in 0 until numHistoryFrames) {
                val frameIdx = (headIndex - col + numHistoryFrames) % numHistoryFrames
                val frame = waterfall[frameIdx]
                val x = numHistoryFrames - 1 - col

                for (bin in 0 until numFreqBins) {
                    val mag = frame[bin]
                    if (mag > 0.05f) {
                        val y = numFreqBins - 1 - bin
                        val baseRgb = when {
                            mag > 0.75f -> peakArgb and 0x00FFFFFF
                            mag > 0.45f -> midArgb and 0x00FFFFFF
                            mag > 0.20f -> lowMidArgb and 0x00FFFFFF
                            else -> floorArgb and 0x00FFFFFF
                        }
                        val alphaScale = if (mag > 0.20f) mag.coerceIn(0.1f, 0.9f) else (mag * 0.5f).coerceIn(0.05f, 0.5f)
                        val alphaInt = (alphaScale * 255f).toInt().coerceIn(0, 255)
                        pixelBuffer[y * numHistoryFrames + x] = (alphaInt shl 24) or baseRgb
                    }
                }
            }

            val bmp = imageBitmapOf(pixelBuffer, numHistoryFrames, numFreqBins)
            drawImage(
                image = bmp,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("20 kHz", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f)))
            Text("1 kHz", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f)))
            Text("20 Hz", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f)))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isPerformanceMode) "SPECTROGRAM (PAUSED)" else "LIVE SPECTROGRAM (<= 30 FPS)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = accent
                )
            )
        }
    }
}
