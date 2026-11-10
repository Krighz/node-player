package com.nodeplayer.model

/**
 * YouTube user account profile.
 */
data class YouTubeAccount(
    val id: String,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val email: String? = null,
    val isLoggedIn: Boolean = true
)

/**
 * Reactive state representing YouTube authentication status.
 */
sealed interface AuthState {
    object LoggedOut : AuthState
    object Loading : AuthState
    data class LoggedIn(val account: YouTubeAccount) : AuthState
    object SessionExpired : AuthState
}
