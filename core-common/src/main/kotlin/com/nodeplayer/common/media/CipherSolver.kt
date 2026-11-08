package com.nodeplayer.common.media

/**
 * Pluggable cipher and throttling solver interface for deobfuscating YouTube media streams.
 *
 * Implemented natively per platform (e.g., Android using headless WebView evaluating YouTube's base.js,
 * Desktop using pure JVM / headless JS script runner or passthrough fallback).
 */
interface CipherSolver {
    /**
     * Deobfuscates encrypted stream signature cipher (params: s, sp, url) and returns direct stream URL.
     */
    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String?

    /**
     * Transforms the 'n' query parameter within a streaming URL using player JS logic
     * to avoid bandwidth throttling and 403 HTTP errors.
     */
    suspend fun transformNParamInUrl(url: String): String

    companion object {
        @Volatile
        var current: CipherSolver? = null
    }
}
