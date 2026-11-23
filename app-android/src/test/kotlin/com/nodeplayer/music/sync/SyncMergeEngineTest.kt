package com.nodeplayer.music.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeEngineTest {

    @Test
    fun testMergePlaylists_newerUpdatedAtWins() {
        val local = listOf(
            SyncPlaylist(
                id = "pl-1",
                name = "Rock Local",
                trackIds = listOf("1", "2"),
                updatedAt = 2000L
            ),
            SyncPlaylist(
                id = "pl-2",
                name = "Jazz Local",
                trackIds = listOf("3"),
                updatedAt = 1000L
            )
        )

        val remote = listOf(
            SyncPlaylist(
                id = "pl-1",
                name = "Rock Remote Older",
                trackIds = listOf("1"),
                updatedAt = 1500L
            ),
            SyncPlaylist(
                id = "pl-2",
                name = "Jazz Remote Newer",
                trackIds = listOf("3", "4"),
                updatedAt = 3000L
            ),
            SyncPlaylist(
                id = "pl-3",
                name = "Ambient Remote Only",
                trackIds = listOf("5"),
                updatedAt = 2500L
            )
        )

        val merged = SyncMergeEngine.mergePlaylists(local, remote)
        assertEquals(3, merged.size)

        val pl1 = merged.find { it.id == "pl-1" }!!
        assertEquals("Rock Local", pl1.name)
        assertEquals(listOf("1", "2"), pl1.trackIds)
        assertEquals(2000L, pl1.updatedAt)

        val pl2 = merged.find { it.id == "pl-2" }!!
        assertEquals("Jazz Remote Newer", pl2.name)
        assertEquals(listOf("3", "4"), pl2.trackIds)
        assertEquals(3000L, pl2.updatedAt)

        val pl3 = merged.find { it.id == "pl-3" }!!
        assertEquals("Ambient Remote Only", pl3.name)
    }

    @Test
    fun testMergeSavedItems_newerUpdatedAtWins() {
        val local = listOf(
            SyncSavedItem(trackId = "track-1", timestamp = 1000L, updatedAt = 5000L),
            SyncSavedItem(trackId = "track-2", timestamp = 1000L, updatedAt = 2000L)
        )

        val remote = listOf(
            SyncSavedItem(trackId = "track-1", timestamp = 1000L, updatedAt = 4000L),
            SyncSavedItem(trackId = "track-2", timestamp = 1000L, updatedAt = 7000L),
            SyncSavedItem(trackId = "track-3", timestamp = 1000L, updatedAt = 3000L)
        )

        val merged = SyncMergeEngine.mergeSavedItems(local, remote)
        assertEquals(3, merged.size)

        val item1 = merged.find { it.trackId == "track-1" }!!
        assertEquals(5000L, item1.updatedAt)

        val item2 = merged.find { it.trackId == "track-2" }!!
        assertEquals(7000L, item2.updatedAt)

        val item3 = merged.find { it.trackId == "track-3" }!!
        assertEquals(3000L, item3.updatedAt)
    }

    @Test
    fun testMergeSongStats_keepsHigherTimeAndPlaysAndLatestLastPlayed() {
        val local = mapOf(
            "song-1" to SyncSongStat(
                songId = "song-1",
                title = "Song A",
                artist = "Artist A",
                totalPlayTimeSeconds = 300L,
                playCount = 5,
                lastPlayedAt = 10_000L
            ),
            "song-2" to SyncSongStat(
                songId = "song-2",
                title = "Song B",
                artist = "Artist B",
                totalPlayTimeSeconds = 600L,
                playCount = 10,
                lastPlayedAt = 20_000L
            )
        )

        val remote = mapOf(
            "song-1" to SyncSongStat(
                songId = "song-1",
                title = "Song A",
                artist = "Artist A",
                totalPlayTimeSeconds = 500L,
                playCount = 4,
                lastPlayedAt = 12_000L
            ),
            "song-3" to SyncSongStat(
                songId = "song-3",
                title = "Song C",
                artist = "Artist C",
                totalPlayTimeSeconds = 120L,
                playCount = 2,
                lastPlayedAt = 5_000L
            )
        )

        val merged = SyncMergeEngine.mergeSongStats(local, remote)
        assertEquals(3, merged.size)

        val song1 = merged["song-1"]!!
        assertEquals(500L, song1.totalPlayTimeSeconds) // maxOf(300, 500)
        assertEquals(5, song1.playCount) // maxOf(5, 4)
        assertEquals(12_000L, song1.lastPlayedAt) // maxOf(10000, 12000)

        val song2 = merged["song-2"]!!
        assertEquals(600L, song2.totalPlayTimeSeconds)
        assertEquals(10, song2.playCount)

        val song3 = merged["song-3"]!!
        assertEquals(120L, song3.totalPlayTimeSeconds)
        assertEquals(2, song3.playCount)
    }

    @Test
    fun testMergePlaybackHistory_deduplicatesAndBoundsTo200() {
        val local = (1..150).map { i ->
            SyncPlaybackHistoryEntry(
                songId = "song-$i",
                playedAt = i * 1000L,
                durationPlayedSeconds = 180L
            )
        }

        val remote = (100..250).map { i ->
            SyncPlaybackHistoryEntry(
                songId = "song-$i",
                playedAt = i * 1000L,
                durationPlayedSeconds = 180L
            )
        }

        val merged = SyncMergeEngine.mergePlaybackHistory(local, remote)
        assertEquals(200, merged.size)

        // Ensure descending order
        assertTrue(merged.first().playedAt > merged.last().playedAt)
        assertEquals(250_000L, merged.first().playedAt)
        assertEquals(51_000L, merged.last().playedAt)
    }

    @Test
    fun testSerializationAndDeserializationRoundtrip() {
        val snapshot = SyncSnapshot(
            schemaVersion = 1,
            lastSyncedAt = "2026-11-23T07:30:00Z",
            playlists = listOf(
                SyncPlaylist(
                    id = "pl-1",
                    name = "Audiophile DSD",
                    trackIds = listOf("101", "102"),
                    createdAt = 1000L,
                    updatedAt = 2000L
                )
            ),
            savedItems = listOf(
                SyncSavedItem(trackId = "101", timestamp = 1500L, updatedAt = 1500L)
            ),
            songStats = mapOf(
                "101" to SyncSongStat(
                    songId = "101",
                    title = "Take Five",
                    artist = "Dave Brubeck",
                    totalPlayTimeSeconds = 324L,
                    playCount = 7,
                    lastPlayedAt = 5000L
                )
            ),
            playbackHistory = listOf(
                SyncPlaybackHistoryEntry(songId = "101", playedAt = 5000L, durationPlayedSeconds = 324L)
            )
        )

        val json = SyncJsonSerializer.serialize(snapshot)
        val deserialized = SyncJsonSerializer.deserialize(json)

        assertEquals(snapshot.schemaVersion, deserialized.schemaVersion)
        assertEquals(snapshot.lastSyncedAt, deserialized.lastSyncedAt)
        assertEquals(1, deserialized.playlists.size)
        assertEquals("Audiophile DSD", deserialized.playlists[0].name)
        assertEquals(listOf("101", "102"), deserialized.playlists[0].trackIds)
        assertEquals(1, deserialized.savedItems.size)
        assertEquals("101", deserialized.savedItems[0].trackId)
        assertEquals(1, deserialized.songStats.size)
        assertEquals(324L, deserialized.songStats["101"]?.totalPlayTimeSeconds)
        assertEquals(1, deserialized.playbackHistory.size)
        assertEquals("101", deserialized.playbackHistory[0].songId)
    }
}
