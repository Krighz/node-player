package com.nodeplayer.windows.data

import com.nodeplayer.common.platform.KeyValueStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings and theme storage for Windows desktop.
 * Persists key-value properties to %APPDATA%\NodePlayer\settings.properties
 */
class DesktopSettingsStore : KeyValueStore {

    private val settingsFile: File
    private val memoryMap = ConcurrentHashMap<String, String>()

    init {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "NodePlayer")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        settingsFile = File(dir, "settings.properties")
        loadFromFile()
    }

    private fun loadFromFile() {
        if (!settingsFile.exists()) return
        try {
            settingsFile.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val splitIdx = trimmed.indexOf('=')
                    if (splitIdx > 0) {
                        val key = trimmed.substring(0, splitIdx).trim()
                        val value = trimmed.substring(splitIdx + 1).trim()
                        memoryMap[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveToFile() {
        synchronized(this) {
            try {
                settingsFile.bufferedWriter().use { writer ->
                    writer.write("# Node Player Windows Settings\n")
                    for ((key, value) in memoryMap) {
                        writer.write("$key=$value\n")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getString(key: String, defaultValue: String): String {
        return memoryMap[key] ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        memoryMap[key] = value
        saveToFile()
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return memoryMap[key]?.toIntOrNull() ?: defaultValue
    }

    override fun putInt(key: String, value: Int) {
        memoryMap[key] = value.toString()
        saveToFile()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return memoryMap[key]?.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        memoryMap[key] = value.toString()
        saveToFile()
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return memoryMap[key]?.toFloatOrNull() ?: defaultValue
    }

    override fun putFloat(key: String, value: Float) {
        memoryMap[key] = value.toString()
        saveToFile()
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return memoryMap[key]?.toLongOrNull() ?: defaultValue
    }

    override fun putLong(key: String, value: Long) {
        memoryMap[key] = value.toString()
        saveToFile()
    }

    override fun contains(key: String): Boolean {
        return memoryMap.containsKey(key)
    }

    override fun remove(key: String) {
        memoryMap.remove(key)
        saveToFile()
    }

    override fun clear() {
        memoryMap.clear()
        saveToFile()
    }
}
