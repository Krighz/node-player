package com.nodeplayer.music.scrobble

import android.content.Context
import com.nodeplayer.model.Track

/**
 * Public feature contract for audio scrobbling (Last.fm, etc.).
 * Allows PlaybackService and UI components to decouple from network implementation details.
 */
interface ScrobbleFeatureApi {
    suspend fun updateNowPlaying(track: Track, sessionKey: String): Boolean
    suspend fun scrobble(track: Track, timestampSec: Long, sessionKey: String, context: Context): Boolean
    suspend fun flushPendingQueue(sessionKey: String): Int
    fun isKeyConfigured(context: Context? = null): Boolean
    fun isSecretConfigured(context: Context? = null): Boolean
    fun isConfigured(context: Context? = null): Boolean
}
