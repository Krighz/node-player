package com.nodeplayer.desktop.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FFmpeg Audio Decoder for Windows.
 * Decodes compressed audio formats (AAC, ALAC, Opus, Ogg Vorbis, FLAC, MP3) into 32-bit float PCM.
 */
class FFmpegAudioDecoder : AudioDecoder {

    private var process: Process? = null
    private var pcmInputStream: InputStream? = null
    override var metadata: AudioSourceMetadata? = null
        private set

    companion object {
        fun isFFmpegAvailable(): Boolean {
            return try {
                val proc = ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start()
                val exitCode = proc.waitFor()
                exitCode == 0
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun open(filePath: String): Boolean {
        close()
        val file = File(filePath)
        if (!file.exists()) return false

        return try {
            val pb = ProcessBuilder(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "error",
                "-i", file.absolutePath,
                "-f", "f32le",
                "-ac", "2",
                "-ar", "44100",
                "pipe:1"
            )
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            val proc = pb.start()
            process = proc
            pcmInputStream = BufferedInputStream(proc.inputStream, 65536)

            metadata = AudioSourceMetadata(
                format = "FFmpeg Float32",
                sampleRate = 44100,
                bitDepth = 32,
                channels = 2,
                durationMs = 0L
            )
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    override fun decodeFrame(interleavedOutput: FloatArray): Int {
        val stream = pcmInputStream ?: return -1
        val maxSamples = interleavedOutput.size
        val bytesToRead = maxSamples * 4
        val byteBuf = ByteArray(bytesToRead)

        var totalRead = 0
        while (totalRead < bytesToRead) {
            val r = stream.read(byteBuf, totalRead, bytesToRead - totalRead)
            if (r <= 0) break
            totalRead += r
        }

        if (totalRead <= 0) return -1

        val samplesRead = totalRead / 4
        val bb = ByteBuffer.wrap(byteBuf, 0, totalRead).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samplesRead) {
            interleavedOutput[i] = bb.float
        }
        return samplesRead
    }

    override fun seekTo(positionMs: Long): Boolean {
        return false
    }

    override fun close() {
        try {
            pcmInputStream?.close()
        } catch (e: Exception) {
            // Ignored
        }
        try {
            process?.destroy()
        } catch (e: Exception) {
            // Ignored
        }
        pcmInputStream = null
        process = null
        metadata = null
    }
}
