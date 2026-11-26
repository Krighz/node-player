package com.nodeplayer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * 3D Gyroscope Tilt State for dynamic artwork parallax and liquid glass reflections.
 *
 * @param isEnabled Whether 3D tilt parallax is currently active.
 * @param tiltX Pitch tilt value (-1f to 1f).
 * @param tiltY Roll tilt value (-1f to 1f).
 * @param specularOffset Dynamic offset for specular highlight sheen.
 */
@Immutable
data class GyroscopeTiltState(
    val isEnabled: Boolean = false,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val specularOffset: Float = 0f
)

val LocalGyroscopeTilt = compositionLocalOf { GyroscopeTiltState() }
