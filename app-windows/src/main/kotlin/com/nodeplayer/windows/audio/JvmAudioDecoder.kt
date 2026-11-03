package com.nodeplayer.windows.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Pure JVM Audio Decoder for WAV, AIFF, and uncompressed PCM audio.
 */
class JvmAudioDecoder : AudioDecoder {

    private var audioStream: AudioInputStream? = null
    override var metadata: AudioSourceMetadata? = null
        private set

    private var targetChannels = 2
    private var sampleSizeBits = 16
    private var sampleRate = 44100

    override fun open(filePath: String): Boolean {
        close()
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return false

        return try {
            val baseStream = AudioSystem.getAudioInputStream(file)
            val baseFormat = baseStream.format

            val decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate,
                16,
                baseFormat.channels,
                2 * baseFormat.channels,
                baseFormat.sampleRate,
                false
            )

            val stream = if (AudioSystem.isConversionSupported(decodedFormat, baseFormat)) {
                AudioSystem.getAudioInputStream(decodedFormat, baseStream)
            } else {
                baseStream
            }

            audioStream = stream
            targetChannels = stream.format.channels
            sampleSizeBits = stream.format.sampleSizeInBits
            sampleRate = stream.format.sampleRate.toInt()

            val durationMs = if (stream.frameLength > 0 && sampleRate > 0) {
                (stream.frameLength * 1000L) / sampleRate
            } else {
                0L
            }

            metadata = AudioSourceMetadata(
                format = stream.format.encoding.toString(),
                sampleRate = sampleRate,
                bitDepth = sampleSizeBits,
                channels = targetChannels,
                durationMs = durationMs
            )
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    override fun decodeFrame(interleavedOutput: FloatArray): Int {
        val stream = audioStream ?: return -1
        val maxSamples = interleavedOutput.size
        val bytesPerSample = sampleSizeBits / 8
        val bytesToRead = maxSamples * bytesPerSample
        val byteBuf = ByteArray(bytesToRead)

        val bytesRead = stream.read(byteBuf, 0, bytesToRead)
        if (bytesRead <= 0) return -1

        val samplesRead = bytesRead / bytesPerSample
        val bb = ByteBuffer.wrap(byteBuf, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until samplesRead) {
            interleavedOutput[i] = bb.short / 32768.0f
        }
        return samplesRead
    }

    override fun seekTo(positionMs: Long): Boolean {
        val stream = audioStream ?: return false
        val targetFrame = (positionMs * sampleRate) / 1000L
        val bytesPerFrame = (sampleSizeBits / 8) * targetChannels
        val bytesToSkip = targetFrame * bytesPerFrame

        return try {
            stream.skip(bytesToSkip) > 0
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        try {
            audioStream?.close()
        } catch (e: Exception) {
            // Ignored
        } finally {
            audioStream = null
            metadata = null
        }
    }
}
