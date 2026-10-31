package com.nodeplayer.music

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nodeplayer.model.CrossfadeConfig
import com.nodeplayer.model.FadeCurve
import com.nodeplayer.model.Track
import com.nodeplayer.music.audio.CrossfadePlayerManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class CrossfadeLivePlaybackTest {

    companion object {
        private const val TAG = "CrossfadeLiveTest"
    }

    private fun getSampleTracks(): List<Track> {
        val t1 = Track(
            id = 1000187903L,
            title = "The Nights",
            artist = "Avicii",
            album = "i'm doing it all for love",
            durationMs = 178058L,
            path = "/storage/emulated/0/Music/The Nights.mp3",
            trackNumber = 1
        )
        val t2 = Track(
            id = 1000187920L,
            title = "LET THE WORLD BURN",
            artist = "Chris Grey",
            album = "LET THE WORLD BURN",
            durationMs = 163291L,
            path = "/storage/emulated/0/Music/LET THE WORLD BURN.mp3",
            trackNumber = 1
        )
        val shortTrack = Track(
            id = 1000121607L,
            title = "AUD-20241022-WA0002",
            artist = "Voice",
            album = "WhatsApp Audio",
            durationMs = 5980L,
            path = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-20241022-WA0002.opus",
            trackNumber = 1
        )
        return listOf(t1, t2, shortTrack)
    }

    @Test
    fun testLiveNormalCrossfadeRamp() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val context = instr.targetContext
        val tracks = getSampleTracks()
        var manager: CrossfadePlayerManager? = null

        instr.runOnMainSync {
            manager = CrossfadePlayerManager(
                context = context,
                onTrackTransition = { Log.d(TAG, "[TEST_LOG] Transitioned to: ${it.title}") },
                onMediaSessionPlayerSwapped = { Log.d(TAG, "[TEST_LOG] MediaSession player swapped") }
            ).apply {
                crossfadeConfig = CrossfadeConfig(
                    enabled = true,
                    durationSeconds = 2.0f,
                    curve = FadeCurve.EQUAL_POWER,
                    autoGapless = true
                )
            }
        }

        val mgr = manager!!

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] === START testLiveNormalCrossfadeRamp ===")
            mgr.playQueue(listOf(tracks[0], tracks[1]), 0)
        }

        // Wait for player to buffer and be ready
        var attempts = 0
        while (attempts < 30) {
            Thread.sleep(100)
            var ready = false
            instr.runOnMainSync {
                ready = mgr.activePlayer.playbackState == Player.STATE_READY
            }
            if (ready) break
            attempts++
        }

        // Trigger crossfade by seeking to 1500ms before track end
        instr.runOnMainSync {
            val dur = if (mgr.activePlayer.duration > 0) mgr.activePlayer.duration else tracks[0].durationMs
            Log.d(TAG, "[TEST_LOG] Track duration: ${dur}ms. Seeking to ${dur - 1500}ms to initiate crossfade.")
            mgr.seekTo(dur - 1500)
        }

        // Allow 4 seconds for crossfade ramp to progress and complete
        Thread.sleep(4000)

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Finished normal crossfade test. Active track idx=${mgr.currentQueueIndex}")
            mgr.release()
        }
    }

    @Test
    fun testLiveMidFadeSeek() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val context = instr.targetContext
        val tracks = getSampleTracks()
        var manager: CrossfadePlayerManager? = null

        instr.runOnMainSync {
            manager = CrossfadePlayerManager(
                context = context,
                onTrackTransition = {},
                onMediaSessionPlayerSwapped = {}
            ).apply {
                crossfadeConfig = CrossfadeConfig(
                    enabled = true,
                    durationSeconds = 3.0f,
                    curve = FadeCurve.EQUAL_POWER,
                    autoGapless = false
                )
            }
        }

        val mgr = manager!!

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] === START testLiveMidFadeSeek ===")
            mgr.playQueue(listOf(tracks[0], tracks[1]), 0)
        }

        Thread.sleep(500)

        // Trigger crossfade
        instr.runOnMainSync {
            val dur = tracks[0].durationMs
            Log.d(TAG, "[TEST_LOG] Seeking to ${dur - 2500}ms to initiate 3s crossfade")
            mgr.seekTo(dur - 2500)
        }

        // Wait 500ms into the fade (mid-fade)
        Thread.sleep(500)

        // Seek backwards mid-fade
        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Executing mid-fade seek to 10000ms")
            mgr.seekTo(10000L)
            assertFalse("Crossfade must be aborted on backward seek", mgr.isCrossfading)
            assertEquals("Active player volume must be restored to 1.0f", 1.0f, mgr.activePlayer.volume, 1e-3f)
        }

        Thread.sleep(500)

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Finished mid-fade seek test.")
            mgr.release()
        }
    }

    @Test
    fun testLiveMidFadeSkip() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val context = instr.targetContext
        val tracks = getSampleTracks()
        var manager: CrossfadePlayerManager? = null

        instr.runOnMainSync {
            manager = CrossfadePlayerManager(
                context = context,
                onTrackTransition = {},
                onMediaSessionPlayerSwapped = {}
            ).apply {
                crossfadeConfig = CrossfadeConfig(
                    enabled = true,
                    durationSeconds = 3.0f,
                    curve = FadeCurve.LINEAR,
                    autoGapless = false
                )
            }
        }

        val mgr = manager!!

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] === START testLiveMidFadeSkip ===")
            mgr.playQueue(listOf(tracks[0], tracks[1]), 0)
        }

        Thread.sleep(500)

        instr.runOnMainSync {
            val dur = tracks[0].durationMs
            Log.d(TAG, "[TEST_LOG] Seeking to ${dur - 2500}ms to initiate 3s crossfade")
            mgr.seekTo(dur - 2500)
        }

        Thread.sleep(500)

        // Skip mid-fade
        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Executing mid-fade skipNext")
            mgr.skipNext()
            assertFalse("Crossfade must be finished immediately on skip", mgr.isCrossfading)
            assertEquals("Queue index must be 1 (next track)", 1, mgr.currentQueueIndex)
        }

        Thread.sleep(500)

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Finished mid-fade skip test.")
            mgr.release()
        }
    }

    @Test
    fun testLiveMidFadePauseAndResume() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val context = instr.targetContext
        val tracks = getSampleTracks()
        var manager: CrossfadePlayerManager? = null

        instr.runOnMainSync {
            manager = CrossfadePlayerManager(
                context = context,
                onTrackTransition = {},
                onMediaSessionPlayerSwapped = {}
            ).apply {
                crossfadeConfig = CrossfadeConfig(
                    enabled = true,
                    durationSeconds = 3.0f,
                    curve = FadeCurve.LOGARITHMIC,
                    autoGapless = false
                )
            }
        }

        val mgr = manager!!

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] === START testLiveMidFadePauseAndResume ===")
            mgr.playQueue(listOf(tracks[0], tracks[1]), 0)
        }

        Thread.sleep(500)

        instr.runOnMainSync {
            val dur = tracks[0].durationMs
            Log.d(TAG, "[TEST_LOG] Seeking to ${dur - 2500}ms to initiate 3s crossfade")
            mgr.seekTo(dur - 2500)
        }

        Thread.sleep(500)

        // Pause mid-fade
        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Executing mid-fade pause")
            mgr.pause()
        }

        Thread.sleep(600)

        // Resume mid-fade
        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Executing mid-fade resume")
            mgr.play()
        }

        Thread.sleep(2500)

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Finished mid-fade pause and resume test.")
            mgr.release()
        }
    }

    @Test
    fun testLiveShortTrackClamping() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val context = instr.targetContext
        val tracks = getSampleTracks()
        var manager: CrossfadePlayerManager? = null

        instr.runOnMainSync {
            manager = CrossfadePlayerManager(
                context = context,
                onTrackTransition = {},
                onMediaSessionPlayerSwapped = {}
            ).apply {
                crossfadeConfig = CrossfadeConfig(
                    enabled = true,
                    durationSeconds = 8.0f, // 8s requested fade
                    curve = FadeCurve.EQUAL_POWER,
                    autoGapless = false
                )
            }
        }

        val mgr = manager!!

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] === START testLiveShortTrackClamping ===")
            mgr.playQueue(listOf(tracks[2], tracks[0]), 0)
        }

        Thread.sleep(500)

        instr.runOnMainSync {
            val dur = tracks[2].durationMs
            val clampedFade = (dur / 2).coerceAtLeast(500L)
            Log.d(TAG, "[TEST_LOG] Short track duration: ${dur}ms. Requested fade: 8000ms. Clamped effective fade: ${clampedFade}ms")
            mgr.seekTo(dur - clampedFade + 200)
        }

        Thread.sleep(2500)

        instr.runOnMainSync {
            Log.d(TAG, "[TEST_LOG] Finished short track clamping test.")
            mgr.release()
        }
    }
}
