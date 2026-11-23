package com.nodeplayer.network.downloader

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadHttpSourceTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "np_http_dl_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSuccessfulDownloadFullContent() = runBlocking {
        val payload = "AudioContentDataPayload1234567890".toByteArray()
        val mockEngine = MockEngine { _ ->
            respond(
                content = payload,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("audio/webm"),
                    HttpHeaders.ContentLength to listOf(payload.size.toString())
                )
            )
        }

        val httpSource = DownloadHttpSource(engine = mockEngine)
        val partFile = File(tempDir, "test.opus.part")

        var reportedBytes = 0L
        val res = httpSource.downloadToFile(
            url = "https://example.com/audio.opus",
            destinationPartFile = partFile,
            expectedLength = payload.size.toLong()
        ) { downloaded, _, _ ->
            reportedBytes = downloaded
        }

        assertTrue(res.isSuccess)
        assertEquals(payload.size.toLong(), res.getOrThrow())
        assertEquals(payload.size.toLong(), reportedBytes)
        assertEquals(payload.size.toLong(), partFile.length())
    }

    @Test
    fun testRangeResumePartialContent() = runBlocking {
        val initialBytes = "InitialPrefixBytes_".toByteArray()
        val partFile = File(tempDir, "resume.opus.part")
        partFile.writeBytes(initialBytes)

        val remainingPayload = "ResumedSuffixBytes!".toByteArray()
        var observedRangeHeader: String? = null

        val mockEngine = MockEngine { request ->
            observedRangeHeader = request.headers[HttpHeaders.Range]
            respond(
                content = remainingPayload,
                status = HttpStatusCode.PartialContent,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("audio/webm"),
                    HttpHeaders.ContentLength to listOf(remainingPayload.size.toString()),
                    HttpHeaders.ContentRange to listOf("bytes ${initialBytes.size}-${initialBytes.size + remainingPayload.size - 1}/${initialBytes.size + remainingPayload.size}")
                )
            )
        }

        val httpSource = DownloadHttpSource(engine = mockEngine)

        val res = httpSource.downloadToFile(
            url = "https://example.com/audio.opus",
            destinationPartFile = partFile
        ) { _, _, _ -> }

        assertTrue(res.isSuccess)
        assertEquals("bytes=${initialBytes.size}-", observedRangeHeader)
        val totalExpected = (initialBytes.size + remainingPayload.size).toLong()
        assertEquals(totalExpected, partFile.length())
    }

    @Test
    fun testHttp403ReturnsStreamExpiredException() = runBlocking {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Forbidden",
                status = HttpStatusCode.Forbidden
            )
        }

        val httpSource = DownloadHttpSource(engine = mockEngine)
        val partFile = File(tempDir, "forbidden.part")

        val res = httpSource.downloadToFile(
            url = "https://example.com/expired_stream",
            destinationPartFile = partFile
        ) { _, _, _ -> }

        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is StreamExpiredException)
    }
}
