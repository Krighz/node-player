package com.nodeplayer.network.search

import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.YouTubeSearchResult

enum class SearchCategory(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists")
}

data class CategorizedSearchResults(
    val activeCategory: SearchCategory,
    val displayItems: List<Track>,
    val songs: List<Track> = emptyList(),
    val artists: List<Track> = emptyList(),
    val albums: List<Track> = emptyList(),
    val playlists: List<Track> = emptyList(),
    val totalCount: Int = displayItems.size
) {
    val isEmpty: Boolean get() = totalCount == 0
}

/**
 * Fast client-side categorization engine.
 * Partitions [YouTubeSearchResult] across All, Songs, Artists, Albums, and Playlists.
 */
object SearchCategorizer {

    fun categorize(
        rawResult: YouTubeSearchResult,
        category: SearchCategory = SearchCategory.ALL
    ): CategorizedSearchResults {
        val songs = rawResult.songs
        val albums = rawResult.albums
        val artists = rawResult.artists

        // Extract playlist items (from explicit playlists parsed or fallback to tagged albums)
        val playlists = if (rawResult.playlists.isNotEmpty()) {
            rawResult.playlists
        } else {
            albums.filter {
                it.mimeType.contains("playlist", ignoreCase = true) ||
                it.formatDescription.contains("playlist", ignoreCase = true) ||
                it.album.contains("playlist", ignoreCase = true)
            }
        }

        val displayItems = when (category) {
            SearchCategory.ALL -> {
                val blended = ArrayList<Track>(songs.size + playlists.size + albums.size + artists.size)
                blended.addAll(songs)
                blended.addAll(playlists)
                blended.addAll(albums.filterNot { it in playlists })
                blended.addAll(artists)
                blended
            }
            SearchCategory.SONGS -> songs
            SearchCategory.ARTISTS -> artists
            SearchCategory.ALBUMS -> albums.filterNot { it in playlists }
            SearchCategory.PLAYLISTS -> playlists
        }

        return CategorizedSearchResults(
            activeCategory = category,
            displayItems = displayItems,
            songs = songs,
            artists = artists,
            albums = albums,
            playlists = playlists,
            totalCount = displayItems.size
        )
    }
}
