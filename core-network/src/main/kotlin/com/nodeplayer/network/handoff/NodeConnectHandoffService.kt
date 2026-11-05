package com.nodeplayer.network.handoff

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import com.nodeplayer.network.protocol.*
import com.nodeplayer.network.transport.NodeConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets

data class HandoffPayload(
    val trackTitle: String,
    val artist: String,
    val album: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val currentQueueIndex: Int = 0,
    val eqPresetName: String = "Flat",
    val bassBoostDb: Float = 0f,
    val sleepTimerRemainingMs: Long = 0L,
    val artworkBase64: String = ""
)

/**
 * Mid-Track Playback Handoff & Sleep Timer Synchronization.
 * Seamlessly moves active playback (track, millisecond position, queue, EQ profile, sleep timer) to another device.
 */
class NodeConnectHandoffService(
    private val onHandoffReceived: ((HandoffPayload) -> Boolean)? = null,
    private val onSleepTimerUpdated: ((Long) -> Unit)? = null
) {
    private var pendingHandoffAck: CompletableDeferred<Boolean>? = null

    private val _lastHandoff = MutableStateFlow<HandoffPayload?>(null)
    val lastHandoff: StateFlow<HandoffPayload?> = _lastHandoff.asStateFlow()

    suspend fun handleFrame(frame: NodeFraming.Frame, connection: NodeConnection): Boolean {
        when (frame.messageType) {
            NodeMessageTypes.HANDOFF_REQ -> {
                handleHandoffReq(frame.payload, connection)
                return true
            }
            NodeMessageTypes.HANDOFF_ACK -> {
                handleHandoffAck(frame.payload)
                return true
            }
            else -> return false
        }
    }

    private suspend fun handleHandoffReq(payload: ByteArray, connection: NodeConnection) {
        val map = SimpleJson.parseStringMap(String(payload, StandardCharsets.UTF_8))
        val handoff = HandoffPayload(
            trackTitle = map["trackTitle"] ?: "",
            artist = map["artist"] ?: "",
            album = map["album"] ?: "",
            positionMs = map["positionMs"]?.toLongOrNull() ?: 0L,
            durationMs = map["durationMs"]?.toLongOrNull() ?: 0L,
            isPlaying = map["isPlaying"]?.toBooleanStrictOrNull() ?: false,
            currentQueueIndex = map["currentQueueIndex"]?.toIntOrNull() ?: 0,
            eqPresetName = map["eqPresetName"] ?: "Flat",
            bassBoostDb = map["bassBoostDb"]?.toFloatOrNull() ?: 0f,
            sleepTimerRemainingMs = map["sleepTimerRemainingMs"]?.toLongOrNull() ?: 0L,
            artworkBase64 = map["artworkBase64"] ?: ""
        )

        _lastHandoff.value = handoff
        NodeLog.i("nodeconnect/handoff", "Received handoff request for '${handoff.trackTitle}' by '${handoff.artist}' pos=${handoff.positionMs}ms playing=${handoff.isPlaying}")
        val accepted = onHandoffReceived?.invoke(handoff) ?: true

        if (handoff.sleepTimerRemainingMs > 0L) {
            onSleepTimerUpdated?.invoke(handoff.sleepTimerRemainingMs)
        }

        val ackJson = """{"success":$accepted}"""
        connection.sendPayload(
            messageType = NodeMessageTypes.HANDOFF_ACK,
            payload = ackJson.toByteArray(StandardCharsets.UTF_8),
            flags = NodeFraming.FLAG_PRIORITY
        )
    }

    private fun handleHandoffAck(payload: ByteArray) {
        val map = SimpleJson.parseStringMap(String(payload, StandardCharsets.UTF_8))
        val success = map["success"]?.toBooleanStrictOrNull() ?: true
        pendingHandoffAck?.complete(success)
    }

    /**
     * Client call: Moves the current track & position to a remote device.
     */
    suspend fun initiateHandoff(
        targetConnection: NodeConnection,
        track: Track,
        positionMs: Long,
        isPlaying: Boolean,
        currentQueueIndex: Int = 0,
        eqPresetName: String = "Flat",
        bassBoostDb: Float = 0f,
        sleepTimerRemainingMs: Long = 0L,
        artworkBase64: String = "",
        timeoutMs: Long = 4000L
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingHandoffAck = deferred
        NodeLog.i("nodeconnect/handoff", "Initiating handoff for '${track.title}' pos=${positionMs}ms playing=$isPlaying to ${targetConnection.peerDeviceId} (${targetConnection.peerHostAddress})")

        val json = """{"trackTitle":"${SimpleJson.escape(track.title)}","artist":"${SimpleJson.escape(track.artist)}","album":"${SimpleJson.escape(track.album)}","positionMs":$positionMs,"durationMs":${track.durationMs},"isPlaying":$isPlaying,"currentQueueIndex":$currentQueueIndex,"eqPresetName":"${SimpleJson.escape(eqPresetName)}","bassBoostDb":$bassBoostDb,"sleepTimerRemainingMs":$sleepTimerRemainingMs,"artworkBase64":"${SimpleJson.escape(artworkBase64)}"}"""

        targetConnection.sendPayload(
            messageType = NodeMessageTypes.HANDOFF_REQ,
            payload = json.toByteArray(StandardCharsets.UTF_8),
            flags = NodeFraming.FLAG_PRIORITY
        )

        return withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false
    }
}
