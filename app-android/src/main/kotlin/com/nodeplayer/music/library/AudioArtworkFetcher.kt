package com.nodeplayer.music.library

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.nodeplayer.model.Track
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File

class AudioArtworkFetcher(
    private val path: String,
    private val trackId: Long,
    private val albumId: Long,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // 1. Android 10+ MediaStore thumbnail if trackId > 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && trackId > 0) {
            try {
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
                val thumb = context.contentResolver.loadThumbnail(trackUri, Size(600, 600), null)
                if (thumb != null) {
                    val baos = ByteArrayOutputStream()
                    thumb.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                    val bytes = baos.toByteArray()
                    return SourceResult(
                        source = ImageSource(Buffer().write(bytes), context),
                        mimeType = "image/jpeg",
                        dataSource = DataSource.DISK
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. Embedded picture via MediaMetadataRetriever from file path
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    val picture = mmr.embeddedPicture
                    if (picture != null) {
                        return SourceResult(
                            source = ImageSource(Buffer().write(picture), context),
                            mimeType = "image/jpeg",
                            dataSource = DataSource.DISK
                        )
                    }
                } catch (_: Exception) {
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
            }
        }

        // 3. MediaStore album art URI
        if (albumId > 0) {
            try {
                val uri = Uri.parse("content://media/external/audio/albumart/$albumId")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        return SourceResult(
                            source = ImageSource(Buffer().write(bytes), context),
                            mimeType = "image/jpeg",
                            dataSource = DataSource.DISK
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    class FileFactory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            val ext = data.extension.lowercase()
            if (ext in AUDIO_EXTENSIONS) {
                return AudioArtworkFetcher(data.absolutePath, -1L, -1L, options.context)
            }
            return null
        }
    }

    class TrackFactory : Fetcher.Factory<Track> {
        override fun create(data: Track, options: Options, imageLoader: ImageLoader): Fetcher {
            return AudioArtworkFetcher(data.path, data.id, data.albumId, options.context)
        }
    }

    companion object {
        val AUDIO_EXTENSIONS = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "alac", "aiff", "wma")
    }
}
