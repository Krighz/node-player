# Proguard rules for NodePlayer

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }



# Models & Data entities
-keep class com.nodeplayer.music.model.** { *; }
-keep class com.nodeplayer.music.data.** { *; }
-keep class com.nodeplayer.music.library.** { *; }
-keep class com.nodeplayer.music.dsp.** { *; }

# Pure Kotlin Domain Models & Glass Controls Configuration
-keep class com.nodeplayer.model.** { *; }
-keepclassmembers class com.nodeplayer.model.** { *; }
-keep class kotlinx.serialization.** { *; }

# Room SQLite Database & DAOs
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

# Liquid Glass Shaders & Runtime Graphics APIs (AGSL / RenderEffect)
-keep class com.kyant.backdrop.** { *; }
-keepclassmembers class com.kyant.backdrop.** { *; }
-keep class android.graphics.RuntimeShader { *; }
-keepclassmembers class android.graphics.RuntimeShader { *; }
-keep class android.graphics.RenderEffect { *; }
-keepclassmembers class android.graphics.RenderEffect { *; }

# JNA Native Bindings
-keep class com.sun.jna.** { *; }

# SLF4J logger binder warning suppression
-dontwarn org.slf4j.impl.StaticLoggerBinder
