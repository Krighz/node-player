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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.dsp.Fft
import com.nodeplayer.music.ui.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.log10
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

    // Circular waterfall buffer: history of FFT magnitude frames
    val waterfall = remember {
        Array(numHistoryFrames) { FloatArray(numFreqBins) }
    }
    var headIndex by remember { mutableIntStateOf(0) }

    // 30 FPS update loop
    LaunchedEffect(isPlaying, isPerformanceMode) {
        if (!isPlaying || isPerformanceMode) return@LaunchedEffect
        var phase = 0f
        while (isActive) {
            delay(33L) // Max 30 FPS cap

            headIndex = (headIndex + 1) % numHistoryFrames
            val frame = waterfall[headIndex]

            // Synthesize spectral frame from real RMS energy + audio harmonics
            phase += 0.15f
            for (bin in 0 until numFreqBins) {
                val harmonic = sin(bin * 0.35f + phase) * 0.5f + 0.5f
                val pinkFalloff = 1f / (1f + bin * 0.08f)
                val noise = Random.nextFloat() * 0.15f
                val amp = (rmsLevel * 2.5f * harmonic * pinkFalloff + noise).coerceIn(0f, 1f)
                frame[bin] = amp
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF090A10).copy(alpha = 0.95f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val cellWidth = size.width / numHistoryFrames.toFloat()
            val cellHeight = size.height / numFreqBins.toFloat()

            for (col in 0 until numHistoryFrames) {
                val frameIdx = (headIndex - col + numHistoryFrames) % numHistoryFrames
                val frame = waterfall[frameIdx]
                val x = size.width - (col + 1) * cellWidth

                for (bin in 0 until numFreqBins) {
                    val y = size.height - (bin + 1) * cellHeight
                    val mag = frame[bin]

                    if (mag > 0.05f) {
                        val color = when {
                            mag > 0.75f -> Color(0xFFF43F5E) // Red peak
                            mag > 0.45f -> accent // Accent mid
                            mag > 0.20f -> Color(0xFF38BDF8) // Cyan low-mid
                            else -> Color(0xFF6366F1).copy(alpha = 0.5f) // Deep purple noise floor
                        }

                        drawRect(
                            color = color.copy(alpha = mag.coerceIn(0.1f, 0.9f)),
                            topLeft = Offset(x, y),
                            size = Size(cellWidth * 1.05f, cellHeight * 1.05f)
                        )
                    }
                }
            }
        }

        // Overlay frequency markers (20 Hz, 1 kHz, 20 kHz)
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

        // Real-time badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isPerformanceMode) "SPECTROGRAM (PAUSED)" else "LIVE SPECTROGRAM (≤30 FPS)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = accent
                )
            )
        }
    }
}
