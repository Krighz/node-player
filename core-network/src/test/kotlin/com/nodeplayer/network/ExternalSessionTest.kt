package com.nodeplayer.network

import com.nodeplayer.network.pairing.DevicePermissions
import com.nodeplayer.network.session.ExternalMediaSession
import com.nodeplayer.network.session.ExternalSessionService
import com.nodeplayer.network.session.PlatformMediaSessionBridge
import com.nodeplayer.network.test.HeadlessTestPeer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ExternalSessionTest {

    private var peerHost: HeadlessTestPeer? = null
    private var peerClient: HeadlessTestPeer? = null

    @After
    fun teardown() {
        peerHost?.stop()
        peerClient?.stop()
    }

    @Test
    fun testExternalSessionMetadataAndControl() = runBlocking {
        val lastCommandApp = AtomicReference<String>("")
        val lastCommandAction = AtomicReference<String>("")

        val mockBridge = object : PlatformMediaSessionBridge {
            override fun sendTransportCommand(appName: String, action: String) {
                lastCommandApp.set(appName)
                lastCommandAction.set(action)
            }
        }

        val host = HeadlessTestPeer("Host-PC", "Windows")
        val client = HeadlessTestPeer("Client-Phone", "Android")
        peerHost = host
        peerClient = client

        // Trust each other with canControlExternalSessions = true
        host.trustPeer(client, DevicePermissions(canControlExternalSessions = true))
        client.trustPeer(host, DevicePermissions(canControlExternalSessions = true))

        val hostService = ExternalSessionService(mockBridge, host.registry)
        val clientService = ExternalSessionService(null, client.registry)

        host.onFrameReceived = { frame, conn -> hostService.handleFrame(frame, conn) }
        client.onFrameReceived = { frame, conn -> clientService.handleFrame(frame, conn) }

        val port = host.startServer(0)
        val clientConn = client.connectTo("127.0.0.1", port)
        delay(100)

        // 1. Host broadcasts Spotify session
        val hostConn = host.activeConnections[0]
        val spotifySession = ExternalMediaSession(
            appName = "Spotify",
            trackTitle = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            isPlaying = true,
            positionMs = 45000L,
            durationMs = 200000L
        )
        hostService.broadcastLocalExternalSession(hostConn, spotifySession)
        delay(150)

        val clientSession = clientService.activeRemoteSession.value
        assertNotNull(clientSession)
        assertEquals("Spotify", clientSession!!.appName)
        assertEquals("Blinding Lights", clientSession.trackTitle)
        assertEquals("The Weeknd", clientSession.artist)
        assertTrue(clientSession.isPlaying)

        // 2. Client sends Next track command to Host
        clientService.sendRemoteSessionCommand(clientConn, "Spotify", "NEXT")
        delay(150)

        assertEquals("Spotify", lastCommandApp.get())
        assertEquals("NEXT", lastCommandAction.get())

        clientConn.close()
    }

    @Test
    fun testExternalSessionBlockedWhenPermissionDisabled() = runBlocking {
        val bridgeCalled = AtomicBoolean(false)
        val mockBridge = object : PlatformMediaSessionBridge {
            override fun sendTransportCommand(appName: String, action: String) {
                bridgeCalled.set(true)
            }
        }

        val host = HeadlessTestPeer("Host-PC", "Windows")
        val client = HeadlessTestPeer("Client-Phone", "Android")
        peerHost = host
        peerClient = client

        // canControlExternalSessions = false (default)
        host.trustPeer(client, DevicePermissions(canControlExternalSessions = false))
        client.trustPeer(host, DevicePermissions(canControlExternalSessions = false))

        val hostService = ExternalSessionService(mockBridge, host.registry)
        val clientService = ExternalSessionService(null, client.registry)

        host.onFrameReceived = { frame, conn -> hostService.handleFrame(frame, conn) }
        client.onFrameReceived = { frame, conn -> clientService.handleFrame(frame, conn) }

        val port = host.startServer(0)
        val clientConn = client.connectTo("127.0.0.1", port)
        delay(100)

        // Client attempts to send command
        clientService.sendRemoteSessionCommand(clientConn, "YouTube Music", "PAUSE")
        delay(150)

        assertFalse("Bridge must not be invoked when permission is false", bridgeCalled.get())
        clientConn.close()
    }
}
