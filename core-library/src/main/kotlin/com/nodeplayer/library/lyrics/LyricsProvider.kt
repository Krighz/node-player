package com.nodeplayer.library.lyrics

import com.nodeplayer.model.Lyrics

/**
 * Interface for lyrics providers (e.g. LRCLIB, local files).
 */
interface LyricsProvider {
    val name: String

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSec: Int? = null
    ): Result<Lyrics?>
}
