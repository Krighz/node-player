package com.nodeplayer.desktop.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata
import org.jflac.FLACDecoder
import org.jflac.io.RandomFileInputStream
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

    private var rawInputStream: InputStream? = null
    private var randomFileStream: RandomFileInputStream? = null
    private var flacDecoder: FLACDecoder? = null
    override var metadata: AudioSourceMetadata? = null
        private set

    private var sampleRate = 44100
    private var channels = 2
    private var bitDepth = 16

    private var pcmBuffer = FloatArray(16384)
    private var pcmCount = 0
    private var pcmOffset = 0

    override fun open(filePath: String): Boolean {
        close()
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return false

        return try {
            val dec = try {
                val rfs = RandomFileInputStream(file)
                randomFileStream = rfs
                FLACDecoder(rfs)
            } catch (e: Exception) {
                val fis = FileInputStream(file)
                val bis = BufferedInputStream(fis, 65536)
                rawInputStream = bis
                FLACDecoder(bis)
            }
            flacDecoder = dec

            // Read all metadata blocks (StreamInfo, Vorbis Comments, SeekTable, etc.)
            // so the stream is correctly positioned at the first audio frame.
            dec.readMetadata()
            val streamInfo = dec.streamInfo
            if (streamInfo == null) {
                close()
                return false
            }

            sampleRate = streamInfo.sampleRate
            channels = streamInfo.channels
            bitDepth = streamInfo.bitsPerSample
            val totalSamples = streamInfo.totalSamples
            val durationMs = if (sampleRate > 0) (totalSamples * 1000L) / sampleRate else 0L

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
                // readNextFrame parses the frame and populates channelData with decoded samples
                val frame = dec.readNextFrame() ?: break
                val blockSize = frame.header.blockSize
                if (blockSize <= 0) break

                val chData = dec.channelData
                val numChannels = minOf(channels, chData.size)
                val totalSamples = blockSize * numChannels

                if (pcmBuffer.size < totalSamples) {
                    pcmBuffer = FloatArray(totalSamples)
                }

                val normFactor = when (bitDepth) {
                    8 -> 128.0f
                    16 -> 32768.0f
                    24 -> 8388608.0f
                    32 -> 2147483648.0f
                    else -> (1L shl (bitDepth - 1)).toFloat()
                }

                var idx = 0
                for (s in 0 until blockSize) {
                    for (c in 0 until numChannels) {
                        val rawSample = chData[c].output[s]
                        pcmBuffer[idx++] = (rawSample.toFloat() / normFactor).coerceIn(-1.0f, 1.0f)
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
        val dec = flacDecoder ?: return false
        val targetSample = (positionMs * sampleRate) / 1000L
        return try {
            dec.seek(targetSample)
            pcmCount = 0
            pcmOffset = 0
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        try {
            randomFileStream?.close()
        } catch (e: Exception) {
            // Ignored
        }
        try {
            rawInputStream?.close()
        } catch (e: Exception) {
            // Ignored
        }
        randomFileStream = null
        rawInputStream = null
        flacDecoder = null
        metadata = null
        pcmCount = 0
        pcmOffset = 0
    }
}
