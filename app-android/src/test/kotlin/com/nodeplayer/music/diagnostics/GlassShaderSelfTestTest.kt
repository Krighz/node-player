package com.nodeplayer.music.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassShaderSelfTestTest {

    @Test
    fun testForceFailReturnsFalse() {
        val result = GlassShaderSelfTest.executeSelfTest(forceFail = true)

        assertFalse(result.passed)
        assertTrue(result.reason.contains("Forced failure", ignoreCase = true))
    }

    @Test
    fun testDimensionsAndPadding() {
        assertEquals(360, GlassShaderSelfTest.DOCK_WIDTH)
        assertEquals(80, GlassShaderSelfTest.DOCK_HEIGHT)
        assertEquals(32, GlassShaderSelfTest.PADDING)
        assertEquals(424, GlassShaderSelfTest.BITMAP_WIDTH)
        assertEquals(144, GlassShaderSelfTest.BITMAP_HEIGHT)
    }
}
