package com.nodeplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nodeplayer.common.AppModeState
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.DiscoveredPeerInfo
import com.nodeplayer.ui.components.DockScrollController

/**
 * Root HomeView acting as an intelligent router between [OfflineHomeView] and [OnlineHomeView].
 *
 * - When [AppModeState.isOnlineMode] is false (Offline):
 *   Renders [OfflineHomeView] with local FLACs, albums, playlists, and zero network calls.
 *
 * - When [AppModeState.isOnlineMode] is true (Online):
 *   Renders [OnlineHomeView] with YouTube Music recommendations, trending charts, and radio mixes.
 *
 * State and scroll positions are retained across mode switches via hoisted LazyListStates.
 */
@Composable
fun HomeView(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    onQuickShuffle: () -> Unit,
    onRescanClick: () -> Unit = {},
    onSelectMusicFolder: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    dockScrollController: DockScrollController? = null,
    showDevicePill: Boolean = false,
    nodeConnectEnabled: Boolean = true,
    connectedDeviceCount: Int = 0,
    onOpenSyncCenter: () -> Unit = {},
    onPairNewDevice: () -> Unit = {},
    onCastToPeer: (DiscoveredPeerInfo) -> Unit = {},
    onToggleNodeConnect: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isOnline by AppModeState.isOnlineMode.collectAsState()
    val offlineListState = rememberLazyListState()
    val onlineListState = rememberLazyListState()

    AnimatedContent(
        targetState = isOnline,
        transitionSpec = {
            (fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.98f, animationSpec = tween(260))) togetherWith
            (fadeOut(animationSpec = tween(260)) + scaleOut(targetScale = 0.98f, animationSpec = tween(260)))
        },
        label = "HomeModeTransition",
        modifier = modifier.fillMaxSize()
    ) { online ->
        if (online) {
            val recentTracks = remember(tracks) {
                tracks.filter { it.lastPlayedTimestamp > 0L }.sortedByDescending { it.lastPlayedTimestamp }
            }
            OnlineHomeView(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onPlayTrack = onPlayTrack,
                onPlayPause = onPlayPause,
                recentTracks = recentTracks,
                dockScrollController = dockScrollController,
                listState = onlineListState
            )
        } else {
            OfflineHomeView(
                tracks = tracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onPlayTrack = onPlayTrack,
                onPlayPause = onPlayPause,
                onQuickShuffle = onQuickShuffle,
                onRescanClick = onRescanClick,
                onSelectMusicFolder = onSelectMusicFolder,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToEqualizer = onNavigateToEqualizer,
                dockScrollController = dockScrollController,
                showDevicePill = showDevicePill,
                nodeConnectEnabled = nodeConnectEnabled,
                connectedDeviceCount = connectedDeviceCount,
                onOpenSyncCenter = onOpenSyncCenter,
                onPairNewDevice = onPairNewDevice,
                onCastToPeer = onCastToPeer,
                onToggleNodeConnect = onToggleNodeConnect,
                listState = offlineListState
            )
        }
    }
}
