/*
 * Copyright 2025 Kyant0, Apache License 2.0
 */
package com.nodeplayer.ui.component.backdrop.internal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill

fun Canvas.clipOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)
        is Outline.Rounded -> {
            path!!.rewind()
            path.addRoundRect(outline.roundRect)
            clipPath(path)
        }
        is Outline.Generic -> clipPath(outline.path)
    }
}

fun DrawScope.drawShapeOutline(
    shape: Shape,
    brush: Brush,
    style: DrawStyle = Fill
) {
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> drawRect(brush = brush, topLeft = outline.rect.topLeft, size = outline.rect.size, style = style)
        is Outline.Rounded -> {
            val rr = outline.roundRect
            drawRoundRect(
                brush = brush,
                topLeft = Offset(rr.left, rr.top),
                size = Size(rr.width, rr.height),
                cornerRadius = CornerRadius(rr.bottomLeftCornerRadius.x, rr.bottomLeftCornerRadius.y),
                style = style
            )
        }
        is Outline.Generic -> drawPath(path = outline.path, brush = brush, style = style)
    }
}

fun DrawScope.drawShapeOutline(
    shape: Shape,
    color: Color,
    style: DrawStyle = Fill
) {
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> drawRect(color = color, topLeft = outline.rect.topLeft, size = outline.rect.size, style = style)
        is Outline.Rounded -> {
            val rr = outline.roundRect
            drawRoundRect(
                color = color,
                topLeft = Offset(rr.left, rr.top),
                size = Size(rr.width, rr.height),
                cornerRadius = CornerRadius(rr.bottomLeftCornerRadius.x, rr.bottomLeftCornerRadius.y),
                style = style
            )
        }
        is Outline.Generic -> drawPath(path = outline.path, color = color, style = style)
    }
}

