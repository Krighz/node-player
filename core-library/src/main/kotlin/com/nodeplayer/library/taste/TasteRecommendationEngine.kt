package com.nodeplayer.library.taste

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import java.time.LocalTime
import kotlin.random.Random

/**
 * Contextual time-of-day mood category per D-11 and Section 4.2.
 */
sealed class ContextualMood(val label: String, val subtitle: String) {
    class Morning(label: String = "Morning Energy", subtitle: String = "Wake up & commute") : ContextualMood(label, subtitle)
    class Focus(label: String = "Deep Focus", subtitle: String = "Work & study flow") : ContextualMood(label, subtitle)
    class Chill(label: String = "Evening Chill", subtitle: String = "Unwind & relax") : ContextualMood(label, subtitle)
    class LateNight(label: String = "Late Night Ambient", subtitle: String = "Deep sleep & calm") : ContextualMood(label, subtitle)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContextualMood) return false
        return javaClass == other.javaClass && label == other.label && subtitle == other.subtitle
    }

    override fun hashCode(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + subtitle.hashCode()
        return result
    }

    override fun toString(): String = "${javaClass.simpleName}(label='$label', subtitle='$subtitle')"
}

/**
 * Pure YouTube listening history taste recommendation engine.
 *
 * Implements:
 * - History ingestion exclusively from authentic YouTube history (zero synthetic mock data) per TASTE-01.
 * - History exclusion: excludes the last 15 played tracks (`history.take(15)`) per D-09.
 * - Top 10 artists ranked by play count with weighted probability sampling (rank 1 weight = 10 ... rank 10 = 1).
 * - 3-artist seed selection drawing ~13 tracks from `RDAMVM<videoId>` radio queues.
 * - Dynamic 25-track station blend combining ~12 daily mix tracks + ~13 seed radio tracks per D-12.
 * - Contextual time-of-day moods per D-11.
 * - Graceful fallback to top charts when history is empty or paused per D-10, T-01-08.
 * - Pure Kotlin with zero `android.*` imports.
 */
class TasteRecommendationEngine(
    private val historySource: TasteHistorySource,
    private val random: Random = Random.Default
) {

    /**
     * Resolves the contextual time-of-day mood for the given [hour] (0-23).
     * If [hour] is null, uses the current system time hour.
     *
     * - 05:00 - 11:59 -> Morning Energy
     * - 12:00 - 16:59 -> Deep Focus
     * - 17:00 - 21:59 -> Evening Chill
     * - 22:00 - 04:59 -> Late Night Ambient
     */
    fun resolveContextualMood(hour: Int? = null): ContextualMood {
        val currentHour = hour ?: LocalTime.now().hour
        return when (currentHour) {
            in 5..11 -> ContextualMood.Morning()
            in 12..16 -> ContextualMood.Focus()
            in 17..21 -> ContextualMood.Chill()
            else -> ContextualMood.LateNight()
        }
    }

    /**
     * Generates a 25-track dynamic station blend derived from authentic YouTube history.
     */
    suspend fun generateTasteStation(): Result<List<Track>> = runCatching {
        val historyResult = historySource.getListeningHistory()
        val rawHistory = historyResult.getOrNull().orEmpty()

        // Fallback per D-10 & T-01-08: If history is empty or fails, fallback to top charts
        if (rawHistory.isEmpty()) {
            NodeLog.i(TAG, "Listening history is empty or paused; falling back to top charts per D-10")
            return historySource.getFallbackTopCharts().map { charts ->
                charts.distinctBy { it.id }.take(STATION_SIZE)
            }
        }

        // Exclusion filter per D-09: Exclude the last 15 played tracks
        val excludedRecent = rawHistory.take(EXCLUSION_LIMIT)
        val excludedVideoIds = excludedRecent.mapNotNull { it.extractVideoId() }.toSet()
        val excludedTrackIds = excludedRecent.map { it.id }.toSet()

        val remainingHistory = rawHistory.drop(EXCLUSION_LIMIT)
        if (remainingHistory.isEmpty()) {
            NodeLog.i(TAG, "Remaining history after exclusion is empty; falling back to daily mix / charts")
            val fallbackTracks = historySource.getDailyMixTracks()
                .recoverCatching { historySource.getFallbackTopCharts().getOrThrow() }
                .getOrDefault(emptyList())

            return Result.success(
                fallbackTracks.filterNot { it.id in excludedTrackIds || it.extractVideoId() in excludedVideoIds }
                    .distinctBy { it.id }
                    .take(STATION_SIZE)
            )
        }

        // Top artist ranking by play count from remaining history
        val artistFrequencies = mutableMapOf<String, Int>()
        for (track in remainingHistory) {
            val artist = track.artist.trim()
            if (artist.isNotEmpty() && !artist.equals("Unknown Artist", ignoreCase = true) && !artist.equals("YouTube Music", ignoreCase = true)) {
                artistFrequencies[artist] = (artistFrequencies[artist] ?: 0) + 1
            }
        }

        val topArtists = artistFrequencies.entries
            .sortedByDescending { it.value }
            .take(TOP_ARTISTS_LIMIT)
            .map { it.key }

        // Weighted 3-artist random draw
        val selectedArtists = drawWeightedArtists(topArtists, SEED_ARTISTS_COUNT)

        // Find a seed track for each selected artist from remaining history
        val seedTracks = selectedArtists.mapNotNull { artist ->
            remainingHistory.firstOrNull { it.artist.equals(artist, ignoreCase = true) && it.extractVideoId() != null }
        }

        // Fetch seed radio queues
        val radioTracks = mutableListOf<Track>()
        for (seed in seedTracks) {
            val videoId = seed.extractVideoId() ?: continue
            val radioResult = historySource.getRadioForTrack(videoId)
            val queue = radioResult.getOrDefault(emptyList())
            radioTracks.addAll(queue)
        }

        // Fetch daily mix tracks
        val dailyMixResult = historySource.getDailyMixTracks()
        val dailyMix = dailyMixResult.getOrDefault(emptyList())

        // Blend station queue per D-12: ~12 daily mix + ~13 seed radio tracks, total 25
        val station = blendStationTracks(
            dailyMix = dailyMix,
            radioTracks = radioTracks,
            excludedTrackIds = excludedTrackIds,
            excludedVideoIds = excludedVideoIds,
            targetSize = STATION_SIZE,
            dailyMixTarget = DAILY_MIX_TARGET
        )

        // If station has fewer than 25 tracks due to heavy exclusions, top up from fallback charts
        if (station.size < STATION_SIZE) {
            val needed = STATION_SIZE - station.size
            val existingIds = station.map { it.id }.toSet()
            val existingVideoIds = station.mapNotNull { it.extractVideoId() }.toSet()
            val charts = historySource.getFallbackTopCharts().getOrDefault(emptyList())
            val topUps = charts.filterNot {
                it.id in excludedTrackIds ||
                    it.extractVideoId() in excludedVideoIds ||
                    it.id in existingIds ||
                    it.extractVideoId() in existingVideoIds
            }.take(needed)

            (station + topUps).take(STATION_SIZE)
        } else {
            station
        }
    }

    /**
     * Samples [count] distinct artists using weighted rank probability.
     * Rank 1 weight = 10, Rank 2 = 9, ..., Rank 10 = 1.
     */
    internal fun drawWeightedArtists(rankedArtists: List<String>, count: Int): List<String> {
        if (rankedArtists.isEmpty()) return emptyList()
        if (rankedArtists.size <= count) return rankedArtists

        val available = rankedArtists.toMutableList()
        val chosen = mutableListOf<String>()

        while (chosen.size < count && available.isNotEmpty()) {
            val weights = available.map { artist ->
                val originalRankIndex = rankedArtists.indexOf(artist).coerceIn(0, TOP_ARTISTS_LIMIT - 1)
                (TOP_ARTISTS_LIMIT - originalRankIndex).coerceAtLeast(1)
            }
            val totalWeight = weights.sum()
            var roll = random.nextInt(totalWeight)

            var selectedIndex = 0
            for (i in weights.indices) {
                roll -= weights[i]
                if (roll < 0) {
                    selectedIndex = i
                    break
                }
            }
            chosen.add(available.removeAt(selectedIndex))
        }

        return chosen
    }

    /**
     * Blends tracks from daily mix and artist radio queues, filtering out recent exclusions and duplicates.
     */
    private fun blendStationTracks(
        dailyMix: List<Track>,
        radioTracks: List<Track>,
        excludedTrackIds: Set<Long>,
        excludedVideoIds: Set<String>,
        targetSize: Int,
        dailyMixTarget: Int
    ): List<Track> {
        val seenTrackIds = mutableSetOf<Long>()
        val seenVideoIds = mutableSetOf<String>()

        fun isEligible(track: Track): Boolean {
            val videoId = track.extractVideoId()
            if (track.id in excludedTrackIds || track.id in seenTrackIds) return false
            if (videoId != null && (videoId in excludedVideoIds || videoId in seenVideoIds)) return false
            return true
        }

        fun accept(track: Track) {
            seenTrackIds.add(track.id)
            track.extractVideoId()?.let { seenVideoIds.add(it) }
        }

        val eligibleDailyMix = dailyMix.filter { isEligible(it) }
        val dailyChosen = mutableListOf<Track>()
        for (track in eligibleDailyMix) {
            if (dailyChosen.size >= dailyMixTarget) break
            if (isEligible(track)) {
                accept(track)
                dailyChosen.add(track)
            }
        }

        val radioTarget = targetSize - dailyChosen.size
        val eligibleRadio = radioTracks.filter { isEligible(it) }
        val radioChosen = mutableListOf<Track>()
        for (track in eligibleRadio) {
            if (radioChosen.size >= radioTarget) break
            if (isEligible(track)) {
                accept(track)
                radioChosen.add(track)
            }
        }

        // Interleave daily mix and radio tracks for a balanced listening experience
        val result = mutableListOf<Track>()
        var dIndex = 0
        var rIndex = 0

        while (result.size < targetSize && (dIndex < dailyChosen.size || rIndex < radioChosen.size)) {
            if (dIndex < dailyChosen.size) {
                result.add(dailyChosen[dIndex++])
            }
            if (rIndex < radioChosen.size && result.size < targetSize) {
                result.add(radioChosen[rIndex++])
            }
        }

        return result
    }

    private fun Track.extractVideoId(): String? {
        if (path.startsWith("nodeplayer://youtube/")) {
            return path.removePrefix("nodeplayer://youtube/").takeIf { it.isNotBlank() }
        }
        return null
    }

    companion object {
        private const val TAG = "TasteRecommendationEngine"
        const val EXCLUSION_LIMIT = 15
        const val TOP_ARTISTS_LIMIT = 10
        const val SEED_ARTISTS_COUNT = 3
        const val STATION_SIZE = 25
        const val DAILY_MIX_TARGET = 12
    }
}
