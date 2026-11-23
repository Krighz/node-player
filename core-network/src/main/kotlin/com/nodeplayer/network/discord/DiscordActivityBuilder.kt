package com.nodeplayer.network.discord

import com.nodeplayer.model.presence.DiscordIpcConstants
import com.nodeplayer.model.presence.PresenceState
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/**
 * Serializer for Discord IPC `SET_ACTIVITY` command payloads.
 * Enforces field length boundaries (2..128 chars) and proper JSON encoding.
 */
object DiscordActivityBuilder {

    /**
     * Sanitizes, bounds (2..128 characters), and pads strings for Discord IPC.
     */
    fun sanitizeField(raw: String): String {
        val trimmed = raw.trim()
        val truncated = if (trimmed.length > DiscordIpcConstants.MAX_FIELD_LENGTH) {
            trimmed.substring(0, DiscordIpcConstants.MAX_FIELD_LENGTH)
        } else {
            trimmed
        }
        return when {
            truncated.isEmpty() -> "  "
            truncated.length < DiscordIpcConstants.MIN_FIELD_LENGTH -> truncated.padEnd(DiscordIpcConstants.MIN_FIELD_LENGTH, ' ')
            else -> truncated
        }
    }

    /**
     * Formats `SET_ACTIVITY` JSON payload for the current playing track.
     */
    fun buildSetActivityJson(
        state: PresenceState,
        processId: Int = getCurrentProcessId(),
        showCover: Boolean = true,
        showAlbum: Boolean = true,
        currentEpochMs: Long = System.currentTimeMillis(),
        nonce: String = UUID.randomUUID().toString()
    ): String {
        val album = state.album
        val coverUrl = state.coverUrl
        val sanitizedDetails = sanitizeField(state.title)

        val formattedState = if (showAlbum && !album.isNullOrBlank()) {
            "by ${state.artist} • $album"
        } else {
            "by ${state.artist}"
        }
        val sanitizedState = sanitizeField(formattedState)

        val startEpochSec = (currentEpochMs - state.positionMs).coerceAtLeast(0L) / 1000L
        val endEpochSec = if (state.durationMs > 0L) {
            startEpochSec + (state.durationMs / 1000L)
        } else {
            null
        }

        val largeImage = if (showCover && !coverUrl.isNullOrBlank()) {
            coverUrl
        } else {
            DiscordIpcConstants.DEFAULT_LARGE_IMAGE_KEY
        }

        val largeText = if (showAlbum && !album.isNullOrBlank()) {
            sanitizeField(album)
        } else {
            sanitizedDetails
        }

        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", processId)
                putJsonObject("activity") {
                    put("type", DiscordIpcConstants.ACTIVITY_TYPE_LISTENING)
                    put("details", sanitizedDetails)
                    put("state", sanitizedState)
                    putJsonObject("timestamps") {
                        put("start", startEpochSec)
                        if (endEpochSec != null && endEpochSec > startEpochSec) {
                            put("end", endEpochSec)
                        }
                    }
                    putJsonObject("assets") {
                        put("large_image", largeImage)
                        put("large_text", largeText)
                    }
                }
            }
            put("nonce", nonce)
        }

        return payload.toString()
    }

    /**
     * Formats `SET_ACTIVITY` JSON payload with `activity = null` to clear rich presence.
     */
    fun buildClearActivityJson(
        processId: Int = getCurrentProcessId(),
        nonce: String = UUID.randomUUID().toString()
    ): String {
        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", processId)
                put("activity", JsonNull)
            }
            put("nonce", nonce)
        }
        return payload.toString()
    }

    private fun getCurrentProcessId(): Int {
        return try {
            val processHandleClass = Class.forName("java.lang.ProcessHandle")
            val currentMethod = processHandleClass.getMethod("current")
            val handle = currentMethod.invoke(null)
            val pidMethod = handle.javaClass.getMethod("pid")
            (pidMethod.invoke(handle) as Long).toInt()
        } catch (_: Throwable) {
            1000
        }
    }
}
