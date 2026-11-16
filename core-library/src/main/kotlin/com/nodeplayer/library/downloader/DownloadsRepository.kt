package com.nodeplayer.library.downloader

import kotlinx.coroutines.flow.Flow

data class DownloadedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val filePath: String,
    val sizeBytes: Long,
    val artUri: String?,
    val downloadedAt: Long
)

interface DownloadsRepository {
    fun observeDownloaded(): Flow<List<DownloadedTrack>>
    suspend fun totalBytes(): Long
    suspend fun delete(trackId: String): Result<Unit>
}
