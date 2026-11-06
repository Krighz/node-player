/*
 * Copyright 2025 Kyant0, Apache License 2.0
 * Multiplatform liquid glass backdrop modifier for Compose 1.6+.
 */
package com.nodeplayer.ui.component.backdrop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.nodeplayer.ui.component.backdrop.highlight.Highlight
import com.nodeplayer.ui.component.backdrop.highlight.drawHighlight
import com.nodeplayer.ui.component.backdrop.shadow.InnerShadow
import com.nodeplayer.ui.component.backdrop.shadow.Shadow
import com.nodeplayer.ui.component.backdrop.shadow.drawInnerShadow
import com.nodeplayer.ui.component.backdrop.shadow.drawShadow

fun Modifier.drawBackdrop(
    backdrop: Backdrop? = null,
    shape: () -> Shape = { RoundedCornerShape(percent = 50) },
    effects: (BackdropEffectScope.() -> Unit)? = null,
    highlight: (() -> Highlight?)? = null,
    shadow: (() -> Shadow?)? = null,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null
): Modifier = composed {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var measuredSize by remember { mutableStateOf(Size.Zero) }

    val resolvedShape = shape()
    val scope = remember { BackdropEffectScopeImpl() }

    val effect = remember(measuredSize, effects, resolvedShape) {
        if (measuredSize.width <= 0f || measuredSize.height <= 0f || effects == null) null
        else {
            scope.size = measuredSize
            scope.density = density.density
            scope.fontScale = density.fontScale
            scope.layoutDirection = layoutDirection
            scope.shape = resolvedShape
            scope.renderEffect = null
            scope.padding = 0f
            scope.effects()
            scope.renderEffect
        }
    }

    this
        .onSizeChanged { measuredSize = Size(it.width.toFloat(), it.height.toFloat()) }
        .graphicsLayer {
            clip = true
            this.shape = resolvedShape
            if (effect != null && isRenderEffectSupported()) {
                this.renderEffect = effect
            }
            layerBlock?.invoke(this)
        }
        .drawWithContent {
            shadow?.invoke()?.let { drawShadow(it, resolvedShape) }
            onDrawSurface?.invoke(this)
            drawContent()
            innerShadow?.invoke()?.let { drawInnerShadow(it, resolvedShape) }
            highlight?.invoke()?.let { drawHighlight(it, resolvedShape) }
        }
}


