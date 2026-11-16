package com.nodeplayer.music.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.nodeplayer.model.GlassThemeConfig
import kotlinx.coroutines.delay

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
            "CUSTOM" -> spring(
                dampingRatio = config.springBounciness.coerceIn(0.3f, 1.0f),
                stiffness = config.springStiffness.coerceIn(150f, 1000f) * speedFactor
            )
            "SMOOTH" -> spring(dampingRatio = 0.82f, stiffness = 380f * speedFactor)
            else -> defaultSpec
        }
    }
}

/**
 * Scroll-safe press-scale modifier using MutableInteractionSource so LazyColumn/LazyVerticalGrid
 * scroll gestures are never intercepted or delayed.
 */
fun Modifier.pressScale(
    targetScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val themeConfig = LocalGlassThemeConfig.current
    val isEffectEnabled = enabled && themeConfig.pressScaleEnabled && !themeConfig.reduceMotion && !themeConfig.isPerformanceMode
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val effectiveTargetScale = if (isEffectEnabled) {
        if (targetScale != 0.96f) targetScale else themeConfig.pressScaleStrength
    } else {
        1.0f
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEffectEnabled) effectiveTargetScale else 1.0f,
        animationSpec = MotionTokens.resolveSpringSpec(themeConfig, MotionTokens.Snappy),
        label = "PressScale"
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    } else {
        Modifier
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(clickModifier)
}

/**
 * Staggered entrance animation for initial list items, respecting user motion preferences.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    maxAnimatedIndex: Int = 10
): Modifier = composed {
    val themeConfig = LocalGlassThemeConfig.current
    val shouldAnimate = themeConfig.staggeredListAnimation &&
        !themeConfig.reduceMotion &&
        !themeConfig.isPerformanceMode &&
        themeConfig.animationPreset != "OFF" &&
        index in 0..maxAnimatedIndex

    if (!shouldAnimate) return@composed this

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delayMs = (index * 24L / themeConfig.motionSpeed.coerceIn(0.5f, 2.0f)).toLong()
        if (delayMs > 0L) delay(delayMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (240f / themeConfig.motionSpeed.coerceIn(0.5f, 2.0f)).toInt().coerceIn(120, 400)
            )
        )
    }

    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18f
    }
}
