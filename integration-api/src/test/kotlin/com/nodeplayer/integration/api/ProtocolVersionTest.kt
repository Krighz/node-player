package com.nodeplayer.integration.api

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolVersionTest {

    @Test
    fun testProtocolCompatibility() {
        assertTrue(ProtocolVersion.isCompatible(1))
        assertFalse(ProtocolVersion.isCompatible(0))
        assertFalse(ProtocolVersion.isCompatible(2))
    }
}
