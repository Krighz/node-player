<!-- refreshed: 2026-11-11 -->
# Technology Stack

**Analysis Date:** 2026-11-11

## Languages

**Primary:**
- Kotlin 1.9.24 - Core application logic, multiplatform models, DSP audio engine, networking protocol, and UI layer (`app-android`, `app-desktop`, `core-common`, `core-model`, `core-dsp`, `core-library`, `core-network`, `core-ui`, `integration-api`)

**Secondary:**
- Java 17 - Android compilation bytecode target (`compileOptions.sourceCompatibility = JavaVersion.VERSION_17`) and JVM toolchain target (`jvmToolchain(17)` in `app-desktop`)
- AGSL (Android Graphics Shading Language) - Custom hardware liquid-glass shader execution on Android (`app-android/src/main/res/raw/liquid_glass.agsl` and widget rendering)
- HTML5 / CSS3 / JavaScript - Static documentation website and marketing portal (`site/`)
- PowerShell - Icon asset generation scripts (`generate_icon.ps1`)

## Runtime

**Environment:**
- Java Virtual Machine (JVM 17 / Temurin 17) - Desktop runtime and Gradle build execution
- Android ART (API level 26 [Android 8.0 Oreo] to API level 34 [Android 14])

**Package Manager:**
- Gradle 8.7 (`gradle/wrapper/gradle-wrapper.properties`)
- Lockfile: Not present (`dependencyResolutionManagement.repositoriesMode` set to `RepositoriesMode.FAIL_ON_PROJECT_REPOS` via Maven Central and Google Repositories)

## Frameworks

**Core:**
- Jetpack Compose / Jetpack Compose Multiplatform 1.6.11 - Declarative UI framework shared across Android and Windows (`core-ui`, `app-android`, `app-desktop`)
- Jetpack Compose BOM 2024.05.00 - Material 3 component library, animation, and runtime compose foundation (`app-android/build.gradle.kts`)
- AndroidX Media3 1.3.1 (`media3-exoplayer`, `media3-session`, `media3-common`, `media3-ui`) - Media playback service, foreground audio notifications, and MediaSession integration on Android (`app-android/src/main/kotlin/com/nodeplayer/music/service/PlaybackService.kt`)
- AndroidX Room 2.6.1 (`room-runtime`, `room-ktx`, `room-compiler` via KSP 1.9.24-1.0.20) - SQLite persistence ORM on Android (`app-android/src/main/kotlin/com/nodeplayer/music/data/NodeDatabase.kt`)
- AndroidX DataStore 1.1.1 (`datastore-preferences`) - Reactive preferences and configuration storage (`app-android/src/main/kotlin/com/nodeplayer/music/data/AppSettingsRepository.kt`)
- AndroidX WorkManager 2.9.0 (`work-runtime-ktx`) - Background asynchronous audio analysis jobs (`app-android/build.gradle.kts`)

**Testing:**
- JUnit 4.13.2 - Unit testing runner across all subprojects (`:core-dsp`, `:core-common`, `:core-network`, `:core-library`, `:app-android`, `:app-desktop`)
- Kotlin Test (`org.jetbrains.kotlin:kotlin-test`) - Multiplatform assertion library
- Kotlinx Coroutines Test 1.8.0 (`kotlinx-coroutines-test`) - Coroutine test scopes and virtual time test dispatchers (`core-network/build.gradle.kts`, `core-common/build.gradle.kts`)
- AndroidX Test Runner & JUnit Ext 1.1.5 (`androidx.test.ext:junit`) - Android instrumentation testing runner (`app-android/build.gradle.kts`)
- AndroidX Espresso 3.5.1 (`espresso-core`) - Android UI automation testing (`app-android/build.gradle.kts`)
- org.json:json 20240303 - JSON payload testing utilities (`app-android/build.gradle.kts`)

**Build/Dev:**
- Android Gradle Plugin (AGP) 8.4.2 (`com.android.application`, `com.android.library`) - Android build system
- Kotlin Gradle Plugin 1.9.24 (`org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.multiplatform`) - Kotlin compilation pipeline
- Kotlin Symbol Processing (KSP) 1.9.24-1.0.20 (`com.google.devtools.ksp`) - Code generation for Room DB entities and DAOs
- Compose Multiplatform Gradle Plugin 1.6.11 (`org.jetbrains.compose`) - Desktop distribution packaging and multiplatform composables
- ProGuard / R8 - Code shrinking, dead code elimination, and obfuscation for Android release builds (`app-android/proguard-rules.pro`)

## Key Dependencies

**Critical:**
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0` & `kotlinx-coroutines-swing:1.8.0` - Asynchronous structured concurrency and desktop UI dispatching
- `dev.chrisbanes.haze:haze:0.7.1` & `haze-materials:0.7.1` - Liquid-glass glassmorphism blur and noise effects for Compose UI
- `io.coil-kt:coil-compose:2.6.0` - Asynchronous image loading and album cover art caching
- `androidx.palette:palette-ktx:1.0.0` - Color extraction from album artwork for dynamic UI theme coloring
- `javazoom:jlayer:1.0.1` - Pure JVM MP3 audio frame decoding on desktop (`app-desktop/build.gradle.kts`)
- `org.jflac:jflac-codec:1.5.2` - Pure JVM FLAC lossless audio frame decoding on desktop (`app-desktop/build.gradle.kts`)
- `net.java.dev.jna:jna:5.14.0` & `jna-platform:5.14.0` - Windows native API bindings for WASAPI audio output and System Media Transport Controls (SMTC) (`app-desktop/build.gradle.kts`)
- `org.xerial:sqlite-jdbc:3.45.1.0` - SQLite JDBC driver for local desktop track library persistence (`app-desktop/build.gradle.kts`)

**Infrastructure:**
- `androidx.camera:camera-camera2:1.3.3`, `camera-lifecycle:1.3.3`, `camera-view:1.3.3` - Android CameraX stack for 100% offline local QR code scanning (`app-android/build.gradle.kts`)
- `com.google.zxing:core:3.5.3` - Pure local QR code barcode generation and matrix decoding (`app-android/build.gradle.kts`, `core-network/src/main/kotlin/com/nodeplayer/network/pairing/QrCodeGenerator.kt`)

## Configuration

**Environment:**
- Configured through Gradle property files and build flags:
  - `gradle.properties`: Sets JVM memory arguments (`-Xmx2048m -Dfile.encoding=UTF-8`), parallel task execution (`org.gradle.parallel=true`), and AndroidX toggles (`android.useAndroidX=true`)
  - `local.properties`: Defines Android SDK location (`sdk.dir`), and optional Last.fm API keys (`LASTFM_API_KEY`, `LASTFM_API_SECRET`)
  - `app-android/lastfm.properties`: Fallback credentials file for Last.fm API scrobbling
  - Android build flavors / types: `debug`, `staging` (`.staging` applicationIdSuffix), and `release` with resource shrinking and ProGuard optimization
- Key environment variables (optional for signing / CI):
  - `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `NODEPLAYER_SIGNING_PROPS` for release APK signing
  - `LASTFM_API_KEY`, `LASTFM_API_SECRET` for scrobbler compilation

**Build:**
- Root build script: `build.gradle.kts`
- Settings script: `settings.gradle.kts` (includes 9 modules: `:core-common`, `:core-model`, `:core-dsp`, `:core-library`, `:core-network`, `:integration-api`, `:core-ui`, `:app-desktop`, `:app-android`)
- ProGuard rules: `app-android/proguard-rules.pro`
- Native distributions config: `app-desktop/build.gradle.kts` (MSI and EXE installers target)

## Platform Requirements

**Development:**
- JDK 17 (Temurin recommended)
- Android SDK with Platform 34 and Build-Tools 34.0.0
- Gradle 8.7 (via included `gradlew` / `gradlew.bat`)
- Windows 10/11 or Ubuntu Linux (for CI build pipeline)

**Production:**
- Android: Android 8.0+ (API 26+) phone, tablet, or automotive head-unit
- Windows: Windows 10 / Windows 11 (64-bit x86_64, packaged as MSI/EXE native distribution)

---

*Stack analysis: 2026-11-05*
