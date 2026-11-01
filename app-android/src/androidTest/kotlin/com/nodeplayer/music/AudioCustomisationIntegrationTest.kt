package com.nodeplayer.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nodeplayer.dsp.*
import com.nodeplayer.model.*
import com.nodeplayer.music.data.AudioPresetEntity
import com.nodeplayer.music.data.NodeDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioCustomisationIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context as NodePlayerApplication

    @Test
    fun testRoomDatabaseAudioPresetStorage() = runBlocking {
        val dao = app.database.playerDao()

        val sampleConfig = AudioEffectConfig(
            eqEnabled = true,
            bands = listOf(
                EqBandConfig(0, 31.25f, 4.0f, 1.414f),
                EqBandConfig(1, 62.5f, 2.0f, 1.414f)
            ),
            toneControls = ToneControlConfig(bassGainDb = 3.0f, trebleGainDb = -2.0f),
            loudnessConfig = LoudnessConfig(enabled = true, strength = 0.75f),
            harmonicBassConfig = HarmonicBassConfig(enabled = true, frequencyHz = 65f, harmonicGain = 0.6f)
        )
        val json = AudioProfileSerializer.toJson(sampleConfig)

        val trackPreset = AudioPresetEntity(
            id = "track_9999",
            isAlbum = false,
            presetName = "Warm Vocal Track",
            configJson = json
        )
        dao.insertAudioPreset(trackPreset)

        val retrieved = dao.getAudioPresetById("track_9999")
        assertNotNull("Track preset must be stored in Room database", retrieved)
        assertEquals("Warm Vocal Track", retrieved?.presetName)

        val parsed = AudioProfileSerializer.fromJson(retrieved!!.configJson)
        assertEquals(3.0f, parsed.toneControls.bassGainDb, 0.01f)
        assertEquals(-2.0f, parsed.toneControls.trebleGainDb, 0.01f)
        assertTrue(parsed.loudnessConfig.enabled)
        assertEquals(0.75f, parsed.loudnessConfig.strength, 0.01f)

        // Cleanup
        dao.deleteAudioPresetById("track_9999")
        assertNull(dao.getAudioPresetById("track_9999"))
    }

    @Test
    fun testConvolverPartitionedPerformanceOnDevice() {
        val convolver = PartitionedConvolver()
        convolver.enabled = true
        convolver.blend = 0.8f

        // 512-tap synthetic room impulse response
        val ir = FloatArray(512)
        ir[0] = 1.0f
        ir[30] = 0.3f
        ir[75] = -0.2f
        for (i in 1 until 512) {
            ir[i] += kotlin.math.exp(-i / 80.0f) * 0.1f
        }
        convolver.loadImpulseResponse(ir)

        // Feed real-time audio buffer: 2048 samples (1024 stereo frames = 23.2ms @ 44.1kHz)
        val bufferSize = 2048
        val buffer = FloatArray(bufferSize) { ((it % 100) / 100.0f) * 0.2f }

        // Warm up ART JIT compiler
        for (w in 0 until 5) {
            convolver.processBlock(buffer, 0, buffer.size, 44100)
        }

        // Process consecutive blocks in steady-state and verify CPU usage stays strictly within real-time audio budget (< 100%)
        var avgCpu = 0.0f
        for (b in 0 until 10) {
            convolver.processBlock(buffer, 0, buffer.size, 44100)
            avgCpu += convolver.lastCpuUsagePercent
        }
        avgCpu /= 10.0f
        // Must stay strictly below 100% audio thread budget so it never stalls audio playback
        assertTrue("Convolver CPU usage must not stall audio thread (< 100% budget)! Was: $avgCpu%", avgCpu < 80.0f)
    }

    @Test
    fun testAbCompareLoudnessMatchOnPhone() {
        val boostedBands = listOf(
            EqBandConfig(0, 31.25f, 6.0f, 1.414f),
            EqBandConfig(1, 62.5f, 4.0f, 1.414f),
            EqBandConfig(2, 125.0f, 3.0f, 1.414f)
        )
        val offset = AbCompareHelper.calculateLoudnessOffsetDb(boostedBands, preampDb = 0.0f)
        assertTrue("A/B compare offset must be negative to match positive boost", offset < 0.0f)
    }

    @Test
    fun testAutoEqProfileParsingAndApplication() {
        val sampleAutoEq = """
            Preamp: -5.4 dB
            Filter 1: ON PK Fc 28 Hz Gain 6.0 dB Q 1.41
            Filter 2: ON PK Fc 105 Hz Gain -2.5 dB Q 0.71
            Filter 3: ON PK Fc 1000 Hz Gain -1.8 dB Q 2.00
            Filter 4: ON PK Fc 8000 Hz Gain 2.2 dB Q 0.71
        """.trimIndent()

        val profile = AutoEqParser.parse(sampleAutoEq, "Sony WH-1000XM4 AutoEQ")
        assertEquals(-5.4f, profile.preampDb, 0.01f)
        assertEquals(4, profile.filters.size)

        val bands = AutoEqParser.toEqBandConfigs(profile)
        assertEquals(10, bands.size)
    }
}
