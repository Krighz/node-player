package com.nodeplayer.music.lyrics

import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track

/**
 * Public feature contract for synchronized and plain lyrics.
 */
interface LyricsFeatureApi {
    fun parseLrc(content: String, trackId: Long = 0L, source: String = "local_lrc"): Lyrics
    fun loadLocalLyrics(track: Track): Lyrics?
}
