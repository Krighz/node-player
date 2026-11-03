package com.nodeplayer.desktop.audio

import com.nodeplayer.common.platform.AudioDecoder
import com.nodeplayer.common.platform.AudioSourceMetadata
import com.nodeplayer.library.AudioHeaderParser
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
    private var firstAudioFrameOffset = 0L

    private fun findFirstMpegFrameOffset(file: File): Long {
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(10)
                var skipOffset = 0L
                if (fis.read(header) == 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val hasFooter = (header[5].toInt() and 0x10) != 0
                    val s0 = header[6].toInt() and 0x7F
                    val s1 = header[7].toInt() and 0x7F
                    val s2 = header[8].toInt() and 0x7F
                    val s3 = header[9].toInt() and 0x7F
                    val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                    skipOffset = 10L + tagSize + (if (hasFooter) 10L else 0L)
                }

                if (skipOffset > 0L) {
                    fis.channel.position(skipOffset)
                }

                val buffer = ByteArray(65536)
                val read = fis.read(buffer)
                if (read >= 4) {
                    for (i in 0..read - 4) {
                        val b0 = buffer[i].toInt() and 0xFF
                        val b1 = buffer[i + 1].toInt() and 0xFF
                        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                            val vBits = (b1 ushr 3) and 0x03
                            val lBits = (b1 ushr 1) and 0x03
                            if (vBits != 1 && lBits == 1) { // MPEG Layer III
                                val b2 = buffer[i + 2].toInt() and 0xFF
                                val bitrateIdx = (b2 ushr 4) and 0x0F
                                val sampleRateIdx = (b2 ushr 2) and 0x03
                                if (bitrateIdx in 1..14 && sampleRateIdx in 0..2) {
                                    return skipOffset + i.toLong()
                                }
                            }
                        }
                    }
                }
                skipOffset
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
            val audioStart = findFirstMpegFrameOffset(file)
            firstAudioFrameOffset = audioStart
            val fis = FileInputStream(file)
            fileInputStream = fis
            if (audioStart > 0L) {
                fis.channel.position(audioStart)
            }
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

            // Read exact duration from file metadata tags or Xing header via AudioHeaderParser
            val formatInfo = AudioHeaderParser.parseFile(file)
            val totalMs = if (formatInfo != null && formatInfo.durationMs > 0L) {
                formatInfo.durationMs
            } else if (header.bitrate() > 0) {
                val audioBytes = maxOf(0L, file.length() - audioStart)
                ((audioBytes * 8L * 1000L) / header.bitrate()).toLong()
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

            var frameDecoded = false
            for (retry in 0 until 4) {
                try {
                    val header = bs.readFrame() ?: break
                    val output = dec.decodeFrame(header, bs) as? SampleBuffer
                    if (output != null) {
                        fillCache(output)
                        frameDecoded = true
                    }
                    bs.closeFrame()
                    break
                } catch (e: Exception) {
                    // Try next sync frame
                }
            }
            if (!frameDecoded && cachedCount - cachedOffset <= 0) {
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
            val audioStart = firstAudioFrameOffset
            val audioBytes = maxOf(0L, file.length() - audioStart)
            val approxByte = audioStart + ((positionMs.toDouble() / totalMs.toDouble()) * audioBytes).toLong().coerceIn(0L, maxOf(0L, audioBytes - 4096L))

            val newFis = FileInputStream(file)
            if (approxByte > 0L) {
                newFis.channel.position(approxByte)
            }

            // Find valid sync word starting around approxByte
            val scanBuf = ByteArray(8192)
            val read = newFis.read(scanBuf)
            var syncOffset = -1
            if (read >= 4) {
                for (i in 0..read - 4) {
                    val b0 = scanBuf[i].toInt() and 0xFF
                    val b1 = scanBuf[i + 1].toInt() and 0xFF
                    if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                        val vBits = (b1 ushr 3) and 0x03
                        val lBits = (b1 ushr 1) and 0x03
                        if (vBits != 1 && lBits == 1) {
                            val b2 = scanBuf[i + 2].toInt() and 0xFF
                            val bitrateIdx = (b2 ushr 4) and 0x0F
                            val sampleRateIdx = (b2 ushr 2) and 0x03
                            if (bitrateIdx in 1..14 && sampleRateIdx in 0..2) {
                                syncOffset = i
                                break
                            }
                        }
                    }
                }
            }

            val exactByte = if (syncOffset >= 0) approxByte + syncOffset.toLong() else approxByte
            newFis.channel.position(exactByte)
            val newBis = BufferedInputStream(newFis, 65536)
            val newBs = Bitstream(newBis)
            val newDec = Decoder()

            val syncedHeader = newBs.readFrame()
            if (syncedHeader != null) {
                try { bitstream?.close() } catch (_: Exception) {}
                try { fileInputStream?.close() } catch (_: Exception) {}

                fileInputStream = newFis
                bitstream = newBs
                decoder = newDec

                val output = newDec.decodeFrame(syncedHeader, newBs) as? SampleBuffer
                if (output != null) {
                    fillCache(output)
                }
                newBs.closeFrame()
                true
            } else {
                try { newBs.close() } catch (_: Exception) {}
                try { newFis.close() } catch (_: Exception) {}
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        try {
            bitstream?.close()
        } catch (_: Exception) {}
        try {
            fileInputStream?.close()
        } catch (_: Exception) {}
        bitstream = null
        decoder = null
        fileInputStream = null
        metadata = null
        cachedCount = 0
        cachedOffset = 0
    }
}
