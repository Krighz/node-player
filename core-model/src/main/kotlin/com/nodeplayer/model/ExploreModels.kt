package com.nodeplayer.model

import kotlinx.serialization.Serializable

@Serializable
data class MoodGenre(
    val title: String,
    val stripeColor: Long?,
    val browseId: String,
    val params: String?,
    val thumbnailUrl: String? = null
)

@Serializable
data class MoodGenreSection(
    val title: String,
    val items: List<MoodGenre>
)
