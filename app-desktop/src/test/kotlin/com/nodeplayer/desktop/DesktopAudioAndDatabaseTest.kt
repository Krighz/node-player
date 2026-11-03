package com.nodeplayer.desktop

import com.nodeplayer.common.platform.AudioFormatConfig
import com.nodeplayer.desktop.audio.WasapiAudioOutput
import com.nodeplayer.desktop.data.DesktopSettingsStore
import com.nodeplayer.desktop.data.DesktopTrackDatabase
import com.nodeplayer.desktop.data.WindowsFileScanner
import com.nodeplayer.model.Track
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DesktopAudioAndDatabaseTest {

    @Test
    fun testDesktopSettingsStore() {
        val store = DesktopSettingsStore()
        store.putString("test_key", "test_val")
        assertEquals("test_val", store.getString("test_key", ""))

        store.putInt("test_int", 42)
        assertEquals(42, store.getInt("test_int", 0))

        store.putBoolean("test_bool", true)
        assertTrue(store.getBoolean("test_bool", false))

        store.putFloat("test_float", 1.5f)
        assertEquals(1.5f, store.getFloat("test_float", 0f), 0.001f)

        store.remove("test_key")
        assertFalse(store.contains("test_key"))
    }

    @Test
    fun testDesktopTrackDatabase() {
        val db = DesktopTrackDatabase()
        val dummyTrack = Track(
            id = 9999L,
            title = "Test Audiophile Track",
            artist = "FLAC Artist",
            album = "Lossless Album",
            durationMs = 240000L,
            path = "C:\\Music\\test_audiophile.flac",
            sampleRate = 96000,
            bitDepth = 24,
            isLossless = true,
            isHiRes = true
        )

        db.insertOrUpdateTracks(listOf(dummyTrack))
        val allTracks = db.getAllTracks()
        assertTrue(allTracks.any { it.path == dummyTrack.path })

        val albums = db.extractAlbums(allTracks)
        assertTrue(albums.any { it.title == "Lossless Album" })

        val artists = db.extractArtists(allTracks)
        assertTrue(artists.any { it.name == "FLAC Artist" })
    }

    @Test
    fun testWasapiAudioOutputLifecycle() {
        val output = WasapiAudioOutput()
        val config = AudioFormatConfig(
            sampleRate = 48000,
            channelCount = 2,
            isFloat32 = true,
            isExclusiveMode = false
        )

        val opened = output.open(config)
        // May open or fall back depending on headless runner
        if (opened) {
            assertTrue(output.isOpened)
            assertEquals(48000, output.currentSampleRate)
            assertEquals(2, output.currentChannels)
            output.close()
            assertFalse(output.isOpened)
        }
    }

    @Test
    fun testWindowsFileScanner() {
        val scanner = WindowsFileScanner()
        val tempFile = File.createTempFile("test_song", ".mp3")
        tempFile.writeBytes(ByteArray(1024))
        try {
            val meta = scanner.extractMetadata(tempFile)
            assertNotNull(meta)
            val track = scanner.toTrack(meta!!, id = 1L)
            assertEquals(1L, track.id)
            assertEquals("audio/mpeg", track.mimeType)
        } finally {
            tempFile.delete()
        }
    }
}
