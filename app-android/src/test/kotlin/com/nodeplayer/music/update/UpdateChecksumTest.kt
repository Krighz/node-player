package com.nodeplayer.music.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateChecksumTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testParseSha256Variants() {
        val hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        // Plain hash
        assertEquals(hash, UpdateDownloadManager.parseSha256Text(hash))

        // Hash with spaces and filename
        val withFilename = "$hash  NodePlayer.apk"
        assertEquals(hash, UpdateDownloadManager.parseSha256Text(withFilename))

        // Hash with binary indicator asterisk
        val withAsterisk = "$hash *NodePlayer.apk"
        assertEquals(hash, UpdateDownloadManager.parseSha256Text(withAsterisk))

        // Uppercase conversion to lowercase
        val upperHash = hash.uppercase()
        assertEquals(hash, UpdateDownloadManager.parseSha256Text(upperHash))

        // Blank or whitespace returns null
        assertNull(UpdateDownloadManager.parseSha256Text(""))
        assertNull(UpdateDownloadManager.parseSha256Text("   \n"))
    }

    @Test
    fun testCalculateSha256() {
        // Empty file has known SHA-256
        val emptyFile = tempFolder.newFile("empty.txt")
        val emptyHash = UpdateDownloadManager.calculateSha256(emptyFile)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)

        // Known string "hello nodeplayer"
        val testFile = tempFolder.newFile("test.bin")
        testFile.writeText("hello nodeplayer")
        val computedHash = UpdateDownloadManager.calculateSha256(testFile)
        assertNotNull(computedHash)
        assertEquals(64, computedHash.length)
        assertTrue(computedHash.matches("^[a-f0-9]{64}$".toRegex()))
    }
}
