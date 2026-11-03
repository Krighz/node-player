package com.nodeplayer.common.platform

interface PlatformClock {
    fun currentTimeMillis(): Long
    fun nanoTime(): Long
}

object SystemClock : PlatformClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun nanoTime(): Long = System.nanoTime()
}

interface PlatformFileSystem {
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun listFiles(path: String): List<String>
    fun readFileBytes(path: String): ByteArray?
    fun readFileText(path: String): String?
    fun writeFileBytes(path: String, bytes: ByteArray): Boolean
    fun writeFileText(path: String, text: String): Boolean
    fun delete(path: String): Boolean
    fun getFileSize(path: String): Long
}

interface PlatformHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse

    data class HttpResponse(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, List<String>> = emptyMap()
    ) {
        val isSuccessful: Boolean get() = statusCode in 200..299
    }
}
