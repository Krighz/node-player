package com.nodeplayer.music.auth

import android.content.Context
import android.net.Uri
import com.nodeplayer.common.logging.NodeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties

/**
 * Manages Google OAuth 2.0 PKCE authentication with strict `https://www.googleapis.com/auth/drive.appdata` scope.
 * Tokens are securely persisted in [SecureSessionStore] (hardware-backed AES-256-GCM Keystore).
 */
object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"

    // Google OAuth 2.0 Endpoints
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"

    // Strict Scope Isolation: ONLY hidden appDataFolder access
    const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    const val DEFAULT_REDIRECT_URI = "com.nodeplayer.music:/oauth2redirect"
    const val FALLBACK_REDIRECT_URI = "nodeplayer://gdrive-callback"

    // Default OAuth Client ID fallback from BuildConfig
    private val DEFAULT_CLIENT_ID: String get() = com.nodeplayer.music.BuildConfig.GDRIVE_CLIENT_ID

    // Keystore keys for SecureSessionStore
    const val KEY_GDRIVE_ACCESS_TOKEN = "gdrive_access_token"
    const val KEY_GDRIVE_REFRESH_TOKEN = "gdrive_refresh_token"
    const val KEY_GDRIVE_EXPIRES_AT = "gdrive_expires_at"
    const val KEY_GDRIVE_CUSTOM_CLIENT_ID = "gdrive_custom_client_id"
    const val KEY_GDRIVE_CUSTOM_CLIENT_SECRET = "gdrive_custom_client_secret"
    const val KEY_GDRIVE_CUSTOM_REDIRECT_URI = "gdrive_custom_redirect_uri"
    const val KEY_GDRIVE_USER_EMAIL = "gdrive_user_email"

    private val secureRandom = SecureRandom()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _configuredClientId = MutableStateFlow<String?>(null)
    val configuredClientId: StateFlow<String?> = _configuredClientId.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    @Volatile
    private var activeCodeVerifier: String? = null

    @Volatile
    private var activeRedirectUri: String? = null

    @Volatile
    private var activeState: String? = null

    private fun getStore(context: Context): SecureSessionStore = SecureSessionStore(context)

    fun initialize(context: Context) {
        val store = getStore(context)
        val customId = store.getString(KEY_GDRIVE_CUSTOM_CLIENT_ID)
        val propId = loadClientIdFromProperties(context)
        val effectiveId = customId?.takeIf { it.isNotBlank() } ?: propId?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID

        _configuredClientId.value = effectiveId.takeIf { it.isNotBlank() }
        _userEmail.value = store.getString(KEY_GDRIVE_USER_EMAIL)

        val token = store.getString(KEY_GDRIVE_ACCESS_TOKEN)
        val refreshToken = store.getString(KEY_GDRIVE_REFRESH_TOKEN)
        _isLoggedIn.value = !token.isNullOrBlank() || !refreshToken.isNullOrBlank()

        NodeLog.i(TAG, "Initialized GoogleAuthManager (hasClientId=${!_configuredClientId.value.isNullOrBlank()}, loggedIn=${_isLoggedIn.value})")
    }

    fun hasConfiguredClientId(): Boolean {
        val id = _configuredClientId.value
        return !id.isNullOrBlank()
    }

    fun setCustomClientId(context: Context, clientId: String) {
        val trimmed = clientId.trim()
        val store = getStore(context)
        if (trimmed.isBlank()) {
            store.remove(KEY_GDRIVE_CUSTOM_CLIENT_ID)
            _configuredClientId.value = loadClientIdFromProperties(context)?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID.takeIf { it.isNotBlank() }
        } else {
            store.putString(KEY_GDRIVE_CUSTOM_CLIENT_ID, trimmed)
            _configuredClientId.value = trimmed
        }
    }

    fun setCustomCredentials(
        context: Context,
        clientId: String,
        clientSecret: String? = null,
        redirectUri: String? = null
    ) {
        val store = getStore(context)
        val trimmedId = clientId.trim()
        if (trimmedId.isBlank()) {
            store.remove(KEY_GDRIVE_CUSTOM_CLIENT_ID)
            _configuredClientId.value = loadClientIdFromProperties(context)?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID.takeIf { it.isNotBlank() }
        } else {
            store.putString(KEY_GDRIVE_CUSTOM_CLIENT_ID, trimmedId)
            _configuredClientId.value = trimmedId
        }

        val trimmedSecret = clientSecret?.trim()
        if (trimmedSecret.isNullOrBlank()) {
            store.remove(KEY_GDRIVE_CUSTOM_CLIENT_SECRET)
        } else {
            store.putString(KEY_GDRIVE_CUSTOM_CLIENT_SECRET, trimmedSecret)
        }

        val trimmedUri = redirectUri?.trim()
        if (trimmedUri.isNullOrBlank()) {
            store.remove(KEY_GDRIVE_CUSTOM_REDIRECT_URI)
        } else {
            store.putString(KEY_GDRIVE_CUSTOM_REDIRECT_URI, trimmedUri)
        }
    }

    fun getCustomClientSecret(context: Context): String? {
        return getStore(context).getString(KEY_GDRIVE_CUSTOM_CLIENT_SECRET)?.takeIf { it.isNotBlank() }
    }

    fun getCustomRedirectUri(context: Context): String? {
        return getStore(context).getString(KEY_GDRIVE_CUSTOM_REDIRECT_URI)?.takeIf { it.isNotBlank() }
    }

    fun getEffectiveRedirectUri(context: Context? = null, clientId: String? = getEffectiveClientId()): String {
        val custom = context?.let { getCustomRedirectUri(it) }
        if (!custom.isNullOrBlank()) return custom
        val id = clientId?.takeIf { it.isNotBlank() } ?: getEffectiveClientId()
        if (!id.isNullOrBlank() && id.endsWith(".apps.googleusercontent.com")) {
            val prefix = id.substringBefore(".apps.googleusercontent.com")
            return "com.googleusercontent.apps.$prefix:/oauth2redirect"
        }
        return DEFAULT_REDIRECT_URI
    }

    fun getEffectiveClientId(): String? {
        return _configuredClientId.value?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID.takeIf { it.isNotBlank() }
    }

    /**
     * Builds the PKCE authorization URL for Google Sign-In.
     * Returns null if no valid Client ID has been configured.
     */
    fun createAuthorizationUrl(
        clientId: String? = getEffectiveClientId(),
        redirectUri: String? = null
    ): String? {
        val effectiveClientId = clientId?.takeIf { it.isNotBlank() } ?: return null
        val effectiveRedirect = redirectUri ?: getEffectiveRedirectUri(clientId = effectiveClientId)
        val verifier = generateCodeVerifier()
        activeCodeVerifier = verifier
        activeRedirectUri = effectiveRedirect
        val challenge = generateCodeChallenge(verifier)
        val state = "np_gdrive_${System.currentTimeMillis()}_${secureRandom.nextInt(100000)}"
        activeState = state

        return "$AUTH_ENDPOINT?" + listOf(
            "client_id" to effectiveClientId,
            "redirect_uri" to effectiveRedirect,
            "response_type" to "code",
            "scope" to SCOPE_DRIVE_APPDATA,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "prompt" to "consent",
            "state" to state
        ).joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8.name())}"
        }
    }

    /**
     * Manually exchanges an authorization code or full callback URL for OAuth tokens.
     */
    suspend fun handleManualCodeOrUrl(context: Context, input: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Code or URL cannot be empty"))
        }

        val code = if (trimmed.contains("code=")) {
            try {
                val parsed = Uri.parse(trimmed)
                parsed.getQueryParameter("code") ?: return@withContext Result.failure(IllegalArgumentException("No code parameter in URL"))
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalArgumentException("Failed to parse URL: ${e.message}"))
            }
        } else {
            trimmed
        }

        exchangeAuthorizationCode(context, code)
    }

    /**
     * Handles the OAuth callback redirect URI and exchanges the code for tokens.
     */
    suspend fun handleAuthorizationResponse(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val error = uri.getQueryParameter("error")
            if (!error.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Google authorization error: $error"))
            }

            val state = uri.getQueryParameter("state")
            if (activeState != null && state != activeState) {
                return@withContext Result.failure(IllegalStateException("OAuth state mismatch (possible CSRF attempt)"))
            }

            val code = uri.getQueryParameter("code")
                ?: return@withContext Result.failure(IllegalStateException("No authorization code in response"))

            exchangeAuthorizationCode(context, code)
        } catch (e: Exception) {
            NodeLog.e(TAG, "Authorization response handling failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun exchangeAuthorizationCode(context: Context, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val verifier = activeCodeVerifier
                ?: return@withContext Result.failure(IllegalStateException("Missing PKCE code verifier. Please initiate sign-in again."))

            val clientId = getEffectiveClientId()
                ?: return@withContext Result.failure(IllegalStateException("No Google OAuth Client ID configured"))

            val effectiveRedirect = activeRedirectUri ?: getEffectiveRedirectUri(context)
            val clientSecret = getCustomClientSecret(context)

            val params = mutableListOf(
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to verifier,
                "grant_type" to "authorization_code",
                "redirect_uri" to effectiveRedirect
            )
            if (!clientSecret.isNullOrBlank()) {
                params.add("client_secret" to clientSecret)
            }

            val postData = params.joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8.name())}"
            }

            val response = executeTokenRequest(postData)
            val json = JSONObject(response)

            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", "")
            val expiresIn = json.optLong("expires_in", 3600L)

            saveTokens(context, accessToken, refreshToken, expiresIn)
            _isLoggedIn.value = true

            // Clear temporary PKCE parameters
            activeCodeVerifier = null
            activeState = null

            NodeLog.i(TAG, "Google OAuth 2.0 PKCE authentication succeeded")
            Result.success(accessToken)
        } catch (e: Exception) {
            NodeLog.e(TAG, "Failed to exchange authorization code: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Returns a valid access token, automatically refreshing it if expired.
     */
    suspend fun getValidAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val store = getStore(context)
        val token = store.getString(KEY_GDRIVE_ACCESS_TOKEN)
        val expiresAt = store.getString(KEY_GDRIVE_EXPIRES_AT)?.toLongOrNull() ?: 0L

        // Buffer 60 seconds before expiration
        if (!token.isNullOrBlank() && System.currentTimeMillis() < (expiresAt - 60_000L)) {
            return@withContext token
        }

        // Token expired or nearing expiration: attempt refresh
        refreshAccessToken(context).getOrNull()
    }

    /**
     * Refreshes the OAuth access token using the stored refresh token.
     */
    suspend fun refreshAccessToken(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val store = getStore(context)
            val refreshToken = store.getString(KEY_GDRIVE_REFRESH_TOKEN)
                ?: return@withContext Result.failure(IllegalStateException("No Google Drive refresh token available"))

            val clientId = getEffectiveClientId()

            val params = mutableListOf(
                "client_id" to clientId,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token"
            )
            val clientSecret = getCustomClientSecret(context)
            if (!clientSecret.isNullOrBlank()) {
                params.add("client_secret" to clientSecret)
            }

            val postData = params.joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8.name())}"
            }

            val response = executeTokenRequest(postData)
            val json = JSONObject(response)

            val newAccessToken = json.getString("access_token")
            val newRefreshToken = json.optString("refresh_token", "")
            val expiresIn = json.optLong("expires_in", 3600L)

            saveTokens(context, newAccessToken, newRefreshToken, expiresIn)
            _isLoggedIn.value = true

            NodeLog.i(TAG, "Successfully refreshed Google Drive access token")
            Result.success(newAccessToken)
        } catch (e: Exception) {
            NodeLog.e(TAG, "Google token refresh failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut(context: Context) {
        val store = getStore(context)
        val token = store.getString(KEY_GDRIVE_ACCESS_TOKEN)

        // Best-effort token revocation
        if (!token.isNullOrBlank()) {
            Thread {
                try {
                    val url = URL("$REVOKE_ENDPOINT?token=${URLEncoder.encode(token, StandardCharsets.UTF_8.name())}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {}
            }.start()
        }

        store.remove(KEY_GDRIVE_ACCESS_TOKEN)
        store.remove(KEY_GDRIVE_REFRESH_TOKEN)
        store.remove(KEY_GDRIVE_EXPIRES_AT)
        store.remove(KEY_GDRIVE_USER_EMAIL)

        _isLoggedIn.value = false
        _userEmail.value = null
        NodeLog.i(TAG, "Signed out from Google Drive. Tokens removed from secure keystore.")
    }

    private fun saveTokens(
        context: Context,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long
    ) {
        val store = getStore(context)
        store.putString(KEY_GDRIVE_ACCESS_TOKEN, accessToken)
        if (!refreshToken.isNullOrBlank()) {
            store.putString(KEY_GDRIVE_REFRESH_TOKEN, refreshToken)
        }
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        store.putString(KEY_GDRIVE_EXPIRES_AT, expiresAt.toString())
    }

    private fun executeTokenRequest(postData: String): String {
        val url = URL(TOKEN_ENDPOINT)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Accept", "application/json")

        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(postData)
            writer.flush()
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) {
            throw IllegalStateException("Token endpoint returned HTTP $code: $response")
        }

        return response
    }

    internal fun generateCodeVerifier(length: Int = 64): String {
        val validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(validChars[secureRandom.nextInt(validChars.length)])
        }
        return sb.toString()
    }

    internal fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun loadClientIdFromProperties(context: Context): String? {
        return try {
            val file = File(context.filesDir.parentFile, "local.properties")
            if (file.exists()) {
                val props = Properties()
                props.load(FileInputStream(file))
                props.getProperty("google.drive.clientId.debug")
                    ?: props.getProperty("google.drive.clientId.release")
                    ?: props.getProperty("google.drive.clientId")
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
