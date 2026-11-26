package com.nodeplayer.music.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nodeplayer.ui.theme.GyroscopeTiltState

/**
 * Provides smooth, battery-friendly 3D Gyroscope tilt state on Android.
 * When [enabled] is false, zero sensor listeners are registered, ensuring zero battery overhead.
 */
@Composable
fun rememberAndroidGyroscopeTiltState(
    enabled: Boolean,
    tiltStrength: Float = 1.0f
): GyroscopeTiltState {
    if (!enabled) {
        return remember { GyroscopeTiltState(isEnabled = false) }
    }

    val context = LocalContext.current
    var rawPitch by remember { mutableFloatStateOf(0f) }
    var rawRoll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context, enabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose {}
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    // orientation[1] = pitch (tilt forward/backward), orientation[2] = roll (tilt left/right)
                    val pitchNorm = (orientation[1] / (Math.PI.toFloat() / 3f)).coerceIn(-1f, 1f)
                    val rollNorm = (orientation[2] / (Math.PI.toFloat() / 3f)).coerceIn(-1f, 1f)
                    rawPitch = pitchNorm
                    rawRoll = rollNorm
                } else {
                    // Fallback using gravity / accelerometer values
                    val x = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val y = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                    rawRoll = -x
                    rawPitch = y
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val smoothTiltX by animateFloatAsState(
        targetValue = rawRoll * tiltStrength,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.82f),
        label = "GyroTiltX"
    )

    val smoothTiltY by animateFloatAsState(
        targetValue = rawPitch * tiltStrength,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.82f),
        label = "GyroTiltY"
    )

    return remember(smoothTiltX, smoothTiltY, enabled) {
        GyroscopeTiltState(
            isEnabled = enabled,
            tiltX = smoothTiltX,
            tiltY = smoothTiltY,
            specularOffset = smoothTiltX
        )
    }
}
