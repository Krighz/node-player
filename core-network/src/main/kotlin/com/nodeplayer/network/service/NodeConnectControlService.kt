package com.nodeplayer.network.service

import com.nodeplayer.common.logging.NodeLog

import com.nodeplayer.network.pairing.DeviceRegistry
import com.nodeplayer.network.protocol.*
import com.nodeplayer.network.transport.NodeConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

interface LocalPlayerController {
    fun play()
    fun pause()
    fun playPause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun setVolume(volume: Float)
    fun toggleShuffle()
    fun cycleRepeat()
    fun playTrack(trackId: Long, trackTitle: String = "") {}
}

/**
 * Handles remote transport commands and broadcasts local playback state to controllers.
 */
class NodeConnectControlService(
    private val localController: LocalPlayerController? = null,
    private val registry: DeviceRegistry? = null
) {
    private val activeControllers = CopyOnWriteArrayList<NodeConnection>()

    private val _remotePlaybackState = MutableStateFlow<ControlStateEventMessage?>(null)
    val remotePlaybackState: StateFlow<ControlStateEventMessage?> = _remotePlaybackState.asStateFlow()

    fun registerController(connection: NodeConnection) {
        if (!activeControllers.contains(connection)) {
            activeControllers.add(connection)
        }
    }

    fun unregisterController(connection: NodeConnection) {
        activeControllers.remove(connection)
    }

    suspend fun handleFrame(frame: NodeFraming.Frame, connection: NodeConnection): Boolean {
        when (frame.messageType) {
            NodeMessageTypes.CONTROL_CMD -> {
                // Verify peer has canRemoteControl permission
                val paired = registry?.getPairedDeviceByFingerprint(connection.peerFingerprint)
                    ?: registry?.getPairedDeviceById(connection.peerDeviceId)
                if (paired != null && !paired.permissions.canRemoteControl) {
                    NodeLog.w("nodeconnect/control", "Rejected control command: permission canRemoteControl disabled for ${connection.peerDeviceId}")
                    return true // Blocked by permission switch
                }

                val cmd = ControlCommandMessage.fromBytes(frame.payload)
                NodeLog.i("nodeconnect/control", "Received control command: ${cmd.action} from ${connection.peerDeviceId} (${connection.peerHostAddress})")
                executeLocalCommand(cmd)
                return true
            }
            NodeMessageTypes.CONTROL_STATE -> {
                val state = ControlStateEventMessage.fromBytes(frame.payload)
                _remotePlaybackState.value = state
                return true
            }
            else -> return false
        }
    }

    private fun executeLocalCommand(cmd: ControlCommandMessage) {
        val ctrl = localController
        if (ctrl == null) {
            NodeLog.w("nodeconnect/control", "Cannot execute command ${cmd.action}: localController is null")
            return
        }
        NodeLog.i("nodeconnect/control", "Executing local transport command: ${cmd.action}")
        when (cmd.action) {
            ControlAction.PLAY -> ctrl.play()
            ControlAction.PAUSE -> ctrl.pause()
            ControlAction.PLAY_PAUSE -> ctrl.playPause()
            ControlAction.SEEK -> ctrl.seekTo(cmd.seekPositionMs)
            ControlAction.NEXT -> ctrl.skipToNext()
            ControlAction.PREVIOUS -> ctrl.skipToPrevious()
            ControlAction.SET_VOLUME -> ctrl.setVolume(cmd.volume)
            ControlAction.TOGGLE_SHUFFLE -> ctrl.toggleShuffle()
            ControlAction.CYCLE_REPEAT -> ctrl.cycleRepeat()
            ControlAction.PLAY_TRACK -> ctrl.playTrack(cmd.trackId, cmd.trackTitle)
        }
    }

    /**
     * Broadcasts updated local playback state to all authorized connected controllers.
     */
    suspend fun broadcastLocalState(state: ControlStateEventMessage) {
        val payload = state.toBytes()
        for (conn in activeControllers) {
            if (conn.isOpen) {
                try {
                    conn.sendPayload(NodeMessageTypes.CONTROL_STATE, payload)
                } catch (e: Exception) {
                    // Ignore send failure
                }
            }
        }
    }

    /**
     * Client call: Sends a transport control command to a remote peer.
     */
    suspend fun sendRemoteCommand(
        connection: NodeConnection,
        action: ControlAction,
        seekPositionMs: Long = 0L,
        volume: Float = 1.0f,
        trackId: Long = 0L,
        trackTitle: String = ""
    ) {
        val cmd = ControlCommandMessage(action, seekPositionMs, volume, trackId, trackTitle)
        NodeLog.i("nodeconnect/control", "Sending remote control command: ${cmd.action} to ${connection.peerDeviceId} (${connection.peerHostAddress})")
        connection.sendPayload(NodeMessageTypes.CONTROL_CMD, cmd.toBytes())
    }
}
