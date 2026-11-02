package com.nodeplayer.music.diagnostics

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import java.util.concurrent.CopyOnWriteArrayList

object FrameMetricsTracker {
    private var isRegistered = false
    private var handlerThread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null

    private val frameDurations = CopyOnWriteArrayList<Float>()
    var currentRefreshRate: Float = 120f
        private set

    fun start(activity: Activity) {
        if (isRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { activity.display } catch (_: Exception) { null }
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay
            }
            currentRefreshRate = display?.refreshRate ?: 120f

            val thread = HandlerThread("FrameMetricsThread").apply { start() }
            handlerThread = thread
            val handler = Handler(thread.looper)

            val frameListener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                val totalDurationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
                val totalDurationMs = totalDurationNs / 1_000_000f
                frameDurations.add(totalDurationMs)
                if (frameDurations.size > 1000) {
                    frameDurations.removeAt(0)
                }
            }
            listener = frameListener

            activity.window.addOnFrameMetricsAvailableListener(frameListener, handler)
            isRegistered = true
            GraphicsEventTracker.logEvent("FRAME_METRICS", "Started frame tracking at ${currentRefreshRate.toInt()}Hz")
        } catch (_: Exception) {}
    }

    fun stop(activity: Activity) {
        if (!isRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            listener?.let { activity.window.removeOnFrameMetricsAvailableListener(it) }
            handlerThread?.quitSafely()
        } catch (_: Exception) {}
        isRegistered = false
    }

    data class FrameStats(
        val sampleCount: Int,
        val refreshRateHz: Float,
        val medianMs: Float,
        val p90Ms: Float,
        val p95Ms: Float,
        val p99Ms: Float,
        val jankPercentage: Float
    )

    fun getStats(): FrameStats {
        val list = frameDurations.toList()
        if (list.isEmpty()) {
            return FrameStats(0, currentRefreshRate, 0f, 0f, 0f, 0f, 0f)
        }
        val sorted = list.sorted()
        val n = sorted.size
        val median = sorted[n / 2]
        val p90 = sorted[(n * 0.90f).toInt().coerceIn(0, n - 1)]
        val p95 = sorted[(n * 0.95f).toInt().coerceIn(0, n - 1)]
        val p99 = sorted[(n * 0.99f).toInt().coerceIn(0, n - 1)]
        val budgetMs = 1000f / currentRefreshRate.coerceAtLeast(30f)
        val jankCount = sorted.count { it > budgetMs }
        val jankPct = (jankCount.toFloat() / n.toFloat()) * 100f
        return FrameStats(n, currentRefreshRate, median, p90, p95, p99, jankPct)
    }
}
