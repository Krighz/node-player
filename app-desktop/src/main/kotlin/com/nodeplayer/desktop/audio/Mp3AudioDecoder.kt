package com.nodeplayer.desktop.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.Header
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Pure Java MP3 Decoder using JLayer.
 * Decodes all MPEG-1, 2, 2.5 Layer III streams directly into 32-bit float PCM.
 */
class Mp3AudioDecoder : AudioDecoder {

    private var currentFilePath: String? = null
    private var fileInputStream: FileInputStream? = null
    private var bitstream: Bitstream? = null
    private var decoder: Decoder? = null
    override var metadata: AudioSourceMetadata? = null
        private set

    private var sampleRate = 44100
    private var channels = 2
    private var cachedSamples = FloatArray(4608)
    private var cachedCount = 0
    private var cachedOffset = 0

    override fun open(filePath: String): Boolean {
        close()
        currentFilePath = filePath
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return false

        return try {
            val fis = FileInputStream(file)
            fileInputStream = fis
            val bis = BufferedInputStream(fis, 65536)
            val bs = Bitstream(bis)
            bitstream = bs
            decoder = Decoder()

            val header = bs.readFrame()
            if (header == null) {
                close()
                return false
            }

            sampleRate = header.frequency()
            channels = if (header.mode() == Header.SINGLE_CHANNEL) 1 else 2
            val totalMs = if (header.bitrate() > 0) {
                ((file.length() * 8L * 1000L) / header.bitrate()).toLong()
            } else {
                0L
            }

            metadata = AudioSourceMetadata(
                format = "MP3",
                sampleRate = sampleRate,
                bitDepth = 16,
                channels = channels,
                durationMs = totalMs,
                bitRateKbps = header.bitrate() / 1000
            )

            val output = decoder?.decodeFrame(header, bs) as? SampleBuffer
            if (output != null) {
                fillCache(output)
            }
            bs.closeFrame()
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    private fun fillCache(sb: SampleBuffer) {
        val count = sb.bufferLength
        val pcm = sb.buffer
        if (cachedSamples.size < count) {
            cachedSamples = FloatArray(count)
        }
        for (i in 0 until count) {
            cachedSamples[i] = pcm[i] / 32768.0f
        }
        cachedCount = count
        cachedOffset = 0
    }

    override fun decodeFrame(interleavedOutput: FloatArray): Int {
        val bs = bitstream ?: return -1
        val dec = decoder ?: return -1
        var outOffset = 0
        val outCap = interleavedOutput.size

        while (outOffset < outCap) {
            val available = cachedCount - cachedOffset
            if (available > 0) {
                val toCopy = minOf(available, outCap - outOffset)
                System.arraycopy(cachedSamples, cachedOffset, interleavedOutput, outOffset, toCopy)
                cachedOffset += toCopy
                outOffset += toCopy
                if (outOffset >= outCap) {
                    return outOffset
                }
            }

            try {
                val header = bs.readFrame() ?: break
                val output = dec.decodeFrame(header, bs) as? SampleBuffer
                if (output != null) {
                    fillCache(output)
                }
                bs.closeFrame()
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
        val totalMs = metadata?.durationMs ?: 0L
        if (totalMs <= 0L) return false

        return try {
            val targetByte = ((positionMs.toDouble() / totalMs.toDouble()) * file.length()).toLong().coerceIn(0L, maxOf(0L, file.length() - 4096L))
            
            try { bitstream?.close() } catch (_: Exception) {}
            try { fileInputStream?.close() } catch (_: Exception) {}

            val fis = FileInputStream(file)
            fileInputStream = fis
            if (targetByte > 0L) {
                fis.channel.position(targetByte)
            }
            val bis = BufferedInputStream(fis, 65536)
            val bs = Bitstream(bis)
            bitstream = bs
            decoder = Decoder()

            val header = bs.readFrame()
            if (header != null) {
                bs.closeFrame()
            }
            cachedCount = 0
            cachedOffset = 0
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        try {
            bitstream?.close()
        } catch (e: Exception) {
            // Ignored
        }
        try {
            fileInputStream?.close()
        } catch (e: Exception) {
            // Ignored
        }
        bitstream = null
        decoder = null
        fileInputStream = null
        metadata = null
        cachedCount = 0
        cachedOffset = 0
    }
}
