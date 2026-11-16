package com.nodeplayer.model.presence

/**
 * Snapshot of the current audio playback state for rich presence reporting.
 */
data class PresenceState(
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false
)

/**
 * Connection and operational status of the rich presence transport.
 */
enum class PresenceStatus {
    DISABLED,
    APP_ID_REQUIRED,
    NOT_RUNNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Standard protocol constants for Discord IPC named pipe transport.
 */
object DiscordIpcConstants {
    const val OP_HANDSHAKE: Int = 0
    const val OP_FRAME: Int = 1
    const val OP_CLOSE: Int = 2
    const val OP_PING: Int = 3
    const val OP_PONG: Int = 4

    const val PIPE_COUNT: Int = 10
    const val PIPE_PREFIX: String = "\\\\.\\pipe\\discord-ipc-"

    const val ACTIVITY_TYPE_LISTENING: Int = 2
    const val MAX_FIELD_LENGTH: Int = 128
    const val MIN_FIELD_LENGTH: Int = 2
    const val MIN_UPDATE_INTERVAL_MS: Long = 15_000L

    const val DEFAULT_LARGE_IMAGE_KEY: String = "app_logo"
}

/**
 * Contract for platform-specific rich presence communications.
 */
interface PresenceTransport {
    suspend fun connect(): Boolean
    suspend fun setActivity(json: String): Boolean
    suspend fun clear(): Boolean
    fun close()
}
