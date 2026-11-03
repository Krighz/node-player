package com.nodeplayer.common.platform

data class AudioSourceMetadata(
    val format: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val durationMs: Long,
    val bitRateKbps: Int = 0
)

interface AudioDecoder : AutoCloseable {
    val metadata: AudioSourceMetadata?

    fun open(filePath: String): Boolean
    fun decodeFrame(interleavedOutput: FloatArray): Int
    fun seekTo(positionMs: Long): Boolean
    override fun close()
}
