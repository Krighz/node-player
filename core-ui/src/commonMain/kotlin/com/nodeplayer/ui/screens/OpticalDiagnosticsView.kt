package com.nodeplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.Track
import com.nodeplayer.ui.PlayerNavDestination
import com.nodeplayer.ui.components.ConvxMiniPlayer
import com.nodeplayer.ui.components.LiquidDock
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig

/**
 * Unforgiving Optical Diagnostics Test Screen (D-01, D-02, D-03, D-04).
 *
 * Provides:
 * 1. Triple-zone scrollable high-contrast test pattern recorded into [pageBackdrop]:
 *    - Zone 1: Alternating monochrome high-contrast vertical and 45-degree diagonal stripes.
 *    - Interactive embedded [LiquidSlider] over the pattern to test continuous dragging and squish.
 *    - Zone 2: Dense multi-size typography blocks (10sp to 24sp, multiple weights and contrast levels).
 *    - Zone 3: High-frequency pixel alignment grid (16dp square grid, center crosshairs, concentric circles).
 * 2. Full active floating liquid glass chrome overlays taking [pageBackdrop]:
 *    - 56dp squircle mini player capsule ([ConvxMiniPlayer]).
 *    - 64dp capsule dock with sliding puck and 56dp circular search button ([LiquidDock]).
 */
@Composable
fun OpticalDiagnosticsView(
    onBack: () -> Unit,
    themeConfig: GlassThemeConfig = LocalGlassThemeConfig.current,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val pageBackdrop = rememberLayerBackdrop()
    val scrollState = rememberScrollState()

    var testSliderValue by remember { mutableFloatStateOf(0.42f) }
    var selectedDockTab by remember { mutableStateOf(PlayerNavDestination.SETTINGS) }
    var isMiniPlaying by remember { mutableStateOf(true) }

    val mockTrack = remember {
        Track(
            id = 9999L,
            title = "Liquid Glass Optical Shader",
            artist = "Node Player Engine (AGSL / SkSL)",
            album = "Reference Diagnostics Suite",
            durationMs = 214000L,
            path = "virtual://diagnostics/optical_test.flac",
            isLossless = true,
            isHiRes = true
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C12))
    ) {
        // 1. Scrollable Test Pattern Layer (Recorded into pageBackdrop)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .layerBackdrop(pageBackdrop)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(84.dp))

            // Test Pattern Introduction Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141824).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OPTICAL BENCHMARK CRITERIA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scroll the pattern behind the floating chrome overlays to verify: (1) Total elimination of sharp text inside dock body, (2) Strong body blur & refractive bending, (3) Puck chromatic fringing, (4) Continuous velocity stretch on LiquidSlider, (5) Zero card self-refraction.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ZONE 1: Monochrome High-Contrast Vertical & 45-degree Diagonal Stripes
            Text(
                text = "ZONE 1: HIGH-CONTRAST MONOCHROME STRIPES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Vertical Stripes Canvas (Alternating 12dp black and white bars)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = 12.dp.toPx()
                    var x = 0f
                    var isWhite = false
                    while (x < size.width) {
                        drawRect(
                            color = if (isWhite) Color.White else Color.Black,
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, size.height)
                        )
                        isWhite = !isWhite
                        x += barWidth
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 45-Degree Diagonal Stripes Canvas (High-pitch refraction grating)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color.Black)
                    val pitch = 16.dp.toPx()
                    val stroke = 8.dp.toPx()
                    var offset = -size.height
                    while (offset < size.width + size.height) {
                        drawLine(
                            color = Color.White,
                            start = Offset(offset, 0f),
                            end = Offset(offset + size.height, size.height),
                            strokeWidth = stroke
                        )
                        offset += pitch
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // INTERACTIVE TEST SLIDER OVER PATTERN
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141824).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Interactive LiquidSlider Test", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(
                            text = "${(testSliderValue * 100).toInt()}%",
                            color = accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Drag continuously to test damped spring velocity stretch, finger squish, and live refraction over the test pattern.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidSlider(
                        value = testSliderValue,
                        onValueChange = { testSliderValue = it },
                        backdrop = pageBackdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ZONE 2: Dense Multi-Size Typography Blocks
            Text(
                text = "ZONE 2: DENSE MULTI-SIZE TYPOGRAPHY BLOCKS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF11141E))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "LIQUID GLASS REFRACTION BENCHMARK 2026",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 28.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Audiophile Bit-Perfect Dynamic Optical Shader v2.1",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The quick brown fox jumps over the lazy dog. 0123456789. High frequency typographic sampling tests edge diffusion and prevents raster text bleed inside glass shader bodies under continuous drag and motion.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pure Kotlin multiplatform rendering across Android ART (API 26+) and Windows Desktop (JVM 17). Convx spring physics, DampedDragAnimation, zero per-frame draw loop allocations, and EBU R128 loudness verification across all playback pathways.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.70f),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "LATENCY: 0.12ms | RMS: -14.2 dBFS | PEAK: -0.1 dBTP | AGSL: ACTIVE | BACKDROP: LAYER_RECORDED | CHROMATIC_ABERRATION: 0.25 | BLUR_RADIUS: 24dp | SPECULAR: 0.85",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00F0FF),
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ZONE 3: High-Frequency Pixel Alignment Grid
            Text(
                text = "ZONE 3: HIGH-FREQUENCY PIXEL ALIGNMENT GRID",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F111A))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 16.dp.toPx()
                    val gridColor = Color.White.copy(alpha = 0.18f)

                    // Vertical grid lines
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += step
                    }

                    // Horizontal grid lines
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                    }

                    // Bold center crosshairs
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    drawLine(accent, Offset(centerX, 0f), Offset(centerX, size.height), strokeWidth = 2.dp.toPx())
                    drawLine(accent, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = 2.dp.toPx())

                    // Diagonal corner crosshairs
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())

                    // Concentric circles at 32dp, 64dp, 96dp, 128dp
                    val circleRadii = listOf(32.dp.toPx(), 64.dp.toPx(), 96.dp.toPx(), 128.dp.toPx())
                    for (radius in circleRadii) {
                        drawCircle(
                            color = Color(0xFF00F0FF).copy(alpha = 0.5f),
                            radius = radius,
                            center = Offset(centerX, centerY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            // Generous bottom spacer so pattern scrolls completely behind bottom chrome
            Spacer(modifier = Modifier.height(240.dp))
        }

        // 2. Fixed Top Bar with Back Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0C12).copy(alpha = 0.92f))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Liquid Glass Optical Test",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Triple-zone optical pattern, blur, refraction and puck fringing verification",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                )
            }
        }

        // 3. Floating Chrome Sibling Overlays (Positioned above backdrop layer in z-order taking pageBackdrop)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 56dp squircle mini player capsule
            ConvxMiniPlayer(
                currentTrack = mockTrack,
                isPlaying = isMiniPlaying,
                progressFraction = 0.35f,
                onPlayPauseClick = { isMiniPlaying = !isMiniPlaying },
                onSkipNextClick = { },
                onClick = { },
                backdrop = pageBackdrop,
                rmsLevel = if (isMiniPlaying) 0.65f else 0f,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 64dp capsule dock with 4 tabs and animated sliding puck + 56dp circular Search button
            LiquidDock(
                currentNav = selectedDockTab,
                onNavSelect = { selectedDockTab = it },
                backdrop = pageBackdrop,
                onSearchClick = { }
            )
        }
    }
}
