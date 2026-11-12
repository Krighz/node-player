package com.nodeplayer.network.innertube

import com.nodeplayer.network.cache.HomeFeedDiskCache
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HomeFeedLoadingTest {
    @Test
    fun displaysHomeBeforeExploreAndChartsFinish() = runTest {
        val homeShown = CompletableDeferred<Unit>()
        val finishOtherShelves = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            val body = (request.body as TextContent).text
            val response = if (body.contains("FEmusic_home")) {
                """{"contents":{"sectionListRenderer":{"contents":[{"musicCarouselShelfRenderer":{
                  "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"simpleText":"Top 50 Charts India"}}},
                  "contents":[{"musicTwoRowItemRenderer":{"title":{"simpleText":"First song"},"navigationEndpoint":{"watchEndpoint":{"videoId":"first_song"}}}}]
                }}]}}}"""
            } else {
                finishOtherShelves.await()
                "{}"
            }
            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        InnerTubeClient(engine = engine).use { client ->
            val load = async {
                client.getCombinedHomeFeed {
                    assertEquals("First song", it.single().items.single().title)
                    homeShown.complete(Unit)
                }.getOrThrow()
            }
            homeShown.await()
            assertFalse(load.isCompleted)
            finishOtherShelves.complete(Unit)
            assertEquals("First song", load.await().single().items.single().title)
        }
    }

    @Test
    fun sharedFeedCacheReusesMemoryAndKeepsAccountsSeparate() {
        val directory = Files.createTempDirectory("node-feed-cache").toFile()
        val oldDirectory = HomeFeedDiskCache.defaultCacheDir
        try {
            HomeFeedDiskCache.defaultCacheDir = directory
            val key = "test:${directory.name}"
            val first = HomeFeedDiskCache.shared(key)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            first.save(listOf(YouTubeSection("Saved shelf", emptyList())))
            assertSame(first, HomeFeedDiskCache.shared(key))
            assertTrue(first.isValid(300_000L))
            assertTrue(HomeFeedDiskCache.shared("$key:another-account").sectionsState.value.isEmpty())
            assertEquals("Saved shelf", HomeFeedDiskCache(cacheDir = directory, cacheKey = key).sectionsState.value.single().title)
        } finally {
            HomeFeedDiskCache.defaultCacheDir = oldDirectory
            directory.deleteRecursively()
        }
    }
}
