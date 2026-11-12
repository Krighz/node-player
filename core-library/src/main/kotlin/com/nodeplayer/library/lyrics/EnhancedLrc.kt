package com.nodeplayer.library.lyrics

import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics

/**
 * Enhanced LRC (A2 format) word-synced lyrics parser.
 * Delegates to [LrcParser.parse] which provides robust parsing of
 * `<mm:ss.xx> word <mm:ss.xx>` word-level timestamps and metadata.
 */
object EnhancedLrc {
    fun parse(
        content: String,
        trackId: Long = 0L,
        source: String = "enhanced_lrc"
    ): Lyrics {
        return LrcParser.parse(lrcContent = content, trackId = trackId, source = source)
    }
}
