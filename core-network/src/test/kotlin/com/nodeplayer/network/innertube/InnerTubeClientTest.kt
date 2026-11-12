package com.nodeplayer.network.innertube

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeClientTest {

    @Test
    fun signedInTwoColumnFeedAndSelectedTabAreParsed() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<BrowseResponse>("""{
          "contents":{"twoColumnBrowseResultsRenderer":{"tabs":[
            {"tabRenderer":{"content":{"sectionListRenderer":{"contents":[]}}}},
            {"tabRenderer":{"selected":true,"content":{"sectionListRenderer":{"contents":[
              {"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"simpleText":"For you"}}},"contents":[
                {"musicTwoRowItemRenderer":{"title":{"simpleText":"Song"},"navigationEndpoint":{"watchEndpoint":{"videoId":"song-id"}}}},
                {"musicTwoRowItemRenderer":{"title":{"simpleText":"Your mix"},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLRDmix"}}}}
              ]}}
            ]}}}}
          ]}}}
        """)
        InnerTubeClient().use { client ->
            val section = client.parseBrowseSections(response).single()
            assertEquals("For you", section.title)
            assertEquals("Song", section.items.single().title)
            assertEquals("RDmix", section.playlists.single().id)
        }
    }

    @Test
    fun testGetHomeRecommendationsMapping() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://music.youtube.com/youtubei/v1/browse", request.url.toString())
            assertEquals("67", request.headers["X-YouTube-Client-Name"])
            assertEquals("1.20260213.01.00", request.headers["X-YouTube-Client-Version"])

            val responseBody = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "musicCarouselShelfRenderer": {
                        "header": {
                          "musicCarouselShelfBasicHeaderRenderer": {
                            "title": { "runs": [{"text": "Forgotten Favorites"}] }
                          }
                        },
                        "contents": [
                          {
                            "musicTwoRowItemRenderer": {
                              "title": { "runs": [{"text": "Comfortably Numb"}] },
                              "subtitle": { "runs": [{"text": "Pink Floyd • The Wall"}] },
                              "navigationEndpoint": {
                                "watchEndpoint": { "videoId": "pink12345" }
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

            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        val result = client.getHomeRecommendations()
        assertTrue(result.isSuccess)

        val sections = result.getOrThrow()
        assertEquals(1, sections.size)
        val section = sections.first()
        assertEquals("Forgotten Favorites", section.title)
        assertEquals(1, section.items.size)

        val track = section.items.first()
        assertEquals("Comfortably Numb", track.title)
        assertEquals("Pink Floyd", track.artist)
        assertEquals("The Wall", track.album)
        assertEquals("nodeplayer://youtube/pink12345", track.path)
        assertTrue(track.id > 0)
        client.close()
    }

    @Test
    fun homeRequestsUseCurrentSessionAndKeepPlaylistCards() = runTest {
        val cookies = mutableListOf<String?>()
        val engine = MockEngine { request ->
            assertEquals(listOf("https://music.youtube.com"), request.headers.getAll("Origin"))
            assertEquals(listOf("https://music.youtube.com/"), request.headers.getAll("Referer"))
            assertEquals("music.youtube.com", request.url.host)
            assertEquals(listOf(YouTubeClient.WEB_REMIX.clientId), request.headers.getAll("X-YouTube-Client-Name"))
            assertEquals(listOf(YouTubeClient.WEB_REMIX.clientVersion), request.headers.getAll("X-YouTube-Client-Version"))
            assertEquals(listOf(YouTubeClient.WEB_REMIX.userAgent), request.headers.getAll("User-Agent"))
            cookies.add(request.headers[HttpHeaders.Cookie])
            if (cookies.size == 2) assertTrue(request.headers[HttpHeaders.Authorization]?.startsWith("SAPISIDHASH ") == true)
            respond(
                """{"contents":{"sectionListRenderer":{"contents":[{"musicCarouselShelfRenderer":{
                  "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Made for you"}]}}},
                  "contents":[
                    {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Your mix"}]},"subtitle":{"runs":[{"text":"YouTube Music"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL123"}},"thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/cover.jpg"}]}}}}},
                    {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Song"}]},"navigationEndpoint":{"watchEndpoint":{"videoId":"song123","playlistId":"RD123"}}}}
                  ]}}]}}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = InnerTubeClient(engine = engine)
        client.getHomeRecommendations().getOrThrow()
        client.sessionCookie = "SAPISID=synthetic; LOGIN_INFO=synthetic"
        val section = client.getHomeRecommendations().getOrThrow().single()
        assertEquals(null, cookies.first())
        assertEquals(client.sessionCookie, cookies.last())
        assertEquals("PL123", section.playlists.single().id)
        assertEquals("https://example.com/cover.jpg", section.playlists.single().thumbnailUrl)
        assertEquals("nodeplayer://youtube/song123", section.items.single().path)
        client.close()
    }

    @Test
    fun testSearchEndpointAndCategorization() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://music.youtube.com/youtubei/v1/search", request.url.toString())
            val responseBody = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "musicShelfRenderer": {
                                  "contents": [
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "playlistItemData": { "videoId": "song_001" },
                                        "flexColumns": [
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": { "runs": [{"text": "Stairway to Heaven"}] }
                                            }
                                          },
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": { "runs": [{"text": "Led Zeppelin"}, {"text": " • "}, {"text": "Led Zeppelin IV"}] }
                                            }
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "playlistItemData": { "videoId": "album_001" },
                                        "flexColumns": [
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": { "runs": [{"text": "Led Zeppelin IV"}] }
                                            }
                                          },
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": { "runs": [{"text": "Album • Led Zeppelin"}] }
                                            }
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "playlistItemData": { "videoId": "artist_001" },
                                        "flexColumns": [
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": { "runs": [{"text": "Led Zeppelin"}] }
                                            }
                                          },
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": { "runs": [{"text": "Artist • 5M Subscribers"}] }
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

            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        val result = client.search("Led Zeppelin")
        assertTrue(result.isSuccess)

        val searchResult = result.getOrThrow()
        assertEquals(1, searchResult.songs.size)
        assertEquals("Stairway to Heaven", searchResult.songs.first().title)
        assertEquals("nodeplayer://youtube/song_001", searchResult.songs.first().path)

        assertEquals(1, searchResult.albums.size)
        assertEquals("Led Zeppelin IV", searchResult.albums.first().title)

        assertEquals(1, searchResult.artists.size)
        assertEquals("Led Zeppelin", searchResult.artists.first().title)
        client.close()
    }

    @Test
    fun testGetPlayerStreamInfoExtraction() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.toString().contains("sw.js_data")) {
                respond(
                    content = ")]}'\n[[null,null,[[null,null,null,null,null,null,null,null,null,null,null,null,null,\"VISITOR_123\"]]]]",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                assertEquals("https://music.youtube.com/youtubei/v1/player", request.url.toString())
                val responseBody = """
                {
                  "playabilityStatus": {
                    "status": "OK"
                  },
                  "playerConfig": {
                    "audioConfig": {
                      "loudnessDb": -6.8
                    }
                  },
                  "videoDetails": {
                    "videoId": "stream_123",
                    "title": "Hotel California",
                    "author": "Eagles",
                    "lengthSeconds": "391"
                  },
                  "streamingData": {
                    "adaptiveFormats": [
                      {
                        "itag": 140,
                        "url": "https://googlevideo.com/playback?itag=140",
                        "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                        "bitrate": 128000,
                        "contentLength": 6250000,
                        "approxDurationMs": "391000",
                        "loudnessDb": -6.8
                      },
                      {
                        "itag": 251,
                        "url": "https://googlevideo.com/playback?itag=251",
                        "mimeType": "audio/webm; codecs=\"opus\"",
                        "bitrate": 160000,
                        "contentLength": 7100000,
                        "approxDurationMs": "391000",
                        "loudnessDb": -6.8
                      }
                    ]
                  }
                }
                """.trimIndent()

                respond(
                    content = responseBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = InnerTubeClient(engine = mockEngine)
        val result = client.getPlayer("stream_123")
        assertTrue(result.isSuccess)

        val streamInfo = result.getOrThrow()
        assertEquals("stream_123", streamInfo.videoId)
        assertEquals("Hotel California", streamInfo.title)
        assertEquals("Eagles", streamInfo.artist)
        assertEquals(391L, streamInfo.durationSeconds)
        // Format with highest bitrate is itag 251 (160000)
        assertEquals(251, streamInfo.itag)
        assertEquals("https://googlevideo.com/playback?itag=251", streamInfo.streamUrl)
        assertEquals("audio/webm; codecs=\"opus\"", streamInfo.mimeType)
        assertEquals(160000L, streamInfo.bitrate)
        assertEquals(7100000L, streamInfo.contentLength)
        assertEquals(-6.8, streamInfo.loudnessDb ?: 0.0, 0.01)
        client.close()
    }

    @Test
    fun testGetNextRadioMapping() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://music.youtube.com/youtubei/v1/next", request.url.toString())
            val responseBody = """
            {
              "continuationContents": {
                "playlistPanelContinuation": {
                  "contents": [
                    {
                      "playlistPanelVideoRenderer": {
                        "videoId": "radio_001",
                        "title": { "runs": [{"text": "Sultans of Swing"}] },
                        "shortBylineText": { "runs": [{"text": "Dire Straits"}] },
                        "longBylineText": { "runs": [{"text": "Dire Straits • Dire Straits"}] },
                        "lengthText": { "runs": [{"text": "5:48"}] }
                      }
                    },
                    {
                      "playlistPanelVideoRenderer": {
                        "videoId": "radio_002",
                        "title": { "runs": [{"text": "Money for Nothing"}] },
                        "shortBylineText": { "runs": [{"text": "Dire Straits"}] },
                        "lengthText": { "runs": [{"text": "4:05"}] }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()

            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        val result = client.getNextRadio("radio_001")
        assertTrue(result.isSuccess)

        val queue = result.getOrThrow()
        assertEquals(2, queue.size)

        val track1 = queue[0]
        assertEquals("Sultans of Swing", track1.title)
        assertEquals("Dire Straits", track1.artist)
        assertEquals("Dire Straits", track1.album)
        assertEquals(348000L, track1.durationMs)
        assertEquals("nodeplayer://youtube/radio_001", track1.path)

        val track2 = queue[1]
        assertEquals("Money for Nothing", track2.title)
        assertEquals("Dire Straits", track2.artist)
        assertEquals(245000L, track2.durationMs)
        assertEquals("nodeplayer://youtube/radio_002", track2.path)
        client.close()
    }

    @Test
    fun testErrorHandlingReturnsResultFailure() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        val result = client.getPlayer("err_123")
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        client.close()
    }

    @Test
    fun testGetCombinedHomeFeedWithDeduplication() = runTest {
        val mockEngine = MockEngine { request ->
            val bodyString = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
            val browseId = if (bodyString.contains("FEmusic_home")) "FEmusic_home" else "FEmusic_explore"
            val responseBody = if (browseId == "FEmusic_home") {
                """
                {
                  "contents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "musicCarouselShelfRenderer": {
                            "header": {
                              "musicCarouselShelfBasicHeaderRenderer": {
                                "title": { "runs": [{"text": "Quick Picks"}] }
                              }
                            },
                            "contents": [
                              {
                                "musicTwoRowItemRenderer": {
                                  "title": { "runs": [{"text": "Track A"}] },
                                  "subtitle": { "runs": [{"text": "Artist A"}] },
                                  "navigationEndpoint": {
                                    "watchEndpoint": { "videoId": "track_a" }
                                  }
                                }
                              }
                            ]
                          }
                        },
                        {
                          "musicCarouselShelfRenderer": {
                            "header": {
                              "musicCarouselShelfBasicHeaderRenderer": {
                                "title": { "runs": [{"text": "Trending Now"}] }
                              }
                            },
                            "contents": [
                              {
                                "musicTwoRowItemRenderer": {
                                  "title": { "runs": [{"text": "Track B"}] },
                                  "subtitle": { "runs": [{"text": "Artist B"}] },
                                  "navigationEndpoint": {
                                    "watchEndpoint": { "videoId": "track_b" }
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
            } else {
                """
                {
                  "contents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "musicCarouselShelfRenderer": {
                            "header": {
                              "musicCarouselShelfBasicHeaderRenderer": {
                                "title": { "runs": [{"text": "trending now"}] }
                              }
                            },
                            "contents": [
                              {
                                "musicTwoRowItemRenderer": {
                                  "title": { "runs": [{"text": "Track B Duplicate"}] },
                                  "subtitle": { "runs": [{"text": "Artist B"}] },
                                  "navigationEndpoint": {
                                    "watchEndpoint": { "videoId": "track_b2" }
                                  }
                                }
                              }
                            ]
                          }
                        },
                        {
                          "musicCarouselShelfRenderer": {
                            "header": {
                              "musicCarouselShelfBasicHeaderRenderer": {
                                "title": { "runs": [{"text": "New Releases"}] }
                              }
                            },
                            "contents": [
                              {
                                "musicTwoRowItemRenderer": {
                                  "title": { "runs": [{"text": "Track C"}] },
                                  "subtitle": { "runs": [{"text": "Artist C"}] },
                                  "navigationEndpoint": {
                                    "watchEndpoint": { "videoId": "track_c" }
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
            }

            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        val result = client.getCombinedHomeFeed()
        assertTrue(result.isSuccess)

        val sections = result.getOrThrow()
        // Quick Picks + Trending Now (from home) + New Releases (from explore, with duplicate 'trending now' filtered out)
        assertEquals(3, sections.size)
        assertEquals("Quick Picks", sections[0].title)
        assertEquals("Trending Now", sections[1].title)
        assertEquals("New Releases", sections[2].title)
        assertEquals("Track A", sections[0].items.first().title)
        assertEquals("Track B", sections[1].items.first().title)
        assertEquals("Track C", sections[2].items.first().title)

        client.close()
    }
}
