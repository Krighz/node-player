package com.nodeplayer.common.logging

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val tag: String,
    val level: LogLevel,
    val message: String,
    val throwable: Throwable? = null
) {
    val feature: String
        get() = tag.substringBefore('/')

    fun format(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(Date(timestampMs))
        val base = "[$timeStr] [${level.name}] [$tag] $message"
        return if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$base\n$sw"
        } else {
            base
        }
    }
}

fun interface LogSink {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

object NodeLog {
    private val sinks = mutableListOf<LogSink>()
    private const val MAX_HISTORY = 1000
    private val history = ConcurrentLinkedDeque<LogEntry>()
    private var logFile: File? = null
    private var fileWriter: PrintWriter? = null
    var isVerbose: Boolean = true

    @Synchronized
    fun initFileLogging(file: File) {
        try {
            file.parentFile?.mkdirs()
            logFile = file
            fileWriter = PrintWriter(FileWriter(file, true), true)
            i("nodeconnect/logging", "Persistent file logging initialized at: ${file.absolutePath}")
        } catch (e: Exception) {
            e("nodeconnect/logging", "Failed to initialize persistent log file: ${e.message}", e)
        }
    }

    @Synchronized
    fun addSink(sink: LogSink) {
        if (!sinks.contains(sink)) {
            sinks.add(sink)
        }
    }

    @Synchronized
    fun removeSink(sink: LogSink) {
        sinks.remove(sink)
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.VERBOSE, tag, message, throwable)
    fun d(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.DEBUG, tag, message, throwable)
    fun i(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.INFO, tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (!isVerbose && level == LogLevel.VERBOSE) return

        val entry = LogEntry(tag = tag, level = level, message = message, throwable = throwable)
        history.addLast(entry)
        while (history.size > MAX_HISTORY) {
            history.pollFirst()
        }

        // Write to persistent file if configured
        fileWriter?.let { writer ->
            try {
                synchronized(this) {
                    writer.println(entry.format())
                }
            } catch (_: Throwable) {
            }
        }

        val currentSinks = synchronized(this) { sinks.toList() }
        for (sink in currentSinks) {
            try {
                sink.log(level, tag, message, throwable)
            } catch (_: Throwable) {
            }
        }
    }

    fun getHistory(): List<LogEntry> = history.toList()

    fun exportFormattedLog(): String {
        return history.joinToString("\n") { it.format() }
    }

    fun getEntriesByFeature(featurePrefix: String): List<LogEntry> {
        val prefix = featurePrefix.trimEnd('/')
        return history.filter { it.feature.equals(prefix, ignoreCase = true) || it.tag.startsWith("$prefix/", ignoreCase = true) }
    }

    fun getEntriesGroupedByFeature(): Map<String, List<LogEntry>> {
        return history.groupBy { it.feature }
    }

    fun clearHistory() {
        history.clear()
    }
}
