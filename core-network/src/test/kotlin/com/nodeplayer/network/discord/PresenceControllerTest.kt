package com.nodeplayer.network.discord

import com.nodeplayer.common.platform.KeyValueStore
import com.nodeplayer.model.presence.PresenceState
import com.nodeplayer.model.presence.PresenceStatus
import com.nodeplayer.model.presence.PresenceTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresenceControllerTest {

    private class FakeKeyValueStore : KeyValueStore {
        private val map = mutableMapOf<String, Any>()

        override fun getString(key: String, defaultValue: String): String = (map[key] as? String) ?: defaultValue
        override fun putString(key: String, value: String) { map[key] = value }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = (map[key] as? Boolean) ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }

        override fun getInt(key: String, defaultValue: Int): Int = (map[key] as? Int) ?: defaultValue
        override fun putInt(key: String, value: Int) { map[key] = value }

        override fun getLong(key: String, defaultValue: Long): Long = (map[key] as? Long) ?: defaultValue
        override fun putLong(key: String, value: Long) { map[key] = value }

        override fun getFloat(key: String, defaultValue: Float): Float = (map[key] as? Float) ?: defaultValue
        override fun putFloat(key: String, value: Float) { map[key] = value }

        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun remove(key: String) { map.remove(key) }
        override fun clear() { map.clear() }
    }

    private class FakePresenceTransport : PresenceTransport {
        var isConnected = false
        var connectResult = true
        val sentActivities = mutableListOf<String>()
        var clearCount = 0
        var closeCount = 0

        override suspend fun connect(): Boolean {
            isConnected = connectResult
            return connectResult
        }

        override suspend fun setActivity(json: String): Boolean {
            sentActivities.add(json)
            return true
        }

        override suspend fun clear(): Boolean {
            clearCount++
            return true
        }

        override fun close() {
            closeCount++
            isConnected = false
        }
    }

    private lateinit var store: FakeKeyValueStore
    private lateinit var transport: FakePresenceTransport
    private var currentTime: Long = 1_000_000L

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        transport = FakePresenceTransport()
        currentTime = 1_000_000L
    }

    @Test
    fun testDisabledByDefault() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val controller = PresenceController(
            transport = transport,
            store = store,
            scope = this,
            clock = { currentTime }
        )

        assertEquals(PresenceStatus.DISABLED, controller.status.value)
        assertEquals(0, transport.sentActivities.size)
    }

    @Test
    fun testAppIdRequiredWhenEnabledWithoutId() = runTest {
        store.putBoolean(PresenceController.KEY_ENABLED, true)
        store.putString(PresenceController.KEY_APP_ID, "")

        val controller = PresenceController(
            transport = transport,
            store = store,
            scope = this,
            clock = { currentTime }
        )

        assertEquals(PresenceStatus.APP_ID_REQUIRED, controller.status.value)
    }

    @Test
    fun testNormalPlaybackDispatchesActivity() = runTest {
        store.putBoolean(PresenceController.KEY_ENABLED, true)
        store.putString(PresenceController.KEY_APP_ID, "12345678")

        val controller = PresenceController(
            transport = transport,
            store = store,
            scope = this,
            clock = { currentTime }
        )

        advanceTimeBy(100)

        val trackState = PresenceState(
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 200_000L,
            positionMs = 10_000L,
            isPlaying = true
        )

        controller.updatePlayback(trackState)
        advanceTimeBy(100)

        assertEquals(1, transport.sentActivities.size)
        assertTrue(transport.sentActivities.first().contains("Test Song"))
        assertEquals(PresenceStatus.CONNECTED, controller.status.value)
    }

    @Test
    fun testPauseClearsActivityImmediately() = runTest {
        store.putBoolean(PresenceController.KEY_ENABLED, true)
        store.putString(PresenceController.KEY_APP_ID, "12345678")
        store.putBoolean(PresenceController.KEY_SHOW_WHILE_PAUSED, false)

        val controller = PresenceController(
            transport = transport,
            store = store,
            scope = this,
            clock = { currentTime }
        )

        advanceTimeBy(100)

        val playing = PresenceState(title = "Song A", artist = "Artist A", durationMs = 180_000L, positionMs = 20_000L, isPlaying = true)
        controller.updatePlayback(playing)
        advanceTimeBy(100)
        assertEquals(1, transport.sentActivities.size)

        val paused = playing.copy(isPlaying = false)
        controller.updatePlayback(paused)
        advanceTimeBy(100)

        assertEquals(1, transport.clearCount)
    }

    @Test
    fun testIncognitoImmediatelyClearsAndSuppresses() = runTest {
        store.putBoolean(PresenceController.KEY_ENABLED, true)
        store.putString(PresenceController.KEY_APP_ID, "12345678")

        val controller = PresenceController(
            transport = transport,
            store = store,
            scope = this,
            clock = { currentTime }
        )

        advanceTimeBy(100)

        val playing = PresenceState(title = "Secret Song", artist = "Secret Artist", isPlaying = true)
        controller.updatePlayback(playing)
        advanceTimeBy(100)
        assertEquals(1, transport.sentActivities.size)

        // Turn on incognito
        controller.setIncognito(true)
        advanceTimeBy(100)

        assertTrue(controller.isIncognito.value)
        assertTrue(transport.clearCount >= 1)

        // Subsequent playbacks are suppressed
        controller.updatePlayback(playing.copy(title = "Another Secret"))
        advanceTimeBy(100)
        assertEquals(1, transport.sentActivities.size)
    }

    @Test
    fun testDebounceCoalescesRapidUpdatesWithin15s() = runTest {
        store.putBoolean(PresenceController.KEY_ENABLED, true)
        store.putString(PresenceController.KEY_APP_ID, "12345678")

        val controller = PresenceController(
            transport = transport,
            store = store,
            scope = this,
            clock = { currentTime }
        )

        advanceTimeBy(100)

        // First track
        val track1 = PresenceState(title = "Song 1", artist = "Artist 1", isPlaying = true)
        controller.updatePlayback(track1)
        advanceTimeBy(100)
        assertEquals(1, transport.sentActivities.size)

        // Rapid position update 2 seconds later (not a 3000ms seek jump)
        currentTime += 2_000L
        val track1Tick = track1.copy(positionMs = 2_000L)
        controller.updatePlayback(track1Tick)
        advanceTimeBy(100)

        // Not sent immediately due to 15s debounce
        assertEquals(1, transport.sentActivities.size)

        // Another rapid update 3 seconds later
        currentTime += 3_000L
        val track1Tick2 = track1.copy(positionMs = 5_000L)
        controller.updatePlayback(track1Tick2)

        // Advance enough time to exceed the 13,000ms delay
        currentTime += 15_000L
        advanceTimeBy(16_000L)

        // Now the coalesced latest state was sent
        assertEquals(2, transport.sentActivities.size)
    }
}
