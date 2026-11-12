package com.nodeplayer.music.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM tracks WHERE isHidden = 0 ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllRawTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isHidden = 0 ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllTracksList(): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllRawTracksList(): List<TrackEntity>

    @Query("UPDATE tracks SET isHidden = :isHidden WHERE path LIKE :folderPrefix || '%'")
    suspend fun setTracksHiddenForFolder(folderPrefix: String, isHidden: Boolean)

    @Query("SELECT * FROM scanned_folders ORDER BY name COLLATE NOCASE ASC")
    fun getAllScannedFolders(): Flow<List<ScannedFolderEntity>>

    @Query("SELECT * FROM scanned_folders ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllScannedFoldersList(): List<ScannedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedFolders(folders: List<ScannedFolderEntity>)

    @Query("UPDATE scanned_folders SET isEnabled = :isEnabled WHERE path = :path")
    suspend fun setFolderEnabled(path: String, isEnabled: Boolean)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks")
    suspend fun clearTracks()

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)

    @Query("UPDATE tracks SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)

    @Query("SELECT * FROM tracks WHERE isFavorite = 1")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllPlaylistsList(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE externalId = :externalId LIMIT 1")
    suspend fun getPlaylistByExternalId(externalId: String): PlaylistEntity?

    @Query("UPDATE playlists SET name = :newName, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPlaylistPosition(playlistId: Long): Int?

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTrackIds(playlistId: Long): List<Long>

    @Query("UPDATE tracks SET playCount = :count WHERE id = :id")
    suspend fun updatePlayCount(id: Long, count: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(crossRefs: List<PlaylistTrackCrossRef>)

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM audio_presets WHERE id = :id")
    suspend fun getAudioPresetById(id: String): AudioPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioPreset(preset: AudioPresetEntity)

    @Query("DELETE FROM audio_presets WHERE id = :id")
    suspend fun deleteAudioPresetById(id: String)

    // --- Audio Analysis Queries ---
    @Query("""
        UPDATE tracks 
        SET hiResStatus = :status, 
            hiResConfidence = :confidence, 
            hiResCutoffHz = :cutoffHz, 
            loudnessLufs = :lufs, 
            dynamicRangeScore = :drScore, 
            dynamicRangeLabel = :drLabel,
            analysisAttempts = 0
        WHERE id = :id
    """)
    suspend fun updateTrackAudioAnalysis(
        id: Long, 
        status: String, 
        confidence: Float, 
        cutoffHz: Float, 
        lufs: Float, 
        drScore: Int, 
        drLabel: String
    )

    @Query("SELECT * FROM tracks WHERE isHidden = 0 AND (hiResStatus IS NULL OR loudnessLufs IS NULL OR dynamicRangeScore IS NULL) AND analysisAttempts < 3")
    suspend fun getTracksNeedingAnalysis(): List<TrackEntity>

    @Query("UPDATE tracks SET analysisAttempts = analysisAttempts + 1 WHERE id = :id")
    suspend fun incrementAnalysisAttempts(id: Long)

    @Query("UPDATE tracks SET hiResStatus = 'FAILED', dynamicRangeLabel = 'Failed', loudnessLufs = 0.0, analysisAttempts = 3 WHERE id = :id")
    suspend fun markTrackAnalysisFailed(id: Long)

    // --- Playback & Listening Stats ---
    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE id = :id")
    suspend fun recordTrackPlayed(id: Long, timestamp: Long)

    @Query("""
        INSERT OR REPLACE INTO daily_listening_stats (date, listeningSeconds, trackPlayCount) 
        VALUES (
            :date, 
            COALESCE((SELECT listeningSeconds FROM daily_listening_stats WHERE date = :date), 0) + :addedSeconds, 
            COALESCE((SELECT trackPlayCount FROM daily_listening_stats WHERE date = :date), 0) + :addedPlays
        )
    """)
    suspend fun addListeningTime(date: String, addedSeconds: Long, addedPlays: Int)

    @Query("SELECT * FROM daily_listening_stats ORDER BY date DESC")
    fun getAllDailyStats(): Flow<List<DailyListeningStatEntity>>

    @Query("SELECT * FROM daily_listening_stats ORDER BY date DESC")
    suspend fun getAllDailyStatsList(): List<DailyListeningStatEntity>

    @Query("DELETE FROM daily_listening_stats")
    suspend fun clearListeningStats()

    // --- Smart Playlists ---
    @Query("SELECT * FROM smart_playlists ORDER BY createdAt DESC")
    fun getAllSmartPlaylists(): Flow<List<SmartPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmartPlaylist(playlist: SmartPlaylistEntity): Long

    @Delete
    suspend fun deleteSmartPlaylist(playlist: SmartPlaylistEntity)

    // --- Last.fm Scrobble Queue ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueScrobble(scrobble: ScrobbleQueueEntity): Long

    @Query("SELECT * FROM scrobble_queue ORDER BY timestampSeconds ASC")
    suspend fun getPendingScrobbles(): List<ScrobbleQueueEntity>

    @Query("DELETE FROM scrobble_queue WHERE id = :id")
    suspend fun removeScrobble(id: Long)

    @Query("UPDATE scrobble_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementScrobbleRetry(id: Long)

    @Query("SELECT COUNT(*) FROM scrobble_queue")
    fun getScrobbleQueueCount(): Flow<Int>
}

@Database(
    entities = [
        TrackEntity::class, 
        PlaylistEntity::class, 
        PlaylistTrackCrossRef::class, 
        AudioPresetEntity::class,
        SmartPlaylistEntity::class,
        DailyListeningStatEntity::class,
        ScrobbleQueueEntity::class,
        ScannedFolderEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class NodeDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao

    companion object {
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Clear stale format metadata so the updated parser re-reads accurate headers
                db.execSQL("UPDATE tracks SET formatDescription = '', sampleRate = 0, bitDepth = 0, bitrateKbps = 0")
            }
        }
    }
}
