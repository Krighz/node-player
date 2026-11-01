package com.nodeplayer.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nodeplayer.music.data.ScannedFolderEntity
import com.nodeplayer.music.data.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderToggleIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context as NodePlayerApplication

    @Test
    fun testDisableFolderHidesTracksWithoutDeletingAndEnableRestoresThem() = runBlocking {
        val dao = app.database.playerDao()

        val rockFolder = "/sdcard/Music/Rock"
        val popFolder = "/sdcard/Music/Pop"

        val trackRock = TrackEntity(
            id = 900101L,
            title = "Rock Anthem",
            artist = "Rock Band",
            artistId = 101L,
            album = "Rock Album",
            albumId = 201L,
            durationMs = 180000L,
            path = "$rockFolder/track1.mp3",
            size = 5000000L,
            mimeType = "audio/mpeg",
            sampleRate = 44100,
            bitDepth = 0,
            channels = 2,
            bitrateKbps = 320,
            formatDescription = "MP3 320 kbps",
            trackNumber = 1,
            discNumber = 1,
            year = 2024,
            genre = "Rock",
            dateAdded = System.currentTimeMillis() / 1000,
            isHidden = false
        )

        val trackPop = TrackEntity(
            id = 900102L,
            title = "Pop Hit",
            artist = "Pop Star",
            artistId = 102L,
            album = "Pop Album",
            albumId = 202L,
            durationMs = 210000L,
            path = "$popFolder/track2.mp3",
            size = 6000000L,
            mimeType = "audio/mpeg",
            sampleRate = 44100,
            bitDepth = 0,
            channels = 2,
            bitrateKbps = 320,
            formatDescription = "MP3 320 kbps",
            trackNumber = 1,
            discNumber = 1,
            year = 2024,
            genre = "Pop",
            dateAdded = System.currentTimeMillis() / 1000,
            isHidden = false
        )

        // 1. Initial Insert
        dao.insertTracks(listOf(trackRock, trackPop))
        dao.insertScannedFolders(
            listOf(
                ScannedFolderEntity(path = rockFolder, name = "Rock", trackCount = 1, isEnabled = true),
                ScannedFolderEntity(path = popFolder, name = "Pop", trackCount = 1, isEnabled = true)
            )
        )

        // Verify initial visible tracks
        val initialVisible = dao.getAllTracksList().filter { it.id in listOf(900101L, 900102L) }
        assertEquals("Both tracks must be visible initially", 2, initialVisible.size)

        // 2. DISABLE Rock Folder
        dao.setTracksHiddenForFolder(rockFolder, isHidden = true)
        dao.setFolderEnabled(rockFolder, isEnabled = false)

        // Verify visible tracks after disable: Rock track must be HIDDEN
        val afterDisableVisible = dao.getAllTracksList().filter { it.id in listOf(900101L, 900102L) }
        assertEquals("Only Pop track should be visible after Rock folder is disabled", 1, afterDisableVisible.size)
        assertEquals("Visible track must be Pop", 900102L, afterDisableVisible[0].id)

        // Verify raw tracks in database: Tracks must NOT be deleted from Room!
        val rawTracks = dao.getAllRawTracksList().filter { it.id in listOf(900101L, 900102L) }
        assertEquals("Disabled tracks must not be deleted from the database", 2, rawTracks.size)

        // Verify folder index still lists both folders (including disabled ones)
        val allFolders = dao.getAllScannedFoldersList().filter { it.path in listOf(rockFolder, popFolder) }
        assertEquals("Folder index must continue listing all folders including disabled ones", 2, allFolders.size)
        val disabledFolder = allFolders.first { it.path == rockFolder }
        assertFalse("Disabled folder must have isEnabled = false in folder index", disabledFolder.isEnabled)

        // 3. RE-ENABLE Rock Folder
        dao.setTracksHiddenForFolder(rockFolder, isHidden = false)
        dao.setFolderEnabled(rockFolder, isEnabled = true)

        // Verify visible tracks after enable: Rock track must RETURN right away!
        val afterEnableVisible = dao.getAllTracksList().filter { it.id in listOf(900101L, 900102L) }
        assertEquals("Both tracks must return immediately when folder is re-enabled", 2, afterEnableVisible.size)

        // Cleanup
        dao.setTracksHiddenForFolder(rockFolder, isHidden = true)
        dao.setTracksHiddenForFolder(popFolder, isHidden = true)
    }
}
