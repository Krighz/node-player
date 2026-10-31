package com.nodeplayer.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nodeplayer.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val size: Long,
    val mimeType: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String?,
    val dateAdded: Long,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val isLossless: Boolean = false,
    val isHiRes: Boolean = false
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        path = path,
        size = size,
        mimeType = mimeType,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        channels = channels,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        dateAdded = dateAdded,
        isLossless = isLossless,
        isHiRes = isHiRes
    )

    companion object {
        fun fromTrack(track: Track): TrackEntity = TrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            artistId = track.artistId,
            album = track.album,
            albumId = track.albumId,
            durationMs = track.durationMs,
            path = track.path,
            size = track.size,
            mimeType = track.mimeType,
            sampleRate = track.sampleRate,
            bitDepth = track.bitDepth,
            channels = track.channels,
            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            year = track.year,
            genre = track.genre,
            dateAdded = track.dateAdded,
            isLossless = track.isLossless,
            isHiRes = track.isHiRes
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)
