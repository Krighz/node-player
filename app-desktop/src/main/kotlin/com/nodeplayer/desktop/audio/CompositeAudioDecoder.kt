package com.nodeplayer.desktop.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata

/**
 * Composite Audio Decoder that routes audio files to specialized decoders:
 * - MP3: Mp3AudioDecoder (JLayer)
 * - FLAC: FlacAudioDecoder (jFLAC)
 * - WAV/AIFF: JvmAudioDecoder (JavaSound)
 * - AAC/ALAC/Opus/OGG: FFmpegAudioDecoder (FFmpeg pipe)
 */
class CompositeAudioDecoder : AudioDecoder {

    private val mp3Decoder = Mp3AudioDecoder()
    private val flacDecoder = FlacAudioDecoder()
    private val jvmDecoder = JvmAudioDecoder()
    private val ffmpegDecoder = FFmpegAudioDecoder()

    private var activeDecoder: AudioDecoder? = null

    override val metadata: AudioSourceMetadata?
        get() = activeDecoder?.metadata

    override fun open(filePath: String): Boolean {
        close()
        val lower = filePath.lowercase()

        if (lower.endsWith(".mp3")) {
            if (mp3Decoder.open(filePath)) {
                activeDecoder = mp3Decoder
                return true
            }
        } else if (lower.endsWith(".flac")) {
            if (flacDecoder.open(filePath)) {
                activeDecoder = flacDecoder
                return true
            }
        } else if (lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".aif")) {
            if (jvmDecoder.open(filePath)) {
                activeDecoder = jvmDecoder
                return true
            }
        }

        // Try FFmpeg for compressed/container formats
        if (FFmpegAudioDecoder.isFFmpegAvailable()) {
            if (ffmpegDecoder.open(filePath)) {
                activeDecoder = ffmpegDecoder
                return true
            }
        }

        // Universal fallback attempts
        if (mp3Decoder.open(filePath)) {
            activeDecoder = mp3Decoder
            return true
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
        mp3Decoder.close()
        flacDecoder.close()
        jvmDecoder.close()
        ffmpegDecoder.close()
        activeDecoder = null
    }
}
