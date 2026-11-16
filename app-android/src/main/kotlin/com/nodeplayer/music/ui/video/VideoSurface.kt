package com.nodeplayer.music.ui.video

import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.nodeplayer.model.video.SurfacePriority
import com.nodeplayer.model.video.VideoAspectMath

@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    fit: Boolean = true,
    priority: SurfacePriority = SurfacePriority.NOW_PLAYING_CARD,
    player: Player? = null,
    useTextureView: Boolean = true,
    onFirstFrameRendered: (() -> Unit)? = null
) {
    var textureView by remember { mutableStateOf<TextureView?>(null) }

    DisposableEffect(textureView, priority, player) {
        val view = textureView ?: return@DisposableEffect onDispose {}
        if (player != null) {
            player.setVideoTextureView(view)
            onDispose {
                player.clearVideoTextureView(view)
            }
        } else {
            val release = MusicVideoEngine.claimSurface(view, priority)
            onDispose { release() }
        }
    }

    val engineRendered by MusicVideoEngine.hasRenderedFirstFrame.collectAsStateWithLifecycle()
    val hasRendered = if (player != null) true else engineRendered
    val engineAspect by MusicVideoEngine.aspect.collectAsStateWithLifecycle()
    val videoAspect = if (player != null) {
        val size = player.videoSize
        if (size.width > 0 && size.height > 0) {
            val par = if (size.pixelWidthHeightRatio > 0f) size.pixelWidthHeightRatio else 1f
            (size.width * par) / size.height
        } else {
            16f / 9f
        }
    } else {
        engineAspect
    }

    LaunchedEffect(hasRendered) {
        if (hasRendered) {
            onFirstFrameRendered?.invoke()
        }
    }

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    textureView = this
                }
            },
            onRelease = { if (textureView === it) textureView = null },
            modifier = Modifier.layout { measurable, constraints ->
                val (w, h) = VideoAspectMath.calculateDimensions(
                    containerWidth = constraints.maxWidth,
                    containerHeight = if (constraints.hasBoundedHeight) {
                        constraints.maxHeight
                    } else {
                        (constraints.maxWidth / videoAspect).toInt()
                    },
                    videoAspect = videoAspect,
                    fit = fit
                )
                val placeable = measurable.measure(
                    Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((constraints.maxWidth - w) / 2, (constraints.maxHeight - h) / 2)
                }
            }
        )
    }
}
