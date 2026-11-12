package com.nodeplayer.library.taste

import com.nodeplayer.model.Track

/**
 * Port interface for retrieving taste profile data, listening history, and radio tracks.
 *
 * Implemented in network or platform adapters (e.g. `:core-network`) without coupling
 * `:core-library` to concrete networking dependencies or Android framework APIs.
 */
interface TasteHistorySource {

    /**
     * Fetches the user's authentic YouTube Music listening history (`FEmusic_history`).
     * Returns a list of recently played [Track]s, newest first.
     */
    suspend fun getListeningHistory(): Result<List<Track>>

    /**
     * Fetches mixed recommendations or daily mix tracks from YouTube Music home feed.
     */
    suspend fun getDailyMixTracks(): Result<List<Track>>

    /**
     * Fetches the dynamic radio queue for a given [videoId] (e.g. `RDAMVM<videoId>`).
     */
    suspend fun getRadioForTrack(videoId: String): Result<List<Track>>

    /**
     * Fetches top charts or trending tracks from YouTube Music (`FEmusic_charts`)
     * as a fallback when listening history is empty or unavailable.
     */
    suspend fun getFallbackTopCharts(): Result<List<Track>>
}
