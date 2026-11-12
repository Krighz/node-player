package com.nodeplayer.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeProfileSheetTest {

    @Test
    fun testAvatarConstants() {
        assertEquals(56.dp, PROFILE_AVATAR_SIZE)
        assertEquals(1.5.dp, PROFILE_AVATAR_BORDER_WIDTH)
    }

    @Test
    fun testFormatChannelHandleWithAndWithoutAtPrefix() {
        assertEquals("@audiophile", formatChannelHandle("audiophile"))
        assertEquals("@audiophile", formatChannelHandle("@audiophile"))
        assertEquals("@johndoe", formatChannelHandle("  @johndoe  "))
        assertEquals("@johndoe", formatChannelHandle("  johndoe  "))
        assertNull(formatChannelHandle(null))
        assertNull(formatChannelHandle(""))
        assertNull(formatChannelHandle("   "))
    }

    @Test
    fun testResolveProfileSubtitlePriority() {
        // 1. Channel handle present -> shows formatted handle
        assertEquals(
            "@creator",
            resolveProfileSubtitle(
                channelHandle = "creator",
                accountEmail = "creator@gmail.com",
                accountName = "Content Creator"
            )
        )

        // 2. Channel handle null/blank, email present -> shows email
        assertEquals(
            "user@example.com",
            resolveProfileSubtitle(
                channelHandle = null,
                accountEmail = "user@example.com",
                accountName = "User Name"
            )
        )
        assertEquals(
            "user@example.com",
            resolveProfileSubtitle(
                channelHandle = "  ",
                accountEmail = "user@example.com",
                accountName = "User Name"
            )
        )

        // 3. Channel handle & email null/blank, name present -> shows "Signed in"
        assertEquals(
            "Signed in",
            resolveProfileSubtitle(
                channelHandle = null,
                accountEmail = null,
                accountName = "User Name"
            )
        )

        // 4. Logged out (name is null) -> shows "Sign in to YouTube Music"
        assertEquals(
            "Sign in to YouTube Music",
            resolveProfileSubtitle(
                channelHandle = null,
                accountEmail = null,
                accountName = null
            )
        )
    }
}
