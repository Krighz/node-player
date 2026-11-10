package com.nodeplayer.music.auth

import android.content.Context
import android.net.Uri
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.network.spotify.SpotifyAuth
import com.nodeplayer.network.spotify.SpotifyClient
import com.nodeplayer.network.spotify.SpotifyTokenResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * Manages Spotify Authentication for Node Player on Android.
 * - Reads SPOTIFY_CLIENT_ID from local.properties, build config, or user preference fallback.
 * - Persists access and refresh tokens in [SecureSessionStore].
 * - Handles PKCE state and verifier lifecycle.
 */
object SpotifyAccountManager {

    private const val TAG = "spotify-account"
    private const val KEY_ACCESS_TOKEN = "spotify_access_token"
    private const val KEY_REFRESH_TOKEN = "spotify_refresh_token"
    private const val KEY_CUSTOM_CLIENT_ID = "spotify_custom_client_id"
    private const val KEY_EXPIRES_AT = "spotify_expires_at"

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _configuredClientId = MutableStateFlow<String?>(null)
    val configuredClientId: StateFlow<String?> = _configuredClientId.asStateFlow()

    var activeVerifier: String? = null
        private set

    val client: SpotifyClient by lazy { SpotifyClient() }

    private fun getStore(context: Context): SecureSessionStore = SecureSessionStore(context)

    fun initialize(context: Context) {
        val store = getStore(context)
        val storedClientId = store.getString(KEY_CUSTOM_CLIENT_ID)
        val fileClientId = loadClientIdFromProperties(context)
        val effectiveClientId: String? = storedClientId?.takeIf { it.isNotBlank() } ?: fileClientId

        _configuredClientId.value = effectiveClientId
        val token = getValidAccessToken(context)
        _isLoggedIn.value = !token.isNullOrBlank()
        NodeLog.i(TAG, "SpotifyAccountManager initialized (hasClientId=${!effectiveClientId.isNullOrBlank()}, loggedIn=${_isLoggedIn.value})")
    }

    fun setCustomClientId(context: Context, clientId: String) {
        val trimmed = clientId.trim()
        getStore(context).putString(KEY_CUSTOM_CLIENT_ID, trimmed)
        _configuredClientId.value = trimmed.takeIf { it.isNotBlank() }
    }

    fun getValidAccessToken(context: Context): String? {
        val store = getStore(context)
        val token = store.getString(KEY_ACCESS_TOKEN)
        val expiresAt = store.getString(KEY_EXPIRES_AT)?.toLongOrNull() ?: 0L
        if (token.isNullOrBlank()) return null
        return if (System.currentTimeMillis() < expiresAt) token else null
    }

    fun getRefreshToken(context: Context): String? {
        return getStore(context).getString(KEY_REFRESH_TOKEN)
    }

    /**
     * Prepares PKCE authorization URL and stores verifier in-memory for callback exchange.
     */
    fun createAuthorizationUrl(redirectUri: String = SpotifyAuth.DEFAULT_REDIRECT_URI): String? {
        val clientId = _configuredClientId.value ?: return null
        val verifier = SpotifyAuth.generateCodeVerifier()
        activeVerifier = verifier
        val challenge = SpotifyAuth.generateCodeChallenge(verifier)
        val state = "node_${System.currentTimeMillis()}"
        return SpotifyAuth.buildAuthorizeUrl(
            clientId = clientId,
            redirectUri = redirectUri,
            codeChallenge = challenge,
            state = state
        )
    }

    /**
     * Handles authorization code redirect URL, exchanging for tokens and persisting them.
     */
    suspend fun handleAuthorizationResponse(context: Context, uri: Uri): Result<SpotifyTokenResponse> {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        if (!error.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Spotify authorization denied: $error"))
        }

        if (code.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("No code parameter found in redirect URL"))
        }

        val verifier = activeVerifier ?: return Result.failure(IllegalStateException("Missing code_verifier"))
        val clientId = _configuredClientId.value ?: return Result.failure(IllegalStateException("Missing client ID"))

        val tokenResult = client.exchangeCodeForToken(clientId, code, verifier)
        return tokenResult.onSuccess { response ->
            saveTokens(context, response)
            activeVerifier = null
            _isLoggedIn.value = true
            NodeLog.i(TAG, "Spotify sign-in successful. Tokens securely stored.")
        }.onFailure { e ->
            NodeLog.e(TAG, "Failed to exchange Spotify authorization code", e)
        }
    }

    suspend fun refreshTokens(context: Context): Result<String> {
        val refreshToken = getRefreshToken(context)
            ?: return Result.failure(IllegalStateException("No refresh token available"))
        val clientId = _configuredClientId.value
            ?: return Result.failure(IllegalStateException("No client ID available"))

        return client.refreshAccessToken(clientId, refreshToken).map { response ->
            saveTokens(context, response)
            _isLoggedIn.value = true
            response.accessToken
        }
    }

    fun logOut(context: Context) {
        val store = getStore(context)
        store.remove(KEY_ACCESS_TOKEN)
        store.remove(KEY_REFRESH_TOKEN)
        store.remove(KEY_EXPIRES_AT)
        _isLoggedIn.value = false
        NodeLog.i(TAG, "Logged out from Spotify. Tokens cleared.")
    }

    private fun saveTokens(context: Context, response: SpotifyTokenResponse) {
        val store = getStore(context)
        store.putString(KEY_ACCESS_TOKEN, response.accessToken)
        if (!response.refreshToken.isNullOrBlank()) {
            store.putString(KEY_REFRESH_TOKEN, response.refreshToken)
        }
        val expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L) - 30000L // 30s buffer
        store.putString(KEY_EXPIRES_AT, expiresAt.toString())
    }

    private fun loadClientIdFromProperties(context: Context): String? {
        return try {
            val file = File(context.filesDir.parentFile, "local.properties")
            if (file.exists()) {
                val props = Properties()
                props.load(FileInputStream(file))
                props.getProperty("spotify.clientId")
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
