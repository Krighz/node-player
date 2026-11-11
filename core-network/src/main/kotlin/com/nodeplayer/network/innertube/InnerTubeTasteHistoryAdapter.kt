package com.nodeplayer.network.innertube

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.taste.TasteHistorySource
import com.nodeplayer.model.Track

/**
 * Implementation of [TasteHistorySource] delegating to [InnerTubeClient].
 *
 * Provides access to YouTube Music history, daily mix tracks, track radio queues,
 * and top charts without leaking networking details to `:core-library`.
 */
class InnerTubeTasteHistoryAdapter(
    private val innerTubeClient: InnerTubeClient
) : TasteHistorySource {

    override suspend fun getListeningHistory(): Result<List<Track>> = runCatching {
        val response = innerTubeClient.browse("FEmusic_history").getOrThrow()
        val tracks = mutableListOf<Track>()

        // 1. Check sections parsed via parseBrowseSections (carousels & shelves)
        val sections = innerTubeClient.parseBrowseSections(response)
        for (section in sections) {
            tracks.addAll(section.items)
        }

        // 2. Fallback / supplement: check itemSectionRenderer contents or direct shelves
        val sectionListContents = response.contents?.sectionListRenderer?.contents
            ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: emptyList()

        for (content in sectionListContents) {
            content.itemSectionRenderer?.contents?.forEach { itemContent ->
                itemContent.musicResponsiveListItemRenderer?.let { renderer ->
                    innerTubeClient.mapResponsiveListItemToTrack(renderer)?.let { tracks.add(it) }
                }
                itemContent.musicShelfRenderer?.contents?.forEach { shelfContent ->
                    shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                        innerTubeClient.mapResponsiveListItemToTrack(renderer)?.let { tracks.add(it) }
                    }
                }
            }
        }

        NodeLog.d(TAG, "Parsed ${tracks.size} tracks from listening history (FEmusic_history)")
        tracks
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to fetch listening history", e)
    }

    override suspend fun getDailyMixTracks(): Result<List<Track>> = runCatching {
        val homeSections = innerTubeClient.getHomeRecommendations().getOrThrow()
        val mixTracks = mutableListOf<Track>()
        for (section in homeSections) {
            mixTracks.addAll(section.items)
        }
        NodeLog.d(TAG, "Parsed ${mixTracks.size} daily mix tracks from home recommendations")
        mixTracks
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to fetch daily mix recommendations", e)
    }

    override suspend fun getRadioForTrack(videoId: String): Result<List<Track>> {
        val playlistId = "RDAMVM$videoId"
        return innerTubeClient.getNextRadio(videoId = videoId, playlistId = playlistId)
    }

    override suspend fun getFallbackTopCharts(): Result<List<Track>> = runCatching {
        val chartsSections = innerTubeClient.getChartsSections().getOrThrow()
        val chartTracks = mutableListOf<Track>()
        for (section in chartsSections) {
            chartTracks.addAll(section.items)
        }
        NodeLog.d(TAG, "Parsed ${chartTracks.size} fallback chart tracks from FEmusic_charts")
        chartTracks
    }.onFailure { e ->
        NodeLog.e(TAG, "Failed to fetch fallback charts", e)
    }

    companion object {
        private const val TAG = "InnerTubeTasteHistoryAdapter"
    }
}
