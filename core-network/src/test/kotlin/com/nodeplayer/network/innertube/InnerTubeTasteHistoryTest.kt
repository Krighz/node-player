package com.nodeplayer.network.innertube

import com.nodeplayer.library.taste.HistoryResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InnerTubeTasteHistoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun createClient(mockEngine: MockEngine): InnerTubeClient {
        return InnerTubeClient(engine = mockEngine)
    }

    @Test
    fun testParseHistorySuccess() {
        runBlocking {
            val mockJson = """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "selected": true,
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "musicShelfRenderer": {
                                  "title": { "runs": [{ "text": "Today" }] },
                                  "contents": [
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "flexColumns": [
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": {
                                                "runs": [
                                                  {
                                                    "text": "Starlight",
                                                    "navigationEndpoint": {
                                                      "watchEndpoint": { "videoId": "vid_starlight_1" }
                                                    }
                                                  }
                                                ]
                                              }
                                            }
                                          },
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": {
                                                "runs": [
                                                  { "text": "Muse" }
                                                ]
                                              }
                                            }
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "flexColumns": [
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": {
                                                "runs": [
                                                  {
                                                    "text": "Time",
                                                    "navigationEndpoint": {
                                                      "watchEndpoint": { "videoId": "vid_time_2" }
                                                    }
                                                  }
                                                ]
                                              }
                                            }
                                          },
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": {
                                                "runs": [
                                                  { "text": "Pink Floyd" }
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

            val client = createClient(mockEngine)
            val adapter = InnerTubeTasteHistoryAdapter(client)

            val result = adapter.recentHistory(limit = 10)
            assertIs<HistoryResult.Success>(result)
            assertEquals(2, result.entries.size)
            assertEquals("vid_starlight_1", result.entries[0].videoId)
            assertEquals("Starlight", result.entries[0].title)
            assertEquals("Muse", result.entries[0].artistName)
            assertEquals("vid_time_2", result.entries[1].videoId)
        }
    }

    @Test
    fun testParseHistoryPaused() {
        runBlocking {
            val pausedJson = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "itemSectionRenderer": {
                        "contents": [
                          {
                            "messageRenderer": {
                              "text": {
                                "runs": [{ "text": "Your YouTube watch history is paused" }]
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

            val mockEngine = MockEngine { _ ->
                respond(
                    content = pausedJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val client = createClient(mockEngine)
            val adapter = InnerTubeTasteHistoryAdapter(client)

            val result = adapter.recentHistory(limit = 10)
            assertIs<HistoryResult.Paused>(result)
        }
    }

    @Test
    fun testParseHistoryEmpty() {
        runBlocking {
            val emptyJson = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": []
                }
              }
            }
            """.trimIndent()

            val mockEngine = MockEngine { _ ->
                respond(
                    content = emptyJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val client = createClient(mockEngine)
            val adapter = InnerTubeTasteHistoryAdapter(client)

            val result = adapter.recentHistory(limit = 10)
            assertIs<HistoryResult.Empty>(result)
        }
    }

    @Test
    fun testNotSignedInMapping() {
        runBlocking {
            val mockEngine = MockEngine { _ ->
                respond(
                    content = "Unauthorized access",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }

            val client = createClient(mockEngine)
            val adapter = InnerTubeTasteHistoryAdapter(client)

            val result = adapter.recentHistory(limit = 10)
            assertIs<HistoryResult.NotSignedIn>(result)
        }
    }
}
