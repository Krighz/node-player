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
    val isFavorite: Boolean = false
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
