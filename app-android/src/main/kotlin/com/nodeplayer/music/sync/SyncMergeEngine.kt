package com.nodeplayer.music.sync

import java.time.Instant

/**
 * Deterministic conflict resolution merge engine for local & remote sync snapshots.
 *
 * Conflict Resolution Rules:
 * 1. Playlists: Keep the version with the newer [SyncPlaylist.updatedAt] timestamp.
 * 2. Saved Items: Keep the version with the newer [SyncSavedItem.updatedAt] timestamp.
 * 3. Song Stats: Per songId, keep higher totalPlayTimeSeconds, higher playCount, and latest lastPlayedAt.
 * 4. Playback History: Deduplicate by (songId, playedAt), sort descending, and trim to 200 items.
 */
object SyncMergeEngine {

    fun merge(local: SyncSnapshot, remote: SyncSnapshot?): SyncSnapshot {
        if (remote == null) {
            return local.copy(
                lastSyncedAt = Instant.now().toString(),
                playbackHistory = local.playbackHistory
                    .distinctBy { "${it.songId}_${it.playedAt}" }
                    .sortedByDescending { it.playedAt }
                    .take(SyncSnapshot.MAX_HISTORY_ENTRIES)
            )
        }

        val mergedPlaylists = mergePlaylists(local.playlists, remote.playlists)
        val mergedSavedItems = mergeSavedItems(local.savedItems, remote.savedItems)
        val mergedSongStats = mergeSongStats(local.songStats, remote.songStats)
        val mergedHistory = mergePlaybackHistory(local.playbackHistory, remote.playbackHistory)

        return SyncSnapshot(
            schemaVersion = maxOf(local.schemaVersion, remote.schemaVersion),
            lastSyncedAt = Instant.now().toString(),
            playlists = mergedPlaylists,
            savedItems = mergedSavedItems,
            songStats = mergedSongStats,
            playbackHistory = mergedHistory
        )
    }

    internal fun mergePlaylists(local: List<SyncPlaylist>, remote: List<SyncPlaylist>): List<SyncPlaylist> {
        val remoteMap = remote.associateBy { it.id }
        val mergedMap = LinkedHashMap<String, SyncPlaylist>()

        // 1. Process local items against remote
        for (localPl in local) {
            val remotePl = remoteMap[localPl.id]
            if (remotePl == null) {
                mergedMap[localPl.id] = localPl
            } else {
                // Keep the version with newer updatedAt
                if (localPl.updatedAt >= remotePl.updatedAt) {
                    mergedMap[localPl.id] = localPl
                } else {
                    mergedMap[localPl.id] = remotePl
                }
            }
        }

        // 2. Add remote items not present in local
        for (remotePl in remote) {
            if (!mergedMap.containsKey(remotePl.id)) {
                mergedMap[remotePl.id] = remotePl
            }
        }

        return mergedMap.values.toList()
    }

    internal fun mergeSavedItems(local: List<SyncSavedItem>, remote: List<SyncSavedItem>): List<SyncSavedItem> {
        val remoteMap = remote.associateBy { it.trackId }
        val mergedMap = LinkedHashMap<String, SyncSavedItem>()

        for (localItem in local) {
            val remoteItem = remoteMap[localItem.trackId]
            if (remoteItem == null) {
                mergedMap[localItem.trackId] = localItem
            } else {
                // Keep the version with newer updatedAt
                if (localItem.updatedAt >= remoteItem.updatedAt) {
                    mergedMap[localItem.trackId] = localItem
                } else {
                    mergedMap[localItem.trackId] = remoteItem
                }
            }
        }

        for (remoteItem in remote) {
            if (!mergedMap.containsKey(remoteItem.trackId)) {
                mergedMap[remoteItem.trackId] = remoteItem
            }
        }

        return mergedMap.values.toList()
    }

    internal fun mergeSongStats(
        local: Map<String, SyncSongStat>,
        remote: Map<String, SyncSongStat>
    ): Map<String, SyncSongStat> {
        val allKeys = (local.keys + remote.keys).toSet()
        val result = LinkedHashMap<String, SyncSongStat>(allKeys.size)

        for (key in allKeys) {
            val l = local[key]
            val r = remote[key]

            if (l != null && r != null) {
                result[key] = SyncSongStat(
                    songId = key,
                    title = if (l.title.isNotBlank()) l.title else r.title,
                    artist = if (l.artist.isNotBlank()) l.artist else r.artist,
                    totalPlayTimeSeconds = maxOf(l.totalPlayTimeSeconds, r.totalPlayTimeSeconds),
                    playCount = maxOf(l.playCount, r.playCount),
                    lastPlayedAt = maxOf(l.lastPlayedAt, r.lastPlayedAt)
                )
            } else if (l != null) {
                result[key] = l
            } else if (r != null) {
                result[key] = r
            }
        }

        return result
    }

    internal fun mergePlaybackHistory(
        local: List<SyncPlaybackHistoryEntry>,
        remote: List<SyncPlaybackHistoryEntry>
    ): List<SyncPlaybackHistoryEntry> {
        return (local + remote)
            .distinctBy { "${it.songId}_${it.playedAt}" }
            .sortedByDescending { it.playedAt }
            .take(SyncSnapshot.MAX_HISTORY_ENTRIES)
    }
}
