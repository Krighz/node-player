package com.nodeplayer.music.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

private const val AGSL_REFRACTION_SRC = """
    uniform shader contents;
    uniform float2 size;
    uniform float refractionIndex;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        float2 center = float2(0.5, 0.5);
        float2 delta = uv - center;
        float dist = length(delta);

        // Convex glass lens refraction distortion curve
        float lensDistort = (1.0 - dist * 0.45) * refractionIndex;
        float2 refractedUv = center + delta * (1.0 + lensDistort * 0.05);

        // Subtle optical chromatic aberration (dispersion at lens perimeter)
        float2 uvR = center + delta * (1.0 + lensDistort * 0.055);
        float2 uvG = refractedUv;
        float2 uvB = center + delta * (1.0 + lensDistort * 0.045);

        half4 cR = contents.eval(uvR * size);
        half4 cG = contents.eval(uvG * size);
        half4 cB = contents.eval(uvB * size);

        return half4(cR.r, cG.g, cB.b, cG.a);
    }
"""

/**
 * Applies a real AGSL optical glass lens refraction shader to the dock on Android 13+ (API 33+).
 * When Performance Mode is active, the shader is disabled to measurably eliminate GPU render overhead.
 */
fun Modifier.glassRefractionDock(
    enabled: Boolean = true,
    refractionIndex: Float = 1.0f
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this
    }

    val shader = try {
        RuntimeShader(AGSL_REFRACTION_SRC)
    } catch (_: Exception) {
        return this
    }

    return this.graphicsLayer {
        if (size.width > 0f && size.height > 0f) {
            try {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("refractionIndex", refractionIndex)
                val effect = RenderEffect.createRuntimeShaderEffect(shader, "contents")
                renderEffect = effect.asComposeRenderEffect()
            } catch (_: Exception) {}
        }
    }
}
