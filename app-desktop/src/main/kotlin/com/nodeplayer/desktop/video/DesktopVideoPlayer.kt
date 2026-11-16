package com.nodeplayer.desktop.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

object DesktopVideoDiscovery {
    val isVlcAvailable: Boolean by lazy {
        try {
            uk.co.caprica.vlcj.factory.discovery.NativeDiscovery().discover()
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * High-performance Compose Desktop video rendering surface slaved to the master audio clock.
 * Features automatic drift correction (< 150ms) and graceful degradation when VLC is absent.
 */
@Composable
fun DesktopVideoSurface(
    streamUrl: String?,
    isPlaying: Boolean,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    if (!DesktopVideoDiscovery.isVlcAvailable || streamUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (!DesktopVideoDiscovery.isVlcAvailable) "VLC Media Player required for desktop video" else "No video stream",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
        return
    }

    val mediaPlayerComponent = remember {
        try {
            uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent()
        } catch (_: Throwable) {
            null
        }
    }

    DisposableEffect(streamUrl) {
        if (mediaPlayerComponent != null && !streamUrl.isNullOrBlank()) {
            try {
                mediaPlayerComponent.mediaPlayer().media().play(streamUrl)
                if (positionMs > 0) {
                    mediaPlayerComponent.mediaPlayer().controls().setTime(positionMs)
                }
            } catch (_: Throwable) {}
        }
        onDispose {
            try {
                mediaPlayerComponent?.release()
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(isPlaying) {
        if (mediaPlayerComponent != null) {
            try {
                if (isPlaying) {
                    mediaPlayerComponent.mediaPlayer().controls().play()
                } else {
                    mediaPlayerComponent.mediaPlayer().controls().pause()
                }
            } catch (_: Throwable) {}
        }
    }

    // Bidirectional A/V sync: drift correction if out of sync by > 150ms
    LaunchedEffect(positionMs) {
        if (mediaPlayerComponent != null && isPlaying) {
            try {
                val vlcTime = mediaPlayerComponent.mediaPlayer().status().time()
                if (kotlin.math.abs(vlcTime - positionMs) > 150L) {
                    mediaPlayerComponent.mediaPlayer().controls().setTime(positionMs)
                }
            } catch (_: Throwable) {}
        }
    }

    if (mediaPlayerComponent != null) {
        SwingPanel(
            factory = { mediaPlayerComponent },
            modifier = modifier.fillMaxSize()
        )
    }
}
