package com.nodeplayer.library.playlist

/**
 * Standard header alias mappings for universal CSV playlist parsing.
 * Supports Spotify exports, TuneMyMusic, Soundiiz, Apple Music, and standard CSV formats.
 * Kept in this single file per architectural guidelines and cataloged in HARDCODED-LEDGER.md.
 */
object ImportColumnAliases {

    val TITLE_ALIASES: Set<String> = setOf(
        "title",
        "track name",
        "track",
        "song",
        "song name",
        "name",
        "track_name",
        "song_name"
    )

    val ARTIST_ALIASES: Set<String> = setOf(
        "artist",
        "artist name",
        "artist name(s)",
        "artists",
        "track artist",
        "performer",
        "artist_name",
        "track_artist"
    )

    val ALBUM_ALIASES: Set<String> = setOf(
        "album",
        "album name",
        "collection",
        "album_name"
    )

    val DURATION_ALIASES: Set<String> = setOf(
        "duration",
        "duration (ms)",
        "duration ms",
        "duration_ms",
        "time",
        "length"
    )

    val ISRC_ALIASES: Set<String> = setOf(
        "isrc",
        "isrc code",
        "isrc_code"
    )

    val URL_ALIASES: Set<String> = setOf(
        "url",
        "track url",
        "spotify url",
        "link",
        "track_url",
        "spotify_url"
    )
}
