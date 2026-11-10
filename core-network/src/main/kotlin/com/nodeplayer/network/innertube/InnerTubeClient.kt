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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    var sessionCookie: String? = null
        set(value) {
            field = value
            cachedSapisid = YouTubeAuth.extractSapisid(value)
            NodeLog.d(TAG, "Session cookie updated: ${YouTubeAuth.maskCookieForLog(value)}")
        }

    private var cachedSapisid: String? = null

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

            sessionCookie?.let { cookie ->
                header(HttpHeaders.Cookie, cookie)
                cachedSapisid?.let { sapisid ->
                    val authHeader = YouTubeAuth.generateSapisidHash(sapisid)
                    header(HttpHeaders.Authorization, authHeader)
                }
            }
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
     * Fetches a rich multi-shelf online home feed combining Quick Picks (from Home),
     * Trending / New Releases (from Explore), and Top Charts (Top 50 Worldwide & Regional).
     */
    suspend fun getCombinedHomeFeed(): Result<List<YouTubeSection>> = runCatching {
        val homeResult = getHomeRecommendations().getOrDefault(emptyList())
        val exploreResult = getExploreSections().getOrDefault(emptyList())
        val chartsResult = getChartsSections().getOrDefault(emptyList())

        val combined = mutableListOf<YouTubeSection>()
        combined.addAll(homeResult)

        val existingTitles = homeResult.map { it.title.lowercase().trim() }.toMutableSet()
        for (section in chartsResult) {
            if (section.title.lowercase().trim() !in existingTitles && section.items.isNotEmpty()) {
                combined.add(section)
                existingTitles.add(section.title.lowercase().trim())
            }
        }

        for (section in exploreResult) {
            if (section.title.lowercase().trim() !in existingTitles && section.items.isNotEmpty()) {
                combined.add(section)
                existingTitles.add(section.title.lowercase().trim())
            }
        }

        // Add curated Top 50 Worldwide & Top 50 India search mixes if not explicitly present
        val hasIndia = combined.any { it.title.contains("India", ignoreCase = true) }
        val hasTop50 = combined.any { it.title.contains("Top 50", ignoreCase = true) || it.title.contains("Charts", ignoreCase = true) }

        if (!hasTop50) {
            val top50Worldwide = search("Top 50 Worldwide", SearchFilter.SONGS).getOrNull()
            if (top50Worldwide != null && top50Worldwide.songs.isNotEmpty()) {
                combined.add(
                    YouTubeSection(
                        title = "Top 50 - Worldwide",
                        items = top50Worldwide.songs.take(20)
                    )
                )
            }
        }

        if (!hasIndia) {
            val topIndia = search("Top 50 India Trending Songs", SearchFilter.SONGS).getOrNull()
            if (topIndia != null && topIndia.songs.isNotEmpty()) {
                combined.add(
                    YouTubeSection(
                        title = "Top 50 - India Trending",
                        items = topIndia.songs.take(20)
                    )
                )
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
        val client = YouTubeClient.WEB_REMIX
        NodeLog.d(TAG, "Executing search: query=\"$query\", filter=$filter, client=${client.clientName}")
        val response = withRetry {
            httpClient.post("search") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                setBody(
                    SearchBody(
                        context = client.toContext(locale),
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

    private var cachedVisitorData: String? = null
    private var visitorDataExpiryMs: Long = 0L

    /**
     * Retrieves valid visitorData required for unauthenticated direct stream playback.
     * Fetched from `https://www.youtube.com/sw.js_data` or fallback headers.
     */
    suspend fun getVisitorData(): String? {
        val now = System.currentTimeMillis()
        if (cachedVisitorData != null && now < visitorDataExpiryMs) {
            return cachedVisitorData
        }

        return try {
            val responseText = httpClient.get("https://www.youtube.com/sw.js_data") {
                header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                header("Accept", "application/json,text/plain,*/*")
            }.body<String>()

            val parsed = parseServiceWorkerVisitorData(responseText)
            if (!parsed.isNullOrBlank()) {
                cachedVisitorData = parsed
                visitorDataExpiryMs = now + 6 * 3600 * 1000L // 6 hours
                NodeLog.i(TAG, "Successfully fetched and cached YouTube visitorData")
                parsed
            } else {
                null
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Failed to fetch YouTube visitorData: ${e.message}")
            cachedVisitorData
        }
    }

    private fun parseServiceWorkerVisitorData(responseText: String): String? = runCatching {
        val jsonText = responseText.substringAfter('\n').trimStart()
        val root = json.parseToJsonElement(jsonText)
        root.jsonArray.getOrNull(0)
            ?.jsonArray?.getOrNull(2)
            ?.jsonArray?.getOrNull(0)
            ?.jsonArray?.getOrNull(0)
            ?.jsonArray?.getOrNull(13)
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Fetches raw [PlayerResponse] for [videoId] using specified [client].
     */
    suspend fun getPlayerResponse(
        videoId: String,
        client: YouTubeClient = clientContext,
    ): Result<PlayerResponse> = runCatching {
        NodeLog.d(TAG, "Fetching player response for videoId=$videoId (client=${client.clientName})")
        val visitorData = getVisitorData()
        withRetry {
            httpClient.post("player") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                setBody(
                    PlayerBody(
                        context = client.toContext(locale, visitorData = visitorData),
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

    /**
     * Fetches user profile info (name, handle, avatar) from account_menu.
     * Requires an authenticated sessionCookie.
     */
    suspend fun fetchAccountInfo(): Result<com.nodeplayer.model.YouTubeAccount?> = runCatching {
        val cookie = sessionCookie
        if (cookie.isNullOrBlank()) return@runCatching null

        withRetry {
            val response = httpClient.post("account/account_menu") {
                val reqBody = AccountMenuBody(
                    context = clientContext.toContext(locale, visitorData = null)
                )
                setBody(reqBody)
            }

            if (response.status == HttpStatusCode.OK) {
                val responseText = response.bodyAsText()
                parseAccountMenuResponse(responseText)
            } else if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                NodeLog.w(TAG, "Account menu request unauthorized (${response.status.value})")
                null
            } else {
                NodeLog.w(TAG, "Account menu request failed with status: ${response.status}")
                null
            }
        }
    }

    private fun parseAccountMenuResponse(jsonText: String): com.nodeplayer.model.YouTubeAccount? {
        return try {
            val element = json.parseToJsonElement(jsonText)
            val actions = element.jsonObject["actions"]?.jsonArray
            val openMenuAction = actions?.firstOrNull()?.jsonObject?.get("openPopupAction")?.jsonObject
            val menuRenderer = openMenuAction?.get("popup")?.jsonObject?.get("multiPageMenuRenderer")?.jsonObject
            val headerRenderer = menuRenderer?.get("header")?.jsonObject?.get("activeAccountHeaderRenderer")?.jsonObject

            if (headerRenderer != null) {
                val accountName = headerRenderer["accountName"]?.jsonObject?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: headerRenderer["accountName"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
                    ?: "YouTube User"

                val handle = headerRenderer["email"]?.jsonObject?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: headerRenderer["email"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content

                val thumbnails = headerRenderer["accountPhoto"]?.jsonObject?.get("thumbnails")?.jsonArray
                val avatarUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

                com.nodeplayer.model.YouTubeAccount(
                    id = handle ?: accountName,
                    name = accountName,
                    handle = handle,
                    avatarUrl = avatarUrl,
                    email = handle?.takeIf { it.contains("@") }
                )
            } else {
                null
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Failed to parse account menu response: ${e.message}")
            null
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
