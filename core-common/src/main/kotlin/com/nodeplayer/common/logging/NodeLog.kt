package com.nodeplayer.common.logging

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val timeStr = DATE_FORMAT.get()!!.format(Date(timestampMs))
        val base = "[$timeStr] [${level.name}] [$tag] $message"
        return if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$base\n$sw"
        } else {
            base
        }
    }

    private companion object {
        val DATE_FORMAT: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }
    }
}

fun interface LogSink {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

object NodeLog {
    private val sinks = mutableListOf<LogSink>()
    private const val MAX_HISTORY = 1000
    private const val FILE_QUEUE_CAPACITY = 2048
    private val history = ConcurrentLinkedDeque<LogEntry>()
    private var logFile: File? = null
    @Volatile
    private var fileWriter: PrintWriter? = null
    private val pendingFileWrites = ArrayBlockingQueue<LogEntry>(FILE_QUEUE_CAPACITY)
    @Volatile
    private var writerThread: Thread? = null
    @Volatile
    private var flushLatch: CountDownLatch? = null
    private val flushSentinel = LogEntry(tag = "__flush__", level = LogLevel.VERBOSE, message = "")
    var isVerbose: Boolean = true

    @Synchronized
    fun initFileLogging(file: File) {
        try {
            file.parentFile?.mkdirs()
            logFile = file
            try {
                fileWriter?.flush()
                fileWriter?.close()
            } catch (_: Throwable) {
            }
            fileWriter = PrintWriter(BufferedWriter(FileWriter(file, true)), false)
            ensureWriterThreadStarted()
            i("nodeconnect/logging", "Persistent file logging initialized at: ${file.absolutePath}")
        } catch (e: Exception) {
            e("nodeconnect/logging", "Failed to initialize persistent log file: ${e.message}", e)
        }
    }

    private fun ensureWriterThreadStarted() {
        if (writerThread != null) return
        val thread = Thread({
            val drained = ArrayList<LogEntry>(64)
            while (true) {
                try {
                    val first = pendingFileWrites.take()
                    drained.clear()
                    drained.add(first)
                    pendingFileWrites.drainTo(drained, 63)

                    var sawFlush = false
                    val writer = fileWriter
                    if (writer != null) {
                        for (i in 0 until drained.size) {
                            val item = drained[i]
                            if (item === flushSentinel) {
                                sawFlush = true
                            } else {
                                writer.println(item.format())
                            }
                        }
                        writer.flush()
                    } else {
                        for (i in 0 until drained.size) {
                            if (drained[i] === flushSentinel) sawFlush = true
                        }
                    }
                    if (sawFlush) {
                        flushLatch?.countDown()
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Throwable) {
                }
            }
        }, "NodeLog-FileWriter").apply {
            isDaemon = true
        }
        writerThread = thread
        thread.start()
    }

    /**
     * Waits up to [timeoutMs] for queued file log entries to be written and flushed off-thread.
     */
    fun flushFileLogging(timeoutMs: Long = 500L) {
        if (fileWriter == null || writerThread == null) return
        val latch = CountDownLatch(1)
        flushLatch = latch
        if (pendingFileWrites.offer(flushSentinel)) {
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
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

        // Enqueue non-blocking persistent file write off caller thread to avoid UI disk stalls
        if (fileWriter != null) {
            pendingFileWrites.offer(entry)
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
