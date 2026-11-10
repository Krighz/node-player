package com.nodeplayer.network.innertube

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaylistTest {

    @Test
    fun testParseUserPlaylistsResponse() = runBlocking {
        val mockJson = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [
                {
                  "tabRenderer": {
                    "content": {
                      "sectionListRenderer": {
                        "contents": [
                          {
                            "gridRenderer": {
                              "items": [
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": {
                                      "runs": [
                                        {
                                          "text": "Audiophile Chillout"
                                        }
                                      ]
                                    },
                                    "subtitle": {
                                      "runs": [
                                        {
                                          "text": "By Node User"
                                        },
                                        {
                                          "text": " • "
                                        },
                                        {
                                          "text": "24 tracks"
                                        }
                                      ]
                                    },
                                    "navigationEndpoint": {
                                      "browseEndpoint": {
                                        "browseId": "VLPLtest_playlist_123"
                                      }
                                    },
                                    "thumbnailRenderer": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [
                                            {
                                              "url": "https://lh3.googleusercontent.com/test.jpg"
                                            }
                                          ]
                                        }
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
                }
              ]
            }
          }
        }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = mockJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        client.sessionCookie = "SAPISID=test123; SID=abc"

        val playlists = client.fetchUserPlaylists().getOrNull()
        assertNotNull(playlists)
        assertEquals(1, playlists!!.size)
        val p = playlists[0]
        assertEquals("PLtest_playlist_123", p.id)
        assertEquals("Audiophile Chillout", p.title)
        assertEquals("By Node User", p.author)
        assertEquals(24, p.trackCount)
        assertEquals("https://lh3.googleusercontent.com/test.jpg", p.thumbnailUrl)
    }

    @Test
    fun testParsePlaylistTracksResponse() = runBlocking {
        val mockJson = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [
                {
                  "tabRenderer": {
                    "content": {
                      "sectionListRenderer": {
                        "contents": [
                          {
                            "musicPlaylistShelfRenderer": {
                              "contents": [
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "playlistItemData": {
                                      "videoId": "vid_abc_456"
                                    },
                                    "flexColumns": [
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": {
                                            "runs": [
                                              {
                                                "text": "Midnight City"
                                              }
                                            ]
                                          }
                                        }
                                      },
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": {
                                            "runs": [
                                              {
                                                "text": "M83"
                                              },
                                              {
                                                "text": " • "
                                              },
                                              {
                                                "text": "Hurry Up, We're Dreaming"
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    ],
                                    "fixedColumns": [
                                      {
                                        "musicResponsiveListItemFixedColumnRenderer": {
                                          "text": {
                                            "runs": [
                                              {
                                                "text": "4:03"
                                              }
                                            ]
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

        val mockEngine = MockEngine { _ ->
            respond(
                content = mockJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        val tracks = client.fetchPlaylistTracks("PLtest_playlist_123").getOrNull()
        assertNotNull(tracks)
        assertEquals(1, tracks!!.size)
        val t = tracks[0]
        assertEquals("Midnight City", t.title)
        assertEquals("M83", t.artist)
        assertEquals("Hurry Up, We're Dreaming", t.album)
        assertEquals(243000L, t.durationMs)
        assertEquals("nodeplayer://youtube/vid_abc_456", t.path)
    }
}
