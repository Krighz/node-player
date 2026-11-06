/*
 * Copyright 2025 Kyant0, Apache License 2.0
 * Multiplatform abstraction for backdrop rendering capabilities.
 */
package com.nodeplayer.ui.component.backdrop

expect fun isRenderEffectSupported(): Boolean

expect fun isRuntimeShaderSupported(): Boolean

@androidx.compose.runtime.Composable
fun isGlassDark(): Boolean {
    val themeConfig = com.nodeplayer.ui.theme.LocalGlassThemeConfig.current
    return themeConfig.themeMode != "LIGHT"
}


