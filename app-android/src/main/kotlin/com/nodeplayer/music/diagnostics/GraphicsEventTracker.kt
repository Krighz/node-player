package com.nodeplayer.music.diagnostics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object GraphicsEventTracker {

    private const val TAG = "GraphicsEventTracker"
    private const val MAX_EVENTS = 200
    private const val FILE_NAME = "graphics_events.log"

    private val eventQueue = ConcurrentLinkedQueue<String>()
    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        appContext = context.applicationContext
        // Pre-populate from existing log file if available
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                val lines = file.readLines()
                val start = (lines.size - MAX_EVENTS).coerceAtLeast(0)
                for (i in start until lines.size) {
                    val line = lines[i].trim()
                    if (line.isNotEmpty()) {
                        eventQueue.add(line)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load previous graphics events", e)
        }
    }

    @Synchronized
    fun logEvent(category: String, details: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] [$category] $details"
        
        eventQueue.add(entry)
        while (eventQueue.size > MAX_EVENTS) {
            eventQueue.poll()
        }

        // Persist to ring buffer file on disk so it survives app crashes
        appContext?.let { ctx ->
            try {
                val file = File(ctx.filesDir, FILE_NAME)
                file.printWriter().use { writer ->
                    for (line in eventQueue) {
                        writer.println(line)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed persisting graphics event to disk", e)
            }
        }
    }

    fun getRecentEvents(): List<String> {
        return eventQueue.toList()
    }

    fun getLogFile(context: Context): File {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }

    fun clear(context: Context) {
        eventQueue.clear()
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
