package com.nodeplayer.network.innertube

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.taste.HistoryEntry
import com.nodeplayer.library.taste.HistoryResult
import com.nodeplayer.library.taste.TasteHistorySource
import com.nodeplayer.model.Track

/**
 * Implementation of [TasteHistorySource] delegating to authenticated [InnerTubeClient].
 *
 * Implements pure listening history retrieval (`FEmusic_history`) with pagination,
 * status detection (`Paused`, `Empty`, `NotSignedIn`), and strict log privacy (never logging
 * song titles, artist names, or video IDs) per F01.
 */
class InnerTubeTasteHistoryAdapter(
    private val innerTubeClient: InnerTubeClient
) : TasteHistorySource {

    override suspend fun recentHistory(limit: Int): HistoryResult = runCatching {
        val initialResult = innerTubeClient.browse(browseId = "FEmusic_history")
        val initialResponse = initialResult.getOrElse { error ->
            val message = error.message.orEmpty().lowercase()
            if (message.contains("401") || message.contains("403") || message.contains("unauthorized") || message.contains("signin")) {
                NodeLog.w(TAG, "History query status: NotSignedIn")
                return HistoryResult.NotSignedIn
            }
            NodeLog.e(TAG, "History query failed: ${error::class.simpleName}")
            return HistoryResult.Failure(error)
        }

        // 1. Check for paused history state
        if (isHistoryPaused(initialResponse)) {
            NodeLog.i(TAG, "History query status: Paused")
            return HistoryResult.Paused
        }

        val entries = mutableListOf<HistoryEntry>()
        val seenVideoIds = mutableSetOf<String>()

        extractHistoryEntries(initialResponse, entries, seenVideoIds)

        // 2. Follow pagination continuations up to requested limit
        var nextContinuation = findContinuationToken(initialResponse)
        var pageCount = 0
        while (entries.size < limit && nextContinuation != null && pageCount < 10) {
            pageCount++
            val nextResult = innerTubeClient.browse(browseId = null, continuation = nextContinuation)
            val nextResponse = nextResult.getOrNull() ?: break
            extractHistoryEntries(nextResponse, entries, seenVideoIds)
            nextContinuation = findContinuationToken(nextResponse)
        }

        NodeLog.i(TAG, "History query status: Success (count=${entries.size}, limit=$limit, pages=$pageCount)")
        if (entries.isEmpty()) {
            HistoryResult.Empty
        } else {
            HistoryResult.Success(entries.take(limit))
        }
    }.getOrElse { throwable ->
        NodeLog.e(TAG, "History query encountered unexpected exception: ${throwable::class.simpleName}")
        HistoryResult.Failure(throwable)
    }

    override suspend fun getListeningHistory(): Result<List<Track>> = runCatching {
        when (val res = recentHistory(limit = 100)) {
            is HistoryResult.Success -> res.entries.map { entry ->
                Track(
                    id = entry.videoId.hashCode().toLong(),
                    path = "https://music.youtube.com/watch?v=${entry.videoId}",
                    title = entry.title,
                    artist = entry.artistName,
                    artistId = entry.artistId?.hashCode()?.toLong() ?: 0L,
                    album = "YouTube Music",
                    durationMs = entry.durationMs ?: 0L
                )
            }
            is HistoryResult.Paused -> emptyList()
            is HistoryResult.Empty -> emptyList()
            is HistoryResult.NotSignedIn -> emptyList()
            is HistoryResult.Failure -> throw res.cause
        }
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
        NodeLog.e(TAG, "Failed to fetch daily mix recommendations: ${e::class.simpleName}")
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
        NodeLog.e(TAG, "Failed to fetch fallback charts: ${e::class.simpleName}")
    }

    private fun extractHistoryEntries(
        response: BrowseResponse,
        destination: MutableList<HistoryEntry>,
        seenVideoIds: MutableSet<String>
    ) {
        val sectionListContents = buildList {
            addAll(response.contents?.sectionListRenderer?.contents.orEmpty())
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
                ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
            val selected = tabs?.firstOrNull { it.tabRenderer?.selected == true } ?: tabs?.firstOrNull()
            addAll(selected?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty())
            addAll(response.continuationContents?.sectionListContinuation?.contents.orEmpty())
        }

        for (content in sectionListContents) {
            // Check direct musicShelfRenderer
            content.musicShelfRenderer?.contents?.forEach { shelfContent ->
                shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                    innerTubeClient.mapResponsiveListItemToTrack(renderer)?.let { track ->
                        val vid = track.extractVideoId() ?: track.id.toString()
                        if (vid.isNotBlank() && seenVideoIds.add(vid)) {
                            destination.add(
                                HistoryEntry(
                                    videoId = vid,
                                    title = track.title,
                                    artistName = track.artist,
                                    artistId = track.artistId.toString(),
                                    durationMs = if (track.durationMs > 0) track.durationMs else null
                                )
                            )
                        }
                    }
                }
            }

            // Check itemSectionRenderer
            content.itemSectionRenderer?.contents?.forEach { itemContent ->
                itemContent.musicResponsiveListItemRenderer?.let { renderer ->
                    innerTubeClient.mapResponsiveListItemToTrack(renderer)?.let { track ->
                        val vid = track.extractVideoId() ?: track.id.toString()
                        if (vid.isNotBlank() && seenVideoIds.add(vid)) {
                            destination.add(
                                HistoryEntry(
                                    videoId = vid,
                                    title = track.title,
                                    artistName = track.artist,
                                    artistId = track.artistId.toString(),
                                    durationMs = if (track.durationMs > 0) track.durationMs else null
                                )
                            )
                        }
                    }
                }
                itemContent.musicShelfRenderer?.contents?.forEach { shelfContent ->
                    shelfContent.musicResponsiveListItemRenderer?.let { renderer ->
                        innerTubeClient.mapResponsiveListItemToTrack(renderer)?.let { track ->
                            val vid = track.extractVideoId() ?: track.id.toString()
                            if (vid.isNotBlank() && seenVideoIds.add(vid)) {
                                destination.add(
                                    HistoryEntry(
                                        videoId = vid,
                                        title = track.title,
                                        artistName = track.artist,
                                        artistId = track.artistId.toString(),
                                        durationMs = if (track.durationMs > 0) track.durationMs else null
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findContinuationToken(response: BrowseResponse): String? {
        // 1. Check sectionListContinuation
        val secCont = response.continuationContents?.sectionListContinuation?.continuations
            ?.firstNotNullOfOrNull { it.nextContinuationData?.continuation }
        if (secCont != null) return secCont

        // 2. Check musicShelfContinuation
        val shelfCont = response.continuationContents?.musicShelfContinuation?.continuations
            ?.firstNotNullOfOrNull { it.nextContinuationData?.continuation }
        if (shelfCont != null) return shelfCont

        // 3. Check sectionListRenderer continuations in contents
        val sectionList = response.contents?.sectionListRenderer
            ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
        return sectionList?.continuations?.firstNotNullOfOrNull { it.nextContinuationData?.continuation }
    }

    private fun isHistoryPaused(response: BrowseResponse): Boolean {
        val sectionContents = response.contents?.sectionListRenderer?.contents.orEmpty()
        for (content in sectionContents) {
            val itemContents = content.itemSectionRenderer?.contents.orEmpty()
            for (item in itemContents) {
                val message = item.messageRenderer ?: continue
                val text = message.text?.text().orEmpty().lowercase()
                if (text.contains("paused") || text.contains("turned off") || text.contains("history is off")) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val TAG = "InnerTubeTasteHistory"
    }
}
