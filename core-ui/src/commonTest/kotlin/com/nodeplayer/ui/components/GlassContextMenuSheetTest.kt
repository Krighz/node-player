package com.nodeplayer.ui.components

import com.nodeplayer.model.ContextMenuConfig
import com.nodeplayer.model.TrackActionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassContextMenuSheetTest {

    @Test
    fun testDefaultContextMenuConfigHasAll11Actions() {
        val config = ContextMenuConfig.Defaults
        val active = config.getActiveTrackActions()

        assertEquals(11, active.size)
        assertEquals(TrackActionId.PLAY_NEXT, active[0])
        assertEquals(TrackActionId.ADD_TO_QUEUE, active[1])
        assertEquals(TrackActionId.ADD_TO_PLAYLIST, active[2])
        assertEquals(TrackActionId.LIKE_SAVE, active[3])
        assertEquals(TrackActionId.NOT_INTERESTED, active.last())
    }

    @Test
    fun testDisabledActionFiltering() {
        val config = ContextMenuConfig(
            disabledActionIds = setOf("NOT_INTERESTED", "SHARE")
        )
        val active = config.getActiveTrackActions()

        assertEquals(9, active.size)
        assertTrue(active.none { it == TrackActionId.NOT_INTERESTED })
        assertTrue(active.none { it == TrackActionId.SHARE })
        assertTrue(active.contains(TrackActionId.PLAY_NEXT))
    }

    @Test
    fun testCustomActionOrdering() {
        val customOrder = listOf(
            "LIKE_SAVE",
            "VIEW_LYRICS",
            "PLAY_NEXT"
        )
        val config = ContextMenuConfig(orderedTrackActionIds = customOrder)
        val active = config.getActiveTrackActions()

        assertEquals(3, active.size)
        assertEquals(TrackActionId.LIKE_SAVE, active[0])
        assertEquals(TrackActionId.VIEW_LYRICS, active[1])
        assertEquals(TrackActionId.PLAY_NEXT, active[2])
    }
}
