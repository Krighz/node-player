package com.nodeplayer.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-configurable streaming audio quality for online playback.
 */
enum class StreamingAudioQuality(
    val id: String,
    val displayName: String,
    val kbpsRating: String,
    val description: String,
    val preferredItags: List<Int>
) {
    HIGH(
        id = "HIGH",
        displayName = "High",
        kbpsRating = "~160–256 kbps",
        description = "Audiophile Transparent (Opus 160k / AAC 256k)",
        preferredItags = listOf(251, 140, 250, 139)
    ),
    MEDIUM(
        id = "MEDIUM",
        displayName = "Medium",
        kbpsRating = "~128 kbps",
        description = "Balanced Fidelity & Lower Buffer Latency (AAC 128k / Opus 128k)",
        preferredItags = listOf(140, 251, 250, 139)
    ),
    LOW(
        id = "LOW",
        displayName = "Low",
        kbpsRating = "~48–70 kbps",
        description = "Data Saver for Mobile Networks (Opus 70k / AAC 48k)",
        preferredItags = listOf(250, 139, 249, 140, 251)
    );

    companion object {
        fun fromId(id: String?): StreamingAudioQuality =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: HIGH
    }
}

/**
 * User-configurable album art rendering resolution for local and online tracks.
 */
enum class AlbumArtQuality(
    val id: String,
    val displayName: String,
    val resolutionLabel: String,
    val targetPx: Int,
    val googleUserContentSizeParam: String
) {
    HIGH(
        id = "HIGH",
        displayName = "High / Ultra",
        resolutionLabel = "1080p–1200p HD",
        targetPx = 1200,
        googleUserContentSizeParam = "=w1200-h1200-l90-rj"
    ),
    MEDIUM(
        id = "MEDIUM",
        displayName = "Medium",
        resolutionLabel = "544p Balanced",
        targetPx = 544,
        googleUserContentSizeParam = "=w544-h544-l90-rj"
    ),
    LOW(
        id = "LOW",
        displayName = "Low",
        resolutionLabel = "256p Data Saver",
        targetPx = 256,
        googleUserContentSizeParam = "=w256-h256-l90-rj"
    );

    companion object {
        fun fromId(id: String?): AlbumArtQuality =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: HIGH
    }
}

/**
 * Global multiplatform reactive holder for streaming media quality settings.
 */
object MediaQualitySettingsState {
    private val _streamingAudioQuality = MutableStateFlow(StreamingAudioQuality.HIGH)
    val streamingAudioQuality = _streamingAudioQuality.asStateFlow()

    private val _albumArtQuality = MutableStateFlow(AlbumArtQuality.HIGH)
    val albumArtQuality = _albumArtQuality.asStateFlow()

    fun setStreamingAudioQuality(quality: StreamingAudioQuality) {
        _streamingAudioQuality.value = quality
    }

    fun setAlbumArtQuality(quality: AlbumArtQuality) {
        _albumArtQuality.value = quality
    }
}
