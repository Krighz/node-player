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
        limiter.threshold = 0.90f

        // Feed signals ranging from -5.0f to +5.0f (massive +14 dBFS overload)
        val step = 0.005f
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
    fun testLimiterStrictlyLinearBelowThreshold() {
        val limiter = SoftLimiter()
        limiter.enabled = true
        limiter.threshold = 0.90f

        // Test normal audio levels from -0.90f to +0.90f
        val testLevels = floatArrayOf(-0.90f, -0.75f, -0.50f, -0.20f, -0.01f, 0.0f, 0.01f, 0.20f, 0.50f, 0.75f, 0.90f)
        for (sample in testLevels) {
            val output = limiter.process(sample)
            assertEquals("Output must be bit-exact equal to input below threshold ($sample)", sample, output, 0.0f)
        }
    }

    @Test
    fun testPreampLinearGainAccuracy() {
        val preamp = PreampGain()
        preamp.enabled = true
        preamp.gainDb = 6.0206f // Exactly 2.0x linear gain

        val sample = 0.25f
        val out = preamp.process(sample)
        assertEquals(0.50f, out, 0.001f)

        // Preamp attenuation -6 dB
        preamp.gainDb = -6.0206f
        val outAttenuated = preamp.process(sample)
        assertEquals(0.125f, outAttenuated, 0.001f)
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

        // Album mode: -2.0 dB
        rp.setOffset(trackGain = 3.0f, albumGain = -2.0f, mode = ReplayGainMode.ALBUM, preampDb = 0.0f)
        val expectedAlbumGain = 10.0.pow(-2.0 / 20.0).toFloat()
        assertEquals(expectedAlbumGain, rp.linearGain, 0.001f)

        // Mode OFF: exactly 1.0f (0 dB)
        rp.setOffset(trackGain = 3.0f, albumGain = -2.0f, mode = ReplayGainMode.OFF, preampDb = 0.0f)
        assertEquals(1.0f, rp.linearGain, 0.001f)
    }

    @Test
    fun testParametricEqSineBoostAt1kHz() {
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

        // Generate 1000 Hz sine wave for 4410 samples (100 ms)
        val numSamples = 4410
        val input = FloatArray(numSamples)
        val output = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            input[i] = sin(2.0 * Math.PI * centerFreq * t).toFloat() * 0.2f // -14 dBFS input
            val out = eq.processStereoSample(input[i], input[i])
            output[i] = out.first
        }

        // Skip filter settling time (first 1000 samples) and measure over an exact integer number of cycles:
        // At 1000 Hz and 44100 Hz, 10 complete cycles = exactly 441 samples.
        // We measure over 20 complete cycles = 882 samples (from index 2000 to 2882).
        val startSample = 2000
        val evalSamples = 882
        var inputSumSquares = 0.0
        var outputSumSquares = 0.0
        for (i in startSample until (startSample + evalSamples)) {
            inputSumSquares += (input[i] * input[i]).toDouble()
            outputSumSquares += (output[i] * output[i]).toDouble()
        }

        val inputRms = sqrt(inputSumSquares / evalSamples)
        val outputRms = sqrt(outputSumSquares / evalSamples)
        val measuredGainLinear = outputRms / inputRms
        val measuredGainDb = 20.0 * log10(measuredGainLinear)

        // Peaking filter gain at center frequency must be accurate within 0.1 dB
        assertEquals(boostDb.toDouble(), measuredGainDb, 0.10)
    }

    @Test
    fun testParametricEqSineBoostAtLowAndHighFreqs() {
        val sampleRate = 44100
        val eq = ParametricEqualizer()
        eq.setSampleRate(sampleRate.toFloat())
        eq.enabled = true

        // Test Bass (125 Hz) boost
        val bassFreq = 125.0f
        val bassBoost = 4.0f
        eq.updateBands(listOf(EqBandConfig(index = 2, centerFreq = bassFreq, gainDb = bassBoost, q = 1.414f)))

        var inPeak = 0.0f
        var outPeak = 0.0f
        for (i in 0 until 4410) {
            val t = i.toDouble() / sampleRate
            val s = sin(2.0 * Math.PI * bassFreq * t).toFloat() * 0.2f
            val out = eq.processStereoSample(s, s).first
            if (i > 2000) {
                inPeak = max(inPeak, abs(s))
                outPeak = max(outPeak, abs(out))
            }
        }
        val bassGainDb = 20.0 * log10((outPeak / inPeak).toDouble())
        assertEquals(bassBoost.toDouble(), bassGainDb, 0.8)

        // Test Treble (8000 Hz) cut (-6 dB)
        val trebleFreq = 8000.0f
        val trebleCut = -6.0f
        eq.updateBands(listOf(EqBandConfig(index = 8, centerFreq = trebleFreq, gainDb = trebleCut, q = 1.414f)))

        inPeak = 0.0f
        outPeak = 0.0f
        for (i in 0 until 4410) {
            val t = i.toDouble() / sampleRate
            val s = sin(2.0 * Math.PI * trebleFreq * t).toFloat() * 0.2f
            val out = eq.processStereoSample(s, s).first
            if (i > 2000) {
                inPeak = max(inPeak, abs(s))
                outPeak = max(outPeak, abs(out))
            }
        }
        val trebleGainDb = 20.0 * log10((outPeak / inPeak).toDouble())
        assertEquals(trebleCut.toDouble(), trebleGainDb, 0.8)
    }

    @Test
    fun testFullDspChainOverloadProtection() {
        val chain = DspChain()
        chain.sampleRate = 44100

        // Configure aggressive boost: +6 dB preamp, +10 dB EQ at 1 kHz, +4 dB ReplayGain
        val config = AudioEffectConfig(
            eqEnabled = true,
            bands = listOf(EqBandConfig(index = 5, centerFreq = 1000f, gainDb = 10.0f, q = 1.414f)),
            preampGainDb = 6.0f,
            limiterEnabled = true,
            replayGainMode = ReplayGainMode.TRACK,
            replayGainPreampDb = 4.0f
        )
        chain.applyConfig(config)

        // Feed full scale 0 dBFS 1 kHz sine wave
        val numSamples = 2000
        val buffer = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / 44100
            buffer[i] = sin(2.0 * Math.PI * 1000.0 * t).toFloat() * 1.0f // 1.0f peak
        }

        chain.processInterleavedFloat(buffer, 0, buffer.size)

        // Limiter must strictly ensure no sample ever exceeds 1.0f despite massive +20 dB internal boost!
        for (sample in buffer) {
            assertTrue("Sample must not exceed 1.0f under limiter protection! Was: $sample", abs(sample) <= 1.00001f)
            assertTrue("Sample must not be NaN or Infinity! Was: $sample", !sample.isNaN() && !sample.isInfinite())
        }
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
