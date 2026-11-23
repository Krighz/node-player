package com.nodeplayer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUrlResolverTest {

    @Test
    fun testGoogleUserContentHighQualityUpscalingAcrossDifferentSongs() {
        // Song 1: "Thunder" by Imagine Dragons (YouTube Music 120px thumb)
        val song1Thumb = "https://lh3.googleusercontent.com/vH97Z7zB6xY7-a123_test=w120-h120-l90-rj"
        val high1 = ArtworkUrlResolver.resolveArtworkUrl(song1Thumb, AlbumArtQuality.HIGH)
        assertEquals("https://lh3.googleusercontent.com/vH97Z7zB6xY7-a123_test=w1200-h1200-l90-rj", high1)

        // Song 2: "Rock That Body" by Black Eyed Peas (YouTube Music 60px thumb)
        val song2Thumb = "https://lh3.googleusercontent.com/abc_rock_that_body=w60-h60-l90-rj"
        val high2 = ArtworkUrlResolver.resolveArtworkUrl(song2Thumb, AlbumArtQuality.HIGH)
        assertEquals("https://lh3.googleusercontent.com/abc_rock_that_body=w1200-h1200-l90-rj", high2)

        // Song 3: "Comfortably Numb" by Pink Floyd (s256-c square size param)
        val song3Thumb = "https://lh3.googleusercontent.com/pink_floyd_floats=s256-c-k-c0x00ffffff-no-rj"
        val high3 = ArtworkUrlResolver.resolveArtworkUrl(song3Thumb, AlbumArtQuality.HIGH)
        assertEquals("https://lh3.googleusercontent.com/pink_floyd_floats=w1200-h1200-l90-rj", high3)
    }

    @Test
    fun testGoogleUserContentMediumAndLowQuality() {
        val rawUrl = "https://lh3.googleusercontent.com/album_art_raw=w120-h120-l90-rj"

        val medium = ArtworkUrlResolver.resolveArtworkUrl(rawUrl, AlbumArtQuality.MEDIUM)
        assertEquals("https://lh3.googleusercontent.com/album_art_raw=w544-h544-l90-rj", medium)

        val low = ArtworkUrlResolver.resolveArtworkUrl(rawUrl, AlbumArtQuality.LOW)
        assertEquals("https://lh3.googleusercontent.com/album_art_raw=w256-h256-l90-rj", low)
    }

    @Test
    fun testYouTubeVideoThumbnailCandidatesDescendingQuality() {
        // High quality should prioritize maxresdefault.jpg (1280x720) and hq720.jpg
        val videoUrl = "nodeplayer://youtube/fKopy74weus"
        val highCandidates = ArtworkUrlResolver.resolveYouTubeThumbnailCandidates(videoUrl, AlbumArtQuality.HIGH)

        assertTrue(highCandidates.size >= 4)
        assertEquals("https://i.ytimg.com/vi/fKopy74weus/maxresdefault.jpg", highCandidates[0])
        assertEquals("https://i.ytimg.com/vi/fKopy74weus/hq720.jpg", highCandidates[1])

        // Medium quality should start at hq720.jpg
        val mediumCandidates = ArtworkUrlResolver.resolveYouTubeThumbnailCandidates(videoUrl, AlbumArtQuality.MEDIUM)
        assertEquals("https://i.ytimg.com/vi/fKopy74weus/hq720.jpg", mediumCandidates[0])

        // Low quality should start at hqdefault.jpg
        val lowCandidates = ArtworkUrlResolver.resolveYouTubeThumbnailCandidates(videoUrl, AlbumArtQuality.LOW)
        assertEquals("https://i.ytimg.com/vi/fKopy74weus/hqdefault.jpg", lowCandidates[0])
    }

    @Test
    fun testVideoIdExtractionFormats() {
        assertEquals("abc1234", ArtworkUrlResolver.extractYouTubeVideoId("nodeplayer://youtube/abc1234"))
        assertEquals("abc1234", ArtworkUrlResolver.extractYouTubeVideoId("https://i.ytimg.com/vi/abc1234/hqdefault.jpg"))
        assertEquals("abc1234", ArtworkUrlResolver.extractYouTubeVideoId("https://www.youtube.com/watch?v=abc1234&list=PL123"))
        assertEquals("abc1234", ArtworkUrlResolver.extractYouTubeVideoId("https://youtu.be/abc1234?t=10"))
    }
}
