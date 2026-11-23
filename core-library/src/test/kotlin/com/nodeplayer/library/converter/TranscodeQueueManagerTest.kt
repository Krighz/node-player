package com.nodeplayer.library.converter

import com.nodeplayer.model.converter.AudioTranscoder
import com.nodeplayer.model.converter.TranscodeJob
import com.nodeplayer.model.converter.TranscodePreset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class TranscodeQueueManagerTest {

    private class FakeAudioTranscoder : AudioTranscoder {
        var shouldFailTranscode = false
        var shouldFailIntegrity = false
        var transcodeDelayMs = 0L

        override suspend fun transcode(job: TranscodeJob, onProgress: (Float) -> Unit): Result<File> {
            if (transcodeDelayMs > 0L) {
                delay(transcodeDelayMs)
            }
            if (shouldFailTranscode) {
                return Result.failure(IllegalStateException("Simulated transcode failure"))
            }
            job.output.parentFile?.mkdirs()
            job.output.writeBytes(ByteArray(500))
            onProgress(1.0f)
            return Result.success(job.output)
        }

        override suspend fun verifyIntegrity(file: File, expectedDurationMs: Long): Boolean {
            if (shouldFailIntegrity) return false
            return file.exists() && file.length() > 0L
        }
    }

    private lateinit var tempDir: File
    private lateinit var transcoder: FakeAudioTranscoder

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("transcode_test").toFile()
        transcoder = FakeAudioTranscoder()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testScanDirectoryFindsOnlyFlacAndSkipsExistingOpus() {
        val manager = TranscodeQueueManager(transcoder)

        val subDir = File(tempDir, "Album").apply { mkdirs() }
        val flac1 = File(subDir, "track1.flac").apply { writeBytes(ByteArray(1000)) }
        val flac2 = File(subDir, "track2.flac").apply { writeBytes(ByteArray(1000)) }
        val mp3 = File(subDir, "track3.mp3").apply { writeBytes(ByteArray(1000)) }

        // Existing opus for track1
        File(subDir, "track1.opus").apply { writeBytes(ByteArray(500)) }

        val jobs = manager.scanDirectory(
            sourceDir = tempDir,
            targetDir = null,
            preset = TranscodePreset.PRESET_128,
            overwriteExisting = false
        )

        // Only flac2 should be prepared as a job because track1.opus already exists
        assertEquals(1, jobs.size)
        assertEquals(flac2.absolutePath, jobs.first().input.absolutePath)
        assertEquals(File(subDir, "track2.opus").absolutePath, jobs.first().output.absolutePath)
    }

    @Test
    fun testNormalBatchExecutionSuccess() = runTest {
        val manager = TranscodeQueueManager(transcoder, externalScope = this)

        val flac = File(tempDir, "song.flac").apply { writeBytes(ByteArray(2000)) }
        val outOpus = File(tempDir, "song.opus")

        val job = TranscodeJob(
            input = flac,
            output = outOpus,
            bitrateKbps = 128
        )

        manager.startBatch(
            jobs = listOf(job),
            keepOriginal = true,
            workersCount = 2
        ).join()

        val state = manager.state.value
        assertTrue(state is TranscodeBatchState.Completed)
        val summary = (state as TranscodeBatchState.Completed).summary
        assertEquals(1, summary.succeeded)
        assertEquals(0, summary.failed)
        assertTrue(flac.exists())
        assertTrue(outOpus.exists())
    }

    @Test
    fun testReplaceOriginalSafetyGateWhenIntegrityFails() = runTest {
        val manager = TranscodeQueueManager(transcoder, externalScope = this)

        transcoder.shouldFailIntegrity = true

        val flac = File(tempDir, "original.flac").apply { writeBytes(ByteArray(2000)) }
        val outOpus = File(tempDir, "original.opus")

        val job = TranscodeJob(
            input = flac,
            output = outOpus,
            bitrateKbps = 128
        )

        var dbUpdated = false

        manager.startBatch(
            jobs = listOf(job),
            keepOriginal = false, // replace original requested
            workersCount = 1,
            onDbUpdate = { _, _ -> dbUpdated = true }
        ).join()

        val state = manager.state.value
        assertTrue(state is TranscodeBatchState.Completed)
        val summary = (state as TranscodeBatchState.Completed).summary
        assertEquals(0, summary.succeeded)
        assertEquals(1, summary.failed)

        // Original file must remain 100% intact!
        assertTrue("Original FLAC must NOT be deleted when verification fails", flac.exists())
        assertFalse(dbUpdated)
        assertFalse("Final output should not exist", outOpus.exists())
        assertFalse("Partial temp file should be deleted", File(tempDir, "original.opus.tmp.opus").exists())
    }

    @Test
    fun testReplaceOriginalSucceedsAndDeletesOnlyAfterVerification() = runTest {
        val manager = TranscodeQueueManager(transcoder, externalScope = this)

        val flac = File(tempDir, "delete_me.flac").apply { writeBytes(ByteArray(2000)) }
        val outOpus = File(tempDir, "delete_me.opus")

        val job = TranscodeJob(
            input = flac,
            output = outOpus,
            bitrateKbps = 128
        )

        var dbUpdated = false

        manager.startBatch(
            jobs = listOf(job),
            keepOriginal = false,
            workersCount = 1,
            onDbUpdate = { _, _ -> dbUpdated = true }
        ).join()

        val state = manager.state.value
        assertTrue(state is TranscodeBatchState.Completed)
        val summary = (state as TranscodeBatchState.Completed).summary
        assertEquals(1, summary.succeeded)

        assertTrue(outOpus.exists())
        assertTrue(dbUpdated)
        assertFalse("Original FLAC was deleted safely after verification", flac.exists())
    }
}
