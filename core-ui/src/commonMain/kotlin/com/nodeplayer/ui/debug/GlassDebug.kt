package com.nodeplayer.ui.debug

import com.nodeplayer.common.logging.NodeLog
import java.util.concurrent.ConcurrentHashMap

object GlassDebug {
    @Volatile
    var isEnabled: Boolean = false // Debug flag (disabled by default in production)

    private val lastLogTimes = ConcurrentHashMap<String, Long>()

    // Performance measurements: update and draw times in nanoseconds
    private val drawDurations = mutableListOf<Long>()
    private val updateDurations = mutableListOf<Long>()
    @Volatile
    private var lastPerfReportTime = 0L

    fun logThrottled(key: String, message: String, minIntervalMs: Long = 1000L) {
        if (!isEnabled) return
        val now = System.currentTimeMillis()
        val lastTime = lastLogTimes[key] ?: 0L

        if (now - lastTime >= minIntervalMs) {
            lastLogTimes[key] = now
            NodeLog.i("GlassDebug", "[$key] $message")
        }
    }

    fun log(key: String, message: String) {
        if (!isEnabled) return
        NodeLog.i("GlassDebug", "[$key] $message")
    }

    fun logError(key: String, message: String, throwable: Throwable? = null) {
        NodeLog.e("GlassDebug", "[$key] $message", throwable)
    }

    fun recordUpdateDuration(nanos: Long) {
        if (!isEnabled) return
        synchronized(updateDurations) {
            updateDurations.add(nanos)
            if (updateDurations.size > 200) updateDurations.removeAt(0)
        }
        checkAndReportPerf()
    }

    fun recordDrawDuration(nanos: Long) {
        if (!isEnabled) return
        synchronized(drawDurations) {
            drawDurations.add(nanos)
            if (drawDurations.size > 200) drawDurations.removeAt(0)
        }
        checkAndReportPerf()
    }

    private fun checkAndReportPerf() {
        val now = System.currentTimeMillis()
        if (now - lastPerfReportTime < 5000L) return
        lastPerfReportTime = now

        val updateList = synchronized(updateDurations) { updateDurations.toList() }
        val drawList = synchronized(drawDurations) { drawDurations.toList() }

        if (updateList.isNotEmpty()) {
            val sorted = updateList.sorted()
            val medianMs = sorted[sorted.size / 2] / 1_000_000f
            val maxMs = sorted.last() / 1_000_000f
            NodeLog.i("GlassDebug", "[Perf:UpdateEffects] Count=${sorted.size}, Median=${medianMs}ms, Max=${maxMs}ms")
        }
        if (drawList.isNotEmpty()) {
            val sorted = drawList.sorted()
            val medianMs = sorted[sorted.size / 2] / 1_000_000f
            val maxMs = sorted.last() / 1_000_000f
            NodeLog.i("GlassDebug", "[Perf:DrawBackdrop] Count=${sorted.size}, Median=${medianMs}ms, Max=${maxMs}ms")
        }
    }

    internal fun drawSampleCountForTest(): Int = synchronized(drawDurations) { drawDurations.size }

    internal fun updateSampleCountForTest(): Int = synchronized(updateDurations) { updateDurations.size }

    internal fun resetForTest() {
        synchronized(drawDurations) { drawDurations.clear() }
        synchronized(updateDurations) { updateDurations.clear() }
        lastLogTimes.clear()
        lastPerfReportTime = 0L
    }
}
