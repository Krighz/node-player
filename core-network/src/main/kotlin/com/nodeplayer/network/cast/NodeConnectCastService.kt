package com.nodeplayer.network.cast

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import com.nodeplayer.network.pairing.DeviceRegistry
import com.nodeplayer.network.protocol.*
import com.nodeplayer.network.transport.NodeConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class ActiveCastSession(
    val streamId: String,
    val sequenceId: Long,
    val trackTitle: String,
    val artist: String,
    val album: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val durationMs: Long,
    val isSender: Boolean,
    val artworkBase64: String = ""
)

/**
 * Node Connect Audio Sharing Service (Cast Plane).
 * Shares live pre-DSP PCM audio streams over local Wi-Fi without transferring files.
 * Zero disk writes on receiver; audio flows directly to jitter buffer and DAC.
 */
class NodeConnectCastService(
    private val registry: DeviceRegistry? = null,
    private val onStreamStartedLocally: ((ActiveCastSession, JitterBuffer) -> Unit)? = null,
    var onStreamStoppedLocally: (() -> Unit)? = null
) {
    private var senderConnection: NodeConnection? = null
    private var activeStreamId: String? = null
    private val sequenceCounter = AtomicLong(1L)
    private val sentChunkCount = AtomicLong(0L)
    private val receivedChunkCount = AtomicLong(0L)

    private val senderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var senderStreamingJob: Job? = null
    private val audioChunkChannel = Channel<ByteArray>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _currentSession = MutableStateFlow<ActiveCastSession?>(null)
    val currentSession: StateFlow<ActiveCastSession?> = _currentSession.asStateFlow()

    private var activeReceiverJitterBuffer: JitterBuffer? = null

    // --- 1. SENDER API ---

    /**
     * Starts audio casting to a remote paired receiver.
     */
    suspend fun startCastAsSender(
        targetConnection: NodeConnection,
        track: Track,
        initialPtsMs: Long = 0L,
        artworkBase64: String = ""
    ): String {
        // Verify receiver permissions
        val paired = registry?.getPairedDeviceByFingerprint(targetConnection.peerFingerprint)
            ?: registry?.getPairedDeviceById(targetConnection.peerDeviceId)
        if (paired != null && !paired.permissions.canReceiveCast) {
            throw SecurityException("Remote device does not have permission to receive audio cast")
        }

        val streamId = UUID.randomUUID().toString()
        val seqId = sequenceCounter.incrementAndGet()
        activeStreamId = streamId
        senderConnection = targetConnection

        // Drain any stale chunks from previous stream
        while (audioChunkChannel.tryReceive().isSuccess) {}

        senderStreamingJob?.cancel()
        senderStreamingJob = senderScope.launch {
            for (chunk in audioChunkChannel) {
                val conn = senderConnection ?: break
                if (!conn.isOpen) break
                try {
                    conn.sendPayload(
                        messageType = NodeMessageTypes.CAST_PCM_CHUNK,
                        payload = chunk,
                        sequenceId = sequenceCounter.get()
                    )
                } catch (e: Exception) {
                    NodeLog.w("nodeconnect/cast", "Streaming PCM write failed: ${e.message}")
                    break
                }
            }
        }

        val startMsg = CastStartMessage(
            streamId = streamId,
            sequenceId = seqId,
            sampleRate = if (track.sampleRate > 0) track.sampleRate else 44100,
            bitDepth = if (track.bitDepth > 0) track.bitDepth else 16,
            channels = if (track.channels > 0) track.channels else 2,
            trackTitle = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            formatName = track.formatBadgeText,
            artworkBase64 = artworkBase64
        )

        targetConnection.sendPayload(NodeMessageTypes.CAST_START, startMsg.toBytes(), NodeFraming.FLAG_PRIORITY, sequenceId = seqId)
        NodeLog.i("nodeconnect/cast", "Starting audio cast sender stream '${startMsg.trackTitle}' to ${targetConnection.peerDeviceId} (${targetConnection.peerHostAddress}) seq=$seqId")

        _currentSession.value = ActiveCastSession(
            streamId = streamId,
            sequenceId = seqId,
            trackTitle = track.title,
            artist = track.artist,
            album = track.album,
            sampleRate = startMsg.sampleRate,
            bitDepth = startMsg.bitDepth,
            channels = startMsg.channels,
            durationMs = track.durationMs,
            isSender = true,
            artworkBase64 = artworkBase64
        )

        return streamId
    }

    /**
     * Streams an uncompressed raw PCM audio chunk to the receiver.
     * Non-blocking and thread-safe: callable directly from real-time audio threads.
     */
    fun sendPcmAudioChunk(pcmBytes: ByteArray, ptsMs: Long = 0L) {
        val conn = senderConnection ?: return
        if (!conn.isOpen) return

        val count = sentChunkCount.incrementAndGet()
        if (count % 100 == 1L) {
            NodeLog.d("nodeconnect/cast", "Streaming PCM audio chunk #$count (${pcmBytes.size} bytes) pts=${ptsMs}ms")
        }

        audioChunkChannel.trySend(pcmBytes.clone())
    }

    /**
     * Flushes the receiver's jitter buffer immediately on seek or track skip.
     */
    suspend fun flushSenderStream(newPtsMs: Long) {
        val conn = senderConnection ?: return
        val streamId = activeStreamId ?: return
        val newSeq = sequenceCounter.incrementAndGet()

        // Drain pending pre-seek audio packets
        while (audioChunkChannel.tryReceive().isSuccess) {}

        val flushMsg = CastFlushMessage(streamId, newSeq)
        conn.sendPayload(NodeMessageTypes.CAST_FLUSH, flushMsg.toBytes(), NodeFraming.FLAG_PRIORITY, sequenceId = newSeq)
    }

    /**
     * Stops casting.
     */
    suspend fun stopCastAsSender() {
        val conn = senderConnection
        val streamId = activeStreamId

        senderStreamingJob?.cancel()
        senderStreamingJob = null
        while (audioChunkChannel.tryReceive().isSuccess) {}

        if (conn != null && streamId != null && conn.isOpen) {
            val stopMsg = """{"streamId":"${SimpleJson.escape(streamId)}"}""".toByteArray(Charsets.UTF_8)
            try {
                conn.sendPayload(NodeMessageTypes.CAST_STOP, stopMsg, NodeFraming.FLAG_PRIORITY)
            } catch (e: Exception) {
                // Ignore
            }
        }
        NodeLog.i("nodeconnect/cast", "Stopped audio cast as sender")
        senderConnection = null
        activeStreamId = null
        _currentSession.value = null
    }

    // --- 2. RECEIVER API ---

    /**
     * Handles incoming cast plane frames from the remote sender.
     */
    suspend fun handleFrame(frame: NodeFraming.Frame, connection: NodeConnection): Boolean {
        when (frame.messageType) {
            NodeMessageTypes.CAST_START -> {
                handleCastStart(frame.payload, connection)
                return true
            }
            NodeMessageTypes.CAST_PCM_CHUNK -> {
                handleCastPcmChunk(frame)
                return true
            }
            NodeMessageTypes.CAST_FLUSH -> {
                handleCastFlush(frame.payload)
                return true
            }
            NodeMessageTypes.CAST_STOP -> {
                handleCastStop()
                return true
            }
            else -> return false
        }
    }

    private fun handleCastStart(payload: ByteArray, connection: NodeConnection) {
        val startMsg = CastStartMessage.fromBytes(payload)
        NodeLog.i("nodeconnect/cast", "Received audio cast start: '${startMsg.trackTitle}' by '${startMsg.artist}' (${startMsg.sampleRate}Hz, ${startMsg.channels}ch, seq=${startMsg.sequenceId}) from ${connection.peerDeviceId}")
        val jitter = JitterBuffer(
            sampleRate = startMsg.sampleRate,
            bitDepth = startMsg.bitDepth,
            channels = startMsg.channels,
            targetBufferMs = 400,
            minBufferMs = 180,
            maxBufferMs = 2000
        )
        jitter.flush(startMsg.sequenceId)
        activeReceiverJitterBuffer = jitter

        val session = ActiveCastSession(
            streamId = startMsg.streamId,
            sequenceId = startMsg.sequenceId,
            trackTitle = startMsg.trackTitle,
            artist = startMsg.artist,
            album = startMsg.album,
            sampleRate = startMsg.sampleRate,
            bitDepth = startMsg.bitDepth,
            channels = startMsg.channels,
            durationMs = startMsg.durationMs,
            isSender = false,
            artworkBase64 = startMsg.artworkBase64
        )
        _currentSession.value = session
        onStreamStartedLocally?.invoke(session, jitter)
    }

    private fun handleCastPcmChunk(frame: NodeFraming.Frame) {
        val jitter = activeReceiverJitterBuffer ?: return
        val count = receivedChunkCount.incrementAndGet()
        if (count % 100 == 1L) {
            NodeLog.d("nodeconnect/cast", "Received PCM cast chunk #$count (${frame.payload.size} bytes) seq=${frame.sequenceId}")
        }
        jitter.push(
            sequenceId = frame.sequenceId,
            ptsMs = 0L,
            data = frame.payload
        )
    }

    private fun handleCastFlush(payload: ByteArray) {
        val flushMsg = CastFlushMessage.fromBytes(payload)
        NodeLog.i("nodeconnect/cast", "Cast stream flushed to sequenceId=${flushMsg.newSequenceId}")
        activeReceiverJitterBuffer?.flush(flushMsg.newSequenceId)
    }

    private fun handleCastStop() {
        NodeLog.i("nodeconnect/cast", "Cast stream stopped by remote sender")
        activeReceiverJitterBuffer = null
        _currentSession.value = null
        onStreamStoppedLocally?.invoke()
    }

    val receiverJitterBuffer: JitterBuffer?
        get() = activeReceiverJitterBuffer

    val isCasting: Boolean
        get() = _currentSession.value != null
}
