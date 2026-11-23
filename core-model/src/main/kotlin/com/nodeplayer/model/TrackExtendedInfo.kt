package com.nodeplayer.model

data class CreditEntry(
    val name: String,
    val role: String,
    val browseId: String? = null
)

data class TrackExtendedInfo(
    val credits: List<CreditEntry> = emptyList(),
    val artistName: String = "",
    val artistBrowseId: String? = null,
    val artistBannerUrl: String? = null,
    val artistSubscribers: String? = null,
    val artistBio: String? = null,
    val moreByArtist: List<Track> = emptyList(),
    val moreLikeThis: List<Track> = emptyList()
)
