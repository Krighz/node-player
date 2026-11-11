package com.nodeplayer.network.library

import com.nodeplayer.model.Track
import kotlinx.serialization.Serializable

enum class OnlineMediaType {
    ALBUM,
    PLAYLIST,
    ARTIST,
    TRACK
}

@Serializable
data class OnlineMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
    val type: OnlineMediaType = OnlineMediaType.TRACK,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class OnlinePlaylist(
    val id: String,
    val title: String,
    val author: String = "",
    val trackCount: Int = 0,
    val artworkUrl: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class OnlineAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int = 0,
    val trackCount: Int = 0,
    val artworkUrl: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class OnlineArtist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SavedTrackRecord(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String,
    val artworkUrl: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        path = path,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp
    )

    companion object {
        fun fromTrack(track: Track, addedTimestamp: Long = System.currentTimeMillis()): SavedTrackRecord =
            SavedTrackRecord(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                path = track.path,
                isFavorite = track.isFavorite,
                playCount = track.playCount,
                lastPlayedTimestamp = track.lastPlayedTimestamp,
                addedTimestamp = addedTimestamp
            )
    }
}

@Serializable
data class OnlineLibraryDiskPayload(
    val savedTracks: List<SavedTrackRecord> = emptyList(),
    val playlists: List<OnlinePlaylist> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class OnlineLibraryState(
    val songs: List<Track> = emptyList(),
    val albums: List<OnlineAlbum> = emptyList(),
    val artists: List<OnlineArtist> = emptyList(),
    val playlists: List<OnlinePlaylist> = emptyList(),
    val recentlyAdded: List<OnlineMediaItem> = emptyList()
)
