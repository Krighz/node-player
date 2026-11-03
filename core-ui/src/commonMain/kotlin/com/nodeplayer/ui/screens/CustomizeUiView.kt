package com.nodeplayer.ui.screens

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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.theme.LocalAccentColor

@Composable
fun CustomizeUiView(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()

    val palettes = listOf(
        Triple("Cyberpunk", 0xFF8B5CF6L, Color(0xFF8B5CF6)),
        Triple("Sapphire", 0xFF0284C7L, Color(0xFF0284C7)),
        Triple("Emerald", 0xFF10B981L, Color(0xFF10B981)),
        Triple("Sunset", 0xFFF97316L, Color(0xFFF97316)),
        Triple("Rose", 0xFFF43F5EL, Color(0xFFF43F5E)),
        Triple("Monolith", 0xFF94A3B8L, Color(0xFF94A3B8)),
        Triple("Amber", 0xFFF59E0BL, Color(0xFFF59E0B)),
        Triple("Amethyst", 0xFF6366F1L, Color(0xFF6366F1))
    )

    val shaders = listOf(
        "LIQUID_BLOBS" to "Liquid Blobs (Organic Chromatic Metaballs)",
        "AURORA" to "Aurora Waves (Harmonic Curtains)",
        "MESH_GRADIENT" to "Mesh Gradient (4-Quadrant Energy Fields)",
        "ALBUM_ART_BLUR" to "Album Art Blur (Dynamic Color Bleed)",
        "SLOW_CAUSTICS" to "Slow Caustics (Refractive Optical Filaments)",
        "STATIC" to "Frozen Glass (Zero CPU Overhead)"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Customize UI & Visuals",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = "Real-time liquid glass shaders, physics tokens, and optical refraction",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Color Palette Presets
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Accent Color Palette",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    palettes.forEach { (name, colorArgb, color) ->
                        val isSelected = (themeConfig.fixedAccentColorArgb and 0xFFFFFFFFL) == (colorArgb and 0xFFFFFFFFL)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                onThemeConfigChange(
                                    themeConfig.copy(
                                        fixedAccentColorArgb = colorArgb,
                                        backgroundColorSource = "CUSTOM"
                                    )
                                )
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(name, fontSize = 10.sp, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Liquid Glass Shader Animations
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Background Animation Style",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                shaders.forEach { (styleKey, styleName) ->
                    val isSelected = themeConfig.backgroundAnimationStyle == styleKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onThemeConfigChange(themeConfig.copy(backgroundAnimationStyle = styleKey))
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = styleName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) accent else Color.White
                            )
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onThemeConfigChange(themeConfig.copy(backgroundAnimationStyle = styleKey)) },
                            colors = RadioButtonDefaults.colors(selectedColor = accent)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Animation Controls (Speed, Intensity, Play-Gating)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Motion & Shader Dynamics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Animation Speed", fontSize = 13.sp)
                    Text("%.1fx".format(themeConfig.backgroundAnimationSpeed), fontSize = 13.sp, color = accent)
                }
                Slider(
                    value = themeConfig.backgroundAnimationSpeed,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(backgroundAnimationSpeed = it)) },
                    valueRange = 0.2f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Animation Intensity", fontSize = 13.sp)
                    Text("${(themeConfig.backgroundAnimationIntensity * 100).toInt()}%", fontSize = 13.sp, color = accent)
                }
                Slider(
                    value = themeConfig.backgroundAnimationIntensity,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(backgroundAnimationIntensity = it)) },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Animate Only While Playing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Freezes backdrop animations when audio is paused to save energy", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = themeConfig.animateOnlyWhilePlaying,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(animateOnlyWhilePlaying = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Glass Surface & Refraction Optics
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Glass Surface & Optics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Glass Opacity (Translucency)", fontSize = 13.sp)
                    Text("${(themeConfig.glassOpacity * 100).toInt()}%", fontSize = 13.sp, color = accent)
                }
                Slider(
                    value = themeConfig.glassOpacity,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(glassOpacity = it)) },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Rim Specular Highlight", fontSize = 13.sp)
                    Text("${(themeConfig.edgeHighlightStrength * 100).toInt()}%", fontSize = 13.sp, color = accent)
                }
                Slider(
                    value = themeConfig.edgeHighlightStrength,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(edgeHighlightStrength = it)) },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Refraction Lens Strength", fontSize = 13.sp)
                    Text("%.2f".format(themeConfig.lensRefractionStrength), fontSize = 13.sp, color = accent)
                }
                Slider(
                    value = themeConfig.lensRefractionStrength,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(lensRefractionStrength = it)) },
                    valueRange = 0.2f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Glass Optical Texture / Grain", fontSize = 13.sp)
                    Text("${(themeConfig.glassTexture * 100).toInt()}%", fontSize = 13.sp, color = accent)
                }
                Slider(
                    value = themeConfig.glassTexture,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(glassTexture = it)) },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full Transparency", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Makes glass backgrounds completely clear, keeping only crystal rim outlines", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = themeConfig.isFullyTransparent,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(isFullyTransparent = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Display & Performance Options
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Display & Performance",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OLED Pure Black", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Sets background pixels to #000000 for infinite contrast", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = themeConfig.isOledBlack,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(isOledBlack = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Performance Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Freezes continuous GPU animations for minimum battery/CPU usage", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = themeConfig.isPerformanceMode,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(isPerformanceMode = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
