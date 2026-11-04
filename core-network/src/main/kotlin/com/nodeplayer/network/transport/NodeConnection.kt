package com.nodeplayer.network.transport

import com.nodeplayer.network.protocol.NodeFraming
import com.nodeplayer.network.protocol.NodeMessageTypes
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Encapsulates an active mTLS connection between two Node Player devices.
 * Handles framed packet serialization, heartbeat ping/pong, and non-blocking coroutine streaming.
 */
class NodeConnection(
    val socket: Socket,
    val peerDeviceId: String,
    val peerFingerprint: String,
    private val scope: CoroutineScope
) {
    private val inputStream = BufferedInputStream(socket.getInputStream(), 64 * 1024)
    private val outputStream = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
    private val writeMutex = Mutex()
    private val isClosed = AtomicBoolean(false)
    private val nextSeq = AtomicLong(1L)

    val incomingFrames = Channel<NodeFraming.Frame>(capacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var lastPingLatencyMs: Long = 0L
        private set

    private var pingJob: Job? = null
    private var readJob: Job? = null

    fun start() {
        // 1. Continuous Read Loop
        readJob = scope.launch(Dispatchers.IO) {
            try {
                while (!isClosed.get() && isActive) {
                    val frame = try {
                        NodeFraming.readFrame(inputStream)
                    } catch (e: EOFException) {
                        break
                    } catch (e: Exception) {
                        if (!isClosed.get()) {
                            // Log or handle
                        }
                        break
                    }

                    // Handle Ping / Pong internally
                    if (frame.messageType == NodeMessageTypes.PING) {
                        val pong = NodeFraming.Frame(
                            messageType = NodeMessageTypes.PONG,
                            sequenceId = frame.sequenceId,
                            payload = frame.payload
                        )
                        sendFrame(pong)
                    } else if (frame.messageType == NodeMessageTypes.PONG) {
                        val sentTime = try {
                            String(frame.payload).toLongOrNull() ?: 0L
                        } catch (e: Exception) {
                            0L
                        }
                        if (sentTime > 0L) {
                            lastPingLatencyMs = (System.currentTimeMillis() - sentTime).coerceAtLeast(0L)
                        }
                    } else {
                        incomingFrames.send(frame)
                    }
                }
            } finally {
                close()
            }
        }

        // 2. Heartbeat Ping Job (every 15 seconds)
        pingJob = scope.launch(Dispatchers.IO) {
            while (!isClosed.get() && isActive) {
                delay(15_000L)
                val timeBytes = System.currentTimeMillis().toString().toByteArray()
                val pingFrame = NodeFraming.Frame(
                    messageType = NodeMessageTypes.PING,
                    sequenceId = nextSeq.getAndIncrement(),
                    payload = timeBytes
                )
                try {
                    sendFrame(pingFrame)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    suspend fun sendFrame(frame: NodeFraming.Frame) {
        if (isClosed.get()) throw IOException("NodeConnection is closed")
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                NodeFraming.writeFrame(frame, outputStream)
            }
        }
    }

    suspend fun sendPayload(messageType: Short, payload: ByteArray, flags: Byte = 0) {
        val frame = NodeFraming.Frame(
            messageType = messageType,
            flags = flags,
            sequenceId = nextSeq.getAndIncrement(),
            payload = payload
        )
        sendFrame(frame)
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            pingJob?.cancel()
            readJob?.cancel()
            incomingFrames.close()
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val isOpen: Boolean get() = !isClosed.get() && socket.isConnected && !socket.isClosed
}
