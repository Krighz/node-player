package com.nodeplayer.music

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.music.audio.CrossfadePlayerManager
import com.nodeplayer.music.service.SystemWideEqService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class SystemWideEqIntegrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        instrumentation.runOnMainSync {
            SystemWideEqService.start(context)
        }
        Thread.sleep(300)
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            SystemWideEqService.stop(context)
        }
        Thread.sleep(200)
    }

    @Test
    fun testServiceRunningStateAndAntiDoubleEqBypass() {
        var manager: CrossfadePlayerManager? = null

        instrumentation.runOnMainSync {
            manager = CrossfadePlayerManager(
                context = context,
                onTrackTransition = {},
                onMediaSessionPlayerSwapped = {}
            )
        }
        val mgr = manager!!

        // 1. Verify Service is running
        assertTrue("SystemWideEqService must be running", SystemWideEqService.isRunning)

        // 2. Apply config with EQ enabled
        val testConfig = AudioEffectConfig(
            eqEnabled = true,
            presetName = "Bass Boost",
            preampGainDb = 2.5f
        )

        instrumentation.runOnMainSync {
            mgr.applyEffectConfig(testConfig)
        }

        // 3. Verify internal biquad EQ is bypassed while System-Wide EQ is running (anti-double-EQ)
        assertFalse(
            "dspChainA internal biquad EQ must be bypassed when System-Wide EQ is running",
            mgr.dspChainA.equalizer.enabled
        )
        assertFalse(
            "dspChainB internal biquad EQ must be bypassed when System-Wide EQ is running",
            mgr.dspChainB.equalizer.enabled
        )
        assertEquals("Preamp gain must still be applied", 2.5f, mgr.dspChainA.preamp.gainDb, 1e-4f)

        // 4. Simulate stopping SystemWideEqService and verify internal EQ is restored
        instrumentation.runOnMainSync {
            mgr.onSystemEqStateChanged(false)
        }

        assertTrue(
            "dspChainA internal biquad EQ must be restored when System-Wide EQ is stopped",
            mgr.dspChainA.equalizer.enabled
        )
        assertTrue(
            "dspChainB internal biquad EQ must be restored when System-Wide EQ is stopped",
            mgr.dspChainB.equalizer.enabled
        )

        instrumentation.runOnMainSync {
            mgr.release()
        }
    }

    @Test
    fun testSessionAttachmentAndDynamicConfigUpdates() {
        // Create a real active AudioTrack to acquire a valid audioserver session ID
        val audioTrack = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(4096)
            .build()

        val testSessionId = audioTrack.audioSessionId
        val testPackage = "com.google.android.youtube"

        try {
            // 1. Attach session to service
            instrumentation.runOnMainSync {
                val service = SystemWideEqService.instance
                assertNotNull("SystemWideEqService instance must be available", service)
                service?.attachSession(testSessionId, testPackage)
            }

            val sessions = SystemWideEqService.activeSessionsState.value
            val attached = sessions.find { it.sessionId == testSessionId }
            assertNotNull("Session $testSessionId must be tracked in activeSessionsState", attached)
            assertEquals(testPackage, attached?.packageName)

            // 2. Dynamic config update (Presets & Bands)
            val rockPreset = AudioEffectConfig.createPreset("Rock").copy(preampGainDb = 1.0f)
            instrumentation.runOnMainSync {
                SystemWideEqService.updateConfig(rockPreset)
            }
            assertEquals("Rock", SystemWideEqService.currentConfig.presetName)

            // 3. Release session
            instrumentation.runOnMainSync {
                SystemWideEqService.instance?.releaseSession(testSessionId)
            }

            val remainingSessions = SystemWideEqService.activeSessionsState.value
            assertNull("Session $testSessionId must be removed after release", remainingSessions.find { it.sessionId == testSessionId })
        } finally {
            audioTrack.release()
        }
    }
}
