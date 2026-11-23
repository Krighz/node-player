package com.nodeplayer.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nodeplayer.model.Track
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

actual object PlatformArtworkProvider {

    private const val MAX_VIBRANT_CACHE_ENTRIES = 128
    private val decodeSemaphore = Semaphore(4)

    @Volatile
    var remoteImageFetcher: (suspend (url: String, maxSizePx: Int) -> ImageBitmap?)? = null

    private val vibrantColorCache = BoundedLruCache<String, Long>(MAX_VIBRANT_CACHE_ENTRIES)

    private fun cropYouTubeLetterbox(bitmap: Bitmap): Bitmap {
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (aspect in 1.30f..1.36f) { // 4:3 hqdefault format (480x360 has aspect 1.333)
            val trimPx = (bitmap.height * 0.175f).toInt()
            val activeHeight = (bitmap.height * 0.775f).toInt()
            if (trimPx > 0 && activeHeight > 0 && trimPx + activeHeight <= bitmap.height) {
                return Bitmap.createBitmap(bitmap, 0, trimPx, bitmap.width, activeHeight)
            }
        }
        return bitmap
    }

    private fun decodeSampledBitmap(bytes: ByteArray, targetPx: Int): ImageBitmap? {
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val sampleSize = computeBitmapSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetPx)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = if (targetPx >= 512) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        val cropped = cropYouTubeLetterbox(decoded)
        return cropped.asImageBitmap()
    }

    private fun downloadRemoteArtworkBytes(resolvedUrl: String): ByteArray? {
        val videoId = if (resolvedUrl.startsWith("nodeplayer://youtube/")) {
            resolvedUrl.removePrefix("nodeplayer://youtube/")
        } else if (resolvedUrl.contains("i.ytimg.com/vi/")) {
            resolvedUrl.substringAfter("i.ytimg.com/vi/").substringBefore("/")
        } else null

        if (videoId != null) {
            val variants = listOf(
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$videoId/hq720.jpg",
                "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            )
            for (url in variants) {
                try {
                    val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    conn.instanceFollowRedirects = true
                    if (conn.responseCode == 200) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) return bytes
                    }
                } catch (_: Exception) {}
            }
            return null
        }

        return try {
            val uri = java.net.URI(resolvedUrl).toURL()
            val conn = uri.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.instanceFollowRedirects = true
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isNotEmpty()) bytes else null
        } catch (_: Exception) {
            null
        }
    }

    actual suspend fun loadArtwork(url: String, maxSizePx: Int): ImageBitmap? {
        if (url.isBlank()) return null
        val targetPx = maxSizePx.coerceIn(96, 2048)
        val cachedBytes = ArtworkCache.getBytes(url)
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            return decodeSemaphore.withPermit {
                try {
                    decodeSampledBitmap(cachedBytes, targetPx)
                } catch (_: Exception) {
                    null
                }
            }
        }
        val resolvedUrl = if (url.startsWith("nodeplayer://youtube/")) {
            val videoId = url.removePrefix("nodeplayer://youtube/")
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
        } else {
            url
        }
        remoteImageFetcher?.let { hook ->
            return try {
                hook(resolvedUrl, targetPx)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                null
            }
        }
        return decodeSemaphore.withPermit {
            try {
                val bytes = downloadRemoteArtworkBytes(url)
                if (bytes != null && bytes.isNotEmpty()) {
                    ArtworkCache.putBytes(url, bytes)
                    decodeSampledBitmap(bytes, targetPx)
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    actual suspend fun loadArtwork(track: Track, maxSizePx: Int): ImageBitmap? {
        val path = track.path
        if (path.isBlank()) return null
        val targetPx = maxSizePx.coerceIn(96, 2048)

        // 0. Check in-memory byte cache for Node Connect virtual streams/tracks
        val cachedBytes = ArtworkCache.getBytes(path)
            ?: ArtworkCache.getBytes(track.id.toString())
            ?: ArtworkCache.getBytes(track.title + track.artist)
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            return decodeSemaphore.withPermit {
                try {
                    decodeSampledBitmap(cachedBytes, targetPx)
                } catch (_: Exception) {
                    null
                }
            }
        }

        // 0.1 Check for YouTube Music remote/cached tracks: route via Coil shared cache hook when registered
        if (path.startsWith("nodeplayer://youtube/") || path.startsWith("http://") || path.startsWith("https://")) {
            val resolvedUrl = if (path.startsWith("nodeplayer://youtube/")) {
                val videoId = path.removePrefix("nodeplayer://youtube/")
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
            } else {
                path
            }
            remoteImageFetcher?.let { hook ->
                return try {
                    hook(resolvedUrl, targetPx)
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    null
                }
            }
            return decodeSemaphore.withPermit {
                try {
                    val bytes = downloadRemoteArtworkBytes(path)
                    if (bytes != null && bytes.isNotEmpty()) {
                        ArtworkCache.putBytes(path, bytes)
                        decodeSampledBitmap(bytes, targetPx)
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }

        val file = File(path)
        if (!file.exists() || !file.canRead()) return null

        return decodeSemaphore.withPermit {
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    val picture = mmr.embeddedPicture
                    if (picture != null) {
                        decodeSampledBitmap(picture, targetPx)
                    } else {
                        val companionCover = File(file.parentFile, "${file.nameWithoutExtension}.jpg")
                        val folderCover = File(file.parentFile, "cover.jpg")
                        when {
                            companionCover.exists() && companionCover.canRead() -> {
                                decodeSampledBitmap(companionCover.readBytes(), targetPx)
                            }
                            folderCover.exists() && folderCover.canRead() -> {
                                decodeSampledBitmap(folderCover.readBytes(), targetPx)
                            }
                            else -> null
                        }
                    }
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    actual suspend fun extractVibrantColor(track: Track): androidx.compose.ui.graphics.Color? {
        val cacheKey = track.path.ifBlank { track.id.toString() }
        vibrantColorCache.get(cacheKey)?.let { cached ->
            val color = androidx.compose.ui.graphics.Color(cached.toULong())
            return if (color == androidx.compose.ui.graphics.Color.Unspecified) null else color
        }
        val path = track.path
        if (path.isBlank()) return null
        val bytes = ArtworkCache.getBytes(path)
            ?: ArtworkCache.getBytes(track.id.toString())
            ?: ArtworkCache.getBytes(track.title + track.artist)
            ?: (File(path).takeIf { it.exists() && it.canRead() }?.let {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(it.absolutePath)
                    mmr.embeddedPicture
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
            })
            ?: return null
        return try {
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val sampleSize = computeBitmapSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 128)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            val bestColor = try {
                val w = bm.width
                val h = bm.height
                if (w <= 0 || h <= 0) {
                    null
                } else {
                    var maxSat = -1f
                    var candidate: androidx.compose.ui.graphics.Color? = null
                    val points = listOf(w / 4 to h / 4, 3 * w / 4 to h / 4, w / 2 to h / 2, w / 4 to 3 * h / 4, 3 * w / 4 to 3 * h / 4)
                    for ((x, y) in points) {
                        val argb = bm.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
                        val c = androidx.compose.ui.graphics.Color(argb)
                        val hsv = com.nodeplayer.ui.theme.ColorUtils.colorToHsv(c)
                        if (hsv[1] > maxSat && hsv[2] > 0.25f && hsv[1] > 0.20f) {
                            maxSat = hsv[1]
                            candidate = c
                        }
                    }
                    candidate
                }
            } finally {
                bm.recycle()
            }
            vibrantColorCache.put(
                cacheKey,
                (bestColor ?: androidx.compose.ui.graphics.Color.Unspecified).value.toLong()
            )
            bestColor
        } catch (_: Exception) {
            null
        }
    }
}

actual fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
