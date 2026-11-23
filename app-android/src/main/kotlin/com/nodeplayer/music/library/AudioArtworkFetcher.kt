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
        val artQuality = com.nodeplayer.model.MediaQualitySettingsState.albumArtQuality.value

        // Online tracks / YouTube Music tracks
        if (path.startsWith("nodeplayer://youtube/") || path.contains("i.ytimg.com") || path.contains("googleusercontent.com") || path.startsWith("http://") || path.startsWith("https://")) {
            val variants = com.nodeplayer.model.ArtworkUrlResolver.resolveYouTubeThumbnailCandidates(path, artQuality)
            val urlsToTry = if (variants.isNotEmpty() && variants.first() != path) {
                variants
            } else {
                listOfNotNull(com.nodeplayer.model.ArtworkUrlResolver.resolveArtworkUrl(path, artQuality))
            }

            for (url in urlsToTry) {
                val request = coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .size(targetW, targetH)
                    .allowHardware(false)
                    .build()
                val result = coil.Coil.imageLoader(context).execute(request)
                if (result is coil.request.SuccessResult) {
                    val rawBmp = (result.drawable as? BitmapDrawable)?.bitmap
                        ?: (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        ?: drawableToBitmap(result.drawable)
                    val cropped = cropYouTubeLetterbox(rawBmp)
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, cropped),
                        isSampled = true,
                        dataSource = result.dataSource
                    )
                }
            }
            return null
        }

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

        // 1. High-Resolution Embedded picture via MediaMetadataRetriever directly from audio file
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    val picture = mmr.embeddedPicture
                    if (picture != null && picture.isNotEmpty()) {
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

                // Check companion cover file in the same directory (cover.jpg, folder.jpg, etc.)
                val companionCover = listOf(
                    File(file.parentFile, "${file.nameWithoutExtension}.jpg"),
                    File(file.parentFile, "${file.nameWithoutExtension}.png"),
                    File(file.parentFile, "cover.jpg"),
                    File(file.parentFile, "folder.jpg"),
                    File(file.parentFile, "front.jpg")
                ).firstOrNull { it.exists() && it.canRead() && it.length() > 0L }

                if (companionCover != null) {
                    try {
                        val bytes = companionCover.readBytes()
                        if (bytes.isNotEmpty()) {
                            decodeSampledDrawable(bytes, maxDim)?.let { drawable ->
                                return DrawableResult(
                                    drawable = drawable,
                                    isSampled = true,
                                    dataSource = DataSource.DISK
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 2. Fallback: Android 10+ MediaStore thumbnail if trackId > 0
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

        // 3. Fallback: MediaStore album art URI
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

    private fun cropYouTubeLetterbox(bitmap: Bitmap): Bitmap {
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (aspect in 1.30f..1.36f) {
            val trimPx = (bitmap.height * 0.125f).toInt()
            val activeHeight = (bitmap.height * 0.75f).toInt()
            if (trimPx > 0 && activeHeight > 0 && trimPx + activeHeight <= bitmap.height) {
                return Bitmap.createBitmap(bitmap, 0, trimPx, bitmap.width, activeHeight)
            }
        }
        return bitmap
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
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
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        val cropped = cropYouTubeLetterbox(bitmap)
        return BitmapDrawable(context.resources, cropped)
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
        val DEFAULT_TARGET_SIZE_PX: Int
            get() = com.nodeplayer.model.MediaQualitySettingsState.albumArtQuality.value.targetPx
        const val MIN_TARGET_SIZE_PX = 96
        const val MAX_TARGET_SIZE_PX = 2048

        val AUDIO_EXTENSIONS = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "alac", "aiff", "wma")

        fun resolveTargetSize(coilSize: coil.size.Size): Pair<Int, Int> {
            val defaultPx = DEFAULT_TARGET_SIZE_PX
            val width = (coilSize.width as? Dimension.Pixels)?.px
                ?.coerceIn(MIN_TARGET_SIZE_PX, MAX_TARGET_SIZE_PX)
                ?: defaultPx
            val height = (coilSize.height as? Dimension.Pixels)?.px
                ?.coerceIn(MIN_TARGET_SIZE_PX, MAX_TARGET_SIZE_PX)
                ?: defaultPx
            return width to height
        }
    }
}
