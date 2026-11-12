package com.nodeplayer.music.auth

import android.content.Context
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.AuthState
import com.nodeplayer.model.YouTubeAccount
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.network.innertube.YouTubeAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton managing YouTube account authentication lifecycle, reactive state,
 * and InnerTubeClient session synchronization.
 */
object YouTubeAccountManager {

    private const val TAG = "YouTubeAccountManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionStore: SecureSessionStore? = null
    var innerTubeClient: InnerTubeClient? = null
    private var validationJob: kotlinx.coroutines.Job? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun isLoggedIn(): Boolean = _authState.value is AuthState.LoggedIn

    fun initialize(context: Context, client: InnerTubeClient) {
        val appContext = context.applicationContext
        sessionStore = SecureSessionStore(appContext)
        innerTubeClient = client

        // Restore cached session if present and stay logged in is true
        val store = sessionStore
        val savedCookie = store?.getCookie()

        if (store != null && store.hasValidSession() && !savedCookie.isNullOrBlank()) {
            client.sessionCookie = savedCookie

            val rawAvatar = store.getAccountAvatar()
            val normalizedAvatarUrl = InnerTubeClient.normalizeAvatarUrl(rawAvatar)
            val handle = store.getAccountHandle()
            val accountId = store.getAccountId() ?: handle ?: "youtube_user"
            val accountName = store.getAccountName() ?: "YouTube User"
            val accountEmail = store.getAccountEmail()

            val account = YouTubeAccount(
                id = accountId,
                name = accountName,
                handle = handle,
                avatarUrl = normalizedAvatarUrl,
                email = accountEmail,
                isLoggedIn = true
            )

            // Immediately emit LoggedIn so UI is hydrated without waiting for a network roundtrip (D-14)
            _authState.value = AuthState.LoggedIn(account)

            // Asynchronously validate session & refresh account profile in background
            validationJob?.cancel()
            validationJob = scope.launch {
                validateSession(savedCookie)
            }
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    suspend fun onLoginSuccess(
        cookie: String,
        pageId: String? = null,
        dataSyncId: String? = null,
        authUser: String? = null,
        visitorData: String? = null,
        clientVersion: String? = null
    ) {
        val client = innerTubeClient ?: return
        val store = sessionStore ?: return

        _authState.value = AuthState.Loading
        client.sessionCookie = cookie
        client.pageId = pageId
        client.dataSyncId = dataSyncId
        client.authUser = authUser
        if (visitorData != null) client.visitorData = visitorData

        val accountResult = client.fetchAccountInfo()
        val account = accountResult.getOrNull()

        if (account != null) {
            val normalizedAvatar = InnerTubeClient.normalizeAvatarUrl(account.avatarUrl)
            val updatedAccount = account.copy(avatarUrl = normalizedAvatar)
            store.saveSession(cookie, updatedAccount, stayLoggedIn = true)
            _authState.value = AuthState.LoggedIn(updatedAccount)
            NodeLog.i(TAG, "Login successful for user: ${updatedAccount.name} (${updatedAccount.handle})")
        } else {
            // Even if profile parsing fails, if cookies are valid keep logged in state
            val fallbackAccount = YouTubeAccount(
                id = "youtube_user",
                name = "YouTube User",
                isLoggedIn = true
            )
            store.saveSession(cookie, fallbackAccount, stayLoggedIn = true)
            _authState.value = AuthState.LoggedIn(fallbackAccount)
            NodeLog.w(TAG, "Login succeeded but profile info could not be parsed; using fallback account")
        }
    }

    fun logout() {
        validationJob?.cancel()
        validationJob = null
        sessionStore?.clearSession()
        innerTubeClient?.sessionCookie = null
        _authState.value = AuthState.LoggedOut
        NodeLog.i(TAG, "User logged out successfully")
    }

    fun setStayLoggedIn(enabled: Boolean) {
        sessionStore?.setStayLoggedIn(enabled)
    }

    fun isStayLoggedIn(): Boolean {
        return sessionStore?.isStayLoggedIn() ?: true
    }

    private suspend fun validateSession(cookie: String) {
        val client = innerTubeClient ?: return
        val store = sessionStore ?: return

        try {
            val accountResult = client.fetchAccountInfo()
            accountResult.onSuccess { account ->
                if (account != null) {
                    val normalizedAvatar = InnerTubeClient.normalizeAvatarUrl(account.avatarUrl)
                    val updatedAccount = account.copy(avatarUrl = normalizedAvatar)
                    store.saveSession(cookie, updatedAccount, stayLoggedIn = store.isStayLoggedIn())
                    _authState.value = AuthState.LoggedIn(updatedAccount)
                    NodeLog.d(TAG, "Session validated successfully: ${updatedAccount.name}")
                } else {
                    // Unauthorized or empty menu -> Session expired (AUTH-06)
                    NodeLog.w(TAG, "Session expired during background verification")
                    _authState.value = AuthState.SessionExpired
                }
            }.onFailure { err ->
                NodeLog.w(TAG, "Failed to validate session: ${err.message}")
                // Do not mark expired on transient network error if we have cached profile
                if (store.getCachedAccount() != null) {
                    _authState.value = AuthState.LoggedIn(store.getCachedAccount()!!)
                } else {
                    _authState.value = AuthState.SessionExpired
                }
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Exception during session validation: ${e.message}")
            _authState.value = AuthState.SessionExpired
        }
    }
}
