package com.nodeplayer.music

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
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

        com.nodeplayer.music.diagnostics.LocalCrashLogger.init(this)

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
