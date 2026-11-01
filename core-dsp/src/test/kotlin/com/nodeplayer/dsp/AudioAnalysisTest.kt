package com.nodeplayer.dsp

import com.nodeplayer.model.HiResStatus
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioAnalysisTest {

    @Test
    fun testFftIdentity() {
        val n = 64
        val real = FloatArray(n) { sin(2.0 * PI * 4 * it / n).toFloat() }
        val imag = FloatArray(n)
        val copyR = real.clone()

        Fft.transform(real, imag, inverse = false)
        Fft.transform(real, imag, inverse = true)

        for (i in 0 until n) {
            assertEquals(copyR[i], real[i], 1e-4f)
            assertEquals(0f, imag[i], 1e-4f)
        }
    }

    @Test
    fun testFakeHiResDetectorDetects16kHzCutoff() {
        val sampleRate = 44100
        val durationSeconds = 1.0
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = FloatArray(numSamples)

        // Generate broadband white noise
        var seed = 123456789L
        for (i in 0 until numSamples) {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            samples[i] = (seed.toFloat() / 0x7fffffffL - 0.5f) * 0.5f
        }

        // Apply a sharp lowpass filter at 16 kHz (e.g. cascaded biquad lowpass)
        val lpf1 = BiquadFilter().apply { configureLowPass(sampleRate.toFloat(), 15800f, 0.707f) }
        val lpf2 = BiquadFilter().apply { configureLowPass(sampleRate.toFloat(), 15800f, 0.707f) }
        val lpf3 = BiquadFilter().apply { configureLowPass(sampleRate.toFloat(), 15800f, 0.707f) }
        val lpf4 = BiquadFilter().apply { configureLowPass(sampleRate.toFloat(), 15800f, 0.707f) }

        for (i in samples.indices) {
            var s = samples[i]
            s = lpf1.processSample(s)
            s = lpf2.processSample(s)
            s = lpf3.processSample(s)
            s = lpf4.processSample(s)
            samples[i] = s
        }

        val result = HiResAnalyzer.analyze(samples, sampleRate)
        assertEquals(HiResStatus.LIKELY_LOSSY, result.status)
        assertTrue("Confidence should be >= 80%", result.confidence >= 0.80f)
        assertTrue("Cutoff should be around 16 kHz, was ${result.cutoffFrequencyHz}", result.cutoffFrequencyHz in 14000f..17500f)
    }

    @Test
    fun testFakeHiResDetectorDetectsUpsampled96kHz() {
        val sampleRate = 96000
        val durationSeconds = 1.0
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = FloatArray(numSamples)

        // Generate noise and cut off at 21 kHz (CD Nyquist)
        var seed = 987654321L
        for (i in 0 until numSamples) {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            samples[i] = (seed.toFloat() / 0x7fffffffL - 0.5f) * 0.5f
        }

        val lpf1 = BiquadFilter().apply { configureLowPass(sampleRate.toFloat(), 21000f, 0.707f) }
        val lpf2 = BiquadFilter().apply { configureLowPass(sampleRate.toFloat(), 21000f, 0.707f) }
        val lpf3 = BiquadFilter().apply { configureLowPass(sampleRate.toFloat(), 21000f, 0.707f) }
        val lpf4 = BiquadFilter().apply { configureLowPass(sampleRate.toFloat(), 21000f, 0.707f) }

        for (i in samples.indices) {
            var s = samples[i]
            s = lpf1.processSample(s)
            s = lpf2.processSample(s)
            s = lpf3.processSample(s)
            s = lpf4.processSample(s)
            samples[i] = s
        }

        val result = HiResAnalyzer.analyze(samples, sampleRate)
        assertEquals(HiResStatus.LIKELY_UPSAMPLED, result.status)
        assertTrue("Cutoff should be around 21 kHz, was ${result.cutoffFrequencyHz}", result.cutoffFrequencyHz in 19000f..24500f)
    }

    @Test
    fun testDynamicRangeMeterCompressedVsDynamic() {
        val sampleRate = 44100
        val durationSeconds = 6.0
        val numSamples = (sampleRate * durationSeconds).toInt()

        // 1. Highly compressed signal: constant high-amplitude square/compressed wave
        val compressed = FloatArray(numSamples) { 0.95f }

        // 2. Highly dynamic signal: brief loud pulses, mostly quiet
        val dynamic = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            dynamic[i] = if ((i % (sampleRate * 2)) < (sampleRate * 0.1)) {
                0.95f // brief 100ms loud burst every 2 seconds
            } else {
                0.02f // quiet floor
            }
        }

        val drCompressed = DynamicRangeMeter.analyze(compressed, sampleRate, 1)
        val drDynamic = DynamicRangeMeter.analyze(dynamic, sampleRate, 1)

        assertTrue(
            "Dynamic signal DR (${drDynamic.score}) must score higher than compressed signal DR (${drCompressed.score})",
            drDynamic.score > drCompressed.score
        )
        assertTrue("Compressed signal should score DR <= 5, was ${drCompressed.score}", drCompressed.score <= 5)
        assertTrue("Dynamic signal should score DR >= 12, was ${drDynamic.score}", drDynamic.score >= 12)
    }

    @Test
    fun testLoudnessR128MeasuresReasonableLevels() {
        val sampleRate = 48000
        val durationSeconds = 2.0
        val numSamples = (sampleRate * durationSeconds).toInt()

        // 1 kHz sine wave at -12 dBFS (amplitude ~ 0.25)
        val amp = 0.25f
        val samples = FloatArray(numSamples * 2) // Stereo
        for (i in 0 until numSamples) {
            val v = amp * sin(2.0 * PI * 1000 * i / sampleRate).toFloat()
            samples[i * 2] = v
            samples[i * 2 + 1] = v
        }

        val result = LoudnessR128.analyze(samples, sampleRate, 2, targetLevelLufs = -14.0f)
        assertTrue("Loudness should be in reasonable range, was ${result.integratedLufs}", result.integratedLufs in -18f..-10f)
        assertTrue("Peak should be around -12 dBFS, was ${result.peakDbfs}", result.peakDbfs in -13f..-11f)
    }
}
