package com.nodeplayer.desktop.data

import com.nodeplayer.library.repository.TrackRepository
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.model.Playlist
import com.nodeplayer.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DesktopTrackRepositoryAdapter(
    private val database: DesktopTrackDatabase
) : TrackRepository {

    override fun getAllTracks(): Flow<List<Track>> = flowOf(database.getAllTracks())

    override suspend fun getAllTracksList(): List<Track> = database.getAllTracks()

    override suspend fun getTrackById(id: Long): Track? {
        return database.getAllTracks().find { it.id == id }
    }

    override suspend fun insertTracks(tracks: List<Track>) {
        database.insertOrUpdateTracks(tracks)
    }

    override suspend fun clearTracks() {}

    override suspend fun incrementPlayCount(id: Long) {
        database.incrementPlayCount(id)
    }

    override suspend fun setFavorite(id: Long, isFav: Boolean) {
        database.setFavorite(id, isFav)
    }

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return flowOf(database.getAllTracks().filter { it.isFavorite })
    }

    override fun getAllScannedFolders(): Flow<List<MusicFolder>> {
        return flowOf(database.extractFolders(database.getAllTracks()))
    }

    override suspend fun insertScannedFolders(folders: List<MusicFolder>) {}

    override suspend fun setFolderEnabled(path: String, isEnabled: Boolean) {}

    override suspend fun setTracksHiddenForFolder(folderPrefix: String, isHidden: Boolean) {}

    override fun getAllPlaylists(): Flow<List<Playlist>> = flowOf(database.getAllPlaylists())

    override suspend fun insertPlaylist(playlist: Playlist): Long = database.insertPlaylist(playlist)

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        database.renamePlaylist(playlistId, newName)
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        database.deletePlaylist(playlistId)
    }

    override fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> = flowOf(database.getTracksForPlaylist(playlistId))

    override suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        database.addTrackToPlaylist(playlistId, trackId)
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        database.removeTrackFromPlaylist(playlistId, trackId)
    }

    override suspend fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        database.reorderPlaylistTracks(playlistId, orderedTrackIds)
    }

    override suspend fun updateTrackAudioAnalysis(
        id: Long,
        status: String,
        confidence: Float,
        cutoffHz: Float,
        lufs: Float,
        drScore: Int,
        drLabel: String
    ) {
        database.updateTrackAudioAnalysis(id, status, confidence, cutoffHz, lufs, drScore, drLabel)
    }

    override suspend fun recordTrackPlayed(id: Long, timestamp: Long) {
        database.recordTrackPlayed(id, timestamp)
    }
}
