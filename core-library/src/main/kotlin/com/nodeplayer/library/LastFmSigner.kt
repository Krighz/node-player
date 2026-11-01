package com.nodeplayer.library

import java.security.MessageDigest

object LastFmSigner {

    /**
     * Signs parameters according to Last.fm API specification:
     * 1. Sort parameters alphabetically by key name.
     * 2. Concatenate key + value pairs in order.
     * 3. Append the shared secret.
     * 4. Compute 32-character lowercase hex MD5.
     */
    fun createApiSignature(params: Map<String, String>, secret: String): String {
        val sortedKeys = params.keys.filter { it != "format" && it != "api_sig" }.sorted()
        val sb = StringBuilder()
        for (key in sortedKeys) {
            sb.append(key).append(params[key] ?: "")
        }
        sb.append(secret)

        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates whether track playback is eligible for scrobbling according to Last.fm rules:
     * - Track duration must be at least 30 seconds.
     * - The track has been played for at least half its duration or 4 minutes (240s), whichever is less.
     */
    fun isEligibleForScrobble(durationSeconds: Long, listenedSeconds: Long): Boolean {
        if (durationSeconds < 30) return false
        val threshold = (durationSeconds / 2).coerceAtMost(240L)
        return listenedSeconds >= threshold
    }
}
