package com.nodeplayer.network.innertube

import com.nodeplayer.model.Track
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Intelligent matcher for music videos inspired by BitChord's TrackMatcher algorithm.
 * Decomposes track titles into core recording identity and listing packaging (e.g. "Official Music Video", "4K"),
 * validates artist overlap, checks version markers (remix, live, acoustic), and scores duration proximity.
 */
object VideoTrackMatcher {

    private val BRACKETED_REGEX = Pattern.compile("""[\(\[\{][^\(\)\[\]\{\}]*[\)\]\}]""")
    private val DASH_SPLIT_REGEX = Pattern.compile("""\s+[-–—|•/]\s+""")
    private val WHITESPACE_REGEX = Pattern.compile("""\s+""")
    private val NON_ALPHANUMERIC_REGEX = Pattern.compile("""[^a-zA-Z0-9\s]""")

    private val VIDEO_PACKAGING_TERMS = setOf(
        "official video",
        "official music video",
        "official audio",
        "official lyric video",
        "lyric video",
        "lyrics video",
        "lyrics",
        "visualizer",
        "music video",
        "video",
        "clip officiel",
        "video oficial",
        "videoclip",
        "4k",
        "hd",
        "uhd",
        "hq",
        "mv"
    )

    private val VERSION_TERMS = setOf(
        "remix",
        "acoustic",
        "live",
        "unplugged",
        "instrumental",
        "orchestral",
        "karaoke",
        "slowed",
        "reverb",
        "sped up",
        "extended",
        "radio edit",
        "cover"
    )

    data class TitleParts(
        val coreWords: List<String>,
        val versions: Set<String>
    )

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("""\p{M}"""), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    fun parseTitle(rawTitle: String): TitleParts {
        var clean = normalize(rawTitle)
        val versions = mutableSetOf<String>()

        // 1. Extract and process bracketed content (e.g. (Official Video), [Remix])
        val matcher = BRACKETED_REGEX.matcher(clean)
        val brackets = mutableListOf<String>()
        while (matcher.find()) {
            brackets.add(matcher.group())
        }

        for (b in brackets) {
            val content = b.removeSurrounding("(", ")")
                .removeSurrounding("[", "]")
                .removeSurrounding("{", "}")
                .trim()
            for (term in VERSION_TERMS) {
                if (content.contains(term)) versions.add(term)
            }
            clean = clean.replace(b, " ")
        }

        // 2. Check dash tails for packaging or versions (e.g. "Song - Official Video")
        val parts = DASH_SPLIT_REGEX.split(clean).map { it.trim() }
        val remainingParts = mutableListOf<String>()
        for (part in parts) {
            var isPackaging = false
            for (pkg in VIDEO_PACKAGING_TERMS) {
                if (part == pkg || part.startsWith("$pkg ") || part.endsWith(" $pkg")) {
                    isPackaging = true
                    break
                }
            }
            for (term in VERSION_TERMS) {
                if (part.contains(term)) versions.add(term)
            }
            if (!isPackaging) {
                remainingParts.add(part)
            }
        }

        clean = remainingParts.joinToString(" ")
        clean = NON_ALPHANUMERIC_REGEX.matcher(clean).replaceAll(" ")
        val coreWords = WHITESPACE_REGEX.split(clean).filter { it.isNotBlank() }

        return TitleParts(coreWords = coreWords, versions = versions)
    }

    /**
     * Scores a candidate music video Track against the target audio Track.
     * Returns null if candidate is rejected due to title divergence, artist mismatch, or duration mismatch.
     */
    fun scoreCandidate(wantedTitle: String, wantedArtist: String, wantedDurationMs: Long?, candidate: Track): Double? {
        val wantedParts = parseTitle(wantedTitle)
        val candidateParts = parseTitle(candidate.title)

        if (wantedParts.coreWords.isEmpty()) return null
        val wantedCoreSet = wantedParts.coreWords.toSet()
        val candidateCoreSet = candidateParts.coreWords.toSet()

        val matchingWords = wantedCoreSet.intersect(candidateCoreSet).size
        val overlapRatio = matchingWords.toDouble() / wantedCoreSet.size.toDouble()
        if (overlapRatio < 0.60) {
            return null
        }

        // Version tags must match (e.g. Acoustic vs Studio, Remix vs Original)
        if (wantedParts.versions != candidateParts.versions) {
            return null
        }

        // Artist check: at least part of the artist name must appear in candidate's artist or title
        val normWantedArtist = normalize(wantedArtist)
        val normCandidateArtist = normalize(candidate.artist)
        val normCandidateTitle = normalize(candidate.title)

        val artistTokens = normWantedArtist.split(Regex("""[,&/+]|\band\b|\bfeat\b|\bft\b"""))
            .map { it.trim() }
            .filter { it.length >= 2 }

        val artistMatches = artistTokens.any { token ->
            normCandidateArtist.contains(token) || normCandidateTitle.contains(token)
        }

        if (!artistMatches && normWantedArtist.isNotBlank() && normWantedArtist != "unknown artist") {
            return null
        }

        var score = 1000.0 * overlapRatio

        // Duration validation: music videos often have intros/outros (tolerance ±120s)
        if (wantedDurationMs != null && wantedDurationMs > 10_000L && candidate.durationMs > 10_000L) {
            val driftSec = abs(wantedDurationMs - candidate.durationMs) / 1000
            if (driftSec > 180) {
                // Reject hour loops or massive deviations
                return null
            }
            score -= driftSec * 1.5
        }

        // Reward explicit official video tags
        val candLower = candidate.title.lowercase(Locale.ROOT)
        if (candLower.contains("official video") || candLower.contains("official music video")) {
            score += 150.0
        }

        return score
    }

    /**
     * Finds the best matching music video track among a list of search candidates.
     */
    fun bestMatch(wantedTitle: String, wantedArtist: String, wantedDurationMs: Long?, candidates: List<Track>): Track? {
        return candidates
            .mapNotNull { candidate ->
                val score = scoreCandidate(wantedTitle, wantedArtist, wantedDurationMs, candidate)
                if (score != null) candidate to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }
}
