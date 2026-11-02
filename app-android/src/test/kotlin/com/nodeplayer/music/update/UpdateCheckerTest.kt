package com.nodeplayer.music.update

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {

    private val validReleaseJson = """
        {
          "tag_name": "v1.2.0",
          "body": "## What's Changed\n* Added in-app update system\n* Added UPI support",
          "published_at": "2026-11-02T12:00:00Z",
          "assets": [
            {
              "name": "NodePlayer.apk",
              "browser_download_url": "https://github.com/Krighz/node-player/releases/download/v1.2.0/NodePlayer.apk",
              "size": 4194304
            },
            {
              "name": "NodePlayer.apk.sha256",
              "browser_download_url": "https://github.com/Krighz/node-player/releases/download/v1.2.0/NodePlayer.apk.sha256",
              "size": 64
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testParseReleaseJsonWhenUpdateAvailable() {
        val result = UpdateChecker.parseReleaseJson(validReleaseJson, currentVersion = "1.1.0")
        assertTrue("Expected UpdateAvailable, got $result", result is UpdateCheckResult.UpdateAvailable)

        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("1.2.0", available.info.versionName)
        assertEquals("v1.2.0", available.info.releaseTag)
        assertEquals("https://github.com/Krighz/node-player/releases/download/v1.2.0/NodePlayer.apk", available.info.downloadUrl)
        assertEquals("https://github.com/Krighz/node-player/releases/download/v1.2.0/NodePlayer.apk.sha256", available.info.sha256Url)
        assertEquals(4194304L, available.info.assetSize)
        assertTrue(available.info.releaseNotes.contains("Added in-app update system"))
    }

    @Test
    fun testParseReleaseJsonWhenAlreadyUpToDate() {
        // Current version is equal to release tag
        val resultEqual = UpdateChecker.parseReleaseJson(validReleaseJson, currentVersion = "1.2.0")
        assertEquals(UpdateCheckResult.UpToDate, resultEqual)

        // Current version is newer than release tag
        val resultNewer = UpdateChecker.parseReleaseJson(validReleaseJson, currentVersion = "1.3.0")
        assertEquals(UpdateCheckResult.UpToDate, resultNewer)
    }

    @Test
    fun testParseReleaseJsonMissingApkAsset() {
        val jsonNoApk = """
            {
              "tag_name": "v1.2.0",
              "assets": [
                {
                  "name": "NodePlayer.apk.sha256",
                  "browser_download_url": "https://example.com/sha256"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateChecker.parseReleaseJson(jsonNoApk, currentVersion = "1.1.0")
        assertTrue(result is UpdateCheckResult.Error)
        assertTrue((result as UpdateCheckResult.Error).message.contains("missing NodePlayer.apk"))
    }

    @Test
    fun testParseReleaseJsonMissingSha256Asset() {
        val jsonNoSha = """
            {
              "tag_name": "v1.2.0",
              "assets": [
                {
                  "name": "NodePlayer.apk",
                  "browser_download_url": "https://example.com/NodePlayer.apk"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateChecker.parseReleaseJson(jsonNoSha, currentVersion = "1.1.0")
        assertTrue(result is UpdateCheckResult.Error)
        assertTrue((result as UpdateCheckResult.Error).message.contains("missing NodePlayer.apk"))
    }

    @Test
    fun testParseReleaseJsonMalformed() {
        val result = UpdateChecker.parseReleaseJson("{ not valid json }", currentVersion = "1.1.0")
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun testParseReleaseJsonMissingTagName() {
        val jsonNoTag = """{ "assets": [] }"""
        val result = UpdateChecker.parseReleaseJson(jsonNoTag, currentVersion = "1.1.0")
        assertTrue(result is UpdateCheckResult.Error)
    }
}
