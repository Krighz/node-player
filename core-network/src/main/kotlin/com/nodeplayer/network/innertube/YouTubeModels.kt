package com.nodeplayer.network.innertube

import com.nodeplayer.model.Track
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/* ==========================================================================
   InnerTube Client Configuration & Request Bodies
   ========================================================================== */

@Serializable
data class YouTubeLocale(
    val gl: String, // Geolocation (e.g. US)
    val hl: String, // Host language (e.g. en)
)

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
    val friendlyName: String? = null,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val useWebPoTokens: Boolean = false,
) {
    fun toContext(locale: YouTubeLocale, visitorData: String? = null, dataSyncId: String? = null) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData
        ),
        user = Context.User(
            onBehalfOfUser = if (loginSupported) dataSyncId else null
        ),
    )

    companion object {
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"
        const val API_URL_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/youtubei/v1/"

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20260213.00.00",
            clientId = "1",
            userAgent = USER_AGENT_WEB,
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260213.01.00",
            clientId = "67",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val TVHTML5_SIMPLY_EMBEDDED_PLAYER = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = "85",
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
            loginSupported = true,
            loginRequired = false,
            useSignatureTimestamp = true,
            isEmbedded = true,
        )

        val ANDROID_VR_1_65_10 = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            buildId = "SQ3A.220605.009.A1",
            packageName = "com.google.android.apps.youtube.vr.oculus",
            friendlyName = "Android VR 1.65",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        val ANDROID_VR = ANDROID_VR_1_65_10

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.03.1",
            clientId = "5",
            userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
            osVersion = "18.2.22C152",
        )

        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "1.3.21O771",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
            friendlyName = "visionOS",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "6.37.50",
            clientId = "73",
            userAgent = "com.google.android.apps.youtube.music/6.37.50 (Linux; U; Android 14) gzip",
            friendlyName = "Android Music",
            loginSupported = true,
            useSignatureTimestamp = true
        )

        val IOS_MUSIC = YouTubeClient(
            clientName = "IOS_MUSIC",
            clientVersion = "6.37.1",
            clientId = "74",
            userAgent = "com.google.ios.youtubemusic/6.37.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
            friendlyName = "iOS Music",
            loginSupported = true,
            useSignatureTimestamp = true
        )
    }
}

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
    val request: Request = Request(),
    val user: User = User()
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: String? = null,
        val gl: String,
        val hl: String,
        val visitorData: String? = null,
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )

    @Serializable
    data class Request(
        val internalExperimentFlags: List<String> = emptyList(),
        val useSsl: Boolean = true,
    )

    @Serializable
    data class User(
        val lockedSafetyMode: Boolean = false,
        val onBehalfOfUser: String? = null,
    )
}

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String? = null,
    val params: String? = null,
    val continuation: String? = null
)

@Serializable
data class SearchBody(
    val context: Context,
    val query: String? = null,
    val params: String? = null,
)

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)

@Serializable
data class NextBody(
    val context: Context,
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val index: Int? = null,
    val params: String? = null,
    val continuation: String? = null,
)

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int
        )
    }

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String
    )
}

/* ==========================================================================
   InnerTube Primitives & Common Elements
   ========================================================================== */

@Serializable
data class ResponseContext(
    val visitorData: String? = null,
    val serviceTrackingParams: List<ServiceTrackingParam>? = null,
) {
    @Serializable
    data class ServiceTrackingParam(
        val params: List<Param>? = null,
        val service: String? = null,
    ) {
        @Serializable
        data class Param(
            val key: String? = null,
            val value: String? = null,
        )
    }
}

@Serializable
data class Runs(
    val runs: List<Run>? = null,
) {
    fun text(): String = runs?.joinToString("") { it.text } ?: ""
}

@Serializable
data class Run(
    val text: String = "",
    val navigationEndpoint: NavigationEndpoint? = null,
)

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail> = emptyList(),
)

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
    val musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer? = null,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer? = null,
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails? = null,
        val thumbnailCrop: String? = null,
        val thumbnailScale: String? = null,
    ) {
        fun getThumbnailUrl(): String? = thumbnail?.thumbnails?.lastOrNull()?.url
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails? = null,
        val backupRenderer: MusicThumbnailRenderer? = null,
    )

    fun getThumbnailUrl(): String? =
        musicThumbnailRenderer?.getThumbnailUrl()
            ?: croppedSquareThumbnailRenderer?.getThumbnailUrl()
}

@Serializable
data class Icon(
    val iconType: String? = null,
)

@Serializable
data class Badges(
    val musicInlineBadgeRenderer: MusicInlineBadgeRenderer? = null,
) {
    @Serializable
    data class MusicInlineBadgeRenderer(
        val icon: Icon? = null,
    )
}

@Serializable
data class Button(
    val buttonRenderer: ButtonRenderer? = null,
) {
    @Serializable
    data class ButtonRenderer(
        val text: Runs? = null,
        val navigationEndpoint: NavigationEndpoint? = null,
        val command: NavigationEndpoint? = null,
        val icon: Icon? = null,
    )
}

@Serializable
data class Menu(
    val menuRenderer: MenuRenderer? = null,
) {
    @Serializable
    data class MenuRenderer(
        val items: List<Item>? = null,
        val topLevelButtons: List<TopLevelButton>? = null,
    ) {
        @Serializable
        data class Item(
            val menuNavigationItemRenderer: MenuNavigationItemRenderer? = null,
            val menuServiceItemRenderer: MenuServiceItemRenderer? = null,
        ) {
            @Serializable
            data class MenuNavigationItemRenderer(
                val text: Runs? = null,
                val icon: Icon? = null,
                val navigationEndpoint: NavigationEndpoint? = null,
            )

            @Serializable
            data class MenuServiceItemRenderer(
                val text: Runs? = null,
                val icon: Icon? = null,
                val serviceEndpoint: NavigationEndpoint? = null,
            )
        }

        @Serializable
        data class TopLevelButton(
            val buttonRenderer: ButtonRenderer? = null,
        ) {
            @Serializable
            data class ButtonRenderer(
                val icon: Icon? = null,
                val navigationEndpoint: NavigationEndpoint? = null,
            )
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Continuation(
    @JsonNames("nextContinuationData", "nextRadioContinuationData")
    val nextContinuationData: NextContinuationData? = null,
) {
    @Serializable
    data class NextContinuationData(
        val continuation: String? = null,
    )
}

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint? = null,
) {
    @Serializable
    data class ContinuationEndpoint(
        val continuationCommand: ContinuationCommand? = null,
    ) {
        @Serializable
        data class ContinuationCommand(
            val token: String? = null,
        )
    }
}

/* ==========================================================================
   Navigation & Endpoints
   ========================================================================== */

@Serializable
data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val watchPlaylistEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null,
    val searchEndpoint: SearchEndpoint? = null,
    val continuationCommand: ContinuationCommand? = null,
) {
    @Serializable
    data class ContinuationCommand(
        val token: String? = null,
    )

    val anyWatchEndpoint: WatchEndpoint?
        get() = watchEndpoint ?: watchPlaylistEndpoint
}

@Serializable
data class WatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val params: String? = null,
    val index: Int? = null,
    val watchEndpointMusicSupportedConfigs: WatchEndpointMusicSupportedConfigs? = null,
) {
    @Serializable
    data class WatchEndpointMusicSupportedConfigs(
        val watchEndpointMusicConfig: WatchEndpointMusicConfig? = null,
    ) {
        @Serializable
        data class WatchEndpointMusicConfig(
            val musicVideoType: String? = null,
        )
    }
}

@Serializable
data class BrowseEndpoint(
    val browseId: String? = null,
    val params: String? = null,
    val browseEndpointContextSupportedConfigs: BrowseEndpointContextSupportedConfigs? = null,
) {
    @Serializable
    data class BrowseEndpointContextSupportedConfigs(
        val browseEndpointContextMusicConfig: BrowseEndpointContextMusicConfig? = null,
    ) {
        @Serializable
        data class BrowseEndpointContextMusicConfig(
            val pageType: String? = null,
        ) {
            companion object {
                const val MUSIC_PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
                const val MUSIC_PAGE_TYPE_AUDIOBOOK = "MUSIC_PAGE_TYPE_AUDIOBOOK"
                const val MUSIC_PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
                const val MUSIC_PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
                const val MUSIC_PAGE_TYPE_USER_CHANNEL = "MUSIC_PAGE_TYPE_USER_CHANNEL"
            }
        }
    }
}

@Serializable
data class SearchEndpoint(
    val params: String? = null,
    val query: String? = null,
)

/* ==========================================================================
   Browse & Tabs Renderers
   ========================================================================== */

@Serializable
data class Tabs(
    val tabs: List<Tab>? = null,
) {
    @Serializable
    data class Tab(
        val tabRenderer: TabRenderer? = null,
    ) {
        @Serializable
        data class TabRenderer(
            val title: String? = null,
            val content: Content? = null,
            val endpoint: NavigationEndpoint? = null,
        ) {
            @Serializable
            data class Content(
                val sectionListRenderer: SectionListRenderer? = null,
            )
        }
    }
}

@Serializable
data class SectionListRenderer(
    val header: Header? = null,
    val contents: List<Content>? = null,
    val continuations: List<Continuation>? = null,
) {
    @Serializable
    data class Header(
        val chipCloudRenderer: ChipCloudRenderer? = null,
    ) {
        @Serializable
        data class ChipCloudRenderer(
            val chips: List<Chip>? = null,
        ) {
            @Serializable
            data class Chip(
                val chipCloudChipRenderer: ChipCloudChipRenderer? = null,
            ) {
                @Serializable
                data class ChipCloudChipRenderer(
                    val isSelected: Boolean = false,
                    val navigationEndpoint: NavigationEndpoint? = null,
                    val text: Runs? = null,
                    val uniqueId: String? = null,
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class Content(
        @JsonNames("musicImmersiveCarouselShelfRenderer")
        val musicCarouselShelfRenderer: MusicCarouselShelfRenderer? = null,
        val musicShelfRenderer: MusicShelfRenderer? = null,
        val itemSectionRenderer: ItemSectionRenderer? = null,
    )
}

@Serializable
data class ItemSectionRenderer(
    val contents: List<Content>? = null,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val musicShelfRenderer: MusicShelfRenderer? = null,
    )
}

@Serializable
data class MusicCarouselShelfRenderer(
    val header: Header? = null,
    val contents: List<Content> = emptyList(),
    val itemSize: String? = null,
    val numItemsPerColumn: Int? = null,
) {
    @Serializable
    data class Header(
        val musicCarouselShelfBasicHeaderRenderer: MusicCarouselShelfBasicHeaderRenderer? = null,
    ) {
        @Serializable
        data class MusicCarouselShelfBasicHeaderRenderer(
            val strapline: Runs? = null,
            val title: Runs? = null,
            val thumbnail: ThumbnailRenderer? = null,
            val moreContentButton: Button? = null,
        )
    }

    @Serializable
    data class Content(
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    )
}

@Serializable
data class MusicShelfRenderer(
    val title: Runs? = null,
    val contents: List<Content>? = null,
    val continuations: List<Continuation>? = null,
    val bottomEndpoint: NavigationEndpoint? = null,
    val moreContentButton: Button? = null,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )
}

@Serializable
data class MusicTwoRowItemRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val subtitleBadges: List<Badges>? = null,
    val menu: Menu? = null,
    val thumbnailRenderer: ThumbnailRenderer? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val thumbnailOverlay: MusicResponsiveListItemRenderer.Overlay? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MusicResponsiveListItemRenderer(
    val badges: List<Badges>? = null,
    val fixedColumns: List<FlexColumn>? = null,
    val flexColumns: List<FlexColumn> = emptyList(),
    val thumbnail: ThumbnailRenderer? = null,
    val menu: Menu? = null,
    val playlistItemData: PlaylistItemData? = null,
    val overlay: Overlay? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
) {
    val videoId: String?
        get() = playlistItemData?.videoId
            ?: navigationEndpoint?.watchEndpoint?.videoId
            ?: overlay?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId

    @Serializable
    data class FlexColumn(
        @JsonNames("musicResponsiveListItemFixedColumnRenderer")
        val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer? = null,
    ) {
        @Serializable
        data class MusicResponsiveListItemFlexColumnRenderer(
            val text: Runs? = null,
        )
    }

    @Serializable
    data class PlaylistItemData(
        val playlistSetVideoId: String? = null,
        val videoId: String,
    )

    @Serializable
    data class Overlay(
        val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer? = null,
    ) {
        @Serializable
        data class MusicItemThumbnailOverlayRenderer(
            val content: Content? = null,
        ) {
            @Serializable
            data class Content(
                val musicPlayButtonRenderer: MusicPlayButtonRenderer? = null,
            ) {
                @Serializable
                data class MusicPlayButtonRenderer(
                    val playNavigationEndpoint: NavigationEndpoint? = null,
                )
            }
        }
    }
}

/* ==========================================================================
   Search & Search Suggestions
   ========================================================================== */

@Serializable
data class SearchResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
) {
    @Serializable
    data class Contents(
        val tabbedSearchResultsRenderer: Tabs? = null,
    )

    @Serializable
    data class ContinuationContents(
        val musicShelfContinuation: MusicShelfContinuation? = null,
    ) {
        @Serializable
        data class MusicShelfContinuation(
            val contents: List<Content> = emptyList(),
            val continuations: List<Continuation>? = null,
        ) {
            @Serializable
            data class Content(
                val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
            )
        }
    }
}

@Serializable
data class GetSearchSuggestionsResponse(
    val contents: List<Content>? = null,
) {
    @Serializable
    data class Content(
        val searchSuggestionsSectionRenderer: SearchSuggestionsSectionRenderer? = null,
    )
}

@Serializable
data class SearchSuggestionsSectionRenderer(
    val contents: List<Content> = emptyList(),
) {
    @Serializable
    data class Content(
        val searchSuggestionRenderer: SearchSuggestionRenderer? = null,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    ) {
        @Serializable
        data class SearchSuggestionRenderer(
            val suggestion: Runs? = null,
            val navigationEndpoint: NavigationEndpoint? = null,
        )
    }
}

@Serializable
data class SearchSuggestion(
    val query: String,
)

/* ==========================================================================
   Browse Response
   ========================================================================== */

@Serializable
data class BrowseResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
    val responseContext: ResponseContext? = null,
    val background: ThumbnailRenderer? = null,
) {
    @Serializable
    data class Contents(
        val singleColumnBrowseResultsRenderer: Tabs? = null,
        val sectionListRenderer: SectionListRenderer? = null,
    )

    @Serializable
    data class ContinuationContents(
        val sectionListContinuation: SectionListContinuation? = null,
        val musicShelfContinuation: MusicShelfRenderer? = null,
    ) {
        @Serializable
        data class SectionListContinuation(
            val contents: List<SectionListRenderer.Content> = emptyList(),
            val continuations: List<Continuation>? = null,
        )
    }
}

/* ==========================================================================
   Next Response & Radio
   ========================================================================== */

@Serializable
data class NextResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
    val currentVideoEndpoint: NavigationEndpoint? = null,
) {
    @Serializable
    data class Contents(
        val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer? = null,
    ) {
        @Serializable
        data class SingleColumnMusicWatchNextResultsRenderer(
            val tabbedRenderer: TabbedRenderer? = null,
        ) {
            @Serializable
            data class TabbedRenderer(
                val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer? = null,
            ) {
                @Serializable
                data class WatchNextTabbedResultsRenderer(
                    val tabs: List<Tabs.Tab> = emptyList(),
                )
            }
        }
    }

    @Serializable
    data class ContinuationContents(
        val playlistPanelContinuation: PlaylistPanelRenderer? = null,
    )
}

@Serializable
data class PlaylistPanelRenderer(
    val title: String? = null,
    val titleText: Runs? = null,
    val shortBylineText: Runs? = null,
    val contents: List<Content> = emptyList(),
    val isInfinite: Boolean? = null,
    val numItemsToShow: Int? = null,
    val playlistId: String? = null,
    val continuations: List<Continuation>? = null,
) {
    @Serializable
    data class Content(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
    )
}

@Serializable
data class PlaylistPanelVideoRenderer(
    val title: Runs? = null,
    val lengthText: Runs? = null,
    val longBylineText: Runs? = null,
    val shortBylineText: Runs? = null,
    val badges: List<Badges>? = null,
    val videoId: String? = null,
    val playlistSetVideoId: String? = null,
    val selected: Boolean = false,
    val thumbnail: Thumbnails? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
)

/* ==========================================================================
   Player Response & Streaming Data
   ========================================================================== */

@Serializable
data class PlayerResponse(
    val responseContext: ResponseContext? = null,
    val playabilityStatus: PlayabilityStatus? = null,
    val playerConfig: PlayerConfig? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig? = null,
    ) {
        @Serializable
        data class AudioConfig(
            val loudnessDb: Double? = null,
            val perceptualLoudnessDb: Double? = null,
        )
    }

    @Serializable
    data class StreamingData(
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format> = emptyList(),
        val expiresInSeconds: Int? = null,
    ) {
        @Serializable
        data class Format(
            val itag: Int,
            val url: String? = null,
            val mimeType: String = "",
            val bitrate: Int? = null,
            val width: Int? = null,
            val height: Int? = null,
            val contentLength: Long? = null,
            val quality: String? = null,
            val fps: Int? = null,
            val qualityLabel: String? = null,
            val averageBitrate: Int? = null,
            val audioQuality: String? = null,
            val approxDurationMs: String? = null,
            val audioSampleRate: Int? = null,
            val audioChannels: Int? = null,
            val loudnessDb: Double? = null,
            val lastModified: Long? = null,
            val signatureCipher: String? = null,
            val cipher: String? = null,
        ) {
            val isAudio: Boolean
                get() = mimeType.startsWith("audio/", ignoreCase = true)
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String,
        val title: String? = null,
        val author: String? = null,
        val channelId: String? = null,
        val lengthSeconds: String? = null,
        val musicVideoType: String? = null,
        val viewCount: String? = null,
        val thumbnail: Thumbnails? = null,
    )
}

/* ==========================================================================
   Domain Wrapper Models for Node Player
   ========================================================================== */

data class YouTubeSection(
    val title: String,
    val items: List<Track>,
)

enum class SearchFilter(val paramValue: String?) {
    ALL(null),
    SONGS("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    VIDEOS("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"),
    ALBUMS("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"),
    ARTISTS("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"),
    FEATURED_PLAYLISTS("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"),
    COMMUNITY_PLAYLISTS("EgeKAQQoAEABagoQAxAEEAoQCRAF");
}

data class YouTubeSearchResult(
    val songs: List<Track> = emptyList(),
    val albums: List<Track> = emptyList(),
    val artists: List<Track> = emptyList(),
)

data class YouTubeStreamInfo(
    val videoId: String,
    val title: String,
    val artist: String,
    val streamUrl: String?,
    val durationSeconds: Long,
    val itag: Int?,
    val mimeType: String?,
    val bitrate: Long?,
    val contentLength: Long?,
    val loudnessDb: Double?,
)
