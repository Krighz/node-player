package com.nodeplayer.music

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nodeplayer.dsp.CrossfadeCurveMath
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.CrossfadeConfig
import com.nodeplayer.model.FadeCurve
import com.nodeplayer.model.Track
import com.nodeplayer.music.audio.CrossfadePlayerManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class CrossfadeIntegrationTest {

    @Test
    fun testDualExoPlayerIsolatedDspChains() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var manager: CrossfadePlayerManager? = null

        instrumentation.runOnMainSync {
            manager = CrossfadePlayerManager(
                context = context,
                onTrackTransition = {},
                onMediaSessionPlayerSwapped = {}
            )
        }

        val mgr = manager!!

        // Verify playerA and playerB have distinct ExoPlayer instances
        assertNotNull(mgr.playerA)
        assertNotNull(mgr.playerB)
        assertNotSame("playerA and playerB must be independent instances", mgr.playerA, mgr.playerB)

        // Verify DSP chains are separate instances to prevent click/pop filter state bleed
        assertNotNull(mgr.dspChainA)
        assertNotNull(mgr.dspChainB)
        assertNotSame("dspChainA and dspChainB must be independent instances", mgr.dspChainA, mgr.dspChainB)
        assertNotSame("Audio processors must be independent instances", mgr.dspAudioProcessorA, mgr.dspAudioProcessorB)

        // Verify effect configuration applies to both chains
        val testConfig = AudioEffectConfig(eqEnabled = true, preampGainDb = 3.5f)
        instrumentation.runOnMainSync {
            mgr.applyEffectConfig(testConfig)
        }
        assertEquals(3.5f, mgr.dspChainA.preamp.gainDb, 1e-4f)
        assertEquals(3.5f, mgr.dspChainB.preamp.gainDb, 1e-4f)

        instrumentation.runOnMainSync {
            mgr.release()
        }
    }

    @Test
    fun testGaplessAutoDetection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var manager: CrossfadePlayerManager? = null

        instrumentation.runOnMainSync {
            manager = CrossfadePlayerManager(
                context = context,
                onTrackTransition = {},
                onMediaSessionPlayerSwapped = {}
            )
        }

        val mgr = manager!!

        val track1 = Track(
            id = 1,
            title = "Intro",
            artist = "Artist",
            album = "The Album",
            trackNumber = 1,
            discNumber = 1,
            durationMs = 60000,
            path = "/sdcard/1.flac"
        )
        val track2 = Track(
            id = 2,
            title = "Track 2",
            artist = "Artist",
            album = "The Album",
            trackNumber = 2,
            discNumber = 1,
            durationMs = 180000,
            path = "/sdcard/2.flac"
        )
        val trackDiffAlbum = Track(
            id = 3,
            title = "Different Song",
            artist = "Artist 2",
            album = "Other Album",
            trackNumber = 3,
            discNumber = 1,
            durationMs = 120000,
            path = "/sdcard/3.flac"
        )
        val liveTrack = Track(
            id = 4,
            title = "Live Jam",
            artist = "Band",
            album = "Single",
            genre = "Live Concert",
            trackNumber = 1,
            durationMs = 200000,
            path = "/sdcard/4.flac"
        )

        // Consecutive tracks in same album must be detected as gapless
        assertTrue("Consecutive tracks in same album must be gapless", mgr.isGaplessTransition(track1, track2))

        // Different album tracks must NOT be gapless
        assertFalse("Different album tracks must not be gapless", mgr.isGaplessTransition(track1, trackDiffAlbum))

        // Live genre must be gapless
        assertTrue("Live track must be gapless", mgr.isGaplessTransition(liveTrack, trackDiffAlbum))

        instrumentation.runOnMainSync {
            mgr.release()
        }
    }

    @Test
    fun testEqualPowerPowerCurveMath() {
        // Equal power curve must conserve power across all steps
        for (i in 0..100) {
            val progress = i / 100f
            val (outGain, inGain) = CrossfadeCurveMath.calculateGains(progress, FadeCurve.EQUAL_POWER)
            val power = outGain * outGain + inGain * inGain
            assertEquals("Power must equal 1.0 at step $progress", 1.0f, power, 0.002f)
        }
    }
}
