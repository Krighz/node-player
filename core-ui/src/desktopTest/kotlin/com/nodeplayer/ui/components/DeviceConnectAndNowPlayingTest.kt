package com.nodeplayer.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceConnectAndNowPlayingTest {

    @Test
    fun testDeviceConnectStatusEnumeration() {
        assertEquals(3, DeviceConnectStatus.values().size)
        assertTrue(DeviceConnectStatus.values().contains(DeviceConnectStatus.DISCONNECTED))
        assertTrue(DeviceConnectStatus.values().contains(DeviceConnectStatus.DISCOVERING))
        assertTrue(DeviceConnectStatus.values().contains(DeviceConnectStatus.CONNECTED))
    }

    @Test
    fun testDiscoveredPeerInfoDataModel() {
        val peer = DiscoveredPeerInfo(
            id = "android_pixel_8",
            name = "Pixel 8 Pro",
            platform = "Android",
            isConnected = true,
            canReceiveCast = true
        )
        assertEquals("android_pixel_8", peer.id)
        assertEquals("Pixel 8 Pro", peer.name)
        assertTrue(peer.isConnected)
        assertTrue(peer.canReceiveCast)
    }

    @Test
    fun testContinuousSquircleShapeCreation() {
        val density = Density(1f)
        val size = Size(200f, 100f)

        val squircle19 = ContinuousRoundedRectangle(19.dp)
        assertEquals(19f, squircle19.topStart.toPx(size, density))

        val squircle24 = ContinuousRoundedRectangle(24.dp)
        assertEquals(24f, squircle24.topStart.toPx(size, density))

        val outline = squircle24.createOutline(size, LayoutDirection.Ltr, density)
        assertTrue(outline.bounds.width > 0f)
    }

    @Test
    fun testNowPlayingProgressCalculations() {
        val positionMs = 45000L
        val durationMs = 180000L
        val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.25f, progress, 0.001f)

        val totalSec = positionMs / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        val formatted = "%d:%02d".format(m, s)
        assertEquals("0:45", formatted)
    }
}
