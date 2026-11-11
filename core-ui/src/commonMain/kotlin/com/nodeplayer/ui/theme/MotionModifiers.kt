package com.nodeplayer.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Reusable zero-allocation interaction modifiers for Node Player.
 * All property mutations (scale, alpha, translation) are deferred to graphicsLayer
 * to guarantee zero recomposition cycles and maintain 120Hz fluid rendering.
 */

/**
 * High-performance fluid press scale modifier with spring physics and zero stutter.
 * Scales down on press and bounces back on release. Respects user motion & reduceMotion preferences.
 */
fun Modifier.pressScale(
    targetScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val themeConfig = LocalGlassThemeConfig.current
    val isEffectEnabled = enabled && themeConfig.pressScaleEnabled && !themeConfig.reduceMotion &&
            themeConfig.motionLevel != com.nodeplayer.model.MotionLevel.OFF &&
            themeConfig.animationPreset.uppercase() != "OFF" &&
            !themeConfig.isPerformanceMode

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val effectiveTargetScale = if (isEffectEnabled) {
        // Allow explicit targetScale override if passed differently than default, otherwise use themeConfig strength
        if (targetScale != 0.96f) targetScale else themeConfig.pressScaleStrength
    } else 1.0f

    val springSpec = remember(themeConfig) {
        MotionTokens.resolveSpringSpec(themeConfig, MotionTokens.Snappy)
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && isEffectEnabled) effectiveTargetScale else 1.0f,
        animationSpec = springSpec,
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
                    enabled = enabled,
                    onClick = onClick
                )
            } else Modifier
        )
}

/**
 * Modifier using interaction source for standard Compose clickable with press scale.
 */
fun Modifier.interactiveScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.96f
): Modifier = composed {
    val themeConfig = LocalGlassThemeConfig.current
    val isEffectEnabled = themeConfig.pressScaleEnabled && !themeConfig.reduceMotion &&
            themeConfig.motionLevel != com.nodeplayer.model.MotionLevel.OFF &&
            themeConfig.animationPreset.uppercase() != "OFF" &&
            !themeConfig.isPerformanceMode

    val isPressed by interactionSource.collectIsPressedAsState()
    val effectiveTarget = if (isEffectEnabled) {
        if (targetScale != 0.96f) targetScale else themeConfig.pressScaleStrength
    } else 1.0f

    val springSpec = remember(themeConfig) {
        MotionTokens.resolveSpringSpec(themeConfig, MotionTokens.Snappy)
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && isEffectEnabled) effectiveTarget else 1.0f,
        animationSpec = springSpec,
        label = "InteractiveScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades content in smoothly upon composition or key change.
 * Bypasses animation immediately when motion is disabled or reduceMotion is active.
 */
fun Modifier.fadeInOnLoad(
    key: Any? = null,
    durationMillis: Int = MotionTokens.DurationMedium
): Modifier = composed {
    val themeConfig = LocalGlassThemeConfig.current
    val effectiveDuration = remember(themeConfig, durationMillis) {
        MotionTokens.resolveDuration(themeConfig, durationMillis)
    }

    val animatableAlpha = remember(key) {
        androidx.compose.animation.core.Animatable(if (effectiveDuration == 0) 1.0f else 0.0f)
    }

    LaunchedEffect(key, effectiveDuration) {
        if (effectiveDuration == 0) {
            animatableAlpha.snapTo(1.0f)
        } else {
            animatableAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = effectiveDuration,
                    easing = MotionTokens.DecelerateEasing
                )
            )
        }
    }

    this.graphicsLayer {
        alpha = animatableAlpha.value
    }
}

/**
 * Staggered cascade entrance modifier for items in a list or grid.
 * Translates vertically and fades in with a clamped index delay.
 * Clamps to [maxIndex] (default 12) to avoid unbounded delays or excessive coroutines.
 * Bypasses animation completely if staggeredListAnimation is disabled or motion is reduced/off.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    maxIndex: Int = 12,
    itemDelayMillis: Int = 30,
    slideDistanceDp: Float = 24f
): Modifier = composed {
    val themeConfig = LocalGlassThemeConfig.current
    val isAnimationEnabled = themeConfig.staggeredListAnimation &&
            !themeConfig.reduceMotion &&
            themeConfig.motionLevel == com.nodeplayer.model.MotionLevel.FULL &&
            themeConfig.animationPreset.uppercase() != "OFF" &&
            !themeConfig.isPerformanceMode &&
            index < maxIndex

    if (!isAnimationEnabled) {
        return@composed Modifier
    }

    val clampedIndex = index.coerceIn(0, maxIndex)
    val delayMs = clampedIndex * itemDelayMillis

    val progress = remember(index) { androidx.compose.animation.core.Animatable(0.0f) }

    LaunchedEffect(index) {
        if (delayMs > 0) {
            kotlinx.coroutines.delay(delayMs.toLong())
        }
        progress.animateTo(
            targetValue = 1.0f,
            animationSpec = MotionTokens.resolveSpringSpec(themeConfig, MotionTokens.Smooth)
        )
    }

    this.graphicsLayer {
        val t = progress.value
        alpha = t.coerceIn(0f, 1f)
        translationY = (1.0f - t) * slideDistanceDp * density
    }
}
