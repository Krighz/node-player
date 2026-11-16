package com.nodeplayer.music.ui.video

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.nodeplayer.model.video.ResolvedPlaybackSource

@UnstableApi
object VideoMediaSourceFactory {

    fun buildMergedSource(
        factory: DataSource.Factory,
        videoUrl: String,
        audioUrl: String,
        videoMediaId: String = "video_stream",
        audioMediaId: String = "audio_stream"
    ): MediaSource {
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val video = ProgressiveMediaSource.Factory(factory, extractors)
            .createMediaSource(MediaItem.Builder().setUri(videoUrl).setMediaId(videoMediaId).build())
        val audio = ProgressiveMediaSource.Factory(factory, extractors)
            .createMediaSource(MediaItem.Builder().setUri(audioUrl).setMediaId(audioMediaId).build())
        return MergingMediaSource(video, audio)
    }

    fun buildSource(
        factory: DataSource.Factory,
        resolved: ResolvedPlaybackSource,
        trackId: String
    ): MediaSource? {
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        return when (resolved) {
            is ResolvedPlaybackSource.Separate -> {
                if (resolved.videoUrl.isBlank() || resolved.audioUrl.isBlank()) null
                else buildMergedSource(factory, resolved.videoUrl, resolved.audioUrl, "video_$trackId", "audio_$trackId")
            }
            is ResolvedPlaybackSource.Muxed -> {
                if (resolved.streamUrl.isBlank()) null
                else ProgressiveMediaSource.Factory(factory, extractors)
                    .createMediaSource(MediaItem.Builder().setUri(resolved.streamUrl).setMediaId("muxed_$trackId").build())
            }
            is ResolvedPlaybackSource.AudioOnly -> {
                if (resolved.audioUrl.isBlank()) null
                else ProgressiveMediaSource.Factory(factory, extractors)
                    .createMediaSource(MediaItem.Builder().setUri(resolved.audioUrl).setMediaId("audio_$trackId").build())
            }
            ResolvedPlaybackSource.Empty -> null
        }
    }
}
