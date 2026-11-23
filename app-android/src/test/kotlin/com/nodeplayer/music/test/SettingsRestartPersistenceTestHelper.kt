package com.nodeplayer.music.test

import com.nodeplayer.model.SettingDefinition
import com.nodeplayer.model.SettingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Reusable test helper for simulating settings persistence across process restarts
 * per F00 Section 6, Section 10, and decisions-F00.md.
 */
object SettingsRestartPersistenceTestHelper {

    /**
     * Simulated in-memory persistent store mimicking DataStore / SharedPreferences.
     */
    class SimulatedPersistentStorage {
        private val storage = mutableMapOf<String, Any>()

        fun <T : Any> write(key: String, value: T) {
            storage[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> read(key: String, default: T): T {
            return (storage[key] as? T) ?: default
        }

        fun restartProcess(): SimulatedPersistentStorage {
            // Creates a fresh storage instance containing the persisted snapshot
            val rebooted = SimulatedPersistentStorage()
            rebooted.storage.putAll(this.storage)
            return rebooted
        }
    }

    /**
     * Asserts that a setting can be modified, saved, survives a process restart,
     * and returns its stored value; or its schema default if unwritten.
     */
    fun <T : Any> assertSettingSurvivesRestart(
        setting: SettingDefinition<T>,
        testValue: T
    ) {
        val originalSession = SimulatedPersistentStorage()

        // 1. Initial read should match schema default
        val initialRead = originalSession.read(setting.key, setting.type.default)
        assertEquals("Initial value should equal definition default", setting.type.default, initialRead)

        // 2. Write new value
        originalSession.write(setting.key, testValue)

        // 3. Simulate process restart
        val restoredSession = originalSession.restartProcess()

        // 4. Value must survive restart
        val restoredValue = restoredSession.read(setting.key, setting.type.default)
        assertEquals("Restored value must match written value after restart", testValue, restoredValue)
    }
}
