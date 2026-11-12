package com.nodeplayer.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.nodeplayer.model.GlassThemeConfig

/**
 * Ambient composition local providing the active [GlassThemeConfig] to motion modifiers and tokens.
 * Defaults to [LocalGlassThemeConfig] for unified theme cohesion.
 */
val LocalMotionConfig = compositionLocalOf { GlassThemeConfig() }

/**
 * Convenient Composable helper to resolve an [AnimationSpec] from the current ambient motion configuration.
 */
@Composable
fun rememberResolvedSpring(
    defaultSpec: AnimationSpec<Float> = MotionTokens.Smooth
): AnimationSpec<Float> {
    val config = LocalMotionConfig.current
    return MotionTokens.resolveSpringSpec(config, defaultSpec)
}
