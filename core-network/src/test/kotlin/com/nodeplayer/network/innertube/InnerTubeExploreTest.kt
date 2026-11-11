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
        val mockEngine = MockEngine { _ ->
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
}
