package com.nodeplayer.music

import com.nodeplayer.dsp.BiquadFilter
import com.nodeplayer.model.AudioEffectConfig
import org.junit.Assert.*
import org.junit.Test

class EqualizerStateTest {

    @Test
    fun testUpdateIndividualBandGain() {
        val initialConfig = AudioEffectConfig()
        assertEquals(10, initialConfig.bands.size)

        // Modify 1 kHz band (index 5)
        val bandIndex = 5
        val updatedBands = initialConfig.bands.toMutableList()
        val targetBand = updatedBands[bandIndex]
        assertEquals(1000f, targetBand.centerFreq)

        val newGain = 4.5f
        updatedBands[bandIndex] = targetBand.copy(gainDb = newGain)
        val newConfig = initialConfig.copy(presetName = "Custom", bands = updatedBands)

        assertEquals("Custom", newConfig.presetName)
        assertEquals(4.5f, newConfig.bands[bandIndex].gainDb, 0.001f)
        // Ensure other bands are untouched
        assertEquals(0f, newConfig.bands[0].gainDb, 0.001f)
    }

    @Test
    fun testResetFlatActionZerosAllBandsAndPreamp() {
        val initialConfig = AudioEffectConfig(
            presetName = "Bass Boost",
            preampGainDb = -3.0f,
            bands = AudioEffectConfig.default10Bands().mapIndexed { idx, band ->
                band.copy(gainDb = if (idx < 3) 6.0f else 0.0f)
            }
        )

        // Simulate Reset action
        val flatBands = initialConfig.bands.map { it.copy(gainDb = 0f) }
        val flatConfig = initialConfig.copy(
            presetName = "Flat",
            preampGainDb = 0f,
            bands = flatBands
        )

        assertEquals("Flat", flatConfig.presetName)
        assertEquals(0f, flatConfig.preampGainDb, 0.001f)
        assertTrue(flatConfig.bands.all { it.gainDb == 0f })
    }

    @Test
    fun testDspBiquadFilterCalculationsWithUpdatedGains() {
        val sampleRate = 44100f
        val centerFreq = 1000f
        val q = 1.414f

        val filter = BiquadFilter()

        // Process impulse with 0 dB gain
        filter.configurePeaking(sampleRate, centerFreq, 0f, q)
        val outFlat = filter.processSample(1.0f)
        assertFalse(outFlat.isNaN())

        // Update gain to +6 dB (simulate vertical fader drag)
        filter.reset()
        filter.configurePeaking(sampleRate, centerFreq, 6.0f, q)
        val outBoost = filter.processSample(1.0f)

        assertFalse(outBoost.isNaN())
        assertFalse(outBoost.isInfinite())
        assertTrue("Output with boost ($outBoost) should exceed flat output ($outFlat)", outBoost > outFlat)

        // Update gain to -6 dB (simulate vertical fader cut)
        filter.reset()
        filter.configurePeaking(sampleRate, centerFreq, -6.0f, q)
        val outCut = filter.processSample(1.0f)

        assertFalse(outCut.isNaN())
        assertFalse(outCut.isInfinite())
        assertTrue("Output with cut ($outCut) should be less than flat output ($outFlat)", outCut < outFlat)
    }
}
