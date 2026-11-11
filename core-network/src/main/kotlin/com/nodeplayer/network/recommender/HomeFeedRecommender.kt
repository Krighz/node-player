package com.nodeplayer.network.recommender

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.YouTubeSection
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Local History-Weighted Recommender and Dismissal Engine.
 * - Scores local history tracks by play count, favorite status, and recency decay.
 * - Merges with region charts to build deterministic Quick Picks without random noise (`HOME-03`).
 * - Manages 'Not interested / hide this' dismissals with immediate filtering (`HOME-06`).
 * - Resolves device locale region with manual override (`HOME-05`).
 */
class HomeFeedRecommender {

    private val dismissedTrackIdentifiers = ConcurrentHashMap.newKeySet<String>()
    private val dismissedArtists = ConcurrentHashMap.newKeySet<String>()

    /**
     * Mark a track as "Not interested / hide this".
     * Immediately registers track identifier and artist demotion.
     */
    fun dismissTrack(track: Track) {
        val identifier = getTrackKey(track)
        dismissedTrackIdentifiers.add(identifier)
        if (track.artist.isNotBlank() && track.artist != "Unknown Artist") {
            dismissedArtists.add(track.artist.trim().lowercase())
        }
        NodeLog.d(TAG, "Dismissed track: '${track.title}' by '${track.artist}' (key=$identifier)")
    }

    /**
     * Mark an entire artist as not interested.
     */
    fun dismissArtist(artistName: String) {
        if (artistName.isNotBlank()) {
            dismissedArtists.add(artistName.trim().lowercase())
            NodeLog.d(TAG, "Dismissed artist: '$artistName'")
        }
    }

    fun isDismissed(track: Track): Boolean {
        if (dismissedTrackIdentifiers.contains(getTrackKey(track))) {
            return true
        }
        if (track.artist.isNotBlank() && dismissedArtists.contains(track.artist.trim().lowercase())) {
            return true
        }
        return false
    }

    fun filterDismissed(tracks: List<Track>): List<Track> {
        return tracks.filterNot { isDismissed(it) }
    }

    fun filterDismissedSections(sections: List<YouTubeSection>): List<YouTubeSection> {
        return sections.mapNotNull { section ->
            val cleanItems = filterDismissed(section.items)
            if (cleanItems.isNotEmpty()) {
                section.copy(items = cleanItems)
            } else null
        }
    }

    /**
     * Calculate deterministic recommendation score for a track.
     * Weights: (playCount * 2.0 + (if favorite 5.0 else 0.0)) * recencyMultiplier.
     */
    fun calculateScore(track: Track, now: Long = System.currentTimeMillis()): Float {
        if (isDismissed(track)) return -1f

        val baseScore = (track.playCount * 2.0f) + (if (track.isFavorite) 5.0f else 0.0f)
        val recencyMultiplier = calculateRecencyMultiplier(track.lastPlayedTimestamp, now)
        return (baseScore + 1.0f) * recencyMultiplier
    }

    /**
     * Build Quick Picks shelf for signed-out or offline mode.
     * Scores local history, takes the top non-dismissed tracks, and supplements with region chart tracks.
     */
    fun buildFallbackQuickPicks(
        recentTracks: List<Track>,
        fallbackRegionCharts: List<Track> = emptyList(),
        limit: Int = 12,
        now: Long = System.currentTimeMillis()
    ): List<Track> {
        val nonDismissedLocal = filterDismissed(recentTracks)
        val scoredLocal = nonDismissedLocal
            .map { it to calculateScore(it, now) }
            .sortedByDescending { it.second }
            .map { it.first }

        val pickedTracks = ArrayList<Track>(limit)
        val seenKeys = HashSet<String>()

        for (track in scoredLocal) {
            val key = getTrackKey(track)
            if (seenKeys.add(key)) {
                pickedTracks.add(track)
                if (pickedTracks.size >= limit) break
            }
        }

        if (pickedTracks.size < limit && fallbackRegionCharts.isNotEmpty()) {
            val nonDismissedCharts = filterDismissed(fallbackRegionCharts)
            for (chartTrack in nonDismissedCharts) {
                val key = getTrackKey(chartTrack)
                if (seenKeys.add(key)) {
                    pickedTracks.add(chartTrack)
                    if (pickedTracks.size >= limit) break
                }
            }
        }

        return pickedTracks
    }

    /**
     * Resolves the country code for regional trending/charts.
     */
    fun resolveRegion(manualOverride: String? = null): String {
        val override = manualOverride?.trim()?.uppercase()
        if (!override.isNullOrEmpty() && override.length == 2) {
            return override
        }
        val systemCountry = Locale.getDefault().country?.trim()?.uppercase()
        return if (!systemCountry.isNullOrEmpty() && systemCountry.length == 2) {
            systemCountry
        } else {
            "US"
        }
    }

    fun clearDismissals() {
        dismissedTrackIdentifiers.clear()
        dismissedArtists.clear()
    }

    private fun getTrackKey(track: Track): String {
        return if (track.path.isNotBlank()) track.path
        else if (track.id != 0L) track.id.toString()
        else "${track.title.lowercase()}_${track.artist.lowercase()}"
    }

    private fun calculateRecencyMultiplier(lastPlayed: Long, now: Long): Float {
        if (lastPlayed <= 0L) return 0.35f
        val diffMs = (now - lastPlayed).coerceAtLeast(0L)
        val oneHour = 3600_000L
        val oneDay = 24 * oneHour
        val oneWeek = 7 * oneDay
        val oneMonth = 30 * oneDay

        return when {
            diffMs < oneDay -> 1.0f
            diffMs < oneWeek -> 0.85f
            diffMs < oneMonth -> 0.65f
            else -> 0.40f
        }
    }

    companion object {
        private const val TAG = "nodeplayer/recommender"
    }
}
