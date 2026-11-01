package com.nodeplayer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance pure-Kotlin Radix-2 Cooley-Tukey FFT implementation.
 */
object Fft {

    /**
     * In-place Cooley-Tukey Radix-2 FFT.
     * [real] and [imag] arrays must have size equal to a power of 2.
     * [inverse] is true for IFFT.
     */
    fun transform(real: FloatArray, imag: FloatArray, inverse: Boolean = false) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2, got $n" }
        require(imag.size == n) { "Imaginary array length must match real array length" }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey butterflies
        val sign = if (inverse) 1.0 else -1.0
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = sign * 2.0 * PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }

        if (inverse) {
            val scale = 1.0f / n
            for (idx in 0 until n) {
                real[idx] *= scale
                imag[idx] *= scale
            }
        }
    }

    /**
     * Applies a Hann window in-place to [buffer].
     */
    fun applyHannWindow(buffer: FloatArray) {
        val n = buffer.size
        for (i in 0 until n) {
            val multiplier = 0.5f * (1.0f - cos(2.0 * PI * i / (n - 1)).toFloat())
            buffer[i] *= multiplier
        }
    }

    /**
     * Calculates magnitude spectrum from complex [real] and [imag] outputs.
     * Returns an array of size n / 2.
     */
    fun magnitudeSpectrum(real: FloatArray, imag: FloatArray): FloatArray {
        val half = real.size / 2
        val magnitudes = FloatArray(half)
        for (i in 0 until half) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
        return magnitudes
    }
}
