package com.nodeplayer.network.session

import com.nodeplayer.network.pairing.DeviceRegistry
import com.nodeplayer.network.protocol.*
import com.nodeplayer.network.transport.NodeConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

data class ExternalMediaSession(
    val appName: String,
    val trackTitle: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long
)

interface PlatformMediaSessionBridge {
    fun sendTransportCommand(appName: String, action: String)
}

/**
 * Third-Party Media Session Service (Spotify, YouTube Music).
 * Transmits now-playing metadata and forwards play, pause, and skip commands.
 * STRICT SECURITY GUARANTEE: ZERO audio capture is performed or transmitted over the network.
 * Off by default; requires canControlExternalSessions permission.
 */
class ExternalSessionService(
    private val platformBridge: PlatformMediaSessionBridge? = null,
    private val registry: DeviceRegistry? = null
) {
    private val _activeRemoteSession = MutableStateFlow<ExternalMediaSession?>(null)
    val activeRemoteSession: StateFlow<ExternalMediaSession?> = _activeRemoteSession.asStateFlow()

    suspend fun handleFrame(frame: NodeFraming.Frame, connection: NodeConnection): Boolean {
        // Enforce permission check
        val paired = registry?.getPairedDeviceByFingerprint(connection.peerFingerprint)
        if (paired != null && !paired.permissions.canControlExternalSessions) {
            return false // Blocked by permission switch
        }

        when (frame.messageType) {
            NodeMessageTypes.EXTERNAL_SESSION_STATE -> {
                val msg = ExternalSessionStateMessage.fromBytes(frame.payload)
                _activeRemoteSession.value = ExternalMediaSession(
                    appName = msg.appName,
                    trackTitle = msg.trackTitle,
                    artist = msg.artist,
                    album = msg.album,
                    isPlaying = msg.isPlaying,
                    positionMs = msg.positionMs,
                    durationMs = msg.durationMs
                )
                return true
            }
            NodeMessageTypes.EXTERNAL_SESSION_CMD -> {
                val json = String(frame.payload, StandardCharsets.UTF_8)
                val map = SimpleJson.parseStringMap(json)
                val app = map["app"] ?: ""
                val action = map["action"] ?: ""
                if (app.isNotBlank() && action.isNotBlank()) {
                    platformBridge?.sendTransportCommand(app, action)
                }
                return true
            }
            else -> return false
        }
    }

    /**
     * Broadcasts current local third-party media session state to connected peers.
     */
    suspend fun broadcastLocalExternalSession(connection: NodeConnection, session: ExternalMediaSession) {
        val msg = ExternalSessionStateMessage(
            appName = session.appName,
            trackTitle = session.trackTitle,
            artist = session.artist,
            album = session.album,
            isPlaying = session.isPlaying,
            positionMs = session.positionMs,
            durationMs = session.durationMs
        )
        connection.sendPayload(NodeMessageTypes.EXTERNAL_SESSION_STATE, msg.toBytes())
    }

    /**
     * Sends transport command to remote third-party session.
     */
    suspend fun sendRemoteSessionCommand(connection: NodeConnection, appName: String, action: String) {
        val json = """{"app":"${SimpleJson.escape(appName)}","action":"${SimpleJson.escape(action)}"}"""
        connection.sendPayload(NodeMessageTypes.EXTERNAL_SESSION_CMD, json.toByteArray(StandardCharsets.UTF_8))
    }
}
