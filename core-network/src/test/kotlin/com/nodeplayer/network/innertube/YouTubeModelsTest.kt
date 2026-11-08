package com.nodeplayer.network.innertube

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class YouTubeModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun testBrowseResponseDeserialization() {
        val jsonString = """
        {
          "responseContext": {
            "visitorData": "Cgt2aXNpdG9yMTIz"
          },
          "contents": {
            "sectionListRenderer": {
              "contents": [
                {
                  "musicCarouselShelfRenderer": {
                    "header": {
                      "musicCarouselShelfBasicHeaderRenderer": {
                        "title": {
                          "runs": [{"text": "Mixed for you"}]
                        }
                      }
                    },
                    "contents": [
                      {
                        "musicTwoRowItemRenderer": {
                          "title": {
                            "runs": [{"text": "My Mix 1"}]
                          },
                          "subtitle": {
                            "runs": [{"text": "Artist A • Song B"}]
                          },
                          "navigationEndpoint": {
                            "watchEndpoint": {
                              "videoId": "abc12345",
                              "playlistId": "RDAMVMabc12345"
                            }
                          }
                        }
                      }
                    ]
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<BrowseResponse>(jsonString)
        assertNotNull(response.contents)
        val sectionList = response.contents?.sectionListRenderer
        assertNotNull(sectionList)
        assertEquals(1, sectionList?.contents?.size)

        val carousel = sectionList?.contents?.first()?.musicCarouselShelfRenderer
        assertNotNull(carousel)
        assertEquals("Mixed for you", carousel?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.text())
        assertEquals(1, carousel?.contents?.size)

        val item = carousel?.contents?.first()?.musicTwoRowItemRenderer
        assertNotNull(item)
        assertEquals("My Mix 1", item?.title?.text())
        assertEquals("abc12345", item?.navigationEndpoint?.watchEndpoint?.videoId)
    }

    @Test
    fun testSearchResponseDeserialization() {
        val jsonString = """
        {
          "contents": {
            "tabbedSearchResultsRenderer": {
              "tabs": [
                {
                  "tabRenderer": {
                    "title": "Songs",
                    "content": {
                      "sectionListRenderer": {
                        "contents": [
                          {
                            "musicShelfRenderer": {
                              "title": {
                                "runs": [{"text": "Songs"}]
                              },
                              "contents": [
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "playlistItemData": {
                                      "videoId": "song_vid_999"
                                    },
                                    "flexColumns": [
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": {
                                            "runs": [{"text": "Bohemian Rhapsody"}]
                                          }
                                        }
                                      },
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": {
                                            "runs": [{"text": "Queen"}]
                                          }
                                        }
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<SearchResponse>(jsonString)
        assertNotNull(response.contents?.tabbedSearchResultsRenderer)
        val tabs = response.contents?.tabbedSearchResultsRenderer?.tabs
        assertNotNull(tabs)
        assertEquals(1, tabs?.size)

        val firstTab = tabs?.first()
        val shelf = firstTab?.tabRenderer?.content?.sectionListRenderer?.contents?.first()?.musicShelfRenderer
        assertNotNull(shelf)
        assertEquals("Songs", shelf?.title?.text())

        val listItem = shelf?.contents?.first()?.musicResponsiveListItemRenderer
        assertNotNull(listItem)
        assertEquals("song_vid_999", listItem?.videoId)
        assertEquals("Bohemian Rhapsody", listItem?.flexColumns?.get(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.text())
        assertEquals("Queen", listItem?.flexColumns?.get(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.text())
    }

    @Test
    fun testPlayerResponseDeserialization() {
        val jsonString = """
        {
          "playabilityStatus": {
            "status": "OK"
          },
          "playerConfig": {
            "audioConfig": {
              "loudnessDb": -7.5,
              "perceptualLoudnessDb": -9.2
            }
          },
          "videoDetails": {
            "videoId": "play123",
            "title": "Song Title",
            "author": "Song Artist",
            "channelId": "UC12345",
            "lengthSeconds": "215"
          },
          "streamingData": {
            "expiresInSeconds": 21600,
            "adaptiveFormats": [
              {
                "itag": 140,
                "url": "https://rr---sn.googlevideo.com/videoplayback?itag=140",
                "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                "bitrate": 129532,
                "contentLength": 3481230,
                "quality": "tiny",
                "averageBitrate": 128000,
                "approxDurationMs": "215000",
                "loudnessDb": -7.5
              },
              {
                "itag": 251,
                "url": "https://rr---sn.googlevideo.com/videoplayback?itag=251",
                "mimeType": "audio/webm; codecs=\"opus\"",
                "bitrate": 154120,
                "contentLength": 3951230,
                "quality": "tiny",
                "averageBitrate": 145000,
                "approxDurationMs": "215000",
                "loudnessDb": -7.5
              }
            ]
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<PlayerResponse>(jsonString)
        assertEquals("OK", response.playabilityStatus?.status)
        assertEquals(-7.5, response.playerConfig?.audioConfig?.loudnessDb)
        assertEquals("play123", response.videoDetails?.videoId)
        assertEquals("Song Title", response.videoDetails?.title)

        val streamingData = response.streamingData
        assertNotNull(streamingData)
        assertEquals(2, streamingData?.adaptiveFormats?.size)

        val format140 = streamingData?.adaptiveFormats?.first { it.itag == 140 }
        assertNotNull(format140)
        assertTrue(format140!!.isAudio)
        assertEquals(129532, format140.bitrate)
        assertEquals("https://rr---sn.googlevideo.com/videoplayback?itag=140", format140.url)
    }

    @Test
    fun testNextResponseDeserialization() {
        val jsonString = """
        {
          "continuationContents": {
            "playlistPanelContinuation": {
              "playlistId": "RDAMVMradio_seed_1",
              "contents": [
                {
                  "playlistPanelVideoRenderer": {
                    "videoId": "radio_track_1",
                    "title": {
                      "runs": [{"text": "Radio Song 1"}]
                    },
                    "shortBylineText": {
                      "runs": [{"text": "Radio Artist 1"}]
                    },
                    "lengthText": {
                      "runs": [{"text": "3:45"}]
                    },
                    "selected": true
                  }
                },
                {
                  "playlistPanelVideoRenderer": {
                    "videoId": "radio_track_2",
                    "title": {
                      "runs": [{"text": "Radio Song 2"}]
                    },
                    "shortBylineText": {
                      "runs": [{"text": "Radio Artist 2"}]
                    },
                    "lengthText": {
                      "runs": [{"text": "4:12"}]
                    },
                    "selected": false
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<NextResponse>(jsonString)
        val continuation = response.continuationContents?.playlistPanelContinuation
        assertNotNull(continuation)
        assertEquals("RDAMVMradio_seed_1", continuation?.playlistId)
        assertEquals(2, continuation?.contents?.size)

        val firstTrack = continuation?.contents?.first()?.playlistPanelVideoRenderer
        assertNotNull(firstTrack)
        assertEquals("radio_track_1", firstTrack?.videoId)
        assertEquals("Radio Song 1", firstTrack?.title?.text())
        assertEquals("Radio Artist 1", firstTrack?.shortBylineText?.text())
        assertTrue(firstTrack?.selected == true)
    }

    @Test
    fun testDomainModelsInstantiation() {
        val streamInfo = YouTubeStreamInfo(
            videoId = "test_vid",
            title = "Test Title",
            artist = "Test Artist",
            streamUrl = "https://example.com/audio.m4a",
            durationSeconds = 180,
            itag = 140,
            mimeType = "audio/mp4",
            bitrate = 128000,
            contentLength = 3000000,
            loudnessDb = -8.0
        )
        assertEquals("test_vid", streamInfo.videoId)
        assertEquals("Test Title", streamInfo.title)

        val searchResult = YouTubeSearchResult(
            songs = emptyList(),
            albums = emptyList(),
            artists = emptyList()
        )
        assertTrue(searchResult.songs.isEmpty())

        val section = YouTubeSection(
            title = "Quick Picks",
            items = emptyList()
        )
        assertEquals("Quick Picks", section.title)
    }
}
