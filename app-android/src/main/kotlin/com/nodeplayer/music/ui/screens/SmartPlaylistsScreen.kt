package com.nodeplayer.music.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nodeplayer.model.*
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.ui.components.LiquidChip
import java.io.File

@Composable
fun SmartPlaylistsScreen(
    allTracks: List<Track>,
    onPlayTracks: (List<Track>) -> Unit,
    onBack: () -> Unit
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current

    // Maintain active smart playlist definitions (starts with ready-made 4 presets)
    var smartPlaylists by remember {
        mutableStateOf(SmartPlaylist.defaultPlaylists())
    }

    var editingPlaylist by remember { mutableStateOf<SmartPlaylist?>(null) }
    var selectedPlaylistForDetail by remember { mutableStateOf<SmartPlaylist?>(null) }

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
                    text = "Smart Playlists",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Dynamic rule-based auto-updating playlists",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }
            IconButton(onClick = {
                val newPl = SmartPlaylist(
                    id = System.currentTimeMillis(),
                    name = "New Smart Playlist",
                    ruleGroup = SmartRuleGroup(
                        operator = LogicalOperator.AND,
                        rules = listOf(SmartRule(SmartRuleField.LOSSLESS, SmartRuleOperator.IS_TRUE, "true"))
                    )
                )
                editingPlaylist = newPl
            }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Playlist",
                    tint = accent
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedPlaylistForDetail != null) {
            val pl = selectedPlaylistForDetail!!
            val matching = remember(allTracks, pl) {
                pl.filterTracks(allTracks)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedPlaylistForDetail = null }) {
                    Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "${pl.name} (${matching.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { exportM3u(context, pl.name, matching) }) {
                    Icon(imageVector = Icons.Rounded.Share, contentDescription = "Export M3U", tint = accent)
                }
                IconButton(onClick = { onPlayTracks(matching) }) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = "Play", tint = accent)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(matching, key = { index, track -> "${track.id}_$index" }) { _, track ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        
                        onClick = { onPlayTracks(listOf(track) + matching.filter { it.id != track.id }) }
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
                                    text = "${track.artist} • ${track.durationFormatted}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(smartPlaylists, key = { index, playlist -> "${playlist.id}_$index" }) { _, playlist ->
                    val matchedCount = remember(allTracks, playlist) {
                        playlist.filterTracks(allTracks).size
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        
                        onClick = { selectedPlaylistForDetail = playlist }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$matchedCount tracks matched",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                )
                            }

                            IconButton(onClick = {
                                val matched = playlist.filterTracks(allTracks)
                                exportM3u(context, playlist.name, matched)
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.FileDownload,
                                    contentDescription = "Export M3U",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }

                            IconButton(onClick = {
                                val matched = playlist.filterTracks(allTracks)
                                if (matched.isNotEmpty()) {
                                    onPlayTracks(matched)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rule Builder / Editor Dialog
    if (editingPlaylist != null) {
        SmartPlaylistEditorDialog(
            playlist = editingPlaylist!!,
            allTracks = allTracks,
            onDismiss = { editingPlaylist = null },
            onSave = { updated ->
                smartPlaylists = (smartPlaylists.filter { it.id != updated.id } + updated).toList()
                editingPlaylist = null
            }
        )
    }
}

@Composable
fun SmartPlaylistEditorDialog(
    playlist: SmartPlaylist,
    allTracks: List<Track>,
    onDismiss: () -> Unit,
    onSave: (SmartPlaylist) -> Unit
) {
    var name by remember { mutableStateOf(playlist.name) }
    var selectedField by remember { mutableStateOf(SmartRuleField.LOSSLESS) }
    var selectedOp by remember { mutableStateOf(SmartRuleOperator.IS_TRUE) }
    var ruleValue by remember { mutableStateOf("true") }

    val previewCount = remember(name, selectedField, selectedOp, ruleValue) {
        val testPl = playlist.copy(
            name = name,
            ruleGroup = SmartRuleGroup(
                operator = LogicalOperator.AND,
                rules = listOf(SmartRule(selectedField, selectedOp, ruleValue))
            )
        )
        testPl.filterTracks(allTracks).size
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Smart Playlist", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Condition Rule:", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(6.dp))

                // Field selector pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        SmartRuleField.LOSSLESS to "Lossless",
                        SmartRuleField.HIRES_STATUS to "Hi-Res",
                        SmartRuleField.DR_SCORE to "DR",
                        SmartRuleField.YEAR to "Year"
                    ).forEach { (field, label) ->
                        LiquidChip(
                            selected = selectedField == field,
                            onClick = {
                                selectedField = field
                                when (field) {
                                    SmartRuleField.LOSSLESS -> {
                                        selectedOp = SmartRuleOperator.IS_TRUE
                                        ruleValue = "true"
                                    }
                                    SmartRuleField.DR_SCORE -> {
                                        selectedOp = SmartRuleOperator.GREATER_THAN
                                        ruleValue = "10"
                                    }
                                    SmartRuleField.HIRES_STATUS -> {
                                        selectedOp = SmartRuleOperator.EQUALS
                                        ruleValue = "GENUINE"
                                    }
                                    else -> {
                                        selectedOp = SmartRuleOperator.EQUALS
                                        ruleValue = "2024"
                                    }
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ruleValue,
                    onValueChange = { ruleValue = it },
                    label = { Text("Filter Value") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Live Preview: $previewCount tracks match",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = LocalAccentColor.current
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val updated = playlist.copy(
                    name = name,
                    ruleGroup = SmartRuleGroup(
                        operator = LogicalOperator.AND,
                        rules = listOf(SmartRule(selectedField, selectedOp, ruleValue))
                    )
                )
                onSave(updated)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun exportM3u(context: Context, name: String, tracks: List<Track>) {
    try {
        val safeName = name.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
        val file = File(context.cacheDir, "$safeName.m3u")
        val content = buildString {
            appendLine("#EXTM3U")
            for (t in tracks) {
                appendLine("#EXTINF:${t.durationMs / 1000},${t.artist} - ${t.title}")
                appendLine(t.path)
            }
        }
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export Playlist M3U"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting M3U: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
