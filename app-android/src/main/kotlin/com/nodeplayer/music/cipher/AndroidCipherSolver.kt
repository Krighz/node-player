package com.nodeplayer.music.cipher

import android.content.Context
import android.net.Uri
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.common.media.CipherSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of CipherSolver using headless CipherWebView and PlayerJsFetcher.
 */
class AndroidCipherSolver(context: Context) : CipherSolver {

    private val appContext: Context = context.applicationContext
    private val playerJsFetcher = PlayerJsFetcher(appContext)

    private var cipherWebView: CipherWebView? = null
    private var currentPlayerHash: String? = null

    override suspend fun getSignatureTimestamp(): Int? {
        val playerJs = playerJsFetcher.getPlayerJs()?.first ?: return null
        return Regex("""signatureTimestamp\s*[:=]\s*(\d+)""").find(playerJs)?.groupValues?.get(1)?.toIntOrNull()
    }

    override suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        return try {
            deobfuscateInternal(signatureCipher, videoId, isRetry = false)
        } catch (e: Exception) {
            NodeLog.e(TAG, "Cipher deobfuscation failed, retrying with fresh JS: ${e.message}", e)
            try {
                playerJsFetcher.invalidateCache()
                closeWebView()
                deobfuscateInternal(signatureCipher, videoId, isRetry = true)
            } catch (retryE: Exception) {
                NodeLog.e(TAG, "Cipher deobfuscation retry also failed: ${retryE.message}", retryE)
                null
            }
        }
    }

    private suspend fun deobfuscateInternal(signatureCipher: String, videoId: String, isRetry: Boolean): String? {
        val params = parseQueryParams(signatureCipher)
        val obfuscatedSig = params["s"]
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"]

        if (obfuscatedSig == null || baseUrl == null) {
            NodeLog.e(TAG, "Could not parse signatureCipher params: s=${obfuscatedSig != null}, url=${baseUrl != null}")
            return null
        }

        NodeLog.d(TAG, "Deobfuscating cipher for $videoId: sig=${obfuscatedSig.take(20)}..., sp=$sigParam")

        val webView = getOrCreateWebView(forceRefresh = isRetry)
            ?: return null

        val deobfuscatedSig = webView.deobfuscateSignature(obfuscatedSig)

        val separator = if ("?" in baseUrl) "&" else "?"
        val finalUrl = "$baseUrl${separator}${sigParam}=${Uri.encode(deobfuscatedSig)}"

        NodeLog.d(TAG, "Custom cipher deobfuscation succeeded for $videoId")
        return finalUrl
    }

    override suspend fun transformNParamInUrl(url: String): String {
        return try {
            transformNInternal(url)
        } catch (e: Exception) {
            NodeLog.e(TAG, "N-transform failed, returning original URL: ${e.message}", e)
            url
        }
    }

    private suspend fun transformNInternal(url: String): String {
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            NodeLog.d(TAG, "No 'n' parameter found in URL, skipping transform")
            return url
        }
        val nValue = Uri.decode(nMatch.groupValues[1])
        NodeLog.d(TAG, "N-param found: $nValue")

        val webView = getOrCreateWebView(forceRefresh = false) ?: return url

        if (!webView.nFunctionAvailable) {
            NodeLog.e(TAG, "N-transform function was not discovered at init time")
            return url
        }

        val transformedN = webView.transformN(nValue)
        NodeLog.d(TAG, "N-param transformed: $nValue -> $transformedN")

        return url.replaceFirst(
            Regex("([?&])n=[^&]+"),
            "$1n=${Uri.encode(transformedN)}"
        )
    }

    private suspend fun getOrCreateWebView(forceRefresh: Boolean): CipherWebView? {
        if (!forceRefresh && cipherWebView != null) {
            return cipherWebView
        }

        if (cipherWebView != null) {
            closeWebView()
        }

        val result = playerJsFetcher.getPlayerJs(forceRefresh = forceRefresh)
        if (result == null) {
            NodeLog.e(TAG, "Failed to get player JS")
            return null
        }
        val (playerJs, hash) = result

        val sigInfo = FunctionNameExtractor.extractSigFunctionInfo(playerJs)
        if (sigInfo == null) {
            NodeLog.e(TAG, "Could not extract signature function info from player JS")
            return null
        }

        val nFuncInfo = FunctionNameExtractor.extractNFunctionInfo(playerJs)
        if (nFuncInfo == null) {
            NodeLog.e(TAG, "Could not extract n-function info from player JS (will try brute-force)")
        }

        NodeLog.d(TAG, "Creating CipherWebView with sig=${sigInfo.name}, constantArg=${sigInfo.constantArg}, nFunc=${nFuncInfo?.name}[${nFuncInfo?.arrayIndex}]")

        val webView = CipherWebView.create(
            context = appContext,
            playerJs = playerJs,
            sigInfo = sigInfo,
            nFuncInfo = nFuncInfo,
        )

        cipherWebView = webView
        currentPlayerHash = hash
        return webView
    }

    private suspend fun closeWebView() {
        withContext(Dispatchers.Main) {
            cipherWebView?.close()
        }
        cipherWebView = null
        currentPlayerHash = null
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = Uri.decode(pair.substring(0, idx))
                val value = Uri.decode(pair.substring(idx + 1))
                result[key] = value
            }
        }
        return result
    }

    companion object {
        private const val TAG = "cipher/solver-android"
    }
}
