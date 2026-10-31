package com.nodeplayer.music.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.nodeplayer.dsp.DspChain
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Custom Media3 AudioProcessor that routes audio through our pure-Kotlin 32-bit Float DSP chain.
 */
class DspAudioProcessor(val dspChain: DspChain) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // We require stereo PCM Float or PCM 16-bit
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            dspChain.sampleRate = inputAudioFormat.sampleRate
            inputAudioFormat
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            // Output float for downstream 32-bit float rendering pipeline
            dspChain.sampleRate = inputAudioFormat.sampleRate
            AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inputFormat = inputAudioFormat
        val channelCount = inputFormat.channelCount

        if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
            val floatCount = remaining / 4
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.order(ByteOrder.nativeOrder())

            val floatArray = FloatArray(floatCount)
            inputBuffer.asFloatBuffer().get(floatArray)

            if (channelCount == 2) {
                dspChain.processInterleavedFloat(floatArray, 0, floatCount)
            }

            outputBuffer.asFloatBuffer().put(floatArray)
            outputBuffer.position(0)
            outputBuffer.limit(remaining)
            inputBuffer.position(inputBuffer.limit())
        } else if (inputFormat.encoding == C.ENCODING_PCM_16BIT) {
            val sampleCount = remaining / 2
            val outputBytes = sampleCount * 4
            val outputBuffer = replaceOutputBuffer(outputBytes)
            outputBuffer.order(ByteOrder.nativeOrder())

            val floatArray = FloatArray(sampleCount)
            var idx = 0
            while (inputBuffer.hasRemaining()) {
                val shortSample = inputBuffer.short
                floatArray[idx++] = shortSample / 32768.0f
            }

            if (channelCount == 2) {
                dspChain.processInterleavedFloat(floatArray, 0, sampleCount)
            }

            outputBuffer.asFloatBuffer().put(floatArray)
            outputBuffer.position(0)
            outputBuffer.limit(outputBytes)
        }
    }
}
