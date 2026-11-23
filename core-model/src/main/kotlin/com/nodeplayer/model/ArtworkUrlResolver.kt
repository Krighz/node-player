package com.nodeplayer.model

/**
 * Pure Kotlin resolver that transforms low-resolution or thumbnail URLs into
 * crisp, high-resolution master image URLs based on [AlbumArtQuality].
 */
object ArtworkUrlResolver {

    private val GOOGLE_USER_CONTENT_REGEX = Regex("(=[sw]\\d+[^?#]*)$")

    /**
     * Resolves the optimal artwork URL for [rawUrl] according to [quality].
     */
    fun resolveArtworkUrl(rawUrl: String?, quality: AlbumArtQuality = AlbumArtQuality.HIGH): String? {
        if (rawUrl.isNullOrBlank()) return null
        val url = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl

        // 1. Google User Content / YouTube Music CDN
        if (url.contains("googleusercontent.com")) {
            val param = quality.googleUserContentSizeParam
            return if (GOOGLE_USER_CONTENT_REGEX.containsMatchIn(url)) {
                url.replace(GOOGLE_USER_CONTENT_REGEX, param)
            } else if (!url.contains(param)) {
                if (url.contains("?")) {
                    val base = url.substringBefore("?")
                    val query = url.substringAfter("?")
                    "$base$param?$query"
                } else {
                    "$url$param"
                }
            } else {
                url
            }
        }

        // 2. YouTube Video Thumbnail
        val videoId = extractYouTubeVideoId(url)
        if (videoId != null) {
            return when (quality) {
                AlbumArtQuality.HIGH -> "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
                AlbumArtQuality.MEDIUM -> "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                AlbumArtQuality.LOW -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
        }

        return url
    }

    /**
     * Returns candidate URLs in descending quality order for resilient fallback.
     */
    fun resolveYouTubeThumbnailCandidates(rawUrl: String, quality: AlbumArtQuality = AlbumArtQuality.HIGH): List<String> {
        val videoId = extractYouTubeVideoId(rawUrl) ?: return listOf(rawUrl)
        return when (quality) {
            AlbumArtQuality.HIGH -> listOf(
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$videoId/hq720.jpg",
                "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            )
            AlbumArtQuality.MEDIUM -> listOf(
                "https://i.ytimg.com/vi/$videoId/hq720.jpg",
                "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            )
            AlbumArtQuality.LOW -> listOf(
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
            )
        }
    }

    fun extractYouTubeVideoId(url: String): String? {
        return when {
            url.startsWith("nodeplayer://youtube/") ->
                url.removePrefix("nodeplayer://youtube/").substringBefore('?').substringBefore('/')
            url.contains("i.ytimg.com/vi/") ->
                url.substringAfter("i.ytimg.com/vi/").substringBefore('/')
            url.contains("youtube.com/watch?v=") ->
                url.substringAfter("watch?v=").substringBefore('&')
            url.contains("youtu.be/") ->
                url.substringAfter("youtu.be/").substringBefore('?')
            else -> null
        }
    }
}
