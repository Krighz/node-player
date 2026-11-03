package com.nodeplayer.integration.api

/**
 * Pure Kotlin integration contract for ecosystem companion apps (TV, Watch, Auto).
 * Any remote client interacts strictly via these command and state projections.
 */
interface IntegrationClientContract {
    fun getProtocolVersion(): Int = ProtocolVersion.CURRENT_VERSION
    fun observeNowPlaying(onStateChanged: (NowPlayingState) -> Unit)
    fun sendCommand(command: PlaybackCommand): Boolean
    fun queryLibrary(query: String, offset: Int, limit: Int): List<ExternalTrackItem>
}
