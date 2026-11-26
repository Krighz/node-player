package com.nodeplayer.ui.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.LyricsBaseColor
import com.nodeplayer.model.LyricsDisplayMode
import com.nodeplayer.model.LyricsFontChoice
import com.nodeplayer.model.LyricsGlowPalette
import com.nodeplayer.model.LyricsProviderChoice
import com.nodeplayer.model.LyricsSettings
import com.nodeplayer.model.LyricsStyle
import com.nodeplayer.ui.lyrics.LocalLyricsFontResolver
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlin.math.roundToInt

private enum class LyricsSheetSubPage {
    MAIN,
    CUSTOMIZE_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricsSettingsBottomSheet(
    settings: LyricsSettings,
    onUpdateSettings: (LyricsSettings) -> Unit,
    onDismissRequest: () -> Unit,
    onShuffleLayout: () -> Unit = {},
    onShare: (() -> Unit)? = null,
    onOpenOffsetControls: (() -> Unit)? = null,
    onSearchAgain: (() -> Unit)? = null,
    currentLyrics: com.nodeplayer.model.Lyrics? = null,
    onToggleSelectionMode: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = LocalAccentColor.current
    var currentSubPage by remember { mutableStateOf(LyricsSheetSubPage.MAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xF0121217),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxSheetHeight = maxHeight * 0.52f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
            ) {
                AnimatedContent(
                    targetState = currentSubPage,
                    transitionSpec = {
                        if (targetState == LyricsSheetSubPage.CUSTOMIZE_SETTINGS) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "LyricsSheetPageTransition"
                ) { page ->
                    when (page) {
                        LyricsSheetSubPage.MAIN -> {
                            val mainScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(mainScrollState)
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                // Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Lyrics & Style",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    IconButton(onClick = onDismissRequest) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Close",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 1. Top Section: Selectable LRC / Lyrics Providers with Status & Action Icons
                                Text(
                                    text = "LYRICS LOCATION & PROVIDER",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                                Text(
                                    text = "Select source location to search, view status, or connect",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.40f),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                )

                                val providerScrollState = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(providerScrollState),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LyricsProviderChoice.entries.forEach { provider ->
                                        val isSelected = settings.preferredProvider == provider
                                        val activeSource = currentLyrics?.source?.lowercase() ?: ""
                                        val isFoundFromSource = currentLyrics != null && currentLyrics.lines.isNotEmpty() && when (provider) {
                                            LyricsProviderChoice.AUTO -> activeSource.isNotEmpty()
                                            LyricsProviderChoice.LRC_RED -> activeSource.contains("lrcred") || activeSource.contains("lrc.red")
                                            LyricsProviderChoice.BINI -> activeSource.contains("bini")
                                            LyricsProviderChoice.MUSIXMATCH -> activeSource.contains("musixmatch")
                                            LyricsProviderChoice.UNISON -> activeSource.contains("unison")
                                            LyricsProviderChoice.LRCLIB -> activeSource.contains("lrclib")
                                            LyricsProviderChoice.BETTER_LYRICS -> activeSource.contains("betterlyrics")
                                            LyricsProviderChoice.LYRICS_PLUS -> activeSource.contains("lyricsplus")
                                            LyricsProviderChoice.KUGOU -> activeSource.contains("kugou")
                                            LyricsProviderChoice.LOCAL -> activeSource.contains("local") || activeSource.contains("sidecar") || activeSource.contains("embedded")
                                        }
                                        val isConnected = isSelected || (settings.preferredProvider == LyricsProviderChoice.AUTO && isFoundFromSource)

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isConnected) Color.White.copy(alpha = 0.20f)
                                                    else if (isFoundFromSource) Color.White.copy(alpha = 0.12f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    width = if (isConnected) 1.5.dp else 1.dp,
                                                    color = if (isConnected) Color.White
                                                            else if (isFoundFromSource) accent.copy(alpha = 0.6f)
                                                            else Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .clickable {
                                                    val updated = settings.copy(preferredProvider = provider)
                                                    onUpdateSettings(updated)
                                                    onSearchAgain?.invoke()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = provider.displayName,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isConnected) FontWeight.Bold else FontWeight.SemiBold,
                                                        color = if (isConnected) Color.White else Color.White.copy(alpha = 0.88f)
                                                    )
                                                    Text(
                                                        text = if (provider.isSyllableCapable) "• Syllable" else "• Line",
                                                        fontSize = 10.5.sp,
                                                        color = if (isConnected) accent else Color.White.copy(alpha = 0.45f)
                                                    )
                                                }

                                                // 3 Status Icons: Click to Search, Lyrics Found, and Connected (OK)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            if (isConnected) accent.copy(alpha = 0.25f)
                                                            else if (isFoundFromSource) Color(0xFF22C55E).copy(alpha = 0.18f)
                                                            else Color.White.copy(alpha = 0.08f)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    when {
                                                        isConnected -> {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Check,
                                                                contentDescription = "Connected OK",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = "Connected • OK",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                        isFoundFromSource -> {
                                                            Icon(
                                                                imageVector = Icons.Rounded.MusicNote,
                                                                contentDescription = "Lyrics Found",
                                                                tint = Color(0xFF4ADE80),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = "Lyrics Found",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = Color(0xFF4ADE80)
                                                            )
                                                        }
                                                        else -> {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Search,
                                                                contentDescription = "Click to Search",
                                                                tint = Color.White.copy(alpha = 0.60f),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = "Click to search",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = Color.White.copy(alpha = 0.60f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 2. Lyrics Style Segmented Selector Bar
                                Text(
                                    text = "LYRICS STYLE",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.07f))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isKinetic = settings.style == LyricsStyle.CINEMATIC_KINETIC
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isKinetic) Color.White else Color.Transparent)
                                            .clickable {
                                                onUpdateSettings(settings.copy(style = LyricsStyle.CINEMATIC_KINETIC))
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Cinematic Kinetic",
                                            fontSize = 13.sp,
                                            fontWeight = if (isKinetic) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isKinetic) Color.Black else Color.White.copy(alpha = 0.75f)
                                        )
                                    }

                                    val isClassic = settings.style == LyricsStyle.CLASSIC_FULLSCREEN
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isClassic) Color.White else Color.Transparent)
                                            .clickable {
                                                onUpdateSettings(settings.copy(style = LyricsStyle.CLASSIC_FULLSCREEN))
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Classic Fullscreen",
                                            fontSize = 13.sp,
                                            fontWeight = if (isClassic) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isClassic) Color.Black else Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Album Art Shape Selector (Landscape)
                                Text(
                                    text = "ALBUM ART SHAPE (LANDSCAPE)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.07f))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    com.nodeplayer.model.AlbumArtShape.entries.forEach { shape ->
                                        val isSelected = settings.albumArtShape == shape
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) Color.White else Color.Transparent)
                                                .clickable {
                                                    onUpdateSettings(settings.copy(albumArtShape = shape))
                                                }
                                                .padding(vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                when (shape) {
                                                    com.nodeplayer.model.AlbumArtShape.SQUIRCLE -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(RoundedCornerShape(3.dp))
                                                                .background(if (isSelected) Color.Black else Color.White)
                                                        )
                                                    }
                                                    com.nodeplayer.model.AlbumArtShape.CIRCLE -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isSelected) Color.Black else Color.White)
                                                        )
                                                    }
                                                    com.nodeplayer.model.AlbumArtShape.HEART -> {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Favorite,
                                                            contentDescription = null,
                                                            tint = if (isSelected) Color.Black else Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = shape.displayName,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Status Bar Immersive Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.07f))
                                        .clickable {
                                            onUpdateSettings(settings.copy(hideStatusBar = !settings.hideStatusBar))
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Hide Status Bar",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Edge-to-edge immersive full-screen display",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.55f)
                                        )
                                    }
                                    Switch(
                                        checked = settings.hideStatusBar,
                                        onCheckedChange = { checked ->
                                            onUpdateSettings(settings.copy(hideStatusBar = checked))
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = accent,
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // 3. Lyrics Style & Settings Navigation Item (Opens Customization Sub-Page)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                        .clickable {
                                            currentSubPage = LyricsSheetSubPage.CUSTOMIZE_SETTINGS
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Tune,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Lyrics Style & Customization",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = if (settings.style == LyricsStyle.CINEMATIC_KINETIC)
                                                        "Glow palette, bounce, glide & zoom"
                                                    else
                                                        "Viewport zoom & line spacing",
                                                    fontSize = 11.5.sp,
                                                    color = Color.White.copy(alpha = 0.60f)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = "Open Settings",
                                            tint = Color.White.copy(alpha = 0.65f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                                Spacer(modifier = Modifier.height(6.dp))

                                // 4. Quick Action Menu Items
                                if (onOpenOffsetControls != null) {
                                    ActionMenuItem(
                                        icon = Icons.Rounded.Timer,
                                        label = "Adjust Sync Offset (± ms)",
                                        onClick = {
                                            onDismissRequest()
                                            onOpenOffsetControls()
                                        }
                                    )
                                }

                                if (onSearchAgain != null) {
                                    ActionMenuItem(
                                        icon = Icons.Rounded.Refresh,
                                        label = "Search & Refetch Lyrics",
                                        onClick = {
                                            onDismissRequest()
                                            onSearchAgain()
                                        }
                                    )
                                }

                                if (onToggleSelectionMode != null) {
                                    ActionMenuItem(
                                        icon = Icons.Rounded.MusicNote,
                                        label = "Select Lyrics Lines (Cards & Sharing)",
                                        onClick = {
                                            onDismissRequest()
                                            onToggleSelectionMode()
                                        }
                                    )
                                }

                                if (onShare != null) {
                                    ActionMenuItem(
                                        icon = Icons.Rounded.Share,
                                        label = "Share Lyrics Card",
                                        onClick = {
                                            onDismissRequest()
                                            onShare()
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        LyricsSheetSubPage.CUSTOMIZE_SETTINGS -> {
                            val subScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(subScrollState)
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                // Sub-page Header with Back Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(onClick = { currentSubPage = LyricsSheetSubPage.MAIN }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "Back",
                                                tint = Color.White
                                            )
                                        }
                                        Text(
                                            text = "Style & Customization",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    IconButton(onClick = onDismissRequest) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Close",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val fontResolver = LocalLyricsFontResolver.current

                                // 1. Typography & Font Style (14 Distinct Fonts)
                                Text(
                                    text = "TYPOGRAPHY & FONT",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = "Select a font: Papyrus, Minecraft, Patrick Hand & 11 popular styles",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.40f),
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                val fontScrollState = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(fontScrollState),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LyricsFontChoice.entries.forEach { font ->
                                        val isSelected = settings.fontChoice == font
                                        val previewFontFamily = fontResolver(font)

                                        Box(
                                            modifier = Modifier
                                                .width(160.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isSelected) Color.White.copy(alpha = 0.20f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .clickable {
                                                    onUpdateSettings(settings.copy(fontChoice = font))
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = font.displayName,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.90f),
                                                        maxLines = 1
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = "Selected",
                                                            tint = accent,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = font.subtitle,
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.50f),
                                                    maxLines = 1
                                                )

                                                Spacer(modifier = Modifier.height(2.dp))

                                                // Live font preview sample text
                                                Text(
                                                    text = "The rhythm of music",
                                                    fontFamily = previewFontFamily,
                                                    fontSize = 15.sp,
                                                    fontWeight = if (settings.isBold) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color(settings.baseColor.colorArgb) else Color.White.copy(alpha = 0.75f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 2. Lyrics Text Size & Weight
                                SettingSliderRow(
                                    label = "Lyrics Text Size",
                                    valueText = "%.0f%%".format(settings.fontSizeScale * 100f),
                                    value = settings.fontSizeScale,
                                    valueRange = 0.70f..1.60f,
                                    steps = 18,
                                    accent = accent,
                                    onValueChange = { onUpdateSettings(settings.copy(fontSizeScale = (it * 20f).roundToInt() / 20f)) }
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Bold Letterforms",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Enhance contrast and weight across backdrop",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.50f)
                                        )
                                    }
                                    Switch(
                                        checked = settings.isBold,
                                        onCheckedChange = { onUpdateSettings(settings.copy(isBold = it)) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = accent,
                                             uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // 3. Base Lyrics Color Swatches
                                Text(
                                    text = "BASE LYRICS COLOR",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                val colorScrollState = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(colorScrollState),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LyricsBaseColor.entries.forEach { col ->
                                        val isSelected = settings.baseColor == col
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) Color(col.colorArgb) else Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { onUpdateSettings(settings.copy(baseColor = col)) }
                                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                                            ) {
                                                 Box(
                                                     modifier = Modifier
                                                         .size(16.dp)
                                                         .clip(CircleShape)
                                                         .background(Color(col.colorArgb))
                                                         .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                                     contentAlignment = Alignment.Center
                                                 ) {
                                                     if (isSelected) {
                                                         Box(
                                                             modifier = Modifier
                                                                 .size(6.dp)
                                                                 .clip(CircleShape)
                                                                 .background(if (col == LyricsBaseColor.PURE_WHITE || col == LyricsBaseColor.WARM_CREAM) Color.Black else Color.White)
                                                         )
                                                     }
                                                 }
                                                 Text(
                                                     text = col.displayName,
                                                     fontSize = 12.sp,
                                                     fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                     color = Color.White
                                                 )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // 4. Glow Palette & Bloom Intensity
                                Text(
                                    text = "GLOW PALETTE & BLOOM",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    LyricsGlowPalette.entries.forEach { p ->
                                        val isSelected = settings.palette == p
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (isSelected) Color(p.midColorArgb).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f))
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) Color(p.litColorArgb) else Color.White.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(999.dp)
                                                )
                                                .clickable { onUpdateSettings(settings.copy(palette = p)) }
                                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(p.litColorArgb))
                                                )
                                                Text(
                                                    text = p.displayName,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                SettingSliderRow(
                                    label = "Glow Bloom & Intensity",
                                    valueText = if (settings.glowIntensity <= 0.05f) "Off (Crisp)" else "%.2fx".format(settings.glowIntensity),
                                    value = settings.glowIntensity,
                                    valueRange = 0.00f..2.00f,
                                    accent = accent,
                                    onValueChange = { onUpdateSettings(settings.copy(glowIntensity = it)) }
                                )

                                SettingSliderRow(
                                    label = "Background Lyrics Opacity",
                                    valueText = "%.0f%%".format(settings.inactiveOpacity * 100f),
                                    value = settings.inactiveOpacity,
                                    valueRange = 0.15f..0.75f,
                                    accent = accent,
                                    onValueChange = { onUpdateSettings(settings.copy(inactiveOpacity = it)) }
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                                Spacer(modifier = Modifier.height(10.dp))

                                // 5. Motion & Viewport Settings
                                if (settings.style == LyricsStyle.CINEMATIC_KINETIC) {
                                    SettingSliderRow(
                                        label = "Viewport Zoom",
                                        valueText = "%.2fx".format(settings.commonZoom),
                                        value = settings.commonZoom,
                                        valueRange = 0.40f..1.80f,
                                        steps = 27,
                                        accent = accent,
                                        onValueChange = { onUpdateSettings(settings.copy(commonZoom = (it * 20f).roundToInt() / 20f)) }
                                    )

                                    SettingSliderRow(
                                        label = "Camera Glide Responsiveness",
                                        valueText = "%.2f".format(settings.cameraGlide),
                                        value = settings.cameraGlide,
                                        valueRange = 0.30f..1.50f,
                                        accent = accent,
                                        onValueChange = { onUpdateSettings(settings.copy(cameraGlide = it)) }
                                    )

                                    SettingSliderRow(
                                        label = "Word Bounce & Pop",
                                        valueText = "%.2f".format(settings.bounce),
                                        value = settings.bounce,
                                        valueRange = 0.00f..1.50f,
                                        accent = accent,
                                        onValueChange = { onUpdateSettings(settings.copy(bounce = it)) }
                                    )

                                    SettingSliderRow(
                                        label = "Horizontal Choreography Swing",
                                        valueText = "%.2f".format(settings.lrSwing),
                                        value = settings.lrSwing,
                                        valueRange = 0.00f..1.00f,
                                        accent = accent,
                                        onValueChange = { onUpdateSettings(settings.copy(lrSwing = it)) }
                                    )

                                    SettingSliderRow(
                                        label = "Line & Stanza Spacing",
                                        valueText = "%.2fx".format(settings.verticalSpacing),
                                        value = settings.verticalSpacing,
                                        valueRange = 0.70f..1.60f,
                                        accent = accent,
                                        onValueChange = { onUpdateSettings(settings.copy(verticalSpacing = it)) }
                                    )

                                    SettingSliderRow(
                                        label = "Starfield Particle Density",
                                        valueText = "%.2f".format(settings.sparkleDensity),
                                        value = settings.sparkleDensity,
                                        valueRange = 0.00f..1.00f,
                                        accent = accent,
                                        onValueChange = { onUpdateSettings(settings.copy(sparkleDensity = it)) }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Shuffle Layout Button
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.10f))
                                            .clickable { onShuffleLayout() }
                                            .padding(vertical = 11.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Shuffle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Shuffle Kinetic Layout",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                } else {
                                    // Classic Fullscreen Settings
                                    SettingSliderRow(
                                        label = "Master Zoom Scale",
                                        valueText = "%.2fx".format(settings.commonZoom),
                                        value = settings.commonZoom,
                                        valueRange = 0.40f..1.80f,
                                        steps = 27,
                                        accent = accent,
                                        onValueChange = { onUpdateSettings(settings.copy(commonZoom = (it * 20f).roundToInt() / 20f)) }
                                    )

                                    SettingSliderRow(
                                        label = "Line Spacing",
                                        valueText = "%.2fx".format(settings.verticalSpacing),
                                        value = settings.verticalSpacing,
                                        valueRange = 0.70f..1.60f,
                                        accent = accent,
                                        onValueChange = { onUpdateSettings(settings.copy(verticalSpacing = it)) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Show Top Header Bar Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Show Top Floating Header",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Display track title and back button at top",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.50f)
                                        )
                                    }
                                    Switch(
                                        checked = settings.showTopBar,
                                        onCheckedChange = { onUpdateSettings(settings.copy(showTopBar = it)) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = accent,
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Romanize Foreign Lyrics Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Romanize Foreign Lyrics",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Transliterate Korean, Japanese, Chinese, Urdu, Hindi, Cyrillic",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.50f)
                                        )
                                    }
                                    Switch(
                                        checked = settings.romanizeForeignLyrics,
                                        onCheckedChange = { onUpdateSettings(settings.copy(romanizeForeignLyrics = it)) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = accent,
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Lyrics Display Mode Segmented Control
                                Text(
                                    text = "DISPLAY MODE",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.07f))
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    LyricsDisplayMode.entries.forEach { mode ->
                                        val isSelected = settings.lyricsDisplayMode == mode
                                        val label = when (mode) {
                                            LyricsDisplayMode.ORIGINAL_ONLY -> "Original"
                                            LyricsDisplayMode.DUAL_LINE -> "Dual-Line"
                                            LyricsDisplayMode.ROMANIZED_ONLY -> "Romanized"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (isSelected) Color.White else Color.Transparent)
                                                .clickable { onUpdateSettings(settings.copy(lyricsDisplayMode = mode)) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f)
            )
            Text(
                text = valueText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}
