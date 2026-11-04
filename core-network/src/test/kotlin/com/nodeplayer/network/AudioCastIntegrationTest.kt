package com.nodeplayer.network

import com.nodeplayer.model.Track
import com.nodeplayer.network.cast.JitterBuffer
import com.nodeplayer.network.cast.NodeConnectCastService
import com.nodeplayer.network.test.HeadlessTestPeer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AudioCastIntegrationTest {

    private var peerSender: HeadlessTestPeer? = null
    private var peerReceiver: HeadlessTestPeer? = null

    @After
    fun teardown() {
        peerSender?.stop()
        peerReceiver?.stop()
    }

    @Test
    fun testCastStartAndPcmAudioStreaming() = runBlocking {
        val streamStarted = AtomicBoolean(false)
        var receiverJitter: JitterBuffer? = null

        val receiverService = NodeConnectCastService(
            onStreamStartedLocally = { session, jitter ->
                streamStarted.set(true)
                receiverJitter = jitter
            }
        )
        val senderService = NodeConnectCastService()

        val sender = HeadlessTestPeer("Sender-PC", "Windows")
        val receiver = HeadlessTestPeer("Receiver-Phone", "Android")
        peerSender = sender
        peerReceiver = receiver

        sender.trustPeer(receiver)
        receiver.trustPeer(sender)

        val port = receiver.startServer(0)
        val senderConn = sender.connectTo("127.0.0.1", port)
        delay(100)

        // Setup frame dispatcher on receiver
        receiver.onFrameReceived = { frame, conn ->
            receiverService.handleFrame(frame, conn)
        }

        val testTrack = Track(
            id = 555L,
            title = "Aishite Aishite Aishite",
            artist = "Ado",
            album = "Kyogen",
            durationMs = 255000L,
            path = "C:\\Music\\ado_aishite.flac",
            sampleRate = 48000,
            bitDepth = 24,
            channels = 2,
            isLossless = true
        )

        // 1. Sender starts cast
        val streamId = senderService.startCastAsSender(senderConn, testTrack)
        assertNotNull(streamId)

        delay(150)
        assertTrue("Receiver should have started stream", streamStarted.get())
        assertNotNull("Receiver should have initialized JitterBuffer", receiverJitter)
        assertEquals(48000, receiverJitter!!.sampleRate)
        assertEquals(24, receiverJitter!!.bitDepth)

        // 2. Sender streams PCM audio chunks (e.g. 50 chunks of 1920 bytes each = ~20ms each)
        val testChunk = ByteArray(1920) { (it % 120).toByte() }
        for (i in 0 until 50) {
            senderService.sendPcmAudioChunk(testChunk, ptsMs = i * 20L)
        }

        delay(200)
        assertTrue("Receiver jitter buffer should have buffered audio", receiverJitter!!.bufferedMs > 0L)

        // 3. Receiver reads audio from jitter buffer directly into hardware sink buffer
        val sinkBuffer = ByteArray(1920)
        val bytesRead = receiverJitter!!.read(sinkBuffer, 0, 1920)
        assertEquals(1920, bytesRead)
        assertArrayEquals(testChunk, sinkBuffer)

        // 4. Test Seek / Flush: sender seeks and flushes stream
        senderService.flushSenderStream(newPtsMs = 60000L)
        delay(100)
        assertEquals("Buffer should be empty immediately after flush", 0L, receiverJitter!!.bufferedMs)

        // Sender sends post-seek audio chunk
        val postSeekChunk = ByteArray(1920) { 0x7A }
        senderService.sendPcmAudioChunk(postSeekChunk, ptsMs = 60000L)
        delay(100)

        val postSeekRead = ByteArray(1920)
        val postBytesRead = receiverJitter!!.read(postSeekRead, 0, 1920)
        assertEquals(1920, postBytesRead)
        assertArrayEquals(postSeekChunk, postSeekRead)

        // 5. Sender stops cast
        senderService.stopCastAsSender()
        delay(100)
        assertFalse(senderService.isCasting)

        senderConn.close()
    }
}
