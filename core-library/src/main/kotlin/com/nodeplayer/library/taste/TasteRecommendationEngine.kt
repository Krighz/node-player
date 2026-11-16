package com.nodeplayer.library.taste

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.MoodBucket
import com.nodeplayer.model.Track
import com.nodeplayer.model.bucketFor
import java.time.LocalDate
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
 * Result bundle for a generated taste station indicating whether fallback mode was triggered.
 */
data class TasteStation(
    val tracks: List<Track>,
    val isFallback: Boolean
)

/**
 * Configuration parameters for [TasteRecommendationEngine] per F01.
 */
data class TasteConfig(
    val useHistory: Boolean = true,
    val historyLimit: Int = 200,
    val recentExcludeCount: Int = 15,
    val topArtistCount: Int = 10,
    val seedArtistCount: Int = 3,
    val dailyMixTrackCount: Int = 12,
    val seedRadioTrackCount: Int = 13,
    val notInterestedVideoIds: Set<String> = emptySet(),
    val daySeed: Long = 0L
)

/**
 * Weighted sampling without replacement per F01.
 */
fun <T> weightedSampleWithoutReplacement(
    items: List<T>,
    weights: List<Double>,
    k: Int,
    random: Random = Random.Default
): List<T> {
    require(items.size == weights.size) { "items and weights must have identical sizes" }
    val pool = items.indices.toMutableList()
    val picked = ArrayList<T>(minOf(k, items.size))
    repeat(minOf(k, items.size)) {
        val total = pool.sumOf { weights[it] }
        var chosenPos = pool.lastIndex
        if (total > 0.0) {
            var r = random.nextDouble() * total
            for ((pos, i) in pool.withIndex()) {
                r -= weights[i]
                if (r <= 0.0) {
                    chosenPos = pos
                    break
                }
            }
        }
        picked += items[pool.removeAt(chosenPos)]
    }
    return picked
}

/**
 * Pure YouTube listening history taste recommendation engine.
 *
 * Implements:
 * - History ingestion exclusively from authentic YouTube history (zero synthetic mock data) per TASTE-01.
 * - History exclusion: excludes the last 15 played tracks (`entries.take(recentExcludeCount)`) per D-09 / F01.
 * - Top 10 artists ranked by play count with recency tie-breaking; 3 seed artists selected via weighted sampling.
 * - Seed radio queues drawn from `RDAMVM<videoId>`.
 * - Daily mix (~12 tracks) derived from user's history with stable intra-day seed.
 * - Dynamic 25-track session blend combining ~12 daily mix + ~13 seed radio tracks per D-12 / F01.
 * - Shuffled on app launch with injected random.
 * - Contextual time-of-day mood buckets per D-11 / F01.
 * - Graceful fallback to regional charts when history is empty, paused, or unavailable.
 * - Strict privacy: zero track titles or video IDs in logs.
 * - Pure Kotlin with zero `android.*` imports.
 */
class TasteRecommendationEngine(
    private val historySource: TasteHistorySource,
    private val random: Random = Random.Default
) {

    /**
     * Resolves the contextual time-of-day mood for the given [hour] (0-23).
     * If [hour] is null, uses the current system time hour.
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
     * Resolves the active [MoodBucket] for the given [hour] against [buckets].
     */
    fun resolveContextualMoodBucket(
        hour: Int? = null,
        buckets: List<MoodBucket> = MoodBucket.DEFAULT_BUCKETS
    ): MoodBucket? {
        val currentHour = hour ?: LocalTime.now().hour
        return bucketFor(currentHour, buckets)
    }

    /**
     * Generates a 25-track dynamic station blend derived from authentic YouTube history per F01.
     */
    suspend fun generateTasteStation(
        config: TasteConfig = TasteConfig(),
        daySeed: Long = LocalDate.now().toEpochDay()
    ): Result<List<Track>> = generateTasteStationDetailed(config, daySeed).map { it.tracks }

    /**
     * Generates a 25-track dynamic station blend with fallback state details per F01.
     */
    suspend fun generateTasteStationDetailed(
        config: TasteConfig = TasteConfig(),
        daySeed: Long = LocalDate.now().toEpochDay()
    ): Result<TasteStation> = runCatching {
        if (!config.useHistory) {
            NodeLog.i(TAG, "History recommendation disabled via config; falling back to top charts")
            val charts = historySource.getFallbackTopCharts().getOrDefault(emptyList())
            return@runCatching TasteStation(
                tracks = charts.distinctBy { it.id }.take(STATION_SIZE),
                isFallback = true
            )
        }

        val historyResult = historySource.recentHistory(config.historyLimit)
        val entries: List<HistoryEntry> = when (historyResult) {
            is HistoryResult.Success -> historyResult.entries
            is HistoryResult.Paused, is HistoryResult.Empty, is HistoryResult.NotSignedIn -> {
                NodeLog.i(TAG, "History query status indicates fallback: ${historyResult::class.simpleName}")
                emptyList()
            }
            is HistoryResult.Failure -> {
                // Fallback check: try legacy getListeningHistory in case source implements it
                val legacy = historySource.getListeningHistory().getOrNull().orEmpty()
                legacy.mapNotNull { track ->
                    val vid = track.extractVideoId() ?: return@mapNotNull null
                    HistoryEntry(
                        videoId = vid,
                        title = track.title,
                        artistName = track.artist,
                        durationMs = track.durationMs
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            NodeLog.i(TAG, "History entries empty; falling back to top charts")
            val charts = historySource.getFallbackTopCharts().getOrDefault(emptyList())
            return@runCatching TasteStation(
                tracks = charts.distinctBy { it.id }.take(STATION_SIZE),
                isFallback = true
            )
        }

        // Step 2: Exclude the recent tracks and "Not Interested" tracks
        val recentExcluded = entries.take(config.recentExcludeCount)
        val excludedVideoIds = (recentExcluded.map { it.videoId } + config.notInterestedVideoIds).toSet()

        val remainingEntries = entries.drop(config.recentExcludeCount)
            .filterNot { it.videoId in excludedVideoIds }

        if (remainingEntries.isEmpty()) {
            NodeLog.i(TAG, "Remaining history after exclusion is empty; falling back to top charts")
            val charts = historySource.getFallbackTopCharts().getOrDefault(emptyList())
            return@runCatching TasteStation(
                tracks = charts.filterNot { it.extractVideoId() in excludedVideoIds }
                    .distinctBy { it.id }
                    .take(STATION_SIZE),
                isFallback = true
            )
        }

        // Step 3: Top artists ranked by play count in remaining history (tie-break: most recent play)
        val artistPlayCounts = mutableMapOf<String, Int>()
        val artistFirstSeenIndex = mutableMapOf<String, Int>()
        remainingEntries.forEachIndexed { index, entry ->
            val artist = entry.artistName.trim()
            if (artist.isNotEmpty() && !artist.equals("Unknown Artist", ignoreCase = true) && !artist.equals("YouTube Music", ignoreCase = true)) {
                artistPlayCounts[artist] = (artistPlayCounts[artist] ?: 0) + 1
                if (artist !in artistFirstSeenIndex) {
                    artistFirstSeenIndex[artist] = index
                }
            }
        }

        val topArtists = artistPlayCounts.keys
            .sortedWith(
                compareByDescending<String> { artistPlayCounts[it] ?: 0 }
                    .thenBy { artistFirstSeenIndex[it] ?: Int.MAX_VALUE }
            )
            .take(config.topArtistCount)

        // Step 4: Sample seed artists with probability proportional to play count
        val artistWeights = topArtists.map { (artistPlayCounts[it] ?: 1).toDouble() }
        val seedArtists = weightedSampleWithoutReplacement(topArtists, artistWeights, config.seedArtistCount, random)

        // Step 5: Daily mix tracks from remaining history ranked by play count, picked with per-day seed
        val effectiveDaySeed = if (config.daySeed != 0L) config.daySeed else daySeed
        val dayRandom = Random(effectiveDaySeed)

        val remainingTrackCounts = mutableMapOf<String, Int>()
        val remainingTrackFirstSeen = mutableMapOf<String, Int>()
        remainingEntries.forEachIndexed { i, t ->
            remainingTrackCounts[t.videoId] = (remainingTrackCounts[t.videoId] ?: 0) + 1
            if (t.videoId !in remainingTrackFirstSeen) remainingTrackFirstSeen[t.videoId] = i
        }

        val rankedUniqueTracks = remainingEntries.distinctBy { it.videoId }
            .sortedWith(
                compareByDescending<HistoryEntry> { remainingTrackCounts[it.videoId] ?: 0 }
                    .thenBy { remainingTrackFirstSeen[it.videoId] ?: Int.MAX_VALUE }
            )

        val dailyMixPool = rankedUniqueTracks.map { it.toTrack() }
        val dailyMixTracks = if (dailyMixPool.size <= config.dailyMixTrackCount) {
            dailyMixPool
        } else {
            val weights = rankedUniqueTracks.map { (remainingTrackCounts[it.videoId] ?: 1).toDouble() }
            weightedSampleWithoutReplacement(dailyMixPool, weights, config.dailyMixTrackCount, dayRandom)
        }

        val takenVideoIds = (excludedVideoIds + dailyMixTracks.mapNotNull { it.extractVideoId() }).toMutableSet()

        // Step 6: For each seed artist pick its most-played track as seed and fetch radio queues
        val radioTracks = mutableListOf<Track>()
        if (seedArtists.isNotEmpty()) {
            val basePerSeed = config.seedRadioTrackCount / seedArtists.size
            val remainder = config.seedRadioTrackCount % seedArtists.size
            val seedQueues = mutableListOf<List<Track>>()

            for ((idx, seedArtist) in seedArtists.withIndex()) {
                val quota = basePerSeed + if (idx == 0) remainder else 0
                val artistTracks = remainingEntries.filter { it.artistName.equals(seedArtist, ignoreCase = true) }

                val trackPlayCounts = mutableMapOf<String, Int>()
                val trackFirstSeen = mutableMapOf<String, Int>()
                artistTracks.forEachIndexed { i, t ->
                    trackPlayCounts[t.videoId] = (trackPlayCounts[t.videoId] ?: 0) + 1
                    if (t.videoId !in trackFirstSeen) trackFirstSeen[t.videoId] = i
                }

                val seedTrack = artistTracks.distinctBy { it.videoId }
                    .sortedWith(
                        compareByDescending<HistoryEntry> { trackPlayCounts[it.videoId] ?: 0 }
                            .thenBy { trackFirstSeen[it.videoId] ?: Int.MAX_VALUE }
                    )
                    .firstOrNull()

                if (seedTrack != null) {
                    val queue = historySource.getRadioForTrack(seedTrack.videoId).getOrDefault(emptyList())
                    seedQueues.add(queue)
                    val eligible = queue.filter { track ->
                        val vid = track.extractVideoId()
                        vid != null && vid !in takenVideoIds
                    }
                    val taken = eligible.take(quota)
                    for (t in taken) {
                        t.extractVideoId()?.let { takenVideoIds.add(it) }
                        radioTracks.add(t)
                    }
                }
            }

            // Top-up if some seeds had fewer tracks than their quota
            if (radioTracks.size < config.seedRadioTrackCount) {
                for (queue in seedQueues) {
                    val eligible = queue.filter { track ->
                        val vid = track.extractVideoId()
                        vid != null && vid !in takenVideoIds
                    }
                    for (t in eligible) {
                        if (radioTracks.size >= config.seedRadioTrackCount) break
                        t.extractVideoId()?.let { takenVideoIds.add(it) }
                        radioTracks.add(t)
                    }
                }
            }
        }

        // Step 7: Blend to 25-track session mix (12 daily mix + 13 radio tracks)
        val blended = (dailyMixTracks + radioTracks).distinctBy { it.extractVideoId() ?: it.id.toString() }

        // Shuffle on app launch / generation using injected random
        val sessionMix = blended.shuffled(random).take(STATION_SIZE)

        // Step 8: If blend has tracks, return what exists (never pad with fake data); if zero, use fallback
        if (sessionMix.isEmpty()) {
            NodeLog.i(TAG, "Session mix is empty; falling back to top charts")
            val fallbackTracks = historySource.getFallbackTopCharts().map { charts ->
                charts.filterNot { it.extractVideoId() in excludedVideoIds }
                    .distinctBy { it.id }
                    .take(STATION_SIZE)
            }.getOrDefault(emptyList())
            TasteStation(tracks = fallbackTracks, isFallback = true)
        } else {
            TasteStation(tracks = sessionMix, isFallback = false)
        }
    }

    /**
     * Samples [count] distinct artists using weighted rank probability.
     */
    internal fun drawWeightedArtists(rankedArtists: List<String>, count: Int): List<String> {
        if (rankedArtists.isEmpty()) return emptyList()
        val weights = rankedArtists.indices.map { (rankedArtists.size - it).toDouble().coerceAtLeast(1.0) }
        return weightedSampleWithoutReplacement(rankedArtists, weights, count, random)
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

/**
 * Converts a [HistoryEntry] to a playable [Track].
 */
fun HistoryEntry.toTrack(): Track {
    val numericId = videoId.hashCode().toLong().let { if (it == Long.MIN_VALUE) 0L else kotlin.math.abs(it) }
    return Track(
        id = numericId,
        title = title,
        artist = artistName,
        album = "YouTube Music",
        durationMs = durationMs ?: 180_000L,
        path = "nodeplayer://youtube/$videoId",
        formatDescription = "YouTube Audio"
    )
}
