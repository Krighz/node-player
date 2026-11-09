package com.nodeplayer.music.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.lyrics.LyricsRepository
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import com.nodeplayer.music.data.AppSettingsRepository
import com.nodeplayer.music.data.dataStore
import com.nodeplayer.network.lyrics.LrclibLyricsProvider
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

/**
 * Android implementation of LyricsFeatureApi wrapping the unified LyricsRepository,
 * persistent DataStore offsets, and cache management.
 */
object LyricsManager : LyricsFeatureApi {

    private var repository: LyricsRepository? = null

    fun initialize(context: Context) {
        val cacheDir = File(context.cacheDir, "lyrics_cache")
        val lrclibProvider = LrclibLyricsProvider()
        repository = LyricsRepository(
            providers = listOf(lrclibProvider),
            cacheDir = cacheDir
        )
        NodeLog.i("LyricsManager", "Initialized LyricsManager with LRCLIB and cache directory")
    }

    override fun parseLrc(content: String, trackId: Long, source: String): Lyrics {
        return com.nodeplayer.library.LrcParser.parse(content, trackId, source)
    }

    override fun loadLocalLyrics(track: Track): Lyrics? {
        val repo = repository ?: LyricsRepository(cacheDir = null)
        return repo.loadLocalLyrics(track)
    }

    suspend fun getLyrics(
        context: Context,
        track: Track,
        forceRefresh: Boolean = false
    ): Lyrics? {
        if (repository == null) {
            initialize(context.applicationContext)
        }

        // Retrieve global and track offsets from DataStore
        var totalOffsetMs = 0L
        try {
            val prefs = context.dataStore.data.firstOrNull()
            val globalOffset = prefs?.get(AppSettingsRepository.KEY_GLOBAL_LYRICS_OFFSET_MS) ?: 0L
            val trackKey = AppSettingsRepository.getTrackOffsetKey(track.id)
            val trackOffset = prefs?.get(trackKey) ?: 0L
            totalOffsetMs = globalOffset + trackOffset
        } catch (e: Exception) {
            NodeLog.w("LyricsManager", "Could not load lyrics offsets: ${e.message}")
        }

        return repository?.getLyrics(
            track = track,
            forceRefresh = forceRefresh,
            userOffsetMs = totalOffsetMs
        )
    }

    suspend fun setTrackOffset(context: Context, trackId: Long, offsetMs: Long) {
        try {
            val key = AppSettingsRepository.getTrackOffsetKey(trackId)
            context.dataStore.edit { prefs ->
                if (offsetMs == 0L) {
                    prefs.remove(key)
                } else {
                    prefs[key] = offsetMs
                }
            }
        } catch (e: Exception) {
            NodeLog.w("LyricsManager", "Failed to persist track offset: ${e.message}")
        }
    }

    suspend fun getTrackOffset(context: Context, trackId: Long): Long {
        return try {
            val key = AppSettingsRepository.getTrackOffsetKey(trackId)
            val prefs = context.dataStore.data.firstOrNull()
            prefs?.get(key) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun clearCache() {
        repository?.clearCache()
    }
}
