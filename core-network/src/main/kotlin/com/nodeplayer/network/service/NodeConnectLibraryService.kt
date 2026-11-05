package com.nodeplayer.network.service

import com.nodeplayer.library.repository.TrackRepository
import com.nodeplayer.model.Track
import com.nodeplayer.network.protocol.NodeFraming
import com.nodeplayer.network.protocol.NodeMessageTypes
import com.nodeplayer.network.protocol.SimpleJson
import com.nodeplayer.network.transport.NodeConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class RemoteLibraryQuery(
    val query: String,
    val offset: Int = 0,
    val limit: Int = 50,
    val requestId: Long = 0L
)

data class RemoteLibraryResult(
    val requestId: Long,
    val totalCount: Int,
    val tracks: List<Track>
)

/**
 * Handles remote library browsing, universal search, and artwork thumbnail queries.
 * Protects user privacy: local absolute file paths are NEVER exposed over the network.
 */
class NodeConnectLibraryService(
    private val trackRepository: TrackRepository? = null,
    private val artworkProvider: ((trackId: Long) -> ByteArray?)? = null
) {
    private val nextRequestId = AtomicLong(1L)
    private val pendingQueries = ConcurrentHashMap<Long, CompletableDeferred<RemoteLibraryResult>>()
    private val pendingArtwork = ConcurrentHashMap<Long, CompletableDeferred<ByteArray?>>()

    /**
     * Handles incoming library plane frames from remote peers.
     */
    suspend fun handleFrame(frame: NodeFraming.Frame, connection: NodeConnection): Boolean {
        when (frame.messageType) {
            NodeMessageTypes.LIBRARY_QUERY -> {
                handleLibraryQuery(frame.payload, connection)
                return true
            }
            NodeMessageTypes.LIBRARY_RESULT -> {
                handleLibraryResult(frame.payload)
                return true
            }
            NodeMessageTypes.ARTWORK_QUERY -> {
                handleArtworkQuery(frame.payload, connection)
                return true
            }
            NodeMessageTypes.ARTWORK_RESULT -> {
                handleArtworkResult(frame)
                return true
            }
            else -> return false
        }
    }

    private suspend fun handleLibraryQuery(payload: ByteArray, connection: NodeConnection) {
        val json = String(payload, StandardCharsets.UTF_8)
        val map = SimpleJson.parseStringMap(json)
        val query = map["query"] ?: ""
        val offset = map["offset"]?.toIntOrNull() ?: 0
        val limit = (map["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
        val reqId = map["requestId"]?.toLongOrNull() ?: 0L

        val allTracks = trackRepository?.getAllTracksList() ?: emptyList()
        val filtered = if (query.isBlank()) {
            allTracks
        } else {
            val q = query.trim().lowercase()
            allTracks.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }

        val page = filtered.drop(offset).take(limit)
        val serializedTracks = page.joinToString(";;") { serializeTrack(it) }
        val respJson = """{"requestId":$reqId,"totalCount":${filtered.size},"tracks":"${SimpleJson.escape(serializedTracks)}"}"""

        connection.sendPayload(NodeMessageTypes.LIBRARY_RESULT, respJson.toByteArray(StandardCharsets.UTF_8))
    }

    private fun handleLibraryResult(payload: ByteArray) {
        val json = String(payload, StandardCharsets.UTF_8)
        val map = SimpleJson.parseStringMap(json)
        val reqId = map["requestId"]?.toLongOrNull() ?: return
        val total = map["totalCount"]?.toIntOrNull() ?: 0
        val rawTracks = map["tracks"] ?: ""

        val list = if (rawTracks.isNotBlank()) {
            rawTracks.split(";;").mapNotNull { deserializeTrack(it) }
        } else {
            emptyList()
        }

        val deferred = pendingQueries.remove(reqId)
        deferred?.complete(RemoteLibraryResult(reqId, total, list))
    }

    private suspend fun handleArtworkQuery(payload: ByteArray, connection: NodeConnection) {
        val map = SimpleJson.parseStringMap(String(payload, StandardCharsets.UTF_8))
        val trackId = map["trackId"]?.toLongOrNull() ?: return
        val reqId = map["requestId"]?.toLongOrNull() ?: 0L

        val artBytes = artworkProvider?.invoke(trackId) ?: ByteArray(0)
        connection.sendPayload(
            messageType = NodeMessageTypes.ARTWORK_RESULT,
            payload = artBytes,
            flags = 0,
            sequenceId = reqId
        )
    }

    private fun handleArtworkResult(frame: NodeFraming.Frame) {
        val reqId = frame.sequenceId
        val deferred = pendingArtwork.remove(reqId)
        deferred?.complete(if (frame.payload.isNotEmpty()) frame.payload else null)
    }

    /**
     * Client call: Queries a connected remote peer's music library.
     */
    suspend fun queryRemoteLibrary(
        connection: NodeConnection,
        query: String = "",
        offset: Int = 0,
        limit: Int = 50,
        timeoutMs: Long = 4000L
    ): RemoteLibraryResult? {
        val reqId = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<RemoteLibraryResult>()
        pendingQueries[reqId] = deferred

        val json = """{"requestId":$reqId,"query":"${SimpleJson.escape(query)}","offset":$offset,"limit":$limit}"""
        connection.sendPayload(NodeMessageTypes.LIBRARY_QUERY, json.toByteArray(StandardCharsets.UTF_8))

        return withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }.also {
            pendingQueries.remove(reqId)
        }
    }

    /**
     * Client call: Fetches remote album artwork thumbnail bytes for a track.
     */
    suspend fun fetchRemoteArtwork(
        connection: NodeConnection,
        trackId: Long,
        timeoutMs: Long = 3000L
    ): ByteArray? {
        val reqId = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<ByteArray?>()
        pendingArtwork[reqId] = deferred

        val json = """{"requestId":$reqId,"trackId":$trackId}"""
        connection.sendPayload(NodeMessageTypes.ARTWORK_QUERY, json.toByteArray(StandardCharsets.UTF_8))

        return withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }.also {
            pendingArtwork.remove(reqId)
        }
    }

    private fun serializeTrack(t: Track): String {
        // Path is intentionally replaced by a virtual remote identifier: "remote://track/<id>"
        return listOf(
            t.id,
            SimpleJson.escape(t.title),
            SimpleJson.escape(t.artist),
            SimpleJson.escape(t.album),
            t.durationMs,
            t.sampleRate,
            t.bitDepth,
            t.channels,
            SimpleJson.escape(t.formatBadgeText),
            t.isLossless
        ).joinToString("||")
    }

    private fun deserializeTrack(raw: String): Track? {
        val p = raw.split("||")
        if (p.size < 10) return null
        return Track(
            id = p[0].toLongOrNull() ?: return null,
            title = p[1],
            artist = p[2],
            album = p[3],
            durationMs = p[4].toLongOrNull() ?: 0L,
            path = "nodeconnect://remote/track/${p[0]}",
            sampleRate = p[5].toIntOrNull() ?: 44100,
            bitDepth = p[6].toIntOrNull() ?: 16,
            channels = p[7].toIntOrNull() ?: 2,
            formatDescription = p[8],
            isLossless = p[9].toBooleanStrictOrNull() ?: false
        )
    }
}
