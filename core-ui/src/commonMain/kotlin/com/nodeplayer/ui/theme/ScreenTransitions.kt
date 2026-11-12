package com.nodeplayer.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel

/**
 * Directional intent for screen and navigation transitions.
 */
enum class TransitionDirection {
    FORWARD,
    BACKWARD,
    NONE
}

/**
 * Reusable, centralized screen transition specifications parameterised by [GlassThemeConfig]
 * and [MotionTokens]. Supports fade-through for tab switching, shared-axis horizontal and
 * vertical sliding for hierarchical navigation, and spring physics for sheets/dialogs.
 */
object ScreenTransitions {

    /**
     * Checks if motion is disabled according to theme and accessibility configuration.
     */
    fun isMotionDisabled(config: GlassThemeConfig): Boolean {
        return config.reduceMotion ||
            config.motionLevel == MotionLevel.OFF ||
            config.animationPreset.uppercase() == "OFF" ||
            config.isPerformanceMode
    }

    /**
     * Resolves a tween specification respecting user motion speed and disablement.
     */
    fun <T> resolveTween(
        config: GlassThemeConfig,
        duration: Int = MotionTokens.DurationMedium,
        delay: Int = 0
    ): TweenSpec<T> {
        val resolvedDuration = MotionTokens.resolveDuration(config, duration)
        return tween(
            durationMillis = resolvedDuration,
            delayMillis = if (isMotionDisabled(config)) 0 else delay,
            easing = MotionTokens.DecelerateEasing
        )
    }

    /**
     * Resolves a finite spring specification for scale and float properties.
     */
    fun resolveFiniteSpring(
        config: GlassThemeConfig,
        dampingRatio: Float = 0.82f,
        stiffness: Float = 380f
    ): FiniteAnimationSpec<Float> {
        if (isMotionDisabled(config)) {
            return snap()
        }
        if (config.motionLevel == MotionLevel.REDUCED) {
            return spring(dampingRatio = 0.90f, stiffness = 220f)
        }
        val speedFactor = config.motionSpeed.coerceIn(0.5f, 2.0f)
        return when (config.animationPreset.uppercase()) {
            "SNAPPY" -> spring(dampingRatio = 0.75f, stiffness = 600f * speedFactor)
            "BOUNCY" -> spring(dampingRatio = 0.65f, stiffness = 420f * speedFactor)
            "GENTLE" -> spring(dampingRatio = 0.90f, stiffness = 220f * speedFactor)
            "MINIMAL" -> spring(dampingRatio = 0.95f, stiffness = 700f * speedFactor)
            "CUSTOM" -> spring(
                dampingRatio = config.springBounciness.coerceIn(0.3f, 1.0f),
                stiffness = config.springStiffness.coerceIn(150f, 1000f) * speedFactor
            )
            else -> spring(dampingRatio = dampingRatio, stiffness = stiffness * speedFactor)
        }
    }

    /**
     * Spring physics spec for sheets, popups, and dialogs.
     * Uses [MotionTokens.Smooth] or user-configured custom physics.
     */
    fun sheetSpring(config: GlassThemeConfig): AnimationSpec<Float> {
        return MotionTokens.resolveSpringSpec(config, MotionTokens.Smooth)
    }

    /**
     * Sheet IntOffset spring spec for vertical slide animations.
     */
    fun sheetOffsetSpring(config: GlassThemeConfig): FiniteAnimationSpec<IntOffset> {
        if (isMotionDisabled(config)) {
            return snap()
        }
        return if (config.motionLevel == MotionLevel.REDUCED) {
            spring(dampingRatio = 0.90f, stiffness = 220f)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 380f)
        }
    }

    /**
     * Fade-through screen transition for tab navigation (Home, Search, Library, Settings).
     *
     * Incoming view fades in and scales gently from 0.96f to 1.0f.
     * Outgoing view fades out cleanly.
     * When motion is disabled, instantly swaps with snap animations.
     */
    fun fadeThrough(config: GlassThemeConfig): ContentTransform {
        if (isMotionDisabled(config)) {
            return ContentTransform(
                targetContentEnter = fadeIn(animationSpec = snap()),
                initialContentExit = fadeOut(animationSpec = snap()),
                targetContentZIndex = 0f,
                sizeTransform = SizeTransform(clip = false)
            )
        }

        val inDuration = MotionTokens.resolveDuration(config, MotionTokens.DurationMedium)
        val outDuration = MotionTokens.resolveDuration(config, MotionTokens.DurationShort)

        val inTween: TweenSpec<Float> = tween(
            durationMillis = inDuration,
            easing = MotionTokens.DecelerateEasing
        )
        val outTween: TweenSpec<Float> = tween(
            durationMillis = outDuration,
            easing = MotionTokens.AccelerateEasing
        )

        val enter = fadeIn(animationSpec = inTween) + scaleIn(
            initialScale = 0.96f,
            animationSpec = resolveFiniteSpring(config, dampingRatio = 0.82f, stiffness = 380f)
        )
        val exit = fadeOut(animationSpec = outTween)

        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            targetContentZIndex = 0f,
            sizeTransform = SizeTransform(clip = false)
        )
    }

    /**
     * Shared-axis horizontal transition for forward and backward drill-down navigation
     * (e.g. Library -> Artist/Album -> Track detail).
     */
    fun sharedAxisX(
        direction: TransitionDirection,
        config: GlassThemeConfig
    ): ContentTransform {
        if (isMotionDisabled(config) || direction == TransitionDirection.NONE) {
            return ContentTransform(
                targetContentEnter = fadeIn(animationSpec = snap()),
                initialContentExit = fadeOut(animationSpec = snap()),
                targetContentZIndex = 0f,
                sizeTransform = SizeTransform(clip = false)
            )
        }

        val slideSpring: FiniteAnimationSpec<IntOffset> = if (config.motionLevel == MotionLevel.REDUCED) {
            spring(dampingRatio = 0.90f, stiffness = 220f)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 380f)
        }

        val fadeDuration = MotionTokens.resolveDuration(config, MotionTokens.DurationMedium)
        val inFade: TweenSpec<Float> = tween(durationMillis = fadeDuration, easing = MotionTokens.DecelerateEasing)
        val outFade: TweenSpec<Float> = tween(durationMillis = fadeDuration, easing = MotionTokens.AccelerateEasing)

        val sign = if (direction == TransitionDirection.FORWARD) 1 else -1

        val enter = slideInHorizontally(
            animationSpec = slideSpring,
            initialOffsetX = { fullWidth -> (fullWidth * 0.25f * sign).toInt() }
        ) + fadeIn(animationSpec = inFade)

        val exit = slideOutHorizontally(
            animationSpec = slideSpring,
            targetOffsetX = { fullWidth -> (-fullWidth * 0.25f * sign).toInt() }
        ) + fadeOut(animationSpec = outFade)

        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            targetContentZIndex = 0f,
            sizeTransform = SizeTransform(clip = false)
        )
    }

    /**
     * Shared-axis vertical transition for modal cards, dialog reveals, or overlay navigation.
     */
    fun sharedAxisY(
        direction: TransitionDirection,
        config: GlassThemeConfig
    ): ContentTransform {
        if (isMotionDisabled(config) || direction == TransitionDirection.NONE) {
            return ContentTransform(
                targetContentEnter = fadeIn(animationSpec = snap()),
                initialContentExit = fadeOut(animationSpec = snap()),
                targetContentZIndex = 0f,
                sizeTransform = SizeTransform(clip = false)
            )
        }

        val slideSpring: FiniteAnimationSpec<IntOffset> = if (config.motionLevel == MotionLevel.REDUCED) {
            spring(dampingRatio = 0.90f, stiffness = 220f)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 380f)
        }

        val fadeDuration = MotionTokens.resolveDuration(config, MotionTokens.DurationMedium)
        val inFade: TweenSpec<Float> = tween(durationMillis = fadeDuration, easing = MotionTokens.DecelerateEasing)
        val outFade: TweenSpec<Float> = tween(durationMillis = fadeDuration, easing = MotionTokens.AccelerateEasing)

        val sign = if (direction == TransitionDirection.FORWARD) 1 else -1

        val enter = slideInVertically(
            animationSpec = slideSpring,
            initialOffsetY = { fullHeight -> (fullHeight * 0.20f * sign).toInt() }
        ) + fadeIn(animationSpec = inFade)

        val exit = slideOutVertically(
            animationSpec = slideSpring,
            targetOffsetY = { fullHeight -> (-fullHeight * 0.20f * sign).toInt() }
        ) + fadeOut(animationSpec = outFade)

        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            targetContentZIndex = 0f,
            sizeTransform = SizeTransform(clip = false)
        )
    }

    /**
     * Sheet enter transition using unified spring physics.
     */
    fun sheetEnter(config: GlassThemeConfig): EnterTransition {
        if (isMotionDisabled(config)) {
            return fadeIn(snap())
        }
        val offsetSpec: FiniteAnimationSpec<IntOffset> = sheetOffsetSpring(config)
        val fadeSpec: TweenSpec<Float> = resolveTween(config, MotionTokens.DurationShort)
        return fadeIn(fadeSpec) + slideInVertically(animationSpec = offsetSpec) { fullHeight -> fullHeight }
    }

    /**
     * Sheet exit transition using unified spring physics.
     */
    fun sheetExit(config: GlassThemeConfig): ExitTransition {
        if (isMotionDisabled(config)) {
            return fadeOut(snap())
        }
        val offsetSpec: FiniteAnimationSpec<IntOffset> = sheetOffsetSpring(config)
        val fadeSpec: TweenSpec<Float> = resolveTween(config, MotionTokens.DurationShort)
        return fadeOut(fadeSpec) + slideOutVertically(animationSpec = offsetSpec) { fullHeight -> fullHeight }
    }
}
