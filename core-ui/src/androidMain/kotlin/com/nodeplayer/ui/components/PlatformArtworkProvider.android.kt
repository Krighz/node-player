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

    private fun decodeSampledBitmap(bytes: ByteArray, targetPx: Int): ImageBitmap? {
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val sampleSize = computeBitmapSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetPx)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)?.asImageBitmap()
    }

    actual suspend fun loadArtwork(track: Track, maxSizePx: Int): ImageBitmap? {
        val path = track.path
        if (path.isBlank()) return null
        val targetPx = maxSizePx.coerceIn(96, 512)

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
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            } else {
                path
            }
            return decodeSemaphore.withPermit {
                val hook = remoteImageFetcher
                if (hook != null) {
                    try {
                        hook(resolvedUrl, targetPx)
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    try {
                        val url = java.net.URI(resolvedUrl).toURL()
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.instanceFollowRedirects = true
                        val bytes = conn.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) {
                            ArtworkCache.putBytes(path, bytes)
                            decodeSampledBitmap(bytes, targetPx)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
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
                        null
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
