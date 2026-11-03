package com.nodeplayer.integration.api

data class ExternalTrackItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: String? = null
)

data class NowPlayingState(
    val protocolVersion: Int = ProtocolVersion.CURRENT_VERSION,
    val currentTrack: ExternalTrackItem?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0
)

sealed interface PlaybackCommand {
    data object PlayPause : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data class SeekTo(val positionMs: Long) : PlaybackCommand
    data class PlayTrack(val trackId: Long) : PlaybackCommand
}

data class ExtensionThemeMeta(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val author: String,
    val accentWarmHex: String,
    val accentCoolHex: String,
    val backgroundBaseHex: String,
    val defaultGlassOpacity: Float
)
