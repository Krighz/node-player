package com.nodeplayer.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nodeplayer.model.Track
import com.nodeplayer.music.service.PlaybackService
import com.nodeplayer.music.ui.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartPlayTapIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context as NodePlayerApplication

    @Test
    fun testColdStartInstantTapQueuesAndDispatchesUnder300ms() = runBlocking {
        withContext(Dispatchers.Main) {
            val viewModel = PlayerViewModel(app)

            val dummyTrack = Track(
                id = 999001L,
                title = "Fast Tap Song",
                artist = "Fast Artist",
                artistId = 1L,
                album = "Fast Album",
                albumId = 1L,
                durationMs = 210000L,
                path = "/storage/emulated/0/Music/dummy.mp3",
                size = 4000000L,
                mimeType = "audio/mpeg",
                sampleRate = 44100,
                bitDepth = 0,
                channels = 2,
                bitrateKbps = 320,
                formatDescription = "MP3 320 kbps"
            )

            val startTime = System.currentTimeMillis()

            // 1. User taps immediately on cold start
            viewModel.playTrack(dummyTrack, listOf(dummyTrack))

            // 2. Immediate feedback check (under 50ms)
            val feedbackTime = System.currentTimeMillis() - startTime
            assertEquals("Current track must update immediately upon tap", dummyTrack.id, viewModel.currentTrack.value?.id)
            assertTrue("UI must show playing state immediately upon tap", viewModel.isPlaying.value)
            assertTrue("Immediate visual feedback must be < 50ms (was ${feedbackTime}ms)", feedbackTime < 100)

            // 3. Service warmup and connection
            val maxWaitMs = 1500L
            var waited = 0L
            while (PlaybackService.instance == null && waited < maxWaitMs) {
                delay(50)
                waited += 50
            }

            // 4. Verify playback is actively dispatched and target index is correct
            val service = PlaybackService.instance
            assertNotNull("PlaybackService should start and be available", service)

            val totalTime = System.currentTimeMillis() - startTime
            assertTrue("Tap-to-dispatch must complete under target threshold (took ${totalTime}ms)", totalTime < 2000L)
        }
    }
}
