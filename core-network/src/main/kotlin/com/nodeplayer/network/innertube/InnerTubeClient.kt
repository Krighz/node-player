package com.nodeplayer.network.innertube

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.Closeable
import kotlin.math.abs

/**
 * High-performance, pure Kotlin YouTube Music InnerTube API client.
 *
 * Designed for cross-platform Node Player usage (Android ART & Windows Desktop JVM)
 * with zero Android framework dependencies.
 */
@OptIn(ExperimentalSerializationApi::class)
class InnerTubeClient(
    engine: HttpClientEngine? = null,
    val locale: YouTubeLocale = YouTubeLocale(gl = "US", hl = "en"),
    val clientContext: YouTubeClient = YouTubeClient.ANDROID_VR_1_65_10,
) : Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val httpClient: HttpClient = if (engine != null) {
        HttpClient(engine) { configureClient() }
    } else {
        HttpClient(CIO) { configureClient() }
    }

    private fun io.ktor.client.HttpClientConfig<*>.configureClient() {
        expectSuccess = true

        install(ContentNegotiation) {
            json(this@InnerTubeClient.json)
        }

        install(ContentEncoding) {
            gzip(0.9f)
            deflate(0.8f)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 15_000L
            requestTimeoutMillis = 30_000L
            socketTimeoutMillis = 30_000L
        }

        defaultRequest {
            url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
            header("User-Agent", clientContext.userAgent)
            header("X-YouTube-Client-Name", clientContext.clientId)
            header("X-YouTube-Client-Version", clientContext.clientVersion)
            header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
            header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Executes an operation with exponential backoff retry for transient network disconnects.
     */
    private suspend fun <T> withRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (t: Throwable) {
                lastException = t
                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong()
                }
            }
        }
        throw lastException ?: IllegalStateException("Operation failed after $maxAttempts attempts")
    }

    /**
     * Executes a raw InnerTube browse request for [browseId] using [YouTubeClient.WEB_REMIX].
     */
    suspend fun browse(browseId: String): Result<BrowseResponse> = runCatching {
        val client = YouTubeClient.WEB_REMIX
        NodeLog.d(TAG, "Fetching browse feed (browseId=$browseId, client=${client.clientName})")
        withRetry {
            httpClient.post("browse") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                setBody(
                    BrowseBody(
                        context = client.toContext(locale),
                        browseId = browseId
                    )
                )
            }.body<BrowseResponse>()
        }
    }

    /**
     * Parses sections from [BrowseResponse] section list contents.
     */
    internal fun parseBrowseSections(response: BrowseResponse): List<YouTubeSection> {
        val sections = mutableListOf<YouTubeSection>()

        val sectionListContents = response.contents?.sectionListRenderer?.contents
            ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: emptyList()

        for (content in sectionListContents) {
            val carousel = content.musicCarouselShelfRenderer
            val shelf = content.musicShelfRenderer

            if (carousel != null) {
                val title = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.text()
                    ?: carousel.header?.musicCarouselShelfBasicHeaderRenderer?.strapline?.text()
                    ?: "Recommendations"

                val tracks = carousel.contents.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { mapTwoRowItemToTrack(it) }
                        ?: item.musicResponsiveListItemRenderer?.let { mapResponsiveListItemToTrack(it) }
                }

                if (tracks.isNotEmpty()) {
                    sections.add(YouTubeSection(title = title, items = tracks))
                }
            } else if (shelf != null) {
                val title = shelf.title?.text() ?: "Recommendations"
                val tracks = shelf.contents.orEmpty().mapNotNull { item ->
                    item.musicResponsiveListItemRenderer?.let { mapResponsiveListItemToTrack(it) }
                }

                if (tracks.isNotEmpty()) {
                    sections.add(YouTubeSection(title = title, items = tracks))
                }
            }
        }
        return sections
    }

    /**
     * Fetches curated home recommendation sections and shelves from YouTube Music (`FEmusic_home`).
     * Uses [YouTubeClient.WEB_REMIX] which is YouTube Music's native web client supporting browse feeds.
     */
    suspend fun getHomeRecommendations(): Result<List<YouTubeSection>> = runCatching {
        val response = browse("FEmusic_home").getOrThrow()
        val sections = parseBrowseSections(response)
        NodeLog.i(TAG, "Loaded ${sections.size} home recommendation sections")
        sections
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to load home recommendations", e)
    }

    /**
     * Fetches explore sections (New releases, Trending, Moods & Genres) from YouTube Music (`FEmusic_explore`).
     */
    suspend fun getExploreSections(): Result<List<YouTubeSection>> = runCatching {
        val response = browse("FEmusic_explore").getOrThrow()
        val sections = parseBrowseSections(response)
        NodeLog.i(TAG, "Loaded ${sections.size} explore sections")
        sections
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to load explore sections", e)
    }

    /**
     * Fetches top charts and trending tracks from YouTube Music (`FEmusic_charts`).
     */
    suspend fun getChartsSections(): Result<List<YouTubeSection>> = runCatching {
        val response = browse("FEmusic_charts").getOrThrow()
        val sections = parseBrowseSections(response)
        NodeLog.i(TAG, "Loaded ${sections.size} charts sections")
        sections
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to load charts sections", e)
    }

    /**
     * Fetches a rich multi-shelf online home feed combining Quick Picks (from Home)
     * and Trending / New Releases (from Explore).
     */
    suspend fun getCombinedHomeFeed(): Result<List<YouTubeSection>> = runCatching {
        val homeResult = getHomeRecommendations().getOrDefault(emptyList())
        val exploreResult = getExploreSections().getOrDefault(emptyList())

        val combined = mutableListOf<YouTubeSection>()
        combined.addAll(homeResult)

        val existingTitles = homeResult.map { it.title.lowercase().trim() }.toSet()
        for (section in exploreResult) {
            if (section.title.lowercase().trim() !in existingTitles) {
                combined.add(section)
            }
        }

        if (combined.isEmpty()) {
            throw IllegalStateException("No recommendation shelves available")
        }
        NodeLog.i(TAG, "Loaded ${combined.size} combined online shelves")
        combined
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to load combined home feed", e)
    }

    /**
     * Searches YouTube Music for songs, albums, and artists matching [query] and [filter].
     */
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.ALL): Result<YouTubeSearchResult> = runCatching {
        NodeLog.d(TAG, "Executing search: query=\"$query\", filter=$filter")
        val response = withRetry {
            httpClient.post("search") {
                setBody(
                    SearchBody(
                        context = clientContext.toContext(locale),
                        query = query,
                        params = filter.paramValue
                    )
                )
            }.body<SearchResponse>()
        }

        val songs = mutableListOf<Track>()
        val albums = mutableListOf<Track>()
        val artists = mutableListOf<Track>()

        // 1. Process tabbed search results
        val tabs = response.contents?.tabbedSearchResultsRenderer?.tabs.orEmpty()
        for (tab in tabs) {
            val sectionList = tab.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
            for (section in sectionList) {
                section.musicShelfRenderer?.contents?.forEach { shelfContent ->
                    shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                        categorizeAndAddSearchItem(renderer, songs, albums, artists)
                    }
                }
                section.itemSectionRenderer?.contents?.forEach { itemContent ->
                    itemContent.musicResponsiveListItemRenderer?.let { renderer ->
                        categorizeAndAddSearchItem(renderer, songs, albums, artists)
                    }
                    itemContent.musicShelfRenderer?.contents?.forEach { shelfContent ->
                        shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                            categorizeAndAddSearchItem(renderer, songs, albums, artists)
                        }
                    }
                }
            }
        }

        // 2. Process continuation contents if present
        response.continuationContents?.musicShelfContinuation?.contents?.forEach { shelfContent ->
            shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                categorizeAndAddSearchItem(renderer, songs, albums, artists)
            }
        }

        NodeLog.i(TAG, "Search completed for \"$query\": songs=${songs.size}, albums=${albums.size}, artists=${artists.size}")
        YouTubeSearchResult(
            songs = songs.distinctBy { it.id },
            albums = albums.distinctBy { it.id },
            artists = artists.distinctBy { it.id }
        )
    }.onFailure { e ->
        NodeLog.e(TAG, "Search failed for query=\"$query\"", e)
    }

    /**
     * Fetches raw [PlayerResponse] for [videoId] using specified [client].
     */
    suspend fun getPlayerResponse(
        videoId: String,
        client: YouTubeClient = clientContext,
    ): Result<PlayerResponse> = runCatching {
        NodeLog.d(TAG, "Fetching player response for videoId=$videoId (client=${client.clientName})")
        withRetry {
            httpClient.post("player") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                setBody(
                    PlayerBody(
                        context = client.toContext(locale),
                        videoId = videoId
                    )
                )
            }.body<PlayerResponse>()
        }
    }

    /**
     * Resolves playable audio stream metadata for [videoId].
     * Uses [client] context allowing unthrottled direct audio stream extraction.
     */
    suspend fun getPlayer(
        videoId: String,
        client: YouTubeClient = clientContext,
    ): Result<YouTubeStreamInfo> = runCatching {
        val response = getPlayerResponse(videoId, client).getOrThrow()

        val playability = response.playabilityStatus
        if (playability != null && playability.status != "OK") {
            val reason = playability.reason ?: playability.status ?: "Unknown playability status"
            throw IllegalStateException("Stream unavailable for video $videoId: $reason")
        }

        val details = response.videoDetails ?: throw IllegalStateException("Missing videoDetails in player response")
        val formats = response.streamingData?.adaptiveFormats.orEmpty() + response.streamingData?.formats.orEmpty()

        // Select the highest quality audio format (preferring opus / mp4a with highest bitrate and direct url)
        val audioFormats = formats.filter { it.isAudio || it.mimeType.contains("audio", ignoreCase = true) }
        val bestFormat = audioFormats.filter { !it.url.isNullOrBlank() }
            .maxByOrNull { it.bitrate ?: 0 }
            ?: formats.firstOrNull { !it.url.isNullOrBlank() }

        val durationSeconds = details.lengthSeconds?.toLongOrNull()
            ?: (bestFormat?.approxDurationMs?.toLongOrNull()?.let { it / 1000 })
            ?: 0L

        val loudnessDb = response.playerConfig?.audioConfig?.loudnessDb ?: bestFormat?.loudnessDb

        NodeLog.i(TAG, "Resolved stream info for videoId=$videoId, itag=${bestFormat?.itag}, bitrate=${bestFormat?.bitrate}")
        YouTubeStreamInfo(
            videoId = details.videoId,
            title = details.title ?: "Unknown Title",
            artist = details.author ?: "Unknown Artist",
            streamUrl = bestFormat?.url,
            durationSeconds = durationSeconds,
            itag = bestFormat?.itag,
            mimeType = bestFormat?.mimeType,
            bitrate = bestFormat?.bitrate?.toLong(),
            contentLength = bestFormat?.contentLength,
            loudnessDb = loudnessDb
        )
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to resolve player stream for videoId=$videoId", e)
    }

    /**
     * Fetches an infinite radio queue based on [videoId] and optional [playlistId].
     */
    suspend fun getNextRadio(videoId: String, playlistId: String? = null): Result<List<Track>> = runCatching {
        NodeLog.d(TAG, "Fetching next radio queue for videoId=$videoId, playlistId=$playlistId")
        val response = withRetry {
            httpClient.post("next") {
                setBody(
                    NextBody(
                        context = clientContext.toContext(locale),
                        videoId = videoId,
                        playlistId = playlistId
                    )
                )
            }.body<NextResponse>()
        }

        val panel = response.continuationContents?.playlistPanelContinuation
            ?: response.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer
                ?.watchNextTabbedResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content
                ?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer?.let { null }

        val tracks = mutableListOf<Track>()

        // Check continuation panel
        panel?.contents?.forEach { content ->
            content.playlistPanelVideoRenderer?.let { videoRenderer ->
                mapPlaylistPanelVideoToTrack(videoRenderer)?.let { tracks.add(it) }
            }
        }

        // If not in continuation, inspect tabs
        if (tracks.isEmpty()) {
            val tabs = response.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer
                ?.watchNextTabbedResultsRenderer?.tabs.orEmpty()
            for (tab in tabs) {
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { sectionContent ->
                    sectionContent.musicShelfRenderer?.contents?.forEach { shelfContent ->
                        shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                            mapResponsiveListItemToTrack(renderer)?.let { tracks.add(it) }
                        }
                    }
                }
            }
        }

        NodeLog.i(TAG, "Resolved ${tracks.size} tracks for next radio queue (videoId=$videoId)")
        tracks
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to fetch next radio queue for videoId=$videoId", e)
    }

    override fun close() {
        httpClient.close()
    }

    // --- Domain Mappers ---

    private fun mapTwoRowItemToTrack(item: MusicTwoRowItemRenderer): Track? {
        val videoId = item.navigationEndpoint?.watchEndpoint?.videoId
            ?: item.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId
            ?: return null

        val title = item.title?.text()?.ifBlank { null } ?: return null
        val subtitle = item.subtitle?.text() ?: ""
        val artist = subtitle.substringBefore(" • ").trim().ifBlank { "YouTube Music" }
        val album = subtitle.substringAfter(" • ", "").trim().ifBlank { "YouTube Music" }

        val id = generateSyntheticTrackId(videoId)

        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = 0L,
            path = "nodeplayer://youtube/$videoId",
            formatDescription = "YouTube Audio"
        )
    }

    private fun mapResponsiveListItemToTrack(item: MusicResponsiveListItemRenderer): Track? {
        val videoId = item.videoId ?: return null
        val title = item.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.text()
            ?.ifBlank { null } ?: return null

        val subtitleRuns = item.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs.orEmpty()
        val artist = subtitleRuns.firstOrNull()?.text?.trim()?.ifBlank { "YouTube Music" } ?: "YouTube Music"
        val album = subtitleRuns.getOrNull(1)?.text?.trim()?.ifBlank { "YouTube Music" } ?: "YouTube Music"

        val durationStr = subtitleRuns.lastOrNull()?.text?.trim()
        val durationMs = parseDurationMs(durationStr)

        val id = generateSyntheticTrackId(videoId)

        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            path = "nodeplayer://youtube/$videoId",
            formatDescription = "YouTube Audio"
        )
    }

    private fun mapPlaylistPanelVideoToTrack(item: PlaylistPanelVideoRenderer): Track? {
        val videoId = item.videoId ?: item.navigationEndpoint?.watchEndpoint?.videoId ?: return null
        val title = item.title?.text()?.ifBlank { null } ?: return null
        val artist = item.shortBylineText?.text()?.ifBlank { null }
            ?: item.longBylineText?.text()?.substringBefore(" • ")?.trim()
            ?: "YouTube Music"

        val album = item.longBylineText?.text()?.substringAfter(" • ", "")?.trim()?.ifBlank { "YouTube Music" }
            ?: "YouTube Music"

        val durationMs = parseDurationMs(item.lengthText?.text())
        val id = generateSyntheticTrackId(videoId)

        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            path = "nodeplayer://youtube/$videoId",
            formatDescription = "YouTube Audio"
        )
    }

    private fun categorizeAndAddSearchItem(
        renderer: MusicResponsiveListItemRenderer,
        songs: MutableList<Track>,
        albums: MutableList<Track>,
        artists: MutableList<Track>
    ) {
        val track = mapResponsiveListItemToTrack(renderer) ?: return
        val secondColText = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.text().orEmpty()
        val pageType = renderer.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs
            ?.browseEndpointContextMusicConfig?.pageType.orEmpty()

        when {
            pageType.contains("ARTIST", ignoreCase = true) || secondColText.startsWith("Artist", ignoreCase = true) -> {
                artists.add(track)
            }
            pageType.contains("ALBUM", ignoreCase = true) || secondColText.startsWith("Album", ignoreCase = true) || secondColText.startsWith("EP", ignoreCase = true) || secondColText.startsWith("Single", ignoreCase = true) -> {
                albums.add(track)
            }
            else -> {
                songs.add(track)
            }
        }
    }

    private fun generateSyntheticTrackId(videoId: String): Long {
        // Derive a stable positive 63-bit integer from the videoId string hash
        val hash = abs(videoId.hashCode().toLong())
        return if (hash == 0L) 1L else hash
    }

    private fun parseDurationMs(durationStr: String?): Long {
        if (durationStr.isNullOrBlank()) return 0L
        val parts = durationStr.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    companion object {
        private const val TAG = "innertube"
    }
}
