package com.nodeplayer.ui.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nodeplayer.network.spotify.SpotifyPlaylistSummary
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Dialog letting the user configure Spotify Client ID or pick a playlist to transfer.
 * Conforms to SPOT-02 and SPOT-06 with liquid glass styling.
 */
@Composable
fun SpotifyImportDialog(
    isConfigured: Boolean,
    isLoggedIn: Boolean,
    playlists: List<SpotifyPlaylistSummary>,
    isLoadingPlaylists: Boolean,
    onLoginClick: () -> Unit,
    onSaveClientId: (String) -> Unit,
    onSelectPlaylist: (SpotifyPlaylistSummary) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var inputClientId by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        GlassControlSurface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F1424).copy(alpha = 0.94f))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Spotify Transfer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Case 1: Unconfigured Client ID
                if (!isConfigured) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.VpnKey,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Spotify Client ID Required",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To transfer playlists, Spotify requires an App Client ID. You can set 'spotify.clientId' in local.properties or enter it below.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = inputClientId,
                            onValueChange = { inputClientId = it },
                            placeholder = { Text("Enter Spotify Client ID", color = Color.White.copy(alpha = 0.4f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accent,
                                cursorColor = accent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LiquidButton(
                            onClick = {
                                val trimmed = inputClientId.trim()
                                if (trimmed.isNotBlank()) onSaveClientId(trimmed)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Text("Save & Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else if (!isLoggedIn) {
                    // Case 2: Configured, but not signed in
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Connect your Spotify account to browse and transfer your playlists into Node Player.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        LiquidButton(
                            onClick = onLoginClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Sign in with Spotify", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    // Case 3: Logged in, list playlists
                    Text(
                        text = "Select a Spotify playlist to transfer:",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoadingPlaylists) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
                        }
                    } else if (playlists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No playlists found on your Spotify account.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(playlists, key = { it.id }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable { onSelectPlaylist(playlist) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color(0xFF1DB954),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.trackCount} tracks",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
