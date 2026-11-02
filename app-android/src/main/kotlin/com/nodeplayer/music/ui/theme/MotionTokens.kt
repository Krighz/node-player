package com.nodeplayer.music.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
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
        val speedFactor = config.motionSpeed.coerceIn(0.5f, 2.0f)
        return when (config.animationPreset) {
            "MINIMAL" -> spring(dampingRatio = 0.95f, stiffness = 700f * speedFactor)
            "BOUNCY" -> spring(dampingRatio = 0.65f, stiffness = 420f * speedFactor)
            "CUSTOM" -> spring(dampingRatio = config.springBounciness.coerceIn(0.3f, 1.0f), stiffness = config.springStiffness.coerceIn(150f, 1000f) * speedFactor)
            "SMOOTH" -> spring(dampingRatio = 0.82f, stiffness = 380f * speedFactor)
            else -> defaultSpec
        }
    }
}

/**
 * Modifier that scales a composable down when pressed and springs back when released.
 * Inspired by iOS fluid touch responsiveness. Respects user motion & reduce-motion preferences.
 */
fun Modifier.pressScale(
    targetScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val themeConfig = LocalGlassThemeConfig.current
    val isEffectEnabled = enabled && themeConfig.pressScaleEnabled && !themeConfig.reduceMotion && !themeConfig.isPerformanceMode

    var isPressed by remember { mutableStateOf(false) }
    val effectiveTargetScale = if (isEffectEnabled) themeConfig.pressScaleStrength else 1.0f
    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEffectEnabled) effectiveTargetScale else 1.0f,
        animationSpec = MotionTokens.resolveSpringSpec(themeConfig, MotionTokens.Snappy),
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
    val themeConfig = LocalGlassThemeConfig.current
    val isEffectEnabled = themeConfig.pressScaleEnabled && !themeConfig.reduceMotion && !themeConfig.isPerformanceMode
    val isPressed by interactionSource.collectIsPressedAsState()
    val effectiveTarget = if (isEffectEnabled) themeConfig.pressScaleStrength else 1.0f

    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEffectEnabled) effectiveTarget else 1.0f,
        animationSpec = MotionTokens.resolveSpringSpec(themeConfig, MotionTokens.Snappy),
        label = "InteractiveScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
