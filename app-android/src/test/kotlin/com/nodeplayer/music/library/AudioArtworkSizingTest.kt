package com.nodeplayer.music.library

import coil.size.Dimension
import coil.size.Size
import com.nodeplayer.ui.components.computeBitmapSampleSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioArtworkSizingTest {

    @Test
    fun testResolveTargetSizeClampsPixelDimensionsAndDefaultsUndefined() {
        // Exact bounds within [96, 512]
        assertEquals(144 to 144, AudioArtworkFetcher.resolveTargetSize(Size(144, 144)))
        assertEquals(320 to 240, AudioArtworkFetcher.resolveTargetSize(Size(320, 240)))

        // Clamped below minimum (96) and above maximum (512)
        assertEquals(96 to 96, AudioArtworkFetcher.resolveTargetSize(Size(32, 48)))
        assertEquals(512 to 512, AudioArtworkFetcher.resolveTargetSize(Size(1200, 1200)))

        // Undefined dimensions fall back to 256
        assertEquals(256 to 256, AudioArtworkFetcher.resolveTargetSize(Size.ORIGINAL))
        assertEquals(144 to 256, AudioArtworkFetcher.resolveTargetSize(Size(Dimension.Pixels(144), Dimension.Undefined)))
        assertEquals(256 to 512, AudioArtworkFetcher.resolveTargetSize(Size(Dimension.Undefined, Dimension.Pixels(800))))
    }

    @Test
    fun testComputeBitmapSampleSizePowerOfTwoDownsampling() {
        // Already smaller or equal -> sampleSize = 1
        assertEquals(1, computeBitmapSampleSize(256, 256, 256))
        assertEquals(1, computeBitmapSampleSize(128, 128, 256))

        // 1024x1024 targeting 256 -> half=512 >= 256 (x2), half/2=256 >= 256 (x4) -> 4
        assertEquals(4, computeBitmapSampleSize(1024, 1024, 256))

        // 2048x2048 targeting 144 -> half=1024 -> /1=1024 >= 144, /2=512 >= 144, /4=256 >= 144, /8=128 < 144 -> 8
        assertEquals(8, computeBitmapSampleSize(2048, 2048, 144))

        // Invalid/zero dimensions safely return 1
        assertEquals(1, computeBitmapSampleSize(0, 500, 256))
        assertEquals(1, computeBitmapSampleSize(500, -1, 256))
    }
}
