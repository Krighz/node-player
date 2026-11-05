package com.nodeplayer.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.nodeplayer.model.Track
import org.jetbrains.skia.Image
import java.io.File
import java.io.FileInputStream

actual object PlatformArtworkProvider {

    actual suspend fun loadArtwork(track: Track): ImageBitmap? {
        val path = track.path
        if (path.isBlank()) return null

        // 0. Check in-memory byte cache for Node Connect virtual streams/tracks
        val cachedBytes = ArtworkCache.getBytes(path)
            ?: ArtworkCache.getBytes(track.id.toString())
            ?: ArtworkCache.getBytes(track.title + track.artist)
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            return try {
                val skiaImage = Image.makeFromEncoded(cachedBytes)
                skiaImage.toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }

        val file = File(path)
        if (!file.exists() || !file.canRead()) return null

        val bytes = extractDesktopArtworkBytes(file) ?: return null
        return try {
            val skiaImage = Image.makeFromEncoded(bytes)
            skiaImage.toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    actual suspend fun extractVibrantColor(track: Track): androidx.compose.ui.graphics.Color? {
        val path = track.path
        if (path.isBlank()) return null
        val bytes = ArtworkCache.getBytes(path)
            ?: ArtworkCache.getBytes(track.id.toString())
            ?: ArtworkCache.getBytes(track.title + track.artist)
            ?: (File(path).takeIf { it.exists() && it.canRead() }?.let { extractDesktopArtworkBytes(it) })
            ?: return null
        return try {
            val img = Image.makeFromEncoded(bytes)
            val bm = org.jetbrains.skia.Bitmap.makeFromImage(img)
            val w = bm.width
            val h = bm.height
            var maxSat = -1f
            var bestColor: androidx.compose.ui.graphics.Color? = null
            val points = listOf(w / 4 to h / 4, 3 * w / 4 to h / 4, w / 2 to h / 2, w / 4 to 3 * h / 4, 3 * w / 4 to 3 * h / 4)
            for ((x, y) in points) {
                val argb = bm.getColor(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
                val c = androidx.compose.ui.graphics.Color(argb)
                val hsv = com.nodeplayer.ui.theme.ColorUtils.colorToHsv(c)
                if (hsv[1] > maxSat && hsv[2] > 0.25f && hsv[1] > 0.20f) {
                    maxSat = hsv[1]
                    bestColor = c
                }
            }
            bestColor
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDesktopArtworkBytes(file: File): ByteArray? {
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
            val scanLen = minOf(file.length(), 2_000_000L).toInt()
            if (scanLen < 32) return null
            val bytes = ByteArray(scanLen)
            FileInputStream(file).use { it.read(bytes) }

            // 1. Scan for JPEG: 0xFF 0xD8 0xFF ... 0xFF 0xD9
            var jpgStart = -1
            for (i in 0 until bytes.size - 4) {
                if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte() && bytes[i + 2] == 0xFF.toByte()) {
                    jpgStart = i
                    break
                }
            }
            if (jpgStart >= 0) {
                var jpgEnd = -1
                for (j in bytes.size - 2 downTo jpgStart + 10) {
                    if (bytes[j] == 0xFF.toByte() && bytes[j + 1] == 0xD9.toByte()) {
                        jpgEnd = j + 2
                        break
                    }
                }
                if (jpgEnd > jpgStart && (jpgEnd - jpgStart) in 100..scanLen) {
                    return bytes.copyOfRange(jpgStart, jpgEnd)
                }
            }

            // 2. Scan for PNG: 0x89 'P' 'N' 'G' ... 'I' 'E' 'N' 'D' 0xAE 0x42 0x60 0x82
            var pngStart = -1
            for (i in 0 until bytes.size - 8) {
                if (bytes[i] == 0x89.toByte() && bytes[i + 1] == 0x50.toByte() &&
                    bytes[i + 2] == 0x4E.toByte() && bytes[i + 3] == 0x47.toByte()
                ) {
                    pngStart = i
                    break
                }
            }
            if (pngStart >= 0) {
                val iend = "IEND".toByteArray(Charsets.US_ASCII)
                for (j in bytes.size - 8 downTo pngStart + 8) {
                    if (bytes[j] == iend[0] && bytes[j + 1] == iend[1] &&
                        bytes[j + 2] == iend[2] && bytes[j + 3] == iend[3]
                    ) {
                        val pngEnd = minOf(j + 8, bytes.size)
                        return bytes.copyOfRange(pngStart, pngEnd)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
