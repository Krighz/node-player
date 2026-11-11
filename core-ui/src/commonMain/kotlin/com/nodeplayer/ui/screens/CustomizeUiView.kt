package com.nodeplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.nodeplayer.model.*
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.*
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LiquidColors

@Composable
fun CustomizeUiView(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()
    val controlsConfig = themeConfig.controlsConfig

    fun updateControls(newControls: GlassControlsConfig) {
        onThemeConfigChange(
            themeConfig.copy(
                controlsConfig = newControls,
                controlsGlassEnabled = newControls.masterEnabled
            )
        )
    }

    // Dialog state
    var pendingPresetChange by remember { mutableStateOf<GlassControlsPreset?>(null) }
    var showResetAllConfirm by remember { mutableStateOf(false) }

    // Accordions expansion state
    var expandedDock by remember { mutableStateOf(false) }
    var expandedHome by remember { mutableStateOf(false) }
    var expandedLyrics by remember { mutableStateOf(false) }
    var expandedMotion by remember { mutableStateOf(false) }
    var expandedAccordions by remember { mutableStateOf(setOf<GlassComponentType>()) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isDesktopSplit = maxWidth > 840.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ScreenHeader()
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Pinned Interactive Live Preview Card (D-03)
            LiveInteractiveGlassPreviewCard(
                themeConfig = themeConfig,
                accent = accent
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Scrollable Accordion Sections
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Master Glass Shaders & Presets Card
                MasterControlsCard(
                    controlsConfig = controlsConfig,
                    onUpdateControls = ::updateControls,
                    onRequestPresetChange = { preset ->
                        val newConfig = when (preset) {
                            GlassControlsPreset.FULL -> com.nodeplayer.model.GlassControlsConfig.PRESET_FULL
                            GlassControlsPreset.BALANCED -> com.nodeplayer.model.GlassControlsConfig.PRESET_BALANCED
                            GlassControlsPreset.LIGHT -> com.nodeplayer.model.GlassControlsConfig.PRESET_LIGHT
                        }
                        updateControls(newConfig)
                    },
                    onResetAll = { showResetAllConfirm = true },
                    onExport = {},
                    onImport = {},
                    accent = accent
                )

                // Accordion 1: Dock & Mini Player Settings (CONF-02)
                CustomizationAccordion(
                    title = "Dock & Navigation Shell",
                    subtitle = "Tab ordering, search button, collapse-on-scroll sensitivity",
                    isExpanded = expandedDock,
                    onToggle = { expandedDock = !expandedDock },
                    accent = accent
                ) {
                    DockSettingsSection(
                        dockConfig = themeConfig.dockConfig,
                        onUpdate = { onThemeConfigChange(themeConfig.copy(dockConfig = it)) },
                        accent = accent
                    )
                }

                // Accordion 2: Home Feed & Library Shelves (CONF-03)
                CustomizationAccordion(
                    title = "Home Feed & Library Shelves",
                    subtitle = "Shelf visibility, ordering, 16:9 cards, content region",
                    isExpanded = expandedHome,
                    onToggle = { expandedHome = !expandedHome },
                    accent = accent
                ) {
                    HomeShelvesSettingsSection(
                        homeConfig = themeConfig.homeLayoutConfig,
                        onUpdate = { onThemeConfigChange(themeConfig.copy(homeLayoutConfig = it)) },
                        accent = accent
                    )
                }

                // Accordion 3: Full-Screen Lyrics & Display (CONF-04)
                CustomizationAccordion(
                    title = "Full-Screen Synced Lyrics",
                    subtitle = "Typography font size, blur strength, 5s auto-hide delay, keep screen on",
                    isExpanded = expandedLyrics,
                    onToggle = { expandedLyrics = !expandedLyrics },
                    accent = accent
                ) {
                    LyricsSettingsSection(
                        lyricsConfig = themeConfig.lyricsViewConfig,
                        onUpdate = { onThemeConfigChange(themeConfig.copy(lyricsViewConfig = it)) },
                        accent = accent
                    )
                }

                // Accordion 4: Motion & Spring Physics
                CustomizationAccordion(
                    title = "Spring Dynamics & Motion",
                    subtitle = "Spring stiffness, bounciness, and gesture scale response",
                    isExpanded = expandedMotion,
                    onToggle = { expandedMotion = !expandedMotion },
                    accent = accent
                ) {
                    MotionSettingsSection(
                        themeConfig = themeConfig,
                        onThemeConfigChange = onThemeConfigChange,
                        accent = accent
                    )
                }

                // Atmosphere / Background Shader Section
                PaletteAndBackgroundSection(
                    themeConfig = themeConfig,
                    onThemeConfigChange = onThemeConfigChange,
                    accent = accent
                )

                Spacer(modifier = Modifier.height(LocalBottomBarPadding.current + 24.dp))
            }
        }
    }
}

@Composable
private fun ScreenHeader() {
    Column {
        Text(
            text = "Appearance & Layout Customization",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = "Fine-tune liquid glass shaders, dock tabs, shelves, lyrics, and spring physics",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
        )
    }
}

/**
 * Pinned Interactive Live Preview Card (D-03).
 * Shows miniature gliding puck, sample text, and liquid slider updating in real time.
 */
@Composable
private fun LiveInteractiveGlassPreviewCard(
    themeConfig: GlassThemeConfig,
    accent: Color
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var previewSliderValue by remember { mutableFloatStateOf(0.65f) }
    val shape = remember { ContinuousRoundedRectangle(20.dp) }

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE INTERACTIVE PREVIEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Refraction: ${themeConfig.glassRefractionHeightDp.toInt()}dp",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.60f)
                )
            }

            // Preview Surface Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini Gliding Puck Sample
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 36.dp)
                            .clip(ContinuousCapsule())
                            .background(accent.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LiquidColors.SurfaceDark)
                    }

                    // Sample Typography
                    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            text = "Sample Lyrics Text",
                            fontSize = (themeConfig.lyricsViewConfig.fontSizeSp * 0.55f).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Apple Music Glass Sweep",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.60f)
                        )
                    }

                    // Mini Liquid Slider
                    LiquidGlassSlider(
                        value = previewSliderValue,
                        onValueChange = { previewSliderValue = it },
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomizationAccordion(
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f))
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isExpanded) accent else Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DockSettingsSection(
    dockConfig: DockConfig,
    onUpdate: (DockConfig) -> Unit,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Search Button", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Separate circular Search glass button on the dock", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            }
            LiquidSwitch(
                checked = dockConfig.showSearchButton,
                onCheckedChange = { onUpdate(dockConfig.copy(showSearchButton = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-hide Mini Player", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Collapse floating pill deck during rapid gestures", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            }
            LiquidSwitch(
                checked = dockConfig.autoHideMiniPlayer,
                onCheckedChange = { onUpdate(dockConfig.copy(autoHideMiniPlayer = it)) }
            )
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Collapse Sensitivity", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${dockConfig.collapseSensitivity}x", fontSize = 13.sp, color = accent)
            }
            LiquidGlassSlider(
                value = (dockConfig.collapseSensitivity - 0.5f) / 1.5f,
                onValueChange = { frac ->
                    val sens = (0.5f + frac * 1.5f)
                    onUpdate(dockConfig.copy(collapseSensitivity = (sens * 10).toInt() / 10f))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HomeShelvesSettingsSection(
    homeConfig: HomeLayoutConfig,
    onUpdate: (HomeLayoutConfig) -> Unit,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Wide 16:9 Video Cards", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Use widescreen aspect ratio for new releases", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            }
            LiquidSwitch(
                checked = homeConfig.cardAspectWide,
                onCheckedChange = { onUpdate(homeConfig.copy(cardAspectWide = it)) }
            )
        }

        Text("Active Home Shelves", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)

        val allShelves = listOf(
            "QUICK_PICKS" to "Quick Picks",
            "NEW_RELEASES" to "New Releases & Videos",
            "TRENDING" to "Trending in Region",
            "PLAYLISTS" to "Curated Playlists",
            "FOLLOWED_ARTISTS" to "From Artists You Follow"
        )

        allShelves.forEach { (shelfKey, shelfTitle) ->
            val isVisible = !homeConfig.hiddenShelves.contains(shelfKey)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val newHidden = if (isVisible) {
                            homeConfig.hiddenShelves + shelfKey
                        } else {
                            homeConfig.hiddenShelves - shelfKey
                        }
                        onUpdate(homeConfig.copy(hiddenShelves = newHidden))
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(shelfTitle, fontSize = 13.sp, color = if (isVisible) Color.White else Color.White.copy(alpha = 0.4f))
                LiquidCheckbox(
                    checked = isVisible,
                    onCheckedChange = { checked ->
                        val newHidden = if (!checked) {
                            homeConfig.hiddenShelves + shelfKey
                        } else {
                            homeConfig.hiddenShelves - shelfKey
                        }
                        onUpdate(homeConfig.copy(hiddenShelves = newHidden))
                    }
                )
            }
        }
    }
}

@Composable
private fun LyricsSettingsSection(
    lyricsConfig: LyricsViewConfig,
    onUpdate: (LyricsViewConfig) -> Unit,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep Screen On", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Prevent display timeout during full-screen lyrics", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            }
            LiquidSwitch(
                checked = lyricsConfig.keepScreenOn,
                onCheckedChange = { onUpdate(lyricsConfig.copy(keepScreenOn = it)) }
            )
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Lyrics Font Size", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${lyricsConfig.fontSizeSp.toInt()} sp", fontSize = 13.sp, color = accent)
            }
            LiquidGlassSlider(
                value = (lyricsConfig.fontSizeSp - 20f) / 16f,
                onValueChange = { frac ->
                    val size = 20f + frac * 16f
                    onUpdate(lyricsConfig.copy(fontSizeSp = size.toInt().toFloat()))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Controls Auto-Hide Delay", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${lyricsConfig.autoHideDelaySec} seconds", fontSize = 13.sp, color = accent)
            }
            LiquidGlassSlider(
                value = (lyricsConfig.autoHideDelaySec - 3) / 12f,
                onValueChange = { frac ->
                    val sec = (3 + frac * 12f).toInt()
                    onUpdate(lyricsConfig.copy(autoHideDelaySec = sec))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MotionSettingsSection(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Spring Bounciness", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${(themeConfig.springBounciness * 100).toInt()}%", fontSize = 13.sp, color = accent)
            }
            LiquidGlassSlider(
                value = (themeConfig.springBounciness - 0.5f) / 0.5f,
                onValueChange = { frac ->
                    val bounciness = 0.5f + frac * 0.5f
                    onThemeConfigChange(themeConfig.copy(springBounciness = bounciness))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Spring Stiffness", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${themeConfig.springStiffness.toInt()}", fontSize = 13.sp, color = accent)
            }
            LiquidGlassSlider(
                value = (themeConfig.springStiffness - 150f) / 850f,
                onValueChange = { frac ->
                    val stiffness = 150f + frac * 850f
                    onThemeConfigChange(themeConfig.copy(springStiffness = stiffness))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MasterControlsCard(
    controlsConfig: GlassControlsConfig,
    onUpdateControls: (GlassControlsConfig) -> Unit,
    onRequestPresetChange: (GlassControlsPreset) -> Unit,
    onResetAll: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    accent: Color
) {
    val global = controlsConfig.global

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Master Glass Shaders",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Real-time backdrop refraction and blur",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                    )
                }

                LiquidSwitch(
                    checked = controlsConfig.masterEnabled,
                    onCheckedChange = { onUpdateControls(controlsConfig.copy(masterEnabled = it)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    GlassControlsPreset.LIGHT to "Subtle",
                    GlassControlsPreset.BALANCED to "Normal",
                    GlassControlsPreset.FULL to "Strong"
                ).forEach { (preset, label) ->
                    val isSelected = controlsConfig.preset == preset
                    LiquidButton(
                        onClick = { onRequestPresetChange(preset) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) accent else Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Blur Radius Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Blur Radius", fontSize = 13.sp)
                    Text("${global.blurDp.toInt()} dp", fontSize = 13.sp, color = accent)
                }
                LiquidGlassSlider(
                    value = (global.blurDp - 8f) / 56f,
                    onValueChange = { frac ->
                        val blur = 8f + frac * 56f
                        onUpdateControls(controlsConfig.copy(global = global.copy(blurDp = blur)))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Lens Refraction Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lens Refraction", fontSize = 13.sp)
                    Text("${global.lensAmountDp.toInt()} dp", fontSize = 13.sp, color = accent)
                }
                LiquidGlassSlider(
                    value = global.lensAmountDp / 32f,
                    onValueChange = { frac ->
                        val lens = frac * 32f
                        onUpdateControls(controlsConfig.copy(global = global.copy(lensAmountDp = lens)))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PaletteAndBackgroundSection(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    accent: Color
) {
    val shaders = listOf(
        "LIQUID_BLOBS" to "Liquid Blobs (Recommended)",
        "AURORA" to "Aurora Waves",
        "SLOW_CAUSTICS" to "Slow Caustics",
        "STATIC" to "Static OLED Minimal"
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Atmosphere Shader Style",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            shaders.forEach { (styleKey, styleName) ->
                val isSelected = themeConfig.backgroundAnimationStyle == styleKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onThemeConfigChange(themeConfig.copy(backgroundAnimationStyle = styleKey)) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = styleName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) accent else Color.White,
                            fontSize = 12.sp
                        )
                    )
                    LiquidRadioButton(
                        selected = isSelected,
                        onClick = { onThemeConfigChange(themeConfig.copy(backgroundAnimationStyle = styleKey)) }
                    )
                }
            }
        }
    }
}
