package com.nodeplayer.music.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

internal const val AGSL_REFRACTION_SRC = """
    uniform shader contents;
    uniform float2 size;
    uniform float refractionIndex;
    uniform float chromaticFringe;
    uniform float grainStrength;
    uniform float time;

    // Hardened hash: modulo guard eliminates high-input float precision degradation on mobile GPUs
    float hash(float2 p) {
        float2 safeP = p - 1024.0 * floor(p / 1024.0);
        float3 p3 = fract(float3(safeP.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    half4 main(float2 fragCoord) {
        // Strict boundary guard: discard any pixel outside this layer's real assigned size
        if (fragCoord.x < 0.0 || fragCoord.x > size.x || fragCoord.y < 0.0 || fragCoord.y > size.y) {
            return half4(0.0, 0.0, 0.0, 0.0);
        }

        // Normalize coordinates to 0..1 guarded by epsilon
        float2 safeSize = max(size, float2(1.0, 1.0));
        float2 uv = clamp(fragCoord / safeSize, 0.0, 1.0);
        float2 center = float2(0.5, 0.5);
        float2 delta = uv - center;
        float dist = length(delta);

        // Convex glass lens refraction distortion curve with safe bounded range
        float lensDistort = clamp((1.0 - dist * 0.45) * refractionIndex, -2.0, 2.0);
        float2 refractedUv = clamp(center + delta * (1.0 + lensDistort * 0.04), 0.0, 1.0);

        // Chromatic aberration fringe (dispersion at lens perimeter)
        float fringe = 0.012 * clamp(chromaticFringe, 0.0, 2.0);
        float2 uvR = clamp(center + delta * (1.0 + lensDistort * 0.04 + fringe), 0.0, 1.0);
        float2 uvG = refractedUv;
        float2 uvB = clamp(center + delta * (1.0 + lensDistort * 0.04 - fringe), 0.0, 1.0);

        // Sample contents safely clamped within assigned node bounds
        half4 cR = contents.eval(uvR * safeSize);
        half4 cG = contents.eval(uvG * safeSize);
        half4 cB = contents.eval(uvB * safeSize);

        float3 col = float3(cR.r, cG.g, cB.b);

        // Optional subtle film grain (safely wrapped time)
        if (grainStrength > 0.001) {
            float wrappedTime = time - 3600.0 * floor(time / 3600.0);
            float grain = (hash(fragCoord + float2(wrappedTime, wrappedTime * 1.618)) - 0.5) * 0.028 * grainStrength;
            col += float3(grain, grain, grain);
        }

        // Clamp final colour and return premultiplied alpha
        col = clamp(col, 0.0, 1.0);
        float alpha = clamp(cG.a, 0.0, 1.0);

        return half4(col * alpha, alpha);
    }
"""

/**
 * Hardened AGSL optical glass lens refraction modifier.
 * - Enforces layer clipping to prevent shader spilling beyond assigned bounds.
 * - Measures real node size with onSizeChanged and discards zero/NaN/huge sizes.
 * - Normalizes coordinates, guards divisions with epsilons, and outputs premultiplied alpha.
 */
fun Modifier.glassRefraction(
    shape: Shape = RoundedCornerShape(32.dp),
    enabled: Boolean = true,
    refractionIndex: Float = 0.85f,
    chromaticFringe: Float = 1.0f,
    grainStrength: Float = 0.0f
): Modifier = composed {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@composed this.clip(shape)
    }

    var measuredSize by remember { mutableStateOf(IntSize.Zero) }

    val shader = remember {
        try {
            RuntimeShader(AGSL_REFRACTION_SRC)
        } catch (_: Throwable) {
            null
        }
    }

    if (shader == null) {
        return@composed this.clip(shape)
    }

    this
        .clip(shape)
        .onSizeChanged { newSize ->
            measuredSize = newSize
        }
        .graphicsLayer {
            val w = if (measuredSize.width > 0) measuredSize.width.toFloat() else size.width
            val h = if (measuredSize.height > 0) measuredSize.height.toFloat() else size.height

            // Skip drawing when size is zero, NaN, or huge (> 4096 px)
            if (w <= 0f || h <= 0f || w.isNaN() || h.isNaN() || w > 4096f || h > 4096f) {
                renderEffect = null
                return@graphicsLayer
            }

            clip = true
            this.shape = shape

            try {
                shader.setFloatUniform("size", w, h)
                shader.setFloatUniform("refractionIndex", refractionIndex)
                shader.setFloatUniform("chromaticFringe", chromaticFringe)
                shader.setFloatUniform("grainStrength", grainStrength)
                val safeTime = (SystemClock.elapsedRealtime() % 3600000L).toFloat() / 1000f
                shader.setFloatUniform("time", safeTime)

                val effect = RenderEffect.createRuntimeShaderEffect(shader, "contents")
                renderEffect = effect.asComposeRenderEffect()
            } catch (_: Throwable) {
                renderEffect = null
            }
        }
}

/**
 * Backward compatibility alias for the dock glass refraction layer.
 */
fun Modifier.glassRefractionDock(
    enabled: Boolean = true,
    refractionIndex: Float = 0.85f,
    shape: Shape = RoundedCornerShape(32.dp)
): Modifier = glassRefraction(
    shape = shape,
    enabled = enabled,
    refractionIndex = refractionIndex
)
