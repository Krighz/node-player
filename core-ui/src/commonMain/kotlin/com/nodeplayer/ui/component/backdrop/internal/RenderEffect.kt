/*
 * Copyright 2025 Kyant0, Apache License 2.0
 */
package com.nodeplayer.ui.component.backdrop.internal

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import com.nodeplayer.ui.component.backdrop.RuntimeShader

internal expect fun RenderEffect.chain(other: RenderEffect): RenderEffect

internal expect fun RuntimeShaderEffect(runtimeShader: RuntimeShader, uniformShaderName: String): RenderEffect

internal expect fun ColorFilterEffect(renderEffect: RenderEffect?, colorFilter: ColorFilter): RenderEffect


