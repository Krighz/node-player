package com.nodeplayer.library.playlist

import com.nodeplayer.model.Track
import com.nodeplayer.model.playlist.ImportConfig
import com.nodeplayer.model.playlist.ImportMatchStatus
import com.nodeplayer.model.playlist.ImportProgressStep
import com.nodeplayer.model.playlist.ParsedTrackCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaylistImportEngineTest {

    private fun createTrack(
        id: Long,
        title: String,
        artist: String,
        durationMs: Long = 180_000L,
        isrc: String? = null
    ): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = "Test Album",
            durationMs = durationMs,
            path = "nodeplayer://test/$id",
            isrc = isrc
        )
    }

    @Test
    fun testNormalizeDiacriticsAndAccents() {
        assertEquals("cafe", PlaylistImportEngine.normalize("Café"))
        assertEquals("bjork", PlaylistImportEngine.normalize("Björk"))
        assertEquals("motley crue", PlaylistImportEngine.normalize("Mötley Crüe"))
    }

    @Test
    fun testNormalizeBracketsAndFeatTags() {
        assertEquals("starboy", PlaylistImportEngine.normalize("Starboy (feat. Daft Punk)"))
        assertEquals("instant crush", PlaylistImportEngine.normalize("Instant Crush (ft. Julian Casablancas)"))
        assertEquals("comfortably numb", PlaylistImportEngine.normalize("Comfortably Numb [2011 Remastered]"))
        assertEquals("track title", PlaylistImportEngine.normalize("Track Title (Live at Wembley)"))
    }

    @Test
    fun testNormalizePunctuationAndSpacing() {
        assertEquals("rock roll", PlaylistImportEngine.normalize("Rock & Roll!"))
        assertEquals("hello world", PlaylistImportEngine.normalize("  Hello...   World??  "))
    }

    @Test
    fun testIsrcDirectMatchScores10() {
        val candidate = ParsedTrackCandidate(title = "Title A", isrc = "US1234567890")
        val track = createTrack(id = 1, title = "Different Title", artist = "Different Artist", isrc = "US1234567890")
        val score = PlaylistImportEngine.scoreCandidate(candidate, track)
        assertEquals(1.0f, score, 0.001f)
    }

    @Test
    fun testDurationWindowBoundaryCondition() {
        val candidate = ParsedTrackCandidate(title = "Awake", artist = "Tycho", durationMs = 200000L)
        val track2999 = createTrack(id = 1, title = "Awake", artist = "Tycho", durationMs = 202999L)
        val track3000 = createTrack(id = 2, title = "Awake", artist = "Tycho", durationMs = 203000L)
        val track3001 = createTrack(id = 3, title = "Awake", artist = "Tycho", durationMs = 203001L)

        val score2999 = PlaylistImportEngine.scoreCandidate(candidate, track2999, durationWindowMs = 3000L)
        val score3000 = PlaylistImportEngine.scoreCandidate(candidate, track3000, durationWindowMs = 3000L)
        val score3001 = PlaylistImportEngine.scoreCandidate(candidate, track3001, durationWindowMs = 3000L)

        assertEquals(0.95f, score2999, 0.001f)
        assertEquals(0.95f, score3000, 0.001f)
        assertEquals(0.75f, score3001, 0.001f) // Exceeds 3000ms window
    }

    @Test
    fun testArtistTokenOverlapTolerance() {
        val candidate = ParsedTrackCandidate(title = "Strobe", artist = "Deadmau5 feat Kaskade", durationMs = 300000L)
        val track = createTrack(id = 1, title = "Strobe", artist = "deadmau5", durationMs = 301000L)
        val score = PlaylistImportEngine.scoreCandidate(candidate, track)
        assertEquals(0.95f, score, 0.001f)
    }

    @Test
    fun testClassifyScoreThresholds() {
        val config = ImportConfig(autoAcceptThreshold = 0.90f, reviewMinThreshold = 0.60f)
        assertEquals(ImportMatchStatus.MATCHED, PlaylistImportEngine.classifyScore(0.95f, config))
        assertEquals(ImportMatchStatus.MATCHED, PlaylistImportEngine.classifyScore(0.90f, config))
        assertEquals(ImportMatchStatus.NEEDS_REVIEW, PlaylistImportEngine.classifyScore(0.89f, config))
        assertEquals(ImportMatchStatus.NEEDS_REVIEW, PlaylistImportEngine.classifyScore(0.60f, config))
        assertEquals(ImportMatchStatus.NOT_FOUND, PlaylistImportEngine.classifyScore(0.59f, config))
        assertEquals(ImportMatchStatus.NOT_FOUND, PlaylistImportEngine.classifyScore(0.0f, config))
    }

    @Test
    fun testLocalMatchingStageFindsExactTracks() {
        runBlocking {
            val candidates = listOf(
                ParsedTrackCandidate(title = "Resonance", artist = "HOME", durationMs = 212000L),
                ParsedTrackCandidate(title = "Unknown Song", artist = "Ghost Artist", durationMs = 150000L)
            )
            val localCatalog = listOf(
                createTrack(id = 101, title = "Resonance", artist = "HOME", durationMs = 212500L)
            )

            val progressSteps = ArrayList<ImportProgressStep>()
            val results = PlaylistImportEngine.matchCandidates(
                candidates = candidates,
                localCatalog = localCatalog,
                searchProvider = null,
                config = ImportConfig(),
                onProgress = { step, _, _ -> progressSteps.add(step) }
            )

            assertEquals(2, results.size)
            assertEquals(ImportMatchStatus.MATCHED, results[0].status)
            assertEquals(101L, results[0].matchedTrack?.id)
            assertTrue(results[0].isAccepted)

            assertEquals(ImportMatchStatus.NOT_FOUND, results[1].status)
            assertNull(results[1].matchedTrack)
            assertFalse(results[1].isAccepted)
        }
    }

    @Test
    fun testTwoStageOnlineFallback() {
        runBlocking {
            val candidates = listOf(
                ParsedTrackCandidate(title = "Solar Sailer", artist = "Daft Punk", durationMs = 160000L)
            )
            val fakeSearchProvider = OnlineTrackSearchProvider { query, limit ->
                listOf(
                    createTrack(id = 555, title = "Solar Sailer", artist = "Daft Punk", durationMs = 161000L)
                )
            }

            val results = PlaylistImportEngine.matchCandidates(
                candidates = candidates,
                localCatalog = emptyList(),
                searchProvider = fakeSearchProvider,
                config = ImportConfig()
            )

            assertEquals(1, results.size)
            assertEquals(ImportMatchStatus.MATCHED, results[0].status)
            assertEquals(555L, results[0].matchedTrack?.id)
            assertEquals(0.95f, results[0].confidenceScore, 0.001f)
        }
    }

    @Test
    fun testCoroutineCancellationAbortsExecution() {
        runBlocking {
            val candidates = (1..100).map {
                ParsedTrackCandidate(title = "Track $it", artist = "Artist $it")
            }
            var cancelled = false
            val job = launch {
                try {
                    PlaylistImportEngine.matchCandidates(
                        candidates = candidates,
                        localCatalog = emptyList(),
                        searchProvider = { _, _ -> emptyList() },
                        config = ImportConfig()
                    )
                } catch (e: CancellationException) {
                    cancelled = true
                }
            }
            job.cancel()
            job.join()
            assertTrue(cancelled || job.isCancelled)
        }
    }
}
