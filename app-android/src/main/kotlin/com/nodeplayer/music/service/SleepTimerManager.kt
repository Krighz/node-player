package com.nodeplayer.music.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class SleepTimerManager(
    private val context: Context,
    private val playerProvider: () -> ExoPlayer?,
    private val onTimerExpired: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "SleepTimerManager"
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 500
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null

    // State
    private val _remainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds: StateFlow<Long?> = _remainingSeconds.asStateFlow()

    private val _stopAfterTracks = MutableStateFlow<Int?>(null)
    val stopAfterTracks: StateFlow<Int?> = _stopAfterTracks.asStateFlow()

    var finishCurrentTrack: Boolean = true
    var fadeSeconds: Int = 10
    var shakeToExtendEnabled: Boolean = true

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastShakeTimestamp: Long = 0

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    fun startTimer(
        minutes: Int,
        finishTrack: Boolean = true,
        fadeLengthSec: Int = 10,
        stopAfterNTracks: Int = 0,
        shakeExtend: Boolean = true
    ) {
        cancel()
        this.finishCurrentTrack = finishTrack
        this.fadeSeconds = fadeLengthSec
        this.shakeToExtendEnabled = shakeExtend

        if (stopAfterNTracks > 0) {
            _stopAfterTracks.value = stopAfterNTracks
        } else {
            _stopAfterTracks.value = null
        }

        val totalSec = minutes * 60L
        _remainingSeconds.value = totalSec

        if (shakeExtend && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }

        tickerJob = scope.launch {
            var currentSec = totalSec
            while (isActive && currentSec > 0) {
                delay(1000L)
                currentSec--
                _remainingSeconds.value = currentSec

                // Smooth volume fade during the last fadeSeconds
                val p = playerProvider()
                if (p != null && currentSec <= fadeSeconds && fadeSeconds > 0) {
                    val progress = (currentSec.toFloat() / fadeSeconds.toFloat()).coerceIn(0f, 1f)
                    p.volume = progress
                }
            }

            if (isActive) {
                handleTimerFinished()
            }
        }
        Log.d(TAG, "Started sleep timer for $minutes minutes (fade: ${fadeLengthSec}s, finishTrack: $finishTrack)")
    }

    fun extend(additionalMinutes: Int) {
        val current = _remainingSeconds.value ?: 0L
        val updated = current + (additionalMinutes * 60L)
        _remainingSeconds.value = updated
        // Restore player volume if it was fading
        playerProvider()?.volume = 1.0f
        Log.d(TAG, "Extended sleep timer by $additionalMinutes min (total: ${updated / 60} min)")
    }

    fun onTrackChanged() {
        val remaining = _stopAfterTracks.value
        if (remaining != null) {
            val next = remaining - 1
            if (next <= 0) {
                _stopAfterTracks.value = null
                handleTimerFinished()
            } else {
                _stopAfterTracks.value = next
            }
        }
    }

    private fun handleTimerFinished() {
        val player = playerProvider() ?: return
        if (finishCurrentTrack && player.isPlaying && player.duration > 0) {
            val remainingMs = player.duration - player.currentPosition
            if (remainingMs in 1..180000) { // If less than 3 min left on track, wait until completion
                scope.launch {
                    delay(remainingMs)
                    finishPlayback()
                }
                return
            }
        }
        finishPlayback()
    }

    private fun finishPlayback() {
        val player = playerProvider()
        if (player != null) {
            player.pause()
            // Restore volume for next playback session
            player.volume = 1.0f
        }
        cancel()
        onTimerExpired()
        Log.d(TAG, "Sleep timer expired. Playback paused safely and volume restored.")
    }

    fun cancel() {
        tickerJob?.cancel()
        tickerJob = null
        _remainingSeconds.value = null
        _stopAfterTracks.value = null
        playerProvider()?.volume = 1.0f
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    // Shake sensor detection
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !shakeToExtendEnabled || _remainingSeconds.value == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0] / SensorManager.GRAVITY_EARTH
            val y = event.values[1] / SensorManager.GRAVITY_EARTH
            val z = event.values[2] / SensorManager.GRAVITY_EARTH

            val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                val now = System.currentTimeMillis()
                if (lastShakeTimestamp + SHAKE_SLOP_TIME_MS > now) return
                lastShakeTimestamp = now

                // Shake detected! Add 5 minutes
                extend(5)
                Log.d(TAG, "Shake gesture detected! Added +5 minutes to Sleep Timer.")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun release() {
        cancel()
        scope.cancel()
    }
}
