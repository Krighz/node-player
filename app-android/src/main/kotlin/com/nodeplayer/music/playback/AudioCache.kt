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
import kotlinx.coroutines.launch
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
    const val CACHE_DIR_NAME = "audio_cache"

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    // In-memory cache for resolved HTTPS stream URLs by videoId with expiration check
    private val resolvedStreamUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
    // In-memory cache for the client User-Agent used to resolve each stream URL (or videoId)
    private val resolvedUserAgents = java.util.concurrent.ConcurrentHashMap<String, String>()
    // In-memory cache for extra request headers (e.g. Referer, Origin) by URL or videoId
    private val resolvedHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    // In-memory cache for mapping resolved HTTPS stream URLs back to their videoId
    private val urlToVideoId = java.util.concurrent.ConcurrentHashMap<String, String>()

    internal val sharedOkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getVideoIdForUrl(url: String): String? = urlToVideoId[url]

    fun cacheResolvedUrl(
        videoId: String,
        url: String,
        userAgent: String? = null,
        headers: Map<String, String>? = null
    ) {
        resolvedStreamUrls[videoId] = url
        urlToVideoId[url] = videoId
        if (!userAgent.isNullOrBlank()) {
            resolvedUserAgents[videoId] = userAgent
            resolvedUserAgents[url] = userAgent
            resolvedUserAgents["nodeplayer_yt_$videoId"] = userAgent
        }
        if (!headers.isNullOrEmpty()) {
            resolvedHeaders[videoId] = headers
            resolvedHeaders[url] = headers
            resolvedHeaders["nodeplayer_yt_$videoId"] = headers
        }
    }

    fun getCachedResolvedUrl(videoId: String): String? {
        val url = resolvedStreamUrls[videoId] ?: return null
        val expiresAt = Regex("[?&]expire=([0-9]+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        if (expiresAt != null && expiresAt <= System.currentTimeMillis() / 1000L + 60L) {
            invalidateUrl(videoId)
            return null
        }
        return url
    }

    fun getUserAgentForUrl(url: String): String? =
        resolvedUserAgents[url]
            ?: extractVideoId(url)?.let { resolvedUserAgents[it] }
            ?: extractVideoId(url)?.let { resolvedUserAgents["nodeplayer_yt_$it"] }

    fun invalidateUrl(urlOrVideoId: String) {
        val videoId = extractVideoId(urlOrVideoId) ?: urlOrVideoId
        val previousUrl = resolvedStreamUrls.remove(videoId)
        previousUrl?.let {
            resolvedUserAgents.remove(it)
            resolvedHeaders.remove(it)
            urlToVideoId.remove(it)
        }
        urlToVideoId.remove(urlOrVideoId)
        resolvedUserAgents.remove(videoId)
        resolvedUserAgents.remove(urlOrVideoId)
        resolvedHeaders.remove(videoId)
        resolvedHeaders.remove(urlOrVideoId)
        NodeLog.i(TAG, "Invalidated cached stream URL for $urlOrVideoId (videoId=$videoId)")
    }

    @Volatile
    private var globalStreamResolver: com.nodeplayer.network.innertube.StreamResolver? = null

    fun getStreamResolver(): com.nodeplayer.network.innertube.StreamResolver {
        return globalStreamResolver ?: synchronized(this) {
            globalStreamResolver ?: com.nodeplayer.network.innertube.StreamResolver(
                innerTubeClient = com.nodeplayer.network.innertube.SharedInnerTubeProvider.get()
            ).also { globalStreamResolver = it }
        }
    }

    /**
     * Re-resolves a stream URL for [videoIdOrUrl] after invalidating stale cache entries.
     */
    fun refreshStreamUrl(videoIdOrUrl: String): String? {
        val isVideo = videoIdOrUrl.startsWith("video_")
        val cleanInput = videoIdOrUrl.removePrefix("video_").removePrefix("nodeplayer_yt_")
        val videoId = extractVideoId(cleanInput) ?: cleanInput
        val cacheKey = if (isVideo) "video_$videoId" else videoId

        invalidateUrl(cacheKey)
        invalidateUrl(videoId)
        return try {
            if (isVideo) {
                try {
                    NewPipeFallback.init(sharedOkHttpClient)
                    val (npUrl, npHeaders) = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        NewPipeFallback.resolveVideoStream(videoId)
                    }
                    cacheResolvedUrl(cacheKey, npUrl, npHeaders["User-Agent"], npHeaders)
                    NodeLog.i(TAG, "Successfully refreshed video stream URL for videoId=$videoId via NewPipe")
                    return npUrl
                } catch (e: Exception) {
                    NodeLog.w(TAG, "NewPipe video refresh failed: ${e.message}, trying StreamResolver")
                }

                val resolver = getStreamResolver()
                val result = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    resolver.resolveVideoStream(videoId)
                }
                if (result.isSuccess) {
                    val resolved = result.getOrThrow()
                    val clientUa = com.nodeplayer.network.innertube.StreamResolver.CLIENT_ROTATION
                        .firstOrNull { it.clientName.equals(resolved.clientName, ignoreCase = true) }?.userAgent
                        ?: com.nodeplayer.network.innertube.YouTubeClient.IOS.userAgent
                    cacheResolvedUrl(cacheKey, resolved.streamUrl, clientUa)
                    NodeLog.i(TAG, "Successfully refreshed video stream URL for videoId=$videoId via ${resolved.clientName}")
                    resolved.streamUrl
                } else null
            } else {
                try {
                    NewPipeFallback.init(sharedOkHttpClient)
                    val (npUrl, npHeaders) = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        NewPipeFallback.resolveStream(videoId)
                    }
                    cacheResolvedUrl(videoId, npUrl, npHeaders["User-Agent"], npHeaders)
                    NodeLog.i(TAG, "Successfully refreshed stream for videoId=$videoId via NewPipe (bypassing 1 MiB cap)")
                    npUrl
                } catch (e: Exception) {
                    NodeLog.w(TAG, "NewPipe refresh failed for videoId=$videoId: ${e.message}, falling back to StreamResolver")
                    val resolver = getStreamResolver()
                    val result = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        resolver.resolveStream(videoId)
                    }
                    if (result.isSuccess) {
                        val resolved = result.getOrThrow()
                        val clientUa = com.nodeplayer.network.innertube.StreamResolver.CLIENT_ROTATION
                            .firstOrNull { it.clientName.equals(resolved.clientName, ignoreCase = true) }?.userAgent
                            ?: com.nodeplayer.network.innertube.YouTubeClient.IOS.userAgent
                        cacheResolvedUrl(videoId, resolved.streamUrl, clientUa)
                        NodeLog.i(TAG, "Successfully refreshed stream URL for videoId=$videoId via ${resolved.clientName}")
                        resolved.streamUrl
                    } else {
                        NodeLog.w(TAG, "Both NewPipe and StreamResolver refresh failed for videoId=$videoId: ${result.exceptionOrNull()?.message}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Error refreshing stream URL for $videoIdOrUrl: ${e.message}")
            null
        }
    }

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
        urlToVideoId[trimmed]?.let { return it }
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
            NewPipeFallback.init(sharedOkHttpClient)
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
     * Builds a DataSource.Factory wrapping dynamic DefaultHttpDataSource with CacheDataSource.
     */
    fun createDataSourceFactory(context: Context): DataSource.Factory {
        val httpFactory = DataSource.Factory {
            object : DataSource {
                private var delegate: DataSource? = null

                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {}

                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    val url = dataSpec.uri.toString()
                    val key = dataSpec.key
                    val customHeaders = resolvedHeaders[url]
                        ?: (if (key != null) resolvedHeaders[key] else null)
                        ?: extractVideoId(url)?.let { vid -> resolvedHeaders[vid] ?: resolvedHeaders["nodeplayer_yt_$vid"] }
                        ?: emptyMap()

                    val targetUa = customHeaders["User-Agent"]
                        ?: resolvedUserAgents[url]
                        ?: (if (key != null) resolvedUserAgents[key] else null)
                        ?: extractVideoId(url)?.let { vid -> resolvedUserAgents[vid] ?: resolvedUserAgents["nodeplayer_yt_$vid"] }
                        ?: getUserAgentForUrl(url)
                        ?: if (url.contains("c=WEB") || url.contains("c=WEB_REMIX")) {
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
                        } else {
                            com.nodeplayer.network.innertube.YouTubeClient.IOS.userAgent
                        }

                    NodeLog.i(TAG, "Opening HTTP stream: targetUa='$targetUa', key='$key', url=${url.take(100)}")

                    val factory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(sharedOkHttpClient)
                        .setUserAgent(targetUa)

                    val defaultReqProps = androidx.media3.datasource.HttpDataSource.RequestProperties()
                    if (url.contains("c=WEB") || url.contains("c=WEB_REMIX") || customHeaders.containsKey("Referer")) {
                        defaultReqProps.set("Referer", customHeaders["Referer"] ?: "https://www.youtube.com/")
                        defaultReqProps.set("Origin", customHeaders["Origin"] ?: "https://www.youtube.com")
                    }
                    customHeaders.forEach { (k, v) ->
                        if (!k.equals("User-Agent", ignoreCase = true)) {
                            defaultReqProps.set(k, v)
                        }
                    }
                    factory.setDefaultRequestProperties(defaultReqProps.getSnapshot())

                    val realSource = factory.createDataSource()
                    delegate = realSource
                    return try {
                        realSource.open(dataSpec)
                    } catch (e: Exception) {
                        NodeLog.e(TAG, "HTTP stream open FAILED: targetUa='$targetUa', key='$key', err=${e.message}", e)
                        throw e
                    }
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return delegate?.read(buffer, offset, length) ?: -1
                }

                override fun getUri(): android.net.Uri? = delegate?.uri

                override fun getResponseHeaders(): Map<String, List<String>> =
                    delegate?.responseHeaders ?: emptyMap()

                override fun close() {
                    delegate?.close()
                    delegate = null
                }
            }
        }

        val cache = simpleCacheInstance
        val rangedFactory = RangedUpstreamDataSource.Factory(httpFactory)
        return if (cache != null) {
            androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(rangedFactory)
                .setCacheKeyFactory(keyFactory)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            rangedFactory
        }
    }

    /**
     * Builds a DataSource.Factory using Media3's ResolvingDataSource.Factory wrapping [createDataSourceFactory].
     *
     * Resolves synthetic YouTube URIs (`nodeplayer://youtube/{videoId}`) synchronously on ExoPlayer's
     * background loader thread, checking disk cache and online mode, without stalling or deadlocking
     * ExoPlayer's playback looper.
     */
    fun createResolvingDataSourceFactory(
        context: Context,
        streamResolver: com.nodeplayer.network.innertube.StreamResolver = getStreamResolver()
    ): DataSource.Factory {
        val baseFactory = createDataSourceFactory(context)
        return androidx.media3.datasource.ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val uri = dataSpec.uri
            val videoId = extractVideoId(uri) ?: dataSpec.key?.removePrefix("nodeplayer_yt_")
            val isSyntheticYt = uri.toString().startsWith("nodeplayer://youtube/", ignoreCase = true) ||
                (uri.scheme.equals("nodeplayer", ignoreCase = true) && uri.host.equals("youtube", ignoreCase = true))

            if (videoId == null || (!isSyntheticYt && dataSpec.key == null)) {
                return@Factory dataSpec
            }

            val cacheKey = "nodeplayer_yt_$videoId"

            // 1. Check if we already have a live resolved HTTPS URL in memory
            var liveUrl = getCachedResolvedUrl(videoId)

            // 2. If no valid HTTPS URL is known yet, resolve it synchronously (works for both cache misses and cached tracks needing upstream)
            if (liveUrl.isNullOrBlank()) {
                if (!com.nodeplayer.common.AppModeState.isOnlineMode.value) {
                    if (isVideoIdCached(videoId)) {
                        NodeLog.d(TAG, "Offline mode: Playing cached track for videoId=$videoId (no refresh)")
                        return@Factory dataSpec.buildUpon().setKey(cacheKey).build()
                    } else {
                        NodeLog.w(TAG, "Offline mode active: Cannot resolve online YouTube track for videoId=$videoId")
                        throw java.io.IOException("Track not available offline (videoId: $videoId)")
                    }
                }

                NodeLog.i(TAG, "Resolving stream synchronously on loader thread for videoId=$videoId (NewPipe primary)")
                try {
                    NewPipeFallback.init(sharedOkHttpClient)
                    val (npUrl, npHeaders) = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        NewPipeFallback.resolveStream(videoId)
                    }
                    liveUrl = npUrl
                    cacheResolvedUrl(videoId, liveUrl, npHeaders["User-Agent"], npHeaders)
                    NodeLog.i(TAG, "Resolved stream via NewPipe primary for videoId=$videoId (unthrottled, no 1 MiB cap)")
                } catch (npError: Exception) {
                    NodeLog.w(TAG, "NewPipe primary resolution failed for videoId=$videoId (${npError.message}), falling back to StreamResolver...")
                    val resolvedResult = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        streamResolver.resolveStream(videoId)
                    }
                    if (resolvedResult.isSuccess) {
                        val resolved = resolvedResult.getOrThrow()
                        liveUrl = resolved.streamUrl
                        val clientUa = com.nodeplayer.network.innertube.StreamResolver.CLIENT_ROTATION
                            .firstOrNull { it.clientName.equals(resolved.clientName, ignoreCase = true) }?.userAgent
                            ?: com.nodeplayer.network.innertube.YouTubeClient.IOS.userAgent
                        cacheResolvedUrl(videoId, liveUrl, clientUa)
                        NodeLog.i(TAG, "Resolved stream via StreamResolver fallback for videoId=$videoId (client=${resolved.clientName})")
                    } else {
                        val err = resolvedResult.exceptionOrNull()
                        NodeLog.e(TAG, "Both NewPipe and StreamResolver failed for videoId=$videoId: ${npError.message} / ${err?.message}", err ?: npError)
                        throw java.io.IOException("Stream resolution failed for videoId: $videoId", err ?: npError)
                    }
                }
            }

            // Always provide the real HTTPS URL and the stable cacheKey to CacheDataSource
            val resolvedUri = Uri.parse(liveUrl)
            val finalSpec = dataSpec.withUri(resolvedUri).buildUpon().setKey(cacheKey).build()

            // CacheDataSource writes the bytes consumed by playback. A second full download
            // here competed with startup buffering and could hold the same cache span.
            finalSpec
        }
    }

    private val activePreloads = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val preloadScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    /**
     * Downloads the full audio stream into [SimpleCache] in the background using Media3's [CacheWriter].
     * Once cached, the entire song is played locally from disk without network reliance or seeking lags.
     */
    fun preloadEntireTrack(context: Context, videoId: String, streamUrl: String, cacheKey: String = "nodeplayer_yt_$videoId") {
        val cache = simpleCacheInstance ?: return
        if (activePreloads.putIfAbsent(cacheKey, true) != null) {
            return // Already caching or cached
        }

        preloadScope.launch {
            try {
                val ua = getUserAgentForUrl(streamUrl) ?: com.nodeplayer.network.innertube.YouTubeClient.IOS.userAgent
                val upstream = DefaultHttpDataSource.Factory()
                    .setUserAgent(ua)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(20_000)
                    .createDataSource()

                val dataSpec = androidx.media3.datasource.DataSpec.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .setKey(cacheKey)
                    .build()

                val cacheWriter = androidx.media3.datasource.cache.CacheWriter(
                    CacheDataSource.Factory()
                        .setCache(cache)
                        .setUpstreamDataSourceFactory { upstream }
                        .setCacheKeyFactory(keyFactory)
                        .createDataSource(),
                    dataSpec,
                    null, // temporary buffer
                    object : androidx.media3.datasource.cache.CacheWriter.ProgressListener {
                        override fun onProgress(requestLength: Long, bytesCached: Long, newBytesCached: Long) {
                            if (requestLength > 0 && bytesCached >= requestLength) {
                                NodeLog.i(TAG, "Completed 100% full background caching for videoId=$videoId ($bytesCached bytes)")
                            }
                        }
                    }
                )

                NodeLog.i(TAG, "Starting full background track cache download for videoId=$videoId")
                cacheWriter.cache()
                NodeLog.i(TAG, "Full background track cached successfully for videoId=$videoId")
            } catch (e: Exception) {
                NodeLog.w(TAG, "Background preloading failed or interrupted for videoId=$videoId: ${e.message}")
            } finally {
                activePreloads.remove(cacheKey)
            }
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
