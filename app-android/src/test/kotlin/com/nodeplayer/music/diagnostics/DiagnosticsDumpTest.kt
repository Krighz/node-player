package com.nodeplayer.music.diagnostics

import com.nodeplayer.model.GlassThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DiagnosticsDumpTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDiagnosticsZipPackagingAndIntegrity() {
        val rootDir = tempFolder.newFolder("diagnostics")
        val infoFile = File(rootDir, "device_info.txt").apply { writeText("SOC: Dimensity 8020\nGPU: Mali-G77 MC9\n") }
        val eventsFile = File(rootDir, "graphics_events.log").apply { writeText("Dock created size: 1080x240\nShader compiled successfully\n") }
        val logcatFile = File(rootDir, "logcat_sanitized.txt").apply { writeText("I/RenderThread: frame completed in 8.2ms\n") }
        val screenshotFile = File(rootDir, "screenshot.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) }

        val zipFile = File(rootDir, "nodeplayer-diagnostics-test.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            listOf(infoFile, eventsFile, logcatFile, screenshotFile).forEach { file ->
                zos.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        assertTrue("ZIP file should exist", zipFile.exists())
        assertTrue("ZIP file should not be empty", zipFile.length() > 0)

        // Verify ZIP contents
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList().map { it.name }.toSet()
            assertTrue(entries.contains("device_info.txt"))
            assertTrue(entries.contains("graphics_events.log"))
            assertTrue(entries.contains("logcat_sanitized.txt"))
            assertTrue(entries.contains("screenshot.png"))

            // Verify content of device_info.txt
            val infoEntry = zip.getEntry("device_info.txt")
            assertNotNull(infoEntry)
            val infoContent = zip.getInputStream(infoEntry).bufferedReader().readText()
            assertTrue(infoContent.contains("Dimensity 8020"))
            assertTrue(infoContent.contains("Mali-G77 MC9"))
        }
    }

    @Test
    fun testAutoModeShaderSelectionOnPassVsFail() {
        // When self-test PASSES (e.g. Snapdragon 7+ Gen 3 / Adreno 732 on realme GT 6T)
        val configAuto = GlassThemeConfig(shaderMode = "AUTO")
        assertTrue(
            "Auto mode should activate full shader when self test passes",
            configAuto.isShaderActive(selfTestPassed = true)
        )

        // When self-test FAILS (e.g. Dimensity 8020 with Mali-G77 spill bug)
        assertFalse(
            "Auto mode should deactivate shader and fallback to blur when self test fails",
            configAuto.isShaderActive(selfTestPassed = false)
        )

        // When user manually picks Safe Graphics Mode
        val configSafe = GlassThemeConfig(
            shaderMode = "BLUR_ONLY",
            glassOpacity = 0.85f,
            blurStrength = 0.50f,
            glassTexture = 0.0f,
            edgeHighlightStrength = 0.20f
        )
        assertEquals("BLUR_ONLY", configSafe.shaderMode)
        assertFalse(
            "Safe graphics mode should disable AGSL shader entirely",
            configSafe.isShaderActive(selfTestPassed = true)
        )
    }
}
