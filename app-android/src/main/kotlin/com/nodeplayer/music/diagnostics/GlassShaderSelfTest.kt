package com.nodeplayer.music.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresApi
import com.nodeplayer.music.BuildConfig
import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ShaderSelfTestResult(
    val passed: Boolean,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

object GlassShaderSelfTest {

    private const val TAG = "GlassShaderSelfTest"
    private const val PREFS_NAME = "node_graphics_selftest"
    const val KEY_LAST_TESTED_VERSION = "last_tested_version"
    const val KEY_SELF_TEST_PASSED = "self_test_passed"
    const val KEY_SELF_TEST_REASON = "self_test_reason"
    const val KEY_FORCE_SELF_TEST_FAIL = "force_self_test_fail"

    const val DOCK_WIDTH = 360
    const val DOCK_HEIGHT = 80
    const val PADDING = 32
    const val BITMAP_WIDTH = DOCK_WIDTH + PADDING * 2 // 424 px
    const val BITMAP_HEIGHT = DOCK_HEIGHT + PADDING * 2 // 144 px

    /**
     * Checks if self-test needs to run (first launch, app version update, or force debug toggle),
     * executes it, persists the outcome, and activates safe fallback if failed.
     */
    fun checkAndRunIfNeeded(context: Context, forceReRun: Boolean = false): ShaderSelfTestResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTestedVersion = prefs.getInt(KEY_LAST_TESTED_VERSION, -1)
        val currentVersion = BuildConfig.VERSION_CODE
        val forceFail = prefs.getBoolean(KEY_FORCE_SELF_TEST_FAIL, false)

        val shouldRun = forceReRun ||
                lastTestedVersion != currentVersion ||
                !prefs.contains(KEY_SELF_TEST_PASSED) ||
                forceFail

        if (!shouldRun) {
            val passed = prefs.getBoolean(KEY_SELF_TEST_PASSED, true)
            val reason = prefs.getString(KEY_SELF_TEST_REASON, "Previously passed") ?: "Previously passed"
            return ShaderSelfTestResult(passed = passed, reason = reason)
        }

        val result = executeSelfTest(context, forceFail)

        prefs.edit()
            .putInt(KEY_LAST_TESTED_VERSION, currentVersion)
            .putBoolean(KEY_SELF_TEST_PASSED, result.passed)
            .putString(KEY_SELF_TEST_REASON, result.reason)
            .apply()

        GraphicsEventTracker.logEvent(
            "SELF_TEST",
            "Result=${if (result.passed) "PASSED" else "FAILED"}, Reason=${result.reason}"
        )

        if (!result.passed) {
            Log.w(TAG, "Shader self-test FAILED: ${result.reason}. Safe fallback (Haze blur + rim) activated.")
            DiagnosticsDumpManager.saveAutoDump(context, "Shader self-test failed: ${result.reason}")
        } else {
            Log.i(TAG, "Shader self-test PASSED: ${result.reason}")
        }

        return result
    }

    /**
     * Executes the offscreen render and pixel verification.
     */
    fun executeSelfTest(context: Context? = null, forceFail: Boolean = false): ShaderSelfTestResult {
        if (forceFail) {
            return ShaderSelfTestResult(
                passed = false,
                reason = "Forced failure via debug switch (testing safe fallback)"
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return ShaderSelfTestResult(
                passed = true,
                reason = "Pre-Android 13 (API ${Build.VERSION.SDK_INT}): AGSL not supported, safe fallback active"
            )
        }

        return try {
            renderAndValidateOffscreen()
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during shader offscreen self-test", t)
            ShaderSelfTestResult(
                passed = false,
                reason = "Offscreen rendering exception: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun renderAndValidateOffscreen(): ShaderSelfTestResult {
        val width = BITMAP_WIDTH
        val height = BITMAP_HEIGHT

        val handlerThread = HandlerThread("ShaderSelfTestThread").apply { start() }
        val handler = Handler(handlerThread.looper)
        val latch = CountDownLatch(1)
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

        val renderNode = RenderNode("ShaderSelfTestNode").apply {
            setPosition(0, 0, width, height)
        }

        val hardwareRenderer = android.graphics.HardwareRenderer().apply {
            setSurface(imageReader.surface)
            setContentRoot(renderNode)
        }

        try {
            // 1. Prepare dummy input content pattern to sample from
            val testPattern = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val testCanvas = android.graphics.Canvas(testPattern)
            val bgPaint = Paint().apply { color = Color.rgb(40, 50, 75) }
            testCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. Initialize AGSL shader with dock dimensions (360 x 80)
            val runtimeShader = android.graphics.RuntimeShader(RoundedRectRefractionShaderString)
            runtimeShader.setInputShader("content", BitmapShader(testPattern, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            runtimeShader.setFloatUniform("size", DOCK_WIDTH.toFloat(), DOCK_HEIGHT.toFloat())
            runtimeShader.setFloatUniform("offset", 0f, 0f)
            val cornerRadius = 26f
            runtimeShader.setFloatUniform("cornerRadii", cornerRadius, cornerRadius, cornerRadius, cornerRadius)
            runtimeShader.setFloatUniform("refractionHeight", 26f)
            runtimeShader.setFloatUniform("refractionAmount", 16f)
            runtimeShader.setFloatUniform("depthEffect", 0f)

            val paint = Paint().apply {
                shader = runtimeShader
                isAntiAlias = true
            }

            // Draw dock bounds (360 x 80) with rounded rect on canvas (424 x 144)
            val canvas = renderNode.beginRecording(width, height)
            canvas.drawRoundRect(0f, 0f, DOCK_WIDTH.toFloat(), DOCK_HEIGHT.toFloat(), cornerRadius, cornerRadius, paint)
            renderNode.endRecording()

            hardwareRenderer.createRenderRequest().syncAndDraw()

            val success = latch.await(1500, TimeUnit.MILLISECONDS)
            val image = acquiredImage
                ?: return ShaderSelfTestResult(
                    passed = false,
                    reason = "ImageReader wait timed out ($success)"
                )

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (rowStride == width * 4 && pixelStride == 4) {
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)
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
                bitmap.copyPixelsFromBuffer(targetBuffer)
            }
            image.close()

            // 3. Pixel verification
            // A. Check pixels outside dock bounds (x >= DOCK_WIDTH or y >= DOCK_HEIGHT)
            var spillCount = 0
            var firstSpillX = -1
            var firstSpillY = -1
            var firstSpillAlpha = -1

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val isOutside = x >= DOCK_WIDTH || y >= DOCK_HEIGHT
                    if (isOutside) {
                        val pixel = bitmap.getPixel(x, y)
                        val alpha = (pixel shr 24) and 0xFF
                        if (alpha > 0) {
                            spillCount++
                            if (firstSpillX == -1) {
                                firstSpillX = x
                                firstSpillY = y
                                firstSpillAlpha = alpha
                            }
                        }
                    }
                }
            }

            if (spillCount > 0) {
                return ShaderSelfTestResult(
                    passed = false,
                    reason = "Out-of-bounds spill detected: $spillCount spilled pixels (first at ($firstSpillX, $firstSpillY) with alpha=$firstSpillAlpha, expected 0)"
                )
            }

            // B. Check inside pixels for NaN/garbage and plausible brightness
            var totalLum = 0.0
            var validPixelCount = 0
            val step = 4 // sample every 4th pixel for performance

            for (y in 0 until DOCK_HEIGHT step step) {
                for (x in 0 until DOCK_WIDTH step step) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // Luminance in [0, 1]
                    val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                    totalLum += lum
                    validPixelCount++
                }
            }

            if (validPixelCount == 0) {
                return ShaderSelfTestResult(
                    passed = false,
                    reason = "No inside pixels evaluated"
                )
            }

            val avgLum = totalLum / validPixelCount
            if (avgLum < 0.01 || avgLum > 0.99) {
                return ShaderSelfTestResult(
                    passed = false,
                    reason = String.format(
                        Locale.US,
                        "Plausible brightness check failed: average luminance %.3f outside range [0.01..0.99]",
                        avgLum
                    )
                )
            }

            return ShaderSelfTestResult(
                passed = true,
                reason = String.format(
                    Locale.US,
                    "Passed: 0 spilled pixels outside bounds, valid color output, avg luminance %.2f",
                    avgLum
                )
            )

        } finally {
            hardwareRenderer.destroy()
            renderNode.discardDisplayList()
            imageReader.close()
            handlerThread.quitSafely()
        }
    }

    /**
     * Sets or clears the debug switch to force self-test failure.
     */
    fun setForceFail(context: Context, force: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FORCE_SELF_TEST_FAIL, force).apply()
        GraphicsEventTracker.logEvent("DEBUG_CONFIG", "ForceSelfTestFail set to $force")
        // Re-run immediately
        checkAndRunIfNeeded(context, forceReRun = true)
    }

    fun isForceFailEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FORCE_SELF_TEST_FAIL, false)
    }

    fun getLastResult(context: Context): ShaderSelfTestResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val passed = prefs.getBoolean(KEY_SELF_TEST_PASSED, true)
        val reason = prefs.getString(KEY_SELF_TEST_REASON, "Not executed yet") ?: "Not executed yet"
        return ShaderSelfTestResult(passed = passed, reason = reason)
    }
}
