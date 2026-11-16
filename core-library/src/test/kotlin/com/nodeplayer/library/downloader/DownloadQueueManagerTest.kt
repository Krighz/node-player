package com.nodeplayer.library.downloader

import com.nodeplayer.model.Track
import com.nodeplayer.model.storage.AudioStreamOption
import com.nodeplayer.model.storage.DownloadContainerFormat
import com.nodeplayer.model.storage.DownloadQualityPreset
import com.nodeplayer.model.storage.DownloadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueManagerTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "np_download_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun sampleTrack(id: Long = 1L, title: String = "Test Song", artist: String = "Test Artist"): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = "Test Album",
            durationMs = 180_000L,
            path = "online://yt_$id"
        )
    }

    @Test
    fun testSuccessfulDownloadLifecycle() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val track = sampleTrack()
        val mockOption = AudioStreamOption(
            itag = 251,
            codec = "opus",
            container = "opus",
            bitrateKbps = 160,
            contentLengthBytes = 2048L,
            url = "https://example.com/audio.opus"
        )

        val manager = DownloadQueueManager(
            downloadsDir = tempDir,
            streamOptionResolver = { _, _, _ -> Result.success(mockOption) },
            streamDownloader = { _, destFile, expected, onProgress ->
                destFile.writeBytes(ByteArray(expected.toInt()))
                onProgress(expected, expected, 500_000L)
                Result.success(expected)
            },
            scope = testScope,
            dispatcher = testDispatcher,
            initialConcurrentLimit = 2,
            initialWifiOnly = false
        )

        val task = manager.enqueue(track)
        assertEquals(DownloadState.QUEUED, task.state)

        testScheduler.advanceUntilIdle()

        val tasks = manager.tasks.value
        assertEquals(1, tasks.size)
        val completedTask = tasks[0]
        assertEquals(DownloadState.COMPLETED, completedTask.state)
        assertNotNull(completedTask.localFilePath)
        assertTrue(File(completedTask.localFilePath!!).exists())
    }

    @Test
    fun testConcurrencyLimitEnforced() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val activeWorkers = AtomicInteger(0)
        val maxSimultaneous = AtomicInteger(0)

        val manager = DownloadQueueManager(
            downloadsDir = tempDir,
            streamOptionResolver = { _, _, _ ->
                Result.success(
                    AudioStreamOption(251, "opus", "opus", 160, 1000L, "https://example.com/audio")
                )
            },
            streamDownloader = { _, destFile, expected, _ ->
                val current = activeWorkers.incrementAndGet()
                var prevMax = maxSimultaneous.get()
                while (current > prevMax && !maxSimultaneous.compareAndSet(prevMax, current)) {
                    prevMax = maxSimultaneous.get()
                }

                delay(100) // Simulate streaming duration
                destFile.writeBytes(ByteArray(expected.toInt()))
                activeWorkers.decrementAndGet()
                Result.success(expected)
            },
            scope = testScope,
            dispatcher = testDispatcher,
            initialConcurrentLimit = 2,
            initialWifiOnly = false
        )

        // Enqueue 4 tracks
        manager.enqueue(sampleTrack(1L))
        manager.enqueue(sampleTrack(2L))
        manager.enqueue(sampleTrack(3L))
        manager.enqueue(sampleTrack(4L))

        testScheduler.advanceTimeBy(50)
        // Concurrency should not exceed limit of 2
        assertTrue(maxSimultaneous.get() <= 2, "Concurrent downloads must not exceed 2")

        testScheduler.advanceUntilIdle()

        val completed = manager.tasks.value.filter { it.state == DownloadState.COMPLETED }
        assertEquals(4, completed.size)
        assertTrue(maxSimultaneous.get() <= 2, "Max simultaneous workers must be <= 2")
    }

    @Test
    fun testWifiAutoPauseAndResume() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val manager = DownloadQueueManager(
            downloadsDir = tempDir,
            streamOptionResolver = { _, _, _ ->
                Result.success(AudioStreamOption(251, "opus", "opus", 160, 1000L, "https://example.com/audio"))
            },
            streamDownloader = { _, destFile, _, _ ->
                delay(500)
                destFile.writeBytes(ByteArray(1000))
                Result.success(1000L)
            },
            scope = testScope,
            dispatcher = testDispatcher,
            initialConcurrentLimit = 1,
            initialWifiOnly = true
        )

        manager.updateNetworkState(connected = true, wifi = true)
        manager.enqueue(sampleTrack(10L))

        testScheduler.advanceTimeBy(50)
        val activeTask = manager.tasks.value.firstOrNull()
        assertNotNull(activeTask)
        assertTrue(activeTask.state == DownloadState.DOWNLOADING || activeTask.state == DownloadState.RESOLVING)

        // Switch off Wi-Fi
        manager.updateNetworkState(connected = true, wifi = false)
        testScheduler.advanceUntilIdle()

        val pausedTask = manager.tasks.value.first()
        assertEquals(DownloadState.PAUSED_NETWORK, pausedTask.state)

        // Switch Wi-Fi back on
        manager.updateNetworkState(connected = true, wifi = true)
        testScheduler.advanceUntilIdle()

        val completedTask = manager.tasks.value.first()
        assertEquals(DownloadState.COMPLETED, completedTask.state)
    }

    @Test
    fun testRetryOnTransientErrorWithBackoff() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val downloadAttempts = AtomicInteger(0)

        val manager = DownloadQueueManager(
            downloadsDir = tempDir,
            streamOptionResolver = { _, _, _ ->
                Result.success(AudioStreamOption(251, "opus", "opus", 160, 1000L, "https://example.com/audio"))
            },
            streamDownloader = { _, destFile, expected, _ ->
                val attempt = downloadAttempts.incrementAndGet()
                if (attempt < 2) {
                    Result.failure(Exception("HTTP 403 Forbidden - stream expired"))
                } else {
                    destFile.writeBytes(ByteArray(expected.toInt()))
                    Result.success(expected)
                }
            },
            scope = testScope,
            dispatcher = testDispatcher,
            initialConcurrentLimit = 1,
            initialWifiOnly = false
        )

        manager.enqueue(sampleTrack(99L))
        testScheduler.advanceUntilIdle()

        assertEquals(2, downloadAttempts.get())
        val task = manager.tasks.value.first()
        assertEquals(DownloadState.COMPLETED, task.state)
    }
}
