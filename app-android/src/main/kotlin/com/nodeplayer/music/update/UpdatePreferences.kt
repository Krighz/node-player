package com.nodeplayer.music.update

import android.content.Context
import android.content.SharedPreferences

object UpdatePreferences {

    private const val PREFS_NAME = "nodeplayer_updates"
    private const val KEY_OPT_IN_PROMPTED = "update_opt_in_prompted"
    private const val KEY_CHECK_AUTOMATICALLY = "update_check_automatically"
    private const val KEY_WIFI_ONLY = "update_wifi_only"
    private const val KEY_LAST_CHECKED = "update_last_checked_timestamp"
    private const val KEY_SKIPPED_VERSIONS = "update_skipped_versions"
    private const val KEY_CACHED_UPDATE = "update_cached_info_json"
    private const val KEY_DEBUG_MOCK_URL = "update_debug_mock_url"
    private const val KEY_LAST_SEEN_VERSION_NAME = "update_last_seen_version_name"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasPromptedOptIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_OPT_IN_PROMPTED, false)
    }

    fun setOptInChoice(context: Context, agreed: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_OPT_IN_PROMPTED, true)
            .putBoolean(KEY_CHECK_AUTOMATICALLY, agreed)
            .apply()
    }

    fun isCheckAutomatically(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CHECK_AUTOMATICALLY, false)
    }

    fun setCheckAutomatically(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_CHECK_AUTOMATICALLY, enabled)
            .apply()
    }

    fun isWifiOnly(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WIFI_ONLY, true)
    }

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .apply()
    }

    fun getLastCheckedTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_CHECKED, 0L)
    }

    fun setLastCheckedTimestamp(context: Context, timestamp: Long) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_CHECKED, timestamp)
            .apply()
    }

    fun getSkippedVersions(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SKIPPED_VERSIONS, emptySet()) ?: emptySet()
    }

    fun skipVersion(context: Context, version: String) {
        val current = getSkippedVersions(context).toMutableSet()
        current.add(version.trim().removePrefix("v").removePrefix("V"))
        getPrefs(context).edit()
            .putStringSet(KEY_SKIPPED_VERSIONS, current)
            .apply()
    }

    fun unskipVersion(context: Context, version: String) {
        val current = getSkippedVersions(context).toMutableSet()
        current.remove(version.trim().removePrefix("v").removePrefix("V"))
        getPrefs(context).edit()
            .putStringSet(KEY_SKIPPED_VERSIONS, current)
            .apply()
    }

    fun clearSkippedVersions(context: Context) {
        getPrefs(context).edit()
            .putStringSet(KEY_SKIPPED_VERSIONS, emptySet())
            .apply()
    }

    fun isVersionSkipped(context: Context, version: String): Boolean {
        val clean = version.trim().removePrefix("v").removePrefix("V")
        return getSkippedVersions(context).contains(clean)
    }

    fun getCachedUpdate(context: Context): String? {
        return getPrefs(context).getString(KEY_CACHED_UPDATE, null)
    }

    fun setCachedUpdate(context: Context, json: String?) {
        getPrefs(context).edit()
            .putString(KEY_CACHED_UPDATE, json)
            .apply()
    }

    fun getDebugMockUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_DEBUG_MOCK_URL, null)
    }

    fun setDebugMockUrl(context: Context, url: String?) {
        getPrefs(context).edit()
            .putString(KEY_DEBUG_MOCK_URL, url)
            .apply()
    }

    fun getLastSeenVersionName(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_SEEN_VERSION_NAME, null)
    }

    fun setLastSeenVersionName(context: Context, versionName: String) {
        getPrefs(context).edit()
            .putString(KEY_LAST_SEEN_VERSION_NAME, versionName)
            .apply()
    }
}
