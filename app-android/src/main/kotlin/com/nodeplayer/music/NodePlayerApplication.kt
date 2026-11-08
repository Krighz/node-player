package com.nodeplayer.music

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nodeplayer.common.logging.LogLevel
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.music.data.NodeDatabase
import com.nodeplayer.music.library.AudioArtworkFetcher
import com.nodeplayer.music.library.LibraryScanner

class NodePlayerApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.TrackFactory())
                add(AudioArtworkFetcher.FileFactory())
            }
            .crossfade(true)
            .build()
    }

    lateinit var database: NodeDatabase
        private set

    lateinit var libraryScanner: LibraryScanner
        private set

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
        NodeLog.initFileLogging(java.io.File(filesDir, "logs/node_connect.log"))
        NodeLog.isVerbose = true
        NodeLog.i("app/lifecycle", "NodePlayerApplication initialized")

        com.nodeplayer.music.diagnostics.LocalCrashLogger.init(this)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            com.kyant.backdrop.RuntimeShaderCache.warmUp()
        }

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

        // Pre-warm SharedPreferences on a background thread to prevent disk read stall on main thread
        Thread {
            getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
        }.start()

        database = Room.databaseBuilder(
            applicationContext,
            NodeDatabase::class.java,
            "node_player.db"
        )
            .addMigrations(NodeDatabase.MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()

        libraryScanner = LibraryScanner(applicationContext)
    }

    companion object {
        lateinit var instance: NodePlayerApplication
            private set
    }
}
