package com.nodeplayer.library

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PlaybackStatsTrackerTest {

    private val utcZone = ZoneId.of("UTC")

    @Test
    fun testPlayCountThresholdAt30Seconds() {
        val tracker = PlaybackStatsTracker(utcZone)
        var playsRecorded = 0
        tracker.onPlayCountThresholdReached = { playsRecorded++ }

        val startTime = 1000000L
        tracker.onTrackStarted(trackId = 42L, currentEpochMs = startTime)

        // Advance 20 seconds
        tracker.tick(currentEpochMs = startTime + 20_000L)
        assertEquals(0, playsRecorded)

        // Advance another 15 seconds (total 35s)
        tracker.tick(currentEpochMs = startTime + 35_000L)
        assertEquals(1, playsRecorded)

        // Further ticks on same track should not record duplicate play
        tracker.tick(currentEpochMs = startTime + 60_000L)
        assertEquals(1, playsRecorded)
    }

    @Test
    fun testPauseDoesNotCountListeningTime() {
        val tracker = PlaybackStatsTracker(utcZone)
        var totalSec = 0L
        tracker.onListeningTimeAccumulated = { _, sec -> totalSec += sec }

        val start = 1000000L
        tracker.onTrackStarted(trackId = 1L, currentEpochMs = start)

        // Play for 10s
        tracker.tick(currentEpochMs = start + 10_000L)
        assertEquals(10L, totalSec)

        // Pause
        tracker.onPlayStateChanged(playing = false, currentEpochMs = start + 10_000L)

        // 30 seconds pass while paused
        tracker.tick(currentEpochMs = start + 40_000L)
        assertEquals("Paused time must not count", 10L, totalSec)

        // Resume and play 5s
        tracker.onPlayStateChanged(playing = true, currentEpochMs = start + 40_000L)
        tracker.tick(currentEpochMs = start + 45_000L)
        assertEquals(15L, totalSec)
    }

    @Test
    fun testSeekDoesNotAddOrRemoveTime() {
        val tracker = PlaybackStatsTracker(utcZone)
        var totalSec = 0L
        tracker.onListeningTimeAccumulated = { _, sec -> totalSec += sec }

        val start = 1000000L
        tracker.onTrackStarted(trackId = 1L, currentEpochMs = start)
        tracker.tick(currentEpochMs = start + 10_000L)
        assertEquals(10L, totalSec)

        // User seeks 2 minutes forward in the track
        tracker.onSeek(start + 10_000L)

        // 5s of real listening passes after seek
        tracker.tick(currentEpochMs = start + 15_000L)
        assertEquals("Seek should not add jumped track time", 15L, totalSec)
    }

    @Test
    fun testMidnightSplitCalculatesBothDaysAccurately() {
        val tracker = PlaybackStatsTracker(utcZone)
        val accumulated = HashMap<String, Long>()
        tracker.onListeningTimeAccumulated = { date, sec ->
            accumulated[date] = (accumulated[date] ?: 0L) + sec
        }

        // Start 20 seconds before UTC midnight on 2026-11-01
        val midnight = ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, utcZone).toInstant().toEpochMilli()

        val start = midnight - 20_000L // 23:59:40
        tracker.onTrackStarted(trackId = 1L, currentEpochMs = start)

        // Tick 35s later: 00:00:15 (20s on day 1, 15s on day 2)
        tracker.tick(currentEpochMs = midnight + 15_000L)

        assertEquals(20L, accumulated["2026-11-01"])
        assertEquals(15L, accumulated["2026-11-02"])
    }

    @Test
    fun testServiceRestartMidTrackPreservesState() {
        val tracker = PlaybackStatsTracker(utcZone)
        var playsRecorded = 0
        tracker.onPlayCountThresholdReached = { playsRecorded++ }

        // Restores a track that was previously played for 25s before service restart
        tracker.restoreState(trackId = 99L, previouslyListenedMs = 25_000L, playAlreadyRecorded = false)
        tracker.onPlayStateChanged(playing = true)

        // After 10s more, threshold (30s) should be reached
        val start = System.currentTimeMillis()
        tracker.tick(currentEpochMs = start + 10_000L)

        assertEquals(1, playsRecorded)
    }
}
