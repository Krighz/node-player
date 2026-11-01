package com.nodeplayer.dsp

import com.nodeplayer.model.HiResAnalysisResult
import com.nodeplayer.model.HiResStatus
import kotlin.math.log10
import kotlin.math.max

object HiResAnalyzer {

    private const val FFT_SIZE = 2048
    private const val HOP_SIZE = 1024

    /**
     * Analyzes PCM samples [samples] (mono or mixed down) at [sampleRate].
     * Returns an estimated [HiResAnalysisResult].
     */
    fun analyze(samples: FloatArray, sampleRate: Int): HiResAnalysisResult {
        if (samples.size < FFT_SIZE) {
            return HiResAnalysisResult(
                status = HiResStatus.UNKNOWN,
                confidence = 0.5f,
                cutoffFrequencyHz = 0f,
                maxFrequencyHz = (sampleRate / 2).toFloat()
            )
        }

        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE + 1
        if (numFrames <= 0) {
            return HiResAnalysisResult(
                status = HiResStatus.UNKNOWN,
                confidence = 0.5f,
                cutoffFrequencyHz = 0f,
                maxFrequencyHz = (sampleRate / 2).toFloat()
            )
        }

        val avgSpectrum = FloatArray(FFT_SIZE / 2)
        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        // Accumulate power spectrum across frames
        for (f in 0 until numFrames) {
            val offset = f * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                real[i] = samples[offset + i]
                imag[i] = 0f
            }
            Fft.applyHannWindow(real)
            Fft.transform(real, imag)
            val mag = Fft.magnitudeSpectrum(real, imag)
            for (i in avgSpectrum.indices) {
                avgSpectrum[i] += mag[i] * mag[i]
            }
        }

        // Convert to dBFS
        val binWidth = sampleRate.toFloat() / FFT_SIZE
        val nyquist = sampleRate / 2f
        val spectrumDb = FloatArray(avgSpectrum.size)
        var maxDb = -200f
        for (i in avgSpectrum.indices) {
            val power = avgSpectrum[i] / numFrames
            val db = if (power > 1e-12f) 10f * log10(power) else -120f
            spectrumDb[i] = db
            if (db > maxDb && (i * binWidth) in 500f..12000f) {
                maxDb = db
            }
        }

        if (maxDb < -80f) {
            // Signal is effectively silence / very low level
            return HiResAnalysisResult(
                status = HiResStatus.UNKNOWN,
                confidence = 0.5f,
                cutoffFrequencyHz = 0f,
                maxFrequencyHz = nyquist
            )
        }

        // Calculate average energy in key bands
        fun averageDbInBand(lowHz: Float, highHz: Float): Float {
            val startBin = (lowHz / binWidth).toInt().coerceIn(0, spectrumDb.lastIndex)
            val endBin = (highHz / binWidth).toInt().coerceIn(startBin, spectrumDb.lastIndex)
            if (startBin >= endBin) return spectrumDb[startBin]
            var sum = 0f
            for (b in startBin..endBin) {
                sum += spectrumDb[b]
            }
            return sum / (endBin - startBin + 1)
        }

        val refEnergy = averageDbInBand(2000f, 10000f)
        val band15k16k = averageDbInBand(15000f, 16000f)
        val band16k17k = averageDbInBand(16200f, 17200f)
        val band18k19k = averageDbInBand(18000f, 19200f)
        val band20k22k = averageDbInBand(20000f, 22000f)
        val ultrasonic24k40k = if (nyquist >= 40000f) averageDbInBand(24000f, 40000f) else -120f

        // Search for steepest drop (cutoff frequency) between 12 kHz and Nyquist
        var detectedCutoffHz = nyquist
        var foundHardCutoff = false
        val startScanBin = (12000f / binWidth).toInt().coerceAtLeast(0)
        val endScanBin = ((nyquist - 300f) / binWidth).toInt().coerceAtMost(spectrumDb.lastIndex)

        val thresholdDrop = refEnergy - 20f // 20 dB down marks the knee of the filter/cutoff
        for (b in startScanBin until endScanBin) {
            val freq = b * binWidth
            if (spectrumDb[b] < thresholdDrop) {
                // Verify it stays down for subsequent bins
                val checkEnd = (b + (1000f / binWidth).toInt()).coerceAtMost(spectrumDb.lastIndex)
                var staysLow = true
                for (cb in b..checkEnd) {
                    if (spectrumDb[cb] > thresholdDrop + 5f) {
                        staysLow = false
                        break
                    }
                }
                if (staysLow) {
                    detectedCutoffHz = freq
                    foundHardCutoff = true
                    break
                }
            }
        }

        // Decision logic based on sample rate and cutoff
        return when {
            // High-resolution sample rate (>= 88.2 kHz, e.g. 96k, 192k)
            sampleRate >= 88200 -> {
                if ((foundHardCutoff && detectedCutoffHz <= 24000f) || ultrasonic24k40k < refEnergy - 35f) {
                    HiResAnalysisResult(
                        status = HiResStatus.LIKELY_UPSAMPLED,
                        confidence = 0.90f,
                        cutoffFrequencyHz = if (foundHardCutoff) detectedCutoffHz else 22050f,
                        maxFrequencyHz = nyquist
                    )
                } else {
                    HiResAnalysisResult(
                        status = HiResStatus.GENUINE,
                        confidence = 0.88f,
                        cutoffFrequencyHz = nyquist,
                        maxFrequencyHz = nyquist
                    )
                }
            }

            // Standard / Lossy formats (44.1 / 48 kHz)
            // Cutoff around 14k - 17.2k: Typical low-bitrate MP3/AAC (<= 128 kbps)
            foundHardCutoff && detectedCutoffHz in 14000f..17200f -> {
                HiResAnalysisResult(
                    status = HiResStatus.LIKELY_LOSSY,
                    confidence = 0.92f,
                    cutoffFrequencyHz = detectedCutoffHz,
                    maxFrequencyHz = nyquist
                )
            }

            // Cutoff around 17.5k - 20.5k: Typical high-bitrate lossy MP3/AAC (192 - 320 kbps)
            foundHardCutoff && detectedCutoffHz in 17500f..20500f -> {
                HiResAnalysisResult(
                    status = HiResStatus.LIKELY_LOSSY,
                    confidence = 0.88f,
                    cutoffFrequencyHz = detectedCutoffHz,
                    maxFrequencyHz = nyquist
                )
            }

            // Standard resolution (44.1 / 48 kHz) without artificial low-pass filter
            sampleRate in 44100..48000 && (!foundHardCutoff || detectedCutoffHz >= 20500f) -> {
                HiResAnalysisResult(
                    status = HiResStatus.GENUINE,
                    confidence = 0.85f,
                    cutoffFrequencyHz = nyquist,
                    maxFrequencyHz = nyquist
                )
            }

            else -> {
                HiResAnalysisResult(
                    status = HiResStatus.UNKNOWN,
                    confidence = 0.60f,
                    cutoffFrequencyHz = detectedCutoffHz,
                    maxFrequencyHz = nyquist
                )
            }
        }
    }
}
