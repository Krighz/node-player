package com.nodeplayer.network.innertube

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InnerTubeExploreTest {

    @Test
    fun testNormalizeAvatarUrl() {
        // Protocol-relative with size param
        val protoRelative = "//lh3.googleusercontent.com/a/ACg8ocL_example=s96-c"
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocL_example=s256-c-k-c0x00ffffff-no-rj",
            InnerTubeClient.normalizeAvatarUrl(protoRelative)
        )

        // Standard https URL with width param
        val widthParam = "https://lh3.googleusercontent.com/a/ACg8ocL_example=w120"
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocL_example=s256-c-k-c0x00ffffff-no-rj",
            InnerTubeClient.normalizeAvatarUrl(widthParam)
        )

        // URL without size param
        val noParam = "https://lh3.googleusercontent.com/a/ACg8ocL_example"
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocL_example=s256-c-k-c0x00ffffff-no-rj",
            InnerTubeClient.normalizeAvatarUrl(noParam)
        )

        // Already normalized
        val normalized = "https://lh3.googleusercontent.com/a/ACg8ocL_example=s256-c-k-c0x00ffffff-no-rj"
        assertEquals(
            normalized,
            InnerTubeClient.normalizeAvatarUrl(normalized)
        )
    }

    @Test
    fun testFetchAccountInfoWithHandleAndAvatarNormalization() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals(YouTubeClient.WEB_REMIX.clientId, request.headers["X-YouTube-Client-Name"])
            assertEquals(YouTubeClient.WEB_REMIX.clientVersion, request.headers["X-YouTube-Client-Version"])
            val json = """
                {
                  "actions": [
                    { "ignoredAction": {} },
                    {
                      "openPopupAction": {
                        "popup": {
                          "multiPageMenuRenderer": {
                            "header": {
                              "activeAccountHeaderRenderer": {
                                "accountName": {
                                  "simpleText": "Hi-Res Audiophile"
                                },
                                "handleText": {
                                  "runs": [
                                    { "text": "@hires_audiophile" }
                                  ]
                                },
                                "email": {
                                  "simpleText": "audiophile@example.com"
                                },
                                "accountPhoto": {
                                  "thumbnails": [
                                    { "url": "//lh3.googleusercontent.com/a/ACg8ocL_test=s88-c-k-c0x00ffffff-no-rj" }
                                  ]
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  ]
                }
            """.trimIndent()

            respond(
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        client.sessionCookie = "SAPISID=sample_token; LOGIN_INFO=sample_login"

        val accountResult = client.fetchAccountInfo()
        assertTrue(accountResult.isSuccess)
        val account = accountResult.getOrNull()

        assertNotNull(account)
        assertEquals("Hi-Res Audiophile", account.name)
        assertEquals("@hires_audiophile", account.handle)
        assertEquals("audiophile@example.com", account.email)
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocL_test=s256-c-k-c0x00ffffff-no-rj",
            account.avatarUrl
        )
        assertTrue(account.isLoggedIn)
    }

    @Test
    fun testParseMoodAndGenresResponse() {
        val client = InnerTubeClient()
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
                                  "header": {
                                    "gridHeaderRenderer": {
                                      "title": {
                                        "runs": [
                                          { "text": "Moods & moments" }
                                        ]
                                      }
                                    }
                                  },
                                  "items": [
                                    {
                                      "musicNavigationButtonRenderer": {
                                        "buttonText": {
                                          "runs": [
                                            { "text": "Chill" }
                                          ]
                                        },
                                        "solid": {
                                          "leftStripeColor": 4288988671
                                        },
                                        "clickCommand": {
                                          "browseEndpoint": {
                                            "browseId": "FEmusic_moods_and_genres_category",
                                            "params": "ggMPOg1uX1JOQWZFeDByc2Jm"
                                          }
                                        }
                                      }
                                    },
                                    {
                                      "musicNavigationButtonRenderer": {
                                        "buttonText": {
                                          "runs": [
                                            { "text": "Focus" }
                                          ]
                                        },
                                        "solid": {
                                          "leftStripeColor": 4283138883
                                        },
                                        "clickCommand": {
                                          "browseEndpoint": {
                                            "browseId": "FEmusic_moods_and_genres_category",
                                            "params": "ggMPOg1uX2ZvY3VzX3BhcmFtcw"
                                          }
                                        }
                                      }
                                    }
                                  ]
                                }
                              },
                              {
                                "gridRenderer": {
                                  "header": {
                                    "gridHeaderRenderer": {
                                      "title": {
                                        "runs": [
                                          { "text": "Genres" }
                                        ]
                                      }
                                    }
                                  },
                                  "items": [
                                    {
                                      "musicNavigationButtonRenderer": {
                                        "buttonText": {
                                          "runs": [
                                            { "text": "Electronic" }
                                          ]
                                        },
                                        "solid": {
                                          "leftStripeColor": 4278232339
                                        },
                                        "clickCommand": {
                                          "browseEndpoint": {
                                            "browseId": "FEmusic_moods_and_genres_category",
                                            "params": "ggMPOg1uX2VsZWN0cm9uaWNfcGFyYW1z"
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

        val sections = client.parseMoodAndGenresResponse(mockJson)
        assertEquals(2, sections.size)

        val moodsSection = sections[0]
        assertEquals("Moods & moments", moodsSection.title)
        assertEquals(2, moodsSection.items.size)
        assertEquals("Chill", moodsSection.items[0].title)
        assertEquals(4288988671L, moodsSection.items[0].stripeColor)
        assertEquals("FEmusic_moods_and_genres_category", moodsSection.items[0].browseId)
        assertEquals("ggMPOg1uX1JOQWZFeDByc2Jm", moodsSection.items[0].params)

        val genresSection = sections[1]
        assertEquals("Genres", genresSection.title)
        assertEquals(1, genresSection.items.size)
        assertEquals("Electronic", genresSection.items[0].title)
        assertEquals(4278232339L, genresSection.items[0].stripeColor)
        assertEquals("FEmusic_moods_and_genres_category", genresSection.items[0].browseId)
    }

    @Test
    fun testParseCategoryShelvesResponse() {
        val client = InnerTubeClient()
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
                                "musicCarouselShelfRenderer": {
                                  "header": {
                                    "musicCarouselShelfBasicHeaderRenderer": {
                                      "title": {
                                        "runs": [
                                          { "text": "Coffee shop blends" }
                                        ]
                                      }
                                    }
                                  },
                                  "contents": [
                                    {
                                      "musicTwoRowItemRenderer": {
                                        "title": {
                                          "runs": [
                                            { "text": "Coffee Shop Blend" }
                                          ]
                                        },
                                        "subtitle": {
                                          "runs": [
                                            { "text": "Playlist" },
                                            { "text": " • " },
                                            { "text": "YouTube Music" }
                                          ]
                                        },
                                        "navigationEndpoint": {
                                          "browseEndpoint": {
                                            "browseId": "VLRDCLAK5uy_nBE4bLuBHUXWZrF59ZrkPEToKt8M_I3Vc"
                                          }
                                        }
                                      }
                                    },
                                    {
                                      "musicTwoRowItemRenderer": {
                                        "title": {
                                          "runs": [
                                            { "text": "Acoustic Morning" }
                                          ]
                                        },
                                        "subtitle": {
                                          "runs": [
                                            { "text": "50 songs" }
                                          ]
                                        },
                                        "navigationEndpoint": {
                                          "browseEndpoint": {
                                            "browseId": "VLPLacoustic_morning_id"
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

        val playlists = client.parseCategoryShelvesResponse(mockJson)
        assertEquals(2, playlists.size)

        val p1 = playlists[0]
        assertEquals("Coffee Shop Blend", p1.name)
        assertEquals("RDCLAK5uy_nBE4bLuBHUXWZrF59ZrkPEToKt8M_I3Vc", p1.externalId)
        assertEquals("youtube", p1.source)

        val p2 = playlists[1]
        assertEquals("Acoustic Morning", p2.name)
        assertEquals("PLacoustic_morning_id", p2.externalId)
        assertEquals(50, p2.trackCount)
    }

    @Test
    fun testBrowseMoodAndGenresNetworkCall() = runBlocking {
        val mockEngine = MockEngine { _ ->
            val json = """
                {
                  "contents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "gridRenderer": {
                            "header": {
                              "gridHeaderRenderer": {
                                "title": { "runs": [ { "text": "Moods" } ] }
                              }
                            },
                            "items": [
                              {
                                "musicNavigationButtonRenderer": {
                                  "buttonText": { "runs": [ { "text": "Chill" } ] },
                                  "solid": { "leftStripeColor": 123456 },
                                  "clickCommand": {
                                    "browseEndpoint": {
                                      "browseId": "FEmusic_moods_and_genres_category",
                                      "params": "param_test"
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

            respond(
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        val result = client.browseMoodAndGenres()
        assertTrue(result.isSuccess)

        val sections = result.getOrNull()
        assertNotNull(sections)
        assertEquals(1, sections.size)
        assertEquals("Moods", sections[0].title)
        assertEquals(1, sections[0].items.size)
        assertEquals("Chill", sections[0].items[0].title)
        assertEquals(123456L, sections[0].items[0].stripeColor)
    }

    @Test
    fun testBrowseCategoryShelvesNetworkCall() = runBlocking {
        val mockEngine = MockEngine { _ ->
            val json = """
                {
                  "contents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "musicCarouselShelfRenderer": {
                            "contents": [
                              {
                                "musicTwoRowItemRenderer": {
                                  "title": { "runs": [ { "text": "Deep Focus" } ] },
                                  "navigationEndpoint": {
                                    "browseEndpoint": { "browseId": "VLPL_focus_123" }
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
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine)
        val result = client.browseCategoryShelves("FEmusic_moods_and_genres_category", "param_test")
        assertTrue(result.isSuccess)

        val playlists = result.getOrNull()
        assertNotNull(playlists)
        assertEquals(1, playlists.size)
        assertEquals("Deep Focus", playlists[0].name)
        assertEquals("PL_focus_123", playlists[0].externalId)
    }
}
