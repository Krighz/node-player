package com.nodeplayer.network

import com.nodeplayer.network.sync.HlcClock
import com.nodeplayer.network.sync.HlcTimestamp
import com.nodeplayer.network.sync.NodeConnectTransferService
import com.nodeplayer.network.sync.SyncEngine
import com.nodeplayer.network.test.HeadlessTestPeer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SyncEngineTest {

    private var peerA: HeadlessTestPeer? = null
    private var peerB: HeadlessTestPeer? = null

    @After
    fun teardown() {
        peerA?.stop()
        peerB?.stop()
    }

    @Test
    fun testHlcOrderingAndConflictResolution() {
        val clockA = HlcClock("device-A")
        val clockB = HlcClock("device-B")

        val tA1 = clockA.now()
        val tB1 = clockB.now()

        // Update clock A with B
        val tA2 = clockA.update(tB1)
        assertTrue("Updated HLC must be strictly greater than remote timestamp", tA2 > tB1)
        assertTrue("Updated HLC must be strictly greater than previous local timestamp", tA2 > tA1)
    }

    @Test
    fun testTombstonePreventsResurrection() {
        val dirA = File.createTempFile("undo_a_", "").apply { delete(); mkdirs() }
        val dirB = File.createTempFile("undo_b_", "").apply { delete(); mkdirs() }

        try {
            val engineA = SyncEngine("device-A", dirA)
            val engineB = SyncEngine("device-B", dirB)

            // 1. Device A creates playlist
            val pl = engineA.putPlaylist("Chill Beats", listOf("fp_1", "fp_2"))
            assertEquals(1, engineA.getActivePlaylists().size)

            // 2. Sync to Device B
            engineB.mergeRemotePackage(engineA.createCurrentPackage())
            assertEquals(1, engineB.getActivePlaylists().size)

            // 3. Device A deletes playlist (tombstone created with newer HLC)
            engineA.deletePlaylist(pl.playlistId)
            assertEquals(0, engineA.getActivePlaylists().size)

            // 4. Device B (still has old playlist) syncs to Device A
            // Stale replica on B must NOT resurrect deleted item on A!
            engineA.mergeRemotePackage(engineB.createCurrentPackage())
            assertEquals("Tombstone must prevent resurrection from older replica", 0, engineA.getActivePlaylists().size)

            // 5. Device A syncs back to Device B -> Device B also deletes it
            engineB.mergeRemotePackage(engineA.createCurrentPackage())
            assertEquals(0, engineB.getActivePlaylists().size)
        } finally {
            dirA.deleteRecursively()
            dirB.deleteRecursively()
        }
    }

    @Test
    fun testStatsUnionMerge() {
        val dir = File.createTempFile("undo_stats_", "").apply { delete(); mkdirs() }
        try {
            val engineA = SyncEngine("device-A", dir)
            val engineB = SyncEngine("device-B", dir)

            // Device A played track 5 times, last played at t = 1000
            for (i in 0 until 5) engineA.recordPlay("track_fp_99", 1000L)

            // Device B played track 8 times, last played at t = 5000
            for (i in 0 until 8) engineB.recordPlay("track_fp_99", 5000L)

            // Merge B into A
            engineA.mergeRemotePackage(engineB.createCurrentPackage())

            val history = engineA.getHistoryRecords().find { it.trackFingerprint == "track_fp_99" }
            assertNotNull(history)
            assertEquals("Play count should be max(5, 8)", 8, history!!.playCount)
            assertEquals("Last played should be max(1000, 5000)", 5000L, history.lastPlayedTimestampMs)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun testThreeSnapshotUndoEngine() {
        val dir = File.createTempFile("undo_stack_", "").apply { delete(); mkdirs() }
        try {
            val engine = SyncEngine("device-A", dir)
            engine.putPlaylist("Original Local Playlist", listOf("track_1"))

            val remotePkg1 = SyncEngine("device-B", dir).apply {
                putPlaylist("Remote Playlist 1", listOf("track_rem_1"))
            }.createCurrentPackage()

            val remotePkg2 = SyncEngine("device-B", dir).apply {
                putPlaylist("Remote Playlist 2", listOf("track_rem_2"))
            }.createCurrentPackage()

            // Merge 1
            engine.mergeRemotePackage(remotePkg1)
            assertEquals(2, engine.getActivePlaylists().size)
            assertEquals(1, engine.availableUndoCount)

            // Merge 2
            engine.mergeRemotePackage(remotePkg2)
            assertEquals(3, engine.getActivePlaylists().size)
            assertEquals(2, engine.availableUndoCount)

            // Undo 1: Reverts Merge 2
            val undo1 = engine.undoLastSync()
            assertTrue(undo1)
            assertEquals(2, engine.getActivePlaylists().size)
            assertNull(engine.getActivePlaylists().find { it.name == "Remote Playlist 2" })

            // Undo 2: Reverts Merge 1 back to original local playlist
            val undo2 = engine.undoLastSync()
            assertTrue(undo2)
            assertEquals(1, engine.getActivePlaylists().size)
            assertEquals("Original Local Playlist", engine.getActivePlaylists()[0].name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun testChunkedTransferOverMtls() = runBlocking {
        val dirA = File.createTempFile("undo_tr_a_", "").apply { delete(); mkdirs() }
        val dirB = File.createTempFile("undo_tr_b_", "").apply { delete(); mkdirs() }

        try {
            val engineA = SyncEngine("Node-PC", dirA)
            val engineB = SyncEngine("Node-Phone", dirB)

            // Create 20 playlists on PC
            for (i in 1..20) {
                engineA.putPlaylist("PC Playlist #$i", listOf("fp_$i", "fp_${i+1}"))
            }
            engineA.setFavorite("favorite_track_42", true)
            engineA.putEqProfile("V-Shape", listOf(4.5f, 3.0f, 1.0f, -0.5f, 0f, 0.5f, 1.5f, 3.0f, 4.0f, 5.0f), 4.0f)

            val serviceA = NodeConnectTransferService(engineA)
            val serviceB = NodeConnectTransferService(engineB)

            val peerA = HeadlessTestPeer("Node-PC", "Windows")
            val peerB = HeadlessTestPeer("Node-Phone", "Android")
            this@SyncEngineTest.peerA = peerA
            this@SyncEngineTest.peerB = peerB

            peerA.trustPeer(peerB)
            peerB.trustPeer(peerA)

            peerB.onFrameReceived = { frame, conn ->
                serviceB.handleFrame(frame, conn)
            }

            val port = peerB.startServer(0)
            val connA = peerA.connectTo("127.0.0.1", port)
            delay(100)

            // PC pushes sync package in chunked slices to Phone
            serviceA.pushSyncPackage(connA)
            delay(250)

            // Phone should now have all 20 playlists, the favorite, and the EQ profile!
            assertEquals(20, engineB.getActivePlaylists().size)
            assertEquals(1, engineB.getActiveFavorites().size)
            assertEquals("favorite_track_42", engineB.getActiveFavorites()[0].trackFingerprint)

            val eq = engineB.getActiveEqProfiles().find { it.profileName == "V-Shape" }
            assertNotNull(eq)
            assertEquals(4.0f, eq!!.bassBoostDb, 0.001f)

            connA.close()
        } finally {
            dirA.deleteRecursively()
            dirB.deleteRecursively()
        }
    }
}
