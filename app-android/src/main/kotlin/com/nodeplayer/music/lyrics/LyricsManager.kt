package com.nodeplayer.music.lyrics

import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import java.io.File

object LyricsManager : LyricsFeatureApi {

    override fun parseLrc(content: String, trackId: Long, source: String): Lyrics {
        return LrcParser.parse(content, trackId, source)
    }

    override fun loadLocalLyrics(track: Track): Lyrics? {
        return try {
            val lrcFile = File(track.path.substringBeforeLast(".") + ".lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                parseLrc(lrcFile.readText(), track.id, "local_lrc")
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
