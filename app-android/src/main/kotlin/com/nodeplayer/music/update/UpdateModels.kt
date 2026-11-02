package com.nodeplayer.music.update

import java.io.File

/**
 * Metadata for an available application update.
 */
data class AppUpdateInfo(
    val versionName: String,
    val releaseTag: String,
    val downloadUrl: String,
    val sha256Url: String,
    val releaseNotes: String,
    val assetSize: Long = 0L,
    val publishedAt: String = ""
)

/**
 * Result of an update check attempt.
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class RateLimited(val message: String) : UpdateCheckResult()
    data class NetworkError(val message: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * State machine for the download and verification phase.
 */
sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadStatus()
    data object Verifying : DownloadStatus()
    data class ReadyToInstall(val apkFile: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
