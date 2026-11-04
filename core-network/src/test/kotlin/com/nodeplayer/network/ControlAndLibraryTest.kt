package com.nodeplayer.network

import com.nodeplayer.library.repository.TrackRepository
import com.nodeplayer.model.Album
import com.nodeplayer.model.Artist
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.model.Playlist
import com.nodeplayer.model.Track
import com.nodeplayer.network.protocol.ControlAction
import com.nodeplayer.network.protocol.ControlStateEventMessage
import com.nodeplayer.network.service.LocalPlayerController
import com.nodeplayer.network.service.NodeConnectControlService
import com.nodeplayer.network.service.NodeConnectLibraryService
import com.nodeplayer.network.service.NodeConnectMirrorService
import com.nodeplayer.network.test.HeadlessTestPeer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ControlAndLibraryTest {

    private var peerHost: HeadlessTestPeer? = null
    private var peerClient: HeadlessTestPeer? = null

    @After
    fun teardown() {
        peerHost?.stop()
        peerClient?.stop()
    }

    private class MockTrackRepository(private val tracks: List<Track>) : TrackRepository {
        override fun getAllTracks(): Flow<List<Track>> = emptyFlow()
        override suspend fun getAllTracksList(): List<Track> = tracks
        override suspend fun getTrackById(id: Long): Track? = tracks.find { it.id == id }
        override suspend fun insertTracks(tracks: List<Track>) {}
        override suspend fun clearTracks() {}
        override suspend fun incrementPlayCount(id: Long) {}
        override suspend fun setFavorite(id: Long, isFav: Boolean) {}
        override fun getFavoriteTracks(): Flow<List<Track>> = emptyFlow()
        override fun getAllScannedFolders(): Flow<List<MusicFolder>> = emptyFlow()
        override suspend fun insertScannedFolders(folders: List<MusicFolder>) {}
        override suspend fun setFolderEnabled(path: String, isEnabled: Boolean) {}
        override suspend fun setTracksHiddenForFolder(folderPrefix: String, isHidden: Boolean) {}
        override fun getAllPlaylists(): Flow<List<Playlist>> = emptyFlow()
        override suspend fun insertPlaylist(playlist: Playlist): Long = 1L
        override suspend fun deletePlaylist(playlistId: Long) {}
        override fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> = emptyFlow()
        override suspend fun updateTrackAudioAnalysis(id: Long, status: String, confidence: Float, cutoffHz: Float, lufs: Float, drScore: Int, drLabel: String) {}
        override suspend fun recordTrackPlayed(id: Long, timestamp: Long) {}
    }

    @Test
    fun testRemoteLibraryQueryAndPrivacy() = runBlocking {
        val hostTracks = listOf(
            Track(
                id = 101L,
                title = "Mosaic",
                artist = "Darbee",
                album = "Vivid",
                durationMs = 210000L,
                path = "C:\\Sensitive\\Users\\Secret\\Music\\mosaic.flac", // Sensitive private local path!
                sampleRate = 48000,
                bitDepth = 24,
                isLossless = true
            ),
            Track(
                id = 102L,
                title = "Kid A",
                artist = "Radiohead",
                album = "Kid A",
                durationMs = 284000L,
                path = "D:\\Private\\Music\\kid_a.flac",
                sampleRate = 44100,
                bitDepth = 16,
                isLossless = true
            )
        )

        val repo = MockTrackRepository(hostTracks)
        val hostLibService = NodeConnectLibraryService(repo)
        val clientLibService = NodeConnectLibraryService()

        val host = HeadlessTestPeer("Host-PC", "Windows")
        val client = HeadlessTestPeer("Client-Phone", "Android")
        peerHost = host
        peerClient = client

        host.onFrameReceived = { frame, conn -> hostLibService.handleFrame(frame, conn) }
        client.onFrameReceived = { frame, conn -> clientLibService.handleFrame(frame, conn) }

        host.trustPeer(client)
        client.trustPeer(host)

        val port = host.startServer(0)
        val clientConn = client.connectTo("127.0.0.1", port)
        delay(100)

        // Client queries remote library
        val result = clientLibService.queryRemoteLibrary(clientConn, query = "Mosaic", timeoutMs = 4000L)
        assertNotNull("Result should not be null", result)
        assertEquals(1, result!!.tracks.size)

        val track = result.tracks[0]
        assertEquals("Mosaic", track.title)
        assertEquals("Darbee", track.artist)
        assertEquals(48000, track.sampleRate)
        assertEquals(24, track.bitDepth)

        // Golden Rule: Sensitive local filesystem paths must NEVER be leaked over network!
        assertFalse("Local path must not contain C: or sensitive folder", track.path.contains("Sensitive"))
        assertTrue("Path should be virtual remote URI", track.path.startsWith("nodeconnect://remote/track/101"))

        clientConn.close()
    }

    @Test
    fun testRemoteControlAndMirror() = runBlocking {
        val played = AtomicBoolean(false)
        val seekPos = AtomicLong(0L)

        val mockController = object : LocalPlayerController {
            override fun play() { played.set(true) }
            override fun pause() { played.set(false) }
            override fun playPause() { played.set(!played.get()) }
            override fun seekTo(positionMs: Long) { seekPos.set(positionMs) }
            override fun skipToNext() {}
            override fun skipToPrevious() {}
            override fun setVolume(volume: Float) {}
            override fun toggleShuffle() {}
            override fun cycleRepeat() {}
        }

        val hostControl = NodeConnectControlService(mockController)
        val clientControl = NodeConnectControlService()
        val mirrorService = NodeConnectMirrorService()

        val host = HeadlessTestPeer("Host-PC", "Windows")
        val client = HeadlessTestPeer("Client-Phone", "Android")
        peerHost = host
        peerClient = client

        host.onFrameReceived = { frame, conn -> hostControl.handleFrame(frame, conn) }
        client.onFrameReceived = { frame, conn -> 
            clientControl.handleFrame(frame, conn) || mirrorService.handleFrame(frame) 
        }

        host.trustPeer(client)
        client.trustPeer(host)

        val port = host.startServer(0)
        val clientConn = client.connectTo("127.0.0.1", port)
        delay(100)

        // Client commands Host to Play and Seek
        clientControl.sendRemoteCommand(clientConn, ControlAction.PLAY)
        clientControl.sendRemoteCommand(clientConn, ControlAction.SEEK, seekPositionMs = 54000L)

        delay(150)
        assertTrue("Host should have executed play", played.get())
        assertEquals("Host should have executed seek to 54s", 54000L, seekPos.get())

        // Host registers controller and broadcasts state
        assertEquals(1, host.activeConnections.size)
        hostControl.registerController(host.activeConnections[0])
        val state = ControlStateEventMessage(
            isPlaying = true,
            positionMs = 54000L,
            durationMs = 210000L,
            volume = 0.9f,
            trackTitle = "Mosaic",
            artist = "Darbee",
            album = "Vivid",
            formatBadge = "FLAC 24-bit / 48kHz"
        )
        hostControl.broadcastLocalState(state)

        delay(150)
        val clientState = clientControl.remotePlaybackState.value
        assertNotNull(clientState)
        assertEquals("Mosaic", clientState!!.trackTitle)
        assertEquals(54000L, clientState.positionMs)
        assertEquals("FLAC 24-bit / 48kHz", clientState.formatBadge)

        clientConn.close()
    }
}
