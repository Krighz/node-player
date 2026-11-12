package com.nodeplayer.ui.debug

import com.nodeplayer.common.logging.LogLevel
import com.nodeplayer.common.logging.NodeLog
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassDebugCharacterizationTest {

    @BeforeTest
    fun setUp() {
        NodeLog.clearHistory()
    }

    @AfterTest
    fun tearDown() {
        GlassDebug.isEnabled = false
        GlassDebug.resetForTest()
        NodeLog.clearHistory()
    }

    @Test
    fun testGlassDebugDefaultsToDisabled() {
        assertFalse(GlassDebug.isEnabled, "GlassDebug.isEnabled must default to false in production")
    }

    @Test
    fun testDisabledRecordAndLogThrottledPerformZeroMutations() {
        GlassDebug.isEnabled = false
        GlassDebug.resetForTest()
        NodeLog.clearHistory()

        GlassDebug.recordDrawDuration(1_500_000L)
        GlassDebug.recordUpdateDuration(2_500_000L)
        GlassDebug.logThrottled("TestKey", "Suppressed debug message", minIntervalMs = 0L)
        GlassDebug.log("TestKey", "Suppressed direct message")

        assertEquals(0, GlassDebug.drawSampleCountForTest())
        assertEquals(0, GlassDebug.updateSampleCountForTest())
        val entries = NodeLog.getHistory().filter { it.tag == "GlassDebug" }
        assertTrue(entries.isEmpty(), "Expected zero GlassDebug log entries when disabled, found: $entries")
    }

    @Test
    fun testLogErrorEmitsRegardlessOfIsEnabled() {
        GlassDebug.isEnabled = false
        NodeLog.clearHistory()

        GlassDebug.logError("ShaderCompile", "Shader compilation error")

        val errors = NodeLog.getHistory().filter {
            it.tag == "GlassDebug" && it.level == LogLevel.ERROR && it.message.contains("Shader compilation error")
        }
        assertEquals(1, errors.size, "logError must still emit when GlassDebug.isEnabled is false")
    }
}
