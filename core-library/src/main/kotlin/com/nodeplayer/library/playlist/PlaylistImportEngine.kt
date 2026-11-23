package com.nodeplayer.library.playlist

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import com.nodeplayer.model.playlist.ImportConfig
import com.nodeplayer.model.playlist.ImportMatchStatus
import com.nodeplayer.model.playlist.ImportProgressStep
import com.nodeplayer.model.playlist.ImportResultItem
import com.nodeplayer.model.playlist.ParsedTrackCandidate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Normalizer
import kotlin.math.abs

/**
 * Pure matching and scoring engine for imported playlist candidates.
 * Executes two-stage matching: in-memory local track catalog index, followed by
 * rate-controlled online searches for unmatched items.
 */
object PlaylistImportEngine {

    private const val TAG = "playlist/import-engine"
    private val DIACRITICS_REGEX = Regex("""\p{M}+""")
    private val BRACKETS_REGEX = Regex("""\(.*?\)|\{.*?\}|\[.*?]""")
    private val FEAT_REGEX = Regex("""\b(feat|ft|featuring)\b\.?.*""", RegexOption.IGNORE_CASE)
    private val NON_ALPHANUM_REGEX = Regex("""[^\p{L}\p{N} ]""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    /**
     * Normalizes a title or artist string by lowercasing, stripping diacritics/accents,
     * removing "(feat. X)" or "[Remastered]" blocks, eliminating punctuation, and trimming spaces.
     */
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .replace(BRACKETS_REGEX, " ")
            .replace(FEAT_REGEX, " ")
            .replace(NON_ALPHANUM_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    /**
     * Calculates the token overlap ratio between two strings [0.0f .. 1.0f].
     */
    fun tokenOverlap(a: String, b: String): Float {
        val tokensA = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        val common = tokensA.intersect(tokensB).size
        val maxTokens = maxOf(tokensA.size, tokensB.size)
        return common.toFloat() / maxTokens
    }

    /**
     * Computes the confidence match score [0.0f .. 1.0f] between an input candidate and a catalog track.
     * Evaluates ISRC identity, normalized title equality, artist overlap, and duration proximity.
     */
    fun scoreCandidate(
        input: ParsedTrackCandidate,
        candidate: Track,
        durationWindowMs: Long = 3000L
    ): Float {
        // Direct ISRC match (1.0)
        val candIsrc = candidate.isrc
        val inputIsrc = input.isrc
        if (!inputIsrc.isNullOrBlank() && !candIsrc.isNullOrBlank()) {
            if (inputIsrc.trim().equals(candIsrc.trim(), ignoreCase = true)) {
                return 1.0f
            }
        }

        val normInputTitle = normalize(input.title)
        val normCandTitle = normalize(candidate.title)
        if (normInputTitle.isEmpty() || normCandTitle.isEmpty()) return 0.0f

        val titleExactMatch = normInputTitle == normCandTitle
        val titleFuzzyOverlap = if (!titleExactMatch) tokenOverlap(normInputTitle, normCandTitle) else 1.0f

        val normInputArtist = normalize(input.artist)
        val normCandArtist = normalize(candidate.artist)
        val artistExactMatch = normInputArtist.isNotEmpty() && normInputArtist == normCandArtist
        val artistOverlap = if (normInputArtist.isNotEmpty() && normCandArtist.isNotEmpty()) {
            tokenOverlap(normInputArtist, normCandArtist)
        } else {
            0.0f
        }
        val artistAcceptable = artistExactMatch || artistOverlap >= 0.6f

        val hasBothDurations = input.durationMs > 0L && candidate.durationMs > 0L
        val durationWithinWindow = if (hasBothDurations) {
            abs(candidate.durationMs - input.durationMs) <= durationWindowMs
        } else {
            false
        }

        return when {
            // Title exact and artist acceptable
            titleExactMatch && artistAcceptable -> {
                when {
                    hasBothDurations && durationWithinWindow -> 0.95f
                    hasBothDurations && !durationWithinWindow -> 0.75f
                    else -> 0.85f // No duration to verify, but strong title & artist
                }
            }
            // Title exact, artist not specified in input
            titleExactMatch && normInputArtist.isEmpty() -> {
                if (hasBothDurations && durationWithinWindow) 0.90f else 0.80f
            }
            // Title exact, but artist mismatch
            titleExactMatch && !artistAcceptable -> 0.65f
            // Fuzzy title match with acceptable artist
            titleFuzzyOverlap >= 0.7f && artistAcceptable -> {
                if (hasBothDurations && durationWithinWindow) 0.80f else 0.65f
            }
            // Fuzzy title without artist
            titleFuzzyOverlap >= 0.7f -> 0.50f
            else -> 0.0f
        }
    }

    /**
     * Classifies a candidate score into an ImportMatchStatus according to [config] thresholds.
     */
    fun classifyScore(score: Float, config: ImportConfig): ImportMatchStatus = when {
        score >= config.autoAcceptThreshold -> ImportMatchStatus.MATCHED
        score >= config.reviewMinThreshold -> ImportMatchStatus.NEEDS_REVIEW
        else -> ImportMatchStatus.NOT_FOUND
    }

    /**
     * Executes the full two-stage playlist candidate matching pipeline.
     */
    suspend fun matchCandidates(
        candidates: List<ParsedTrackCandidate>,
        localCatalog: List<Track>,
        searchProvider: OnlineTrackSearchProvider? = null,
        config: ImportConfig = ImportConfig(),
        onProgress: (step: ImportProgressStep, current: Int, total: Int) -> Unit = { _, _, _ -> }
    ): List<ImportResultItem> {
        val total = candidates.size
        NodeLog.d(TAG, "Starting playlist match for $total candidates (local catalog size: ${localCatalog.size})")

        // Step 1: Build in-memory normalized title index and ISRC index for local tracks
        onProgress(ImportProgressStep.MATCHING_LOCAL, 0, total)
        val titleIndex = HashMap<String, MutableList<Track>>()
        val isrcIndex = HashMap<String, Track>()

        for (track in localCatalog) {
            val normTitle = normalize(track.title)
            if (normTitle.isNotEmpty()) {
                titleIndex.getOrPut(normTitle) { ArrayList() }.add(track)
            }
            val trackIsrc = track.isrc
            if (!trackIsrc.isNullOrBlank()) {
                isrcIndex[trackIsrc.trim().lowercase()] = track
            }
        }

        val results = ArrayList<ImportResultItem>(total)
        val unmatchedIndices = ArrayList<Int>()

        // Pass 1: Local catalog matching
        for ((idx, candidate) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()

            // 1a: Try direct ISRC match
            val isrcMatch = candidate.isrc?.trim()?.lowercase()?.let { isrcIndex[it] }
            if (isrcMatch != null) {
                results.add(
                    ImportResultItem(
                        candidate = candidate,
                        matchedTrack = isrcMatch,
                        confidenceScore = 1.0f,
                        status = ImportMatchStatus.MATCHED,
                        alternatives = listOf(isrcMatch),
                        isAccepted = true
                    )
                )
                onProgress(ImportProgressStep.MATCHING_LOCAL, idx + 1, total)
                continue
            }

            // 1b: Search local candidates by normalized title
            val normTitle = normalize(candidate.title)
            val potentialTracks = titleIndex[normTitle] ?: emptyList()

            var bestLocalTrack: Track? = null
            var bestLocalScore = 0.0f
            val scoredAlternatives = ArrayList<Pair<Track, Float>>()

            for (cand in potentialTracks) {
                val score = scoreCandidate(candidate, cand, config.durationWindowMs)
                if (score > 0.0f) {
                    scoredAlternatives.add(cand to score)
                    if (score > bestLocalScore) {
                        bestLocalScore = score
                        bestLocalTrack = cand
                    }
                }
            }

            if (bestLocalScore >= config.autoAcceptThreshold && bestLocalTrack != null) {
                val sortedAlts = scoredAlternatives.sortedByDescending { it.second }.map { it.first }.take(5)
                results.add(
                    ImportResultItem(
                        candidate = candidate,
                        matchedTrack = bestLocalTrack,
                        confidenceScore = bestLocalScore,
                        status = ImportMatchStatus.MATCHED,
                        alternatives = sortedAlts,
                        isAccepted = true
                    )
                )
            } else {
                // Keep placeholder, schedule for remote online search pass
                val sortedAlts = scoredAlternatives.sortedByDescending { it.second }.map { it.first }.take(5)
                val status = classifyScore(bestLocalScore, config)
                results.add(
                    ImportResultItem(
                        candidate = candidate,
                        matchedTrack = bestLocalTrack,
                        confidenceScore = bestLocalScore,
                        status = status,
                        alternatives = sortedAlts,
                        isAccepted = (status == ImportMatchStatus.MATCHED)
                    )
                )
                unmatchedIndices.add(idx)
            }

            onProgress(ImportProgressStep.MATCHING_LOCAL, idx + 1, total)
        }

        NodeLog.d(TAG, "Local match completed. Unmatched or review candidates: ${unmatchedIndices.size} of $total")

        // Pass 2: Online catalog search for unmatched items if search provider exists
        if (searchProvider != null && unmatchedIndices.isNotEmpty()) {
            val semaphore = Semaphore(config.onlineConcurrency.coerceIn(1, 4))
            val onlineTotal = unmatchedIndices.size
            var completedOnline = 0
            onProgress(ImportProgressStep.MATCHING_ONLINE, 0, onlineTotal)

            for (targetIdx in unmatchedIndices) {
                currentCoroutineContext().ensureActive()
                val candidate = results[targetIdx].candidate
                val localBest = results[targetIdx].matchedTrack
                val localScore = results[targetIdx].confidenceScore

                val onlineQuery = buildSearchQuery(candidate)
                val onlineCandidates = try {
                    semaphore.withPermit {
                        delay(60) // Polite throttle between search calls
                        searchProvider.searchSongs(onlineQuery, limit = 5)
                    }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    emptyList()
                }

                var bestScore = localScore
                var bestTrack = localBest
                val allAlternatives = ArrayList<Pair<Track, Float>>()
                if (localBest != null && localScore > 0f) {
                    allAlternatives.add(localBest to localScore)
                }

                for (remoteTrack in onlineCandidates) {
                    val score = scoreCandidate(candidate, remoteTrack, config.durationWindowMs)
                    if (score > 0f) {
                        allAlternatives.add(remoteTrack to score)
                        if (score > bestScore) {
                            bestScore = score
                            bestTrack = remoteTrack
                        }
                    }
                }

                val sortedAlternatives = allAlternatives
                    .distinctBy { it.first.id }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .take(5)

                val status = classifyScore(bestScore, config)
                results[targetIdx] = results[targetIdx].copy(
                    matchedTrack = bestTrack,
                    confidenceScore = bestScore,
                    status = status,
                    alternatives = sortedAlternatives,
                    isAccepted = (status == ImportMatchStatus.MATCHED)
                )

                completedOnline++
                onProgress(ImportProgressStep.MATCHING_ONLINE, completedOnline, onlineTotal)
            }
        }

        val matchedCount = results.count { it.status == ImportMatchStatus.MATCHED }
        val reviewCount = results.count { it.status == ImportMatchStatus.NEEDS_REVIEW }
        val notFoundCount = results.count { it.status == ImportMatchStatus.NOT_FOUND }
        NodeLog.i(TAG, "Matching finished: $matchedCount matched, $reviewCount review, $notFoundCount not found (total $total)")

        onProgress(ImportProgressStep.DONE, total, total)
        return results
    }

    private fun buildSearchQuery(candidate: ParsedTrackCandidate): String {
        return if (candidate.artist.isNotBlank()) {
            "${candidate.artist} ${candidate.title}".trim()
        } else {
            candidate.title.trim()
        }
    }
}
