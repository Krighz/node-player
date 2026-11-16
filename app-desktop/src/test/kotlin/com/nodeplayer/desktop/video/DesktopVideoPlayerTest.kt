package com.nodeplayer.desktop.video

import org.junit.Assert.assertNotNull
import org.junit.Test

class DesktopVideoPlayerTest {

    @Test
    fun testVlcDiscoveryDoesNotThrow() {
        // Discovery must be completely safe and never throw an uncaught exception
        val isAvailable = DesktopVideoDiscovery.isVlcAvailable
        assertNotNull(isAvailable)
    }
}
