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
import androidx.compose.ui.graphics.Color
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

internal const val AGSL_LENS_BUBBLE_SRC = """
    uniform shader contents;
    uniform float2 size;
    uniform float magnification;
    uniform float refractionIndex;
    uniform float chromaticFringe;
    uniform float rimStrength;
    uniform float4 rimColor;
    uniform float grainStrength;
    uniform float time;

    float hash(float2 p) {
        float2 safeP = p - 1024.0 * floor(p / 1024.0);
        float3 p3 = fract(float3(safeP.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    half4 main(float2 fragCoord) {
        if (fragCoord.x < 0.0 || fragCoord.x > size.x || fragCoord.y < 0.0 || fragCoord.y > size.y) {
            return half4(0.0, 0.0, 0.0, 0.0);
        }

        float2 safeSize = max(size, float2(1.0, 1.0));
        float2 uv = clamp(fragCoord / safeSize, 0.0, 1.0);
        float2 delta = uv - float2(0.5, 0.5);

        float aspect = safeSize.x / max(safeSize.y, 1.0);
        float2 radialDelta = float2(delta.x, delta.y * (1.0 / max(aspect * 0.5, 0.5)));
        float dist = length(radialDelta);
        float r = clamp(dist * 2.2, 0.0, 1.8);

        // 1. Center optical magnification (curved magnifying glass lens)
        float mag = 1.0 + magnification * clamp(1.0 - r * r, 0.0, 1.0);
        float2 magDelta = delta / max(mag, 0.01);

        // 2. Peripheral refractive displacement (bending towards center at curved edge)
        float edgeBend = smoothstep(0.40, 0.98, r) * 0.07 * refractionIndex;
        float2 displacedDelta = magDelta + delta * edgeBend;

        // 3. Chromatic dispersion prism split at perimeter
        float fringe = smoothstep(0.45, 0.96, r) * 0.014 * chromaticFringe;
        float2 uvR = clamp(float2(0.5, 0.5) + displacedDelta * (1.0 + fringe), 0.0, 1.0);
        float2 uvG = clamp(float2(0.5, 0.5) + displacedDelta, 0.0, 1.0);
        float2 uvB = clamp(float2(0.5, 0.5) + displacedDelta * (1.0 - fringe), 0.0, 1.0);

        half4 cR = contents.eval(uvR * safeSize);
        half4 cG = contents.eval(uvG * safeSize);
        half4 cB = contents.eval(uvB * safeSize);

        float3 col = float3(cR.r, cG.g, cB.b);

        // 4. Specular rim highlight (golden / warm highlight arcs)
        float rimMask = smoothstep(0.70, 0.92, r) * (1.0 - smoothstep(0.96, 1.02, r));
        float angleGlow = 0.6 + 0.4 * abs(delta.y) * 2.5;
        float3 specular = rimColor.rgb * (rimMask * angleGlow * rimStrength * 1.6);
        col += specular;

        // Subtle tactile grain
        if (grainStrength > 0.001) {
            float wrappedTime = time - 3600.0 * floor(time / 3600.0);
            float grain = (hash(fragCoord + float2(wrappedTime, wrappedTime * 1.618)) - 0.5) * 0.02 * grainStrength;
            col += float3(grain, grain, grain);
        }

        col = clamp(col, 0.0, 1.0);
        float alpha = clamp(cG.a, 0.0, 1.0);
        return half4(col * alpha, alpha);
    }
"""

/**
 * Optical Magnifying Lens Bubble modifier for the floating dock.
 * Magnifies the background with convex radial curvature, prismatic dispersion, and specular rim.
 */
fun Modifier.glassLensBubble(
    shape: Shape = RoundedCornerShape(26.dp),
    enabled: Boolean = true,
    magnification: Float = 0.25f,
    refractionIndex: Float = 0.70f,
    chromaticFringe: Float = 1.0f,
    rimStrength: Float = 0.85f,
    rimColor: Color = Color(0xFFFDE68A),
    grainStrength: Float = 0.0f
): Modifier = composed {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@composed this.clip(shape)
    }

    var measuredSize by remember { mutableStateOf(IntSize.Zero) }

    val shader = remember {
        try {
            RuntimeShader(AGSL_LENS_BUBBLE_SRC)
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

            if (w <= 0f || h <= 0f || w.isNaN() || h.isNaN() || w > 4096f || h > 4096f) {
                renderEffect = null
                return@graphicsLayer
            }

            clip = true
            this.shape = shape

            try {
                shader.setFloatUniform("size", w, h)
                shader.setFloatUniform("magnification", magnification)
                shader.setFloatUniform("refractionIndex", refractionIndex)
                shader.setFloatUniform("chromaticFringe", chromaticFringe)
                shader.setFloatUniform("rimStrength", rimStrength)
                shader.setFloatUniform("rimColor", rimColor.red, rimColor.green, rimColor.blue, rimColor.alpha)
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
