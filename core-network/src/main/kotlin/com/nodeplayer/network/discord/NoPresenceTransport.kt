package com.nodeplayer.network.discord

import com.nodeplayer.model.presence.PresenceTransport

/**
 * No-op rich presence transport for unsupported platforms (Android) and headless environments.
 */
class NoPresenceTransport : PresenceTransport {
    override suspend fun connect(): Boolean = false
    override suspend fun setActivity(json: String): Boolean = false
    override suspend fun clear(): Boolean = true
    override fun close() {}
}
