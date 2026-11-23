package com.nodeplayer.model.storage

import com.nodeplayer.model.Track
import java.util.UUID

/**
 * Lifecycle state transitions for an offline track download task.
 */
enum class DownloadState {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    TAGGING,
    COMPLETED,
    PAUSED_USER,
    PAUSED_NETWORK,
    FAILED
}

/**
 * Quality selection strategy for audio stream downloading.
 */
enum class DownloadQualityPreset {
    ASK,
    HIGHEST,
    BALANCED,
    DATA_SAVER
}

/**
 * Container and codec format preference for offline audio files.
 */
enum class DownloadContainerFormat {
    AUTO,
    OPUS,
    M4A
}

/**
 * Dynamically resolved audio stream option with container, bitrate, and size metadata.
 */
data class AudioStreamOption(
    val itag: Int,
    val codec: String,
    val container: String,
    val bitrateKbps: Int,
    val contentLengthBytes: Long = 0L,
    val url: String
) {
    val displayLabel: String
        get() = "${container.uppercase()} • ${bitrateKbps} kbps"

    val sizeMbFormatted: String
        get() = if (contentLengthBytes > 0) {
            "%.1f MB".format(contentLengthBytes / (1024.0 * 1024.0))
        } else {
            "~%.1f MB".format((bitrateKbps * 180 / 8) / 1024.0) // Estimate for ~3min track
        }
}

/**
 * Active or completed offline track download item in the multi-tier queue.
 */
data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val track: Track,
    val selectedOption: AudioStreamOption? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val attempts: Int = 0,
    val errorMessage: String? = null,
    val localFilePath: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val isTerminal: Boolean
        get() = state == DownloadState.COMPLETED || state == DownloadState.FAILED
}

/**
 * Unified contract representing an app storage or cache partition.
 */
interface StorageCategory {
    val id: String
    val displayName: String
    val description: String
    val clearable: Boolean
    val limitSettingKey: String? get() = null
    suspend fun sizeBytes(): Long
    suspend fun clear(): Result<Unit>
}

/**
 * Calculated usage statistics for a storage partition.
 */
data class StorageUsageStats(
    val categoryId: String,
    val displayName: String,
    val sizeBytes: Long,
    val limitBytes: Long? = null,
    val clearable: Boolean = true
) {
    val sizeMbFormatted: String
        get() = "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
}
