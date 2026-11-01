package com.nodeplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nodeplayer.model.Track
import com.nodeplayer.music.data.DailyListeningStatEntity
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(
    dailyStats: List<DailyListeningStatEntity>,
    allTracks: List<Track>,
    onBack: () -> Unit,
    onOpenRecap: () -> Unit,
    hazeState: HazeState? = null
) {
    val accent = LocalAccentColor.current

    // Aggregate statistics
    val todayStr = remember { LocalDate.now().toString() }
    val todaySec = remember(dailyStats) {
        dailyStats.firstOrNull { it.date == todayStr }?.listeningSeconds ?: 0L
    }

    val totalSec = remember(dailyStats) {
        dailyStats.sumOf { it.listeningSeconds }
    }

    val totalHours = totalSec / 3600f

    // Top tracks (sorted by playCount descending)
    val topTracks = remember(allTracks) {
        allTracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(10)
    }

    // Top artists
    val topArtists = remember(allTracks) {
        allTracks.filter { it.playCount > 0 }
            .groupBy { it.artist }
            .mapValues { entry -> entry.value.sumOf { it.playCount } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    // Last 7 days for the chart
    val last7Days = remember(dailyStats) {
        val today = LocalDate.now()
        val list = mutableListOf<Pair<String, Long>>()
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.toString()
            val dayLabel = date.format(DateTimeFormatter.ofPattern("EEE"))
            val sec = dailyStats.firstOrNull { it.date == dateStr }?.listeningSeconds ?: 0L
            list.add(dayLabel to sec)
        }
        list
    }

    val maxDaySec = remember(last7Days) {
        (last7Days.maxOfOrNull { it.second } ?: 3600L).coerceAtLeast(60L)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
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
                    text = "Listening Stats",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Wall-clock playtime analytics",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }

            Button(
                onClick = onOpenRecap,
                colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.25f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Celebration, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Recap", color = accent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Metric Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        hazeState = hazeState,
                        tintAlpha = 0.45f
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Today", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f)))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${todaySec / 60}m",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = accent)
                            )
                            Text("${todaySec % 60}s active", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp))
                        }
                    }

                    GlassCard(
                        modifier = Modifier.weight(1f),
                        hazeState = hazeState,
                        tintAlpha = 0.45f
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Total Time", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f)))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%.1fh".format(totalHours),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = Color.White)
                            )
                            Text("${dailyStats.size} active days", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp))
                        }
                    }

                    GlassCard(
                        modifier = Modifier.weight(1f),
                        hazeState = hazeState,
                        tintAlpha = 0.45f
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Tracks Played", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f)))
                            Spacer(modifier = Modifier.height(4.dp))
                            val totalPlays = allTracks.sumOf { it.playCount }
                            Text(
                                text = "$totalPlays",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
                            )
                            Text("plays recorded", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp))
                        }
                    }
                }
            }

            // Glass Daily Activity Chart (Past 7 Days)
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    hazeState = hazeState,
                    tintAlpha = 0.45f
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LAST 7 DAYS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    color = accent
                                )
                            )
                            Text(
                                text = "Daily Listening",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bar Chart Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            last7Days.forEach { (day, sec) ->
                                val fraction = (sec.toFloat() / maxDaySec.toFloat()).coerceIn(0.05f, 1f)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${sec / 60}m",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            color = if (sec > 0) Color.White.copy(alpha = 0.8f) else Color.Transparent
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .fillMaxHeight(fraction)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(accent, accent.copy(alpha = 0.4f))
                                                )
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = day,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top Artists
            if (topArtists.isNotEmpty()) {
                item {
                    Text(
                        text = "TOP ARTISTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            color = accent
                        )
                    )
                }

                items(topArtists) { (artist, plays) ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        hazeState = hazeState,
                        tintAlpha = 0.35f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$plays plays",
                                style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Top Tracks
            if (topTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "TOP TRACKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            color = accent
                        )
                    )
                }

                items(topTracks) { track ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        hazeState = hazeState,
                        tintAlpha = 0.35f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                                )
                            }
                            Text(
                                text = "${track.playCount} plays",
                                style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
