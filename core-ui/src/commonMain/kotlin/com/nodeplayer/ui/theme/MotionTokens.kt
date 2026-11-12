package com.nodeplayer.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel

/**
 * Centralized motion tokens, duration constants, easing curves, and physics resolvers
 * for Node Player's fluid iOS-grade animation system.
 */
object MotionTokens {

    // --- Standard Duration Constants (ms) ---
    const val DurationShort: Int = 150
    const val DurationMedium: Int = 300
    const val DurationLong: Int = 500

    // --- Standard Easing Curves ---
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val DecelerateEasing: Easing = LinearOutSlowInEasing
    val AccelerateEasing: Easing = FastOutSlowInEasing

    /**
     * Convx-matched critically damped spring for the selection puck.
     */
    val ConvxPuck: AnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 380f
    )

    val ConvxPuckDp: AnimationSpec<Dp> = spring(
        dampingRatio = 0.82f,
        stiffness = 380f
    )

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
     * Touch-tracking spring with minimal latency for interactive dragging.
     */
    val DragTracking: AnimationSpec<Float> = spring(
        dampingRatio = 0.95f,
        stiffness = 900f
    )

    /**
     * Instant / snap spec when motion is disabled.
     */
    val Instant: AnimationSpec<Float> = spring(
        dampingRatio = 1.0f,
        stiffness = 50000f
    )

    /**
     * Dynamically resolves the spring spec based on user Motion settings and accessibility.
     */
    fun resolveSpringSpec(
        config: GlassThemeConfig,
        defaultSpec: AnimationSpec<Float> = Smooth
    ): AnimationSpec<Float> {
        if (config.reduceMotion || config.motionLevel == MotionLevel.OFF || config.animationPreset.uppercase() == "OFF" || config.isPerformanceMode) {
            return Instant
        }
        if (config.motionLevel == MotionLevel.REDUCED) {
            return Gentle
        }
        val speedFactor = config.motionSpeed.coerceIn(0.5f, 2.0f)
        return when (config.animationPreset.uppercase()) {
            "SNAPPY" -> if (speedFactor == 1.0f) Snappy else spring(dampingRatio = 0.75f, stiffness = 600f * speedFactor)
            "BOUNCY" -> if (speedFactor == 1.0f) Bouncy else spring(dampingRatio = 0.65f, stiffness = 420f * speedFactor)
            "GENTLE" -> if (speedFactor == 1.0f) Gentle else spring(dampingRatio = 0.90f, stiffness = 220f * speedFactor)
            "MINIMAL" -> spring(dampingRatio = 0.95f, stiffness = 700f * speedFactor)
            "CUSTOM" -> spring(
                dampingRatio = config.springBounciness.coerceIn(0.3f, 1.0f),
                stiffness = config.springStiffness.coerceIn(150f, 1000f) * speedFactor
            )
            "SMOOTH" -> if (speedFactor == 1.0f) Smooth else spring(dampingRatio = 0.82f, stiffness = 380f * speedFactor)
            else -> defaultSpec
        }
    }

    /**
     * Resolves the animation duration in milliseconds based on motion settings.
     * When motion is disabled, returns 0 ms for immediate transition.
     */
    fun resolveDuration(
        config: GlassThemeConfig,
        baseDurationMillis: Int = DurationMedium
    ): Int {
        if (config.reduceMotion || config.motionLevel == MotionLevel.OFF || config.animationPreset.uppercase() == "OFF" || config.isPerformanceMode) {
            return 0
        }
        if (config.motionLevel == MotionLevel.REDUCED) {
            return DurationShort
        }
        val speedFactor = config.motionSpeed.coerceIn(0.5f, 2.0f)
        // Higher motionSpeed means faster animation -> shorter duration
        return (baseDurationMillis / speedFactor).toInt().coerceAtLeast(1)
    }
}
