package com.nodeplayer.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DockScrollControllerTest {

    @Test
    fun testScrollDownHidesDock() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, returnDelayMs = 2000L)
        assertEquals(0f, controller.offsetFraction.value)

        // Scroll down (negative delta)
        controller.onScroll(-100f)
        assertTrue(controller.offsetFraction.value > 0f)

        // Strong downward scroll
        controller.onScroll(-500f)
        assertEquals(1f, controller.offsetFraction.value)
    }

    @Test
    fun testScrollUpRestoresDock() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, returnDelayMs = 2000L)
        controller.hideDock()
        assertEquals(1f, controller.offsetFraction.value)

        // Scroll up (positive delta)
        controller.onScroll(100f)
        assertTrue(controller.offsetFraction.value < 1f)

        controller.restoreDock()
        assertEquals(0f, controller.offsetFraction.value)
    }

    @Test
    fun testTwoSecondTimeoutRestoresDock() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, returnDelayMs = 2000L)
        controller.hideDock()
        assertEquals(1f, controller.offsetFraction.value)

        // Trigger scroll event which starts timer
        controller.onScroll(-20f)
        advanceTimeBy(1000L)
        // Still hidden at 1s
        assertTrue(controller.offsetFraction.value > 0f)

        // After 2s timeout passes, dock should return to 0f
        advanceTimeBy(1100L)
        assertEquals(0f, controller.offsetFraction.value)
    }

    @Test
    fun testTopOfListRestoresDock() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, returnDelayMs = 2000L)
        controller.hideDock()
        assertEquals(1f, controller.offsetFraction.value)

        // Reaching non-top item should NOT restore dock
        controller.onListScrolledToTop(1, 10)
        assertEquals(1f, controller.offsetFraction.value)

        // Reaching exact top should immediately restore dock
        controller.onListScrolledToTop(0, 0)
        assertEquals(0f, controller.offsetFraction.value)
    }

    @Test
    fun testDisabledStateMaintainsZeroOffset() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, returnDelayMs = 2000L, enabled = false)
        assertEquals(0f, controller.offsetFraction.value)

        // Scrolling when disabled should not change offset
        controller.onScroll(-300f)
        assertEquals(0f, controller.offsetFraction.value)

        // Enabling allows scrolling
        controller.enabled = true
        controller.onScroll(-300f)
        assertTrue(controller.offsetFraction.value > 0f)

        // Disabling immediately restores dock
        controller.enabled = false
        assertEquals(0f, controller.offsetFraction.value)
    }
}
