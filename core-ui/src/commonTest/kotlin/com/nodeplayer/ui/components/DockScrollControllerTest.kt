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
        val controller = DockScrollController(this, sensitivity = 1.0f, returnDelayMs = 1000L)
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
        val controller = DockScrollController(this, sensitivity = 1.0f, returnDelayMs = 1000L)
        controller.hideDock()
        assertEquals(1f, controller.offsetFraction.value)

        // Scroll up (positive delta)
        controller.onScroll(100f)
        assertTrue(controller.offsetFraction.value < 1f)

        controller.restoreDock()
        assertEquals(0f, controller.offsetFraction.value)
    }

    @Test
    fun testAutoReturnTimerRestoresDock() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, returnDelayMs = 1000L)
        controller.hideDock()
        assertEquals(1f, controller.offsetFraction.value)

        // Trigger scroll event which starts timer
        controller.onScroll(-20f)
        advanceTimeBy(500L)
        // Still hidden midway
        assertTrue(controller.offsetFraction.value > 0f)

        // After timeout passes, dock should return to 0f
        advanceTimeBy(600L)
        assertEquals(0f, controller.offsetFraction.value)
    }
}
