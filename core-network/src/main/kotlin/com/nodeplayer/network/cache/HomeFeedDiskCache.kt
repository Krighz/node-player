package com.nodeplayer.network.cache

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.YouTubeSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedTrackDto(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val path: String = "",
    val formatDescription: String = "",
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val isFavorite: Boolean = false
)

@Serializable
data class CachedSectionDto(
    val title: String,
    val items: List<CachedTrackDto>
)

@Serializable
data class HomeFeedCachePayload(
    val timestamp: Long,
    val region: String,
    val sections: List<CachedSectionDto>
)

/**
 * Thread-safe disk & memory cache for YouTube Music home feed sections.
 * Implements stale-while-revalidate for sub-50ms cold-start display.
 */
class HomeFeedDiskCache(
    private val cacheDir: File? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _sectionsState = MutableStateFlow<List<YouTubeSection>>(emptyList())
    val sectionsState: StateFlow<List<YouTubeSection>> = _sectionsState.asStateFlow()

    private var cachedTimestamp: Long = 0L
    private var cachedRegion: String = ""

    private val cacheFile: File? by lazy {
        try {
            val baseDir = cacheDir ?: File(System.getProperty("user.home"), ".nodeplayer/cache")
            if (!baseDir.exists()) baseDir.mkdirs()
            File(baseDir, "home_feed_cache.json")
        } catch (t: Throwable) {
            NodeLog.w(TAG, "Failed to resolve cache file path: ${t.message}")
            null
        }
    }

    init {
        loadFromDisk()
    }

    fun getTimestamp(): Long = cachedTimestamp
    fun getRegion(): String = cachedRegion

    fun isValid(ttlMs: Long = 24 * 60 * 60 * 1000L): Boolean {
        return _sectionsState.value.isNotEmpty() && (System.currentTimeMillis() - cachedTimestamp) < ttlMs
    }

    @Synchronized
    fun save(sections: List<YouTubeSection>, region: String = "US") {
        if (sections.isEmpty()) return
        cachedTimestamp = System.currentTimeMillis()
        cachedRegion = region
        _sectionsState.value = sections

        val file = cacheFile ?: return
        try {
            val payload = HomeFeedCachePayload(
                timestamp = cachedTimestamp,
                region = region,
                sections = sections.map { sec ->
                    CachedSectionDto(
                        title = sec.title,
                        items = sec.items.map { it.toDto() }
                    )
                }
            )
            val jsonString = json.encodeToString(payload)
            file.writeText(jsonString)
            NodeLog.d(TAG, "Saved ${sections.size} home sections to disk cache (${file.length()} bytes)")
        } catch (t: Throwable) {
            NodeLog.e(TAG, "Failed to save home feed cache: ${t.message}")
        }
    }

    @Synchronized
    fun loadFromDisk(): List<YouTubeSection> {
        val file = cacheFile
        if (file == null || !file.exists()) {
            return emptyList()
        }

        try {
            val content = file.readText()
            val payload = json.decodeFromString<HomeFeedCachePayload>(content)
            cachedTimestamp = payload.timestamp
            cachedRegion = payload.region
            val loadedSections = payload.sections.map { sec ->
                YouTubeSection(
                    title = sec.title,
                    items = sec.items.map { it.toTrack() }
                )
            }
            _sectionsState.value = loadedSections
            NodeLog.i(TAG, "Loaded ${loadedSections.size} sections from disk cache (${payload.region})")
            return loadedSections
        } catch (t: Throwable) {
            NodeLog.w(TAG, "Error reading home feed cache: ${t.message}")
            return emptyList()
        }
    }

    @Synchronized
    fun clear() {
        _sectionsState.value = emptyList()
        cachedTimestamp = 0L
        cachedRegion = ""
        try {
            cacheFile?.delete()
        } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "nodeplayer/home_cache"

        fun Track.toDto(): CachedTrackDto = CachedTrackDto(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            path = path,
            formatDescription = formatDescription,
            playCount = playCount,
            lastPlayedTimestamp = lastPlayedTimestamp,
            isFavorite = isFavorite
        )

        fun CachedTrackDto.toTrack(): Track = Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            path = path,
            formatDescription = formatDescription,
            playCount = playCount,
            lastPlayedTimestamp = lastPlayedTimestamp,
            isFavorite = isFavorite
        )
    }
}
