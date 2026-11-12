package com.nodeplayer.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PaletteDownsampleCharacterizationTest {

    @Test
    fun testComputeBitmapSampleSizeReturnsOneWithinMaxDim() {
        assertEquals(1, computeBitmapSampleSize(128, 128, 192))
        assertEquals(1, computeBitmapSampleSize(192, 192, 192))
        assertEquals(1, computeBitmapSampleSize(256, 256, 192))
    }

    @Test
    fun testComputeBitmapSampleSizePowerOfTwoForLargeArtwork() {
        // 1024x1024 targeting 192px -> half=512 (2), 256 (4), 128 < 192 -> sampleSize = 4
        assertEquals(4, computeBitmapSampleSize(1024, 1024, 192))
        // 1024x1024 targeting 128px -> half=512 (2), 256 (4), 128 (8) -> sampleSize = 8
        assertEquals(8, computeBitmapSampleSize(1024, 1024, 128))
        // 2048x2048 targeting 192px -> sampleSize = 8
        assertEquals(8, computeBitmapSampleSize(2048, 2048, 192))
    }

    @Test
    fun testComputeBitmapSampleSizeHandlesNonPositiveBoundsSafely() {
        assertEquals(1, computeBitmapSampleSize(0, 0, 192))
        assertEquals(1, computeBitmapSampleSize(-100, 500, 192))
        assertEquals(1, computeBitmapSampleSize(500, -1, 192))
        assertEquals(1, computeBitmapSampleSize(1024, 1024, 0))
        assertEquals(1, computeBitmapSampleSize(1024, 1024, -64))
    }
}
