package com.nodeplayer.music.sync

import com.nodeplayer.music.auth.GoogleAuthManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class GoogleAuthManagerTest {

    @Test
    fun testGenerateCodeVerifier_lengthAndCharacterSet() {
        val verifier = GoogleAuthManager.generateCodeVerifier(64)
        assertEquals(64, verifier.length)
        val validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        assertTrue(verifier.all { it in validChars })
    }

    @Test
    fun testGenerateCodeChallenge_validBase64UrlWithoutPadding() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge = GoogleAuthManager.generateCodeChallenge(verifier)
        assertFalse(challenge.contains("="))
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
        assertTrue(challenge.isNotBlank())
    }

    @Test
    fun testCreateAuthorizationUrl_strictlyUsesAppDataScope() {
        val testClientId = "123456789-test.apps.googleusercontent.com"
        val authUrl = GoogleAuthManager.createAuthorizationUrl(clientId = testClientId)
        assertTrue(authUrl != null && authUrl.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        val nonNullUrl = authUrl!!
        
        // Ensure strict drive.appdata scope ONLY
        assertTrue(nonNullUrl.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.appdata"))
        assertFalse("Must never request full Drive scope", nonNullUrl.contains("https://www.googleapis.com/auth/drive&"))
        assertFalse("Must never request full Drive file scope", nonNullUrl.contains("https://www.googleapis.com/auth/drive.file"))
        
        // Ensure redirect uri is valid reverse-DNS scheme
        assertTrue(nonNullUrl.contains("redirect_uri=com.googleusercontent.apps.123456789-test%3A%2Foauth2redirect"))
        assertTrue(nonNullUrl.contains("access_type=offline"))
        assertTrue(nonNullUrl.contains("code_challenge_method=S256"))
    }

    @Test
    fun testBuildConfigClientId_injectedFromLocalProperties() {
        // BuildConfig.GDRIVE_CLIENT_ID must match the debug ID configured in local.properties
        val buildConfigClientId = com.nodeplayer.music.BuildConfig.GDRIVE_CLIENT_ID
        assertTrue("BuildConfig.GDRIVE_CLIENT_ID must not be blank", buildConfigClientId.isNotBlank())
        assertTrue("BuildConfig.GDRIVE_CLIENT_ID must end with apps.googleusercontent.com", buildConfigClientId.endsWith(".apps.googleusercontent.com"))
    }
}
