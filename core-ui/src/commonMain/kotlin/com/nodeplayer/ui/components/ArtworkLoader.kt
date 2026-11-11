package com.nodeplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

expect object PlatformArtworkProvider {
    suspend fun loadArtwork(track: Track, maxSizePx: Int = ArtworkCache.DEFAULT_TARGET_SIZE_PX): ImageBitmap?
    suspend fun extractVibrantColor(track: Track): Color?
}

internal class BoundedLruCache<K : Any, V : Any>(
    private val maxEntries: Int
) {
    private val map = LinkedHashMap<K, V>(maxEntries, 0.75f, true)

    fun get(key: K): V? = synchronized(map) { map[key] }

    fun put(key: K, value: V) {
        synchronized(map) {
            map[key] = value
            if (map.size > maxEntries) {
                val eldest = map.entries.iterator()
                if (eldest.hasNext()) {
                    eldest.next()
                    eldest.remove()
                }
            }
        }
    }

    fun size(): Int = synchronized(map) { map.size }

    fun clear() {
        synchronized(map) { map.clear() }
    }
}

object ArtworkCache {
    const val MAX_MEMORY_ENTRIES = 300
    const val MAX_BYTE_ENTRIES = 200
    const val DEFAULT_TARGET_SIZE_PX = 256

    private val memoryCache = BoundedLruCache<String, ImageBitmap>(MAX_MEMORY_ENTRIES)
    private val byteCache = BoundedLruCache<String, ByteArray>(MAX_BYTE_ENTRIES)

    fun sizedKey(baseKey: String, maxSizePx: Int = DEFAULT_TARGET_SIZE_PX): String =
        "$baseKey@$maxSizePx"

    fun get(key: String): ImageBitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: ImageBitmap) = memoryCache.put(key, bitmap)

    fun getBytes(key: String): ByteArray? = byteCache.get(key)

    fun putBytes(key: String, bytes: ByteArray) = byteCache.put(key, bytes)

    fun memorySize(): Int = memoryCache.size()

    fun byteSize(): Int = byteCache.size()

    fun clear() {
        memoryCache.clear()
        byteCache.clear()
    }
}

@Composable
fun TrackArtwork(
    track: Track?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 12.dp,
    targetSizePx: Int = ArtworkCache.DEFAULT_TARGET_SIZE_PX
) {
    val accent = LocalAccentColor.current
    val cacheKey = track?.path ?: track?.id?.toString() ?: ""
    val sizedCacheKey = if (cacheKey.isNotEmpty()) ArtworkCache.sizedKey(cacheKey, targetSizePx) else ""
    var bitmap by remember(track?.id, track?.path, targetSizePx) {
        mutableStateOf(if (sizedCacheKey.isNotEmpty()) ArtworkCache.get(sizedCacheKey) else null)
    }

    LaunchedEffect(track?.id, track?.path, targetSizePx) {
        if (track == null || sizedCacheKey.isEmpty()) return@LaunchedEffect
        val cached = ArtworkCache.get(sizedCacheKey)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val loaded = PlatformArtworkProvider.loadArtwork(track, targetSizePx)
            if (loaded != null) {
                ArtworkCache.put(sizedCacheKey, loaded)
                withContext(Dispatchers.Main) {
                    bitmap = loaded
                }
            }
        }
    }

    val squircleShape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius) }
    val borderBrush = remember {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
        )
    }
    val backgroundBrush = remember(accent) {
        Brush.linearGradient(
            listOf(accent.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
        )
    }
    Box(
        modifier = modifier
            .clip(squircleShape)
            .border(
                1.dp,
                borderBrush,
                squircleShape
            )
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = track?.title ?: "Artwork",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = accent.copy(alpha = 0.75f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

fun computeBitmapSampleSize(srcW: Int, srcH: Int, maxDim: Int): Int {
    if (srcW <= 0 || srcH <= 0 || maxDim <= 0) return 1
    var sampleSize = 1
    val halfW = srcW / 2
    val halfH = srcH / 2
    while (halfW / sampleSize >= maxDim && halfH / sampleSize >= maxDim) {
        sampleSize *= 2
    }
    return sampleSize
}
