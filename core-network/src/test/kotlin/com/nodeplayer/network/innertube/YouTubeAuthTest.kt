package com.nodeplayer.network.innertube

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAuthTest {

    @Test
    fun testExtractSapisidFromCookie() {
        val cookie = "VISITOR_INFO1_LIVE=abc; SAPISID=secret_sapisid_123; HSID=xyz; SSID=123"
        val sapisid = YouTubeAuth.extractSapisid(cookie)
        assertEquals("secret_sapisid_123", sapisid)
    }

    @Test
    fun testExtractSecure3PAPISIDFallback() {
        val cookie = "VISITOR_INFO1_LIVE=abc; __Secure-3PAPISID=secure_sapisid_456; HSID=xyz"
        val sapisid = YouTubeAuth.extractSapisid(cookie)
        assertEquals("secure_sapisid_456", sapisid)
    }

    @Test
    fun testSapisidHashGeneration() {
        val sapisid = "test_sapisid"
        val origin = "https://music.youtube.com"
        val timestamp = 1700000000L

        val authHeader = YouTubeAuth.generateSapisidHash(sapisid, origin, timestamp)
        assertTrue(authHeader.startsWith("SAPISIDHASH 1700000000_"))
        assertEquals(authHeader.length, "SAPISIDHASH 1700000000_".length + 40) // 40 hex chars for SHA-1
    }

    @Test
    fun testMaskCookieForLog() {
        val cookie = "SAPISID=super_secret; LOGIN_INFO=top_secret_token; SID=xyz"
        val masked = YouTubeAuth.maskCookieForLog(cookie)
        assertTrue(masked.contains("sapisid=true"))
        assertTrue(masked.contains("loginInfo=true"))
        // Assert secret tokens are NEVER logged
        assertTrue(!masked.contains("super_secret"))
        assertTrue(!masked.contains("top_secret_token"))
    }

    @Test
    fun testInnerTubeClientAuthenticatedRequest() = runBlocking {
        var capturedAuthHeader: String? = null
        var capturedCookieHeader: String? = null

        val mockEngine = MockEngine { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            capturedCookieHeader = request.headers[HttpHeaders.Cookie]

            val json = """
                {
                  "actions": [
                    {
                      "openPopupAction": {
                        "popup": {
                          "multiPageMenuRenderer": {
                            "header": {
                              "activeAccountHeaderRenderer": {
                                "accountName": {
                                  "simpleText": "Audiophile User"
                                },
                                "email": {
                                  "simpleText": "@audiophile"
                                },
                                "accountPhoto": {
                                  "thumbnails": [
                                    { "url": "https://lh3.googleusercontent.com/avatar.jpg" }
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
        client.sessionCookie = "SAPISID=sample_sapisid; LOGIN_INFO=sample_login"

        val accountResult = client.fetchAccountInfo()
        assertTrue(accountResult.isSuccess)
        val account = accountResult.getOrNull()

        assertNotNull(account)
        assertEquals("Audiophile User", account?.name)
        assertEquals("@audiophile", account?.handle)
        assertEquals("https://lh3.googleusercontent.com/avatar.jpg=s256-c-k-c0x00ffffff-no-rj", account?.avatarUrl)

        assertNotNull(capturedAuthHeader)
        assertTrue(capturedAuthHeader!!.startsWith("SAPISIDHASH "))
        assertEquals("SAPISID=sample_sapisid; LOGIN_INFO=sample_login", capturedCookieHeader)
    }
}
