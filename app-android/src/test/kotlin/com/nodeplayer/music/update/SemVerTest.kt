package com.nodeplayer.music.update

import org.junit.Assert.*
import org.junit.Test

class SemVerTest {

    @Test
    fun testParseStandardVersions() {
        val v1 = SemVer.parse("1.2.0")
        assertNotNull(v1)
        assertEquals(1, v1?.major)
        assertEquals(2, v1?.minor)
        assertEquals(0, v1?.patch)
        assertNull(v1?.preRelease)

        val v2 = SemVer.parse("v1.2.3")
        assertNotNull(v2)
        assertEquals(1, v2?.major)
        assertEquals(2, v2?.minor)
        assertEquals(3, v2?.patch)

        val v3 = SemVer.parse("V2.0.0-rc1")
        assertNotNull(v3)
        assertEquals(2, v3?.major)
        assertEquals(0, v3?.minor)
        assertEquals(0, v3?.patch)
        assertEquals("rc1", v3?.preRelease)
    }

    @Test
    fun testVersionComparison() {
        // Newer minor
        assertTrue(SemVer.isNewer("1.2.0", "1.1.0"))
        assertTrue(SemVer.isNewer("v1.2.0", "1.1.0"))
        assertTrue(SemVer.isNewer("1.2.0", "v1.1.0"))

        // Older minor
        assertFalse(SemVer.isNewer("1.1.0", "1.2.0"))

        // Equal version
        assertFalse(SemVer.isNewer("1.2.0", "1.2.0"))
        assertFalse(SemVer.isNewer("v1.2.0", "1.2.0"))
        assertEquals(0, SemVer.compare("v1.2.0", "1.2.0"))

        // Newer patch
        assertTrue(SemVer.isNewer("1.2.1", "1.2.0"))
        assertFalse(SemVer.isNewer("1.2.0", "1.2.1"))

        // Newer major
        assertTrue(SemVer.isNewer("2.0.0", "1.9.9"))
        assertFalse(SemVer.isNewer("1.9.9", "2.0.0"))
    }

    @Test
    fun testPreReleaseComparison() {
        // Stable release is newer than pre-release with same numbers
        assertTrue(SemVer.isNewer("1.2.0", "1.2.0-beta1"))
        assertFalse(SemVer.isNewer("1.2.0-beta1", "1.2.0"))

        // Pre-release vs newer version
        assertTrue(SemVer.isNewer("1.2.1-rc1", "1.2.0"))
    }

    @Test
    fun testInvalidOrBlankVersions() {
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("   "))
        assertEquals(-1, SemVer.compare("", "1.0.0"))
        assertEquals(1, SemVer.compare("1.0.0", ""))
    }
}
