package com.nodeplayer.desktop.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata
import com.nodeplayer.library.AudioHeaderParser
import org.jflac.FLACDecoder
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Pure Java FLAC Decoder using jFLAC.
 * Decodes lossless FLAC streams (16-bit, 24-bit, 32-bit, multi-rate) into 32-bit float PCM buffers.
 * Directly extracts integer subframe samples from channel data to guarantee bit-perfect fidelity.
 */
class FlacAudioDecoder : AudioDecoder {

    private var currentFilePath: String? = null
    private var fileInputStream: FileInputStream? = null
    private var bufferedInputStream: InputStream? = null
    private var flacDecoder: FLACDecoder? = null
    override var metadata: AudioSourceMetadata? = null
        private set

    private var sampleRate = 44100
    private var channels = 2
    private var bitDepth = 16
    private var flacStartOffset = 0L

    private var pcmBuffer = FloatArray(16384)
    private var pcmCount = 0
    private var pcmOffset = 0

    private fun findFlacStartOffset(file: File): Long {
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(10)
                if (fis.read(magic) == 10) {
                    if (magic[0] == 0x66.toByte() && magic[1] == 0x4C.toByte() &&
                        magic[2] == 0x61.toByte() && magic[3] == 0x43.toByte()
                    ) {
                        return 0L
                    }
                    if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
                        val hasFooter = (magic[5].toInt() and 0x10) != 0
                        val s0 = magic[6].toInt() and 0x7F
                        val s1 = magic[7].toInt() and 0x7F
                        val s2 = magic[8].toInt() and 0x7F
                        val s3 = magic[9].toInt() and 0x7F
                        val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                        val skip = 10L + tagSize + (if (hasFooter) 10L else 0L)
                        fis.channel.position(skip)
                        val flacCheck = ByteArray(4)
                        if (fis.read(flacCheck) == 4 &&
                            flacCheck[0] == 0x66.toByte() && flacCheck[1] == 0x4C.toByte() &&
                            flacCheck[2] == 0x61.toByte() && flacCheck[3] == 0x43.toByte()
                        ) {
                            return skip
                        }
                    }
                }
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    override fun open(filePath: String): Boolean {
        close()
        currentFilePath = filePath
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return false

        return try {
            flacStartOffset = findFlacStartOffset(file)
            val fis = FileInputStream(file)
            fileInputStream = fis
            if (flacStartOffset > 0L) {
                fis.channel.position(flacStartOffset)
            }
            val bis = BufferedInputStream(fis, 65536)
            bufferedInputStream = bis

            val dec = FLACDecoder(bis)
            flacDecoder = dec

            dec.readMetadata()
            val streamInfo = dec.streamInfo
            if (streamInfo == null) {
                close()
                return false
            }

            sampleRate = if (streamInfo.sampleRate > 0) streamInfo.sampleRate else 44100
            channels = if (streamInfo.channels > 0) streamInfo.channels else 2
            bitDepth = if (streamInfo.bitsPerSample > 0) streamInfo.bitsPerSample else 16
            val totalSamples = streamInfo.totalSamples
            val durationMs = if (sampleRate > 0 && totalSamples > 0) (totalSamples * 1000L) / sampleRate else 0L

            metadata = AudioSourceMetadata(
                format = "FLAC",
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                channels = channels,
                durationMs = durationMs
            )
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    override fun decodeFrame(interleavedOutput: FloatArray): Int {
        val dec = flacDecoder ?: return -1
        var outOffset = 0
        val outCap = interleavedOutput.size

        while (outOffset < outCap) {
            val avail = pcmCount - pcmOffset
            if (avail > 0) {
                val toCopy = minOf(avail, outCap - outOffset)
                System.arraycopy(pcmBuffer, pcmOffset, interleavedOutput, outOffset, toCopy)
                pcmOffset += toCopy
                outOffset += toCopy
                if (outOffset >= outCap) return outOffset
            }

            try {
                val frame = dec.readNextFrame() ?: break
                val blockSize = frame.header.blockSize
                if (blockSize <= 0) break

                val chData = dec.channelData
                val numChannels = minOf(channels, chData.size)
                val totalSamples = blockSize * numChannels

                if (pcmBuffer.size < totalSamples) {
                    pcmBuffer = FloatArray(totalSamples)
                }

                val effectiveBits = if (frame.header.bitsPerSample > 0) frame.header.bitsPerSample else bitDepth
                val normFactor = when (effectiveBits) {
                    8 -> 128.0f
                    16 -> 32768.0f
                    24 -> 8388608.0f
                    32 -> 2147483648.0f
                    else -> (1L shl (maxOf(1, effectiveBits) - 1)).toFloat()
                }

                var idx = 0
                for (s in 0 until blockSize) {
                    for (c in 0 until numChannels) {
                        val rawSample = chData[c].output[s]
                        pcmBuffer[idx++] = rawSample / normFactor
                    }
                }
                pcmCount = idx
                pcmOffset = 0
            } catch (e: Exception) {
                break
            }
        }

        return if (outOffset > 0) outOffset else -1
    }

    override fun seekTo(positionMs: Long): Boolean {
        val path = currentFilePath ?: return false
        val file = File(path)
        if (!file.exists()) return false

        // For position <= 0, restart from beginning
        if (positionMs <= 0L) {
            return open(path)
        }

        val totalMs = metadata?.durationMs ?: 0L
        if (totalMs <= 0L) return false

        return try {
            val targetSample = (positionMs * sampleRate) / 1000L
            // Reopen and skip frames up to target
            open(path)
            val dec = flacDecoder ?: return false
            var currentSample = 0L
            while (currentSample + 4096 < targetSample) {
                val frame = dec.readNextFrame() ?: break
                currentSample += frame.header.blockSize
            }
            pcmCount = 0
            pcmOffset = 0
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun close() {
        try {
            bufferedInputStream?.close()
        } catch (_: Exception) {}
        try {
            fileInputStream?.close()
        } catch (_: Exception) {}
        bufferedInputStream = null
        fileInputStream = null
        flacDecoder = null
        metadata = null
        pcmCount = 0
        pcmOffset = 0
    }
}
