package com.nodeplayer.music.diagnostics

import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.GLES20
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import java.util.Locale
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext

data class DeviceGraphicsInfo(
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val device: String = Build.DEVICE,
    val androidVersion: String = Build.VERSION.RELEASE,
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val socModel: String,
    val hardware: String = Build.HARDWARE,
    val gpuRenderer: String,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val densityScale: Float,
    val refreshRateHz: Float
) {
    fun toSanitizedPlainText(selfTestResult: ShaderSelfTestResult, shaderMode: String): String {
        return buildString {
            appendLine("=== Node Player Graphics Diagnostics ===")
            appendLine("Device: $manufacturer $model ($device)")
            appendLine("Android OS: $androidVersion (API $apiLevel)")
            appendLine("SoC: $socModel (Hardware: $hardware)")
            appendLine("GPU Renderer: $gpuRenderer")
            appendLine("Display: ${screenWidthPx}x${screenHeightPx} @ ${String.format(Locale.US, "%.1f", refreshRateHz)} Hz")
            appendLine("Density: ${densityDpi} dpi (${String.format(Locale.US, "%.1f", densityScale)}x)")
            appendLine("Self-Test Status: ${if (selfTestResult.passed) "PASSED" else "FAILED"}")
            appendLine("Self-Test Details: ${selfTestResult.reason}")
            appendLine("Current Shader Mode: $shaderMode")
            appendLine("Dock Shader Target: ${GlassShaderSelfTest.DOCK_WIDTH}x${GlassShaderSelfTest.DOCK_HEIGHT} px")
            appendLine("Test Canvas Buffer: ${GlassShaderSelfTest.BITMAP_WIDTH}x${GlassShaderSelfTest.BITMAP_HEIGHT} px")
            appendLine("=========================================")
        }
    }
}

object GpuInspector {

    private var cachedInfo: DeviceGraphicsInfo? = null

    fun getDeviceInfo(context: Context): DeviceGraphicsInfo {
        cachedInfo?.let { return it }

        val resMetrics = context.resources.displayMetrics
        var widthPx = resMetrics.widthPixels
        var heightPx = resMetrics.heightPixels
        var densityDpi = resMetrics.densityDpi
        var densityScale = resMetrics.density
        var refreshRate = 60f

        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

            val display: Display? = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    try {
                        context.display
                    } catch (_: Throwable) {
                        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
                    }
                }
                else -> {
                    @Suppress("DEPRECATION")
                    wm?.defaultDisplay ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
                }
            }

            if (display != null) {
                val realMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(realMetrics)
                widthPx = realMetrics.widthPixels
                heightPx = realMetrics.heightPixels
                densityDpi = realMetrics.densityDpi
                densityScale = realMetrics.density
                refreshRate = display.refreshRate
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
                val bounds = wm.maximumWindowMetrics.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    widthPx = bounds.width()
                    heightPx = bounds.height()
                }
            }
        } catch (_: Throwable) {
            // Safe fallback already populated from context.resources.displayMetrics
        }

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Build.SOC_MODEL.ifBlank { Build.HARDWARE }
            } catch (_: Throwable) {
                Build.HARDWARE
            }
        } else {
            Build.HARDWARE
        }

        val gpu = try {
            queryGpuFromEgl()
        } catch (_: Throwable) {
            "Unknown GPU"
        }

        val info = DeviceGraphicsInfo(
            socModel = soc,
            gpuRenderer = gpu,
            screenWidthPx = widthPx,
            screenHeightPx = heightPx,
            densityDpi = densityDpi,
            densityScale = densityScale,
            refreshRateHz = refreshRate
        )
        cachedInfo = info
        return info
    }

    /**
     * Creates a temporary headless 1x1 pbuffer EGL 1.0 context to query GL_RENDERER and GL_VENDOR,
     * ensuring proper teardown.
     */
    private fun queryGpuFromEgl(): String {
        return try {
            val egl = EGLContext.getEGL() as EGL10
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            if (display == EGL10.EGL_NO_DISPLAY) return "Unknown EGL Display"

            val version = IntArray(2)
            if (!egl.eglInitialize(display, version)) return "EGL Init Failed"

            val attribList = intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            egl.eglChooseConfig(display, attribList, configs, 1, numConfigs)
            val config = configs[0] ?: return "No EGL Config"

            val contextAttribs = intArrayOf(
                12440, 2, // EGL_CONTEXT_CLIENT_VERSION = 2
                EGL10.EGL_NONE
            )
            val context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, contextAttribs)
            val pbufferAttribs = intArrayOf(
                EGL10.EGL_WIDTH, 1,
                EGL10.EGL_HEIGHT, 1,
                EGL10.EGL_NONE
            )
            val surface = egl.eglCreatePbufferSurface(display, config, pbufferAttribs)

            egl.eglMakeCurrent(display, surface, surface, context)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown GPU"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""

            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
            egl.eglDestroySurface(display, surface)
            egl.eglDestroyContext(display, context)
            egl.eglTerminate(display)

            if (vendor.isNotBlank()) "$renderer ($vendor)" else renderer
        } catch (e: Throwable) {
            "GLES Query Error: ${e.message}"
        }
    }
}
