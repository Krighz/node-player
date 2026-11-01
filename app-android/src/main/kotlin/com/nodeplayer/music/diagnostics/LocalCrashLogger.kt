package com.nodeplayer.music.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalCrashLogger {

    private const val TAG = "LocalCrashLogger"
    private const val LOG_FILE_NAME = "node_crash.log"
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record local crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val logEntry = buildString {
            appendLine("==================================================")
            appendLine("CRASH TIMESTAMP: $timeStr")
            appendLine("DEVICE: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("ANDROID OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("HEAP MEMORY: ${usedMemMb}MB / ${maxMemMb}MB max")
            appendLine("CRASHED THREAD: ${thread.name} (id=${thread.id})")
            appendLine("EXCEPTION: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("STACKTRACE:")
            appendLine(stackTrace)
            appendLine("==================================================")
            appendLine()
        }

        try {
            // Keep up to 50KB to avoid excessive file growth
            if (file.exists() && file.length() > 50 * 1024) {
                file.delete()
            }
            file.appendText(logEntry)
            Log.e(TAG, "Uncaught exception logged locally to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing crash log to disk", e)
        }
    }

    fun hasCrashLog(context: Context): Boolean {
        val file = File(context.filesDir, LOG_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun getCrashLogs(context: Context): String {
        val file = File(context.filesDir, LOG_FILE_NAME)
        return if (file.exists() && file.length() > 0) {
            try {
                file.readText()
            } catch (e: Exception) {
                "Error reading crash log: ${e.message}"
            }
        } else {
            "No crashes recorded. Node Player system is healthy."
        }
    }

    fun clearCrashLogs(context: Context): Boolean {
        val file = File(context.filesDir, LOG_FILE_NAME)
        return if (file.exists()) file.delete() else true
    }
}
