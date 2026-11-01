package com.nodeplayer.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long = 0,
    val album: String,
    val albumId: Long = 0,
    val durationMs: Long,
    val path: String,
    val size: Long = 0,
    val mimeType: String = "audio/*",
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val channels: Int = 2,
    val bitrateKbps: Int = 0,
    val formatDescription: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val year: Int = 0,
    val genre: String? = null,
    val dateAdded: Long = 0,
    val replayGainTrackGain: Float? = null,
    val replayGainAlbumGain: Float? = null,
    val replayGainTrackPeak: Float? = null,
    val replayGainAlbumPeak: Float? = null,
    val isLossless: Boolean = false,
    val isHiRes: Boolean = false,
    val hiResStatus: HiResStatus = HiResStatus.UNKNOWN,
    val hiResConfidence: Float = 0f,
    val hiResCutoffHz: Float = 0f,
    val loudnessLufs: Float? = null,
    val dynamicRangeScore: Int? = null,
    val dynamicRangeLabel: String? = null,
    val lastPlayedTimestamp: Long = 0L,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val analysisAttempts: Int = 0
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val formatBadgeText: String
        get() {
            if (formatDescription.isNotBlank()) return formatDescription
            val rateStr = if (sampleRate in 8000..768000) {
                if (sampleRate % 1000 == 0) "${sampleRate / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", sampleRate / 1000.0)
            } else "—"
            val fmt = when {
                mimeType.contains("flac", ignoreCase = true) || path.endsWith(".flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("wav", ignoreCase = true) || path.endsWith(".wav", ignoreCase = true) -> "WAV"
                mimeType.contains("alac", ignoreCase = true) || path.endsWith(".alac", ignoreCase = true) -> "ALAC"
                mimeType.contains("aac", ignoreCase = true) || path.endsWith(".m4a", ignoreCase = true) || path.endsWith(".aac", ignoreCase = true) -> "AAC"
                mimeType.contains("mp3", ignoreCase = true) || path.endsWith(".mp3", ignoreCase = true) -> "MP3"
                mimeType.contains("ogg", ignoreCase = true) || path.endsWith(".ogg", ignoreCase = true) -> "OGG"
                mimeType.contains("opus", ignoreCase = true) || path.endsWith(".opus", ignoreCase = true) -> "Opus"
                else -> mimeType.substringAfterLast("/").uppercase().ifBlank { "Audio" }
            }
            return if (isLossless) {
                val depthStr = if (bitDepth in 8..32) "$bitDepth-bit" else "—"
                "$fmt $depthStr / $rateStr"
            } else {
                if (bitrateKbps in 8..1411) "$fmt $bitrateKbps kbps" else if (rateStr != "—") "$fmt $rateStr" else fmt
            }
        }
}
