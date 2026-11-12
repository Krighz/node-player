package com.nodeplayer.network.innertube

import java.security.MessageDigest

/**
 * YouTube authentication utilities: cookie parsing, SAPISID extraction,
 * and SAPISIDHASH computation for InnerTube requests.
 */
object YouTubeAuth {

    const val ORIGIN_MUSIC = "https://music.youtube.com"
    const val REFERER_MUSIC = "https://music.youtube.com/"

    /**
     * Resolves the effective SAPISID value from a raw cookie string.
     * Looks for SAPISID, __Secure-3PAPISID, or __Secure-1PAPISID.
     */
    fun extractSapisid(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        val cookieMap = parseCookieString(cookie)
        return cookieMap["SAPISID"]?.takeIf { it.isNotBlank() }
            ?: cookieMap["__Secure-3PAPISID"]?.takeIf { it.isNotBlank() }
            ?: cookieMap["__Secure-1PAPISID"]?.takeIf { it.isNotBlank() }
    }

    /**
     * Generates the SAPISIDHASH Authorization header value.
     * Format: "SAPISIDHASH {timestamp}_{sha1(timestamp + " " + sapisid + " " + origin)}"
     */
    fun generateSapisidHash(
        sapisid: String,
        origin: String = ORIGIN_MUSIC,
        timestampSec: Long = System.currentTimeMillis() / 1000L
    ): String {
        val payload = "$timestampSec $sapisid $origin"
        val hash = sha1Hex(payload)
        return "SAPISIDHASH ${timestampSec}_$hash"
    }

    /**
     * Parses standard HTTP cookie string (name=value; name2=value2).
     */
    fun parseCookieString(cookie: String): Map<String, String> {
        return cookie.split(";")
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val idx = trimmed.indexOf('=')
                if (idx > 0) {
                    val key = trimmed.substring(0, idx).trim()
                    val value = trimmed.substring(idx + 1).trim()
                    key to value
                } else null
            }
            .toMap()
    }

    /**
     * Sanitizes cookie string for logging to avoid leaking sensitive credentials (AUTH-05).
     */
    fun maskCookieForLog(cookie: String?): String {
        if (cookie.isNullOrBlank()) return "missing"
        val parsed = parseCookieString(cookie)
        val hasSapisid = parsed.containsKey("SAPISID") || parsed.containsKey("__Secure-3PAPISID")
        val hasLoginInfo = parsed.containsKey("LOGIN_INFO")
        return "present(items=${parsed.size}, sapisid=$hasSapisid, loginInfo=$hasLoginInfo, len=${cookie.length})"
    }

    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
