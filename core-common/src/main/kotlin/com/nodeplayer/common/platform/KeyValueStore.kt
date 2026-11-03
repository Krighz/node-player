package com.nodeplayer.common.platform

interface KeyValueStore {
    fun getString(key: String, defaultValue: String = ""): String
    fun putString(key: String, value: String)

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)

    fun getFloat(key: String, defaultValue: Float = 0f): Float
    fun putFloat(key: String, value: Float)

    fun getInt(key: String, defaultValue: Int = 0): Int
    fun putInt(key: String, value: Int)

    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun putLong(key: String, value: Long)

    fun contains(key: String): Boolean
    fun remove(key: String)
    fun clear()
}
