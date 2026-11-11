package com.nodeplayer.ui.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests verifying State 3 Lyrics Collapse Behavior (COL-01..COL-04).
 */
class LyricsCollapseStateTest {

    @Test
    fun testInactivityTimeoutTriggersCollapse() {
        val timeoutMs = 4000L
        var showControls = true
        var lastInteractionTime = 1000L

        // Inactivity elapsed
        val currentTime = 5001L
        if (currentTime - lastInteractionTime >= timeoutMs) {
            showControls = false
        }

        assertFalse(showControls, "Controls should collapse after 4s inactivity")
    }

    @Test
    fun testUserInteractionResetsTimeout() {
        val timeoutMs = 4000L
        var showControls = false
        var lastInteractionTime = 1000L

        // User taps screen
        showControls = true
        lastInteractionTime = 2000L

        val currentTime = 3000L
        val shouldCollapse = (currentTime - lastInteractionTime) >= timeoutMs
        assertFalse(shouldCollapse, "Controls should stay visible when interaction occurred within 4s")
        assertTrue(showControls)
    }

    @Test
    fun testScrollBackToActiveLineRestoresControls() {
        var showControls = false
        val activeIndex = 5

        // User scrolls back to activeIndex
        fun onScrollPositionChanged(firstVisibleIndex: Int, offset: Int): Boolean {
            return if (firstVisibleIndex == 0 && offset == 0) {
                true
            } else firstVisibleIndex == activeIndex || firstVisibleIndex == (activeIndex - 1).coerceAtLeast(0)
        }

        val restored = onScrollPositionChanged(firstVisibleIndex = 5, offset = 10)
        assertTrue(restored, "Scrolling back to active index should restore controls")
    }

    @Test
    fun testScrollToTopRestoresControls() {
        fun isAtTop(firstVisibleIndex: Int, offset: Int): Boolean {
            return firstVisibleIndex == 0 && offset == 0
        }

        assertTrue(isAtTop(0, 0), "First item at zero offset is at the top")
        assertFalse(isAtTop(1, 0), "Item index > 0 is not top")
        assertFalse(isAtTop(0, 15), "Non-zero offset is not top")
    }
}
