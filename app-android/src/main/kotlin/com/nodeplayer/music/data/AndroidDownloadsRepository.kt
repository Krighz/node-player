package com.nodeplayer.music.data

import android.content.Context
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.downloader.DownloadedTrack
import com.nodeplayer.library.downloader.DownloadsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class AndroidDownloadsRepository(
    private val context: Context,
    private val database: NodeDatabase
) : DownloadsRepository {

    companion object {
        private const val TAG = "downloads/repository"
    }

    override fun observeDownloaded(): Flow<List<DownloadedTrack>> {
        return database.playerDao().getAllTracks().map { trackEntities ->
            trackEntities.map { it.toTrack() }
                .filter { it.isDownloaded }
                .map { track ->
                    val file = File(track.path)
                    val realSize = if (file.exists()) file.length() else track.size
                    val companionCover = File(file.parentFile, "${file.nameWithoutExtension}.jpg")
                    val artUri = if (companionCover.exists()) companionCover.toURI().toString() else null
                    DownloadedTrack(
                        id = track.id.toString(),
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                        filePath = track.path,
                        sizeBytes = realSize,
                        artUri = artUri,
                        downloadedAt = track.dateAdded
                    )
                }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        try {
            val list = database.playerDao().getAllTracksList().map { it.toTrack() }.filter { it.isDownloaded }
            list.sumOf { track ->
                val f = File(track.path)
                if (f.exists()) f.length() else track.size
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Failed to compute totalBytes: ${e.message}")
            0L
        }
    }

    override suspend fun delete(trackId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val idLong = trackId.toLongOrNull() ?: return@runCatching
            val entity = database.playerDao().getTrackById(idLong)
            if (entity != null) {
                val file = File(entity.path)
                if (file.exists()) {
                    file.delete()
                }
                val companionCover = File(file.parentFile, "${file.nameWithoutExtension}.jpg")
                if (companionCover.exists()) {
                    companionCover.delete()
                }
                database.playerDao().deleteTrackById(idLong)
                NodeLog.i(TAG, "Deleted downloaded track $trackId (${entity.title})")
            }
        }
    }
}
