package com.nodeplayer.windows.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata

/**
 * Composite Audio Decoder that routes file paths to either JvmAudioDecoder or FFmpegAudioDecoder.
 */
class CompositeAudioDecoder : AudioDecoder {

    private val jvmDecoder = JvmAudioDecoder()
    private val ffmpegDecoder = FFmpegAudioDecoder()
    private var activeDecoder: AudioDecoder? = null

    override val metadata: AudioSourceMetadata?
        get() = activeDecoder?.metadata

    override fun open(filePath: String): Boolean {
        close()
        val lower = filePath.lowercase()

        if (lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".aif")) {
            if (jvmDecoder.open(filePath)) {
                activeDecoder = jvmDecoder
                return true
            }
        }

        if (FFmpegAudioDecoder.isFFmpegAvailable()) {
            if (ffmpegDecoder.open(filePath)) {
                activeDecoder = ffmpegDecoder
                return true
            }
        }

        if (jvmDecoder.open(filePath)) {
            activeDecoder = jvmDecoder
            return true
        }

        return false
    }

    override fun decodeFrame(interleavedOutput: FloatArray): Int {
        return activeDecoder?.decodeFrame(interleavedOutput) ?: -1
    }

    override fun seekTo(positionMs: Long): Boolean {
        return activeDecoder?.seekTo(positionMs) ?: false
    }

    override fun close() {
        jvmDecoder.close()
        ffmpegDecoder.close()
        activeDecoder = null
    }
}
