package com.nodeplayer.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.nodeplayer.model.GlassThemeConfig

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

    /**
     * Dynamically resolves the spring spec based on user Motion settings.
     */
    fun resolveSpringSpec(
        config: GlassThemeConfig,
        defaultSpec: AnimationSpec<Float> = Smooth
    ): AnimationSpec<Float> {
        if (config.reduceMotion || config.animationPreset == "OFF" || config.isPerformanceMode) {
            return spring(dampingRatio = 1.0f, stiffness = 2500f)
        }
        return when (config.animationPreset.uppercase()) {
            "SNAPPY" -> Snappy
            "BOUNCY" -> Bouncy
            "GENTLE" -> Gentle
            else -> defaultSpec
        }
    }
}

/**
 * High-performance fluid press scale modifier with spring physics and zero stutter.
 */
fun Modifier.pressScale(
    targetScale: Float = 0.96f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

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
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )
}
