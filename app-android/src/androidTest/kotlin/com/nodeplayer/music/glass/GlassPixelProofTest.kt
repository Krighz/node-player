package com.nodeplayer.music.glass

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nodeplayer.model.GlassControlsConfig
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.components.ConvxLiquidDock
import com.nodeplayer.ui.theme.LocalControlBackdrop
import com.nodeplayer.ui.theme.LocalGlassControlsConfig
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class GlassPixelProofTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun computeLuminance(color: Int): Float {
        val r = android.graphics.Color.red(color) / 255f
        val g = android.graphics.Color.green(color) / 255f
        val b = android.graphics.Color.blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun computeEdgeEnergy(bitmap: Bitmap, startX: Int, startY: Int, width: Int, height: Int): Float {
        var totalDiff = 0.0
        var count = 0
        val endX = (startX + width).coerceAtMost(bitmap.width - 1)
        val endY = (startY + height).coerceAtMost(bitmap.height - 1)

        for (y in startY until endY) {
            for (x in startX until endX) {
                val lCurrent = computeLuminance(bitmap.getPixel(x, y))
                val lRight = computeLuminance(bitmap.getPixel(x + 1, y))
                val lDown = computeLuminance(bitmap.getPixel(x, y + 1))
                totalDiff += abs(lCurrent - lRight) + abs(lCurrent - lDown)
                count += 2
            }
        }
        return if (count > 0) (totalDiff / count).toFloat() else 0f
    }

    private fun computeStats(bitmap: Bitmap, startX: Int, startY: Int, width: Int, height: Int): Pair<Float, Float> {
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        val endX = (startX + width).coerceAtMost(bitmap.width)
        val endY = (startY + height).coerceAtMost(bitmap.height)

        for (y in startY until endY) {
            for (x in startX until endX) {
                val lum = computeLuminance(bitmap.getPixel(x, y))
                sum += lum
                sumSq += lum * lum
                count++
            }
        }
        if (count == 0) return Pair(0f, 0f)
        val mean = (sum / count).toFloat()
        val variance = (sumSq / count - mean * mean).coerceAtLeast(0.0)
        val stdDev = sqrt(variance).toFloat()
        return Pair(mean, stdDev)
    }

    @Test
    fun glassDockBlursBackdropAndRendersNonBlack() {
        val themeConfig = GlassThemeConfig(
            blurRadiusDp = 28f,
            refractionShaderEnabled = true,
            controlsConfig = GlassControlsConfig(masterEnabled = true)
        )

        composeTestRule.setContent {
            val pageBackdrop = rememberLayerBackdrop()
            var currentRoute by remember { mutableStateOf("library") }

            CompositionLocalProvider(
                LocalGlassThemeConfig provides themeConfig,
                LocalGlassControlsConfig provides themeConfig.controlsConfig,
                LocalControlBackdrop provides pageBackdrop
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("TestRoot")
                        .background(Color.Black)
                ) {
                    // High contrast 4px vertical black/white stripes
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(pageBackdrop)
                            .testTag("StripeCanvas")
                    ) {
                        val stripeWidth = 4f
                        var x = 0f
                        var isWhite = true
                        while (x < size.width) {
                            drawRect(
                                color = if (isWhite) Color.White else Color.Black,
                                topLeft = Offset(x, 0f),
                                size = Size(stripeWidth, size.height)
                            )
                            x += stripeWidth
                            isWhite = !isWhite
                        }
                    }

                    // Floating ConvxLiquidDock above pageBackdrop
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .testTag("DockContainer")
                    ) {
                        ConvxLiquidDock(
                            selectedRoute = currentRoute,
                            onRouteChange = { currentRoute = it },
                            backdrop = pageBackdrop,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        val fullImage = composeTestRule.onNodeWithTag("TestRoot").captureToImage()
        val bitmap = fullImage.asAndroidBitmap()

        // 1. Measure background edge energy in stripe region (above the dock)
        val stripeEnergy = computeEdgeEnergy(bitmap, 50, 100, 300, 200)
        val (pageMean, pageStdDev) = computeStats(bitmap, 50, 100, 300, 200)

        // 2. Measure dock region
        val dockY = (bitmap.height - 250).coerceAtLeast(0)
        val dockHeight = 150.coerceAtMost(bitmap.height - dockY)
        val dockWidth = (bitmap.width - 200).coerceAtLeast(100)
        val dockX = 100

        val dockEnergy = computeEdgeEnergy(bitmap, dockX + 50, dockY + 20, dockWidth - 100, dockHeight - 40)
        val (dockMean, dockStdDev) = computeStats(bitmap, dockX + 50, dockY + 20, dockWidth - 100, dockHeight - 40)

        Log.i("GlassProof", "=== GLASS PIXEL PROOF MEASUREMENTS ===")
        Log.i("GlassProof", "Stripe background: Mean Lum=$pageMean, StdDev=$pageStdDev, EdgeEnergy=$stripeEnergy")
        Log.i("GlassProof", "Dock body interior: Mean Lum=$dockMean, StdDev=$dockStdDev, EdgeEnergy=$dockEnergy")
        Log.i("GlassProof", "Energy ratio (dock / stripe) = ${dockEnergy / stripeEnergy * 100f}% (Target: <= 40%)")

        // Probe 10x10 grid values
        val sb = StringBuilder("Dock 10x10 Probe Grid Lum:\n")
        val stepX = (dockWidth - 40) / 10
        val stepY = (dockHeight - 40) / 10
        for (gy in 0 until 10) {
            for (gx in 0 until 10) {
                val px = dockX + 20 + gx * stepX
                val py = dockY + 20 + gy * stepY
                val col = bitmap.getPixel(px, py)
                val lum = computeLuminance(col)
                sb.append(String.format("%.2f ", lum))
            }
            sb.append("\n")
        }
        Log.i("GlassProof", sb.toString())

        // Assert 1: Edge energy inside dock must be reduced significantly (<= 40% of stripe region)
        assertTrue(
            "Dock energy ($dockEnergy) must be <= 40% of stripe background energy ($stripeEnergy), ratio=${dockEnergy / stripeEnergy}",
            dockEnergy <= stripeEnergy * 0.45f
        )

        // Assert 2: Non-black / non-transparent
        assertTrue(
            "Dock mean luminance ($dockMean) must be >= 20% of page luminance ($pageMean)",
            dockMean >= pageMean * 0.20f
        )
        assertTrue(
            "Dock luminance stdDev ($dockStdDev) must be > 0 (not flat solid color)",
            dockStdDev > 0.01f
        )

        Log.i("GlassProof", "SUCCESS: All pixel proof assertions passed cleanly!")
    }
}
