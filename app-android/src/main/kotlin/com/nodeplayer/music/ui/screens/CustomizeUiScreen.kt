package com.nodeplayer.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.music.data.NodeThemeSerializer
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.components.glassLensBubble
import com.nodeplayer.music.ui.components.glassRefraction
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.ui.components.LiquidChip
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.LiquidToggle
import kotlin.math.roundToInt

@Composable
fun CustomizeUiScreen(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    onApplySafeGraphicsMode: () -> Unit = {},
    onReportProblem: () -> Unit = {},
    onOpenMotionPlayground: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.bufferedReader().readText()
                    val loaded = NodeThemeSerializer.fromJson(json)
                    if (loaded != null) {
                        onThemeConfigChange(loaded.copy(glassTexture = 0.0f, noiseGrainAlpha = 0.0f))
                        Toast.makeText(context, "Theme imported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid theme file", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to import theme: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Customize UI",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
        Text(
            text = "CUSTOMIZE UI & GLASS",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // 1. Live Interactive Preview
                Text(
                    text = "Live Preview",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = "Reflects active glassmorphism, shapes, dock and typography",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(themeConfig.effectiveCornerRadiusDp.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE11D48),
                                    Color(0xFF7C3AED),
                                    Color(0xFF2563EB)
                                )
                            )
                        )
                        .padding(12.dp)
                ) {
                    val previewShape = RoundedCornerShape(themeConfig.effectiveCornerRadiusDp.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassRefraction(
                                shape = previewShape,
                                enabled = themeConfig.isShaderActive(true),
                                refractionIndex = 0.85f * (themeConfig.lensRefractionStrength / 0.85f),
                                chromaticFringe = themeConfig.chromaticFringeValue()
                            )
                            .glassSurface(
                                shape = previewShape,
                                tintAlpha = themeConfig.effectiveTintAlpha
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.35f * themeConfig.edgeHighlightStrength),
                                        Color.White.copy(alpha = 0.10f * themeConfig.edgeHighlightStrength)
                                    )
                                ),
                                shape = previewShape
                            )
                            .padding(12.dp)
                    ) {
                        // Header Track Info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "505 - Arctic Monkeys",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = "Font: ${themeConfig.fontFamily} • ${themeConfig.cornerRoundness}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.80f), fontSize = 11.sp)
                                )
                            }
                            IconButton(onClick = {}) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                            }
                        }

                        // Symmetrical Mini Dock Preview
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .glassLensBubble(
                                            shape = CircleShape,
                                            enabled = themeConfig.isShaderActive(true),
                                            magnification = 0.22f,
                                            refractionIndex = 0.65f * (themeConfig.lensRefractionStrength / 0.85f),
                                            chromaticFringe = themeConfig.chromaticFringeValue(),
                                            rimStrength = themeConfig.lensRimSpecular,
                                            rimColor = Color.White.copy(alpha = 0.90f * themeConfig.lensRimSpecular)
                                        )
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.18f * themeConfig.lensRimSpecular),
                                                    Color.White.copy(alpha = 0.04f)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.85f * themeConfig.lensRimSpecular),
                                                    Color.White.copy(alpha = 0.25f * themeConfig.lensRimSpecular)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Icon(Icons.Rounded.Explore, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                }
                            }

                            if (themeConfig.showDockSearchButton) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.20f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Presets
                Text(
                    text = "THEME PRESETS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        "Obsidian Liquid" to GlassThemeConfig.PRESET_OBSIDIAN_LIQUID,
                        "Frost Pearl" to GlassThemeConfig.PRESET_FROST_PEARL,
                        "Amber Flare" to GlassThemeConfig.PRESET_AMBER_FLARE,
                        "Neon Aurora" to GlassThemeConfig.PRESET_NEON_AURORA,
                        "Stealth OLED" to GlassThemeConfig.PRESET_STEALTH_OLED,
                        "Minimal Glass" to GlassThemeConfig.PRESET_MINIMAL_GLASS
                    )
                    presets.forEach { (name, presetConfig) ->
                        OutlinedButton(
                            onClick = { onThemeConfigChange(presetConfig.copy(glassTexture = 0.0f, noiseGrainAlpha = 0.0f)) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color(presetConfig.fixedAccentColorArgb).copy(alpha = 0.8f),
                                        Color.White.copy(alpha = 0.2f)
                                    )
                                )
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(presetConfig.fixedAccentColorArgb), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(name, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 3. Theme Mode & Dynamic Accent
                Text("Theme Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("SYSTEM" to "System", "DARK" to "Dark", "LIGHT" to "Light", "OLED" to "OLED").forEach { (mode, label) ->
                        LiquidChip(
                            selected = themeConfig.themeMode == mode,
                            onClick = { onThemeConfigChange(themeConfig.copy(themeMode = mode, isOledBlack = mode == "OLED")) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Accent Color Source", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ARTWORK" to "Album Art", "MATERIAL_YOU" to "Material You", "PRESET" to "Custom Preset").forEach { (src, label) ->
                        LiquidChip(
                            selected = themeConfig.accentSource == src,
                            onClick = { onThemeConfigChange(themeConfig.copy(accentSource = src, useDynamicAccent = src == "ARTWORK")) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = themeConfig.accentSource == "PRESET",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val swatches = listOf(
                            0xFF6366F1 to "Indigo",
                            0xFF06B6D4 to "Cyan",
                            0xFF10B981 to "Emerald",
                            0xFFF59E0B to "Amber",
                            0xFFF43F5E to "Rose",
                            0xFF8B5CF6 to "Violet"
                        )
                        swatches.forEach { (argb, _) ->
                            val isSel = themeConfig.fixedAccentColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(argb), CircleShape)
                                    .border(
                                        width = if (isSel) 3.dp else 1.dp,
                                        color = if (isSel) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onThemeConfigChange(themeConfig.copy(fixedAccentColorArgb = argb)) }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 4. Glass Appearance Sliders (Grain Slider Completely Removed)
                Text("GLASS APPEARANCE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(6.dp))

                // Opacity
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Glass Opacity", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${(themeConfig.glassOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.glassOpacity,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(glassOpacity = it, isFullyTransparent = false)) },
                    valueRange = 0f..1f
                )

                // Blur Strength
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Blur Strength", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${(themeConfig.blurStrength * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.blurStrength,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(blurStrength = it)) },
                    valueRange = 0f..1f
                )

                // Rim Highlight
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Edge Highlight Strength", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${(themeConfig.edgeHighlightStrength * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.edgeHighlightStrength,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(edgeHighlightStrength = it)) },
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fully Transparent Glass", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Removes fill and blur; keeps edge highlight", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.isFullyTransparent,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(isFullyTransparent = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep Text Readable", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Adaptive background contrast scrim for text", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.keepTextReadable,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(keepTextReadable = it)) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 5. Lens Dock Controls
                Text("LENS DOCK & REFRACTION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Lens Refraction Strength", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("%.2fx".format(themeConfig.lensRefractionStrength), style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.lensRefractionStrength,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(lensRefractionStrength = it)) },
                    valueRange = 0.20f..1.50f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Lens Rim Specular Intensity", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${(themeConfig.lensRimSpecular * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.lensRimSpecular,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(lensRimSpecular = it)) },
                    valueRange = 0f..1f
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 5b. Background Ambience & Shader Styles
                Text("BACKGROUND AMBIENCE & STYLES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(6.dp))

                Text("Shader Background Style", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val styles = listOf(
                        "LIQUID_BLOBS" to "Liquid Blobs",
                        "AURORA" to "Aurora Waves",
                        "MESH_GRADIENT" to "Mesh Gradient",
                        "ALBUM_ART_BLUR" to "Album Art Blur",
                        "SLOW_CAUSTICS" to "Slow Caustics",
                        "STATIC" to "Static Minimal"
                    )
                    styles.forEach { (styleKey, styleName) ->
                        LiquidChip(
                            selected = themeConfig.backgroundAnimationStyle == styleKey,
                            onClick = { onThemeConfigChange(themeConfig.copy(backgroundAnimationStyle = styleKey)) },
                            label = { Text(styleName, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Animation Speed", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("%.1fx".format(themeConfig.backgroundAnimationSpeed), style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.backgroundAnimationSpeed,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(backgroundAnimationSpeed = it)) },
                    valueRange = 0.2f..3.0f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Animation Intensity", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${(themeConfig.backgroundAnimationIntensity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.backgroundAnimationIntensity,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(backgroundAnimationIntensity = it)) },
                    valueRange = 0.1f..1.0f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Background Color Source", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ARTWORK" to "Artwork", "PALETTE" to "Theme Palette", "CUSTOM" to "Custom Swatch").forEach { (src, label) ->
                        LiquidChip(
                            selected = themeConfig.backgroundColorSource == src,
                            onClick = { onThemeConfigChange(themeConfig.copy(backgroundColorSource = src)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Animate Only While Playing", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Pauses fluid background shader when music is paused to save battery", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.animateOnlyWhilePlaying,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(animateOnlyWhilePlaying = it)) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 6. Typography & Shapes
                Text("TYPOGRAPHY & SHAPES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(6.dp))

                Text("App Font Family", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Text("Bundled clean typefaces applied across the entire app", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("SYSTEM" to "System Default", "INTER" to "Inter", "OUTFIT" to "Outfit", "JETBRAINS_MONO" to "JetBrains Mono").forEach { (f, label) ->
                        LiquidChip(
                            selected = themeConfig.fontFamily == f,
                            onClick = { onThemeConfigChange(themeConfig.copy(fontFamily = f)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Corner Roundness", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("SHARP" to "Sharp (8dp)", "ROUNDED" to "Rounded (16dp)", "PILL" to "Pill (24dp)").forEach { (r, label) ->
                        LiquidChip(
                            selected = themeConfig.cornerRoundness == r,
                            onClick = {
                                onThemeConfigChange(
                                    themeConfig.copy(
                                        cornerRoundness = r,
                                        cornerRadiusDp = when (r) { "SHARP" -> 8f; "PILL" -> 24f; else -> 16f }
                                    )
                                )
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Spacing Density", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("COMPACT" to "Compact (10dp)", "COMFORTABLE" to "Comfortable (16dp)", "ROOMY" to "Roomy (22dp)").forEach { (d, label) ->
                        LiquidChip(
                            selected = themeConfig.spacingDensity == d,
                            onClick = { onThemeConfigChange(themeConfig.copy(spacingDensity = d)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onOpenMotionPlayground,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Animation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Interactive Motion & Spring Playground")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 7. Component Visibility Toggles
                Text("COMPONENT VISIBILITY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dock Search Button", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Show circular search icon next to the lens dock", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.showDockSearchButton,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(showDockSearchButton = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mini Player Progress Bar", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Show bottom playback progress strip on mini player", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.showMiniPlayerWave,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(showMiniPlayerWave = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audio Quality Format Badge", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Show bit-perfect / resampled sink badge in Now Playing", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.formatBadgeVisible,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(formatBadgeVisible = it)) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Now Playing Hero & Artwork Controls
                Text("NOW PLAYING HERO & ARTWORK", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(6.dp))

                // Artwork Zoom & Overscan
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Artwork Zoom & Overscan", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("%.2fx".format(themeConfig.artZoomScale), style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.artZoomScale,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(artZoomScale = it)) },
                    valueRange = 1.00f..1.15f
                )

                // Video / Artwork Vertical Alignment
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Hero Vertical Alignment", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${themeConfig.artVerticalOffsetDp} dp", style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.artVerticalOffsetDp.toFloat(),
                    onValueChange = { onThemeConfigChange(themeConfig.copy(artVerticalOffsetDp = it.roundToInt())) },
                    valueRange = -40f..40f
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 8. Theme Export & Import
                Text("THEME SHARING & BACKUP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                val file = NodeThemeSerializer.exportToFile(context, themeConfig.copy(glassTexture = 0.0f, noiseGrainAlpha = 0.0f))
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Node Theme"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to share theme: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.25f), contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export (.nodetheme)")
                    }

                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Theme")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = onApplySafeGraphicsMode,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFF38BDF8))
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Safe Graphics Mode")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onReportProblem,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B))
                ) {
                    Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report a Graphics Problem")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onThemeConfigChange(GlassThemeConfig(glassTexture = 0.0f, noiseGrainAlpha = 0.0f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f))
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset All UI & Glass to Defaults")
                }
            }
        }
    }
}
