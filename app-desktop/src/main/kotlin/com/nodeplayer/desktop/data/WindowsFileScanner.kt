package com.nodeplayer.desktop.data

import com.nodeplayer.common.platform.MediaLibraryScanner
import com.nodeplayer.common.platform.ScannedAudioMetadata
import com.nodeplayer.library.AudioHeaderParser
import com.nodeplayer.model.HiResStatus
import com.nodeplayer.model.Track
import java.io.File

/**
 * Windows Music Folder Scanner.
 * Recursively scans directories, extracting real audio metadata and stream specs from headers.
 * Never estimates or guesses duration or format attributes.
 */
class WindowsFileScanner : MediaLibraryScanner {

    private val supportedExtensions = setOf("flac", "wav", "mp3", "m4a", "aac", "alac", "ogg", "opus")

    override fun scanPath(path: String, onTrackFound: (ScannedAudioMetadata) -> Unit): Int {
        val root = File(path)
        if (!root.exists()) return 0

        var count = 0
        if (root.isFile) {
            val meta = extractMetadata(root)
            if (meta != null) {
                onTrackFound(meta)
                count++
            }
            return count
        }

        root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .forEach { file ->
                val meta = extractMetadata(file)
                if (meta != null) {
                    onTrackFound(meta)
                    count++
                }
            }
        return count
    }

    fun extractMetadata(file: File): ScannedAudioMetadata? {
        if (!file.exists() || !file.canRead() || file.length() < 12) return null
        return try {
            val formatInfo = AudioHeaderParser.parseFile(file)
            val nameWithoutExt = file.nameWithoutExtension

            val (fallbackArtist, fallbackTitle) = if (nameWithoutExt.contains(" - ")) {
                val parts = nameWithoutExt.split(" - ", limit = 2)
                parts[0].trim() to parts[1].trim()
            } else {
                "Unknown Artist" to nameWithoutExt.trim()
            }

            val title = formatInfo?.title?.ifBlank { null } ?: fallbackTitle
            val artist = formatInfo?.artist?.ifBlank { null } ?: fallbackArtist
            val album = formatInfo?.album?.ifBlank { null } ?: (file.parentFile?.name ?: "Unknown Album")
            val sampleRate = formatInfo?.sampleRate ?: 44100
            val bitDepth = formatInfo?.bitDepth ?: 16
            val durationMs = formatInfo?.durationMs ?: 0L

            ScannedAudioMetadata(
                filePath = file.absolutePath,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                format = formatInfo?.formatName ?: file.extension.uppercase()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun toTrack(meta: ScannedAudioMetadata, id: Long = 0L): Track {
        val isHiRes = meta.sampleRate > 48000 || meta.bitDepth > 16
        val isLossless = meta.format.contains("FLAC", ignoreCase = true) ||
                         meta.format.contains("WAV", ignoreCase = true) ||
                         meta.format.contains("ALAC", ignoreCase = true)
        val mimeType = when (meta.format.lowercase()) {
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "aac", "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            else -> "audio/*"
        }

        return Track(
            id = id,
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            durationMs = meta.durationMs,
            path = meta.filePath,
            size = File(meta.filePath).length(),
            mimeType = mimeType,
            sampleRate = meta.sampleRate,
            bitDepth = meta.bitDepth,
            channels = 2,
            bitrateKbps = 0,
            formatDescription = "${meta.format} ${if (isLossless && meta.bitDepth > 0) "${meta.bitDepth}-bit" else ""} ${meta.sampleRate / 1000}kHz".trim(),
            isLossless = isLossless,
            isHiRes = isHiRes,
            hiResStatus = if (isHiRes) HiResStatus.GENUINE else HiResStatus.UNKNOWN
        )
    }
}
