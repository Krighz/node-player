package com.nodeplayer.music.library

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Dimension
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.computeBitmapSampleSize
import okio.Buffer
import java.io.File

class AudioArtworkFetcher(
    private val path: String,
    private val trackId: Long,
    private val albumId: Long,
    private val context: Context,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val (targetW, targetH) = resolveTargetSize(options.size)
        val maxDim = maxOf(targetW, targetH)

        // 0. Check in-memory byte cache for Node Connect virtual streams/tracks
        val cachedBytes = com.nodeplayer.ui.components.ArtworkCache.getBytes(path)
            ?: (if (trackId > 0) com.nodeplayer.ui.components.ArtworkCache.getBytes(trackId.toString()) else null)
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            decodeSampledDrawable(cachedBytes, maxDim)?.let { drawable ->
                return DrawableResult(
                    drawable = drawable,
                    isSampled = true,
                    dataSource = DataSource.MEMORY
                )
            }
            return SourceResult(
                source = ImageSource(Buffer().write(cachedBytes), context),
                mimeType = "image/jpeg",
                dataSource = DataSource.MEMORY
            )
        }

        // 1. Android 10+ MediaStore thumbnail if trackId > 0 — return DrawableResult directly without JPEG re-encode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && trackId > 0) {
            try {
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
                val thumb = context.contentResolver.loadThumbnail(trackUri, Size(targetW, targetH), null)
                return DrawableResult(
                    drawable = BitmapDrawable(context.resources, thumb),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (_: Exception) {}
        }

        // 2. Embedded picture via MediaMetadataRetriever from file path (downsampled via inSampleSize)
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    val picture = mmr.embeddedPicture
                    if (picture != null) {
                        decodeSampledDrawable(picture, maxDim)?.let { drawable ->
                            return DrawableResult(
                                drawable = drawable,
                                isSampled = true,
                                dataSource = DataSource.DISK
                            )
                        }
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
                        decodeSampledDrawable(bytes, maxDim)?.let { drawable ->
                            return DrawableResult(
                                drawable = drawable,
                                isSampled = true,
                                dataSource = DataSource.DISK
                            )
                        }
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

    private fun decodeSampledDrawable(bytes: ByteArray, maxDim: Int): BitmapDrawable? {
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val sampleSize = computeBitmapSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDim)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    class FileFactory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            val ext = data.extension.lowercase()
            if (ext in AUDIO_EXTENSIONS) {
                return AudioArtworkFetcher(data.absolutePath, -1L, -1L, options.context, options)
            }
            return null
        }
    }

    class TrackFactory : Fetcher.Factory<Track> {
        override fun create(data: Track, options: Options, imageLoader: ImageLoader): Fetcher {
            return AudioArtworkFetcher(data.path, data.id, data.albumId, options.context, options)
        }
    }

    companion object {
        const val DEFAULT_TARGET_SIZE_PX = 256
        const val MIN_TARGET_SIZE_PX = 96
        const val MAX_TARGET_SIZE_PX = 512

        val AUDIO_EXTENSIONS = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "alac", "aiff", "wma")

        fun resolveTargetSize(coilSize: coil.size.Size): Pair<Int, Int> {
            val width = (coilSize.width as? Dimension.Pixels)?.px
                ?.coerceIn(MIN_TARGET_SIZE_PX, MAX_TARGET_SIZE_PX)
                ?: DEFAULT_TARGET_SIZE_PX
            val height = (coilSize.height as? Dimension.Pixels)?.px
                ?.coerceIn(MIN_TARGET_SIZE_PX, MAX_TARGET_SIZE_PX)
                ?: DEFAULT_TARGET_SIZE_PX
            return width to height
        }
    }
}
