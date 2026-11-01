package com.nodeplayer.dsp

import com.nodeplayer.model.DynamicRangeResult
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Standard TT Dynamic Range (DR) Meter calculation.
 * Analyzes audio in 3-second blocks, compares the top 20% loudest RMS to peak.
 */
object DynamicRangeMeter {

    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int = 2
    ): DynamicRangeResult {
        if (samples.isEmpty()) {
            return DynamicRangeResult(
                score = 0,
                label = "Unknown",
                peakDbfs = -100f,
                rmsTop20Dbfs = -100f
            )
        }

        val totalFrames = samples.size / channels
        var maxPeak = 0.0f
        for (i in samples.indices) {
            val a = abs(samples[i])
            if (a > maxPeak) maxPeak = a
        }
        val peakDbfs = if (maxPeak > 1e-6f) 20f * log10(maxPeak) else -100f

        val blockSizeFrames = sampleRate * 3 // 3-second blocks
        val hopSizeFrames = sampleRate * 1 // 1-second hop (overlapping blocks)

        val blockRmsList = ArrayList<Float>()
        var frame = 0
        while (frame + blockSizeFrames <= totalFrames) {
            var sumSquare = 0.0
            val totalSamplesInBlock = blockSizeFrames * channels
            var sampleIdx = frame * channels
            val endIdx = sampleIdx + totalSamplesInBlock
            while (sampleIdx < endIdx) {
                val v = samples[sampleIdx]
                sumSquare += v * v
                sampleIdx++
            }
            val rms = sqrt(sumSquare / totalSamplesInBlock).toFloat()
            if (rms > 1e-6f) {
                blockRmsList.add(rms)
            }
            frame += hopSizeFrames
        }

        // If audio is shorter than 3s, calculate whole-track RMS
        if (blockRmsList.isEmpty()) {
            var sumSquare = 0.0
            for (v in samples) {
                sumSquare += v * v
            }
            val rms = sqrt(sumSquare / samples.size).toFloat()
            blockRmsList.add(rms.coerceAtLeast(1e-6f))
        }

        // Sort block RMS descending
        blockRmsList.sortDescending()

        // Top 20% loudest blocks
        val topCount = ceil(blockRmsList.size * 0.20).toInt().coerceAtLeast(1)
        var sumTopSquares = 0.0
        for (i in 0 until topCount) {
            val r = blockRmsList[i]
            sumTopSquares += r * r
        }
        val rmsTop20 = sqrt(sumTopSquares / topCount).toFloat()
        val rmsTop20Dbfs = if (rmsTop20 > 1e-6f) 20f * log10(rmsTop20) else -100f

        val drFloat = if (maxPeak > 1e-6f && rmsTop20 > 1e-6f) {
            20f * log10(maxPeak / rmsTop20)
        } else {
            0f
        }
        val drScore = drFloat.roundToInt().coerceAtLeast(0)

        val label = when {
            drScore <= 7 -> "Heavily compressed"
            drScore in 8..11 -> "Moderately dynamic"
            drScore in 12..15 -> "Very dynamic"
            else -> "Audiophile dynamic"
        }

        return DynamicRangeResult(
            score = drScore,
            label = label,
            peakDbfs = peakDbfs,
            rmsTop20Dbfs = rmsTop20Dbfs
        )
    }
}
