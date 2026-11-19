package com.nodeplayer.music.mixtape

import android.content.Context
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.storage.StorageCategoryRegistry
import com.nodeplayer.model.CanonicalTrackKey
import com.nodeplayer.model.storage.StorageCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SmartMixtapeManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : StorageCategory {

    override val id: String = "smart_mixtape"
    override val displayName: String = "Smart Offline Mixtape"
    override val description: String = "Auto-cached songs for zero-data offline playback"
    override val clearable: Boolean = true

    val mixtapeDir: File = File(context.filesDir, "smart_mixtape").apply {
        if (!exists()) mkdirs()
    }

    private val validAudioExtensions = setOf("opus", "m4a", "mp3", "ogg", "aac", "flac")

    private val _cachedKeyMap = MutableStateFlow<Map<CanonicalTrackKey, File>>(emptyMap())
    val cachedKeyMap: StateFlow<Map<CanonicalTrackKey, File>> = _cachedKeyMap.asStateFlow()

    init {
        refreshMemoryIndex()
        StorageCategoryRegistry.registerCategory(this)
    }

    fun interceptPlayback(rawUriOrPath: String): File? {
        val key = CanonicalTrackKey.from(rawUriOrPath)
        val file = _cachedKeyMap.value[key]
        if (file != null && file.exists() && file.length() > 0) {
            scope.launch(Dispatchers.IO) {
                file.setLastModified(System.currentTimeMillis())
            }
            return file
        }
        return null
    }

    fun refreshMemoryIndex() {
        val files = mixtapeDir.listFiles { f -> 
            f.isFile && f.extension.lowercase() in validAudioExtensions && !f.name.endsWith(".tmp")
        } ?: emptyArray()

        val map = HashMap<CanonicalTrackKey, File>(files.size)
        for (f in files) {
            map[CanonicalTrackKey.from(f.nameWithoutExtension)] = f
        }
        _cachedKeyMap.value = map
    }

    fun getCachedTracks(): List<com.nodeplayer.model.Track> {
        val files = _cachedKeyMap.value.values.toList()
        return files.mapIndexed { index, file ->
            com.nodeplayer.model.Track(
                id = -(index + 1000L),
                title = file.nameWithoutExtension,
                artist = "Offline Mixtape",
                album = "Smart Mixtape",
                durationMs = 0L,
                path = file.absolutePath
            )
        }
    }

    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        mixtapeDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    override suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mixtapeDir.listFiles()?.forEach { it.delete() }
            refreshMemoryIndex()
            NodeLog.i("SmartMixtape", "Smart Offline Mixtape partition purged.")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
