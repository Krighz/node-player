package com.nodeplayer.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsRegistryTest {

    @Test
    fun testSettingsSectionsOrderAndIds() {
        val sections = SettingSection.entries.sortedBy { it.order }
        assertEquals("appearance", sections[0].id)
        assertEquals("playback", sections[1].id)
        assertEquals("library_files", sections[2].id)
        assertEquals("online_connect_network", sections[3].id)
        assertEquals("storage", sections[4].id)
    }

    @Test
    fun testOnlineConnectAndNetworkRegistryEntries() {
        val onlineEntries = SettingsRegistry.getBySection(SettingSection.ONLINE_CONNECT_NETWORK)
        assertTrue(onlineEntries.isNotEmpty(), "Online section should contain registered settings")
        
        val onlineMode = SettingsRegistry.getByKey("is_online_mode")
        assertNotNull(onlineMode)
        assertEquals(SettingSubsection.YOUTUBE_ACCOUNT, onlineMode.subsection)
        assertEquals(true, (onlineMode.type as SettingType.Toggle).default)

        val nodeConnect = SettingsRegistry.getByKey("node_connect_enabled")
        assertNotNull(nodeConnect)
        assertEquals(SettingSubsection.NODE_CONNECT_DEVICES, nodeConnect.subsection)
        assertEquals(true, (nodeConnect.type as SettingType.Toggle).default)
    }

    @Test
    fun testPlaylistImportRegistryEntries() {
        val importEntries = SettingsRegistry.getBySubsection(SettingSubsection.PLAYLIST_IMPORT)
        assertEquals(8, importEntries.size, "Playlist import subsection should contain 8 registered settings")

        val orderSetting = SettingsRegistry.getByKey("import_artist_title_order")
        assertNotNull(orderSetting)
        assertEquals(SettingSection.LIBRARY_FILES, orderSetting.section)
        assertEquals("artist_title", (orderSetting.type as SettingType.Choice).default)

        val autoAccept = SettingsRegistry.getByKey("import_auto_accept")
        assertNotNull(autoAccept)
        assertEquals(90, (autoAccept.type as SettingType.IntRange).default)

        val durationWindow = SettingsRegistry.getByKey("import_duration_window_ms")
        assertNotNull(durationWindow)
        assertEquals(3000, (durationWindow.type as SettingType.IntRange).default)
    }

    @Test
    fun testStorageAndDownloadRegistryEntries() {
        val storageEntries = SettingsRegistry.getBySection(SettingSection.STORAGE)
        assertEquals(7, storageEntries.size, "Storage section should have 7 registered settings")

        val concurrency = SettingsRegistry.getByKey("download_concurrent_limit")
        assertNotNull(concurrency)
        assertEquals(2, (concurrency.type as SettingType.IntRange).default)

        val wifiOnly = SettingsRegistry.getByKey("download_wifi_only")
        assertNotNull(wifiOnly)
        assertEquals(true, (wifiOnly.type as SettingType.Toggle).default)

        val quality = SettingsRegistry.getByKey("download_default_quality")
        assertNotNull(quality)
        assertEquals("ASK", (quality.type as SettingType.Choice).default)

        val imgLimit = SettingsRegistry.getByKey("cache_limit_images_mb")
        assertNotNull(imgLimit)
        assertEquals(200, (imgLimit.type as SettingType.IntRange).default)
    }

    @Test
    fun testDiscordRichPresenceRegistryEntries() {
        val discordEntries = SettingsRegistry.getBySubsection(SettingSubsection.DISCORD_PRESENCE)
        assertEquals(6, discordEntries.size, "Discord subsection should have 6 registered settings")

        for (entry in discordEntries) {
            assertEquals(setOf(SettingPlatform.DESKTOP), entry.platforms, "${entry.key} must be Desktop only")
            assertEquals(SettingSection.ONLINE_CONNECT_NETWORK, entry.section)
        }

        val enabled = SettingsRegistry.getByKey("discord_enabled")
        assertNotNull(enabled)
        assertEquals(false, (enabled.type as SettingType.Toggle).default)

        val incognito = SettingsRegistry.getByKey("discord_incognito")
        assertNotNull(incognito)
        assertEquals(false, (incognito.type as SettingType.Toggle).default)

        val showCover = SettingsRegistry.getByKey("discord_show_cover")
        assertNotNull(showCover)
        assertEquals(true, (showCover.type as SettingType.Toggle).default)

        val showAlbum = SettingsRegistry.getByKey("discord_show_album")
        assertNotNull(showAlbum)
        assertEquals(true, (showAlbum.type as SettingType.Toggle).default)

        val showPaused = SettingsRegistry.getByKey("discord_show_while_paused")
        assertNotNull(showPaused)
        assertEquals(false, (showPaused.type as SettingType.Toggle).default)

        val appId = SettingsRegistry.getByKey("discord_app_id")
        assertNotNull(appId)
        assertEquals("", (appId.type as SettingType.Text).default)
    }

    @Test
    fun testAudioConverterRegistryEntries() {
        val converterEntries = SettingsRegistry.getBySubsection(SettingSubsection.AUDIO_CONVERTER)
        assertEquals(13, converterEntries.size, "Audio converter subsection must contain 13 registered settings")

        for (entry in converterEntries) {
            assertEquals(SettingSection.LIBRARY_FILES, entry.section)
            assertEquals(SettingSubsection.AUDIO_CONVERTER, entry.subsection)
        }

        val preset = SettingsRegistry.getByKey("convert_default_preset")
        assertNotNull(preset)
        assertEquals("128", (preset.type as SettingType.Choice).default)
        assertEquals(listOf("192", "128", "96", "CUSTOM"), (preset.type as SettingType.Choice).options)

        val bitrate = SettingsRegistry.getByKey("convert_custom_bitrate")
        assertNotNull(bitrate)
        assertEquals(160, (bitrate.type as SettingType.IntRange).default)
        assertEquals(32, (bitrate.type as SettingType.IntRange).min)
        assertEquals(320, (bitrate.type as SettingType.IntRange).max)

        val vbr = SettingsRegistry.getByKey("convert_vbr")
        assertNotNull(vbr)
        assertEquals(true, (vbr.type as SettingType.Toggle).default)

        val app = SettingsRegistry.getByKey("convert_application")
        assertNotNull(app)
        assertEquals("audio", (app.type as SettingType.Choice).default)

        val compression = SettingsRegistry.getByKey("convert_compression_level")
        assertNotNull(compression)
        assertEquals(10, (compression.type as SettingType.IntRange).default)

        val keepOriginal = SettingsRegistry.getByKey("convert_keep_original")
        assertNotNull(keepOriginal)
        assertEquals(true, (keepOriginal.type as SettingType.Toggle).default)

        val outputMode = SettingsRegistry.getByKey("convert_output_mode")
        assertNotNull(outputMode)
        assertEquals("CUSTOM_FOLDER", (outputMode.type as SettingType.Choice).default)

        val outputFolder = SettingsRegistry.getByKey("convert_output_folder")
        assertNotNull(outputFolder)
        assertEquals("", (outputFolder.type as SettingType.Text).default)

        val template = SettingsRegistry.getByKey("convert_name_template")
        assertNotNull(template)
        assertEquals("{title}", (template.type as SettingType.Text).default)

        val workers = SettingsRegistry.getByKey("convert_workers")
        assertNotNull(workers)
        assertEquals(2, (workers.type as SettingType.IntRange).default)

        val r128 = SettingsRegistry.getByKey("convert_write_r128")
        assertNotNull(r128)
        assertEquals(false, (r128.type as SettingType.Toggle).default)

        val ffmpegPath = SettingsRegistry.getByKey("convert_ffmpeg_path")
        assertNotNull(ffmpegPath)
        assertEquals("", (ffmpegPath.type as SettingType.Text).default)
        assertEquals(setOf(SettingPlatform.DESKTOP), ffmpegPath.platforms)

        val rescan = SettingsRegistry.getByKey("convert_rescan_after")
        assertNotNull(rescan)
        assertEquals(true, (rescan.type as SettingType.Toggle).default)
    }

    @Test
    fun testRegistryKeysAreUnique() {
        val allEntries = SettingsRegistry.getAll()
        val uniqueKeys = allEntries.map { it.key }.toSet()
        assertEquals(allEntries.size, uniqueKeys.size, "All registry keys must be unique")
    }
}
