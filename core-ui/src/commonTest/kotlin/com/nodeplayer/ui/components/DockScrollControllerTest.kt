package com.nodeplayer.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DockScrollControllerTest {

    @Test
    fun testScrollDownPast32dpCollapsesDock() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, collapseThresholdDp = 32f)
        assertEquals(0f, controller.collapseProgress.value)
        assertFalse(controller.isCollapsed.value)

        // Scroll down 20dp (under threshold) -> not collapsed
        controller.onScroll(-20f)
        assertFalse(controller.isCollapsed.value)
        assertEquals(0f, controller.collapseProgress.value)

        // Scroll down another 15dp (total 35dp >= 32dp) -> collapses
        controller.onScroll(-15f)
        assertTrue(controller.isCollapsed.value)
        assertEquals(1f, controller.collapseProgress.value)
    }

    @Test
    fun testScrollUpImmediatelyRestoresDock() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, collapseThresholdDp = 32f)
        controller.collapseDock()
        assertTrue(controller.isCollapsed.value)
        assertEquals(1f, controller.collapseProgress.value)

        // Scroll up (positive delta > 4f) -> immediately restores dock
        controller.onScroll(10f)
        assertFalse(controller.isCollapsed.value)
        assertEquals(0f, controller.collapseProgress.value)
    }

    @Test
    fun testExpandAndCollapseMethods() = runTest {
        val controller = DockScrollController(this)
        assertFalse(controller.isCollapsed.value)

        controller.collapse()
        assertTrue(controller.isCollapsed.value)
        assertEquals(1f, controller.collapseProgress.value)

        controller.expand()
        assertFalse(controller.isCollapsed.value)
        assertEquals(0f, controller.collapseProgress.value)

        controller.toggle()
        assertTrue(controller.isCollapsed.value)

        controller.toggle()
        assertFalse(controller.isCollapsed.value)
    }

    @Test
    fun testTopOfListRestoresDock() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f)
        controller.collapseDock()
        assertTrue(controller.isCollapsed.value)

        // Reaching non-top item should NOT restore dock
        controller.onListScrolledToTop(1, 10)
        assertTrue(controller.isCollapsed.value)

        // Reaching exact top should immediately restore dock
        controller.onListScrolledToTop(0, 0)
        assertFalse(controller.isCollapsed.value)
        assertEquals(0f, controller.collapseProgress.value)
    }

    @Test
    fun testDisabledStateMaintainsZeroOffset() = runTest {
        val controller = DockScrollController(this, sensitivity = 1.0f, enabled = false)
        assertFalse(controller.isCollapsed.value)

        // Scrolling when disabled should not collapse
        controller.onScroll(-100f)
        assertFalse(controller.isCollapsed.value)

        // Enabling allows collapsing
        controller.enabled = true
        controller.onScroll(-100f)
        assertTrue(controller.isCollapsed.value)

        // Disabling immediately restores dock
        controller.enabled = false
        assertFalse(controller.isCollapsed.value)
        assertEquals(0f, controller.collapseProgress.value)
    }
}
