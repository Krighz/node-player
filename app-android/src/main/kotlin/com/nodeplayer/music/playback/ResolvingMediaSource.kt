package com.nodeplayer.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import com.nodeplayer.network.innertube.StreamResolver

/**
 * Resolving media source for ExoPlayer.
 *
 * @deprecated Prefer using [AudioCache.createResolvingDataSourceFactory] directly with [ProgressiveMediaSource.Factory].
 * This class now transparently wraps [ProgressiveMediaSource] backed by Media3's [ResolvingDataSource.Factory],
 * ensuring zero looper stalling or uninitialized child race conditions during [createPeriod].
 */
@Deprecated("Use AudioCache.createResolvingDataSourceFactory directly with ProgressiveMediaSource.Factory")
@UnstableApi
class ResolvingMediaSource(
    private val context: Context,
    private val item: MediaItem,
    private val streamResolver: StreamResolver = StreamResolver(),
    private val resolve: (suspend () -> MediaSource)? = null
) : CompositeMediaSource<Unit>() {

    private val child: MediaSource = run {
        val factory = AudioCache.createResolvingDataSourceFactory(context, streamResolver)
        ProgressiveMediaSource.Factory(
            factory,
            androidx.media3.extractor.DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        ).createMediaSource(item)
    }

    override fun getMediaItem(): MediaItem = item

    override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean = false

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        prepareChildSource(Unit, child)
    }

    override fun onChildSourceInfoRefreshed(id: Unit, mediaSource: MediaSource, newTimeline: Timeline) {
        refreshSourceInfo(newTimeline)
    }

    override fun createPeriod(id: MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        child.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        child.releasePeriod(mediaPeriod)
    }

    override fun releaseSourceInternal() {
        super.releaseSourceInternal()
    }
}

