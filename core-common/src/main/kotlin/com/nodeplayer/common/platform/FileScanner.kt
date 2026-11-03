package com.nodeplayer.common.platform

data class ScannedAudioMetadata(
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val format: String = "AUDIO"
)

interface FileScanner {
    fun scanPath(path: String, onTrackFound: (ScannedAudioMetadata) -> Unit): Int
}
