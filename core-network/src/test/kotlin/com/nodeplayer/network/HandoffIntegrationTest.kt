package com.nodeplayer.network

import com.nodeplayer.model.Track
import com.nodeplayer.network.handoff.HandoffPayload
import com.nodeplayer.network.handoff.NodeConnectHandoffService
import com.nodeplayer.network.test.HeadlessTestPeer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class HandoffIntegrationTest {

    private var peerSender: HeadlessTestPeer? = null
    private var peerReceiver: HeadlessTestPeer? = null

    @After
    fun teardown() {
        peerSender?.stop()
        peerReceiver?.stop()
    }

    @Test
    fun testPlaybackHandoffWithPositionAndEq() = runBlocking {
        val handoffReceived = AtomicBoolean(false)
        var receivedData: HandoffPayload? = null
        val sleepTimerVal = AtomicLong(0L)

        val receiverHandoff = NodeConnectHandoffService(
            onHandoffReceived = { payload ->
                handoffReceived.set(true)
                receivedData = payload
                true // accept handoff
            },
            onSleepTimerUpdated = { remainingMs ->
                sleepTimerVal.set(remainingMs)
            }
        )
        val senderHandoff = NodeConnectHandoffService()

        val sender = HeadlessTestPeer("Sender-Phone", "Android")
        val receiver = HeadlessTestPeer("Receiver-PC", "Windows")
        peerSender = sender
        peerReceiver = receiver

        sender.trustPeer(receiver)
        receiver.trustPeer(sender)

        val port = receiver.startServer(0)
        val senderConn = sender.connectTo("127.0.0.1", port)
        delay(100)

        receiver.onFrameReceived = { frame, conn ->
            receiverHandoff.handleFrame(frame, conn)
        }
        sender.onFrameReceived = { frame, conn ->
            senderHandoff.handleFrame(frame, conn)
        }

        val currentTrack = Track(
            id = 42L,
            title = "There and Back",
            artist = "Protostar",
            album = "Genesis",
            durationMs = 240000L,
            path = "internal/music/there_and_back.flac",
            sampleRate = 44100,
            bitDepth = 16
        )

        // Sender initiates handoff mid-track at 1m 34s (94,000ms), with EQ preset "Bass Boost" and 15min sleep timer
        val success = senderHandoff.initiateHandoff(
            targetConnection = senderConn,
            track = currentTrack,
            positionMs = 94000L,
            isPlaying = true,
            currentQueueIndex = 3,
            eqPresetName = "Bass Boost",
            bassBoostDb = 6.0f,
            sleepTimerRemainingMs = 900_000L,
            timeoutMs = 3000L
        )

        assertTrue("Handoff should be acknowledged successfully", success)
        assertTrue("Receiver should have processed handoff", handoffReceived.get())
        assertNotNull("Payload must not be null", receivedData)

        assertEquals("There and Back", receivedData!!.trackTitle)
        assertEquals("Protostar", receivedData!!.artist)
        assertEquals(94000L, receivedData!!.positionMs)
        assertTrue(receivedData!!.isPlaying)
        assertEquals("Bass Boost", receivedData!!.eqPresetName)
        assertEquals(6.0f, receivedData!!.bassBoostDb, 0.001f)
        assertEquals(900_000L, sleepTimerVal.get())

        senderConn.close()
    }
}
