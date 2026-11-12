package com.nodeplayer.network.library

import com.nodeplayer.model.Track
import java.io.File
import kotlin.test.*

class OnlineLibraryManagerTest {

    private lateinit var tempDir: File
    private lateinit var manager: OnlineLibraryManager

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("online_lib_test", "").apply {
            delete()
            mkdirs()
        }
        manager = OnlineLibraryManager(storageDir = tempDir)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testLikeOnlineTrackUpdatesStateAndCounts() {
        val onlineTrack1 = Track(
            id = 101L,
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243000L,
            path = "https://music.youtube.com/watch?v=dX3k_PDnzHE"
        )
        val onlineTrack2 = Track(
            id = 102L,
            title = "Wait",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationMs = 343000L,
            path = "https://music.youtube.com/watch?v=lzkKzvm8"
        )

        manager.likeTrack(onlineTrack1)
        manager.likeTrack(onlineTrack2)

        val state = manager.state.value
        assertEquals(2, state.songs.size)
        assertEquals(1, state.albums.size)
        assertEquals("Hurry Up, We're Dreaming", state.albums[0].title)
        assertEquals(2, state.albums[0].trackCount)
        assertEquals(1, state.artists.size)
        assertEquals("M83", state.artists[0].name)
        assertTrue(manager.isLiked(101L))
        assertTrue(manager.isLiked(102L))
    }

    @Test
    fun testStrictIsolationExcludesLocalFiles() {
        val localTrackAndroid = Track(
            id = 201L,
            title = "Offline Local Song",
            artist = "Local Artist",
            album = "Device Storage",
            durationMs = 180000L,
            path = "/storage/emulated/0/Music/song.mp3"
        )
        val localTrackWindows = Track(
            id = 202L,
            title = "Desktop Local Song",
            artist = "Local Artist",
            album = "Device Storage",
            durationMs = 200000L,
            path = "C:\\Users\\User\\Music\\song.flac"
        )

        assertFalse(manager.isOnlineTrack(localTrackAndroid))
        assertFalse(manager.isOnlineTrack(localTrackWindows))

        manager.likeTrack(localTrackAndroid)
        manager.likeTrack(localTrackWindows)

        // Strict isolation (D-03, LBRY-02, LBRY-03): zero local tracks in online library
        assertEquals(0, manager.state.value.songs.size)
        assertFalse(manager.isLiked(201L))
        assertFalse(manager.isLiked(202L))
    }

    @Test
    fun testRecentlyAddedCombinesPlaylistsAndAlbumsSortedByRecency() {
        val track = Track(
            id = 301L,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 230000L,
            path = "nodeplayer://stream/video_123"
        )
        manager.saveTrack(track)

        val playlist = OnlinePlaylist(
            id = "pl_synthwave",
            title = "Synthwave Classics",
            author = "Curator",
            trackCount = 45,
            addedTimestamp = System.currentTimeMillis() + 1000L
        )
        manager.addPlaylist(playlist)

        val state = manager.state.value
        assertTrue(state.recentlyAdded.isNotEmpty())
        // Playlist has later timestamp, so it must be first
        assertEquals("Synthwave Classics", state.recentlyAdded.first().title)
        assertEquals(OnlineMediaType.PLAYLIST, state.recentlyAdded.first().type)
    }

    @Test
    fun testPersistenceSavesAndRestoresLibrary() {
        val track = Track(
            id = 401L,
            title = "Levitating",
            artist = "Dua Lipa",
            album = "Future Nostalgia",
            durationMs = 203000L,
            path = "https://music.youtube.com/watch?v=TUVcZfQe-Kw"
        )
        manager.likeTrack(track)

        val playlist = OnlinePlaylist(
            id = "pl_pop",
            title = "Today's Hits",
            author = "YouTube Music",
            trackCount = 50
        )
        manager.addPlaylist(playlist)

        // Create a new instance pointing to the same storageDir to test disk reload
        val restoredManager = OnlineLibraryManager(storageDir = tempDir)
        val restoredState = restoredManager.state.value

        assertEquals(1, restoredState.songs.size)
        assertEquals("Levitating", restoredState.songs[0].title)
        assertEquals(1, restoredState.playlists.size)
        assertEquals("Today's Hits", restoredState.playlists[0].title)
        assertTrue(restoredManager.isLiked(401L))
    }
}
