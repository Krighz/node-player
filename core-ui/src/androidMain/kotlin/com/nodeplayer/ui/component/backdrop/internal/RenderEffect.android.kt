/*
 * Copyright 2025 Kyant0, Apache License 2.0
 */
package com.nodeplayer.ui.component.backdrop.internal

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.nodeplayer.ui.component.backdrop.RuntimeShader
import com.nodeplayer.ui.component.backdrop.asAndroidRuntimeShader

@RequiresApi(Build.VERSION_CODES.S)
internal fun RenderEffect.toAndroidRenderEffect(): android.graphics.RenderEffect? {
    return try {
        val method = this::class.java.getMethod("getAndroidRenderEffect")
        method.invoke(this) as? android.graphics.RenderEffect
    } catch (_: Throwable) {
        try {
            val field = this::class.java.getDeclaredField("androidRenderEffect").apply { isAccessible = true }
            field.get(this) as? android.graphics.RenderEffect
        } catch (_: Throwable) {
            null
        }
    }
}

internal actual fun RenderEffect.chain(other: RenderEffect): RenderEffect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val a = other.toAndroidRenderEffect()
        val b = this.toAndroidRenderEffect()
        if (a != null && b != null) {
            android.graphics.RenderEffect.createChainEffect(a, b).asComposeRenderEffect()
        } else {
            other
        }
    } else {
        this
    }
}

internal actual fun RuntimeShaderEffect(runtimeShader: RuntimeShader, uniformShaderName: String): RenderEffect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.graphics.RenderEffect.createRuntimeShaderEffect(
            runtimeShader.asAndroidRuntimeShader(),
            uniformShaderName
        ).asComposeRenderEffect()
    } else {
        error("RuntimeShader is not supported on API < 33")
    }
}

internal actual fun ColorFilterEffect(renderEffect: RenderEffect?, colorFilter: ColorFilter): RenderEffect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val base = renderEffect?.toAndroidRenderEffect()
        if (base != null) {
            android.graphics.RenderEffect.createColorFilterEffect(
                colorFilter.asAndroidColorFilter(),
                base
            ).asComposeRenderEffect()
        } else {
            android.graphics.RenderEffect.createColorFilterEffect(
                colorFilter.asAndroidColorFilter()
            ).asComposeRenderEffect()
        }
    } else {
        renderEffect ?: error("ColorFilter is not supported on API < 31")
    }
}
