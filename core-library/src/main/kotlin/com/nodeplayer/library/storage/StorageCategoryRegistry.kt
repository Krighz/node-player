package com.nodeplayer.library.storage

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.storage.StorageCategory
import com.nodeplayer.model.storage.StorageUsageStats
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified registry orchestrating storage usage reporting and per-category cache eviction.
 * Decouples platform-specific file directories from pure UI consumption.
 */
object StorageCategoryRegistry {

    private const val TAG = "storage/registry"
    private val categories = ConcurrentHashMap<String, StorageCategory>()

    const val CATEGORY_AUDIO_CACHE = "audio_cache"
    const val CATEGORY_IMAGE_CACHE = "image_cache"
    const val CATEGORY_LYRICS_CACHE = "lyrics_cache"
    const val CATEGORY_FEED_CACHE = "feed_cache"
    const val CATEGORY_TASTE_CACHE = "taste_cache"
    const val CATEGORY_OFFLINE_DOWNLOADS = "offline_downloads"

    /**
     * Registers a storage category handler.
     */
    fun registerCategory(category: StorageCategory) {
        categories[category.id] = category
        NodeLog.d(TAG, "Registered storage category: ${category.id}")
    }

    /**
     * Unregisters a category by ID.
     */
    fun unregisterCategory(id: String) {
        categories.remove(id)
    }

    /**
     * Retrieves all registered storage categories in standard display order.
     */
    fun getAllCategories(): List<StorageCategory> {
        val order = listOf(
            CATEGORY_OFFLINE_DOWNLOADS,
            CATEGORY_AUDIO_CACHE,
            CATEGORY_IMAGE_CACHE,
            CATEGORY_LYRICS_CACHE,
            CATEGORY_FEED_CACHE,
            CATEGORY_TASTE_CACHE
        )
        return categories.values.sortedBy { cat ->
            val idx = order.indexOf(cat.id)
            if (idx != -1) idx else 999
        }
    }

    /**
     * Gets a specific category by ID.
     */
    fun getCategory(id: String): StorageCategory? = categories[id]

    /**
     * Computes usage statistics across all registered categories.
     */
    suspend fun calculateTotalUsage(): List<StorageUsageStats> {
        return getAllCategories().map { cat ->
            val size = try {
                cat.sizeBytes()
            } catch (e: Exception) {
                NodeLog.w(TAG, "Failed calculating size for ${cat.id}: ${e.message}")
                0L
            }
            StorageUsageStats(
                categoryId = cat.id,
                displayName = cat.displayName,
                sizeBytes = size,
                clearable = cat.clearable
            )
        }
    }

    /**
     * Clears a specific category by ID.
     */
    suspend fun clearCategory(id: String): Result<Unit> {
        val cat = categories[id] ?: return Result.failure(IllegalArgumentException("Category $id not registered"))
        if (!cat.clearable) {
            return Result.failure(IllegalStateException("Category $id is protected and cannot be cleared"))
        }

        return try {
            val res = cat.clear()
            NodeLog.i(TAG, "Category $id cleared: isSuccess=${res.isSuccess}")
            res
        } catch (e: Exception) {
            NodeLog.w(TAG, "Error clearing category $id: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Helper to create a directory-backed storage category.
     */
    fun createDirectoryCategory(
        id: String,
        displayName: String,
        description: String,
        directory: File,
        clearable: Boolean = true,
        limitSettingKey: String? = null
    ): StorageCategory {
        return object : StorageCategory {
            override val id: String = id
            override val displayName: String = displayName
            override val description: String = description
            override val clearable: Boolean = clearable
            override val limitSettingKey: String? = limitSettingKey

            override suspend fun sizeBytes(): Long {
                return calculateDirectorySize(directory)
            }

            override suspend fun clear(): Result<Unit> {
                return runCatching {
                    if (directory.exists()) {
                        directory.listFiles()?.forEach { file ->
                            file.deleteRecursively()
                        }
                    }
                    Unit
                }
            }
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += if (child.isDirectory) calculateDirectorySize(child) else child.length()
        }
        return total
    }
}
