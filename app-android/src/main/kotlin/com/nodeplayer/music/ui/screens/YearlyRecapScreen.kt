package com.nodeplayer.music.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nodeplayer.model.Track
import com.nodeplayer.music.data.DailyListeningStatEntity
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import dev.chrisbanes.haze.HazeState
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

@Composable
fun YearlyRecapScreen(
    dailyStats: List<DailyListeningStatEntity>,
    allTracks: List<Track>,
    onBack: () -> Unit,
    hazeState: HazeState? = null
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val currentView = LocalView.current

    val currentYear = remember { LocalDate.now().year }
    var selectedYear by remember { mutableIntStateOf(currentYear) }

    // Filter daily stats by selected year
    val yearStats = remember(dailyStats, selectedYear) {
        dailyStats.filter { it.date.startsWith("$selectedYear") }
    }

    val totalSec = remember(yearStats) {
        yearStats.sumOf { it.listeningSeconds }
    }
    val totalHours = totalSec / 3600f

    // Consecutive streak calculation
    val streakDays = remember(yearStats) {
        if (yearStats.isEmpty()) return@remember 0
        val sortedDates = yearStats.map { LocalDate.parse(it.date) }.sortedDescending()
        var streak = 0
        var expected = sortedDates.first()
        for (d in sortedDates) {
            if (d == expected) {
                streak++
                expected = expected.minusDays(1)
            } else {
                break
            }
        }
        streak
    }

    // Top track of the year
    val topTrack = remember(allTracks) {
        allTracks.maxByOrNull { it.playCount }
    }

    // Top artist of the year
    val topArtist = remember(allTracks) {
        allTracks.groupBy { it.artist }
            .mapValues { entry -> entry.value.sumOf { it.playCount } }
            .maxByOrNull { it.value }?.key ?: "None"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Node Recap",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Your Year in Sound",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }

            IconButton(onClick = {
                shareRecapCard(context, currentView)
            }) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Share Recap",
                    tint = accent
                )
            }
        }

        // Year Selector Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf(currentYear, currentYear - 1).forEach { year ->
                val isSelected = selectedYear == year
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedYear = year },
                    label = { Text("$year", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Glass Recap Card (Artistic Dribbble / Liquid Glass presentation)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.25f),
                            Color(0xFF0F1018).copy(alpha = 0.90f),
                            Color(0xFF07080D).copy(alpha = 0.95f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(accent.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "NODE PLAYER $selectedYear RECAP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                            color = accent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Total hours
                Text(
                    text = "%.1f".format(totalHours),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 58.sp
                    )
                )
                Text(
                    text = "HOURS OF MUSIC",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Stats Grid Inside Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$streakDays Days",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = accent)
                        )
                        Text(
                            text = "Streak",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allTracks.sumOf { it.playCount }}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        )
                        Text(
                            text = "Plays",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allTracks.count { it.isLossless }}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF818CF8))
                        )
                        Text(
                            text = "Lossless",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(20.dp))

                // Top Artist
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "TOP ARTIST",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = topArtist,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Top Track
                if (topTrack != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "TOP TRACK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = topTrack.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "${topTrack.artist} • ${topTrack.playCount} plays",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Crafted with pure local precision • Node Player",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { shareRecapCard(context, currentView) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Recap Card", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(180.dp))
    }
}

private fun shareRecapCard(context: Context, view: View) {
    try {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(720), view.height.coerceAtLeast(1280), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        val file = File(context.cacheDir, "node_player_recap_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Check out my Year in Sound with Node Player!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Yearly Recap"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing recap: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
