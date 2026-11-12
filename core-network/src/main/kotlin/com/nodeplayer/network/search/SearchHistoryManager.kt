package com.nodeplayer.network.search

import com.nodeplayer.common.logging.NodeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SearchHistoryPayload(
    val queries: List<String> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Persistent, thread-safe manager for user recent search queries.
 * Deduplicates entries, caps at [maxHistory] (default 15), and provides individual deletion.
 */
class SearchHistoryManager(
    private val storageDir: File? = null,
    private val maxHistory: Int = 15
) {
    private val TAG = "nodeplayer/search_history"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _queriesState = MutableStateFlow<List<String>>(emptyList())
    val queriesState: StateFlow<List<String>> = _queriesState.asStateFlow()

    private val historyFile: File? by lazy {
        try {
            val baseDir = storageDir ?: File(System.getProperty("user.home"), ".nodeplayer/search")
            if (!baseDir.exists()) baseDir.mkdirs()
            File(baseDir, "recent_searches.json")
        } catch (t: Throwable) {
            NodeLog.w(TAG, "Failed to resolve search history file: ${t.message}")
            null
        }
    }

    init {
        loadFromDisk()
    }

    @Synchronized
    fun addQuery(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return

        val current = _queriesState.value.toMutableList()
        // Deduplicate case-insensitively
        current.removeAll { it.equals(query, ignoreCase = true) }
        // Prepend to top
        current.add(0, query)

        val trimmed = if (current.size > maxHistory) current.take(maxHistory) else current
        _queriesState.value = trimmed
        saveToDisk(trimmed)
    }

    @Synchronized
    fun removeQuery(rawQuery: String) {
        val query = rawQuery.trim()
        val current = _queriesState.value.toMutableList()
        val removed = current.removeAll { it.equals(query, ignoreCase = true) }
        if (removed) {
            _queriesState.value = current
            saveToDisk(current)
        }
    }

    @Synchronized
    fun clearAll() {
        _queriesState.value = emptyList()
        saveToDisk(emptyList())
    }

    fun getRecentQueries(): List<String> = _queriesState.value

    @Synchronized
    fun loadFromDisk(): List<String> {
        val file = historyFile
        if (file == null || !file.exists()) {
            return emptyList()
        }

        try {
            val content = file.readText()
            val payload = json.decodeFromString<SearchHistoryPayload>(content)
            val queries = payload.queries.take(maxHistory)
            _queriesState.value = queries
            NodeLog.d(TAG, "Loaded ${queries.size} search history items from disk")
            return queries
        } catch (t: Throwable) {
            NodeLog.w(TAG, "Failed to read search history from disk: ${t.message}")
            return emptyList()
        }
    }

    private fun saveToDisk(queries: List<String>) {
        val file = historyFile ?: return
        try {
            val payload = SearchHistoryPayload(
                queries = queries,
                lastUpdated = System.currentTimeMillis()
            )
            file.writeText(json.encodeToString(payload))
        } catch (t: Throwable) {
            NodeLog.e(TAG, "Failed to save search history to disk: ${t.message}")
        }
    }
}
