package com.nodeplayer.music.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.YouTubeAccount
import com.nodeplayer.network.innertube.YouTubeAuth

/**
 * Hardware-backed encrypted session storage for YouTube credentials and profile metadata.
 * Uses AES-256-GCM via Android Keystore to prevent plain-text exposure on disk (AUTH-04).
 */
class SecureSessionStore(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            NodeLog.w(TAG, "Failed to initialize EncryptedSharedPreferences, resetting: ${e.message}")
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun saveSession(
        cookie: String,
        account: YouTubeAccount?,
        stayLoggedIn: Boolean = true
    ) {
        prefs.edit()
            .putString(KEY_COOKIE, cookie)
            .putBoolean(KEY_STAY_LOGGED_IN, stayLoggedIn)
            .putString(KEY_ACCOUNT_ID, account?.id)
            .putString(KEY_ACCOUNT_NAME, account?.name)
            .putString(KEY_ACCOUNT_HANDLE, account?.handle)
            .putString(KEY_ACCOUNT_AVATAR, account?.avatarUrl)
            .putString(KEY_ACCOUNT_EMAIL, account?.email)
            .apply()

        NodeLog.d(TAG, "Saved session in hardware-backed storage: ${YouTubeAuth.maskCookieForLog(cookie)}")
    }

    fun getCookie(): String? {
        val stayLoggedIn = prefs.getBoolean(KEY_STAY_LOGGED_IN, true)
        if (!stayLoggedIn) return null
        return prefs.getString(KEY_COOKIE, null)
    }

    fun getCachedAccount(): YouTubeAccount? {
        val id = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        val name = prefs.getString(KEY_ACCOUNT_NAME, null) ?: "YouTube User"
        return YouTubeAccount(
            id = id,
            name = name,
            handle = prefs.getString(KEY_ACCOUNT_HANDLE, null),
            avatarUrl = prefs.getString(KEY_ACCOUNT_AVATAR, null),
            email = prefs.getString(KEY_ACCOUNT_EMAIL, null),
            isLoggedIn = true
        )
    }

    fun isStayLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_STAY_LOGGED_IN, true)
    }

    fun setStayLoggedIn(stayLoggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_STAY_LOGGED_IN, stayLoggedIn).apply()
    }

    fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, value).apply()
        }
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        return prefs.getString(key, defaultValue)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /**
     * Purges all credentials, cookies, and cached profile data (AUTH-03).
     */
    fun clearSession() {
        prefs.edit().clear().apply()
        NodeLog.i(TAG, "Cleared YouTube session and credentials from secure storage")
    }

    companion object {
        private const val TAG = "SecureSessionStore"
        private const val PREFS_FILE_NAME = "secure_youtube_session"

        private const val KEY_COOKIE = "encrypted_cookie"
        private const val KEY_STAY_LOGGED_IN = "stay_logged_in"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_ACCOUNT_HANDLE = "account_handle"
        private const val KEY_ACCOUNT_AVATAR = "account_avatar"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
    }
}
