package com.nodeplayer.music.downloader

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.downloader.DownloadTagger
import com.nodeplayer.model.Track
import com.nodeplayer.model.storage.AudioStreamOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Android implementation of DownloadTagger.
 * Fetches cover artwork, remuxes the audio without re-encoding (-c:a copy),
 * and embeds ID3v2/MP4 metadata and cover artwork using ffmpeg-kit-audio.
 */
class AndroidDownloadTagger(private val context: Context) : DownloadTagger {

    companion object {
        private const val TAG = "download/android-tagger"
    }

    override suspend fun tagAndFinalize(
        tempPartFile: File,
        destinationFile: File,
        track: Track,
        option: AudioStreamOption
    ): Result<File> = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()

        // 1. Resolve and download cover art image if available
        var tempCoverFile: File? = null
        val resolvedVid: String? = when {
            track.path.startsWith("nodeplayer://youtube/track/") -> track.path.removePrefix("nodeplayer://youtube/track/").substringBefore("?").substringBefore("/")
            track.path.startsWith("nodeplayer://youtube/") -> track.path.removePrefix("nodeplayer://youtube/").substringBefore("?").substringBefore("/")
            track.path.startsWith("online://") -> track.path.removePrefix("online://").substringBefore("?").substringBefore("/")
            track.path.contains("watch?v=") -> track.path.substringAfter("watch?v=").substringBefore("&").substringBefore("?")
            track.path.contains("youtu.be/") -> track.path.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            track.path.length == 11 && !track.path.contains("/") -> track.path
            else -> null
        }
        val artUri: String? = when {
            resolvedVid != null -> "https://i.ytimg.com/vi/$resolvedVid/hqdefault.jpg"
            track.path.startsWith("http://") || track.path.startsWith("https://") -> track.path
            else -> null
        }

        if (!artUri.isNullOrBlank()) {
            try {
                val coverDest = File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                val url = URL(artUri)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        FileOutputStream(coverDest).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (coverDest.exists() && coverDest.length() > 0) {
                        tempCoverFile = coverDest
                        // Also persist companion cover file alongside final audio for offline display
                        val companionCover = File(destinationFile.parentFile, "${destinationFile.nameWithoutExtension}.jpg")
                        try {
                            coverDest.copyTo(companionCover, overwrite = true)
                        } catch (_: Exception) {}
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                NodeLog.w(TAG, "Failed to fetch cover art for ${track.title}: ${e.message}")
            }
        }

        // 2. Prepare atomic temporary file for FFmpeg output
        val tempTaggedFile = File(
            destinationFile.parentFile,
            "tagged_${System.currentTimeMillis()}_${destinationFile.name}"
        )

        val safeTitle = track.title.replace("\"", "").replace("'", "").trim()
        val safeArtist = track.artist.replace("\"", "").replace("'", "").trim()
        val safeAlbum = track.album.replace("\"", "").replace("'", "").trim()

        val ext = destinationFile.extension.lowercase()
        val isOpusOrOgg = ext == "opus" || ext == "ogg"

        val parts = mutableListOf(
            "-hide_banner",
            "-nostdin",
            "-y",
            "-i", tempPartFile.absolutePath
        )

        val hasCover = tempCoverFile != null && tempCoverFile.exists() && tempCoverFile.length() > 0
        if (hasCover && !isOpusOrOgg) {
            parts.add("-i")
            parts.add(tempCoverFile!!.absolutePath)
            parts.addAll(listOf("-map", "0:a", "-map", "1:v", "-c:a", "copy", "-c:v", "copy"))
            if (ext == "mp3") {
                parts.addAll(listOf("-id3v2_version", "3", "-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"))
            } else {
                parts.addAll(listOf("-disposition:v:0", "attached_pic"))
            }
        } else {
            parts.addAll(
                listOf(
                    "-map", "0:a",
                    "-c:a", "copy"
                )
            )
        }

        if (safeTitle.isNotBlank()) {
            parts.add("-metadata")
            parts.add("title=$safeTitle")
        }
        if (safeArtist.isNotBlank()) {
            parts.add("-metadata")
            parts.add("artist=$safeArtist")
        }
        if (safeAlbum.isNotBlank()) {
            parts.add("-metadata")
            parts.add("album=$safeAlbum")
        }
        if (track.year > 0) {
            parts.add("-metadata")
            parts.add("date=${track.year}")
        }

        parts.add(tempTaggedFile.absolutePath)

        try {
            val success = suspendCancellableCoroutine<Boolean> { continuation ->
                val session = FFmpegKit.executeWithArgumentsAsync(
                    parts.toTypedArray(),
                    { completedSession ->
                        val returnCode = completedSession.returnCode
                        continuation.resume(ReturnCode.isSuccess(returnCode))
                    },
                    { _ -> },
                    { _ -> }
                )
                continuation.invokeOnCancellation {
                    try {
                        FFmpegKit.cancel(session.sessionId)
                    } catch (_: Exception) {}
                }
            }

            if (success && tempTaggedFile.exists() && tempTaggedFile.length() > 0) {
                if (destinationFile.exists()) destinationFile.delete()
                val moved = tempTaggedFile.renameTo(destinationFile)
                if (!moved) {
                    tempTaggedFile.copyTo(destinationFile, overwrite = true)
                    tempTaggedFile.delete()
                }
                tempPartFile.delete()
                NodeLog.i(TAG, "Tagged and finalized audio with embedded artwork: ${destinationFile.name}")
                Result.success(destinationFile)
            } else {
                NodeLog.w(TAG, "FFmpeg tagging returned non-zero code. Falling back to direct rename.")
                if (tempTaggedFile.exists()) tempTaggedFile.delete()
                fallbackRename(tempPartFile, destinationFile)
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Exception during tagging: ${e.message}. Falling back to direct rename.")
            if (tempTaggedFile.exists()) tempTaggedFile.delete()
            fallbackRename(tempPartFile, destinationFile)
        } finally {
            tempCoverFile?.delete()
        }
    }

    private fun fallbackRename(tempPartFile: File, destinationFile: File): Result<File> {
        return runCatching {
            if (destinationFile.exists()) destinationFile.delete()
            val renamed = tempPartFile.renameTo(destinationFile)
            if (!renamed) {
                tempPartFile.copyTo(destinationFile, overwrite = true)
                tempPartFile.delete()
            }
            destinationFile
        }
    }
}
