package com.nodeplayer.common.platform

interface MediaSessionBridge {
    fun setPlaybackState(isPlaying: Boolean, positionMs: Long, durationMs: Long)
    fun setMetadata(title: String, artist: String, album: String, artworkUri: String?)
    fun setEventListener(listener: MediaSessionEventListener)
}

interface MediaSessionEventListener {
    fun onPlay()
    fun onPause()
    fun onNext()
    fun onPrevious()
    fun onSeekTo(positionMs: Long)
}
