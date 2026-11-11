package com.nodeplayer.network.innertube

/**
 * Thread-safe shared singleton provider for [InnerTubeClient].
 *
 * Prevents per-request Ktor CIO engine allocations and ensures cookie/auth state
 * initialized by YouTubeAccountManager is shared across StreamResolver, Home, and Search.
 */
object SharedInnerTubeProvider {

    @Volatile
    private var instance: InnerTubeClient? = null

    @Volatile
    private var factory: () -> InnerTubeClient = { InnerTubeClient() }

    fun get(): InnerTubeClient {
        return instance ?: synchronized(this) {
            instance ?: factory().also { instance = it }
        }
    }

    fun setProvider(provider: () -> InnerTubeClient) {
        synchronized(this) {
            factory = provider
            instance = null
        }
    }

    fun reset() {
        synchronized(this) {
            factory = { InnerTubeClient() }
            instance = null
        }
    }
}
