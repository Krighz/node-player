package com.nodeplayer.music.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

object MotionTokens {

    /**
     * Fast, tactile spring for button presses, toggles, and micro-interactions.
     */
    val Snappy: AnimationSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 600f
    )

    /**
     * Fluid, natural spring for bottom sheets, navigation transitions, and cards.
     */
    val Smooth: AnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 380f
    )

    /**
     * Playful spring with visible overshoot for favorite hearts, badges, and success states.
     */
    val Bouncy: AnimationSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = 420f
    )

    /**
     * Relaxed, cushioned spring for large layout morphs and backdrop transitions.
     */
    val Gentle: AnimationSpec<Float> = spring(
        dampingRatio = 0.90f,
        stiffness = 220f
    )
}

/**
 * Modifier that scales a composable down when pressed and springs back when released.
 * Inspired by iOS fluid touch responsiveness.
 */
fun Modifier.pressScale(
    targetScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    if (!enabled) return@composed this

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = MotionTokens.Snappy,
        label = "PressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled, onClick) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    val success = tryAwaitRelease()
                    isPressed = false
                    if (success && onClick != null) {
                        onClick()
                    }
                }
            )
        }
}

/**
 * Modifier using interaction source for standard Compose clickable with press scale.
 */
fun Modifier.interactiveScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.96f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = MotionTokens.Snappy,
        label = "InteractiveScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
