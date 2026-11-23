package com.nodeplayer.music.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Versioned snapshot model representing user data stored as `user_music_db.json`
 * in Google Drive's hidden `appDataFolder` space.
 */
data class SyncSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val lastSyncedAt: String = Instant.now().toString(),
    val playlists: List<SyncPlaylist> = emptyList(),
    val savedItems: List<SyncSavedItem> = emptyList(),
    val songStats: Map<String, SyncSongStat> = emptyMap(),
    val playbackHistory: List<SyncPlaybackHistoryEntry> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val FILE_NAME = "user_music_db.json"
        const val MAX_HISTORY_ENTRIES = 200
    }
}

data class SyncPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SyncSavedItem(
    val trackId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = timestamp
)

data class SyncSongStat(
    val songId: String,
    val title: String,
    val artist: String,
    val totalPlayTimeSeconds: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L
)

data class SyncPlaybackHistoryEntry(
    val songId: String,
    val playedAt: Long,
    val durationPlayedSeconds: Long
)
