package com.nodeplayer.library.repository

import com.nodeplayer.model.*
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getAllTracks(): Flow<List<Track>>
    suspend fun getAllTracksList(): List<Track>
    suspend fun getTrackById(id: Long): Track?
    suspend fun insertTracks(tracks: List<Track>)
    suspend fun clearTracks()
    suspend fun incrementPlayCount(id: Long)
    suspend fun setFavorite(id: Long, isFav: Boolean)
    fun getFavoriteTracks(): Flow<List<Track>>

    fun getAllScannedFolders(): Flow<List<MusicFolder>>
    suspend fun insertScannedFolders(folders: List<MusicFolder>)
    suspend fun setFolderEnabled(path: String, isEnabled: Boolean)
    suspend fun setTracksHiddenForFolder(folderPrefix: String, isHidden: Boolean)

    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun insertPlaylist(playlist: Playlist): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>>
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)
    suspend fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>)

    suspend fun updateTrackAudioAnalysis(
        id: Long,
        status: String,
        confidence: Float,
        cutoffHz: Float,
        lufs: Float,
        drScore: Int,
        drLabel: String
    )
    suspend fun recordTrackPlayed(id: Long, timestamp: Long)
}
