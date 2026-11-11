package com.nodeplayer.common

import com.nodeplayer.common.logging.LogLevel
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.common.result.NodeResult
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeLogTest {

    @BeforeTest
    fun setup() {
        NodeLog.clearHistory()
    }

    @Test
    fun testFeatureTagGrouping() {
        NodeLog.d("widgets/render", "Rendering 4x2 widget")
        NodeLog.e("widgets/shader", "Shader compilation fallback")
        NodeLog.i("playback/engine", "Track transition complete")
        NodeLog.w("scrobble/lastfm", "Network offline queueing")

        val grouped = NodeLog.getEntriesGroupedByFeature()
        assertEquals(3, grouped.size)
        assertTrue(grouped.containsKey("widgets"))
        assertTrue(grouped.containsKey("playback"))
        assertTrue(grouped.containsKey("scrobble"))

        val widgetEntries = NodeLog.getEntriesByFeature("widgets")
        assertEquals(2, widgetEntries.size)
        assertEquals(LogLevel.DEBUG, widgetEntries[0].level)
        assertEquals(LogLevel.ERROR, widgetEntries[1].level)
    }

    @Test
    fun testNodeResultSuccessAndFailure() {
        val success = NodeResult.runCatching { 42 }
        assertTrue(success.isSuccess)
        assertEquals(42, success.getOrNull())

        val failure = NodeResult.runCatching { throw IllegalArgumentException("Test exception") }
        assertTrue(failure.isFailure)
        assertEquals("Test exception", (failure as NodeResult.Failure).message)
    }

    @Test
    fun testAsyncFileLoggingFlushesOffThread() {
        val tempFile = kotlin.io.path.createTempFile("nodelog-test-", ".log").toFile()
        tempFile.deleteOnExit()

        NodeLog.initFileLogging(tempFile)
        NodeLog.i("perf/logging", "Async log write entry")
        NodeLog.flushFileLogging(timeoutMs = 2000L)

        val content = tempFile.readText()
        assertTrue(content.contains("Async log write entry"), "Expected flushed log entry in $tempFile")
    }
}
