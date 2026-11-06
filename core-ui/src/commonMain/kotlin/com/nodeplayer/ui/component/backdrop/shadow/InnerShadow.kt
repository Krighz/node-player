/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.nodeplayer.ui.component.backdrop.shadow


import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.nodeplayer.ui.component.backdrop.internal.drawShapeOutline


@Immutable
data class InnerShadow(
    val radius: Dp = 24f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
     val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {

    companion object {

        @Stable
        val Default: InnerShadow = InnerShadow()
    }
}

@Stable
fun lerp(start: InnerShadow, stop: InnerShadow, fraction: Float): InnerShadow {
    return InnerShadow(
        radius = lerp(start.radius, stop.radius, fraction),
        offset = lerp(start.offset, stop.offset, fraction),
        color = lerp(start.color, stop.color, fraction),
        alpha = start.alpha + fraction * (stop.alpha - start.alpha),
        blendMode = if (fraction < 0.5f) start.blendMode else stop.blendMode
    )
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInnerShadow(
    innerShadow: InnerShadow,
    shape: androidx.compose.ui.graphics.Shape
) {
    if (innerShadow.alpha <= 0.001f || innerShadow.radius.value <= 0f) return
    val strokeWidth = (innerShadow.radius / 2f).toPx()
    drawShapeOutline(
        shape = shape,
        color = innerShadow.color.copy(alpha = innerShadow.color.alpha * innerShadow.alpha),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )
}



