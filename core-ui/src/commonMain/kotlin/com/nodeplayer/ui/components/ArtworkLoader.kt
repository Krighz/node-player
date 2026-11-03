package com.nodeplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

expect object ArtworkImageDecoder {
    fun decode(bytes: ByteArray): ImageBitmap?
}

object ArtworkCache {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(key: String): ImageBitmap? = memoryCache[key]
    fun put(key: String, bitmap: ImageBitmap) {
        if (memoryCache.size > 250) {
            memoryCache.clear()
        }
        memoryCache[key] = bitmap
    }
}

/**
 * Universal embedded artwork extractor for Desktop and Android:
 * 1. Folder art (cover.jpg, folder.jpg, front.jpg, albumart.jpg)
 * 2. FLAC PICTURE metadata block
 * 3. MP3 ID3v2 APIC / PIC frame
 * 4. Header scan for embedded JPEG & PNG binary streams
 */
object EmbeddedArtworkExtractor {
    fun extractArtworkBytes(filePath: String): ByteArray? {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null

        // 1. Check parent folder for cover images
        val parent = file.parentFile
        if (parent != null && parent.exists()) {
            val commonNames = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png", "albumart.jpg", "album.jpg")
            for (name in commonNames) {
                val candidate = File(parent, name)
                if (candidate.exists() && candidate.length() in 100..15_000_000) {
                    return candidate.readBytes()
                }
            }
            val match = parent.listFiles()?.firstOrNull { f ->
                val lower = f.name.lowercase()
                (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) &&
                (lower.contains("cover") || lower.contains("folder") || lower.contains("front") || lower.contains("albumart"))
            }
            if (match != null && match.length() in 100..15_000_000) {
                return match.readBytes()
            }
        }

        // 2. Embedded artwork extraction
        return try {
            val ext = file.extension.lowercase()
            when (ext) {
                "flac" -> extractFlacPicture(file) ?: scanForJpegPng(file)
                else -> scanForJpegPng(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFlacPicture(file: File): ByteArray? {
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                if (fis.read(header) != 4 || String(header, Charsets.US_ASCII) != "fLaC") return null
                while (true) {
                    val b0 = fis.read()
                    if (b0 == -1) break
                    val isLast = (b0 and 0x80) != 0
                    val blockType = b0 and 0x7F
                    val l0 = fis.read()
                    val l1 = fis.read()
                    val l2 = fis.read()
                    if (l0 == -1 || l1 == -1 || l2 == -1) break
                    val length = (l0 shl 16) or (l1 shl 8) or l2

                    if (blockType == 6) { // PICTURE block
                        val blockData = ByteArray(length)
                        var readTotal = 0
                        while (readTotal < length) {
                            val r = fis.read(blockData, readTotal, length - readTotal)
                            if (r <= 0) break
                            readTotal += r
                        }
                        if (readTotal == length) {
                            var offset = 4 // skip picture type
                            val mimeLen = readInt32(blockData, offset); offset += 4 + mimeLen
                            val descLen = readInt32(blockData, offset); offset += 4 + descLen
                            offset += 16 // skip width, height, bitDepth, colors
                            val dataLen = readInt32(blockData, offset); offset += 4
                            if (dataLen > 0 && offset + dataLen <= blockData.size) {
                                return blockData.copyOfRange(offset, offset + dataLen)
                            }
                        }
                        break
                    } else {
                        fis.skip(length.toLong())
                    }
                    if (isLast) break
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readInt32(b: ByteArray, offset: Int): Int {
        if (offset + 4 > b.size) return 0
        return ((b[offset].toInt() and 0xFF) shl 24) or
               ((b[offset + 1].toInt() and 0xFF) shl 16) or
               ((b[offset + 2].toInt() and 0xFF) shl 8) or
               (b[offset + 3].toInt() and 0xFF)
    }

    private fun scanForJpegPng(file: File): ByteArray? {
        return try {
            val scanLen = minOf(file.length(), 3_000_000L).toInt()
            if (scanLen < 32) return null
            val bytes = ByteArray(scanLen)
            FileInputStream(file).use { it.read(bytes) }

            // Search for JPEG: FF D8 FF
            for (i in 0 until (bytes.size - 4)) {
                if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte() && bytes[i + 2] == 0xFF.toByte()) {
                    var endIdx = -1
                    for (j in (i + 3) until (bytes.size - 1)) {
                        if (bytes[j] == 0xFF.toByte() && bytes[j + 1] == 0xD9.toByte()) {
                            endIdx = j + 2
                            break
                        }
                    }
                    if (endIdx > i && (endIdx - i) > 200) {
                        return bytes.copyOfRange(i, endIdx)
                    } else if (bytes.size - i > 500) {
                        return bytes.copyOfRange(i, bytes.size)
                    }
                }
                // Search for PNG: 89 50 4E 47
                if (bytes[i] == 0x89.toByte() && bytes[i + 1] == 0x50.toByte() && bytes[i + 2] == 0x4E.toByte() && bytes[i + 3] == 0x47.toByte()) {
                    var endIdx = -1
                    for (j in (i + 8) until (bytes.size - 8)) {
                        if (bytes[j] == 0x49.toByte() && bytes[j + 1] == 0x45.toByte() && bytes[j + 2] == 0x4E.toByte() && bytes[j + 3] == 0x44.toByte()) {
                            endIdx = j + 8
                            break
                        }
                    }
                    if (endIdx > i) {
                        return bytes.copyOfRange(i, endIdx)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
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
    var bitmap by remember(track?.id, track?.path) {
        mutableStateOf(track?.path?.let { ArtworkCache.get(it) })
    }

    LaunchedEffect(track?.id, track?.path) {
        val path = track?.path ?: return@LaunchedEffect
        val cached = ArtworkCache.get(path)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val bytes = EmbeddedArtworkExtractor.extractArtworkBytes(path)
            if (bytes != null) {
                val decoded = ArtworkImageDecoder.decode(bytes)
                if (decoded != null) {
                    ArtworkCache.put(path, decoded)
                    withContext(Dispatchers.Main) {
                        bitmap = decoded
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
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
