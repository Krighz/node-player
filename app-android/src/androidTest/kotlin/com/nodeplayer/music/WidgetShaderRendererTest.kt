package com.nodeplayer.music

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nodeplayer.music.widget.WidgetShaderConfig
import com.nodeplayer.music.widget.WidgetShaderRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetShaderRendererTest {

    @Test
    fun testWidgetShaderHardwareRendering() {
        val config = WidgetShaderConfig(
            widthPx = 500,
            heightPx = 250,
            cornerRadiusPx = 36f,
            isPlaying = true,
            phase = 1.5f,
            glassOpacity = 0.85f
        )

        val startTime = System.currentTimeMillis()
        val bitmap = WidgetShaderRenderer.renderGlassBackground(config)
        val durationMs = System.currentTimeMillis() - startTime

        assertNotNull("Rendered bitmap must not be null", bitmap)
        assertEquals("Width must match config", 500, bitmap.width)
        assertEquals("Height must match config", 250, bitmap.height)
        assertEquals("Bitmap config must be ARGB_8888", Bitmap.Config.ARGB_8888, bitmap.config)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputFile = java.io.File(context.cacheDir, "test_shader.png")
        val fos = java.io.FileOutputStream(outputFile)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.close()
        android.util.Log.i("WidgetTest", "Saved test_shader.png to ${outputFile.absolutePath}")

        assertTrue("Render should take less than 1500ms on realme GT 6T cold start", durationMs < 1500)

        // Center pixel should be non-transparent
        val centerPixel = bitmap.getPixel(250, 125)
        val alpha = android.graphics.Color.alpha(centerPixel)
        val red = android.graphics.Color.red(centerPixel)
        val green = android.graphics.Color.green(centerPixel)
        val blue = android.graphics.Color.blue(centerPixel)
        android.util.Log.i("WidgetTest", "Center pixel (250, 125): A=$alpha R=$red G=$green B=$blue (0x${Integer.toHexString(centerPixel)})")
        android.util.Log.i("WidgetTest", "Pixel (100, 100): 0x${Integer.toHexString(bitmap.getPixel(100, 100))}")
        android.util.Log.i("WidgetTest", "Pixel (300, 150): 0x${Integer.toHexString(bitmap.getPixel(300, 150))}")
        assertTrue("Center alpha should be non-zero", alpha > 50)

        // Corner (0,0) should be transparent due to rounded box SDF
        val cornerPixel = bitmap.getPixel(2, 2)
        val cornerAlpha = android.graphics.Color.alpha(cornerPixel)
        assertEquals("Corner pixel must be transparent", 0, cornerAlpha)

        // Cache hit test
        val cached = WidgetShaderRenderer.renderGlassBackground(config)
        assertEquals("Subsequent call should return cached bitmap", bitmap, cached)
    }

    @Test
    fun testStaticFallbackRendering() {
        val config = WidgetShaderConfig(
            widthPx = 400,
            heightPx = 200,
            cornerRadiusPx = 24f,
            isPlaying = false,
            isPerformanceMode = true
        )

        val bitmap = WidgetShaderRenderer.renderGlassBackground(config)
        assertNotNull(bitmap)
        assertEquals(400, bitmap.width)
        assertEquals(200, bitmap.height)
    }
}
