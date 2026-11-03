package com.nodeplayer.windows

import com.nodeplayer.common.platform.AudioFormatConfig
import com.nodeplayer.windows.audio.RepeatMode
import com.nodeplayer.windows.audio.WasapiAudioOutput
import com.nodeplayer.windows.audio.WindowsPlaybackEngine
import com.nodeplayer.windows.data.DesktopSettingsStore
import com.nodeplayer.windows.data.WindowsFileScanner
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WindowsAudioAndStorageTest {

    @Test
    fun testWasapiAudioOutputDeviceEnumeration() {
        val output = WasapiAudioOutput()
        val devices = output.getAvailableDevices()
        assertNotNull("Devices list should not be null", devices)
        assertTrue("At least one audio device should be detected on Windows", devices.isNotEmpty())
        val defaultDevice = devices.find { it.isDefault }
        assertNotNull("Default audio device should exist", defaultDevice)
    }

    @Test
    fun testWasapiAudioOutputOpenAndClose() {
        val output = WasapiAudioOutput()
        val config = AudioFormatConfig(
            sampleRate = 44100,
            channelCount = 2,
            isFloat32 = true,
            isExclusiveMode = false
        )
        val opened = output.open(config)
        output.pause()
        output.resume()
        output.flush()
        output.close()
    }

    @Test
    fun testDesktopSettingsStore() {
        val store = DesktopSettingsStore()
        store.putString("test_key", "node_player_win")
        assertEquals("node_player_win", store.getString("test_key", ""))

        store.putInt("sample_rate", 96000)
        assertEquals(96000, store.getInt("sample_rate", 44100))

        store.putBoolean("exclusive_mode", true)
        assertTrue(store.getBoolean("exclusive_mode", false))

        store.remove("test_key")
        assertEquals("default_val", store.getString("test_key", "default_val"))
    }

    @Test
    fun testWindowsFileScannerMetadataExtraction() {
        val scanner = WindowsFileScanner()
        val nullMeta = scanner.extractMetadata(File("non_existent_file.flac"))
        assertNull(nullMeta)
    }

    @Test
    fun testPlaybackEngineControls() {
        val engine = WindowsPlaybackEngine()
        assertEquals(1.0f, engine.volume.value, 0.001f)
        engine.setVolume(0.75f)
        assertEquals(0.75f, engine.volume.value, 0.001f)

        assertEquals(RepeatMode.OFF, engine.repeatMode.value)
        engine.cycleRepeatMode()
        assertEquals(RepeatMode.ALL, engine.repeatMode.value)
        engine.cycleRepeatMode()
        assertEquals(RepeatMode.ONE, engine.repeatMode.value)
        engine.cycleRepeatMode()
        assertEquals(RepeatMode.OFF, engine.repeatMode.value)

        assertFalse(engine.isShuffle.value)
        engine.toggleShuffle()
        assertTrue(engine.isShuffle.value)

        engine.release()
    }
}
