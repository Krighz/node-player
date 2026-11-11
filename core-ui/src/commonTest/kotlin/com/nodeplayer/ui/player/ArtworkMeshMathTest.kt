package com.nodeplayer.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ArtworkMeshMathTest {

    @Test
    fun testSeamRow0IsPreservedLiterally() {
        val cols = 6
        val rows = 6
        val cells = IntArray(cols * rows) { index -> 0xFF000000.toInt() or index }
        val rotated = ArtworkMeshGenerator.rotateBelowSeam(cells, cols, rows, seed = 42)

        // Seam row 0 must remain identical element-by-element
        for (x in 0 until cols) {
            assertEquals(
                cells[x],
                rotated[x],
                "Cell at row 0, col $x must be identical before and after rotateBelowSeam"
            )
        }
    }

    @Test
    fun testLowerRowsAreAlteredByCyclicShiftOrMirror() {
        val cols = 6
        val rows = 6
        val cells = IntArray(cols * rows) { index -> 0xFF000000.toInt() or index }
        val rotated = ArtworkMeshGenerator.rotateBelowSeam(cells, cols, rows, seed = 12345)

        var alteredCount = 0
        for (i in cols until (cols * rows)) {
            if (cells[i] != rotated[i]) {
                alteredCount++
            }
        }
        assertTrue(alteredCount > 0, "Lower rows (1..5) should undergo cyclic shift or mirror")
    }

    @Test
    fun testSmoothstepHermiteBounds() {
        assertEquals(0f, ArtworkMeshGenerator.smoothstep(0f), 0.0001f)
        assertEquals(1f, ArtworkMeshGenerator.smoothstep(1f), 0.0001f)
        assertEquals(0.5f, ArtworkMeshGenerator.smoothstep(0.5f), 0.0001f)

        // Derivative at 0 and 1 should be zero (horizontal tangents)
        val delta = 0.01f
        val stepNearZero = ArtworkMeshGenerator.smoothstep(delta) - ArtworkMeshGenerator.smoothstep(0f)
        val linearNearZero = delta
        assertTrue(stepNearZero < linearNearZero, "Hermite curve should ease in near 0")

        // Out-of-bounds inputs should be clamped safely
        assertEquals(0f, ArtworkMeshGenerator.smoothstep(-1f), 0.0001f)
        assertEquals(1f, ArtworkMeshGenerator.smoothstep(2f), 0.0001f)
    }

    @Test
    fun testResampleHermiteDimensionsAndEdgeValues() {
        val srcCols = 6
        val srcRows = 6
        val src = IntArray(srcCols * srcRows) { 0xFF102030.toInt() }
        val dstW = 32
        val dstH = 32
        val resampled = ArtworkMeshGenerator.resampleHermite(src, srcCols, srcRows, dstW, dstH)

        assertEquals(dstW * dstH, resampled.size)
        // Uniform color should remain identical after Hermite interpolation
        for (i in resampled.indices) {
            assertEquals(0xFF102030.toInt(), resampled[i])
        }
    }

    @Test
    fun testBoxBlurPassMaintainsBounds() {
        val width = 32
        val height = 32
        val pixels = IntArray(width * height) { (0xFF shl 24) or (it and 0xFF) }
        val blurred = ArtworkMeshGenerator.boxBlurPass(pixels, width, height)

        assertEquals(pixels.size, blurred.size)
        // Ensure no exception and alpha is preserved at 0xFF
        for (pixel in blurred) {
            val alpha = (pixel shr 24) and 0xFF
            assertEquals(0xFF, alpha)
        }
    }

    @Test
    fun testLiftColorFloorsBlackOffPureBlack() {
        val pureBlack = 0xFF000000.toInt()
        val lifted = ArtworkMeshGenerator.liftColor(pureBlack)
        val r = (lifted shr 16) and 0xFF
        val g = (lifted shr 8) and 0xFF
        val b = lifted and 0xFF

        // Value must be lifted off pure 0 per D-05 (lightness >= 0.045f)
        assertTrue(r > 0 || g > 0 || b > 0, "Color should be lifted off pure black")
    }
}
