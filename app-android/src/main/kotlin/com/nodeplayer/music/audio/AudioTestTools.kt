package com.nodeplayer.music.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

object AudioTestTools {

    private var activeJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Safety volume cap to prevent hearing / speaker damage
    const val MAX_SAFE_AMPLITUDE = 0.25f

    enum class TestMode {
        NONE, LEFT_CHANNEL, RIGHT_CHANNEL, POLARITY_IN_PHASE, POLARITY_OUT_PHASE, SINE_TONE, FREQ_SWEEP, PINK_NOISE, WHITE_NOISE
    }

    var currentMode: TestMode = TestMode.NONE
        private set

    fun stop() {
        activeJob?.cancel()
        activeJob = null
        currentMode = TestMode.NONE
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (_: Exception) {}
        activeAudioTrack = null
    }

    /**
     * Plays a channel check tone: 440 Hz on Left channel only, then Right channel only.
     */
    fun playChannelCheck(onChannelSwitch: (isLeft: Boolean) -> Unit) {
        stop()
        currentMode = TestMode.LEFT_CHANNEL
        val sampleRate = 44100
        val bufferSize = sampleRate * 2 // 1 second buffer (stereo)

        val track = createAudioTrack(sampleRate)
        activeAudioTrack = track
        track.play()

        activeJob = scope.launch {
            while (isActive) {
                // 1. Play Left Channel only for 1.5s
                currentMode = TestMode.LEFT_CHANNEL
                withContext(Dispatchers.Main) { onChannelSwitch(true) }
                generateTone(track, sampleRate, 440.0, durationSec = 1.5f, leftGain = MAX_SAFE_AMPLITUDE, rightGain = 0f)
                delay(300L)

                // 2. Play Right Channel only for 1.5s
                currentMode = TestMode.RIGHT_CHANNEL
                withContext(Dispatchers.Main) { onChannelSwitch(false) }
                generateTone(track, sampleRate, 440.0, durationSec = 1.5f, leftGain = 0f, rightGain = MAX_SAFE_AMPLITUDE)
                delay(300L)
            }
        }
    }

    /**
     * Plays in-phase tone vs out-of-phase (inverted) tone to check speaker polarity.
     */
    fun playPolarityCheck(inPhase: Boolean) {
        stop()
        currentMode = if (inPhase) TestMode.POLARITY_IN_PHASE else TestMode.POLARITY_OUT_PHASE
        val sampleRate = 44100
        val track = createAudioTrack(sampleRate)
        activeAudioTrack = track
        track.play()

        activeJob = scope.launch {
            val rightSign = if (inPhase) 1.0f else -1.0f
            while (isActive) {
                generateTone(track, sampleRate, 220.0, durationSec = 1.0f, leftGain = MAX_SAFE_AMPLITUDE, rightGain = MAX_SAFE_AMPLITUDE * rightSign)
            }
        }
    }

    /**
     * Plays a continuous sine wave tone with user-adjustable frequency.
     */
    fun playSineTone(frequencyHz: Double) {
        stop()
        currentMode = TestMode.SINE_TONE
        val sampleRate = 44100
        val track = createAudioTrack(sampleRate)
        activeAudioTrack = track
        track.play()

        activeJob = scope.launch {
            while (isActive) {
                generateTone(track, sampleRate, frequencyHz, durationSec = 1.0f, leftGain = MAX_SAFE_AMPLITUDE, rightGain = MAX_SAFE_AMPLITUDE)
            }
        }
    }

    /**
     * Plays a logarithmic frequency sweep from 20 Hz to 20,000 Hz over 6 seconds.
     */
    fun playFrequencySweep() {
        stop()
        currentMode = TestMode.FREQ_SWEEP
        val sampleRate = 44100
        val durationSec = 6.0f
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = FloatArray(totalSamples * 2)

        val fStart = 20.0
        val fEnd = 20000.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / durationSec
            // Logarithmic frequency ramp
            val currentFreq = fStart * (fEnd / fStart).pow(progress)
            val phase = 2.0 * PI * fStart * ((fEnd / fStart).pow(progress) - 1.0) / kotlin.math.ln(fEnd / fStart) * durationSec
            val sample = (sin(phase) * MAX_SAFE_AMPLITUDE).toFloat()
            buffer[i * 2] = sample
            buffer[i * 2 + 1] = sample
        }

        val track = createAudioTrack(sampleRate)
        activeAudioTrack = track
        track.play()

        activeJob = scope.launch {
            while (isActive) {
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                delay(400L)
            }
        }
    }

    /**
     * Plays Pink Noise (1/f equal energy per octave) or White Noise.
     */
    fun playNoise(isPink: Boolean) {
        stop()
        currentMode = if (isPink) TestMode.PINK_NOISE else TestMode.WHITE_NOISE
        val sampleRate = 44100
        val track = createAudioTrack(sampleRate)
        activeAudioTrack = track
        track.play()

        val chunkSize = 44100
        val buffer = FloatArray(chunkSize * 2)

        // Paul Kellet's filtered pink noise generator filter state
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0

        activeJob = scope.launch {
            while (isActive) {
                for (i in 0 until chunkSize) {
                    val white = (Random.nextFloat() * 2f - 1f) * MAX_SAFE_AMPLITUDE * 0.8f
                    val sample = if (isPink) {
                        b0 = 0.99886 * b0 + white * 0.0555179
                        b1 = 0.99332 * b1 + white * 0.0750759
                        b2 = 0.96900 * b2 + white * 0.1538520
                        b3 = 0.86650 * b3 + white * 0.3104856
                        b4 = 0.55000 * b4 + white * 0.5329522
                        b5 = -0.7616 * b5 - white * 0.0168980
                        ((b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11).toFloat()
                    } else {
                        white
                    }
                    b6 = white * 0.115926
                    buffer[i * 2] = sample
                    buffer[i * 2 + 1] = sample
                }
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    private fun generateTone(
        track: AudioTrack,
        sampleRate: Int,
        frequencyHz: Double,
        durationSec: Float,
        leftGain: Float,
        rightGain: Float
    ) {
        val samples = (sampleRate * durationSec).toInt()
        val buffer = FloatArray(samples * 2)
        val angularFreq = 2.0 * PI * frequencyHz / sampleRate

        for (i in 0 until samples) {
            val sample = sin(i * angularFreq).toFloat()
            buffer[i * 2] = sample * leftGain
            buffer[i * 2 + 1] = sample * rightGain
        }
        track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
    }

    private fun createAudioTrack(sampleRate: Int): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(sampleRate * 2 * 4)
            .build()
    }
}
