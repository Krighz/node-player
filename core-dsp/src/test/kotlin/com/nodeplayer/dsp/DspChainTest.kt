package com.nodeplayer.dsp

import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.EqBandConfig
import com.nodeplayer.model.ReplayGainMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.*

class DspChainTest {

    @Test
    fun testLimiterNeverExceedsFullScale() {
        val limiter = SoftLimiter()
        limiter.enabled = true
        limiter.threshold = 0.9885f

        // Feed signals ranging from -5.0f to +5.0f (massive overload)
        val step = 0.01f
        var maxPeak = 0.0f
        var input = -5.0f
        while (input <= 5.0f) {
            val output = limiter.process(input)
            val absOut = abs(output)
            if (absOut > maxPeak) {
                maxPeak = absOut
            }
            assertTrue("Output must not exceed 1.0f (full scale). Was: $output for input $input", absOut <= 1.00001f)
            input += step
        }
        assertTrue("Max peak under limiter should reach near 1.0f without clipping. Was: $maxPeak", maxPeak > 0.98f)
    }

    @Test
    fun testReplayGainAppliesRightOffset() {
        val rp = ReplayGainProcessor()
        rp.enabled = true

        // +3.0 dB track gain with 0 preamp
        rp.setOffset(trackGain = 3.0f, albumGain = -1.0f, mode = ReplayGainMode.TRACK, preampDb = 0.0f)
        val expectedGain = 10.0.pow(3.0 / 20.0).toFloat()
        assertEquals(expectedGain, rp.linearGain, 0.001f)

        val sample = 0.5f
        val processed = rp.process(sample)
        assertEquals(sample * expectedGain, processed, 0.001f)

        // Album mode
        rp.setOffset(trackGain = 3.0f, albumGain = -2.0f, mode = ReplayGainMode.ALBUM, preampDb = 0.0f)
        val expectedAlbumGain = 10.0.pow(-2.0 / 20.0).toFloat()
        assertEquals(expectedAlbumGain, rp.linearGain, 0.001f)

        // Mode OFF
        rp.setOffset(trackGain = 3.0f, albumGain = -2.0f, mode = ReplayGainMode.OFF, preampDb = 0.0f)
        assertEquals(1.0f, rp.linearGain, 0.001f)
    }

    @Test
    fun testParametricEqSineBoost() {
        val sampleRate = 44100
        val centerFreq = 1000.0f
        val boostDb = 6.0f

        val eq = ParametricEqualizer()
        eq.setSampleRate(sampleRate.toFloat())
        eq.enabled = true
        eq.updateBands(
            listOf(
                EqBandConfig(index = 5, centerFreq = centerFreq, gainDb = boostDb, q = 1.414f)
            )
        )

        // Generate 1000 Hz sine wave
        val numSamples = 4410
        val input = FloatArray(numSamples)
        val output = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            input[i] = sin(2.0 * Math.PI * centerFreq * t).toFloat() * 0.2f // -14 dBFS input
            val out = eq.processStereoSample(input[i], input[i])
            output[i] = out.first
        }

        // Measure steady state peak (last 2000 samples)
        var inputPeak = 0.0f
        var outputPeak = 0.0f
        for (i in 2000 until numSamples) {
            inputPeak = max(inputPeak, abs(input[i]))
            outputPeak = max(outputPeak, abs(output[i]))
        }

        val measuredGainLinear = outputPeak / inputPeak
        val measuredGainDb = 20.0 * log10(measuredGainLinear.toDouble())

        // Expect approximately +6 dB boost at center frequency
        assertEquals(boostDb.toDouble(), measuredGainDb, 0.7) // Within 0.7 dB accuracy for biquad peak
    }

    @Test
    fun testBypassKeepsSignalUntouched() {
        val chain = DspChain()
        chain.sampleRate = 44100
        val config = AudioEffectConfig(
            eqEnabled = false,
            preampGainDb = 0.0f,
            limiterEnabled = false,
            replayGainMode = ReplayGainMode.OFF,
            stereoWidth = 1.0f,
            crossfeedEnabled = false
        )
        chain.applyConfig(config)

        val buffer = floatArrayOf(0.1f, -0.2f, 0.35f, -0.4f)
        val original = buffer.clone()
        chain.processInterleavedFloat(buffer, 0, buffer.size)

        for (i in buffer.indices) {
            assertEquals(original[i], buffer[i], 0.00001f)
        }
    }
}
