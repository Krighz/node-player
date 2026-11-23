package com.nodeplayer.model

import com.nodeplayer.model.storage.DownloadQualityPreset
import java.security.MessageDigest
import java.util.regex.Pattern

@JvmInline
value class CanonicalTrackKey(val value: String) {

    val isYouTube: Boolean
        get() = YOUTUBE_ID_PATTERN.matcher(value).matches()

    val diskFileName: String
        get() = if (isYouTube) "$value.opus" else "${sha256Prefix(value)}.opus"

    companion object {
        private val YOUTUBE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$")
        private val URL_ID_PATTERN = Pattern.compile(
            "(?:v=|/v/|youtu\\.be/|/embed/|online://|nodeplayer://youtube/)([a-zA-Z0-9_-]{11})"
        )

        fun from(track: Track): CanonicalTrackKey {
            val extracted = track.extractVideoId()
            if (!extracted.isNullOrBlank()) {
                return CanonicalTrackKey(extracted)
            }
            return from(track.path)
        }

        fun from(raw: String): CanonicalTrackKey {
            val trimmed = raw.trim()
            if (YOUTUBE_ID_PATTERN.matcher(trimmed).matches()) {
                return CanonicalTrackKey(trimmed)
            }
            val matcher = URL_ID_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                return CanonicalTrackKey(matcher.group(1) ?: trimmed)
            }
            return CanonicalTrackKey(trimmed)
        }

        private fun sha256Prefix(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }.take(32)
        }
    }
}

data class SmartMixtapeConfig(
    val isEnabled: Boolean = true,
    val targetSongCount: Int = 30, // 15 to 100
    val maxStorageCapBytes: Long = 1024L * 1024L * 1024L, // 1 GB default
    val requireUnmeteredWifi: Boolean = true,
    val audioQualityPreset: DownloadQualityPreset = DownloadQualityPreset.BALANCED
)

data class SmartMixtapeEntry(
    val canonicalKey: CanonicalTrackKey,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val fileSizeBytes: Long,
    val lastPlayedEpochMs: Long,
    val addedEpochMs: Long,
    val filePath: String
)
