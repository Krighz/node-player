package com.nodeplayer.dsp

import com.nodeplayer.model.LoudnessAnalysisResult
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * ITU-R BS.1770-4 / EBU R128 Integrated Loudness Analyzer.
 */
object LoudnessR128 {

    /**
     * Measures integrated loudness in LUFS for mono or multi-channel audio [samples].
     * [sampleRate]: e.g. 44100 or 48000.
     * [channels]: number of interleaved channels (e.g. 1 or 2).
     * [targetLevelLufs]: target playback level (default -14.0 LUFS).
     * [clipGuard]: if true, caps gain so that peak + gain <= 0 dBFS.
     */
    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int = 2,
        targetLevelLufs: Float = -14.0f,
        clipGuard: Boolean = true
    ): LoudnessAnalysisResult {
        if (samples.isEmpty()) {
            return LoudnessAnalysisResult(
                integratedLufs = -70.0f,
                peakDbfs = -100.0f,
                recommendedGainDb = 0.0f
            )
        }

        val totalFrames = samples.size / channels
        var maxPeak = 0.0f
        for (i in samples.indices) {
            val a = abs(samples[i])
            if (a > maxPeak) maxPeak = a
        }
        val peakDbfs = if (maxPeak > 1e-6f) 20f * log10(maxPeak) else -100f

        // K-weighting filter coefficients for 48kHz (scaled appropriately for other rates)
        // Stage 1: High shelf (+4 dB at 1.5 kHz)
        val stage1 = BiquadFilter().apply {
            configureHighShelf(sampleRate.toFloat(), 1500f, 4.0f)
        }
        // Stage 2: High pass (RLB weighting at 38 Hz)
        val stage2 = BiquadFilter().apply {
            configureHighPass(sampleRate.toFloat(), 38f, 0.5f)
        }

        // Filter all channels
        val filtered = FloatArray(samples.size)
        // Separate filter state per channel
        val stage1Filters = Array(channels) {
            BiquadFilter().apply { configureHighShelf(sampleRate.toFloat(), 1500f, 4.0f) }
        }
        val stage2Filters = Array(channels) {
            BiquadFilter().apply { configureHighPass(sampleRate.toFloat(), 38f, 0.5f) }
        }

        for (ch in 0 until channels) {
            var idx = ch
            while (idx < samples.size) {
                var s = samples[idx]
                s = stage1Filters[ch].processSample(s)
                s = stage2Filters[ch].processSample(s)
                filtered[idx] = s
                idx += channels
            }
        }

        // 400ms blocks with 75% overlap (100ms hop)
        val blockSizeFrames = (sampleRate * 0.400).toInt().coerceAtLeast(1)
        val hopSizeFrames = (sampleRate * 0.100).toInt().coerceAtLeast(1)

        val blockPowers = ArrayList<Float>()
        var frame = 0
        while (frame + blockSizeFrames <= totalFrames) {
            var sumSquare = 0.0
            for (ch in 0 until channels) {
                var sampleIdx = frame * channels + ch
                val endIdx = (frame + blockSizeFrames) * channels + ch
                while (sampleIdx < endIdx) {
                    val v = filtered[sampleIdx]
                    sumSquare += v * v
                    sampleIdx += channels
                }
            }
            val meanSquare = (sumSquare / (blockSizeFrames * channels)).toFloat()
            blockPowers.add(meanSquare)
            frame += hopSizeFrames
        }

        if (blockPowers.isEmpty()) {
            return LoudnessAnalysisResult(
                integratedLufs = -70.0f,
                peakDbfs = peakDbfs,
                recommendedGainDb = 0.0f
            )
        }

        // Absolute gate: -70 LKFS (power threshold ~ 1e-7)
        val absThresholdPower = 1e-7f
        val aboveAbsGate = ArrayList<Float>()
        var sumAboveAbs = 0.0
        for (p in blockPowers) {
            if (p > absThresholdPower) {
                aboveAbsGate.add(p)
                sumAboveAbs += p
            }
        }

        if (aboveAbsGate.isEmpty()) {
            return LoudnessAnalysisResult(
                integratedLufs = -70.0f,
                peakDbfs = peakDbfs,
                recommendedGainDb = 0.0f
            )
        }

        val ungatedMeanPower = (sumAboveAbs / aboveAbsGate.size).toFloat()
        val ungatedLoudness = -0.691f + 10f * log10(ungatedMeanPower)

        // Relative gate: -10 LU below ungated loudness
        val relThresholdLoudness = ungatedLoudness - 10.0f
        var gatedSum = 0.0
        var gatedCount = 0
        for (p in aboveAbsGate) {
            val blockLoudness = -0.691f + 10f * log10(p)
            if (blockLoudness > relThresholdLoudness) {
                gatedSum += p
                gatedCount++
            }
        }

        val integratedLufs = if (gatedCount > 0) {
            -0.691f + 10f * log10((gatedSum / gatedCount).toFloat())
        } else {
            ungatedLoudness
        }

        var recommendedGain = targetLevelLufs - integratedLufs
        if (clipGuard && (peakDbfs + recommendedGain > 0.0f)) {
            recommendedGain = -peakDbfs
        }

        return LoudnessAnalysisResult(
            integratedLufs = integratedLufs,
            peakDbfs = peakDbfs,
            recommendedGainDb = recommendedGain
        )
    }
}
