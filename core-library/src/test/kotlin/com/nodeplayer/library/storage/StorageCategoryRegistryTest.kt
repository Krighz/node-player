package com.nodeplayer.library.storage

import com.nodeplayer.model.storage.StorageCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StorageCategoryRegistryTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "np_storage_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        // Unregister any test categories
        listOf("test_cat_1", "test_cat_dir", StorageCategoryRegistry.CATEGORY_AUDIO_CACHE).forEach {
            StorageCategoryRegistry.unregisterCategory(it)
        }
    }

    @Test
    fun testRegisterAndCalculateUsage() = runBlocking {
        val testCat = object : StorageCategory {
            override val id: String = "test_cat_1"
            override val displayName: String = "Test Category"
            override val description: String = "Test Description"
            override val clearable: Boolean = true
            override suspend fun sizeBytes(): Long = 1024L * 1024L * 5L // 5MB
            override suspend fun clear(): Result<Unit> = Result.success(Unit)
        }

        StorageCategoryRegistry.registerCategory(testCat)
        val fetched = StorageCategoryRegistry.getCategory("test_cat_1")
        assertNotNull(fetched)
        assertEquals("Test Category", fetched.displayName)

        val stats = StorageCategoryRegistry.calculateTotalUsage()
        val catStat = stats.find { it.categoryId == "test_cat_1" }
        assertNotNull(catStat)
        assertEquals(5L * 1024L * 1024L, catStat.sizeBytes)
        assertEquals("5.0 MB", catStat.sizeMbFormatted)
    }

    @Test
    fun testClearCategoryProtected() = runBlocking {
        val protectedCat = object : StorageCategory {
            override val id: String = "protected_cat"
            override val displayName: String = "Protected"
            override val description: String = "Cannot be cleared"
            override val clearable: Boolean = false
            override suspend fun sizeBytes(): Long = 100L
            override suspend fun clear(): Result<Unit> = Result.success(Unit)
        }

        StorageCategoryRegistry.registerCategory(protectedCat)
        val result = StorageCategoryRegistry.clearCategory("protected_cat")
        assertFalse(result.isSuccess, "Protected category should not be clearable")
        StorageCategoryRegistry.unregisterCategory("protected_cat")
    }

    @Test
    fun testDirectoryCategorySizeAndClear() = runBlocking {
        val testSubDir = File(tempDir, "cache_dir")
        testSubDir.mkdirs()
        val file1 = File(testSubDir, "sample.bin")
        file1.writeBytes(ByteArray(1024)) // 1 KB

        val dirCategory = StorageCategoryRegistry.createDirectoryCategory(
            id = "test_cat_dir",
            displayName = "Dir Category",
            description = "Backing directory test",
            directory = testSubDir
        )

        StorageCategoryRegistry.registerCategory(dirCategory)
        assertEquals(1024L, dirCategory.sizeBytes())

        val clearRes = StorageCategoryRegistry.clearCategory("test_cat_dir")
        assertTrue(clearRes.isSuccess)
        assertEquals(0L, dirCategory.sizeBytes())
        assertFalse(file1.exists(), "File should be deleted on clear")
    }
}
