package com.nodeplayer.library.taste

import com.nodeplayer.model.Track

/**
 * Single entry from authentic YouTube Music listening history (`FEmusic_history`).
 */
data class HistoryEntry(
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val durationMs: Long? = null
)

/**
 * Result of querying listening history from the account per F01.
 */
sealed interface HistoryResult {
    data class Success(val entries: List<HistoryEntry>) : HistoryResult // Newest first
    data object Empty : HistoryResult
    data object Paused : HistoryResult // History turned off on the account
    data object NotSignedIn : HistoryResult
    data class Failure(val cause: Throwable) : HistoryResult
}

/**
 * Port interface for retrieving taste profile data, listening history, and radio tracks.
 *
 * Implemented in network adapters (e.g. `:core-network`) without coupling `:core-library`
 * to concrete networking dependencies or Android framework APIs.
 */
interface TasteHistorySource {

    /**
     * Retrieves recent authentic YouTube Music listening history up to [limit] entries per F01.
     */
    suspend fun recentHistory(limit: Int): HistoryResult

    /**
     * Backward-compatible helper fetching the user's recently played [Track]s, newest first.
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
     * as a fallback when listening history is empty, paused, or unavailable.
     */
    suspend fun getFallbackTopCharts(): Result<List<Track>>
}
