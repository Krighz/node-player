package com.nodeplayer.windows.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance liquid glass effect for Windows desktop.
 * Renders glass refraction gradient, inner specular highlights, and glowing rim borders.
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 1.dp,
    surfaceAlpha: Float = 0.08f,
    blurRadius: Dp = 20.dp
): Modifier = this.drawBehind {
    val radiusPx = cornerRadius.toPx()
    val borderPx = borderWidth.toPx()

    // 1. Refraction frosted glass backdrop
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2E3856).copy(alpha = surfaceAlpha * 1.5f),
                Color(0xFF131B2E).copy(alpha = surfaceAlpha * 2.5f)
            )
        ),
        cornerRadius = CornerRadius(radiusPx, radiusPx)
    )

    // 2. Specular top rim light highlight
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.05f)
            )
        ),
        start = Offset(radiusPx, 0f),
        end = Offset(size.width - radiusPx, 0f),
        strokeWidth = borderPx
    )

    // 3. Ambient glass border
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.04f)
            )
        ),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderPx)
    )
}
