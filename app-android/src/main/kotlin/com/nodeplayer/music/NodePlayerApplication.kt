package com.nodeplayer.music

import android.app.Application
import androidx.room.Room
import com.nodeplayer.music.data.NodeDatabase
import com.nodeplayer.music.library.LibraryScanner

class NodePlayerApplication : Application() {

    lateinit var database: NodeDatabase
        private set

    lateinit var libraryScanner: LibraryScanner
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            NodeDatabase::class.java,
            "node_player.db"
        ).fallbackToDestructiveMigration().build()

        libraryScanner = LibraryScanner(applicationContext)
    }

    companion object {
        lateinit var instance: NodePlayerApplication
            private set
    }
}
