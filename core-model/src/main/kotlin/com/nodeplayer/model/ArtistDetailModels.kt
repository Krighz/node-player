package com.nodeplayer.model

data class SongSectionMarker(
    val title: String, // "Intro", "Verse 1", "Chorus", "Drop", "Bridge", "Outro"
    val startMs: Long,
    val endMs: Long? = null
)

data class ArtistPageData(
    val artistName: String,
    val browseId: String,
    val heroImageUrl: String? = null,
    val isVerified: Boolean = true,
    val subscribersText: String? = null,
    val monthlyListenersText: String? = null,
    val bio: String? = null,
    val latestRelease: ArtistReleaseItem? = null,
    val topSongs: List<Track> = emptyList(),
    val albums: List<ArtistReleaseItem> = emptyList(),
    val singlesAndEPs: List<ArtistReleaseItem> = emptyList(),
    val videos: List<ArtistVideoItem> = emptyList(),
    val livePerformances: List<ArtistVideoItem> = emptyList(),
    val featuredOn: List<ArtistPlaylistItem> = emptyList(),
    val playlists: List<ArtistPlaylistItem> = emptyList(),
    val similarArtists: List<SimilarArtistItem> = emptyList(),
    val isFollowed: Boolean = false
)

data class ArtistReleaseItem(
    val id: String, // browseId or albumId
    val title: String,
    val year: String,
    val type: String = "Album", // "Album", "Single", "EP"
    val artworkUrl: String? = null,
    val isExplicit: Boolean = false
)

data class ArtistVideoItem(
    val videoId: String,
    val title: String,
    val viewsText: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null
)

data class ArtistPlaylistItem(
    val playlistId: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null
)

data class SimilarArtistItem(
    val browseId: String,
    val name: String,
    val audienceText: String? = null,
    val avatarUrl: String? = null
)

data class ArtistSectionShowAll(
    val title: String,
    val items: List<Any>,
    val type: String
)
