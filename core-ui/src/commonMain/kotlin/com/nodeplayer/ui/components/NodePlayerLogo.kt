package com.nodeplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Flagship Node Player Disc Logo matching Android ic_launcher.
 * Features liquid glass caustics, subtle audiophile vinyl micro-grooves,
 * and the iconic node play triangle.
 */
@Composable
fun NodePlayerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    accentColor: Color = LocalAccentColor.current
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = (w / 2f) * 0.92f

        // 1. Ambient Drop Shadow / Backing Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent),
                center = center,
                radius = radius * 1.15f
            ),
            radius = radius * 1.12f,
            center = center
        )

        // 2. Liquid Glass Primary Disc Body
        val discBrush = Brush.linearGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.85f),
                Color(
                    red = (accentColor.red * 0.82f),
                    green = (accentColor.green * 0.82f),
                    blue = (accentColor.blue * 0.85f),
                    alpha = 1.0f
                )
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        drawCircle(
            brush = discBrush,
            radius = radius,
            center = center
        )

        // 3. Audiophile Vinyl Micro-Grooves (Concentric delicate rings)
        val ringCount = 3
        for (i in 1..ringCount) {
            val r = radius * (0.40f + i * 0.16f)
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = r,
                center = center,
                style = Stroke(width = 1.dp.toPx() * 0.75f)
            )
        }

        // 4. Specular Top Rim Light (Fresnel Glass Edge)
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.75f),
                    Color.White.copy(alpha = 0.10f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.35f),
                    Color.White.copy(alpha = 0.75f)
                ),
                center = center
            ),
            startAngle = 180f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 5. Center Play Triangle matching Android M46,38 L68,54 L46,70 Z (normalized to disc)
        val playPath = Path().apply {
            val left = center.x - radius * 0.24f
            val right = center.x + radius * 0.38f
            val top = center.y - radius * 0.36f
            val bottom = center.y + radius * 0.36f

            moveTo(left, top)
            lineTo(right, center.y)
            lineTo(left, bottom)
            close()
        }

        // Triangle glyph color: dark cashmere ink (#131728) for high contrast on cream/beige/light accents
        val isLightAccent = (accentColor.red * 0.299f + accentColor.green * 0.587f + accentColor.blue * 0.114f) > 0.5f
        val glyphColor = if (isLightAccent) Color(0xFF131728) else Color.White

        drawPath(
            path = playPath,
            color = glyphColor
        )
    }
}
