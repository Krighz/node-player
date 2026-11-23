package com.nodeplayer.library.playlist

import com.nodeplayer.model.Track

/**
 * Pure interface for querying online audio streaming catalogs during playlist track matching.
 * Implemented in :core-network by InnerTubeTrackSearchProvider, keeping :core-library decoupled from networking.
 */
fun interface OnlineTrackSearchProvider {

    /**
     * Searches the remote catalog for songs matching [query], returning up to [limit] candidates.
     */
    suspend fun searchSongs(query: String, limit: Int): List<Track>
}
