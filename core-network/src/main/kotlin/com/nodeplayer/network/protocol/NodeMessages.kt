package com.nodeplayer.network.protocol

import java.nio.charset.StandardCharsets

object NodeMessageTypes {
    const val HANDSHAKE_REQ: Short = 0x0001
    const val HANDSHAKE_RESP: Short = 0x0002
    const val PING: Short = 0x0003
    const val PONG: Short = 0x0004
    const val DISCONNECT: Short = 0x0005
    const val PAIRING_REQ: Short = 0x0008
    const val PAIRING_RESP: Short = 0x0009

    // Control Plane (0x0010 - 0x001F)
    const val CONTROL_CMD: Short = 0x0010
    const val CONTROL_STATE: Short = 0x0011

    // Library Plane (0x0020 - 0x002F)
    const val LIBRARY_QUERY: Short = 0x0020
    const val LIBRARY_RESULT: Short = 0x0021
    const val ARTWORK_QUERY: Short = 0x0022
    const val ARTWORK_RESULT: Short = 0x0023

    // Cast Audio Plane (0x0030 - 0x003F)
    const val CAST_START: Short = 0x0030
    const val CAST_PCM_CHUNK: Short = 0x0031
    const val CAST_OPUS_CHUNK: Short = 0x0032
    const val CAST_FLUSH: Short = 0x0033
    const val CAST_STOP: Short = 0x0034

    // Playback Handoff (0x0040 - 0x004F)
    const val HANDOFF_REQ: Short = 0x0040
    const val HANDOFF_ACK: Short = 0x0041

    // Data Transfer & Sync Rail (0x0050 - 0x005F)
    const val SYNC_MANIFEST_REQ: Short = 0x0050
    const val SYNC_MANIFEST_RESP: Short = 0x0051
    const val SYNC_PUSH: Short = 0x0052
    const val SYNC_PULL: Short = 0x0053
    const val SYNC_ACK: Short = 0x0054

    // Mirror & 3rd-Party Sessions (0x0060 - 0x007F)
    const val MIRROR_STATE: Short = 0x0060
    const val EXTERNAL_SESSION_STATE: Short = 0x0070
    const val EXTERNAL_SESSION_CMD: Short = 0x0071
}

// Lightweight JSON helper for parsing without heavy external frameworks
internal object SimpleJson {
    fun escape(s: String): String {
        return buildString {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
    }

    fun parseStringMap(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val trimmed = json.trim().removeSurrounding("{", "}").trim()
        if (trimmed.isEmpty()) return map

        var i = 0
        while (i < trimmed.length) {
            val keyStart = trimmed.indexOf('"', i)
            if (keyStart == -1) break
            val keyEnd = findClosingQuote(trimmed, keyStart + 1)
            if (keyEnd == -1) break
            val key = unescape(trimmed.substring(keyStart + 1, keyEnd))

            val colon = trimmed.indexOf(':', keyEnd)
            if (colon == -1) break

            var valStart = colon + 1
            while (valStart < trimmed.length && trimmed[valStart].isWhitespace()) valStart++
            if (valStart >= trimmed.length) break

            val (value, nextIdx) = if (trimmed[valStart] == '"') {
                val valEnd = findClosingQuote(trimmed, valStart + 1)
                val strVal = if (valEnd != -1) unescape(trimmed.substring(valStart + 1, valEnd)) else ""
                val after = trimmed.indexOf(',', if (valEnd != -1) valEnd else valStart)
                strVal to (if (after != -1) after + 1 else trimmed.length)
            } else {
                val comma = trimmed.indexOf(',', valStart)
                val rawVal = if (comma != -1) trimmed.substring(valStart, comma).trim() else trimmed.substring(valStart).trim()
                rawVal to (if (comma != -1) comma + 1 else trimmed.length)
            }
            map[key] = value
            i = nextIdx
        }
        return map
    }

    private fun findClosingQuote(s: String, start: Int): Int {
        var idx = start
        while (idx < s.length) {
            if (s[idx] == '"' && (idx == 0 || s[idx - 1] != '\\')) {
                return idx
            }
            idx++
        }
        return -1
    }

    private fun unescape(s: String): String {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
    }
}

// 1. Handshake Messages
data class HandshakeMessage(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val protocolVersion: Int = 1,
    val roles: List<String> = listOf("AUDIO_SENDER", "AUDIO_RECEIVER", "LIBRARY_HOST", "REMOTE_CONTROLLER")
) {
    fun toBytes(): ByteArray {
        val json = """{"deviceId":"${SimpleJson.escape(deviceId)}","deviceName":"${SimpleJson.escape(deviceName)}","platform":"${SimpleJson.escape(platform)}","protocolVersion":$protocolVersion,"roles":"${roles.joinToString(",")}"}"""
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): HandshakeMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            val rolesStr = map["roles"] ?: ""
            val rolesList = if (rolesStr.isNotBlank()) rolesStr.split(",") else emptyList()
            return HandshakeMessage(
                deviceId = map["deviceId"] ?: "",
                deviceName = map["deviceName"] ?: "Unknown Device",
                platform = map["platform"] ?: "Unknown",
                protocolVersion = map["protocolVersion"]?.toIntOrNull() ?: 1,
                roles = rolesList
            )
        }
    }
}

// 2. Control Messages
enum class ControlAction {
    PLAY, PAUSE, PLAY_PAUSE, NEXT, PREVIOUS, SEEK, SET_VOLUME, TOGGLE_SHUFFLE, CYCLE_REPEAT, PLAY_TRACK
}

data class ControlCommandMessage(
    val action: ControlAction,
    val seekPositionMs: Long = 0L,
    val volume: Float = 1.0f,
    val trackId: Long = 0L,
    val trackTitle: String = ""
) {
    fun toBytes(): ByteArray {
        val json = """{"action":"${action.name}","seekPositionMs":$seekPositionMs,"volume":$volume,"trackId":$trackId,"trackTitle":"${SimpleJson.escape(trackTitle)}"}"""
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): ControlCommandMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            val action = try {
                ControlAction.valueOf(map["action"] ?: "PLAY_PAUSE")
            } catch (e: Exception) {
                ControlAction.PLAY_PAUSE
            }
            return ControlCommandMessage(
                action = action,
                seekPositionMs = map["seekPositionMs"]?.toLongOrNull() ?: 0L,
                volume = map["volume"]?.toFloatOrNull() ?: 1.0f,
                trackId = map["trackId"]?.toLongOrNull() ?: 0L,
                trackTitle = map["trackTitle"] ?: ""
            )
        }
    }
}

data class ControlStateEventMessage(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val volume: Float,
    val trackTitle: String,
    val artist: String,
    val album: String,
    val isShuffle: Boolean = false,
    val repeatMode: Int = 0,
    val formatBadge: String = "",
    val artworkBase64: String = ""
) {
    fun toBytes(): ByteArray {
        val json = """{"isPlaying":$isPlaying,"positionMs":$positionMs,"durationMs":$durationMs,"volume":$volume,"trackTitle":"${SimpleJson.escape(trackTitle)}","artist":"${SimpleJson.escape(artist)}","album":"${SimpleJson.escape(album)}","isShuffle":$isShuffle,"repeatMode":$repeatMode,"formatBadge":"${SimpleJson.escape(formatBadge)}","artworkBase64":"${SimpleJson.escape(artworkBase64)}"}"""
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): ControlStateEventMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            return ControlStateEventMessage(
                isPlaying = map["isPlaying"]?.toBooleanStrictOrNull() ?: false,
                positionMs = map["positionMs"]?.toLongOrNull() ?: 0L,
                durationMs = map["durationMs"]?.toLongOrNull() ?: 0L,
                volume = map["volume"]?.toFloatOrNull() ?: 1.0f,
                trackTitle = map["trackTitle"] ?: "",
                artist = map["artist"] ?: "",
                album = map["album"] ?: "",
                isShuffle = map["isShuffle"]?.toBooleanStrictOrNull() ?: false,
                repeatMode = map["repeatMode"]?.toIntOrNull() ?: 0,
                formatBadge = map["formatBadge"] ?: "",
                artworkBase64 = map["artworkBase64"] ?: ""
            )
        }
    }
}

// 3. Audio Cast Messages (Zero file sharing — pure pre-DSP PCM audio streaming)
data class CastStartMessage(
    val streamId: String,
    val sequenceId: Long,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val trackTitle: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val formatName: String,
    val artworkBase64: String = ""
) {
    fun toBytes(): ByteArray {
        val json = """{"streamId":"${SimpleJson.escape(streamId)}","sequenceId":$sequenceId,"sampleRate":$sampleRate,"bitDepth":$bitDepth,"channels":$channels,"trackTitle":"${SimpleJson.escape(trackTitle)}","artist":"${SimpleJson.escape(artist)}","album":"${SimpleJson.escape(album)}","durationMs":$durationMs,"formatName":"${SimpleJson.escape(formatName)}","artworkBase64":"${SimpleJson.escape(artworkBase64)}"}"""
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): CastStartMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            return CastStartMessage(
                streamId = map["streamId"] ?: "",
                sequenceId = map["sequenceId"]?.toLongOrNull() ?: 0L,
                sampleRate = map["sampleRate"]?.toIntOrNull() ?: 44100,
                bitDepth = map["bitDepth"]?.toIntOrNull() ?: 16,
                channels = map["channels"]?.toIntOrNull() ?: 2,
                trackTitle = map["trackTitle"] ?: "Audio Stream",
                artist = map["artist"] ?: "Node Player",
                album = map["album"] ?: "",
                durationMs = map["durationMs"]?.toLongOrNull() ?: 0L,
                formatName = map["formatName"] ?: "PCM",
                artworkBase64 = map["artworkBase64"] ?: ""
            )
        }
    }
}

data class CastFlushMessage(
    val streamId: String,
    val newSequenceId: Long
) {
    fun toBytes(): ByteArray {
        return """{"streamId":"${SimpleJson.escape(streamId)}","newSequenceId":$newSequenceId}""".toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): CastFlushMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            return CastFlushMessage(
                streamId = map["streamId"] ?: "",
                newSequenceId = map["newSequenceId"]?.toLongOrNull() ?: 0L
            )
        }
    }
}

// 4. Playback Handoff Message
data class HandoffRequestMessage(
    val trackTitle: String,
    val artist: String,
    val album: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val eqPresetName: String = "Flat",
    val bassBoostDb: Float = 0f
) {
    fun toBytes(): ByteArray {
        val json = """{"trackTitle":"${SimpleJson.escape(trackTitle)}","artist":"${SimpleJson.escape(artist)}","album":"${SimpleJson.escape(album)}","positionMs":$positionMs,"durationMs":$durationMs,"isPlaying":$isPlaying,"eqPresetName":"${SimpleJson.escape(eqPresetName)}","bassBoostDb":$bassBoostDb}"""
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): HandoffRequestMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            return HandoffRequestMessage(
                trackTitle = map["trackTitle"] ?: "",
                artist = map["artist"] ?: "",
                album = map["album"] ?: "",
                positionMs = map["positionMs"]?.toLongOrNull() ?: 0L,
                durationMs = map["durationMs"]?.toLongOrNull() ?: 0L,
                isPlaying = map["isPlaying"]?.toBooleanStrictOrNull() ?: false,
                eqPresetName = map["eqPresetName"] ?: "Flat",
                bassBoostDb = map["bassBoostDb"]?.toFloatOrNull() ?: 0f
            )
        }
    }
}

// 5. Third-Party External Session (Spotify, YT Music)
data class ExternalSessionStateMessage(
    val appName: String,
    val trackTitle: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long
) {
    fun toBytes(): ByteArray {
        val json = """{"appName":"${SimpleJson.escape(appName)}","trackTitle":"${SimpleJson.escape(trackTitle)}","artist":"${SimpleJson.escape(artist)}","album":"${SimpleJson.escape(album)}","isPlaying":$isPlaying,"positionMs":$positionMs,"durationMs":$durationMs}"""
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): ExternalSessionStateMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            return ExternalSessionStateMessage(
                appName = map["appName"] ?: "External App",
                trackTitle = map["trackTitle"] ?: "",
                artist = map["artist"] ?: "",
                album = map["album"] ?: "",
                isPlaying = map["isPlaying"]?.toBooleanStrictOrNull() ?: false,
                positionMs = map["positionMs"]?.toLongOrNull() ?: 0L,
                durationMs = map["durationMs"]?.toLongOrNull() ?: 0L
            )
        }
    }
}

// 6. Pairing Messages
data class PairingRequestMessage(
    val tokenOrPin: String,
    val deviceId: String,
    val deviceName: String,
    val platform: String
) {
    fun toBytes(): ByteArray {
        val json = """{"tokenOrPin":"${SimpleJson.escape(tokenOrPin)}","deviceId":"${SimpleJson.escape(deviceId)}","deviceName":"${SimpleJson.escape(deviceName)}","platform":"${SimpleJson.escape(platform)}"}"""
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): PairingRequestMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            return PairingRequestMessage(
                tokenOrPin = map["tokenOrPin"] ?: "",
                deviceId = map["deviceId"] ?: "",
                deviceName = map["deviceName"] ?: "",
                platform = map["platform"] ?: ""
            )
        }
    }
}

data class PairingResponseMessage(
    val success: Boolean,
    val message: String,
    val serverDeviceId: String,
    val serverDeviceName: String
) {
    fun toBytes(): ByteArray {
        val json = """{"success":$success,"message":"${SimpleJson.escape(message)}","serverDeviceId":"${SimpleJson.escape(serverDeviceId)}","serverDeviceName":"${SimpleJson.escape(serverDeviceName)}"}"""
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): PairingResponseMessage {
            val map = SimpleJson.parseStringMap(String(bytes, StandardCharsets.UTF_8))
            return PairingResponseMessage(
                success = map["success"]?.toBooleanStrictOrNull() ?: false,
                message = map["message"] ?: "",
                serverDeviceId = map["serverDeviceId"] ?: "",
                serverDeviceName = map["serverDeviceName"] ?: ""
            )
        }
    }
}
