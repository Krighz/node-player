package com.nodeplayer.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nodeplayer.model.HiResStatus
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
    val bitrateKbps: Int = 0,
    val formatDescription: String = "",
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String?,
    val dateAdded: Long,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val isLossless: Boolean = false,
    val isHiRes: Boolean = false,
    val hiResStatus: String? = null,
    val hiResConfidence: Float? = null,
    val hiResCutoffHz: Float? = null,
    val loudnessLufs: Float? = null,
    val dynamicRangeScore: Int? = null,
    val dynamicRangeLabel: String? = null
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
        bitrateKbps = bitrateKbps,
        formatDescription = formatDescription,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        dateAdded = dateAdded,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp,
        isFavorite = isFavorite,
        isLossless = isLossless,
        isHiRes = isHiRes,
        hiResStatus = hiResStatus?.let { runCatching { HiResStatus.valueOf(it) }.getOrDefault(HiResStatus.UNKNOWN) } ?: HiResStatus.UNKNOWN,
        hiResConfidence = hiResConfidence ?: 0f,
        hiResCutoffHz = hiResCutoffHz ?: 0f,
        loudnessLufs = loudnessLufs,
        dynamicRangeScore = dynamicRangeScore,
        dynamicRangeLabel = dynamicRangeLabel
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
            bitrateKbps = track.bitrateKbps,
            formatDescription = track.formatDescription,
            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            year = track.year,
            genre = track.genre,
            dateAdded = track.dateAdded,
            playCount = track.playCount,
            lastPlayedTimestamp = track.lastPlayedTimestamp,
            isFavorite = track.isFavorite,
            isLossless = track.isLossless,
            isHiRes = track.isHiRes,
            hiResStatus = track.hiResStatus.name,
            hiResConfidence = track.hiResConfidence,
            hiResCutoffHz = track.hiResCutoffHz,
            loudnessLufs = track.loudnessLufs,
            dynamicRangeScore = track.dynamicRangeScore,
            dynamicRangeLabel = track.dynamicRangeLabel
        )
    }
}

@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rulesJson: String, // JSON serialization of rules
    val sortField: String = "title",
    val sortAscending: Boolean = true,
    val limitCount: Int = 0, // 0 = unlimited
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_listening_stats")
data class DailyListeningStatEntity(
    @PrimaryKey val date: String, // "YYYY-MM-DD"
    val listeningSeconds: Long = 0L,
    val trackPlayCount: Int = 0
)

@Entity(tableName = "scrobble_queue")
data class ScrobbleQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artist: String,
    val track: String,
    val album: String = "",
    val timestampSeconds: Long = 0L,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

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

@Entity(tableName = "audio_presets")
data class AudioPresetEntity(
    @PrimaryKey val id: String, // e.g. "track_1234" or "album_5678"
    val isAlbum: Boolean,
    val presetName: String,
    val configJson: String
)
