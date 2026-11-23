package com.nodeplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.model.WidgetAccentMode
import com.nodeplayer.model.WidgetSettings
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.music.widget.WidgetPreferences
import com.nodeplayer.music.widget.WidgetUpdateManager
import com.nodeplayer.ui.components.LiquidChip
import com.nodeplayer.ui.components.LiquidRadioButton
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.LiquidToggle

enum class WidgetVariant(val label: String, val desc: String) {
    CARD_4X2("Card 4x2", "Standard full player with seekbar and scrubber"),
    LYRICS_4X3("Lyrics 4x3", "Synced karaoke lyrics with playback controls"),
    SLIM_4X1("Slim 4x1", "Ultra-compact minimal banner"),
    SQUARE_2X2("Square 2x2", "Cover art focus with play button")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    var settings by remember { mutableStateOf(WidgetPreferences.getSettings(context)) }
    var selectedVariant by remember { mutableStateOf(WidgetVariant.CARD_4X2) }

    fun updateSettings(newSettings: WidgetSettings) {
        settings = newSettings
        WidgetPreferences.saveSettings(context, newSettings)
        WidgetUpdateManager.updateAllWidgets(context)
    }

    val displayTitle = currentTrack?.title ?: "Syndrom"
    val displayArtist = currentTrack?.artist ?: "Viktor Sheen"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Widgets & Lock Screen",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            updateSettings(WidgetSettings())
                        }
                    ) {
                        Text("Reset", color = accent, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(shape = RoundedCornerShape(16.dp), tintAlpha = 0.45f)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Home screen widgets are static RemoteViews bitmaps rendered on track events to preserve battery. Liquid-glass shimmer is baked in.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(shape = RoundedCornerShape(20.dp), tintAlpha = 0.35f)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WidgetVariant.values().forEach { variant ->
                    val isSelected = selectedVariant == variant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.30f) else Color.Transparent)
                            .clickable { selectedVariant = variant }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = variant.label.split(" ").first(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LIVE IN-APP PREVIEW",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.2.sp
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            WidgetLivePreviewCard(
                variant = selectedVariant,
                settings = settings,
                title = displayTitle,
                artist = displayArtist,
                isPlaying = isPlaying,
                accent = accent
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ELEMENT VISIBILITY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.2.sp
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(shape = RoundedCornerShape(20.dp), tintAlpha = 0.50f)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WidgetToggleRow(
                    icon = Icons.Rounded.Image,
                    label = "Album Artwork",
                    desc = "Show track cover art thumbnail",
                    checked = settings.showArt,
                    accent = accent,
                    onCheckedChange = { updateSettings(settings.copy(showArt = it)) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                WidgetToggleRow(
                    icon = Icons.Rounded.GraphicEq,
                    label = "Waveform Glyph",
                    desc = "Show waveform indicator badge",
                    checked = settings.showWaveform,
                    accent = accent,
                    onCheckedChange = { updateSettings(settings.copy(showWaveform = it)) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                WidgetToggleRow(
                    icon = Icons.Rounded.Title,
                    label = "Title & Artist",
                    desc = "Show song name and performer",
                    checked = settings.showTitleArtist,
                    accent = accent,
                    onCheckedChange = { updateSettings(settings.copy(showTitleArtist = it)) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                WidgetToggleRow(
                    icon = Icons.Rounded.LinearScale,
                    label = "Seekbar & Elapsed Times",
                    desc = "Show progress bar with tap zones",
                    checked = settings.showSeekbarTimes,
                    accent = accent,
                    onCheckedChange = { updateSettings(settings.copy(showSeekbarTimes = it)) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                WidgetToggleRow(
                    icon = Icons.Rounded.PlayCircle,
                    label = "Transport Controls",
                    desc = "Show Play, Pause, Previous, Next",
                    checked = settings.showTransport,
                    accent = accent,
                    onCheckedChange = { updateSettings(settings.copy(showTransport = it)) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                WidgetToggleRow(
                    icon = Icons.Rounded.Lyrics,
                    label = "Synced Lyrics Lines",
                    desc = "Show lyrics container on 4x3 card",
                    checked = settings.showLyrics,
                    accent = accent,
                    onCheckedChange = { updateSettings(settings.copy(showLyrics = it)) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "GLASS APPEARANCE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.2.sp
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(shape = RoundedCornerShape(20.dp), tintAlpha = 0.50f)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = "Accent Color Mode",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WidgetAccentMode.values().forEach { mode ->
                        val isModeSelected = settings.accentMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isModeSelected) accent.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { updateSettings(settings.copy(accentMode = mode)) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidRadioButton(
                                selected = isModeSelected,
                                onClick = { updateSettings(settings.copy(accentMode = mode)) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isModeSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                    fontWeight = if (isModeSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Column {
                    Text(
                        text = "Liquid Refraction Style",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val styles = listOf(
                            "LIQUID_OBSIDIAN" to "Obsidian Glass",
                            "FROST_PEARL" to "Frost Pearl",
                            "DYNAMIC_ART" to "Dynamic Art",
                            "PRISMATIC_CLEAR" to "Prismatic"
                        )
                        styles.forEach { (styleKey, styleLabel) ->
                            val isSel = settings.refractionStyle == styleKey
                            LiquidChip(
                                selected = isSel,
                                onClick = { updateSettings(settings.copy(refractionStyle = styleKey)) },
                                label = { Text(styleLabel, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Glass Opacity",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "${(settings.glassOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(color = accent, fontWeight = FontWeight.Bold)
                        )
                    }
                    LiquidSlider(
                        value = settings.glassOpacity,
                        onValueChange = { updateSettings(settings.copy(glassOpacity = it)) },
                        valueRange = 0.1f..1.0f
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Edge Specular Rim Highlight",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "${(settings.edgeHighlightStrength * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(color = accent, fontWeight = FontWeight.Bold)
                        )
                    }
                    LiquidSlider(
                        value = settings.edgeHighlightStrength,
                        onValueChange = { updateSettings(settings.copy(edgeHighlightStrength = it)) },
                        valueRange = 0.0f..1.0f
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Corner Radius",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "${settings.cornerRadiusDp.toInt()} dp",
                            style = MaterialTheme.typography.bodyMedium.copy(color = accent, fontWeight = FontWeight.Bold)
                        )
                    }
                    LiquidSlider(
                        value = settings.cornerRadiusDp,
                        onValueChange = { updateSettings(settings.copy(cornerRadiusDp = it)) },
                        valueRange = 12f..36f
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                WidgetToggleRow(
                    icon = Icons.Rounded.HighQuality,
                    label = "High-DPI Razor Rendering",
                    desc = "Renders widgets at 2x resolution for razor-sharp clarity on 1080p+ displays",
                    checked = settings.highDpi,
                    accent = accent,
                    onCheckedChange = { updateSettings(settings.copy(highDpi = it)) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                WidgetToggleRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    label = "Performance Mode",
                    desc = "Disable complex specular shaders for low-end devices",
                    checked = settings.isPerformanceMode,
                    accent = accent,
                    onCheckedChange = { updateSettings(settings.copy(isPerformanceMode = it)) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WidgetLivePreviewCard(
    variant: WidgetVariant,
    settings: WidgetSettings,
    title: String,
    artist: String,
    isPlaying: Boolean,
    accent: Color
) {
    val cornerShape = RoundedCornerShape(settings.cornerRadiusDp.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                when (variant) {
                    WidgetVariant.CARD_4X2 -> 200.dp
                    WidgetVariant.LYRICS_4X3 -> 260.dp
                    WidgetVariant.SLIM_4X1 -> 88.dp
                    WidgetVariant.SQUARE_2X2 -> 160.dp
                }
            )
            .glassSurface(
                shape = cornerShape,
                tintAlpha = settings.glassOpacity.coerceIn(0.1f, 0.95f)
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.90f * settings.edgeHighlightStrength),
                        accent.copy(alpha = 0.40f * settings.edgeHighlightStrength),
                        Color.White.copy(alpha = 0.45f * settings.edgeHighlightStrength)
                    )
                ),
                shape = cornerShape
            )
            .padding(14.dp)
    ) {
        when (variant) {
            WidgetVariant.CARD_4X2 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (settings.showArt) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF232533)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        if (settings.showTitleArtist) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.65f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        if (settings.showWaveform) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (settings.showSeekbarTimes) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.35f)
                                        .fillMaxHeight()
                                        .background(accent)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("0:48", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp))
                                Text("2:32", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp))
                            }
                        }
                    }

                    if (settings.showTransport) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(20.dp))
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.30f))
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
            WidgetVariant.LYRICS_4X3 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (settings.showArt) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF232533)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        if (settings.showTitleArtist) {
                            Column {
                                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                Text(artist, style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)))
                            }
                        }
                    }

                    if (settings.showLyrics) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Synced lyrics preview line 1", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.45f)))
                            Text("Active karaoke lyric line highlighted", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            Text("Upcoming lyric line preview", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.45f)))
                        }
                    }

                    if (settings.showTransport) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            WidgetVariant.SLIM_4X1 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (settings.showArt) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF232533)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    if (settings.showTitleArtist) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White), maxLines = 1)
                            Text(artist, style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)), maxLines = 1)
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (settings.showTransport) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            WidgetVariant.SQUARE_2X2 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF232533)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (settings.showArt) {
                            Icon(imageVector = Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        if (settings.showTransport) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 6.dp, y = 6.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (settings.showTitleArtist) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White), maxLines = 1)
                            Text(artist, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.65f)), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    desc: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.5.sp
                )
            )
        }
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
