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
import java.util.concurrent.ConcurrentHashMap

expect object PlatformArtworkProvider {
    suspend fun loadArtwork(track: Track): ImageBitmap?
    suspend fun extractVibrantColor(track: Track): Color?
}

object ArtworkCache {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private val byteCache = ConcurrentHashMap<String, ByteArray>()

    fun get(key: String): ImageBitmap? = memoryCache[key]
    fun put(key: String, bitmap: ImageBitmap) {
        if (memoryCache.size > 300) {
            memoryCache.clear()
        }
        memoryCache[key] = bitmap
    }

    fun getBytes(key: String): ByteArray? = byteCache[key]
    fun putBytes(key: String, bytes: ByteArray) {
        if (byteCache.size > 200) {
            byteCache.clear()
        }
        byteCache[key] = bytes
    }
}

@Composable
fun TrackArtwork(
    track: Track?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 12.dp
) {
    val accent = LocalAccentColor.current
    val cacheKey = track?.path ?: track?.id?.toString() ?: ""
    var bitmap by remember(track?.id, track?.path) {
        mutableStateOf(if (cacheKey.isNotEmpty()) ArtworkCache.get(cacheKey) else null)
    }

    LaunchedEffect(track?.id, track?.path) {
        if (track == null || cacheKey.isEmpty()) return@LaunchedEffect
        val cached = ArtworkCache.get(cacheKey)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val loaded = PlatformArtworkProvider.loadArtwork(track)
            if (loaded != null) {
                ArtworkCache.put(cacheKey, loaded)
                withContext(Dispatchers.Main) {
                    bitmap = loaded
                }
            }
        }
    }

    val squircleShape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius) }
    Box(
        modifier = modifier
            .clip(squircleShape)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
                ),
                squircleShape
            )
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
                )
            ),
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
