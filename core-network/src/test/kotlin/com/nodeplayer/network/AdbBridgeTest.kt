package com.nodeplayer.network

import com.nodeplayer.network.discovery.AdbBridge
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AdbBridgeTest {

    @Test
    fun testDefaultPorts() {
        assertEquals(54825, AdbBridge.DEFAULT_FORWARD_PORT)
        assertEquals(54826, AdbBridge.DEFAULT_REVERSE_PORT)
        assertEquals(54820, AdbBridge.DEFAULT_NODE_PORT)
    }

    @Test
    fun testFindAdbPathDoesNotThrow() {
        val path = AdbBridge.findAdbPath()
        assertNotNull(path)
    }
}
