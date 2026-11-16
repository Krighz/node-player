package com.nodeplayer.library.converter

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.converter.AudioTranscoder
import com.nodeplayer.model.converter.ConverterMath
import com.nodeplayer.model.converter.TranscodeBatchSummary
import com.nodeplayer.model.converter.TranscodeJob
import com.nodeplayer.model.converter.TranscodePreset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * State of a batch transcoding session.
 */
sealed interface TranscodeBatchState {
    data object Idle : TranscodeBatchState
    data class Scanning(val directory: File) : TranscodeBatchState
    data class Ready(
        val jobs: List<TranscodeJob>,
        val totalInputBytes: Long,
        val estimatedOutputBytes: Long,
        val preset: TranscodePreset
    ) : TranscodeBatchState
    data class Running(
        val totalFiles: Int,
        val completedFiles: Int,
        val currentFileName: String,
        val currentFileProgress: Float,
        val overallProgress: Float
    ) : TranscodeBatchState
    data class Completed(val summary: TranscodeBatchSummary) : TranscodeBatchState
    data object Cancelled : TranscodeBatchState
}

/**
 * Orchestrator for scanning, estimating, and safely executing batch audio transcoding.
 */
class TranscodeQueueManager(
    private val transcoder: AudioTranscoder,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "converter/queue"
    }

    private val _state = MutableStateFlow<TranscodeBatchState>(TranscodeBatchState.Idle)
    val state: StateFlow<TranscodeBatchState> = _state.asStateFlow()

    private var currentBatchJob: Job? = null

    /**
     * Scans [sourceDir] recursively for FLAC files and prepares [TranscodeJob] items.
     */
    fun scanDirectory(
        sourceDir: File,
        targetDir: File?,
        preset: TranscodePreset,
        overwriteExisting: Boolean = false,
        vbr: Boolean = true,
        application: String = "audio",
        compressionLevel: Int = 10
    ): List<TranscodeJob> {
        _state.value = TranscodeBatchState.Scanning(sourceDir)

        val flacFiles = mutableListOf<File>()
        findFlacFilesRecursive(sourceDir, flacFiles)

        val jobs = mutableListOf<TranscodeJob>()
        var totalInputBytes = 0L

        for (file in flacFiles) {
            val outDir = targetDir ?: file.parentFile ?: sourceDir
            val outFile = File(outDir, "${file.nameWithoutExtension}.opus")

            if (!overwriteExisting && outFile.exists() && outFile.length() > 0L) {
                continue
            }

            totalInputBytes += file.length()
            jobs.add(
                TranscodeJob(
                    input = file,
                    output = outFile,
                    bitrateKbps = preset.bitrateKbps,
                    vbr = vbr,
                    application = application,
                    compressionLevel = compressionLevel
                )
            )
        }

        // Rough estimation assuming standard 35MB FLAC averages ~240s duration
        val estimatedOutputBytes = jobs.sumOf {
            val estimatedDurationMs = (it.input.length() / (44100 * 2 * 2)) * 1000L
            ConverterMath.estimatedOutputBytes(estimatedDurationMs.coerceAtLeast(60_000L), it.bitrateKbps)
        }

        val readyState = TranscodeBatchState.Ready(
            jobs = jobs,
            totalInputBytes = totalInputBytes,
            estimatedOutputBytes = estimatedOutputBytes,
            preset = preset
        )
        _state.value = readyState
        return jobs
    }

    /**
     * Prepares [TranscodeJob] items from an explicit list of [inputFiles].
     */
    fun prepareJobs(
        inputFiles: List<File>,
        targetDir: File?,
        preset: TranscodePreset,
        overwriteExisting: Boolean = false,
        vbr: Boolean = true,
        application: String = "audio",
        compressionLevel: Int = 10
    ): List<TranscodeJob> {
        val jobs = mutableListOf<TranscodeJob>()
        for (file in inputFiles) {
            val outDir = targetDir ?: file.parentFile ?: File(".")
            val outFile = File(outDir, "${file.nameWithoutExtension}.opus")

            if (!overwriteExisting && outFile.exists() && outFile.length() > 0L) {
                continue
            }

            jobs.add(
                TranscodeJob(
                    input = file,
                    output = outFile,
                    bitrateKbps = preset.bitrateKbps,
                    vbr = vbr,
                    application = application,
                    compressionLevel = compressionLevel
                )
            )
        }
        return jobs
    }

    private fun findFlacFilesRecursive(directory: File, result: MutableList<File>) {
        if (!directory.exists() || !directory.isDirectory) return
        val children = directory.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                findFlacFilesRecursive(child, result)
            } else if (child.isFile && child.extension.equals("flac", ignoreCase = true)) {
                result.add(child)
            }
        }
    }

    /**
     * Executes the prepared batch of transcode jobs.
     */
    fun startBatch(
        jobs: List<TranscodeJob>,
        keepOriginal: Boolean,
        workersCount: Int = 2,
        onDbUpdate: (suspend (oldPath: String, newPath: String) -> Unit)? = null,
        onRescan: (suspend () -> Unit)? = null
    ): Job {
        if (jobs.isEmpty()) {
            _state.value = TranscodeBatchState.Completed(
                TranscodeBatchSummary(0, 0, 0, 0L, 0L)
            )
            return Job().apply { complete() }
        }

        currentBatchJob?.cancel()

        val batchJob = externalScope.launch {
            val semaphore = Semaphore(workersCount.coerceIn(1, 4))
            val totalFiles = jobs.size
            val completedCount = AtomicInteger(0)
            val succeededCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val totalInputBytes = AtomicLong(0L)
            val totalOutputBytes = AtomicLong(0L)
            val failedFiles = ConcurrentLinkedQueue<Pair<File, String>>()

            _state.value = TranscodeBatchState.Running(
                totalFiles = totalFiles,
                completedFiles = 0,
                currentFileName = jobs.first().input.name,
                currentFileProgress = 0f,
                overallProgress = 0f
            )

            val tasks = jobs.map { job ->
                async {
                    semaphore.withPermit {
                        if (!isActive) return@withPermit

                        val inputBytes = job.input.length()
                        totalInputBytes.addAndGet(inputBytes)

                        // 1. Safe temporary output destination
                        val tempOutput = File(job.output.parentFile, "${job.output.name}.tmp.opus")
                        val singleJob = job.copy(output = tempOutput)

                        try {
                            _state.value = TranscodeBatchState.Running(
                                totalFiles = totalFiles,
                                completedFiles = completedCount.get(),
                                currentFileName = job.input.name,
                                currentFileProgress = 0f,
                                overallProgress = completedCount.get().toFloat() / totalFiles.toFloat()
                            )

                            // 2. Transcode to temp file
                            val transcodeResult = transcoder.transcode(singleJob) { fileProgress ->
                                val done = completedCount.get().toFloat()
                                val overall = (done + fileProgress) / totalFiles.toFloat()
                                _state.value = TranscodeBatchState.Running(
                                    totalFiles = totalFiles,
                                    completedFiles = completedCount.get(),
                                    currentFileName = job.input.name,
                                    currentFileProgress = fileProgress,
                                    overallProgress = overall.coerceIn(0f, 1f)
                                )
                            }

                            if (transcodeResult.isFailure) {
                                throw transcodeResult.exceptionOrNull() ?: IllegalStateException("Transcode failed")
                            }

                            // 3. Verify integrity of temp output
                            val isVerified = transcoder.verifyIntegrity(tempOutput)
                            if (!isVerified) {
                                throw IllegalStateException("Integrity check failed for transcoded output")
                            }

                            // 4. Atomically commit temp file to final destination
                            if (job.output.exists()) {
                                job.output.delete()
                            }
                            val renamed = tempOutput.renameTo(job.output)
                            if (!renamed) {
                                tempOutput.copyTo(job.output, overwrite = true)
                                tempOutput.delete()
                            }

                            totalOutputBytes.addAndGet(job.output.length())
                            succeededCount.incrementAndGet()

                            // 5. Replace original safety gate: delete original ONLY if verified and committed
                            if (!keepOriginal) {
                                onDbUpdate?.invoke(job.input.absolutePath, job.output.absolutePath)
                                job.input.delete()
                            }
                        } catch (e: Throwable) {
                            tempOutput.delete()
                            failedCount.incrementAndGet()
                            failedFiles.add(Pair(job.input, e.message ?: "Unknown error"))
                            NodeLog.w(TAG, "File transcode failed: ${e.message}")
                        } finally {
                            completedCount.incrementAndGet()
                        }
                    }
                }
            }

            tasks.awaitAll()

            val summary = TranscodeBatchSummary(
                totalFiles = totalFiles,
                succeeded = succeededCount.get(),
                failed = failedCount.get(),
                totalInputBytes = totalInputBytes.get(),
                totalOutputBytes = totalOutputBytes.get(),
                failedFiles = failedFiles.toList()
            )

            try {
                onRescan?.invoke()
            } catch (_: Throwable) {}

            _state.value = TranscodeBatchState.Completed(summary)
        }
        currentBatchJob = batchJob
        return batchJob
    }

    /**
     * Cancels active batch transcoding and cleans up partial files.
     */
    fun cancel() {
        currentBatchJob?.cancel()
        currentBatchJob = null
        _state.value = TranscodeBatchState.Cancelled
    }
}
