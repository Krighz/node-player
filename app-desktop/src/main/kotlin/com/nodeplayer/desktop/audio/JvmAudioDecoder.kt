package com.nodeplayer.desktop.audio

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
 * Supports 16-bit, 24-bit, and 32-bit PCM and IEEE float.
 */
class JvmAudioDecoder : AudioDecoder {

    private var audioStream: AudioInputStream? = null
    override var metadata: AudioSourceMetadata? = null
        private set

    private var targetChannels = 2
    private var sampleSizeBits = 16
    private var sampleRate = 44100
    private var isFloat = false
    private var isBigEndian = false

    override fun open(filePath: String): Boolean {
        close()
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return false

        return try {
            val baseStream = AudioSystem.getAudioInputStream(file)
            val baseFormat = baseStream.format

            val stream = if (baseFormat.encoding == AudioFormat.Encoding.PCM_SIGNED ||
                baseFormat.encoding == AudioFormat.Encoding.PCM_FLOAT) {
                baseStream
            } else {
                val decodedFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    2 * baseFormat.channels,
                    baseFormat.sampleRate,
                    false
                )
                if (AudioSystem.isConversionSupported(decodedFormat, baseFormat)) {
                    AudioSystem.getAudioInputStream(decodedFormat, baseStream)
                } else {
                    baseStream
                }
            }

            audioStream = stream
            targetChannels = stream.format.channels
            sampleSizeBits = stream.format.sampleSizeInBits
            sampleRate = stream.format.sampleRate.toInt()
            isFloat = stream.format.encoding == AudioFormat.Encoding.PCM_FLOAT
            isBigEndian = stream.format.isBigEndian

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
        val bytesPerSample = maxOf(1, sampleSizeBits / 8)
        val bytesToRead = maxSamples * bytesPerSample
        val byteBuf = ByteArray(bytesToRead)

        val bytesRead = stream.read(byteBuf, 0, bytesToRead)
        if (bytesRead <= 0) return -1

        val samplesRead = bytesRead / bytesPerSample
        val bb = ByteBuffer.wrap(byteBuf, 0, bytesRead)
        bb.order(if (isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)

        when {
            isFloat -> {
                for (i in 0 until samplesRead) {
                    interleavedOutput[i] = bb.float.coerceIn(-1.0f, 1.0f)
                }
            }
            sampleSizeBits == 24 -> {
                for (i in 0 until samplesRead) {
                    val b0 = bb.get().toInt() and 0xFF
                    val b1 = bb.get().toInt() and 0xFF
                    val b2 = bb.get().toInt()
                    val s24 = (b2 shl 16) or (b1 shl 8) or b0
                    interleavedOutput[i] = (s24 / 8388608.0f).coerceIn(-1.0f, 1.0f)
                }
            }
            sampleSizeBits == 32 -> {
                for (i in 0 until samplesRead) {
                    interleavedOutput[i] = (bb.int / 2147483648.0).toFloat().coerceIn(-1.0f, 1.0f)
                }
            }
            sampleSizeBits == 8 -> {
                for (i in 0 until samplesRead) {
                    interleavedOutput[i] = (bb.get().toFloat() / 128.0f).coerceIn(-1.0f, 1.0f)
                }
            }
            else -> {
                for (i in 0 until samplesRead) {
                    interleavedOutput[i] = (bb.short / 32768.0f).coerceIn(-1.0f, 1.0f)
                }
            }
        }
        return samplesRead
    }

    override fun seekTo(positionMs: Long): Boolean {
        val stream = audioStream ?: return false
        val targetFrame = (positionMs * sampleRate) / 1000L
        val bytesPerFrame = (maxOf(1, sampleSizeBits / 8)) * targetChannels
        val bytesToSkip = targetFrame * bytesPerFrame

        return try {
            stream.reset()
            stream.skip(bytesToSkip)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        try {
            audioStream?.close()
        } catch (e: Exception) {
            // Ignored
        }
        audioStream = null
        metadata = null
    }
}
