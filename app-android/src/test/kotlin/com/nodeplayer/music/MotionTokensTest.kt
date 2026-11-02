package com.nodeplayer.music

import com.nodeplayer.music.ui.theme.MotionTokens
import org.junit.Assert.assertNotNull
import org.junit.Test

class MotionTokensTest {

    @Test
    fun testMotionTokensDefinitions() {
        assertNotNull(MotionTokens.Snappy)
        assertNotNull(MotionTokens.Smooth)
        assertNotNull(MotionTokens.Bouncy)
        assertNotNull(MotionTokens.Gentle)
    }
}
