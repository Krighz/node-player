package com.nodeplayer.network.spotify

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Spotify OAuth 2.0 PKCE helper (RFC 7636).
 * Pure Kotlin — zero Android framework dependencies.
 */
object SpotifyAuth {

    private const val SPOTIFY_AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
    const val SPOTIFY_TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
    const val DEFAULT_SCOPES = "playlist-read-private playlist-read-collaborative"
    const val DEFAULT_REDIRECT_URI = "nodeplayer://spotify-callback"

    private val secureRandom = SecureRandom()

    /**
     * Generates a high-entropy cryptographic random string using the unreserved
     * characters [A-Z], [a-z], [0-9], "-", ".", "_", "~", from 43 to 128 characters.
     */
    fun generateCodeVerifier(length: Int = 64): String {
        val validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(validChars[secureRandom.nextInt(validChars.length)])
        }
        return sb.toString()
    }

    /**
     * Creates a code_challenge from code_verifier by calculating SHA-256 hash
     * and Base64-URL-encoding the result without padding.
     */
    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    /**
     * Builds the authorization URL for user consent.
     */
    fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String = DEFAULT_REDIRECT_URI,
        codeChallenge: String,
        state: String,
        scopes: String = DEFAULT_SCOPES
    ): String {
        val params = listOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "code_challenge_method" to "S256",
            "code_challenge" to codeChallenge,
            "state" to state,
            "scope" to scopes
        )

        val query = params.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8.toString())}"
        }
        return "$SPOTIFY_AUTH_ENDPOINT?$query"
    }

    /**
     * Masks token strings for diagnostic logging, guaranteeing secret material is never leaked.
     */
    fun maskTokenForLog(token: String?): String {
        if (token.isNullOrBlank()) return "none"
        if (token.length <= 8) return "***"
        return "${token.take(4)}...${token.takeLast(4)}"
    }
}
