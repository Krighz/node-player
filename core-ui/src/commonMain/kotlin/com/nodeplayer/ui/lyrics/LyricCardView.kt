package com.nodeplayer.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle

private val CARD_CONTAINER_SHAPE = ContinuousRoundedRectangle(24.dp)
private val CARD_THUMBNAIL_SHAPE = ContinuousRoundedRectangle(10.dp)

/**
 * High-quality vertical Lyric Card (9:16 ratio) matching BitChord and Apple Music aesthetics.
 *
 * - Top-right: Node Player brand logo and title.
 * - Center: Selected lyric lines in bold, high-contrast typography.
 * - Bottom: Rounded squircle album artwork, track title, and artist name.
 * - Background: Dynamic vertical ambient gradient extracted from the album artwork palette.
 */
@Composable
fun LyricCardView(
    track: Track?,
    lines: List<String>,
    palette: AlbumArtPalette,
    coverBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    appName: String = "Node Player"
) {
    val topGradientColor = remember(palette.darkMuted) {
        Color(palette.darkMuted).copy(alpha = 1f)
    }
    val centerGradientColor = remember(palette.dominant, palette.vibrant) {
        val base = if (palette.dominant != 0L) Color(palette.dominant) else Color(palette.vibrant)
        base.copy(alpha = 0.50f)
    }
    val bottomGradientColor = Color(0xFF09090B)

    val backgroundBrush = remember(topGradientColor, centerGradientColor, bottomGradientColor) {
        Brush.verticalGradient(
            0.0f to topGradientColor,
            0.45f to centerGradientColor,
            1.0f to bottomGradientColor
        )
    }

    Box(
        modifier = modifier
            .clip(CARD_CONTAINER_SHAPE)
            .background(backgroundBrush)
            .border(1.dp, Color.White.copy(alpha = 0.12f), CARD_CONTAINER_SHAPE)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: App Branding on the Top Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = appName,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }

            // 2. Middle Body: Selected Lyric Lines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (lines.size <= 3) 16.dp else 10.dp
                )
            ) {
                val fontSize = when {
                    lines.size <= 2 -> 28.sp
                    lines.size <= 4 -> 24.sp
                    else -> 20.sp
                }
                val lineHeight = (fontSize.value * 1.30f).sp

                lines.forEach { lineText ->
                    Text(
                        text = lineText,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = lineHeight,
                        letterSpacing = (-0.45).sp,
                        color = Color.White
                    )
                }
            }

            // 3. Bottom Footer: Album Art Thumbnail, Song Title, and Artist
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CARD_THUMBNAIL_SHAPE)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = track?.title ?: "Unknown Track",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track?.artist ?: "Unknown Artist",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Modal dialog presenting the Lyric Card preview with options to share the high-res image,
 * save it to the device, or copy the lyric text.
 */
@Composable
fun LyricCardGeneratorDialog(
    track: Track?,
    lines: List<String>,
    palette: AlbumArtPalette,
    coverBitmap: ImageBitmap?,
    onDismiss: () -> Unit,
    onShareImage: () -> Unit,
    onSaveImage: () -> Unit = {},
    onCopyText: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* absorb clicks */ },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lyric Card Preview",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Lyric Card in 9:16 story aspect ratio
                LyricCardView(
                    track = track,
                    lines = lines,
                    palette = palette,
                    coverBitmap = coverBitmap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .heightIn(max = 520.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Actions Row: Share Image (Primary Pill) + Save + Copy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Share Card Button (Primary White Pill)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(ContinuousCapsule)
                            .background(Color.White)
                            .clickable { onShareImage() }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = Color.Black,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share Story",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }

                    // 2. Save Image Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { onSaveImage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Save Image",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // 3. Copy Text Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { onCopyText() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Text",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
