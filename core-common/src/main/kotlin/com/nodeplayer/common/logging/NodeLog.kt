package com.nodeplayer.common.logging

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
}

fun interface LogSink {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

object NodeLog {
    private val sinks = mutableListOf<LogSink>()
    private const val MAX_HISTORY = 500
    private val history = ConcurrentLinkedDeque<LogEntry>()

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
        val entry = LogEntry(tag = tag, level = level, message = message, throwable = throwable)
        history.addLast(entry)
        while (history.size > MAX_HISTORY) {
            history.pollFirst()
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
