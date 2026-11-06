/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.nodeplayer.ui.component.backdrop.highlight


import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.component.backdrop.internal.drawShapeOutline

@Immutable
data class Highlight(
    val width: Dp = 0.5f.dp,
    val blurRadius: Dp = width / 2f,
     val alpha: Float = 1f,
    val style: HighlightStyle = HighlightStyle.Default
) {

    companion object {

        @Stable
        val Default: Highlight = Highlight()

        @Stable
        val Ambient: Highlight = Highlight(style = HighlightStyle.Ambient)

        @Stable
        val Plain: Highlight = Highlight(style = HighlightStyle.Plain)
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHighlight(
    highlight: Highlight,
    shape: androidx.compose.ui.graphics.Shape
) {
    if (highlight.alpha <= 0.001f || highlight.width.value <= 0f) return
    val strokeWidth = highlight.width.toPx()
    drawShapeOutline(
        shape = shape,
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f * highlight.alpha),
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f * highlight.alpha),
                androidx.compose.ui.graphics.Color.Transparent
            )
        ),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )
}



