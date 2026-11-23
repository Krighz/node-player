package com.nodeplayer.library.downloader

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import com.nodeplayer.model.storage.AudioStreamOption
import com.nodeplayer.model.storage.DownloadContainerFormat
import com.nodeplayer.model.storage.DownloadQualityPreset
import com.nodeplayer.model.storage.DownloadState
import com.nodeplayer.model.storage.DownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust multi-tier queue manager for offline audio downloads.
 * Handles concurrency bounding, exponential backoff retries, network auto-pause/resume,
 * and lifecycle transitions.
 */
class DownloadQueueManager(
    private val downloadsDir: File,
    private val streamOptionResolver: AudioStreamOptionResolver,
    private val streamDownloader: AudioStreamDownloader,
    private val tagger: DownloadTagger = DefaultDownloadTagger(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialConcurrentLimit: Int = 2,
    initialWifiOnly: Boolean = false,
    private val onDownloadCompleted: ((File, Track) -> Unit)? = null
) {

    private companion object {
        const val TAG = "download/queue-manager"
        const val MAX_RETRIES = 3
    }

    private val mutex = Mutex()
    private val taskMap = LinkedHashMap<String, DownloadTask>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    @Volatile
    var concurrentLimit: Int = initialConcurrentLimit.coerceIn(1, 4)
        private set

    @Volatile
    var wifiOnly: Boolean = initialWifiOnly
        private set

    @Volatile
    var isConnected: Boolean = true
        private set

    @Volatile
    var isWifi: Boolean = true
        private set

    private var semaphore = Semaphore(concurrentLimit)

    init {
        downloadsDir.mkdirs()
    }

    /**
     * Enqueues a track for offline download.
     */
    fun enqueue(
        track: Track,
        option: AudioStreamOption? = null,
        preset: DownloadQualityPreset = DownloadQualityPreset.HIGHEST,
        format: DownloadContainerFormat = DownloadContainerFormat.AUTO
    ): DownloadTask {
        val taskId = track.id.toString()
        val task = DownloadTask(
            id = taskId,
            track = track,
            selectedOption = option,
            state = DownloadState.QUEUED
        )

        scope.launch(dispatcher) {
            mutex.withLock {
                taskMap[taskId] = task
                publishTasksLocked()
            }
            dispatchQueuePump()
        }

        return task
    }

    /**
     * Pauses an active or queued download task by user request.
     */
    fun pause(taskId: String) {
        scope.launch(dispatcher) {
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)

            mutex.withLock {
                val current = taskMap[taskId] ?: return@withLock
                if (!current.isTerminal) {
                    taskMap[taskId] = current.copy(state = DownloadState.PAUSED_USER, speedBytesPerSec = 0L)
                    publishTasksLocked()
                }
            }
        }
    }

    /**
     * Resumes a paused task.
     */
    fun resume(taskId: String) {
        scope.launch(dispatcher) {
            mutex.withLock {
                val current = taskMap[taskId] ?: return@withLock
                if (current.state == DownloadState.PAUSED_USER || current.state == DownloadState.PAUSED_NETWORK) {
                    taskMap[taskId] = current.copy(state = DownloadState.QUEUED, errorMessage = null)
                    publishTasksLocked()
                }
            }
            dispatchQueuePump()
        }
    }

    /**
     * Cancels and removes a task, cleaning up any temporary `.part` file.
     */
    fun cancel(taskId: String) {
        scope.launch(dispatcher) {
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)

            mutex.withLock {
                taskMap.remove(taskId)
                publishTasksLocked()
            }

            // Cleanup partial file
            val partFile = File(downloadsDir, "$taskId.part")
            if (partFile.exists()) {
                partFile.delete()
            }
            dispatchQueuePump()
        }
    }

    /**
     * Retries a failed task.
     */
    fun retry(taskId: String) {
        scope.launch(dispatcher) {
            mutex.withLock {
                val current = taskMap[taskId] ?: return@withLock
                if (current.state == DownloadState.FAILED) {
                    taskMap[taskId] = current.copy(state = DownloadState.QUEUED, attempts = 0, errorMessage = null)
                    publishTasksLocked()
                }
            }
            dispatchQueuePump()
        }
    }

    /**
     * Clears all completed or failed tasks from the in-memory queue.
     */
    fun clearCompleted() {
        scope.launch(dispatcher) {
            mutex.withLock {
                taskMap.values.removeAll { it.isTerminal }
                publishTasksLocked()
            }
        }
    }

    /**
     * Updates concurrent worker limit (`1..4`).
     */
    fun updateConcurrentLimit(newLimit: Int) {
        val clamped = newLimit.coerceIn(1, 4)
        if (concurrentLimit != clamped) {
            concurrentLimit = clamped
            semaphore = Semaphore(clamped)
            dispatchQueuePump()
        }
    }

    /**
     * Updates Wi-Fi only restriction setting.
     */
    fun updateWifiOnly(enabled: Boolean) {
        wifiOnly = enabled
        evaluateNetworkConditions()
    }

    /**
     * Notifies the manager of current connectivity and network type.
     */
    fun updateNetworkState(connected: Boolean, wifi: Boolean) {
        isConnected = connected
        isWifi = wifi
        evaluateNetworkConditions()
    }

    private fun evaluateNetworkConditions() {
        val allowed = isConnected && (!wifiOnly || isWifi)
        scope.launch(dispatcher) {
            if (!allowed) {
                // Pause currently downloading or queued tasks due to network
                mutex.withLock {
                    for ((id, task) in taskMap) {
                        if (task.state == DownloadState.DOWNLOADING || task.state == DownloadState.RESOLVING) {
                            activeJobs[id]?.cancel()
                            activeJobs.remove(id)
                            taskMap[id] = task.copy(state = DownloadState.PAUSED_NETWORK, speedBytesPerSec = 0L)
                        }
                    }
                    publishTasksLocked()
                }
            } else {
                // Resume tasks that were paused due to network
                var resumedAny = false
                mutex.withLock {
                    for ((id, task) in taskMap) {
                        if (task.state == DownloadState.PAUSED_NETWORK) {
                            taskMap[id] = task.copy(state = DownloadState.QUEUED)
                            resumedAny = true
                        }
                    }
                    if (resumedAny) {
                        publishTasksLocked()
                    }
                }
                if (resumedAny) {
                    dispatchQueuePump()
                }
            }
        }
    }

    /**
     * Pumps the queue to start any pending QUEUED tasks up to semaphore availability.
     */
    private fun dispatchQueuePump() {
        val allowed = isConnected && (!wifiOnly || isWifi)
        if (!allowed) return

        scope.launch(dispatcher) {
            val queuedTasks = mutex.withLock {
                taskMap.values.filter { it.state == DownloadState.QUEUED && !activeJobs.containsKey(it.id) }
            }

            for (task in queuedTasks) {
                if (activeJobs.containsKey(task.id)) continue
                val job = launchWorker(task.id)
                activeJobs[task.id] = job
            }
        }
    }

    private fun launchWorker(taskId: String): Job = scope.launch(dispatcher) {
        semaphore.acquire()
        try {
            executeDownloadLoop(taskId)
        } finally {
            semaphore.release()
            activeJobs.remove(taskId)
            dispatchQueuePump()
        }
    }

    private suspend fun executeDownloadLoop(taskId: String) {
        var currentTask = mutex.withLock { taskMap[taskId] } ?: return
        if (currentTask.state != DownloadState.QUEUED) return

        val path = currentTask.track.path
        val videoId = when {
            path.startsWith("nodeplayer://youtube/track/") -> path.removePrefix("nodeplayer://youtube/track/").substringBefore("?").substringBefore("/")
            path.startsWith("nodeplayer://youtube/") -> path.removePrefix("nodeplayer://youtube/").substringBefore("?").substringBefore("/")
            path.startsWith("online://") -> path.removePrefix("online://").substringBefore("?").substringBefore("/")
            path.contains("watch?v=") -> path.substringAfter("watch?v=").substringBefore("&").substringBefore("/")
            path.contains("youtu.be/") -> path.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            path.length == 11 && !path.contains("/") && !path.contains(".") -> path
            else -> "query:${currentTask.track.title} ${currentTask.track.artist}".trim()
        }
        var currentAttempt = currentTask.attempts

        while (currentAttempt < MAX_RETRIES) {
            currentAttempt++

            // Step 1: Resolving option if not selected
            var streamOption = currentTask.selectedOption
            if (streamOption == null) {
                updateTaskState(taskId, DownloadState.RESOLVING, attempts = currentAttempt)
                val resolveRes = streamOptionResolver.resolveBestOption(
                    videoId = videoId,
                    preset = DownloadQualityPreset.HIGHEST,
                    preferredFormat = DownloadContainerFormat.AUTO
                )

                if (resolveRes.isFailure) {
                    val err = resolveRes.exceptionOrNull()?.message ?: "Failed resolving stream URL"
                    NodeLog.w(TAG, "Attempt $currentAttempt: Resolve error: $err")
                    if (currentAttempt >= MAX_RETRIES) {
                        updateTaskState(taskId, DownloadState.FAILED, errorMessage = err)
                        return
                    }
                    delay(calculateBackoffMs(currentAttempt))
                    continue
                }
                streamOption = resolveRes.getOrThrow()
                mutex.withLock {
                    val existing = taskMap[taskId] ?: return
                    taskMap[taskId] = existing.copy(selectedOption = streamOption)
                    publishTasksLocked()
                }
            }

            // Step 2: Downloading
            updateTaskState(taskId, DownloadState.DOWNLOADING, attempts = currentAttempt)
            val partFile = File(downloadsDir, "$taskId.part")

            val downloadRes = streamDownloader.downloadToFile(
                url = streamOption.url,
                destinationPartFile = partFile,
                expectedLength = streamOption.contentLengthBytes,
                onProgress = { downloaded, total, speed ->
                    scope.launch(dispatcher) {
                        mutex.withLock {
                            val t = taskMap[taskId] ?: return@withLock
                            if (t.state == DownloadState.DOWNLOADING) {
                                taskMap[taskId] = t.copy(
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    speedBytesPerSec = speed
                                )
                                publishTasksLocked()
                            }
                        }
                    }
                }
            )

            if (downloadRes.isSuccess) {
                // Step 3: Tagging & Finalizing
                updateTaskState(taskId, DownloadState.TAGGING, speedBytesPerSec = 0L)

                val containerExt = streamOption.container.lowercase()
                val safeFileName = "${sanitizeFileName(currentTask.track.artist)} - ${sanitizeFileName(currentTask.track.title)}.$containerExt"
                val finalFile = File(downloadsDir, safeFileName)

                val tagRes = tagger.tagAndFinalize(partFile, finalFile, currentTask.track, streamOption)
                if (tagRes.isSuccess) {
                    val savedFile = tagRes.getOrThrow()
                    mutex.withLock {
                        val t = taskMap[taskId] ?: return@withLock
                        taskMap[taskId] = t.copy(
                            state = DownloadState.COMPLETED,
                            localFilePath = savedFile.absolutePath,
                            downloadedBytes = savedFile.length(),
                            totalBytes = savedFile.length(),
                            speedBytesPerSec = 0L
                        )
                        publishTasksLocked()
                    }
                    onDownloadCompleted?.invoke(savedFile, currentTask.track)
                    NodeLog.i(TAG, "Download completed successfully: ${savedFile.name}")
                    return
                } else {
                    val err = tagRes.exceptionOrNull()?.message ?: "Tagging failed"
                    updateTaskState(taskId, DownloadState.FAILED, errorMessage = err)
                    return
                }
            } else {
                val error = downloadRes.exceptionOrNull()
                if (error is CancellationException) {
                    // Task was paused or cancelled
                    return
                }

                NodeLog.w(TAG, "Attempt $currentAttempt: Download failed: ${error?.message}")
                if (currentAttempt >= MAX_RETRIES) {
                    updateTaskState(taskId, DownloadState.FAILED, errorMessage = error?.message ?: "Download failed after $MAX_RETRIES attempts")
                    return
                }

                // If stream expired, invalidate cached option so next attempt re-resolves
                if (error?.message?.contains("expired", ignoreCase = true) == true ||
                    error?.message?.contains("403", ignoreCase = true) == true ||
                    error?.message?.contains("410", ignoreCase = true) == true
                ) {
                    mutex.withLock {
                        val existing = taskMap[taskId] ?: return@withLock
                        taskMap[taskId] = existing.copy(selectedOption = null)
                    }
                }

                delay(calculateBackoffMs(currentAttempt))
            }
        }
    }

    private suspend fun updateTaskState(
        taskId: String,
        state: DownloadState,
        attempts: Int? = null,
        errorMessage: String? = null,
        speedBytesPerSec: Long? = null
    ) {
        mutex.withLock {
            val current = taskMap[taskId] ?: return@withLock
            taskMap[taskId] = current.copy(
                state = state,
                attempts = attempts ?: current.attempts,
                errorMessage = errorMessage ?: current.errorMessage,
                speedBytesPerSec = speedBytesPerSec ?: current.speedBytesPerSec
            )
            publishTasksLocked()
        }
    }

    private fun publishTasksLocked() {
        _tasks.value = taskMap.values.toList()
    }

    private fun calculateBackoffMs(attempt: Int): Long {
        return (1000L * (1 shl (attempt - 1).coerceAtLeast(0))).coerceAtMost(8000L)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "Track" }
    }
}
