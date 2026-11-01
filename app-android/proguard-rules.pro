# Proguard rules for NodePlayer

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }

# Haze Glassmorphism
-keep class dev.chrisbanes.haze.** { *; }

# Models & Data entities
-keep class com.nodeplayer.music.model.** { *; }
-keep class com.nodeplayer.music.data.** { *; }
-keep class com.nodeplayer.music.library.** { *; }
-keep class com.nodeplayer.music.dsp.** { *; }

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# App Widgets & Broadcast Receivers
-keep class com.nodeplayer.music.widget.** { *; }

# Coil
-keep class coil.** { *; }
-keep class * implements coil.fetch.Fetcher$Factory { *; }
