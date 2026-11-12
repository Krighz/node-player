package com.nodeplayer.music

import com.nodeplayer.music.ui.PlayerViewModel
import com.nodeplayer.music.ui.TrailingSeekDebouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class PlaybackTelemetryCharacterizationTest {

    @Test
    fun testShouldEmitFloatDeltaThresholdAndZeroDrop() {
        assertFalse(PlayerViewModel.shouldEmitFloatDelta(0.0f, 0.0f, 0.002f))
        assertFalse(PlayerViewModel.shouldEmitFloatDelta(0.500f, 0.501f, 0.002f))
        assertTrue(PlayerViewModel.shouldEmitFloatDelta(0.500f, 0.503f, 0.002f))

        // Immediate drop to 0f when stopped even if previous RMS was smaller than epsilon
        assertTrue(PlayerViewModel.shouldEmitFloatDelta(0.001f, 0.0f, 0.002f))

        // Convolver CPU epsilon (0.1f)
        assertFalse(PlayerViewModel.shouldEmitFloatDelta(12.0f, 12.05f, 0.1f))
        assertTrue(PlayerViewModel.shouldEmitFloatDelta(12.0f, 12.15f, 0.1f))
    }

    @Test
    fun testShouldUpdateTrackDurationRules() {
        assertFalse(PlayerViewModel.shouldUpdateTrackDuration(0L, 0L))
        assertTrue(PlayerViewModel.shouldUpdateTrackDuration(0L, 180_000L))
        assertFalse(PlayerViewModel.shouldUpdateTrackDuration(180_000L, 180_500L))
        assertTrue(PlayerViewModel.shouldUpdateTrackDuration(180_000L, 181_500L))
        assertFalse(PlayerViewModel.shouldUpdateTrackDuration(180_000L, -1L))
    }

    @Test
    fun testTrailingSeekDebouncerCoalescesRapidSeeksToFinalTarget() = runBlocking {
        val persistedPositions = CopyOnWriteArrayList<Long>()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val debouncer = TrailingSeekDebouncer(
                scope = scope,
                dispatcher = Dispatchers.Default,
                delayMs = 80L
            ) { targetMs ->
                persistedPositions.add(targetMs)
            }

            debouncer.schedule(1_000L)
            delay(15L)
            debouncer.schedule(2_500L)
            delay(15L)
            debouncer.schedule(4_000L)
            delay(15L)
            debouncer.schedule(7_500L)

            delay(220L)
            assertEquals(listOf(7_500L), persistedPositions.toList())
        } finally {
            scope.cancel()
        }
    }
}
