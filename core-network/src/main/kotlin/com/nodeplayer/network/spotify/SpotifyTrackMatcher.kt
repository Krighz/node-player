package com.nodeplayer.network.spotify

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.network.innertube.SearchFilter
import kotlin.math.abs

/**
 * Result of attempting to match a Spotify track against YouTube Music.
 */
sealed interface MatchResult {
    data class HighConfidence(
        val spotifyTrack: SpotifyTrack,
        val candidate: Track,
        val confidenceScore: Float
    ) : MatchResult

    data class Uncertain(
        val spotifyTrack: SpotifyTrack,
        val topCandidates: List<Track>
    ) : MatchResult

    data class NotFound(
        val spotifyTrack: SpotifyTrack
    ) : MatchResult
}

/**
 * Two-stage ISRC and text matching engine mapping Spotify metadata to playable YouTube Music tracks.
 * Conforms to SPOT-04 and SPOT-08.
 */
class SpotifyTrackMatcher(
    private val innerTubeClient: InnerTubeClient
) {

    /**
     * Attempts to find a matching YouTube Music track for [spotifyTrack].
     */
    suspend fun matchTrack(spotifyTrack: SpotifyTrack): MatchResult {
        // Stage 1: Try ISRC exact search if ISRC is available
        if (!spotifyTrack.isrc.isNullOrBlank()) {
            val isrcResult = searchByIsrc(spotifyTrack)
            if (isrcResult != null) {
                return isrcResult
            }
        }

        // Stage 2: Fuzzy normalized text search
        return searchByFuzzyText(spotifyTrack)
    }

    private suspend fun searchByIsrc(spotifyTrack: SpotifyTrack): MatchResult.HighConfidence? {
        val isrc = spotifyTrack.isrc ?: return null
        val searchRes = innerTubeClient.search(isrc, SearchFilter.SONGS).getOrNull() ?: return null
        val candidates = searchRes.songs

        for (candidate in candidates) {
            val score = scoreCandidate(spotifyTrack, candidate)
            if (score >= HIGH_CONFIDENCE_THRESHOLD) {
                NodeLog.d(TAG, "Matched '${spotifyTrack.title}' via ISRC '$isrc' -> '${candidate.title}' (score=$score)")
                return MatchResult.HighConfidence(spotifyTrack, candidate, score)
            }
        }
        return null
    }

    private suspend fun searchByFuzzyText(spotifyTrack: SpotifyTrack): MatchResult {
        val cleanTitle = cleanTrackTitle(spotifyTrack.title)
        val cleanArtist = cleanArtistName(spotifyTrack.primaryArtist)
        val query = "$cleanTitle $cleanArtist".trim()

        val searchRes = innerTubeClient.search(query, SearchFilter.SONGS).getOrNull()
        val candidates = searchRes?.songs.orEmpty()
        if (candidates.isEmpty()) {
            NodeLog.d(TAG, "No candidates found for '${spotifyTrack.title}' by '${spotifyTrack.primaryArtist}'")
            return MatchResult.NotFound(spotifyTrack)
        }

        val scored = candidates.map { candidate ->
            candidate to scoreCandidate(spotifyTrack, candidate)
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull()
        return if (best != null && best.second >= HIGH_CONFIDENCE_THRESHOLD) {
            NodeLog.d(TAG, "High confidence match for '${spotifyTrack.title}' -> '${best.first.title}' (score=${best.second})")
            MatchResult.HighConfidence(spotifyTrack, best.first, best.second)
        } else if (scored.isNotEmpty()) {
            NodeLog.d(TAG, "Uncertain match for '${spotifyTrack.title}' (${scored.size} candidates, top score=${best?.second})")
            MatchResult.Uncertain(spotifyTrack, scored.take(5).map { it.first })
        } else {
            MatchResult.NotFound(spotifyTrack)
        }
    }

    /**
     * Computes a match score from 0.0 to 1.0 based on duration proximity, artist match, and title token overlap.
     */
    fun scoreCandidate(spotify: SpotifyTrack, candidate: Track): Float {
        var score = 0f

        // 1. Duration proximity (±5 seconds threshold)
        if (spotify.durationMs > 0 && candidate.durationMs > 0) {
            val delta = abs(spotify.durationMs - candidate.durationMs)
            when {
                delta <= 2000L -> score += 0.35f
                delta <= 5000L -> score += 0.25f
                delta <= 10000L -> score += 0.10f
                else -> score -= 0.20f // significant duration divergence penalty
            }
        } else {
            score += 0.15f
        }

        // 2. Artist match
        val normSpotArtist = normalize(spotify.primaryArtist)
        val normCandArtist = normalize(candidate.artist)
        if (normSpotArtist == normCandArtist) {
            score += 0.30f
        } else if (normSpotArtist.isNotBlank() && (normCandArtist.contains(normSpotArtist) || normSpotArtist.contains(normCandArtist))) {
            score += 0.20f
        } else {
            val allSpotArtists = spotify.artists.map { normalize(it) }
            if (allSpotArtists.any { it.isNotBlank() && normCandArtist.contains(it) }) {
                score += 0.20f
            }
        }

        // 3. Title token overlap
        val cleanSpotTitle = cleanTrackTitle(spotify.title)
        val cleanCandTitle = cleanTrackTitle(candidate.title)
        val spotTokens = tokenize(cleanSpotTitle)
        val candTokens = tokenize(cleanCandTitle)

        if (spotTokens.isNotEmpty() && candTokens.isNotEmpty()) {
            val intersection = spotTokens.intersect(candTokens).size
            val union = spotTokens.union(candTokens).size
            val jaccard = intersection.toFloat() / union.toFloat()
            score += (jaccard * 0.35f)
        }

        return score.coerceIn(0f, 1f)
    }

    fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("""\s*\(.*?\)\s*"""), " ") // remove parenthetical suffixes e.g. (Remastered 2021)
            .replace(Regex("""\s*\[.*?\]\s*"""), " ") // remove brackets e.g. [feat. Drake]
            .replace(Regex("""\s*-\s*(Remastered|Deluxe|Live|Single|Bonus|Radio Edit).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*feat(\.|\s).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*ft(\.|\s).*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("""\s*,.*"""), "") // take primary before comma
            .replace(Regex("""\s*feat(\.|\s).*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun normalize(str: String): String {
        return str.lowercase().replace(Regex("""[^a-z0-9]"""), "")
    }

    private fun tokenize(str: String): Set<String> {
        return str.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.isNotBlank() && it !in STOP_WORDS }
            .toSet()
    }

    companion object {
        private const val TAG = "spotify-matcher"
        const val HIGH_CONFIDENCE_THRESHOLD = 0.65f

        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "in", "on", "at", "to", "for", "with", "of", "remastered", "version"
        )
    }
}
