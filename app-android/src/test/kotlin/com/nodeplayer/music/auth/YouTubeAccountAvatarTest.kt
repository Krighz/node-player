package com.nodeplayer.music.auth

import com.nodeplayer.model.AuthState
import com.nodeplayer.model.YouTubeAccount
import com.nodeplayer.network.innertube.InnerTubeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for YouTube account avatar URL normalization, session hydration,
 * and authentication lifecycle transitions per D-13, D-14, and D-15.
 */
class YouTubeAccountAvatarTest {

    @Test
    fun testAvatarUrlNormalizationProtocolRelative() {
        // Protocol-relative URLs must gain https: prefix and =s256 sizing per D-13
        val protoRelative = "//lh3.googleusercontent.com/a/ABC"
        val normalized = InnerTubeClient.normalizeAvatarUrl(protoRelative)
        assertEquals("https://lh3.googleusercontent.com/a/ABC=s256-c-k-c0x00ffffff-no-rj", normalized)
    }

    @Test
    fun testAvatarUrlNormalizationResizeExistingParam() {
        // Existing width/size param must be replaced with =s256-c-k-c0x00ffffff-no-rj
        val widthParam = "https://lh3.googleusercontent.com/a/ABC=w120-h120-p"
        val normalized = InnerTubeClient.normalizeAvatarUrl(widthParam)
        assertEquals("https://lh3.googleusercontent.com/a/ABC=s256-c-k-c0x00ffffff-no-rj", normalized)

        val sParam = "https://lh3.googleusercontent.com/a/ABC=s120"
        val normalizedS = InnerTubeClient.normalizeAvatarUrl(sParam)
        assertEquals("https://lh3.googleusercontent.com/a/ABC=s256-c-k-c0x00ffffff-no-rj", normalizedS)
    }

    @Test
    fun testAvatarUrlNormalizationAlreadyNormalized() {
        val alreadyNormalized = "https://lh3.googleusercontent.com/a/ABC=s256-c-k-c0x00ffffff-no-rj"
        val normalized = InnerTubeClient.normalizeAvatarUrl(alreadyNormalized)
        assertEquals(alreadyNormalized, normalized)
    }

    @Test
    fun testAvatarUrlNormalizationNullOrBlank() {
        assertNull(InnerTubeClient.normalizeAvatarUrl(null))
        assertNull(InnerTubeClient.normalizeAvatarUrl(""))
        assertNull(InnerTubeClient.normalizeAvatarUrl("   "))
    }

    @Test
    fun testAvatarUrlNormalizationWithQueryParams() {
        val urlWithQuery = "https://lh3.googleusercontent.com/a/ABC?authuser=0"
        val normalized = InnerTubeClient.normalizeAvatarUrl(urlWithQuery)
        assertEquals("https://lh3.googleusercontent.com/a/ABC=s256-c-k-c0x00ffffff-no-rj?authuser=0", normalized)
    }

    @Test
    fun testAccountModelChannelHandleAliasAndAvatarIntegrity() {
        val rawAvatar = "//lh3.googleusercontent.com/a/XYZ"
        val normalizedAvatar = InnerTubeClient.normalizeAvatarUrl(rawAvatar)

        val account = YouTubeAccount(
            id = "@audiophile",
            name = "Audio Phile",
            handle = "@audiophile",
            avatarUrl = normalizedAvatar,
            email = "audiophile@example.com",
            isLoggedIn = true
        )

        assertEquals("@audiophile", account.channelHandle)
        assertEquals("https://lh3.googleusercontent.com/a/XYZ=s256-c-k-c0x00ffffff-no-rj", account.avatarUrl)
        assertTrue(account.isLoggedIn)
    }

    @Test
    fun testAuthStateTransitions() {
        // Verify state representation and immediate hydration pattern
        val loggedOut: AuthState = AuthState.LoggedOut
        assertTrue(loggedOut is AuthState.LoggedOut)

        val account = YouTubeAccount(
            id = "user123",
            name = "Test User",
            handle = "@testuser",
            avatarUrl = InnerTubeClient.normalizeAvatarUrl("//lh3.googleusercontent.com/a/USER"),
            email = "user@gmail.com",
            isLoggedIn = true
        )

        val loggedIn: AuthState = AuthState.LoggedIn(account)
        assertTrue(loggedIn is AuthState.LoggedIn)
        val hydrated = (loggedIn as AuthState.LoggedIn).account
        assertEquals("@testuser", hydrated.channelHandle)
        assertEquals("https://lh3.googleusercontent.com/a/USER=s256-c-k-c0x00ffffff-no-rj", hydrated.avatarUrl)
    }
}
