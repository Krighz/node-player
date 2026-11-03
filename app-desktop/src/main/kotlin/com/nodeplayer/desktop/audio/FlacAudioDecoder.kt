package com.nodeplayer.desktop.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Pure Java FLAC Decoder using jFLAC.
 * Decodes lossless FLAC streams into 32-bit float PCM buffers.
 */
class FlacAudioDecoder : AudioDecoder, PCMProcessor {

    private var fileInputStream: FileInputStream? = null
    private var flacDecoder: FLACDecoder? = null
    override var metadata: AudioSourceMetadata? = null
        private set

    private var sampleRate = 44100
    private var channels = 2
    private var bitDepth = 16

    private var pcmBuffer = FloatArray(8192)
    private var pcmCount = 0
    private var pcmOffset = 0

    override fun open(filePath: String): Boolean {
        close()
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return false

        return try {
            val fis = FileInputStream(file)
            fileInputStream = fis
            val bis = BufferedInputStream(fis, 65536)
            val dec = FLACDecoder(bis)
            flacDecoder = dec
            dec.addPCMProcessor(this)

            val streamInfo = dec.readStreamInfo()
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

    override fun processStreamInfo(info: StreamInfo) {
        sampleRate = info.sampleRate
        channels = info.channels
        bitDepth = info.bitsPerSample
    }

    override fun processPCM(pcmData: ByteData) {
        val bytes = pcmData.data
        val length = pcmData.len
        val bytesPerSample = bitDepth / 8
        val samplesInPacket = length / bytesPerSample

        if (pcmBuffer.size < samplesInPacket) {
            pcmBuffer = FloatArray(samplesInPacket)
        }

        var idx = 0
        var b = 0
        while (b < length && idx < samplesInPacket) {
            val sample = if (bitDepth == 16) {
                val b0 = bytes[b].toInt() and 0xFF
                val b1 = bytes[b + 1].toInt()
                val s16 = (b1 shl 8) or b0
                b += 2
                s16 / 32768.0f
            } else if (bitDepth == 24) {
                val b0 = bytes[b].toInt() and 0xFF
                val b1 = bytes[b + 1].toInt() and 0xFF
                val b2 = bytes[b + 2].toInt()
                val s24 = (b2 shl 16) or (b1 shl 8) or b0
                b += 3
                s24 / 8388608.0f
            } else {
                val b0 = bytes[b].toInt() and 0xFF
                val b1 = bytes[b + 1].toInt()
                b += 2
                ((b1 shl 8) or b0) / 32768.0f
            }
            pcmBuffer[idx++] = sample
        }

        pcmCount = idx
        pcmOffset = 0
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
                dec.readNextFrame() ?: break
            } catch (e: Exception) {
                break
            }
        }

        return if (outOffset > 0) outOffset else -1
    }

    override fun seekTo(positionMs: Long): Boolean {
        return false
    }

    override fun close() {
        try {
            fileInputStream?.close()
        } catch (e: Exception) {
            // Ignored
        }
        fileInputStream = null
        flacDecoder = null
        metadata = null
        pcmCount = 0
        pcmOffset = 0
    }
}
