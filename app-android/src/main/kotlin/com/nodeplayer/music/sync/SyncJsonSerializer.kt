package com.nodeplayer.music.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Handles robust JSON serialization and deserialization for [SyncSnapshot].
 */
object SyncJsonSerializer {

    fun serialize(snapshot: SyncSnapshot, indentSpaces: Int = 2): String {
        val root = JSONObject()
        root.put("schemaVersion", snapshot.schemaVersion)
        root.put("lastSyncedAt", snapshot.lastSyncedAt)

        // 1. Playlists
        val playlistsArray = JSONArray()
        for (playlist in snapshot.playlists) {
            val pObj = JSONObject()
            pObj.put("id", playlist.id)
            pObj.put("name", playlist.name)
            val trackIdsArray = JSONArray()
            playlist.trackIds.forEach { trackIdsArray.put(it) }
            pObj.put("trackIds", trackIdsArray)
            pObj.put("createdAt", playlist.createdAt)
            pObj.put("updatedAt", playlist.updatedAt)
            playlistsArray.put(pObj)
        }
        root.put("playlists", playlistsArray)

        // 2. Saved Items (Favorites)
        val savedItemsArray = JSONArray()
        for (item in snapshot.savedItems) {
            val sObj = JSONObject()
            sObj.put("trackId", item.trackId)
            sObj.put("timestamp", item.timestamp)
            sObj.put("updatedAt", item.updatedAt)
            savedItemsArray.put(sObj)
        }
        root.put("savedItems", savedItemsArray)

        // 3. Song Stats (Keyed by songId)
        val songStatsObj = JSONObject()
        for ((songId, stat) in snapshot.songStats) {
            val statObj = JSONObject()
            statObj.put("title", stat.title)
            statObj.put("artist", stat.artist)
            statObj.put("totalPlayTimeSeconds", stat.totalPlayTimeSeconds)
            statObj.put("playCount", stat.playCount)
            statObj.put("lastPlayedAt", stat.lastPlayedAt)
            songStatsObj.put(songId, statObj)
        }
        root.put("songStats", songStatsObj)

        // 4. Playback History (Bounded, latest 200)
        val historyArray = JSONArray()
        val boundedHistory = snapshot.playbackHistory
            .sortedByDescending { it.playedAt }
            .take(SyncSnapshot.MAX_HISTORY_ENTRIES)

        for (entry in boundedHistory) {
            val hObj = JSONObject()
            hObj.put("songId", entry.songId)
            hObj.put("playedAt", entry.playedAt)
            hObj.put("durationPlayedSeconds", entry.durationPlayedSeconds)
            historyArray.put(hObj)
        }
        root.put("playbackHistory", historyArray)

        return if (indentSpaces > 0) root.toString(indentSpaces) else root.toString()
    }

    fun deserialize(jsonString: String): SyncSnapshot {
        if (jsonString.isBlank()) {
            return SyncSnapshot()
        }

        val root = JSONObject(jsonString)
        val schemaVersion = root.optInt("schemaVersion", 1)
        val lastSyncedAt = root.optString("lastSyncedAt", Instant.now().toString())

        // 1. Playlists
        val playlists = mutableListOf<SyncPlaylist>()
        val playlistsArray = root.optJSONArray("playlists")
        if (playlistsArray != null) {
            for (i in 0 until playlistsArray.length()) {
                val pObj = playlistsArray.optJSONObject(i) ?: continue
                val id = pObj.optString("id", "")
                val name = pObj.optString("name", "Untitled")
                val trackIds = mutableListOf<String>()
                val tArray = pObj.optJSONArray("trackIds") ?: pObj.optJSONArray("tracks")
                if (tArray != null) {
                    for (j in 0 until tArray.length()) {
                        val trackId = tArray.optString(j, "")
                        if (trackId.isNotBlank()) {
                            trackIds.add(trackId)
                        }
                    }
                }
                val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = pObj.optLong("updatedAt", createdAt)
                if (id.isNotBlank()) {
                    playlists.add(
                        SyncPlaylist(
                            id = id,
                            name = name,
                            trackIds = trackIds,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                    )
                }
            }
        }

        // 2. Saved Items
        val savedItems = mutableListOf<SyncSavedItem>()
        val savedArray = root.optJSONArray("savedItems")
        if (savedArray != null) {
            for (i in 0 until savedArray.length()) {
                val sObj = savedArray.optJSONObject(i)
                if (sObj != null) {
                    val trackId = sObj.optString("trackId", sObj.optString("id", ""))
                    val timestamp = sObj.optLong("timestamp", sObj.optLong("savedAt", sObj.optLong("favoritedAt", System.currentTimeMillis())))
                    val updatedAt = sObj.optLong("updatedAt", timestamp)
                    if (trackId.isNotBlank()) {
                        savedItems.add(SyncSavedItem(trackId = trackId, timestamp = timestamp, updatedAt = updatedAt))
                    }
                } else {
                    // Could be plain string IDs in array
                    val rawId = savedArray.optString(i, "")
                    if (rawId.isNotBlank()) {
                        savedItems.add(SyncSavedItem(trackId = rawId, timestamp = System.currentTimeMillis()))
                    }
                }
            }
        }

        // 3. Song Stats (support either JSON object map or array)
        val songStats = mutableMapOf<String, SyncSongStat>()
        val statsObj = root.optJSONObject("songStats")
        if (statsObj != null) {
            val keys = statsObj.keys()
            while (keys.hasNext()) {
                val songId = keys.next()
                val item = statsObj.optJSONObject(songId) ?: continue
                songStats[songId] = SyncSongStat(
                    songId = songId,
                    title = item.optString("title", ""),
                    artist = item.optString("artist", ""),
                    totalPlayTimeSeconds = item.optLong("totalPlayTimeSeconds", 0L),
                    playCount = item.optInt("playCount", 0),
                    lastPlayedAt = item.optLong("lastPlayedAt", 0L)
                )
            }
        } else {
            val statsArray = root.optJSONArray("songStats")
            if (statsArray != null) {
                for (i in 0 until statsArray.length()) {
                    val item = statsArray.optJSONObject(i) ?: continue
                    val songId = item.optString("songId", item.optString("id", ""))
                    if (songId.isNotBlank()) {
                        songStats[songId] = SyncSongStat(
                            songId = songId,
                            title = item.optString("title", ""),
                            artist = item.optString("artist", ""),
                            totalPlayTimeSeconds = item.optLong("totalPlayTimeSeconds", 0L),
                            playCount = item.optInt("playCount", 0),
                            lastPlayedAt = item.optLong("lastPlayedAt", 0L)
                        )
                    }
                }
            }
        }

        // 4. Playback History
        val history = mutableListOf<SyncPlaybackHistoryEntry>()
        val historyArray = root.optJSONArray("playbackHistory")
        if (historyArray != null) {
            for (i in 0 until historyArray.length()) {
                val hObj = historyArray.optJSONObject(i) ?: continue
                val songId = hObj.optString("songId", "")
                val playedAt = hObj.optLong("playedAt", 0L)
                val duration = hObj.optLong("durationPlayedSeconds", 0L)
                if (songId.isNotBlank()) {
                    history.add(
                        SyncPlaybackHistoryEntry(
                            songId = songId,
                            playedAt = playedAt,
                            durationPlayedSeconds = duration
                        )
                    )
                }
            }
        }

        return SyncSnapshot(
            schemaVersion = schemaVersion,
            lastSyncedAt = lastSyncedAt,
            playlists = playlists,
            savedItems = savedItems,
            songStats = songStats,
            playbackHistory = history.sortedByDescending { it.playedAt }.take(SyncSnapshot.MAX_HISTORY_ENTRIES)
        )
    }
}
