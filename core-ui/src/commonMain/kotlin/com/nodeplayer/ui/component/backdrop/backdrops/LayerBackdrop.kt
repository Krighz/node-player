/*
 * Copyright 2025 Kyant0, Apache License 2.0
 */
package com.nodeplayer.ui.component.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.nodeplayer.ui.component.backdrop.Backdrop

@Stable
interface LayerBackdrop : Backdrop

@Composable
fun rememberLayerBackdrop(): LayerBackdrop {
    return remember { SimpleLayerBackdrop() }
}

fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier = this

@Immutable
private class SimpleLayerBackdrop : LayerBackdrop {
    override val isCoordinatesDependent: Boolean = false
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {}
}

@Composable
fun rememberCombinedBackdrop(vararg backdrops: Backdrop): Backdrop {
    return remember(backdrops) {
        object : Backdrop {
            override val isCoordinatesDependent: Boolean = backdrops.any { it.isCoordinatesDependent }
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?
            ) {
                backdrops.forEach { b ->
                    with(b) {
                        drawBackdrop(density, coordinates, layerBlock)
                    }
                }
            }
        }
    }
}


