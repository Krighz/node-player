package com.nodeplayer.music.ui.screens

import android.app.Activity
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.library.DuplicateFinder
import com.nodeplayer.library.DuplicateGroup
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.components.HazeState
import com.nodeplayer.music.ui.theme.LocalAccentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DuplicateFinderScreen(
    allTracks: List<Track>,
    onBack: () -> Unit,
    onTrackDeleted: (Long) -> Unit = {},
    hazeState: HazeState? = null
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var selectedGroupToDelete by remember { mutableStateOf<Track?>(null) }

    // System MediaStore Delete/Trash Launcher for Android 11+
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedGroupToDelete?.let { track ->
                onTrackDeleted(track.id)
                // Remove from local list
                duplicateGroups = duplicateGroups.mapNotNull { group ->
                    val filtered = group.tracks.filter { it.id != track.id }
                    if (filtered.size > 1) group.copy(tracks = filtered) else null
                }
                Toast.makeText(context, "Track removed from library", Toast.LENGTH_SHORT).show()
            }
        }
        selectedGroupToDelete = null
    }

    LaunchedEffect(allTracks) {
        if (allTracks.isNotEmpty()) {
            isScanning = true
            coroutineScope.launch(Dispatchers.Default) {
                val groups = DuplicateFinder.findDuplicates(allTracks)
                withContext(Dispatchers.Main) {
                    duplicateGroups = groups
                    isScanning = false
                }
            }
        }
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
                    text = "Duplicate Finder",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = if (isScanning) "Scanning library for exact & similar duplicates..." else "${duplicateGroups.size} duplicate sets found",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }
            IconButton(
                onClick = {
                    isScanning = true
                    coroutineScope.launch(Dispatchers.Default) {
                        val groups = DuplicateFinder.findDuplicates(allTracks)
                        withContext(Dispatchers.Main) {
                            duplicateGroups = groups
                            isScanning = false
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Rescan",
                    tint = accent
                )
            }
        }

        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accent)
            }
        } else if (duplicateGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircleOutline,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Clean Library!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = "No duplicate files or matching audio copies found.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(duplicateGroups) { group ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        
                        tintAlpha = 0.45f
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = group.reason,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Text(
                                    text = "${group.tracks.size} versions",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // List duplicates in this group
                            group.tracks.forEach { track ->
                                val isBest = track.id == group.recommendedTrackId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isBest) accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                                                    color = Color.White
                                                )
                                            )
                                            if (isBest) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF10B981).copy(alpha = 0.25f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "BEST QUALITY",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color(0xFF10B981),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${track.mimeType.substringAfterLast("/")} • ${track.sampleRate} Hz • ${track.bitDepth}-bit • %.2f MB".format(track.size / (1024.0 * 1024.0)),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White.copy(alpha = 0.65f),
                                                fontSize = 11.sp
                                            )
                                        )
                                        Text(
                                            text = track.path,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White.copy(alpha = 0.40f),
                                                fontSize = 10.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }

                                    if (!isBest) {
                                        IconButton(
                                            onClick = {
                                                selectedGroupToDelete = track
                                                requestSystemTrackDeletion(context, track.id) { sender ->
                                                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete inferior duplicate",
                                                tint = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun requestSystemTrackDeletion(
    context: android.content.Context,
    trackId: Long,
    onSenderReady: (android.content.IntentSender) -> Unit
) {
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
        onSenderReady(pendingIntent.intentSender)
    } else {
        // Fallback for older versions
        try {
            context.contentResolver.delete(uri, null, null)
            Toast.makeText(context, "Deleted track", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error deleting track: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
