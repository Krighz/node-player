package com.nodeplayer.ui.screens

import com.nodeplayer.model.Track
import com.nodeplayer.network.library.*
import kotlin.test.*

class LibraryViewTest {

    @Test
    fun testLibrarySubDestinationValues() {
        val destinations = LibrarySubDestination.values()
        assertEquals(5, destinations.size)
        assertTrue(destinations.contains(LibrarySubDestination.NONE))
        assertTrue(destinations.contains(LibrarySubDestination.PLAYLISTS))
        assertTrue(destinations.contains(LibrarySubDestination.ARTISTS))
        assertTrue(destinations.contains(LibrarySubDestination.ALBUMS))
        assertTrue(destinations.contains(LibrarySubDestination.SONGS))
    }

    @Test
    fun testOnlineLibraryStateIntegratesWithLibraryView() {
        val onlineSong = Track(
            id = 501L,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 230000L,
            path = "https://music.youtube.com/watch?v=34Na4j8AVgA"
        )
        val onlineAlbum = OnlineAlbum(
            id = "starboy",
            title = "Starboy",
            artist = "The Weeknd",
            trackCount = 18
        )
        val onlinePlaylist = OnlinePlaylist(
            id = "pl_synth",
            title = "Synthwave",
            author = "Curator",
            trackCount = 25
        )
        val onlineArtist = OnlineArtist(
            id = "the_weeknd",
            name = "The Weeknd",
            trackCount = 1
        )
        val recentItem = OnlineMediaItem(
            id = "starboy",
            title = "Starboy",
            subtitle = "The Weeknd",
            type = OnlineMediaType.ALBUM
        )

        val state = OnlineLibraryState(
            songs = listOf(onlineSong),
            albums = listOf(onlineAlbum),
            artists = listOf(onlineArtist),
            playlists = listOf(onlinePlaylist),
            recentlyAdded = listOf(recentItem)
        )

        assertEquals(1, state.songs.size)
        assertEquals(1, state.albums.size)
        assertEquals(1, state.artists.size)
        assertEquals(1, state.playlists.size)
        assertEquals(1, state.recentlyAdded.size)
        assertEquals("Starboy", state.recentlyAdded.first().title)
        assertEquals(OnlineMediaType.ALBUM, state.recentlyAdded.first().type)
    }

    @Test
    fun testEmptyOnlineLibraryStateHasZeroCounts() {
        val emptyState = OnlineLibraryState()
        assertTrue(emptyState.songs.isEmpty())
        assertTrue(emptyState.albums.isEmpty())
        assertTrue(emptyState.artists.isEmpty())
        assertTrue(emptyState.playlists.isEmpty())
        assertTrue(emptyState.recentlyAdded.isEmpty())
    }
}
