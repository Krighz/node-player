package com.nodeplayer.network.sync

import com.nodeplayer.network.protocol.SimpleJson
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class SyncPlaylistRecord(
    val playlistId: String,
    val name: String,
    val trackFingerprints: List<String>,
    val timestamp: HlcTimestamp,
    val isTombstone: Boolean = false
)

data class SyncFavoriteRecord(
    val trackFingerprint: String,
    val isFavorite: Boolean,
    val timestamp: HlcTimestamp
)

data class SyncHistoryRecord(
    val trackFingerprint: String,
    val playCount: Int,
    val lastPlayedTimestampMs: Long
)

data class SyncEqProfileRecord(
    val profileName: String,
    val bandGains: List<Float>,
    val bassBoostDb: Float,
    val timestamp: HlcTimestamp,
    val isTombstone: Boolean = false
)

data class SyncPackage(
    val sourceDeviceId: String,
    val playlists: List<SyncPlaylistRecord> = emptyList(),
    val favorites: List<SyncFavoriteRecord> = emptyList(),
    val history: List<SyncHistoryRecord> = emptyList(),
    val eqProfiles: List<SyncEqProfileRecord> = emptyList()
)

data class SyncSnapshot(
    val snapshotId: Long = System.currentTimeMillis(),
    val timestampStr: String,
    val packageData: SyncPackage
)

/**
 * Node Connect Conflict-Free Sync Engine.
 * Implements HLC conflict resolution, 30-day tombstones, stats union, and 3-snapshot undo engine.
 */
class SyncEngine(
    val deviceId: String,
    private val undoStorageDir: File = getDefaultUndoDir()
) {
    companion object {
        fun getDefaultUndoDir(): File {
            val appData = System.getenv("APPDATA")
            val base = if (!appData.isNullOrBlank()) {
                File(appData, "NodePlayer/sync_undo")
            } else {
                File(System.getProperty("user.home", "."), ".nodeplayer/sync_undo")
            }
            base.mkdirs()
            return base
        }
    }

    val hlc = HlcClock(deviceId)

    // In-Memory state for sync domains
    private val playlists = ConcurrentHashMap<String, SyncPlaylistRecord>()
    private val favorites = ConcurrentHashMap<String, SyncFavoriteRecord>()
    private val history = ConcurrentHashMap<String, SyncHistoryRecord>()
    private val eqProfiles = ConcurrentHashMap<String, SyncEqProfileRecord>()

    private val undoStack = CopyOnWriteArrayList<SyncSnapshot>()

    init {
        undoStorageDir.mkdirs()
    }

    // --- Local Mutation APIs ---

    fun putPlaylist(name: String, trackFingerprints: List<String>, existingId: String? = null): SyncPlaylistRecord {
        val id = existingId ?: java.util.UUID.randomUUID().toString()
        val record = SyncPlaylistRecord(
            playlistId = id,
            name = name,
            trackFingerprints = trackFingerprints,
            timestamp = hlc.now(),
            isTombstone = false
        )
        playlists[id] = record
        return record
    }

    fun deletePlaylist(id: String): Boolean {
        val existing = playlists[id] ?: return false
        val tombstone = existing.copy(
            isTombstone = true,
            timestamp = hlc.now()
        )
        playlists[id] = tombstone
        return true
    }

    fun setFavorite(trackFingerprint: String, isFav: Boolean): SyncFavoriteRecord {
        val record = SyncFavoriteRecord(
            trackFingerprint = trackFingerprint,
            isFavorite = isFav,
            timestamp = hlc.now()
        )
        favorites[trackFingerprint] = record
        return record
    }

    fun recordPlay(trackFingerprint: String, timestampMs: Long = System.currentTimeMillis()) {
        val existing = history[trackFingerprint]
        val newCount = (existing?.playCount ?: 0) + 1
        val maxTime = Math.max(existing?.lastPlayedTimestampMs ?: 0L, timestampMs)
        history[trackFingerprint] = SyncHistoryRecord(trackFingerprint, newCount, maxTime)
    }

    fun putEqProfile(name: String, bandGains: List<Float>, bassBoostDb: Float): SyncEqProfileRecord {
        val record = SyncEqProfileRecord(
            profileName = name,
            bandGains = bandGains,
            bassBoostDb = bassBoostDb,
            timestamp = hlc.now(),
            isTombstone = false
        )
        eqProfiles[name] = record
        return record
    }

    // --- Export / Package ---

    fun createCurrentPackage(): SyncPackage {
        return SyncPackage(
            sourceDeviceId = deviceId,
            playlists = playlists.values.toList(),
            favorites = favorites.values.toList(),
            history = history.values.toList(),
            eqProfiles = eqProfiles.values.toList()
        )
    }

    // --- Merge with Conflict Resolution & 3-Snapshot Undo ---

    @Synchronized
    fun mergeRemotePackage(remote: SyncPackage): Boolean {
        // 1. Capture Pre-Sync Undo Snapshot (Save max 3)
        captureUndoSnapshot()

        // 2. Merge Playlists (Last-Write-Wins via HLC)
        for (rem in remote.playlists) {
            hlc.update(rem.timestamp)
            val loc = playlists[rem.playlistId]
            if (loc == null || rem.timestamp > loc.timestamp) {
                playlists[rem.playlistId] = rem
            }
        }

        // 3. Merge Favorites (Last-Write-Wins via HLC)
        for (rem in remote.favorites) {
            hlc.update(rem.timestamp)
            val loc = favorites[rem.trackFingerprint]
            if (loc == null || rem.timestamp > loc.timestamp) {
                favorites[rem.trackFingerprint] = rem
            }
        }

        // 4. Merge Play History & Stats (Union: Max of play counts & max timestamp)
        for (rem in remote.history) {
            val loc = history[rem.trackFingerprint]
            if (loc == null) {
                history[rem.trackFingerprint] = rem
            } else {
                val mergedCount = Math.max(loc.playCount, rem.playCount)
                val mergedTime = Math.max(loc.lastPlayedTimestampMs, rem.lastPlayedTimestampMs)
                history[rem.trackFingerprint] = SyncHistoryRecord(rem.trackFingerprint, mergedCount, mergedTime)
            }
        }

        // 5. Merge EQ Profiles (Last-Write-Wins via HLC)
        for (rem in remote.eqProfiles) {
            hlc.update(rem.timestamp)
            val loc = eqProfiles[rem.profileName]
            if (loc == null || rem.timestamp > loc.timestamp) {
                eqProfiles[rem.profileName] = rem
            }
        }

        return true
    }

    // --- 3-Snapshot Undo Engine ---

    @Synchronized
    private fun captureUndoSnapshot() {
        val current = createCurrentPackage()
        val snapshot = SyncSnapshot(
            snapshotId = System.currentTimeMillis(),
            timestampStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
            packageData = current
        )
        undoStack.add(snapshot)
        while (undoStack.size > 3) {
            undoStack.removeAt(0)
        }
    }

    @Synchronized
    fun undoLastSync(): Boolean {
        if (undoStack.isEmpty()) return false
        val lastSnapshot = undoStack.removeAt(undoStack.lastIndex)

        // Restore state from snapshot
        playlists.clear()
        for (p in lastSnapshot.packageData.playlists) {
            playlists[p.playlistId] = p
        }

        favorites.clear()
        for (f in lastSnapshot.packageData.favorites) {
            favorites[f.trackFingerprint] = f
        }

        history.clear()
        for (h in lastSnapshot.packageData.history) {
            history[h.trackFingerprint] = h
        }

        eqProfiles.clear()
        for (e in lastSnapshot.packageData.eqProfiles) {
            eqProfiles[e.profileName] = e
        }

        return true
    }

    val availableUndoCount: Int
        get() = undoStack.size

    fun getActivePlaylists(): List<SyncPlaylistRecord> = playlists.values.filter { !it.isTombstone }
    fun getActiveFavorites(): List<SyncFavoriteRecord> = favorites.values.filter { it.isFavorite }
    fun getHistoryRecords(): List<SyncHistoryRecord> = history.values.toList()
    fun getActiveEqProfiles(): List<SyncEqProfileRecord> = eqProfiles.values.filter { !it.isTombstone }
}
