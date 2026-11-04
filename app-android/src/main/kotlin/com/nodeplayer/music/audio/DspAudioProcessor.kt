package com.nodeplayer.music.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.nodeplayer.dsp.DspChain
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Robust 32-bit Float DSP Audio Processor for Media3.
 * Supports both PCM Float and PCM 16-bit (Mono and Stereo).
 * Extracts real RMS audio levels for visualizers and live signal path metrics.
 */
class DspAudioProcessor(val dspChain: DspChain) : BaseAudioProcessor() {

    @Volatile
    var currentRms: Float = 0.0f
        private set

    @Volatile
    var actualSampleRate: Int = 44100
        private set

    @Volatile
    var actualEncoding: String = "PCM_16BIT"
        private set

    @Volatile
    var preDspPcmTap: ((pcmBytes: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int) -> Unit)? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        dspChain.sampleRate = inputAudioFormat.sampleRate
        actualSampleRate = inputAudioFormat.sampleRate
        actualEncoding = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) "PCM_FLOAT (32-bit)" else "PCM_16BIT (16-bit)"
        // Keep output format identical to input format to avoid any sink negotiation issues
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inputFormat = inputAudioFormat
        val channels = inputFormat.channelCount
        val sampleRate = inputFormat.sampleRate

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.nativeOrder())

        if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
            val floatCount = remaining / 4
            val floatArray = FloatArray(floatCount)
            inputBuffer.asFloatBuffer().get(floatArray)

            // Tap pre-DSP PCM audio before applying EQ / DSP chain
            val tap = preDspPcmTap
            if (tap != null) {
                val pcmBytes = ByteArray(floatCount * 2)
                for (i in 0 until floatCount) {
                    val s = (floatArray[i] * 32767.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
                    pcmBytes[i * 2] = (s.toInt() and 0xFF).toByte()
                    pcmBytes[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                }
                tap(pcmBytes, sampleRate, channels, 16)
            }

            // Compute real RMS amplitude of audio buffer
            var sumSq = 0.0
            for (s in floatArray) {
                sumSq += (s * s).toDouble()
            }
            currentRms = kotlin.math.sqrt(sumSq / floatCount.coerceAtLeast(1)).toFloat()

            if (channels == 2) {
                dspChain.processInterleavedFloat(floatArray, 0, floatCount)
            } else if (channels == 1) {
                // Mono: process as dual-mono
                val stereo = FloatArray(floatCount * 2)
                for (i in 0 until floatCount) {
                    stereo[i * 2] = floatArray[i]
                    stereo[i * 2 + 1] = floatArray[i]
                }
                dspChain.processInterleavedFloat(stereo, 0, stereo.size)
                for (i in 0 until floatCount) {
                    floatArray[i] = (stereo[i * 2] + stereo[i * 2 + 1]) * 0.5f
                }
            }

            outputBuffer.asFloatBuffer().put(floatArray)
            outputBuffer.position(0)
            outputBuffer.limit(remaining)
            inputBuffer.position(inputBuffer.limit())

        } else if (inputFormat.encoding == C.ENCODING_PCM_16BIT) {
            val tap = preDspPcmTap
            if (tap != null) {
                val markPos = inputBuffer.position()
                val pcmBytes = ByteArray(remaining)
                inputBuffer.get(pcmBytes)
                inputBuffer.position(markPos)
                tap(pcmBytes, sampleRate, channels, 16)
            }

            val sampleCount = remaining / 2
            val floatArray = FloatArray(sampleCount)

            var idx = 0
            var sumSq = 0.0
            while (inputBuffer.hasRemaining()) {
                val shortSample = inputBuffer.short
                val norm = shortSample / 32768.0f
                floatArray[idx++] = norm
                sumSq += (norm * norm).toDouble()
            }
            currentRms = kotlin.math.sqrt(sumSq / sampleCount.coerceAtLeast(1)).toFloat()

            if (channels == 2) {
                dspChain.processInterleavedFloat(floatArray, 0, sampleCount)
            } else if (channels == 1) {
                val stereo = FloatArray(sampleCount * 2)
                for (i in 0 until sampleCount) {
                    stereo[i * 2] = floatArray[i]
                    stereo[i * 2 + 1] = floatArray[i]
                }
                dspChain.processInterleavedFloat(stereo, 0, stereo.size)
                for (i in 0 until sampleCount) {
                    floatArray[i] = (stereo[i * 2] + stereo[i * 2 + 1]) * 0.5f
                }
            }

            // Convert back to 16-bit PCM for output
            for (i in 0 until sampleCount) {
                val clamped = (floatArray[i] * 32767.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
                outputBuffer.putShort(clamped)
            }
            outputBuffer.position(0)
            outputBuffer.limit(remaining)
        }
    }
}
