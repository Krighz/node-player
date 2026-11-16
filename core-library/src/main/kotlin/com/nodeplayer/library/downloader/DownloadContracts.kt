package com.nodeplayer.library.downloader

import com.nodeplayer.model.Track
import com.nodeplayer.model.storage.AudioStreamOption
import com.nodeplayer.model.storage.DownloadContainerFormat
import com.nodeplayer.model.storage.DownloadQualityPreset
import java.io.File

/**
 * Contract for dynamically resolving an audio stream option for a track or video ID.
 */
fun interface AudioStreamOptionResolver {
    suspend fun resolveBestOption(
        videoId: String,
        preset: DownloadQualityPreset,
        preferredFormat: DownloadContainerFormat
    ): Result<AudioStreamOption>
}

/**
 * Contract for streaming audio bytes from a URL to a local destination file.
 */
fun interface AudioStreamDownloader {
    suspend fun downloadToFile(
        url: String,
        destinationPartFile: File,
        expectedLength: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit
    ): Result<Long>
}

/**
 * Contract for tagging and finalizing downloaded audio files.
 */
interface DownloadTagger {
    suspend fun tagAndFinalize(
        tempPartFile: File,
        destinationFile: File,
        track: Track,
        option: AudioStreamOption
    ): Result<File>
}

/**
 * Default atomic rename tagger when platform-specific tag embedding is not bound.
 */
class DefaultDownloadTagger : DownloadTagger {
    override suspend fun tagAndFinalize(
        tempPartFile: File,
        destinationFile: File,
        track: Track,
        option: AudioStreamOption
    ): Result<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            val renamed = tempPartFile.renameTo(destinationFile)
            if (!renamed) {
                // Fallback to copy and delete if cross-filesystem
                tempPartFile.copyTo(destinationFile, overwrite = true)
                tempPartFile.delete()
            }
            destinationFile
        }
    }
}
