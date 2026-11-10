package com.nodeplayer.network.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("scope") val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null
)

data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val description: String? = null,
    val trackCount: Int = 0,
    val ownerName: String? = null,
    val coverUrl: String? = null
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
    val isrc: String? = null
) {
    val primaryArtist: String get() = artists.firstOrNull() ?: "Unknown Artist"
}
