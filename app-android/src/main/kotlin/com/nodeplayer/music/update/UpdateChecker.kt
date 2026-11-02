package com.nodeplayer.music.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/Krighz/node-player/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 15000

    /**
     * Checks if the device is currently on Wi-Fi or unmetered network.
     */
    fun isUnmeteredConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            return !cm.isActiveNetworkMetered
        }
    }

    /**
     * Gets installed app version name.
     */
    fun getInstalledVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    /**
     * Executes the GitHub Releases API query.
     * @param ignoreNetworkRestrictions If true (e.g. manual user tap "Check now"), ignores Wi-Fi only constraint.
     */
    fun checkForUpdate(
        context: Context,
        ignoreNetworkRestrictions: Boolean = false
    ): UpdateCheckResult {
        val currentVersion = getInstalledVersionName(context)

        // Check Wi-Fi restriction if enabled and not manually overridden
        if (!ignoreNetworkRestrictions && UpdatePreferences.isWifiOnly(context) && !isUnmeteredConnection(context)) {
            Log.d(TAG, "Update check skipped: network is metered and Wi-Fi only is requested.")
            return UpdateCheckResult.NetworkError("Skipped: Metered network (Wi-Fi only enabled)")
        }

        val requestUrl = UpdatePreferences.getDebugMockUrl(context)?.takeIf { it.isNotBlank() }
            ?: GITHUB_LATEST_RELEASE_URL

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(requestUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "NodePlayer/$currentVersion")

            val responseCode = connection.responseCode
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseText = reader.use { it.readText() }
                    parseReleaseJson(responseText, currentVersion)
                }
                403, 429 -> {
                    Log.w(TAG, "GitHub API rate limit encountered: HTTP $responseCode")
                    UpdateCheckResult.RateLimited("GitHub API rate limit reached. Please try again later.")
                }
                else -> {
                    Log.w(TAG, "Unexpected HTTP response from GitHub API: $responseCode")
                    UpdateCheckResult.Error("HTTP $responseCode from update server")
                }
            }
        } catch (e: UnknownHostException) {
            Log.d(TAG, "Device offline or DNS failure: ${e.message}")
            UpdateCheckResult.NetworkError("Network unavailable")
        } catch (e: SocketTimeoutException) {
            Log.d(TAG, "Connection timeout: ${e.message}")
            UpdateCheckResult.NetworkError("Connection timed out")
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateCheckResult.Error(e.message ?: "Unknown error")
        } finally {
            connection?.disconnect()
            UpdatePreferences.setLastCheckedTimestamp(context, System.currentTimeMillis())
        }
    }

    /**
     * Parses the GitHub release JSON object and determines if a valid update exists.
     */
    fun parseReleaseJson(jsonString: String, currentVersion: String): UpdateCheckResult {
        return try {
            val json = JSONObject(jsonString)
            val tagName = json.optString("tag_name", "")
            val body = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            if (tagName.isBlank()) {
                return UpdateCheckResult.Error("Invalid release format: missing tag_name")
            }

            val cleanTag = tagName.removePrefix("v").removePrefix("V")
            val isNewer = SemVer.isNewer(cleanTag, currentVersion)
            if (!isNewer) {
                return UpdateCheckResult.UpToDate
            }

            var apkUrl: String? = null
            var sha256Url: String? = null
            var apkSize: Long = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.equals("NodePlayer.apk", ignoreCase = true)) {
                        apkUrl = downloadUrl
                        apkSize = asset.optLong("size", 0L)
                    } else if (name.equals("NodePlayer.apk.sha256", ignoreCase = true)) {
                        sha256Url = downloadUrl
                    }
                }
            }

            if (apkUrl.isNullOrBlank() || sha256Url.isNullOrBlank()) {
                return UpdateCheckResult.Error("Release $tagName missing NodePlayer.apk or NodePlayer.apk.sha256 asset")
            }

            val info = AppUpdateInfo(
                versionName = cleanTag,
                releaseTag = tagName,
                downloadUrl = apkUrl,
                sha256Url = sha256Url,
                releaseNotes = body,
                assetSize = apkSize,
                publishedAt = publishedAt
            )
            UpdateCheckResult.UpdateAvailable(info)
        } catch (e: Exception) {
            try { Log.e(TAG, "Failed parsing release JSON", e) } catch (_: Throwable) {}
            UpdateCheckResult.Error("Failed to parse release metadata: ${e.message}")
        }
    }
}
