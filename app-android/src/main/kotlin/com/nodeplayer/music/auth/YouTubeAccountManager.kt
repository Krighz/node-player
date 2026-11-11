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
        val savedCookie = sessionStore?.getCookie()
        val cachedAccount = sessionStore?.getCachedAccount()

        if (!savedCookie.isNullOrBlank()) {
            client.sessionCookie = savedCookie
            if (cachedAccount != null) {
                _authState.value = AuthState.LoggedIn(cachedAccount)
            } else {
                _authState.value = AuthState.Loading
            }

            // Asynchronously validate session & refresh account profile
            validationJob?.cancel()
            validationJob = scope.launch {
                validateSession(savedCookie)
            }
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    suspend fun onLoginSuccess(cookie: String) {
        val client = innerTubeClient ?: return
        val store = sessionStore ?: return

        _authState.value = AuthState.Loading
        client.sessionCookie = cookie

        val accountResult = client.fetchAccountInfo()
        val account = accountResult.getOrNull()

        if (account != null) {
            store.saveSession(cookie, account, stayLoggedIn = true)
            _authState.value = AuthState.LoggedIn(account)
            NodeLog.i(TAG, "Login successful for user: ${account.name} (${account.handle})")
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
                    store.saveSession(cookie, account, stayLoggedIn = store.isStayLoggedIn())
                    _authState.value = AuthState.LoggedIn(account)
                    NodeLog.d(TAG, "Session validated successfully: ${account.name}")
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
