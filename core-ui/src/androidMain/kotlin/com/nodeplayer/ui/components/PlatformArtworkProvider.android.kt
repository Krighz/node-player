package com.nodeplayer.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nodeplayer.model.Track
import java.io.File

actual object PlatformArtworkProvider {

    private const val MAX_VIBRANT_CACHE_ENTRIES = 128
    private val vibrantColorCache = object : LinkedHashMap<String, androidx.compose.ui.graphics.Color>(
        MAX_VIBRANT_CACHE_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.Color>?
        ): Boolean = size > MAX_VIBRANT_CACHE_ENTRIES
    }

    actual suspend fun loadArtwork(track: Track): ImageBitmap? {
        val path = track.path
        if (path.isBlank()) return null

        // 0. Check in-memory byte cache for Node Connect virtual streams/tracks
        val cachedBytes = ArtworkCache.getBytes(path)
            ?: ArtworkCache.getBytes(track.id.toString())
            ?: ArtworkCache.getBytes(track.title + track.artist)
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            return try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeByteArray(cachedBytes, 0, cachedBytes.size, opts)
                bitmap?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }

        // 0.1 Check for YouTube Music remote/cached tracks: fetch thumbnail if online
        if (path.startsWith("nodeplayer://youtube/") || path.startsWith("http://") || path.startsWith("https://")) {
            val resolvedUrl = if (path.startsWith("nodeplayer://youtube/")) {
                val videoId = path.removePrefix("nodeplayer://youtube/")
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            } else {
                path
            }
            return try {
                val url = java.net.URI(resolvedUrl).toURL()
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.instanceFollowRedirects = true
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    ArtworkCache.putBytes(path, bytes)
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    bitmap?.asImageBitmap()
                } else null
            } catch (_: Exception) {
                null
            }
        }

        val file = File(path)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                val picture = mmr.embeddedPicture
                if (picture != null) {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, opts)
                    bitmap?.asImageBitmap()
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

    actual suspend fun extractVibrantColor(track: Track): androidx.compose.ui.graphics.Color? {
        val cacheKey = track.path.ifBlank { track.id.toString() }
        synchronized(vibrantColorCache) {
            vibrantColorCache[cacheKey]?.let { cached ->
                return if (cached == androidx.compose.ui.graphics.Color.Unspecified) null else cached
            }
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
            synchronized(vibrantColorCache) {
                vibrantColorCache[cacheKey] = bestColor ?: androidx.compose.ui.graphics.Color.Unspecified
            }
            bestColor
        } catch (_: Exception) {
            null
        }
    }
}
