package com.nodeplayer.music.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.nodeplayer.common.logging.NodeLog
import java.io.File

/**
 * Global audio cache manager using AndroidX Media3 SimpleCache with a 512MB LRU eviction policy.
 *
 * Ensures YouTube music streams are cached by video ID, enabling full offline replay
 * without redundant network requests or token expiration issues.
 */
@UnstableApi
object AudioCache {

    private const val TAG = "AudioCache"
    const val MAX_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB
    private const val CACHE_DIR_NAME = "audio_cache"

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    val cache: SimpleCache?
        get() = simpleCacheInstance

    /**
     * Extracts YouTube videoId from synthetic URI or query parameters.
     * Supports:
     * - `nodeplayer://youtube/{videoId}`
     * - `https://.../watch?v={videoId}`
     * - `https://.../videoplayback?...` with query param `v`
     * - `youtu.be/{videoId}`
     */
    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.startsWith("nodeplayer://youtube/", ignoreCase = true)) {
            val after = trimmed.substring("nodeplayer://youtube/".length).trimStart('/')
            return after.substringBefore('?').substringBefore('#').substringBefore('/').takeIf { it.isNotBlank() }
        }

        // Check for v= query parameter
        val vParamRegex = Regex("""[?&]v=([a-zA-Z0-9_-]{11})""")
        vParamRegex.find(trimmed)?.let { match ->
            return match.groupValues.getOrNull(1)
        }

        // Check for youtu.be/{id}
        val youtuBeRegex = Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""")
        youtuBeRegex.find(trimmed)?.let { match ->
            return match.groupValues.getOrNull(1)
        }

        // Check for shorts or embed
        val shortsRegex = Regex("""/(?:shorts|embed)/([a-zA-Z0-9_-]{11})""")
        shortsRegex.find(trimmed)?.let { match ->
            return match.groupValues.getOrNull(1)
        }

        return null
    }

    fun extractVideoId(uri: Uri): String? {
        val uriStr = uri.toString()
        val fromString = extractVideoId(uriStr)
        if (fromString != null) return fromString

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        if (scheme == "nodeplayer" && host == "youtube") {
            val path = uri.path?.trimStart('/')
            if (!path.isNullOrBlank()) {
                return path.substringBefore('/')
            }
        }

        uri.getQueryParameter("v")?.let { if (it.isNotBlank()) return it }
        return null
    }

    /**
     * Generates a stable cache key formatted as `nodeplayer_yt_{videoId}` for YouTube audio,
     * or defaults to standard URI spec key.
     */
    fun getEffectiveCacheKey(url: String): String {
        val videoId = extractVideoId(url)
        return if (!videoId.isNullOrBlank()) {
            "nodeplayer_yt_$videoId"
        } else {
            url
        }
    }

    fun getEffectiveCacheKey(uri: Uri): String {
        return getEffectiveCacheKey(uri.toString())
    }

    val keyFactory = CacheKeyFactory { spec ->
        spec.key ?: getEffectiveCacheKey(spec.uri)
    }

    @Synchronized
    fun init(context: Context) {
        if (simpleCacheInstance != null) return

        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCacheInstance = SimpleCache(cacheDir, evictor, databaseProvider)
            NodeLog.i(TAG, "Initialized SimpleCache at ${cacheDir.absolutePath} with 512MB LRU limit")
        } catch (e: Exception) {
            NodeLog.e(TAG, "Failed to initialize AudioCache: ${e.message}", e)
        }
    }

    /**
     * Checks if a track given by its URI (or videoId) is fully or partially cached in disk.
     */
    fun isCached(uri: Uri): Boolean {
        val cache = simpleCacheInstance ?: return false
        val key = getEffectiveCacheKey(uri)
        return cache.isCached(key, 0, 1)
    }

    /**
     * Checks if a track is cached given a videoId.
     */
    fun isVideoIdCached(videoId: String): Boolean {
        val cache = simpleCacheInstance ?: return false
        val key = "nodeplayer_yt_$videoId"
        return cache.isCached(key, 0, 1)
    }

    /**
     * Builds a DataSource.Factory wrapping DefaultHttpDataSource with CacheDataSource.
     */
    fun createDataSourceFactory(context: Context): DataSource.Factory {
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val cache = simpleCacheInstance
        return if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheKeyFactory(keyFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }
    }

    @Synchronized
    fun release() {
        try {
            simpleCacheInstance?.release()
        } catch (e: Exception) {
            NodeLog.w(TAG, "Error releasing SimpleCache: ${e.message}")
        } finally {
            simpleCacheInstance = null
        }
    }
}
