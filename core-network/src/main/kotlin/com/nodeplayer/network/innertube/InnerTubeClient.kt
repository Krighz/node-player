package com.nodeplayer.network.innertube

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.MoodGenre
import com.nodeplayer.model.MoodGenreSection
import com.nodeplayer.model.Playlist
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
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

    /** Authenticated session scope injected via YTCFG probe on login (AUTH-02). */
    var authUser: String? = null
    var pageId: String? = null
    var dataSyncId: String? = null
    var visitorData: String? = null

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
            if (!headers.contains("User-Agent")) header("User-Agent", clientContext.userAgent)
            if (!headers.contains("X-YouTube-Client-Name")) header("X-YouTube-Client-Name", clientContext.clientId)
            if (!headers.contains("X-YouTube-Client-Version")) header("X-YouTube-Client-Version", clientContext.clientVersion)
            if (!headers.contains("Origin")) header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
            if (!headers.contains("Referer")) header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
            contentType(ContentType.Application.Json)

            val supportsSession = headers["X-YouTube-Client-Name"] in setOf(
                YouTubeClient.WEB_REMIX.clientId, YouTubeClient.ANDROID_MUSIC.clientId,
                YouTubeClient.IOS_MUSIC.clientId, YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER.clientId
            )
            sessionCookie?.takeIf { supportsSession }?.let { cookie ->
                header(HttpHeaders.Cookie, cookie)
                header("X-Goog-AuthUser", "0")
                header("X-Origin", YouTubeAuth.ORIGIN_MUSIC)
                cachedSapisid?.let { sapisid ->
                    val authHeader = YouTubeAuth.generateSapisidHash(sapisid)
                    header(HttpHeaders.Authorization, authHeader)
                }
                authUser?.let { header("X-Goog-AuthUser", it) }
                pageId?.let { header("X-Goog-PageId", it) }
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
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
                if (t is kotlinx.coroutines.CancellationException) throw t
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
     * Executes a raw InnerTube browse request for [browseId] and optional [params] using [YouTubeClient.WEB_REMIX].
     */
    suspend fun browse(browseId: String, params: String? = null): Result<BrowseResponse> = runCatching {
        val client = YouTubeClient.WEB_REMIX
        NodeLog.d(TAG, "Fetching browse feed (browseId=$browseId, params=$params, client=${client.clientName})")
        withRetry {
            httpClient.post("browse") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                setBody(
                    BrowseBody(
                        context = client.toContext(locale, visitorData = visitorData, dataSyncId = dataSyncId),
                        browseId = browseId,
                        params = params
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

        val contents = response.contents
        val sectionListContents = buildList {
            addAll(contents?.sectionListRenderer?.contents.orEmpty())
            val tabs = contents?.singleColumnBrowseResultsRenderer?.tabs
                ?: contents?.twoColumnBrowseResultsRenderer?.tabs
            val selected = tabs?.firstOrNull { it.tabRenderer?.selected == true } ?: tabs?.firstOrNull()
            addAll(selected?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty())
            addAll(contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents.orEmpty())
            addAll(response.continuationContents?.sectionListContinuation?.contents.orEmpty())
        }

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

                val playlists = carousel.contents.mapNotNull { content ->
                    content.musicTwoRowItemRenderer?.let { mapTwoRowItemToPlaylist(it) }
                }
                if (tracks.isNotEmpty() || playlists.isNotEmpty()) {
                    sections.add(YouTubeSection(title = title, items = tracks, playlists = playlists))
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
     * Browses moods and genres categories (`FEmusic_moods_and_genres`).
     * Returns structured sections (e.g. "Moods & moments", "Genres") and their items.
     */
    suspend fun browseMoodAndGenres(): Result<List<MoodGenreSection>> = runCatching {
        val client = YouTubeClient.WEB_REMIX
        NodeLog.d(TAG, "Fetching moods and genres categories (browseId=FEmusic_moods_and_genres)")
        val responseText = withRetry {
            httpClient.post("browse") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                setBody(
                    BrowseBody(
                        context = client.toContext(locale, visitorData = visitorData, dataSyncId = dataSyncId),
                        browseId = "FEmusic_moods_and_genres"
                    )
                )
            }.bodyAsText()
        }
        parseMoodAndGenresResponse(responseText)
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to load moods and genres", e)
    }

    /**
     * Parses raw InnerTube JSON for `FEmusic_moods_and_genres` into [MoodGenreSection]s.
     */
    internal fun parseMoodAndGenresResponse(jsonText: String): List<MoodGenreSection> {
        val sections = mutableListOf<MoodGenreSection>()
        try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val tabs = root["contents"]?.jsonObject?.get("singleColumnBrowseResultsRenderer")?.jsonObject?.get("tabs")?.jsonArray
                ?: root["contents"]?.jsonObject?.get("tabs")?.jsonArray
            val contents = tabs?.firstOrNull()?.jsonObject?.get("tabRenderer")?.jsonObject?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject?.get("contents")?.jsonArray
                ?: root["contents"]?.jsonObject?.get("sectionListRenderer")?.jsonObject?.get("contents")?.jsonArray

            contents?.forEach { sectionElement ->
                val sectionObj = sectionElement.jsonObject
                // Handle gridRenderer
                val gridRenderer = sectionObj["gridRenderer"]?.jsonObject
                val musicCarousel = sectionObj["musicCarouselShelfRenderer"]?.jsonObject

                val sectionTitle = gridRenderer?.get("header")?.jsonObject?.get("gridHeaderRenderer")?.jsonObject
                    ?.get("title")?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: gridRenderer?.get("header")?.jsonObject?.get("gridHeaderRenderer")?.jsonObject
                        ?.get("title")?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
                    ?: musicCarousel?.get("header")?.jsonObject?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject
                        ?.get("title")?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: "Categories"

                val itemsArray = gridRenderer?.get("items")?.jsonArray
                    ?: musicCarousel?.get("contents")?.jsonArray

                val items = mutableListOf<MoodGenre>()
                itemsArray?.forEach { itemElem ->
                    val btn = itemElem.jsonObject["musicNavigationButtonRenderer"]?.jsonObject
                    if (btn != null) {
                        val title = btn["buttonText"]?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                            ?: btn["buttonText"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
                            ?: ""

                        val stripeColor = btn["solid"]?.jsonObject?.get("leftStripeColor")?.jsonPrimitive?.content?.toLongOrNull()
                            ?: btn["leftStripeColor"]?.jsonPrimitive?.content?.toLongOrNull()

                        val clickCommand = btn["clickCommand"]?.jsonObject
                        val browseEndpoint = clickCommand?.get("browseEndpoint")?.jsonObject
                        val browseId = browseEndpoint?.get("browseId")?.jsonPrimitive?.content ?: ""
                        val params = browseEndpoint?.get("params")?.jsonPrimitive?.content

                        val thumbnails = btn["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                            ?: btn["thumbnailRenderer"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                                ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                            ?: btn["thumbnails"]?.jsonArray
                        val rawThumbUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                        val thumbnailUrl = rawThumbUrl?.let {
                            if (it.startsWith("//")) "https:$it" else it
                        }

                        if (title.isNotBlank() && browseId.isNotBlank()) {
                            items.add(
                                MoodGenre(
                                    title = title,
                                    stripeColor = stripeColor,
                                    browseId = browseId,
                                    params = params,
                                    thumbnailUrl = thumbnailUrl
                                )
                            )
                        }
                    }
                }

                if (items.isNotEmpty()) {
                    sections.add(MoodGenreSection(title = sectionTitle, items = items))
                }
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Failed to parse moods and genres response: ${e.message}")
        }
        return sections
    }

    /**
     * Browses category shelves (e.g. for a specific mood or genre category browseId) and returns
     * playlists contained in the category carousel/shelf renderers.
     */
    suspend fun browseCategoryShelves(browseId: String, params: String? = null): Result<List<Playlist>> = runCatching {
        val client = YouTubeClient.WEB_REMIX
        NodeLog.d(TAG, "Fetching category shelves (browseId=$browseId, params=$params)")
        val responseText = withRetry {
            httpClient.post("browse") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                setBody(
                    BrowseBody(
                        context = client.toContext(locale, visitorData = visitorData, dataSyncId = dataSyncId),
                        browseId = browseId,
                        params = params
                    )
                )
            }.bodyAsText()
        }
        parseCategoryShelvesResponse(responseText)
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to load category shelves", e)
    }

    /**
     * Parses raw InnerTube JSON for category browse response into a list of [Playlist]s.
     */
    internal fun parseCategoryShelvesResponse(jsonText: String): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val sectionContents = renderers(root, setOf("musicCarouselShelfRenderer", "musicShelfRenderer", "musicPlaylistShelfRenderer", "gridRenderer"))
            sectionContents.forEach { shelf ->
                val itemsArray = (shelf["contents"] ?: shelf["items"]) as? JsonArray

                itemsArray?.forEach { itemElem ->
                    val twoRow = itemElem.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
                        ?: itemElem.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject

                    if (twoRow != null) {
                        val browseId = playlistIdForRenderer(twoRow)

                        val title = twoRow["title"]?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                            ?: twoRow["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content

                        val thumbArray = twoRow["thumbnailRenderer"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                            ?: twoRow["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                            ?: twoRow["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                                ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                        var artworkUrl = thumbArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                        if (artworkUrl != null && artworkUrl.startsWith("//")) {
                            artworkUrl = "https:$artworkUrl"
                        }

                        if (!browseId.isNullOrBlank() && !title.isNullOrBlank()) {
                            val cleanId = if (browseId.startsWith("VL")) browseId.removePrefix("VL") else browseId
                            val syntheticId = generateSyntheticTrackId(cleanId)

                            val subtitleRuns = twoRow["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                            val countStr = subtitleRuns?.lastOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                            val trackCount = countStr?.filter { it.isDigit() }?.toIntOrNull() ?: 0

                            playlists.add(
                                Playlist(
                                    id = syntheticId,
                                    name = title,
                                    trackCount = trackCount,
                                    source = "youtube",
                                    externalId = cleanId,
                                    thumbnailUrl = renderers(twoRow, setOf("thumbnail")).firstNotNullOfOrNull { thumb ->
                                        (thumb["thumbnails"] as? JsonArray)?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                                    }
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Failed to parse category shelves response: ${e.message}")
        }
        return playlists
    }

    /**
     * Fetches a rich multi-shelf online home feed combining Quick Picks (from Home),
     * Trending / New Releases (from Explore), and Top Charts (Top 50 Worldwide & Regional).
     */
    suspend fun getCombinedHomeFeed(
        onHomeLoaded: suspend (List<YouTubeSection>) -> Unit = {}
    ): Result<List<YouTubeSection>> = runCatching {
        val (home, explore, charts) = coroutineScope {
            val homeRequest = async { getHomeRecommendations() }
            val exploreRequest = async { getExploreSections() }
            val chartsRequest = async { getChartsSections() }
            val home = homeRequest.await()
            onHomeLoaded(home.getOrDefault(emptyList()))
            Triple(home, exploreRequest.await(), chartsRequest.await())
        }
        val homeResult = home.getOrDefault(emptyList())
        val exploreResult = explore.getOrDefault(emptyList())
        val chartsResult = charts.getOrDefault(emptyList())

        val combined = mutableListOf<YouTubeSection>()
        combined.addAll(homeResult)

        val existingTitles = homeResult.map { it.title.lowercase().trim() }.toMutableSet()
        for (section in chartsResult) {
            if (section.title.lowercase().trim() !in existingTitles && (section.items.isNotEmpty() || section.playlists.isNotEmpty())) {
                combined.add(section)
                existingTitles.add(section.title.lowercase().trim())
            }
        }

        for (section in exploreResult) {
            if (section.title.lowercase().trim() !in existingTitles && (section.items.isNotEmpty() || section.playlists.isNotEmpty())) {
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

        // A new/restricted account can have no home shelves yet. Keep its library reachable.
        if (combined.isEmpty() && !sessionCookie.isNullOrBlank()) {
            val playlists = fetchUserPlaylists().getOrDefault(emptyList())
            if (playlists.isNotEmpty()) {
                combined.add(YouTubeSection(title = "Your playlists", items = emptyList(), playlists = playlists))
            }
        }
        if (combined.isEmpty()) {
            throw IllegalStateException(
                "No recommendation shelves available",
                home.exceptionOrNull() ?: explore.exceptionOrNull() ?: charts.exceptionOrNull()
            )
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
        val playlists = mutableListOf<Track>()

        // 1. Process tabbed search results
        val tabs = response.contents?.tabbedSearchResultsRenderer?.tabs.orEmpty()
        for (tab in tabs) {
            val sectionList = tab.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
            for (section in sectionList) {
                section.musicShelfRenderer?.contents?.forEach { shelfContent ->
                    shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                        categorizeAndAddSearchItem(renderer, songs, albums, artists, playlists)
                    }
                }
                section.itemSectionRenderer?.contents?.forEach { itemContent ->
                    itemContent.musicResponsiveListItemRenderer?.let { renderer ->
                        categorizeAndAddSearchItem(renderer, songs, albums, artists, playlists)
                    }
                    itemContent.musicShelfRenderer?.contents?.forEach { shelfContent ->
                        shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                            categorizeAndAddSearchItem(renderer, songs, albums, artists, playlists)
                        }
                    }
                }
            }
        }

        // 2. Process continuation contents if present
        response.continuationContents?.musicShelfContinuation?.contents?.forEach { shelfContent ->
            shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                categorizeAndAddSearchItem(renderer, songs, albums, artists, playlists)
            }
        }

        NodeLog.i(TAG, "Search completed for \"$query\": songs=${songs.size}, albums=${albums.size}, artists=${artists.size}, playlists=${playlists.size}")
        YouTubeSearchResult(
            songs = songs.distinctBy { it.id },
            albums = albums.distinctBy { it.id },
            artists = artists.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id }
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
        val visitorData = kotlinx.coroutines.withTimeoutOrNull(3_000L) { getVisitorData() }
        val signatureTimestamp = if (client.useSignatureTimestamp) {
            com.nodeplayer.common.media.CipherSolver.current?.getSignatureTimestamp()
        } else null
        withRetry {
            httpClient.post("player") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                setBody(
                    PlayerBody(
                        context = client.toContext(locale, visitorData = visitorData),
                        videoId = videoId,
                        playbackContext = signatureTimestamp?.let {
                            PlayerBody.PlaybackContext(PlayerBody.PlaybackContext.ContentPlaybackContext(it))
                        }
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

    private fun mapTwoRowItemToPlaylist(item: MusicTwoRowItemRenderer): YouTubePlaylistSummary? {
        val endpoint = item.navigationEndpoint
        val browseId = endpoint?.browseEndpoint?.browseId
        val playlistId = browseId?.takeIf { it.startsWith("VL") }?.removePrefix("VL")
            ?: endpoint?.watchPlaylistEndpoint?.playlistId
            ?: endpoint?.watchEndpoint?.takeIf { it.videoId == null }?.playlistId
            ?: item.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint?.let { play ->
                    play.watchPlaylistEndpoint?.playlistId
                        ?: play.watchEndpoint?.takeIf { it.videoId == null }?.playlistId
                }
            ?: return null
        val title = item.title?.text()?.takeIf { it.isNotBlank() } ?: return null
        return YouTubePlaylistSummary(
            id = playlistId,
            title = title,
            author = item.subtitle?.text()?.takeIf { it.isNotBlank() },
            thumbnailUrl = item.thumbnailRenderer?.musicThumbnailRenderer?.getThumbnailUrl()
                ?: item.thumbnailRenderer?.croppedSquareThumbnailRenderer?.getThumbnailUrl()
        )
    }

    private fun mapTwoRowItemToTrack(item: MusicTwoRowItemRenderer): Track? {
        // Playlist cards are opened as collections, even when they also have a play endpoint.
        if (mapTwoRowItemToPlaylist(item) != null) return null
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

    internal fun mapResponsiveListItemToTrack(item: MusicResponsiveListItemRenderer): Track? {
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
        artists: MutableList<Track>,
        playlists: MutableList<Track>
    ) {
        val secondColRuns = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs.orEmpty()
        val secondColText = secondColRuns.joinToString("") { it.text.orEmpty() }
        val pageType = renderer.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs
            ?.browseEndpointContextMusicConfig?.pageType.orEmpty()
        val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId.orEmpty()

        val playlistIdFromOverlay = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
            ?.playNavigationEndpoint?.watchPlaylistEndpoint?.playlistId

        val isPlaylist = pageType.contains("PLAYLIST", ignoreCase = true) ||
            browseId.startsWith("VL") ||
            browseId.startsWith("PL") ||
            playlistIdFromOverlay != null ||
            secondColText.trim().startsWith("Playlist", ignoreCase = true)

        if (isPlaylist) {
            val effectiveId = playlistIdFromOverlay ?: if (browseId.startsWith("VL")) browseId else if (browseId.isNotEmpty()) "VL$browseId" else ""
            val title = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text.orEmpty()
            if (effectiveId.isNotBlank() && title.isNotBlank()) {
                val author = secondColRuns.getOrNull(2)?.text?.trim()?.ifBlank { null }
                    ?: secondColRuns.firstOrNull()?.text?.trim()?.takeIf { !it.equals("Playlist", ignoreCase = true) }
                    ?: "YouTube Music"
                val thumbUrl = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url.orEmpty()
                val playlistTrack = Track(
                    id = generateSyntheticTrackId(effectiveId),
                    title = title,
                    artist = author,
                    album = effectiveId,
                    durationMs = 0L,
                    path = thumbUrl.ifBlank { "nodeplayer://youtube/playlist/$effectiveId" },
                    formatDescription = "YouTube Playlist",
                    mimeType = "playlist"
                )
                playlists.add(playlistTrack)
                return
            }
        }

        val track = mapResponsiveListItemToTrack(renderer) ?: return

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
            val client = YouTubeClient.WEB_REMIX
            val response = httpClient.post("account/account_menu") {
                header("User-Agent", client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                val reqBody = AccountMenuBody(
                    context = client.toContext(locale, visitorData = null)
                )
                setBody(reqBody)
            }

            if (response.status == HttpStatusCode.OK) {
                val responseText = response.bodyAsText()
                val parsed = parseAccountMenuResponse(responseText)
                if (parsed != null) {
                    parsed
                } else {
                    NodeLog.w(TAG, "Account menu returned OK but activeAccountHeaderRenderer was not found in response: ${responseText.take(200)}")
                    null
                }
            } else if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                NodeLog.w(TAG, "Account menu request unauthorized (${response.status.value})")
                null
            } else {
                NodeLog.w(TAG, "Account menu request failed with status: ${response.status}")
                null
            }
        }
    }

    private fun collectRenderers(root: JsonElement, name: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node[name] as? JsonObject)?.let(out::add)
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    private fun parseAccountMenuResponse(jsonText: String): com.nodeplayer.model.YouTubeAccount? {
        return try {
            val element = json.parseToJsonElement(jsonText)
            val actions = element.jsonObject["actions"]?.jsonArray
            val headerRenderer = actions?.firstNotNullOfOrNull { action ->
                action.jsonObject["openPopupAction"]?.jsonObject?.get("popup")?.jsonObject
                    ?.get("multiPageMenuRenderer")?.jsonObject?.get("header")?.jsonObject
                    ?.get("activeAccountHeaderRenderer")?.jsonObject
            }

            if (headerRenderer != null) {
                val accountName = headerRenderer["accountName"]?.jsonObject?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: headerRenderer["accountName"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
                    ?: "YouTube User"

                val handle = headerRenderer["handleText"]?.jsonObject?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: headerRenderer["channelHandle"]?.jsonObject?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

                val email = headerRenderer["email"]?.jsonObject?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: headerRenderer["email"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content

                val effectiveHandle = handle ?: email?.takeIf { it.startsWith("@") }
                val effectiveEmail = email?.takeIf { it.contains("@") && !it.startsWith("@") }

                val thumbnails = headerRenderer["accountPhoto"]?.jsonObject?.get("thumbnails")?.jsonArray
                    ?: headerRenderer["avatar"]?.jsonObject?.get("thumbnails")?.jsonArray
                val rawAvatarUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                val avatarUrl = normalizeAvatarUrl(rawAvatarUrl)

                com.nodeplayer.model.YouTubeAccount(
                    id = effectiveHandle ?: effectiveEmail ?: accountName,
                    name = accountName,
                    handle = effectiveHandle,
                    avatarUrl = avatarUrl,
                    email = effectiveEmail,
                    isLoggedIn = true
                )
            } else {
                null
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Failed to parse account menu response: ${e.message}")
            null
        }
    }

    /**
     * Fetches user's saved/owned playlists from YouTube Music (`FEmusic_liked_playlists`).
     * Requires an authenticated sessionCookie.
     */
    suspend fun fetchUserPlaylists(): Result<List<YouTubePlaylistSummary>> = runCatching {
        check(!sessionCookie.isNullOrBlank()) { "Sign in to sync YouTube playlists" }
        fetchBrowsePages("FEmusic_liked_playlists")
            .flatMap { parseUserPlaylists(it) }.distinctBy { it.id }
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to fetch user playlists", e)
    }

    /** Playlist cards can carry their browse endpoint in the title, not only at card level. */
    private fun playlistIdForRenderer(item: JsonObject): String? =
        renderers(item, setOf("browseEndpoint")).firstNotNullOfOrNull {
            it["browseId"]?.jsonPrimitive?.contentOrNull?.takeIf { id ->
                id.startsWith("VL") || id.startsWith("PL") || id.startsWith("RD") || id.startsWith("OLAK") || id == "LM" || id == "SE"
            }
        } ?: renderers(item, setOf("watchPlaylistEndpoint", "watchEndpoint", "playlistEditorEndpoint"))
            .firstNotNullOfOrNull { it["playlistId"]?.jsonPrimitive?.contentOrNull }

    private fun renderers(element: JsonElement, names: Set<String>): List<JsonObject> = buildList {
        when (element) {
            is JsonObject -> element.forEach { (name, value) ->
                if (name in names && value is JsonObject) add(value)
                else addAll(renderers(value, names))
            }
            is JsonArray -> element.forEach { addAll(renderers(it, names)) }
            else -> Unit
        }
    }

    /** Follow library/playlist pagination before committing a complete local snapshot. */
    private suspend fun fetchBrowsePages(browseId: String): List<String> {
        val client = YouTubeClient.WEB_REMIX
        val pages = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var continuation: String? = null
        var legacyContinuation = false
        do {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val next = continuation
            val response = withRetry {
                httpClient.post("browse") {
                    header("User-Agent", client.userAgent)
                    header("X-YouTube-Client-Name", client.clientId)
                    header("X-YouTube-Client-Version", client.clientVersion)
                    if (next != null && legacyContinuation) {
                        url.parameters.append("ctoken", next)
                        url.parameters.append("continuation", next)
                    }
                    setBody(BrowseBody(
                        context = client.toContext(locale),
                        browseId = if (next == null || legacyContinuation) browseId else null,
                        continuation = if (legacyContinuation) null else next
                    ))
                }.bodyAsText()
            }
            val root = json.parseToJsonElement(response)
            check(root is JsonObject && root["error"] == null) { "YouTube playlist request failed" }
            pages.add(response)
            val legacy = renderers(root, setOf("nextContinuationData", "reloadContinuationData"))
                .firstNotNullOfOrNull { it["continuation"]?.jsonPrimitive?.contentOrNull }
            legacyContinuation = legacy != null
            continuation = legacy ?: renderers(root, setOf("continuationCommand"))
                .firstNotNullOfOrNull { it["token"]?.jsonPrimitive?.contentOrNull }
            if (continuation != null) {
                check(seen.add(continuation) && pages.size < 100) { "YouTube playlist pagination did not finish" }
            }
        } while (continuation != null)
        return pages
    }

    private fun parseUserPlaylists(jsonText: String): List<YouTubePlaylistSummary> {
        val list = mutableListOf<YouTubePlaylistSummary>()
        try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val contents = renderers(root, setOf(
                "gridRenderer", "musicGridRenderer", "musicShelfRenderer", "musicCarouselShelfRenderer",
                "gridContinuation", "musicShelfContinuation"
            ))
            val appendedItems = renderers(root, setOf("appendContinuationItemsAction"))
            (contents + appendedItems).forEach { shelf ->
                val items = (shelf["items"] ?: shelf["contents"] ?: shelf["continuationItems"]) as? JsonArray

                items?.forEach { itemElement ->
                    val twoRow = itemElement.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
                        ?: itemElement.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                    if (twoRow != null) {
                        val playlistId = playlistIdForRenderer(twoRow)
                        if (playlistId != null) {
                            val title = twoRow["title"]?.jsonObject?.get("runs")?.jsonArray
                                ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                                ?: twoRow["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
                                ?: twoRow["flexColumns"]?.jsonArray?.firstOrNull()?.jsonObject
                                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                                    ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                                ?: "YouTube Playlist"

                            val subtitleRuns = twoRow["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                            val author = subtitleRuns?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                            val countStr = subtitleRuns?.lastOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                            val trackCount = countStr?.filter { it.isDigit() }?.toIntOrNull() ?: 0

                            val thumbnails = twoRow["thumbnailRenderer"]?.jsonObject
                                ?.get("musicThumbnailRenderer")?.jsonObject
                                ?.get("thumbnail")?.jsonObject
                                ?.get("thumbnails")?.jsonArray
                                ?: twoRow["thumbnails"]?.jsonArray
                            val thumbUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

                            val cleanId = if (playlistId.startsWith("VL")) playlistId.removePrefix("VL") else playlistId
                            list.add(
                                YouTubePlaylistSummary(
                                    id = cleanId,
                                    title = title,
                                    author = author,
                                    trackCount = trackCount,
                                    thumbnailUrl = thumbUrl
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Error parsing user playlists JSON: ${e.message}")
        }
        return list
    }

    /**
     * Fetches tracks from a YouTube Music playlist by playlist ID (`VL$playlistId` or browseId).
     */
    suspend fun fetchPlaylistTracks(playlistId: String): Result<List<Track>> = runCatching {
        val cleanId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        fetchBrowsePages(cleanId).flatMap { parsePlaylistTracks(it) }.distinctBy { it.path }
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to fetch playlist tracks for $playlistId", e)
    }

    private fun parsePlaylistTracks(jsonText: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = json.parseToJsonElement(jsonText).jsonObject

            val contents = renderers(root, setOf(
                "musicPlaylistShelfRenderer", "musicShelfRenderer",
                "musicPlaylistShelfContinuation", "musicShelfContinuation", "appendContinuationItemsAction"
            ))
            check(contents.isNotEmpty()) { "YouTube returned an unsupported playlist layout" }
            contents.forEach { shelf ->
                val items = (shelf["contents"] ?: shelf["continuationItems"]) as? JsonArray
                items?.forEach { itemElement ->
                    val responsiveItem = itemElement.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                    if (responsiveItem != null) {
                        val videoId = responsiveItem["navigationEndpoint"]?.jsonObject
                            ?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                            ?: responsiveItem["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                            ?: responsiveItem["menu"]?.jsonObject
                                ?.get("menuRenderer")?.jsonObject
                                ?.get("topLevelButtons")?.jsonArray
                                ?.firstOrNull()?.jsonObject
                                ?.get("likeButtonRenderer")?.jsonObject
                                ?.get("target")?.jsonObject
                                ?.get("videoId")?.jsonPrimitive?.content
                            ?: responsiveItem["overlay"]?.jsonObject
                                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                                ?.get("content")?.jsonObject
                                ?.get("musicPlayButtonRenderer")?.jsonObject
                                ?.get("playNavigationEndpoint")?.jsonObject
                                ?.get("watchEndpoint")?.jsonObject
                                ?.get("videoId")?.jsonPrimitive?.content

                        val flexCols = responsiveItem["flexColumns"]?.jsonArray
                        val title = flexCols?.getOrNull(0)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content ?: "Unknown Title"

                        val subRuns = flexCols?.getOrNull(1)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("runs")?.jsonArray

                        val artist = subRuns?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "Unknown Artist"
                        val album = if ((subRuns?.size ?: 0) >= 3) subRuns?.getOrNull(2)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "YouTube Music" else "YouTube Music"

                        val fixedCols = responsiveItem["fixedColumns"]?.jsonArray
                        val durationStr = fixedCols?.getOrNull(0)?.jsonObject
                            ?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content
                            ?: subRuns?.lastOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

                        if (!videoId.isNullOrBlank()) {
                            tracks.add(
                                Track(
                                    id = generateSyntheticTrackId(videoId),
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    durationMs = parseDurationMs(durationStr),
                                    path = "nodeplayer://youtube/$videoId",
                                    formatDescription = "YouTube Audio"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Error parsing playlist tracks JSON: ${e.message}")
            throw e
        }
        return tracks
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
        private const val AVATAR_SIZE_PARAM = "=s256-c-k-c0x00ffffff-no-rj"

        /**
         * Normalizes YouTube/Google account avatar URLs per D-13:
         * 1. Auto-prefixes "https:" if protocol-relative ("//...").
         * 2. Replaces size parameters (=s... or =w...) with crisp =s256-c-k-c0x00ffffff-no-rj.
         */
        fun normalizeAvatarUrl(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val prefixed = if (url.startsWith("//")) "https:$url" else url
            val regex = Regex("(=[sw]\\d+[^?#]*)$")
            return if (regex.containsMatchIn(prefixed)) {
                prefixed.replace(regex, AVATAR_SIZE_PARAM)
            } else if (!prefixed.contains(AVATAR_SIZE_PARAM)) {
                if (prefixed.contains("?")) {
                    val base = prefixed.substringBefore("?")
                    val query = prefixed.substringAfter("?")
                    "$base$AVATAR_SIZE_PARAM?$query"
                } else {
                    "$prefixed$AVATAR_SIZE_PARAM"
                }
            } else {
                prefixed
            }
        }
    }
}
