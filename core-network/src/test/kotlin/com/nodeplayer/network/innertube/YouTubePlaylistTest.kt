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

        val mockEngine = MockEngine { request ->
            val body = (request.body as io.ktor.http.content.TextContent).text
            val browseId = kotlinx.serialization.json.Json.parseToJsonElement(body)
                .let { it as kotlinx.serialization.json.JsonObject }["browseId"].toString()
            assertEquals("\"FEmusic_liked_playlists\"", browseId)
            assertEquals("/youtubei/v1/browse", request.url.encodedPath)
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
    @Test
    fun librarySyncReadsTwoColumnAndContinuationPages() = runBlocking {
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = (request.body as io.ktor.http.content.TextContent).text + request.url.toString()
            requests.add(body)
            val response = if (body.contains("library_next")) {
                """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
                  {"musicTwoRowItemRenderer":{"title":{"simpleText":"Second playlist"},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLsecond"}}}}
                ]}}]}"""
            } else {
                """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                  {"gridRenderer":{"items":[
                    {"musicTwoRowItemRenderer":{"title":{"simpleText":"First playlist"},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLfirst"}}}},
                    {"musicTwoRowItemRenderer":{"title":{"simpleText":"Create playlist"},"navigationEndpoint":{"browseEndpoint":{"browseId":"FEmusic_new_playlist"}}}}
                  ],"continuations":[{"nextContinuationData":{"continuation":"library_next"}}]}}
                ]}}}}]}}}"""
            }
            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        InnerTubeClient(engine = engine).use { client ->
            client.sessionCookie = "SAPISID=synthetic"
            assertEquals(listOf("PLfirst", "PLsecond"), client.fetchUserPlaylists().getOrThrow().map { it.id })
            assertEquals(2, requests.size)
            assertTrue(requests.first().contains("FEmusic_liked_playlists"))
            assertTrue(requests.last().contains("ctoken=library_next"))
        }
    }

    @Test
    fun playlistSyncIncludesEveryPageAndRejectsInvalidSnapshots() = runBlocking {
        var calls = 0
        val engine = MockEngine { request ->
            val body = (request.body as io.ktor.http.content.TextContent).text + request.url.toString()
            calls++
            val response = when {
                body.contains("PLinvalid") -> """{"contents":{"messageRenderer":{"text":"Unavailable"}}}"""
                body.contains("tracks_next") -> """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[
                  {"musicResponsiveListItemRenderer":{"navigationEndpoint":{"watchEndpoint":{"videoId":"song_second"}},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Second song"}]}}}]}}
                ]}}}"""
                else -> """{"contents":{"sectionListRenderer":{"contents":[{"musicPlaylistShelfRenderer":{"contents":[
                  {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"song_first"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"First song"}]}}}]}}
                ],"continuations":[{"nextContinuationData":{"continuation":"tracks_next"}}]}}]}}}"""
            }
            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        InnerTubeClient(engine = engine).use { client ->
            assertEquals(listOf("First song", "Second song"), client.fetchPlaylistTracks("PLvalid").getOrThrow().map { it.title })
            assertEquals(2, calls)
            assertTrue(client.fetchPlaylistTracks("PLinvalid").isFailure)
        }
    }

    @Test
    fun repeatedContinuationFailsInsteadOfCommittingPartialPlaylist() = runBlocking {
        val engine = MockEngine {
            respond("""{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[],"continuations":[{"nextContinuationData":{"continuation":"same"}}]}}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        InnerTubeClient(engine = engine).use { client ->
            assertTrue(client.fetchPlaylistTracks("PLloop").isFailure)
        }
    }

    @Test
    fun libraryAndCategoriesReadPlaylistLinksFromTitleRuns() = runBlocking {
        val card = """{"musicTwoRowItemRenderer":{
          "title":{"runs":[{"text":"peace","navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLpeace"}}}]},
          "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/peace.jpg"}]}}}
        }}"""
        val response = """{"contents":{"sectionListRenderer":{"contents":[{"gridRenderer":{"items":[$card]}}]}}}"""
        val engine = MockEngine { respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        InnerTubeClient(engine = engine).use { client ->
            client.sessionCookie = "SAPISID=synthetic"
            assertEquals("peace", client.fetchUserPlaylists().getOrThrow().single().title)
            assertEquals("PLpeace", client.fetchUserPlaylists().getOrThrow().single().id)
            val category = client.parseCategoryShelvesResponse(response).single()
            assertEquals("PLpeace", category.externalId)
            assertEquals("https://example.com/peace.jpg", category.thumbnailUrl)
        }
    }

}
