package com.nodeplayer.model

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long = 0,
    val trackCount: Int = 0,
    val year: Int = 0,
    val artworkUri: String? = null
)

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val albumCount: Int = 0
)

data class MusicFolder(
    val name: String,
    val path: String,
    val trackCount: Int = 0
)

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val source: String = "local", // "local", "youtube", "spotify"
    val externalId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val thumbnailUrl: String? = null
)

data class QueueItem(
    val queueId: String,
    val track: Track,
    val isPlaying: Boolean = false
)
