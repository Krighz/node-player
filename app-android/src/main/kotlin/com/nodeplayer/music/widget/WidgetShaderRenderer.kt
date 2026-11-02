package com.nodeplayer.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.palette.graphics.Palette
import kotlin.math.roundToInt

data class WidgetShaderConfig(
    val widthPx: Int,
    val heightPx: Int,
    val cornerRadiusPx: Float,
    val isPlaying: Boolean = true,
    val phase: Float = 0.0f,
    val glassOpacity: Float = 0.52f,
    val warmColor: FloatArray = floatArrayOf(0.95f, 0.98f, 1.0f),
    val coolColor: FloatArray = floatArrayOf(0.20f, 0.85f, 1.0f),
    val baseColor: FloatArray = floatArrayOf(0.04f, 0.05f, 0.07f),
    val isPerformanceMode: Boolean = false,
    val edgeHighlightStrength: Float = 0.85f,
    val refractionStyle: String = "LIQUID_OBSIDIAN"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WidgetShaderConfig) return false
        return widthPx == other.widthPx &&
                heightPx == other.heightPx &&
                cornerRadiusPx == other.cornerRadiusPx &&
                isPlaying == other.isPlaying &&
                phase == other.phase &&
                glassOpacity == other.glassOpacity &&
                isPerformanceMode == other.isPerformanceMode &&
                edgeHighlightStrength == other.edgeHighlightStrength &&
                refractionStyle == other.refractionStyle &&
                warmColor.contentEquals(other.warmColor) &&
                coolColor.contentEquals(other.coolColor) &&
                baseColor.contentEquals(other.baseColor)
    }

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + cornerRadiusPx.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + phase.hashCode()
        result = 31 * result + glassOpacity.hashCode()
        result = 31 * result + isPerformanceMode.hashCode()
        result = 31 * result + edgeHighlightStrength.hashCode()
        result = 31 * result + refractionStyle.hashCode()
        result = 31 * result + warmColor.contentHashCode()
        result = 31 * result + coolColor.contentHashCode()
        result = 31 * result + baseColor.contentHashCode()
        return result
    }
}

object WidgetShaderRenderer {

    private const val TAG = "WidgetShaderRenderer"

    // AGSL Shader source for Android 13+ (API 33+)
    private const val AGSL_LIQUID_GLASS = """
        uniform float2 u_resolution;
        uniform float u_phase;
        uniform float3 u_color_warm;
        uniform float3 u_color_cool;
        uniform float3 u_color_base;
        uniform float u_is_playing;
        uniform float u_corner_radius;
        uniform float u_glass_opacity;
        uniform float u_edge_highlight;

        // Hardened hash: modulo wrapped to eliminate high-coordinate GPU float overflow
        float hash(float2 p) {
            float2 safeP = p - 1024.0 * floor(p / 1024.0);
            float3 p3 = fract(float3(safeP.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            // Strict bounds check: discard if evaluated outside resolution
            if (fragCoord.x < 0.0 || fragCoord.x > u_resolution.x || fragCoord.y < 0.0 || fragCoord.y > u_resolution.y) {
                return half4(0.0, 0.0, 0.0, 0.0);
            }

            // 1. Direct pixel-space rounded rectangle SDF for exact edge anti-aliasing
            float2 halfSize = max(u_resolution * 0.5, float2(0.5, 0.5));
            float2 pPixel = fragCoord - halfSize;
            float dBox = sdRoundedBox(pPixel, halfSize, u_corner_radius);
            float cardMask = 1.0 - smoothstep(0.0, 1.5, dBox);
            if (cardMask <= 0.0) {
                return half4(0.0, 0.0, 0.0, 0.0);
            }

            // Normalise coordinates to 0..1 guarded by epsilon
            float2 safeRes = max(u_resolution, float2(1.0, 1.0));
            float2 uv = clamp(fragCoord / safeRes, 0.0, 1.0);
            float aspect = safeRes.x / (safeRes.y + 1e-5);
            float2 p = (uv - 0.5) * float2(aspect, 1.0);

            // 2. Fluid Metaball / Domain-Warped Shapes with safely wrapped phase
            float t = u_phase - 3600.0 * floor(u_phase / 3600.0);
            float2 warp = float2(
                noise(p * 2.2 + float2(t * 0.28, t * 0.18)),
                noise(p * 2.2 + float2(-t * 0.22, t * 0.32))
            );
            float2 fluidP = p + (warp - 0.5) * 0.55;

            float2 c1 = float2(sin(t * 0.35) * 0.25, cos(t * 0.45) * 0.15);
            float2 c2 = float2(cos(t * -0.30) * 0.30, sin(t * -0.38) * 0.20);

            float d1Sq = dot(fluidP - c1, fluidP - c1) + 0.09;
            float d2Sq = dot(fluidP - c2, fluidP - c2) + 0.09;

            float fluidField = 0.32 / (d1Sq + 1e-5) + 0.28 / (d2Sq + 1e-5);
            float fluidIso = smoothstep(1.5, 3.2, fluidField);

            // Safe normal derivation for specular rim lighting (guarded with epsilon)
            float eps = 0.02;
            float2 pR = fluidP + float2(eps, 0.0);
            float2 pU = fluidP + float2(0.0, eps);
            float fieldR = 0.32 / (dot(pR - c1, pR - c1) + 0.09 + 1e-5) + 0.28 / (dot(pR - c2, pR - c2) + 0.09 + 1e-5);
            float fieldU = 0.32 / (dot(pU - c1, pU - c1) + 0.09 + 1e-5) + 0.28 / (dot(pU - c2, pU - c2) + 0.09 + 1e-5);
            float2 grad = float2(fieldR - fluidField, fieldU - fluidField);
            float gradLen = length(grad);
            float2 norm = gradLen > 1e-5 ? grad / (gradLen + 1e-5) : float2(0.0, 1.0);

            float fluidEdge = smoothstep(0.18, 0.0, abs(fluidField - 2.2));

            // 3. Chromatic Rim Highlights (Amber/Warm on bottom-left, Cyan/Cool on top-right)
            float rimWarm = clamp(dot(norm, float2(-0.707, -0.707)), 0.0, 1.0) * fluidEdge;
            float rimCool = clamp(dot(norm, float2(0.707, 0.707)), 0.0, 1.0) * fluidEdge;

            // Inner card edge rim light (within 10 pixels of the boundary)
            float cardInnerRim = smoothstep(-10.0, 0.0, dBox) * (1.0 - smoothstep(0.0, 1.5, dBox));
            float cardWarmFringe = clamp(p.x * -1.5, 0.0, 1.0) * cardInnerRim;
            float cardCoolFringe = clamp(p.x * 1.5, 0.0, 1.0) * cardInnerRim;

            // 4. Liquid Glass Compositing (Obsidian Depth + Pearl Caustics + Crystal Specular Rim)
            float3 col = u_color_base * 0.45;
            col = mix(col, u_color_base * 0.92 + float3(0.025, 0.035, 0.045), fluidIso * 0.85);

            // Pearl caustics inside fluid field
            float pearlCaustics = smoothstep(1.9, 3.6, fluidField) * 0.35;
            col += float3(pearlCaustics, pearlCaustics, pearlCaustics);

            // Chromatic dispersion prism fringe along perimeter
            col += u_color_warm * (rimWarm * 0.80 + cardWarmFringe * 0.55) * u_edge_highlight;
            col += u_color_cool * (rimCool * 0.95 + cardCoolFringe * 0.70) * u_edge_highlight;

            // Razor-sharp anti-aliased outer crystal rim (1.5px boundary)
            float outerRim = smoothstep(1.5, 0.0, abs(dBox)) * u_edge_highlight;
            float angleLight = clamp(dot(norm, float2(-0.707, -0.707)), 0.0, 1.0);
            float rimLight = outerRim * (0.75 + 0.25 * angleLight);
            float3 rimColor = mix(float3(1.0, 1.0, 1.0), u_color_cool, 0.22) * rimLight * 1.5;
            col += rimColor;

            float maxRim = clamp(max(rimWarm, rimCool), 0.0, 1.0);
            float specularCore = pow(max(maxRim, 0.0), 3.0) * 0.90 * u_edge_highlight;
            col += float3(specularCore, specularCore, specularCore);

            float grain = (hash(fragCoord) - 0.5) * 0.025;
            col += float3(grain, grain, grain);

            if (u_is_playing < 0.8) {
                col *= 0.70;
            }

            // Output clamped colour and premultiplied alpha for translucent glass
            col = clamp(col, 0.0, 1.0);
            float alpha = clamp(cardMask * clamp(u_glass_opacity, 0.0, 1.0), 0.0, 1.0);

            return half4(col * alpha, alpha);
        }
    """

    // LRU cache for rendered bitmaps to prevent redundant GPU work (cache up to 8 widget variants)
    private val bitmapCache = LruCache<Int, Bitmap>(8)

    /**
     * Extracts chromatic warm, cool, and base colors from album artwork using Palette API.
     */
    fun extractColors(artwork: Bitmap?): Triple<FloatArray, FloatArray, FloatArray> {
        if (artwork == null) {
            return Triple(
                floatArrayOf(0.95f, 0.98f, 1.0f),  // Crystal pearl
                floatArrayOf(0.20f, 0.85f, 1.0f),  // Cyan specular
                floatArrayOf(0.04f, 0.05f, 0.07f)   // Deep obsidian
            )
        }

        return try {
            val palette = Palette.from(artwork).maximumColorCount(16).generate()
            val vibrant = palette.getVibrantColor(0xFFFFA000.toInt())
            val dominant = palette.getDominantColor(0xFF00E5FF.toInt())
            val darkMuted = palette.getDarkMutedColor(0xFF0D1117.toInt())

            fun toRgb(color: Int): FloatArray = floatArrayOf(
                ((color shr 16) and 0xFF) / 255f,
                ((color shr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f
            )

            Triple(toRgb(vibrant), toRgb(dominant), toRgb(darkMuted))
        } catch (e: Exception) {
            Log.w(TAG, "Palette generation failed, using fallback", e)
            Triple(
                floatArrayOf(1.0f, 0.65f, 0.12f),
                floatArrayOf(0.12f, 0.78f, 0.98f),
                floatArrayOf(0.06f, 0.07f, 0.09f)
            )
        }
    }

    /**
     * Renders the liquid glass background bitmap.
     * Uses AGSL + HardwareRenderer on Android 13+ (API 33+), with static frosted fallback on older devices or Performance Mode.
     */
    fun renderGlassBackground(config: WidgetShaderConfig): Bitmap {
        if (config.widthPx <= 0 || config.heightPx <= 0 ||
            config.widthPx > 4096 || config.heightPx > 4096 ||
            config.cornerRadiusPx.isNaN() || config.cornerRadiusPx < 0f ||
            config.phase.isNaN()
        ) {
            Log.w(TAG, "Invalid widget dimensions ${config.widthPx}x${config.heightPx}, using fallback")
            return renderStaticFallback(config)
        }

        val cacheKey = config.hashCode()
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val startTime = SystemClock.elapsedRealtime()

        val bitmap: Bitmap = if (config.isPerformanceMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            renderStaticFallback(config)
        } else {
            try {
                renderAgslHardware(config)
            } catch (e: Throwable) {
                Log.e(TAG, "HardwareRenderer AGSL failed, falling back to static", e)
                renderStaticFallback(config)
            }
        }

        val renderTimeMs = SystemClock.elapsedRealtime() - startTime
        Log.i(TAG, "Rendered widget glass bitmap [${config.widthPx}x${config.heightPx}] in ${renderTimeMs}ms (size: ${bitmap.byteCount / 1024} KB)")

        bitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun renderAgslHardware(config: WidgetShaderConfig): Bitmap {
        val width = config.widthPx
        val height = config.heightPx

        val handlerThread = android.os.HandlerThread("ShaderRenderThread").apply { start() }
        val handler = android.os.Handler(handlerThread.looper)
        val latch = java.util.concurrent.CountDownLatch(1)
        var acquiredImage: android.media.Image? = null

        val imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_CPU_READ_OFTEN
        )

        imageReader.setOnImageAvailableListener({ reader ->
            acquiredImage = reader.acquireLatestImage()
            latch.countDown()
        }, handler)

        val renderNode = RenderNode("WidgetShaderNode").apply {
            setPosition(0, 0, width, height)
        }

        val hardwareRenderer = android.graphics.HardwareRenderer().apply {
            setSurface(imageReader.surface)
            setContentRoot(renderNode)
        }

        try {
            val runtimeShader = android.graphics.RuntimeShader(AGSL_LIQUID_GLASS)
            runtimeShader.setFloatUniform("u_resolution", width.toFloat(), height.toFloat())
            val safePhase = config.phase % 3600f
            runtimeShader.setFloatUniform("u_phase", safePhase)
            runtimeShader.setFloatUniform("u_color_warm", config.warmColor[0], config.warmColor[1], config.warmColor[2])
            runtimeShader.setFloatUniform("u_color_cool", config.coolColor[0], config.coolColor[1], config.coolColor[2])
            runtimeShader.setFloatUniform("u_color_base", config.baseColor[0], config.baseColor[1], config.baseColor[2])
            runtimeShader.setFloatUniform("u_is_playing", if (config.isPlaying) 1.0f else 0.4f)
            runtimeShader.setFloatUniform("u_corner_radius", config.cornerRadiusPx)
            runtimeShader.setFloatUniform("u_glass_opacity", config.glassOpacity)
            runtimeShader.setFloatUniform("u_edge_highlight", config.edgeHighlightStrength)

            val paint = Paint().apply {
                shader = runtimeShader
                isAntiAlias = true
            }

            val canvas = renderNode.beginRecording(width, height)
            canvas.save()
            val clipPath = android.graphics.Path().apply {
                addRoundRect(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    config.cornerRadiusPx, config.cornerRadiusPx,
                    android.graphics.Path.Direction.CW
                )
            }
            canvas.clipPath(clipPath)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            canvas.restore()
            renderNode.endRecording()

            val syncResult = hardwareRenderer.createRenderRequest().syncAndDraw()
            Log.i(TAG, "HardwareRenderer syncResult: $syncResult")

            val success = latch.await(1200, java.util.concurrent.TimeUnit.MILLISECONDS)
            val image = acquiredImage
                ?: throw IllegalStateException("ImageReader wait timed out (success=$success, acquiredImage=null)")

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            Log.i(TAG, "Image info: size=${width}x${height}, pixelStride=$pixelStride, rowStride=$rowStride, remaining=${buffer.remaining()}")

            val rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (rowStride == width * 4 && pixelStride == 4) {
                buffer.rewind()
                rawBitmap.copyPixelsFromBuffer(buffer)
            } else {
                buffer.rewind()
                val rowBuffer = ByteArray(width * 4)
                val targetBuffer = java.nio.ByteBuffer.allocate(width * height * 4)
                for (row in 0 until height) {
                    buffer.position(row * rowStride)
                    buffer.get(rowBuffer, 0, width * 4)
                    targetBuffer.put(rowBuffer)
                }
                targetBuffer.rewind()
                rawBitmap.copyPixelsFromBuffer(targetBuffer)
            }
            image.close()

            return rawBitmap
        } finally {
            hardwareRenderer.destroy()
            renderNode.discardDisplayList()
            imageReader.close()
            handlerThread.quitSafely()
        }
    }

    /**
     * Clean static frosted canvas fallback for older Android versions or when Performance Mode is active.
     */
    private fun renderStaticFallback(config: WidgetShaderConfig): Bitmap {
        val bitmap = Bitmap.createBitmap(config.widthPx, config.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, config.widthPx.toFloat(), config.heightPx.toFloat())

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                (config.glassOpacity * 240).roundToInt().coerceIn(0, 255),
                (config.baseColor[0] * 255).roundToInt(),
                (config.baseColor[1] * 255).roundToInt(),
                (config.baseColor[2] * 255).roundToInt()
            )
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, config.cornerRadiusPx, config.cornerRadiusPx, bgPaint)

        // Subtle gradient rim
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, config.widthPx.toFloat(), config.heightPx.toFloat(),
                Color.argb(80, (config.warmColor[0] * 255).roundToInt(), (config.warmColor[1] * 255).roundToInt(), (config.warmColor[2] * 255).roundToInt()),
                Color.argb(80, (config.coolColor[0] * 255).roundToInt(), (config.coolColor[1] * 255).roundToInt(), (config.coolColor[2] * 255).roundToInt()),
                android.graphics.Shader.TileMode.CLAMP
            )
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(rect, config.cornerRadiusPx, config.cornerRadiusPx, borderPaint)

        return bitmap
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }
}
