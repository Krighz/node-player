package com.nodeplayer.network.service

import com.nodeplayer.network.protocol.ControlStateEventMessage
import com.nodeplayer.network.protocol.NodeFraming
import com.nodeplayer.network.protocol.NodeMessageTypes
import com.nodeplayer.network.transport.NodeConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles Now Playing Mirroring (display-only mode for projecting playback metadata to larger displays).
 * Zero local audio output; purely visual telemetry.
 */
class NodeConnectMirrorService {

    private val _mirroredState = MutableStateFlow<ControlStateEventMessage?>(null)
    val mirroredState: StateFlow<ControlStateEventMessage?> = _mirroredState.asStateFlow()

    suspend fun handleFrame(frame: NodeFraming.Frame): Boolean {
        if (frame.messageType == NodeMessageTypes.MIRROR_STATE) {
            val state = ControlStateEventMessage.fromBytes(frame.payload)
            _mirroredState.value = state
            return true
        }
        return false
    }

    suspend fun broadcastMirrorState(connection: NodeConnection, state: ControlStateEventMessage) {
        connection.sendPayload(NodeMessageTypes.MIRROR_STATE, state.toBytes())
    }
}
