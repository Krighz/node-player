package com.nodeplayer.music

import android.app.Application
import android.content.Context
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
            .addMigrations(NodeDatabase.MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()

        libraryScanner = LibraryScanner(applicationContext)

        com.nodeplayer.ui.components.PlatformArtworkProvider.remoteImageFetcher = { url, maxSizePx ->
            val targetPx = maxSizePx.coerceIn(
                AudioArtworkFetcher.MIN_TARGET_SIZE_PX,
                AudioArtworkFetcher.MAX_TARGET_SIZE_PX
            )
            val request = ImageRequest.Builder(this@NodePlayerApplication)
                .data(url)
                .size(targetPx, targetPx)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(this@NodePlayerApplication).execute(request)
            if (result is SuccessResult) {
                val bmp = (result.drawable as? BitmapDrawable)?.bitmap ?: result.drawable.toBitmap()
                bmp.asImageBitmap()
            } else {
                null
            }
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
