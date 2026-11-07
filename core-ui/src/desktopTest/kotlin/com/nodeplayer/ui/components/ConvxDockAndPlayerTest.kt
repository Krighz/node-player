package com.nodeplayer.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvxDockAndPlayerTest {

    @Test
    fun testDefaultNavTabsCountAndRoutes() {
        val tabs = DEFAULT_CONVX_NAV_TABS
        assertEquals(4, tabs.size)
        assertEquals("home", tabs[0].route)
        assertEquals("library", tabs[1].route)
        assertEquals("equalizer", tabs[2].route)
        assertEquals("settings", tabs[3].route)
    }

    @Test
    fun testPuckOffsetMath() {
        val totalTabs = 4
        val tabWidthDp = 70.dp
        val selectedIndex = 2
        val expectedOffset = tabWidthDp * selectedIndex
        assertEquals(140.dp, expectedOffset)
    }

    @Test
    fun testDragTouchMapping() {
        val pillWidthPx = 400f
        val totalTabs = 4
        val tabWidthPx = pillWidthPx / totalTabs
        assertEquals(100f, tabWidthPx)

        val touchX1 = 50f
        val index1 = (touchX1 / tabWidthPx).toInt().coerceIn(0, totalTabs - 1)
        assertEquals(0, index1)

        val touchX2 = 250f
        val index2 = (touchX2 / tabWidthPx).toInt().coerceIn(0, totalTabs - 1)
        assertEquals(2, index2)

        val touchXOut = 500f
        val indexOut = (touchXOut / tabWidthPx).toInt().coerceIn(0, totalTabs - 1)
        assertEquals(3, indexOut)
    }
}
