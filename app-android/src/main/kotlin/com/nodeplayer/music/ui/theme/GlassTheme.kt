package com.nodeplayer.music.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassTokensData(
    val opacity: Float = 0.65f,
    val blurStrength: Float = 0.70f,
    val grainStrength: Float = 0.40f,
    val rimStrength: Float = 0.60f,
    val isFullyTransparent: Boolean = false,
    val keepTextReadable: Boolean = true,
    val isPerformanceMode: Boolean = false,
    val defaultCornerRadius: Dp = 26.dp,
    val cardCornerRadius: Dp = 22.dp,
    val pillCornerRadius: Dp = 999.dp,
    val borderWidth: Dp = 1.2.dp
) {
    val effectiveBlurRadius: Dp
        get() {
            if (isPerformanceMode || isFullyTransparent || blurStrength <= 0.001f) return 0.dp
            return (32f * blurStrength).dp
        }

    val effectiveTintAlpha: Float
        get() {
            if (isFullyTransparent) return 0.0f
            return opacity.coerceIn(0f, 1f)
        }

    fun liquidBorderBrush(isOled: Boolean = false): Brush {
        val rimScale = if (isFullyTransparent) 1.0f else rimStrength.coerceIn(0.1f, 1.0f)
        val alphaWhite = (0xCC * rimScale).toInt().coerceIn(0, 255)
        val alphaSoft = (0x44 * rimScale).toInt().coerceIn(0, 255)
        val alphaSide = (0x15 * rimScale).toInt().coerceIn(0, 255)

        return Brush.verticalGradient(
            colors = listOf(
                Color(alphaWhite shl 24 or 0x00FFFFFF),
                Color(alphaSoft shl 24 or 0x00FFFFFF),
                Color(alphaSide shl 24 or 0x00FFFFFF),
                Color(0x05000000)
            )
        )
    }

    fun oledBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x88FFFFFF),
            Color(0x22FFFFFF),
            Color.Transparent
        )
    )
}

val LocalGlassTokens = staticCompositionLocalOf { GlassTokensData() }

object GlassTokens {
    val defaultCornerRadius: Dp = 26.dp
    val cardCornerRadius: Dp = 22.dp
    val pillCornerRadius: Dp = 999.dp
    val defaultBlurRadius: Dp = 28.dp
    val borderWidth: Dp = 1.2.dp

    fun liquidBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xCCFFFFFF),
            Color(0x44FFFFFF),
            Color(0x15FFFFFF),
            Color(0x05000000)
        )
    )

    fun oledBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x88FFFFFF),
            Color(0x22FFFFFF),
            Color.Transparent
        )
    )
}

object GlassNoiseCache {
    private var cachedPaint: android.graphics.Paint? = null
    private var lastStrength: Float = -1f

    fun getPaint(strength: Float): android.graphics.Paint {
        val s = (strength * 100).toInt() / 100f
        if (cachedPaint != null && kotlin.math.abs(lastStrength - s) < 0.05f) {
            return cachedPaint!!
        }
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = kotlin.random.Random(1337)
        val maxAlpha = (28 * s).toInt().coerceIn(2, 65)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val noise = random.nextInt(maxAlpha.coerceAtLeast(3))
                bitmap.setPixel(x, y, android.graphics.Color.argb(noise, 255, 255, 255))
            }
        }
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            this.shader = shader
        }
        cachedPaint = paint
        lastStrength = s
        return paint
    }
}

@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(GlassTokens.cardCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    tintAlpha: Float? = null,
    customTint: Color? = null
): Modifier {
    val tokens = LocalGlassTokens.current
    val masterOpacity = tokens.effectiveTintAlpha
    val effectiveAlpha = when {
        tokens.isFullyTransparent -> 0.0f
        tintAlpha != null -> (tintAlpha * (masterOpacity / 0.65f).coerceIn(0.15f, 2.0f)).coerceIn(0.0f, 1.0f)
        else -> masterOpacity
    }

    val baseTint = when {
        tokens.isFullyTransparent -> Color.Transparent
        customTint != null -> customTint.copy(alpha = effectiveAlpha)
        isOled -> Color(0xFF000000).copy(alpha = 0.88f * effectiveAlpha)
        isDark -> Color(0xFF131520).copy(alpha = effectiveAlpha)
        else -> Color(0xFFEBF0FA).copy(alpha = effectiveAlpha)
    }

    val borderBrush = if (isOled) tokens.oledBorderBrush() else tokens.liquidBorderBrush(isOled)
    val blurRadius = tokens.effectiveBlurRadius
    val canBlur = false

    val scrimModifier = if (tokens.keepTextReadable && effectiveAlpha < 0.25f && !tokens.isFullyTransparent) {
        Modifier.background(Color.Black.copy(alpha = 0.12f), shape)
    } else {
        Modifier
    }

    val grainModifier = if (tokens.grainStrength > 0.01f && !tokens.isFullyTransparent && !tokens.isPerformanceMode) {
        Modifier.drawWithContent {
            drawContent()
            val paint = GlassNoiseCache.getPaint(tokens.grainStrength)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        }
    } else {
        Modifier
    }

    return this
        .clip(shape)
        .then(scrimModifier)
        .then(
            if (!tokens.isFullyTransparent && effectiveAlpha > 0.001f) {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            baseTint.copy(alpha = (effectiveAlpha * 1.1f).coerceAtMost(1f)),
                            baseTint.copy(alpha = (effectiveAlpha * 0.85f))
                        )
                    ),
                    shape
                )
            } else {
                Modifier
            }
        )
        .then(grainModifier)
        .border(
            width = tokens.borderWidth,
            brush = borderBrush,
            shape = shape
        )
}
