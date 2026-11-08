package com.nodeplayer.music.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.nodeplayer.common.AppModeState
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.network.innertube.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Lazy resolving media source for ExoPlayer.
 *
 * Resolves synthetic YouTube stream URIs (`nodeplayer://youtube/{videoId}`) into live playable
 * CDN stream URLs asynchronously on [Dispatchers.IO] only when ExoPlayer prepares playback.
 * Integrates with [AudioCache] for disk-cached offline replay and checks [AppModeState.isOnlineMode].
 */
@UnstableApi
class ResolvingMediaSource(
    private val context: Context,
    private val item: MediaItem,
    private val streamResolver: StreamResolver = StreamResolver(),
    private val resolve: (suspend () -> MediaSource)? = null
) : CompositeMediaSource<Unit>() {

    companion object {
        private const val TAG = "ResolvingMediaSource"
    }

    private var child: MediaSource? = null
    private var job: Job? = null
    private var handler: Handler? = null
    @Volatile private var error: IOException? = null
    private var generation = 0

    override fun getMediaItem(): MediaItem = item

    override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean = false

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val playbackHandler = Handler(checkNotNull(Looper.myLooper()))
        handler = playbackHandler
        val currentGeneration = ++generation
        error = null

        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val source = if (resolve != null) {
                    resolve.invoke()
                } else {
                    buildResolvedChildSource()
                }

                currentCoroutineContext().ensureActive()
                playbackHandler.post {
                    if (currentGeneration == generation) {
                        child = source
                        prepareChildSource(Unit, source)
                        NodeLog.d(TAG, "Child source prepared successfully for item: ${item.mediaId}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NodeLog.e(TAG, "Resolution failed for ${item.mediaId}: ${e.message}", e)
                playbackHandler.post {
                    if (currentGeneration == generation) {
                        error = if (e is IOException) e else IOException("Could not resolve playback source: ${e.message}", e)
                    }
                }
            }
        }
    }

    private suspend fun buildResolvedChildSource(): MediaSource {
        val uri = item.localConfiguration?.uri ?: Uri.parse(item.mediaId)
        val videoId = AudioCache.extractVideoId(uri)

        val dataSourceFactory = AudioCache.createDataSourceFactory(context)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(
            dataSourceFactory,
            androidx.media3.extractor.DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        )

        // Check if cached on disk
        val isCached = if (videoId != null) {
            AudioCache.isVideoIdCached(videoId)
        } else {
            AudioCache.isCached(uri)
        }

        if (isCached) {
            NodeLog.i(TAG, "Track $videoId is cached on disk. Replaying offline without stream resolution.")
            return mediaSourceFactory.createMediaSource(item)
        }

        // If offline and not cached, reject cleanly
        if (!AppModeState.isOnlineMode.value) {
            throw IOException("Track is not available offline (videoId: $videoId)")
        }

        // Resolve live stream URL using StreamResolver
        if (videoId.isNullOrBlank()) {
            throw IOException("Cannot extract video ID from URI: $uri")
        }

        NodeLog.d(TAG, "Resolving live stream lazily for videoId=$videoId")
        val result = streamResolver.resolveStream(videoId)
        if (result.isFailure) {
            throw IOException(result.exceptionOrNull()?.message ?: "StreamResolver failed for $videoId")
        }

        val resolved = result.getOrThrow()
        val streamUri = Uri.parse(resolved.streamUrl)
        val cacheKey = "nodeplayer_yt_$videoId"

        // Build media item pointing to resolved stream with custom cache key
        val resolvedItem = item.buildUpon()
            .setUri(streamUri)
            .setCustomCacheKey(cacheKey)
            .build()

        return mediaSourceFactory.createMediaSource(resolvedItem)
    }

    override fun onChildSourceInfoRefreshed(id: Unit, mediaSource: MediaSource, newTimeline: Timeline) {
        refreshSourceInfo(newTimeline)
    }

    override fun maybeThrowSourceInfoRefreshError() {
        error?.let { throw it }
        super.maybeThrowSourceInfoRefreshError()
    }

    override fun createPeriod(id: MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        checkNotNull(child) { "Child MediaSource is not yet prepared" }.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        child?.releasePeriod(mediaPeriod)
    }

    override fun releaseSourceInternal() {
        generation++
        job?.cancel()
        job = null
        handler?.removeCallbacksAndMessages(null)
        handler = null
        super.releaseSourceInternal()
        child = null
        error = null
    }
}
