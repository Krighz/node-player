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

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class QueueItem(
    val queueId: String,
    val track: Track,
    val isPlaying: Boolean = false
)
