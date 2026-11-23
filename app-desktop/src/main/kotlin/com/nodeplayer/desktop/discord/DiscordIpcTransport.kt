package com.nodeplayer.desktop.discord

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.presence.DiscordIpcConstants
import com.nodeplayer.model.presence.PresenceTransport
import com.nodeplayer.network.discord.DiscordActivityBuilder
import com.nodeplayer.network.discord.DiscordFrameCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Windows Desktop Discord IPC named pipe transport.
 * Communicates via local named pipes: \\.\pipe\discord-ipc-0..9
 */
class DiscordIpcTransport(
    private val clientIdProvider: () -> String
) : PresenceTransport {

    companion object {
        private const val TAG = "discord/ipc"
    }

    private var pipe: RandomAccessFile? = null
    private val isRunning = AtomicBoolean(false)
    private var readerThread: Thread? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        close()
        val clientId = clientIdProvider().trim()
        if (clientId.isEmpty()) return@withContext false

        for (i in 0 until DiscordIpcConstants.PIPE_COUNT) {
            val pipePath = DiscordIpcConstants.PIPE_PREFIX + i
            val raf = try {
                RandomAccessFile(pipePath, "rw")
            } catch (_: Throwable) {
                continue
            }

            try {
                // Handshake Op 0
                val handshakeJson = """{"v":1,"client_id":"$clientId"}"""
                val handshakeFrame = DiscordFrameCodec.encodeFrame(DiscordIpcConstants.OP_HANDSHAKE, handshakeJson)
                raf.write(handshakeFrame)

                // Read READY response Op 1
                val headerBytes = ByteArray(DiscordFrameCodec.HEADER_SIZE)
                raf.readFully(headerBytes)
                val (op, len) = DiscordFrameCodec.decodeHeader(headerBytes)
                val payloadBytes = ByteArray(len)
                raf.readFully(payloadBytes)

                if (op == DiscordIpcConstants.OP_FRAME) {
                    pipe = raf
                    isRunning.set(true)
                    startReaderLoop(raf)
                    NodeLog.i(TAG, "Connected to Discord IPC on pipe index $i")
                    return@withContext true
                } else {
                    raf.close()
                }
            } catch (e: Throwable) {
                NodeLog.d(TAG, "Pipe index $i handshake failed: ${e.message}")
                try { raf.close() } catch (_: Throwable) {}
            }
        }
        false
    }

    private fun startReaderLoop(raf: RandomAccessFile) {
        readerThread = Thread({
            val headerBuffer = ByteArray(DiscordFrameCodec.HEADER_SIZE)
            while (isRunning.get()) {
                try {
                    raf.readFully(headerBuffer)
                    val (op, len) = DiscordFrameCodec.decodeHeader(headerBuffer)
                    if (len > 0) {
                        val payload = ByteArray(len)
                        raf.readFully(payload)
                    }
                    if (op == DiscordIpcConstants.OP_CLOSE) {
                        NodeLog.i(TAG, "Discord IPC received OP_CLOSE")
                        break
                    }
                } catch (_: Throwable) {
                    break
                }
            }
            isRunning.set(false)
        }, "DiscordIpcReader").apply {
            isDaemon = true
            start()
        }
    }

    override suspend fun setActivity(json: String): Boolean = withContext(Dispatchers.IO) {
        val currentPipe = pipe ?: return@withContext false
        if (!isRunning.get()) return@withContext false
        try {
            val frame = DiscordFrameCodec.encodeFrame(DiscordIpcConstants.OP_FRAME, json)
            synchronized(currentPipe) {
                currentPipe.write(frame)
            }
            true
        } catch (e: Throwable) {
            NodeLog.w(TAG, "Failed to write activity frame: ${e.message}")
            close()
            false
        }
    }

    override suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        val clearJson = DiscordActivityBuilder.buildClearActivityJson()
        setActivity(clearJson)
    }

    override fun close() {
        isRunning.set(false)
        try {
            readerThread?.interrupt()
        } catch (_: Throwable) {}
        readerThread = null

        val p = pipe
        pipe = null
        if (p != null) {
            try {
                p.close()
            } catch (_: Throwable) {}
        }
    }
}
