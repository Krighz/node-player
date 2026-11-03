package com.nodeplayer.desktop

import com.nodeplayer.common.platform.AudioFormatConfig
import com.nodeplayer.desktop.audio.FlacAudioDecoder
import com.nodeplayer.desktop.audio.JvmAudioDecoder
import com.nodeplayer.desktop.audio.WasapiAudioOutput
import com.nodeplayer.desktop.data.DesktopSettingsStore
import com.nodeplayer.desktop.data.DesktopTrackDatabase
import com.nodeplayer.desktop.data.WindowsFileScanner
import com.nodeplayer.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    fun testThemeAndEffectConfigPersistence() {
        val store = DesktopSettingsStore()
        val customTheme = GlassThemeConfig(
            fixedAccentColorArgb = 0xFF10B981L,
            backgroundAnimationStyle = "SLOW_CAUSTICS",
            backgroundAnimationSpeed = 1.8f,
            backgroundAnimationIntensity = 0.9f,
            glassOpacity = 0.42f,
            blurStrength = 0.88f,
            edgeHighlightStrength = 0.77f,
            lensRefractionStrength = 1.15f,
            isOledBlack = true,
            isPerformanceMode = false
        )
        store.saveThemeConfig(customTheme)
        val loadedTheme = store.loadThemeConfig()
        assertEquals(0xFF10B981L, loadedTheme.fixedAccentColorArgb)
        assertEquals("SLOW_CAUSTICS", loadedTheme.backgroundAnimationStyle)
        assertEquals(1.8f, loadedTheme.backgroundAnimationSpeed, 0.01f)
        assertEquals(0.42f, loadedTheme.glassOpacity, 0.01f)
        assertTrue(loadedTheme.isOledBlack)

        val customEffect = AudioEffectConfig(
            eqEnabled = true,
            presetName = "Bass Boost",
            preampGainDb = 2.5f,
            stereoWidth = 1.35f,
            toneControls = ToneControlConfig(bassGainDb = 4.0f, trebleGainDb = -1.5f),
            loudnessConfig = LoudnessConfig(enabled = true, strength = 0.75f)
        )
        store.saveEffectConfig(customEffect)
        val loadedEffect = store.loadEffectConfig()
        assertTrue(loadedEffect.eqEnabled)
        assertEquals("Bass Boost", loadedEffect.presetName)
        assertEquals(2.5f, loadedEffect.preampGainDb, 0.01f)
        assertEquals(1.35f, loadedEffect.stereoWidth, 0.01f)
        assertEquals(4.0f, loadedEffect.toneControls.bassGainDb, 0.01f)
        assertTrue(loadedEffect.loudnessConfig.enabled)
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
        if (opened) {
            assertTrue(output.isOpened)
            assertEquals(48000, output.currentSampleRate)
            assertEquals(2, output.currentChannels)
            output.close()
            assertFalse(output.isOpened)
        }
    }

    @Test
    fun testJvmWavAudioDecoder() {
        val tempWav = File.createTempFile("test_audio", ".wav")
        try {
            val sampleRate = 44100
            val channels = 2
            val numFrames = 4410 // 100ms
            val bytesPerSample = 2
            val dataSize = numFrames * channels * bytesPerSample

            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)

            // RIFF header
            dos.writeBytes("RIFF")
            dos.writeInt(Integer.reverseBytes(36 + dataSize))
            dos.writeBytes("WAVE")
            dos.writeBytes("fmt ")
            dos.writeInt(Integer.reverseBytes(16)) // subchunk1 size
            dos.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // PCM format
            dos.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
            dos.writeInt(Integer.reverseBytes(sampleRate))
            dos.writeInt(Integer.reverseBytes(sampleRate * channels * bytesPerSample))
            dos.writeShort(java.lang.Short.reverseBytes((channels * bytesPerSample).toShort()).toInt())
            dos.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt()) // 16-bit
            dos.writeBytes("data")
            dos.writeInt(Integer.reverseBytes(dataSize))

            for (i in 0 until numFrames * channels) {
                dos.writeShort(java.lang.Short.reverseBytes(8000.toShort()).toInt())
            }
            dos.flush()
            tempWav.writeBytes(baos.toByteArray())

            val decoder = JvmAudioDecoder()
            val opened = decoder.open(tempWav.absolutePath)
            assertTrue(opened)
            assertNotNull(decoder.metadata)
            assertEquals(44100, decoder.metadata?.sampleRate)
            assertEquals(2, decoder.metadata?.channels)

            val pcmOut = FloatArray(1024)
            val decodedCount = decoder.decodeFrame(pcmOut)
            assertTrue(decodedCount > 0)
            assertEquals(8000f / 32768.0f, pcmOut[0], 0.01f)
            decoder.close()
        } finally {
            tempWav.delete()
        }
    }

    @Test
    fun testFlacAudioDecoderLifecycle() {
        val decoder = FlacAudioDecoder()
        // Missing/invalid file should return false gracefully without crash
        assertFalse(decoder.open("non_existent_file.flac"))
        assertNull(decoder.metadata)
        assertEquals(-1, decoder.decodeFrame(FloatArray(256)))
        decoder.close()
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
