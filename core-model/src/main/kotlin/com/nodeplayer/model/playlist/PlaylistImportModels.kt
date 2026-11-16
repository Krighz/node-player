package com.nodeplayer.model.playlist

import com.nodeplayer.model.Track
import java.util.UUID

/**
 * Ordering convention for ambiguous plain-text playlist lines (e.g. "A - B").
 */
enum class ArtistTitleOrder {
    ARTIST_TITLE,
    TITLE_ARTIST
}

/**
 * A track candidate parsed from an imported playlist file or pasted text block.
 */
data class ParsedTrackCandidate(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val isrc: String? = null,
    val url: String? = null,
    val rawLine: String = ""
)

/**
 * Status classification for a candidate matched against local or remote catalog.
 */
enum class ImportMatchStatus {
    MATCHED,
    NEEDS_REVIEW,
    NOT_FOUND
}

/**
 * Progress phases during playlist import pipeline execution.
 */
enum class ImportProgressStep {
    PARSING,
    MATCHING_LOCAL,
    MATCHING_ONLINE,
    SAVING,
    DONE
}

/**
 * Configuration options governing playlist parsing and matching engine behavior.
 */
data class ImportConfig(
    val artistTitleOrder: ArtistTitleOrder = ArtistTitleOrder.ARTIST_TITLE,
    val autoAcceptThreshold: Float = 0.90f,
    val reviewMinThreshold: Float = 0.60f,
    val durationWindowMs: Long = 3000L,
    val maxLines: Int = 5000,
    val maxMb: Int = 5,
    val onlineConcurrency: Int = 2,
    val alsoCreateYouTube: Boolean = false
)

/**
 * Matched item evaluation representing the resolution of a single parsed track candidate.
 */
data class ImportResultItem(
    val id: String = UUID.randomUUID().toString(),
    val candidate: ParsedTrackCandidate,
    val matchedTrack: Track? = null,
    val confidenceScore: Float = 0f,
    val status: ImportMatchStatus = ImportMatchStatus.NOT_FOUND,
    val alternatives: List<Track> = emptyList(),
    val isAccepted: Boolean = (status == ImportMatchStatus.MATCHED)
)

/**
 * Active session holding progress, candidate evaluations, and naming for imported playlist.
 */
data class ImportSession(
    val id: String = UUID.randomUUID().toString(),
    val playlistName: String,
    val items: List<ImportResultItem> = emptyList(),
    val totalLines: Int = 0,
    val isCancelled: Boolean = false
)
