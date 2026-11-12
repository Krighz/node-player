package com.nodeplayer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsViewConfigTest {

    @Test
    fun testDefaultLyricsViewConfigValues() {
        val config = LyricsViewConfig()
        assertEquals(28.0f, config.fontSizeSp)
        assertEquals(3, config.blurredLinesCount)
        assertEquals("GRADIENT_SWEEP", config.wordFillStyle)
        assertEquals(5, config.autoHideDelaySec)
        assertTrue(config.showPreviewLine)
        assertTrue(config.keepScreenOn)
    }

    @Test
    fun testCopyAndCustomValues() {
        val config = LyricsViewConfig(
            fontSizeSp = 40.0f,
            autoHideDelaySec = 0,
            showPreviewLine = false
        )
        assertEquals(40.0f, config.fontSizeSp)
        assertEquals(0, config.autoHideDelaySec)
        assertEquals(false, config.showPreviewLine)
    }
}
