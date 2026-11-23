package com.nodeplayer.network.innertube

import com.nodeplayer.model.Track
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Intelligent matcher for music videos inspired by BitChord's TrackMatcher algorithm.
 * Decomposes track titles into core recording identity and packaging, validates artist overlap,
 * strictly rejects lyric/karaoke/visualizer videos in favor of official music videos, and applies
 * BitChord's duration tolerance allowing narrative/cinematic intros/outros up to 180s without penalty.
 */
object VideoTrackMatcher {

    private val BRACKETED_REGEX = Pattern.compile("""[\(\[\{][^\(\)\[\]\{\}]*[\)\]\}]""")
    private val DASH_SPLIT_REGEX = Pattern.compile("""\s+[-–—|•/]\s+""")
    private val WHITESPACE_REGEX = Pattern.compile("""\s+""")
    private val NON_ALPHANUMERIC_REGEX = Pattern.compile("""[^a-zA-Z0-9\s]""")

    private val YOUTUBE_TYPE_LABELS = setOf(
        "song", "video", "album", "single", "ep", "artist", "playlist", "podcast", "episode"
    )

    private val NOISE_WORDS = setOf(
        "official", "video", "audio", "lyrics", "lyric", "lyrical", "visualizer",
        "song", "songs", "full", "music", "the", "and", "from", "feat", "ft",
        "featuring", "with", "new", "latest", "free", "download", "remaster",
        "remastered", "explicit", "clean", "bonus", "track", "deluxe", "original",
        "album", "single", "hd", "hq", "4k", "mp3", "mv"
    )

    private val VERSION_TERMS = setOf(
        "remix", "remixes", "rmx", "refix", "flip", "bootleg", "mashup", "medley",
        "live", "concert", "unplugged", "acoustic", "instrumental", "karaoke",
        "vocals", "vocal", "acapella", "acappella", "backing", "stems", "stem",
        "cover", "demo", "reprise", "remake", "rework", "extended", "edit",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "symphonic"
    )

    private val ANTI_VIDEO_TERMS = setOf(
        "lyrics", "lyric", "lyrical", "lyric video", "lyrics video",
        "official lyric video", "official lyrics video", "audio", "official audio",
        "audio only", "visualizer", "official visualizer", "karaoke", "instrumental", "cover"
    )

    private val OFFICIAL_VIDEO_TERMS = listOf(
        "official music video", "official video", "music video", "video oficial",
        "clip officiel", "m/v", "[mv]", "(mv)", "videoclip"
    )

    data class TitleParts(
        val coreWords: List<String>,
        val versions: Set<String>,
        val rawClean: String
    )

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("""\p{M}"""), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    /** Extracts clean searchable title without noise packaging per BitChord's searchableTitle. */
    fun searchableTitle(title: String, artist: String = ""): String {
        val parts = parseTitle(title)
        val core = parts.coreWords.filter { it !in NOISE_WORDS }
        return (core + parts.versions).joinToString(" ").ifBlank { normalize(title) }
    }

    /** Extracts primary artist name, skipping YouTube type badges per BitChord's primaryArtist. */
    fun primaryArtist(artist: String): String {
        val norm = normalize(artist)
        val primary = norm.split(Regex("""[,&/+]|\band\b|\bfeat\b|\bft\b""")).firstOrNull()?.trim().orEmpty()
        return if (primary.lowercase(Locale.ROOT) in YOUTUBE_TYPE_LABELS) "" else primary
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
            var isNoise = false
            for (noise in NOISE_WORDS) {
                if (part == noise || part.startsWith("$noise ") || part.endsWith(" $noise")) {
                    isNoise = true
                    break
                }
            }
            for (term in VERSION_TERMS) {
                if (part.contains(term)) versions.add(term)
            }
            if (!isNoise) {
                remainingParts.add(part)
            }
        }

        clean = remainingParts.joinToString(" ")
        val rawClean = clean
        clean = NON_ALPHANUMERIC_REGEX.matcher(clean).replaceAll(" ")
        val coreWords = WHITESPACE_REGEX.split(clean).filter { it.isNotBlank() }

        return TitleParts(coreWords = coreWords, versions = versions, rawClean = rawClean)
    }

    /**
     * Scores a candidate music video Track against the target audio Track.
     * Implements BitChord's duration tolerance (cinematic intro/outro up to 180s without penalty when artist matches),
     * strict anti-lyric/anti-audio rejection, and official music video bonuses.
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

        val candLower = candidate.title.lowercase(Locale.ROOT)
        val candArtistLower = candidate.artist.lowercase(Locale.ROOT)

        // Strict Anti-Lyric & Anti-Audio Penalty:
        // A video containing "lyrics", "audio", "visualizer", or "karaoke" must NEVER beat an official music video.
        var isAntiVideo = false
        for (anti in ANTI_VIDEO_TERMS) {
            if (candLower.contains(anti)) {
                isAntiVideo = true
                break
            }
        }

        // Artist check: at least part of the artist name must appear in candidate's artist or title
        val normWantedArtist = normalize(wantedArtist)
        val artistTokens = normWantedArtist.split(Regex("""[,&/+]|\band\b|\bfeat\b|\bft\b"""))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in YOUTUBE_TYPE_LABELS }

        val artistMatches = artistTokens.any { token ->
            candArtistLower.contains(token) || candLower.contains(token)
        }

        if (!artistMatches && normWantedArtist.isNotBlank() && normWantedArtist != "unknown artist" && normWantedArtist !in YOUTUBE_TYPE_LABELS) {
            return null
        }

        var score = 1000.0 * overlapRatio

        // Penalize lyric/audio videos heavily (-800) so official music videos always win
        if (isAntiVideo) {
            score -= 800.0
        }

        // BitChord Duration Tolerance:
        // A music video often includes a visual/narrative intro or outro (e.g. Blinding Lights 4:30 vs 3:20).
        // When the artist matches, drift up to 180 seconds carries 0 penalty!
        if (wantedDurationMs != null && wantedDurationMs > 10_000L && candidate.durationMs > 10_000L) {
            val driftSec = (candidate.durationMs - wantedDurationMs) / 1000
            if (driftSec < -30) {
                // Video is significantly shorter than the song (cut off or preview)
                score -= abs(driftSec) * 3.0
            } else if (driftSec > 180) {
                // Excessively long (movie version or loop)
                score -= (driftSec - 180) * 2.0
            }
            // Drift between -30s and +180s gets 0 penalty (BitChord allowVideoDrift rule)
        }

        // Reward official music video markers (+450)
        for (official in OFFICIAL_VIDEO_TERMS) {
            if (candLower.contains(official)) {
                score += 450.0
                break
            }
        }

        // Channel/Artist verification bonus (+250): candidate channel or artist name matches
        if (artistTokens.any { candArtistLower.contains(it) } || candArtistLower.contains("vevo") || candArtistLower.contains("official")) {
            score += 250.0
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
