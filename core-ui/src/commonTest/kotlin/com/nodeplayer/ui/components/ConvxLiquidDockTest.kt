package com.nodeplayer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvxLiquidDockTest {

    @Test
    fun testDefaultTabsStructure() {
        assertEquals(4, DEFAULT_CONVX_NAV_TABS.size, "Dock must contain exactly 4 core tabs")

        assertEquals("home", DEFAULT_CONVX_NAV_TABS[0].route)
        assertEquals("Home", DEFAULT_CONVX_NAV_TABS[0].title)

        assertEquals("explore", DEFAULT_CONVX_NAV_TABS[1].route)
        assertEquals("Explore", DEFAULT_CONVX_NAV_TABS[1].title)

        assertEquals("library", DEFAULT_CONVX_NAV_TABS[2].route)
        assertEquals("Library", DEFAULT_CONVX_NAV_TABS[2].title)

        assertEquals("search", DEFAULT_CONVX_NAV_TABS[3].route)
        assertEquals("Search", DEFAULT_CONVX_NAV_TABS[3].title)
    }

    @Test
    fun testTabIndicesAndPuckOffsetCalculation() {
        val tabWidthDp = 80f
        val selectedIndexHome = DEFAULT_CONVX_NAV_TABS.indexOfFirst { it.route == "home" }
        assertEquals(0, selectedIndexHome)
        assertEquals(0f, tabWidthDp * selectedIndexHome)

        val selectedIndexExplore = DEFAULT_CONVX_NAV_TABS.indexOfFirst { it.route == "explore" }
        assertEquals(1, selectedIndexExplore)
        assertEquals(80f, tabWidthDp * selectedIndexExplore)

        val selectedIndexLibrary = DEFAULT_CONVX_NAV_TABS.indexOfFirst { it.route == "library" }
        assertEquals(2, selectedIndexLibrary)
        assertEquals(160f, tabWidthDp * selectedIndexLibrary)

        val selectedIndexSearch = DEFAULT_CONVX_NAV_TABS.indexOfFirst { it.route == "search" }
        assertEquals(3, selectedIndexSearch)
        assertEquals(240f, tabWidthDp * selectedIndexSearch)
    }

    @Test
    fun testLiquidDockTabsDestinationMapping() {
        assertEquals(3, LIQUID_DOCK_TABS.size)
        assertEquals("Home", LIQUID_DOCK_TABS[0].title)
        assertEquals("New", LIQUID_DOCK_TABS[1].title)
        assertEquals("Library", LIQUID_DOCK_TABS[2].title)
    }
}
