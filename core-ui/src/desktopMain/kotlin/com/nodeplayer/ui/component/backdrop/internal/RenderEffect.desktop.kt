/*
 * Copyright 2025 Kyant0, Apache License 2.0
 */
package com.nodeplayer.ui.component.backdrop.internal

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asDesktopImageFilter
import androidx.compose.ui.graphics.asSkiaColorFilter
import com.nodeplayer.ui.component.backdrop.RuntimeShader
import com.nodeplayer.ui.component.backdrop.asSkikoRuntimeShader
import org.jetbrains.skia.ImageFilter

internal actual fun RenderEffect.chain(other: RenderEffect): RenderEffect {
    val imageFilter = ImageFilter.makeCompose(
        other.asDesktopImageFilter(),
        this.asDesktopImageFilter()
    )
    return imageFilter.asComposeRenderEffect()
}

internal actual fun RuntimeShaderEffect(runtimeShader: RuntimeShader, uniformShaderName: String): RenderEffect {
    val builder = runtimeShader.asSkikoRuntimeShader()
    val filter = ImageFilter.makeRuntimeShader(builder, uniformShaderName, null)
    return filter.asComposeRenderEffect()
}

internal actual fun ColorFilterEffect(renderEffect: RenderEffect?, colorFilter: ColorFilter): RenderEffect {
    val filter = ImageFilter.makeColorFilter(
        colorFilter.asSkiaColorFilter(),
        renderEffect?.asDesktopImageFilter(),
        null
    )
    return filter.asComposeRenderEffect()
}
