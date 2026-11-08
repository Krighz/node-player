package com.nodeplayer.desktop.cipher

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.common.media.CipherSolver
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Desktop implementation of CipherSolver.
 * Desktop playback primarily uses direct streaming formats (ANDROID_VR / web formats without signature obfuscation),
 * but this provides basic signature extraction and safe passthrough fallback.
 */
class DesktopCipherSolver : CipherSolver {

    override suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        NodeLog.d(TAG, "Desktop deobfuscateStreamUrl called for video: $videoId")
        return try {
            val params = parseQueryParams(signatureCipher)
            val obfuscatedSig = params["s"]
            val sigParam = params["sp"] ?: "signature"
            val baseUrl = params["url"]

            if (baseUrl == null) {
                NodeLog.w(TAG, "No base URL found in cipher for $videoId")
                return null
            }

            // In desktop environment without embedded Android WebView, if 's' is present,
            // construct the best-effort direct URL. If 's' is already deobfuscated or passthrough:
            val effectiveSig = obfuscatedSig ?: ""
            val separator = if ("?" in baseUrl) "&" else "?"
            val finalUrl = if (effectiveSig.isNotEmpty()) {
                "$baseUrl$separator$sigParam=${URLEncoder.encode(effectiveSig, "UTF-8")}"
            } else {
                baseUrl
            }

            NodeLog.d(TAG, "Desktop processed stream URL for $videoId")
            finalUrl
        } catch (e: Exception) {
            NodeLog.e(TAG, "Desktop deobfuscateStreamUrl failed: ${e.message}", e)
            null
        }
    }

    override suspend fun transformNParamInUrl(url: String): String {
        // Desktop streams typically do not throttle on direct client formats like ANDROID_VR.
        // Return URL as passthrough.
        return url
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[key] = value
            }
        }
        return result
    }

    companion object {
        private const val TAG = "cipher/solver-desktop"
    }
}
