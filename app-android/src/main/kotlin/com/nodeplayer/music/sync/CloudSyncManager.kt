package com.nodeplayer.music.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.music.auth.GoogleAuthManager
import com.nodeplayer.music.data.PlaybackHistoryEntity
import com.nodeplayer.music.data.PlaylistEntity
import com.nodeplayer.music.data.PlaylistTrackCrossRef
import com.nodeplayer.music.data.SongStatsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Orchestrates local-first cloud synchronization with Google Drive's hidden `appDataFolder`.
 * Enforces dirty-flag optimization, safe merge resolution, and periodic WorkManager scheduling.
 */
object CloudSyncManager {

    private const val TAG = "CloudSyncManager"
    private const val PREFS_NAME = "node_cloud_sync_prefs"

    // Settings Keys
    const val FREQ_HOURLY = "HOURLY"
    const val FREQ_DAILY = "DAILY"
    const val FREQ_MANUAL = "MANUAL"

    private const val KEY_SYNC_FREQUENCY = "sync_frequency"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"
    private const val KEY_HAS_UNSYNCED_CHANGES = "has_unsynced_changes"
    private const val KEY_LAST_STATUS = "last_status"

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncFrequency = MutableStateFlow(FREQ_MANUAL)
    val syncFrequency: StateFlow<String> = _syncFrequency.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow(0L)
    val lastSyncedAt: StateFlow<Long> = _lastSyncedAt.asStateFlow()

    private val _hasUnsyncedChanges = MutableStateFlow(false)
    val hasUnsyncedChanges: StateFlow<Boolean> = _hasUnsyncedChanges.asStateFlow()

    private val _statusMessage = MutableStateFlow("Idle")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun initialize(context: Context) {
        val prefs = getPrefs(context)
        _syncFrequency.value = prefs.getString(KEY_SYNC_FREQUENCY, FREQ_MANUAL) ?: FREQ_MANUAL
        _lastSyncedAt.value = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        _hasUnsyncedChanges.value = prefs.getBoolean(KEY_HAS_UNSYNCED_CHANGES, false)
        _statusMessage.value = prefs.getString(KEY_LAST_STATUS, "Idle") ?: "Idle"

        GoogleAuthManager.initialize(context)

        // Reschedule WorkManager according to current frequency setting
        schedulePeriodicSync(context, _syncFrequency.value)

        NodeLog.i(TAG, "CloudSyncManager initialized (frequency=${_syncFrequency.value}, lastSynced=${_lastSyncedAt.value}, dirty=${_hasUnsyncedChanges.value})")
    }

    fun setSyncFrequency(context: Context, frequency: String) {
        val validFrequency = when (frequency) {
            FREQ_HOURLY -> FREQ_HOURLY
            FREQ_DAILY -> FREQ_DAILY
            else -> FREQ_MANUAL
        }
        _syncFrequency.value = validFrequency
        getPrefs(context).edit().putString(KEY_SYNC_FREQUENCY, validFrequency).apply()

        schedulePeriodicSync(context, validFrequency)
        NodeLog.i(TAG, "Updated cloud sync frequency: $validFrequency")
    }

    fun markUnsyncedChanges(context: Context) {
        _hasUnsyncedChanges.value = true
        getPrefs(context).edit().putBoolean(KEY_HAS_UNSYNCED_CHANGES, true).apply()
        NodeLog.d(TAG, "Dirty flag set: hasUnsyncedChanges = true")
    }

    private fun schedulePeriodicSync(context: Context, frequency: String) {
        val workManager = WorkManager.getInstance(context)

        if (frequency == FREQ_MANUAL || !GoogleAuthManager.isLoggedIn.value) {
            workManager.cancelUniqueWork(CloudSyncWorker.WORK_NAME)
            NodeLog.d(TAG, "Periodic cloud sync disabled/cancelled")
            return
        }

        val repeatInterval = if (frequency == FREQ_HOURLY) 1L else 24L
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(repeatInterval, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            CloudSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        NodeLog.i(TAG, "Enqueued periodic sync worker every $repeatInterval hour(s)")
    }

    /**
     * Executes the cloud sync flow with safe merge conflict resolution.
     */
    suspend fun performSync(context: Context, isManualTrigger: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            return@withContext Result.failure(IllegalStateException("A sync operation is already in progress"))
        }

        val token = GoogleAuthManager.getValidAccessToken(context)
        if (token.isNullOrBlank()) {
            val msg = "Not signed in to Google Account"
            updateStatus(context, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        // Dirty-flag optimization: skip upload if nothing changed unless user explicitly clicked "Sync Now"
        if (!isManualTrigger && !_hasUnsyncedChanges.value) {
            val msg = "Up to date (no unsynced changes)"
            NodeLog.i(TAG, "Dirty-flag optimization: $msg. Skipping sync.")
            updateStatus(context, msg)
            return@withContext Result.success(Unit)
        }

        _isSyncing.value = true
        updateStatus(context, "Syncing with Google Drive...")

        try {
            val db = (context.applicationContext as? NodePlayerApplication)?.database
                ?: return@withContext Result.failure(IllegalStateException("Database not available"))
            val dao = db.playerDao()
            val apiClient = GoogleDriveApiClient(context)

            // Step 1: Read local snapshot
            val localSnapshot = buildLocalSnapshot(dao)

            // Step 2: Check for existing backup in appDataFolder
            val fileIdResult = apiClient.findBackupFileId()
            val fileId = fileIdResult.getOrNull()

            val remoteSnapshot: SyncSnapshot? = if (!fileId.isNullOrBlank()) {
                val downloadRes = apiClient.downloadBackup(fileId)
                if (downloadRes.isSuccess) {
                    SyncJsonSerializer.deserialize(downloadRes.getOrThrow())
                } else {
                    NodeLog.w(TAG, "Failed to download remote backup, will treat as initial: ${downloadRes.exceptionOrNull()?.message}")
                    null
                }
            } else {
                null
            }

            // Step 3: Safe Merge (conflict resolution)
            val mergedSnapshot = SyncMergeEngine.merge(localSnapshot, remoteSnapshot)

            // Step 4: Persist merged state into local Room DB
            applyMergedSnapshotToLocal(dao, mergedSnapshot)

            // Step 5: Upload merged snapshot to Google Drive appDataFolder
            val serializedMerged = SyncJsonSerializer.serialize(mergedSnapshot)
            if (!fileId.isNullOrBlank()) {
                apiClient.updateBackup(fileId, serializedMerged).getOrThrow()
            } else {
                apiClient.createBackup(serializedMerged).getOrThrow()
            }

            // Step 6: Update sync state
            val now = System.currentTimeMillis()
            _lastSyncedAt.value = now
            _hasUnsyncedChanges.value = false
            val successMsg = "Synced successfully"
            updateStatus(context, successMsg)

            getPrefs(context).edit()
                .putLong(KEY_LAST_SYNCED_AT, now)
                .putBoolean(KEY_HAS_UNSYNCED_CHANGES, false)
                .putString(KEY_LAST_STATUS, successMsg)
                .apply()

            NodeLog.i(TAG, "Cloud sync completed successfully at $now")
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = "Sync failed: ${e.message}"
            NodeLog.e(TAG, errorMsg, e)
            updateStatus(context, errorMsg)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Deletes `user_music_db.json` from the user's `appDataFolder`.
     */
    suspend fun deleteCloudBackup(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            return@withContext Result.failure(IllegalStateException("Cannot delete backup while sync is in progress"))
        }

        try {
            val apiClient = GoogleDriveApiClient(context)
            val fileId = apiClient.findBackupFileId().getOrNull()

            if (!fileId.isNullOrBlank()) {
                apiClient.deleteBackup(fileId).getOrThrow()
            }

            _lastSyncedAt.value = 0L
            _hasUnsyncedChanges.value = true
            val msg = "Cloud backup deleted"
            updateStatus(context, msg)

            getPrefs(context).edit()
                .putLong(KEY_LAST_SYNCED_AT, 0L)
                .putBoolean(KEY_HAS_UNSYNCED_CHANGES, true)
                .putString(KEY_LAST_STATUS, msg)
                .apply()

            NodeLog.i(TAG, "Deleted cloud backup from Google Drive appDataFolder")
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = "Failed to delete backup: ${e.message}"
            NodeLog.e(TAG, errorMsg, e)
            updateStatus(context, errorMsg)
            Result.failure(e)
        }
    }

    private suspend fun buildLocalSnapshot(dao: com.nodeplayer.music.data.PlayerDao): SyncSnapshot {
        // 1. Playlists
        val storedPlaylists = dao.getAllPlaylistsList()
        val syncPlaylists = storedPlaylists.map { pl ->
            val trackIds = dao.getPlaylistTrackIds(pl.id).map { it.toString() }
            SyncPlaylist(
                id = pl.id.toString(),
                name = pl.name,
                trackIds = trackIds,
                createdAt = pl.createdAt,
                updatedAt = pl.updatedAt
            )
        }

        // 2. Saved Items (Favorites)
        val allTracks = dao.getAllTracksList()
        val savedItems = allTracks.filter { it.isFavorite }.map { track ->
            val ts = if (track.lastPlayedTimestamp > 0L) track.lastPlayedTimestamp else track.dateAdded
            SyncSavedItem(
                trackId = track.id.toString(),
                timestamp = ts,
                updatedAt = ts
            )
        }

        // 3. Song Stats
        val dbStats = dao.getAllSongStats()
        val statsMap = LinkedHashMap<String, SyncSongStat>()

        if (dbStats.isNotEmpty()) {
            for (stat in dbStats) {
                statsMap[stat.songId] = SyncSongStat(
                    songId = stat.songId,
                    title = stat.title,
                    artist = stat.artist,
                    totalPlayTimeSeconds = stat.totalPlayTimeSeconds,
                    playCount = stat.playCount,
                    lastPlayedAt = stat.lastPlayedAt
                )
            }
        } else {
            // Seed from tracks if song_stats table hasn't been populated yet
            allTracks.filter { it.playCount > 0 }.forEach { track ->
                val songId = track.id.toString()
                statsMap[songId] = SyncSongStat(
                    songId = songId,
                    title = track.title,
                    artist = track.artist,
                    totalPlayTimeSeconds = (track.playCount * (track.durationMs / 1000L)).coerceAtLeast(0L),
                    playCount = track.playCount,
                    lastPlayedAt = track.lastPlayedTimestamp
                )
            }
        }

        // 4. Playback History
        val historyEntries = dao.getPlaybackHistory(SyncSnapshot.MAX_HISTORY_ENTRIES).map { h ->
            SyncPlaybackHistoryEntry(
                songId = h.songId,
                playedAt = h.playedAt,
                durationPlayedSeconds = h.durationPlayedSeconds
            )
        }

        return SyncSnapshot(
            schemaVersion = SyncSnapshot.CURRENT_SCHEMA_VERSION,
            playlists = syncPlaylists,
            savedItems = savedItems,
            songStats = statsMap,
            playbackHistory = historyEntries
        )
    }

    private suspend fun applyMergedSnapshotToLocal(
        dao: com.nodeplayer.music.data.PlayerDao,
        merged: SyncSnapshot
    ) {
        // 1. Playlists
        for (pl in merged.playlists) {
            val localId = pl.id.toLongOrNull()
            if (localId != null) {
                val existing = dao.getAllPlaylistsList().find { it.id == localId }
                val targetId = if (existing == null) {
                    dao.insertPlaylist(
                        PlaylistEntity(
                            id = localId,
                            name = pl.name,
                            createdAt = pl.createdAt,
                            updatedAt = pl.updatedAt
                        )
                    )
                } else {
                    if (pl.updatedAt > existing.updatedAt) {
                        dao.renamePlaylist(existing.id, pl.name)
                    }
                    existing.id
                }

                // Sync playlist tracks
                if (pl.trackIds.isNotEmpty()) {
                    dao.clearPlaylistTracks(targetId)
                    val crossRefs = pl.trackIds.mapIndexedNotNull { index, tId ->
                        tId.toLongOrNull()?.let { trackId ->
                            PlaylistTrackCrossRef(playlistId = targetId, trackId = trackId, position = index)
                        }
                    }
                    if (crossRefs.isNotEmpty()) {
                        dao.insertPlaylistTracks(crossRefs)
                    }
                }
            }
        }

        // 2. Saved Items (Favorites)
        for (saved in merged.savedItems) {
            val trackId = saved.trackId.toLongOrNull()
            if (trackId != null) {
                dao.setFavorite(trackId, true)
            }
        }

        // 3. Song Stats
        val songStatsEntities = merged.songStats.values.map { stat ->
            // Also update track play count and timestamp if greater
            stat.songId.toLongOrNull()?.let { trackId ->
                dao.updatePlayCount(trackId, stat.playCount)
            }
            SongStatsEntity(
                songId = stat.songId,
                title = stat.title,
                artist = stat.artist,
                totalPlayTimeSeconds = stat.totalPlayTimeSeconds,
                playCount = stat.playCount,
                lastPlayedAt = stat.lastPlayedAt
            )
        }
        if (songStatsEntities.isNotEmpty()) {
            dao.upsertSongStatsList(songStatsEntities)
        }

        // 4. Playback History
        val historyEntities = merged.playbackHistory.map { h ->
            PlaybackHistoryEntity(
                songId = h.songId,
                playedAt = h.playedAt,
                durationPlayedSeconds = h.durationPlayedSeconds
            )
        }
        if (historyEntities.isNotEmpty()) {
            dao.insertPlaybackHistoryList(historyEntities)
            dao.trimPlaybackHistory(SyncSnapshot.MAX_HISTORY_ENTRIES)
        }
    }

    private fun updateStatus(context: Context, status: String) {
        _statusMessage.value = status
        getPrefs(context).edit().putString(KEY_LAST_STATUS, status).apply()
    }
}
