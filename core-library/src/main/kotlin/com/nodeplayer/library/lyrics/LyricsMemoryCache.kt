package com.nodeplayer.library.lyrics

import com.nodeplayer.model.Lyrics
import java.util.LinkedHashMap

/**
 * Thread-safe 50-track in-memory LRU cache for lyrics per D-07.
 * Replaces flawed plain-text disk cache, preventing loss of word/syllable timings.
 */
class LyricsMemoryCache(
    private val maxSize: Int = 50
) {

    data class Entry(
        val lyrics: Lyrics?,
        val isNegative: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val map = object : LinkedHashMap<String, Entry>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: String): Entry? {
        return map[key]
    }

    @Synchronized
    fun put(key: String, entry: Entry) {
        map[key] = entry
    }

    @Synchronized
    fun put(key: String, lyrics: Lyrics?, isNegative: Boolean) {
        map[key] = Entry(lyrics = lyrics, isNegative = isNegative)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size
}
