package com.nodeplayer.network.innertube

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.playlist.OnlineTrackSearchProvider
import com.nodeplayer.model.Track

/**
 * InnerTube-backed search provider for the universal playlist importer.
 * Queries YouTube Music songs endpoint with privacy-first logging.
 */
class InnerTubeTrackSearchProvider(
    private val client: InnerTubeClient
) : OnlineTrackSearchProvider {

    private companion object {
        const val TAG = "innertube/track-search"
    }

    override suspend fun searchSongs(query: String, limit: Int): List<Track> {
        if (query.isBlank()) return emptyList()

        return try {
            val result = client.search(query.trim(), SearchFilter.SONGS).getOrNull()
            val songs = result?.songs.orEmpty().take(limit)
            NodeLog.d(TAG, "Search returned ${songs.size} candidates (limit=$limit)")
            songs
        } catch (e: Exception) {
            NodeLog.w(TAG, "Search failed with exception: ${e.message}")
            emptyList()
        }
    }
}
