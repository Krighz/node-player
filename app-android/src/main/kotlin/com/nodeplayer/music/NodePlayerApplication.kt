package com.nodeplayer.music

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.room.Room
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nodeplayer.common.logging.LogLevel
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.music.data.NodeDatabase
import com.nodeplayer.music.library.AudioArtworkFetcher
import com.nodeplayer.music.library.LibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NodePlayerApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.TrackFactory())
                add(AudioArtworkFetcher.FileFactory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    lateinit var database: NodeDatabase
        private set

    lateinit var libraryScanner: LibraryScanner
        private set

    lateinit var downloadQueueManager: com.nodeplayer.library.downloader.DownloadQueueManager
        private set

    val smartMixtapeManager by lazy {
        com.nodeplayer.music.mixtape.SmartMixtapeManager(this)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var asyncBootstrapStarted = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        NodeLog.addSink { level, tag, message, throwable ->
            val androidPriority = when (level) {
                LogLevel.VERBOSE -> android.util.Log.VERBOSE
                LogLevel.DEBUG -> android.util.Log.DEBUG
                LogLevel.INFO -> android.util.Log.INFO
                LogLevel.WARN -> android.util.Log.WARN
                LogLevel.ERROR -> android.util.Log.ERROR
            }
            if (throwable != null) {
                android.util.Log.println(androidPriority, tag, "$message\n${android.util.Log.getStackTraceString(throwable)}")
            } else {
                android.util.Log.println(androidPriority, tag, message)
            }
        }
        NodeLog.isVerbose = BuildConfig.DEBUG
        // Keep GlassDebug opt-in so debug benchmarks and normal scrolling never pay per-frame nanoTime/logging overhead
        com.nodeplayer.ui.debug.GlassDebug.isEnabled = false
        NodeLog.i("app/lifecycle", "NodePlayerApplication initialized")

        com.nodeplayer.common.media.CipherSolver.current = com.nodeplayer.music.cipher.AndroidCipherSolver(this)

        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        database = Room.databaseBuilder(
            applicationContext,
            NodeDatabase::class.java,
            "node_player.db"
        )
            .addMigrations(NodeDatabase.MIGRATION_6_7, NodeDatabase.MIGRATION_7_8, NodeDatabase.MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .build()

        libraryScanner = LibraryScanner(applicationContext)
        com.nodeplayer.network.cache.HomeFeedDiskCache.defaultCacheDir = cacheDir.resolve("home_feeds")
        initializeDownloadAndStorage()

        com.nodeplayer.ui.components.PlatformArtworkProvider.remoteImageFetcher = { url, maxSizePx ->
            val targetPx = maxSizePx.coerceIn(
                AudioArtworkFetcher.MIN_TARGET_SIZE_PX,
                AudioArtworkFetcher.MAX_TARGET_SIZE_PX
            )
            val videoId = if (url.startsWith("nodeplayer://youtube/")) {
                url.removePrefix("nodeplayer://youtube/").substringBefore('?').substringBefore('/')
            } else if (url.contains("i.ytimg.com/vi/")) {
                url.substringAfter("i.ytimg.com/vi/").substringBefore('/')
            } else null

            val urlsToTry = if (videoId != null) {
                listOf(
                    "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/hq720.jpg",
                    "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                )
            } else {
                listOf(url)
            }

            var imageBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
            for (u in urlsToTry) {
                val request = ImageRequest.Builder(this@NodePlayerApplication)
                    .data(u)
                    .size(targetPx, targetPx)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(this@NodePlayerApplication).execute(request)
                if (result is SuccessResult) {
                    val bmp = (result.drawable as? BitmapDrawable)?.bitmap ?: result.drawable.toBitmap()
                    val cropped = if (u.contains("hqdefault") || u.contains("sddefault")) {
                        val aspect = bmp.width.toFloat() / bmp.height.toFloat()
                        if (aspect in 1.30f..1.36f) {
                            val trimPx = (bmp.height * 0.125f).toInt()
                            val activeHeight = (bmp.height * 0.75f).toInt()
                            if (trimPx > 0 && activeHeight > 0 && trimPx + activeHeight <= bmp.height) {
                                Bitmap.createBitmap(bmp, 0, trimPx, bmp.width, activeHeight)
                            } else bmp
                        } else bmp
                    } else bmp
                    imageBitmap = cropped.asImageBitmap()
                    break
                }
            }
            imageBitmap
        }

        startAsyncBootstrap()
    }

    private fun startAsyncBootstrap() {
        if (asyncBootstrapStarted) return
        synchronized(this) {
            if (asyncBootstrapStarted) return
            asyncBootstrapStarted = true
        }
        applicationScope.launch {
            try {
                if (BuildConfig.DEBUG) {
                    ensureFileLoggingInitialized(this@NodePlayerApplication)
                }
                com.nodeplayer.music.diagnostics.LocalCrashLogger.init(this@NodePlayerApplication)
                getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
                val sharedInnerTube = com.nodeplayer.network.innertube.SharedInnerTubeProvider.get()
                com.nodeplayer.music.auth.YouTubeAccountManager.initialize(this@NodePlayerApplication, sharedInnerTube)
                com.nodeplayer.music.lyrics.LyricsManager.initialize(this@NodePlayerApplication)
            } catch (t: Throwable) {
                NodeLog.w("app/lifecycle", "Async bootstrap partial failure: ${t.message}")
            }
        }
    }

    private fun initializeDownloadAndStorage() {
        val downloadsDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: java.io.File(filesDir, "downloads")
        val resolver = com.nodeplayer.network.downloader.StreamFormatResolver()
        val httpSource = com.nodeplayer.network.downloader.DownloadHttpSource()
        val searchClient = com.nodeplayer.network.innertube.InnerTubeClient()

        val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
        val concurrent = prefs.getInt("download_concurrent_limit", 2)
        val wifiOnly = prefs.getBoolean("download_wifi_only", false)

        downloadQueueManager = com.nodeplayer.library.downloader.DownloadQueueManager(
            downloadsDir = downloadsDir,
            streamOptionResolver = { targetId, preset, format ->
                var resolvedVid = targetId
                if (targetId.startsWith("query:") || targetId.length != 11) {
                    val query = if (targetId.startsWith("query:")) targetId.removePrefix("query:") else targetId
                    val searchRes = searchClient.search(query, com.nodeplayer.network.innertube.SearchFilter.SONGS).getOrNull()
                    val firstSong = searchRes?.songs?.firstOrNull()
                    val songVid = firstSong?.let { s ->
                        when {
                            s.path.startsWith("nodeplayer://youtube/track/") -> s.path.removePrefix("nodeplayer://youtube/track/").substringBefore("?").substringBefore("/")
                            s.path.startsWith("nodeplayer://youtube/") -> s.path.removePrefix("nodeplayer://youtube/").substringBefore("?").substringBefore("/")
                            s.path.startsWith("online://") -> s.path.removePrefix("online://").substringBefore("?").substringBefore("/")
                            s.path.length == 11 && !s.path.contains("/") -> s.path
                            else -> null
                        }
                    }
                    if (songVid != null) {
                        resolvedVid = songVid
                    }
                }
                val opts = resolver.resolveAvailableAudioOptions(resolvedVid).getOrThrow()
                val best = resolver.selectBestOption(opts, preset, format)
                    ?: throw IllegalStateException("No format matched preset")
                Result.success(best)
            },
            streamDownloader = { url, destFile, expected, onProg ->
                httpSource.downloadToFile(url, destFile, expected, onProg)
            },
            tagger = com.nodeplayer.music.downloader.AndroidDownloadTagger(this),
            initialConcurrentLimit = concurrent,
            initialWifiOnly = wifiOnly,
            onDownloadCompleted = { savedFile, originalTrack ->
                applicationScope.launch(Dispatchers.IO) {
                    val trackId = if (originalTrack.id > 0L) originalTrack.id else (savedFile.absolutePath.hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL)
                    val downloadedTrack = originalTrack.copy(
                        id = trackId,
                        path = savedFile.absolutePath,
                        size = savedFile.length()
                    )
                    database.playerDao().insertTracks(listOf(com.nodeplayer.music.data.TrackEntity.fromTrack(downloadedTrack)))
                    com.nodeplayer.common.logging.NodeLog.i("NodeApp", "Downloaded and persisted track to DB: ${downloadedTrack.title}")
                }
            }
        )

        val reg = com.nodeplayer.library.storage.StorageCategoryRegistry
        reg.registerCategory(
            reg.createDirectoryCategory(
                id = reg.CATEGORY_AUDIO_CACHE,
                displayName = "Audio & Stream Cache",
                description = "ExoPlayer LRU stream cache",
                directory = cacheDir.resolve(com.nodeplayer.music.playback.AudioCache.CACHE_DIR_NAME)
            )
        )
        reg.registerCategory(
            reg.createDirectoryCategory(
                id = reg.CATEGORY_IMAGE_CACHE,
                displayName = "Cover Art & Images",
                description = "Coil disk cache for album artwork",
                directory = cacheDir.resolve("image_cache"),
                limitSettingKey = "cache_limit_images_mb"
            )
        )
        reg.registerCategory(
            reg.createDirectoryCategory(
                id = reg.CATEGORY_LYRICS_CACHE,
                displayName = "Synchronized Lyrics",
                description = "Local LRC/TTML lyrics cache",
                directory = cacheDir.resolve("lyrics_cache"),
                limitSettingKey = "cache_limit_lyrics_mb"
            )
        )
        reg.registerCategory(
            reg.createDirectoryCategory(
                id = reg.CATEGORY_FEED_CACHE,
                displayName = "Home Feed Cache",
                description = "Home feed and mood shelves cache",
                directory = cacheDir.resolve("home_feeds"),
                limitSettingKey = "cache_limit_feed_mb"
            )
        )
        reg.registerCategory(
            reg.createDirectoryCategory(
                id = reg.CATEGORY_TASTE_CACHE,
                displayName = "Taste & Recommendations",
                description = "Taste profile and mix cache",
                directory = cacheDir.resolve("taste_cache")
            )
        )
        reg.registerCategory(
            reg.createDirectoryCategory(
                id = reg.CATEGORY_OFFLINE_DOWNLOADS,
                displayName = "Offline Downloads",
                description = "Downloaded audio files",
                directory = downloadsDir,
                clearable = false
            )
        )
    }

    companion object {
        lateinit var instance: NodePlayerApplication
            private set

        @Volatile
        private var fileLoggingInitialized: Boolean = false

        fun ensureFileLoggingInitialized(context: Context) {
            if (fileLoggingInitialized) return
            synchronized(this) {
                if (fileLoggingInitialized) return
                NodeLog.initFileLogging(java.io.File(context.filesDir, "logs/node_connect.log"))
                fileLoggingInitialized = true
            }
        }
    }
}
