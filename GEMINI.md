<!-- GSD:project-start source:PROJECT.md -->

## Project

**Node Player - Liquid Glass Revamp**

Node Player is a local-first audiophile music player built with Compose Multiplatform for Android (`com.nodeplayer.music`) and Windows desktop. This project revamps the liquid glass UI system, replacing sluggish or buggy implementations with clean, correct, and high-performance glass shaders, fluid iOS-like spring physics inspired by Convx, intelligent dock scroll-hiding, and rich customization settings.

**Core Value:** Make the liquid glass code clean, correct, and fast with fluid iOS-like spring animations, zero layout crashes, no rendering artifacts, and deep real-time customization across both Android and Windows.

### Constraints

- **Platform Compatibility**: Android ART (API 26+) and Windows Desktop (JVM 17 / Compose Desktop). Android 13+ AGSL shader support with fallback for older versions.
- **Git Policy**: All work done on `feature/liquid-dock`. Never push to `main` until go live. Never push, merge, tag or publish without user instruction.
- **Performance**: Zero per-frame allocations or reflection inside draw loops. Small controls must use lightweight plain Compose; lens shaders restricted to hero components.
- **Documentation**: Update `docs/platforms/CHANGES.md`, `docs/state/FIXES_AND_STATUS.md`, and `docs/reference/SETTINGS.md` after each phase.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->

## Technology Stack

## Languages

- Kotlin 1.9.24 - Core application logic, multiplatform models, DSP audio engine, networking protocol, and UI layer (`app-android`, `app-desktop`, `core-common`, `core-model`, `core-dsp`, `core-library`, `core-network`, `core-ui`, `integration-api`)
- Java 17 - Android compilation bytecode target (`compileOptions.sourceCompatibility = JavaVersion.VERSION_17`) and JVM toolchain target (`jvmToolchain(17)` in `app-desktop`)
- AGSL (Android Graphics Shading Language) - Custom hardware liquid-glass shader execution on Android (`app-android/src/main/res/raw/liquid_glass.agsl` and widget rendering)
- HTML5 / CSS3 / JavaScript - Static documentation website and marketing portal (`site/`)
- PowerShell - Icon asset generation scripts (`generate_icon.ps1`)

## Runtime

- Java Virtual Machine (JVM 17 / Temurin 17) - Desktop runtime and Gradle build execution
- Android ART (API level 26 [Android 8.0 Oreo] to API level 34 [Android 14])
- Gradle 8.7 (`gradle/wrapper/gradle-wrapper.properties`)
- Lockfile: Not present (`dependencyResolutionManagement.repositoriesMode` set to `RepositoriesMode.FAIL_ON_PROJECT_REPOS` via Maven Central and Google Repositories)

## Frameworks

- Jetpack Compose / Jetpack Compose Multiplatform 1.6.11 - Declarative UI framework shared across Android and Windows (`core-ui`, `app-android`, `app-desktop`)
- Jetpack Compose BOM 2024.05.00 - Material 3 component library, animation, and runtime compose foundation (`app-android/build.gradle.kts`)
- AndroidX Media3 1.3.1 (`media3-exoplayer`, `media3-session`, `media3-common`, `media3-ui`) - Media playback service, foreground audio notifications, and MediaSession integration on Android (`app-android/src/main/kotlin/com/nodeplayer/music/service/PlaybackService.kt`)
- AndroidX Room 2.6.1 (`room-runtime`, `room-ktx`, `room-compiler` via KSP 1.9.24-1.0.20) - SQLite persistence ORM on Android (`app-android/src/main/kotlin/com/nodeplayer/music/data/NodeDatabase.kt`)
- AndroidX DataStore 1.1.1 (`datastore-preferences`) - Reactive preferences and configuration storage (`app-android/src/main/kotlin/com/nodeplayer/music/data/AppSettingsRepository.kt`)
- AndroidX WorkManager 2.9.0 (`work-runtime-ktx`) - Background asynchronous audio analysis jobs (`app-android/build.gradle.kts`)
- JUnit 4.13.2 - Unit testing runner across all subprojects (`:core-dsp`, `:core-common`, `:core-network`, `:core-library`, `:app-android`, `:app-desktop`)
- Kotlin Test (`org.jetbrains.kotlin:kotlin-test`) - Multiplatform assertion library
- Kotlinx Coroutines Test 1.8.0 (`kotlinx-coroutines-test`) - Coroutine test scopes and virtual time test dispatchers (`core-network/build.gradle.kts`, `core-common/build.gradle.kts`)
- AndroidX Test Runner & JUnit Ext 1.1.5 (`androidx.test.ext:junit`) - Android instrumentation testing runner (`app-android/build.gradle.kts`)
- AndroidX Espresso 3.5.1 (`espresso-core`) - Android UI automation testing (`app-android/build.gradle.kts`)
- org.json:json 20240303 - JSON payload testing utilities (`app-android/build.gradle.kts`)
- Android Gradle Plugin (AGP) 8.4.2 (`com.android.application`, `com.android.library`) - Android build system
- Kotlin Gradle Plugin 1.9.24 (`org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.multiplatform`) - Kotlin compilation pipeline
- Kotlin Symbol Processing (KSP) 1.9.24-1.0.20 (`com.google.devtools.ksp`) - Code generation for Room DB entities and DAOs
- Compose Multiplatform Gradle Plugin 1.6.11 (`org.jetbrains.compose`) - Desktop distribution packaging and multiplatform composables
- ProGuard / R8 - Code shrinking, dead code elimination, and obfuscation for Android release builds (`app-android/proguard-rules.pro`)

## Key Dependencies

- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0` & `kotlinx-coroutines-swing:1.8.0` - Asynchronous structured concurrency and desktop UI dispatching
- `dev.chrisbanes.haze:haze:0.7.1` & `haze-materials:0.7.1` - Liquid-glass glassmorphism blur and noise effects for Compose UI
- `io.coil-kt:coil-compose:2.6.0` - Asynchronous image loading and album cover art caching
- `androidx.palette:palette-ktx:1.0.0` - Color extraction from album artwork for dynamic UI theme coloring
- `javazoom:jlayer:1.0.1` - Pure JVM MP3 audio frame decoding on desktop (`app-desktop/build.gradle.kts`)
- `org.jflac:jflac-codec:1.5.2` - Pure JVM FLAC lossless audio frame decoding on desktop (`app-desktop/build.gradle.kts`)
- `net.java.dev.jna:jna:5.14.0` & `jna-platform:5.14.0` - Windows native API bindings for WASAPI audio output and System Media Transport Controls (SMTC) (`app-desktop/build.gradle.kts`)
- `org.xerial:sqlite-jdbc:3.45.1.0` - SQLite JDBC driver for local desktop track library persistence (`app-desktop/build.gradle.kts`)
- `androidx.camera:camera-camera2:1.3.3`, `camera-lifecycle:1.3.3`, `camera-view:1.3.3` - Android CameraX stack for 100% offline local QR code scanning (`app-android/build.gradle.kts`)
- `com.google.zxing:core:3.5.3` - Pure local QR code barcode generation and matrix decoding (`app-android/build.gradle.kts`, `core-network/src/main/kotlin/com/nodeplayer/network/pairing/QrCodeGenerator.kt`)

## Configuration

- Configured through Gradle property files and build flags:
- Key environment variables (optional for signing / CI):
- Root build script: `build.gradle.kts`
- Settings script: `settings.gradle.kts` (includes 9 modules: `:core-common`, `:core-model`, `:core-dsp`, `:core-library`, `:core-network`, `:integration-api`, `:core-ui`, `:app-desktop`, `:app-android`)
- ProGuard rules: `app-android/proguard-rules.pro`
- Native distributions config: `app-desktop/build.gradle.kts` (MSI and EXE installers target)

## Platform Requirements

- JDK 17 (Temurin recommended)
- Android SDK with Platform 34 and Build-Tools 34.0.0
- Gradle 8.7 (via included `gradlew` / `gradlew.bat`)
- Windows 10/11 or Ubuntu Linux (for CI build pipeline)
- Android: Android 8.0+ (API 26+) phone, tablet, or automotive head-unit
- Windows: Windows 10 / Windows 11 (64-bit x86_64, packaged as MSI/EXE native distribution)

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Naming Patterns

- Kotlin source files use `PascalCase.kt` matching their primary class or object (e.g., `app-android/src/main/kotlin/com/nodeplayer/music/library/LastFmClient.kt`, `core-common/src/main/kotlin/com/nodeplayer/common/logging/NodeLog.kt`).
- Unit and integration tests mirror production class names with a `Test` suffix: `core-dsp/src/test/kotlin/com/nodeplayer/dsp/DspChainTest.kt`, `app-android/src/androidTest/kotlin/com/nodeplayer/music/CrossfadeIntegrationTest.kt`.
- Utility collections or multi-entity files use descriptive plural or domain PascalCase (e.g., `app-android/src/main/kotlin/com/nodeplayer/music/data/Entities.kt`).
- Use standard Kotlin `camelCase` for functions and methods (e.g., `fun getEffectiveApiKey()`, `fun processInterleavedFloat()`, `fun calculateGains()`).
- Use `@Composable` PascalCase for Jetpack Compose UI component functions (e.g., `@Composable fun NowPlayingScreen()`, `@Composable fun OpticalRailDock()`).
- Test methods use `camelCase` prefixed with `test` (e.g., `fun testLimiterNeverExceedsFullScale()`, `fun testMutualTlsHandshakeAndControlCommand()`).
- Local variables and class properties follow `camelCase` (e.g., `sampleRate`, `totalSamples`, `currentSinks`).
- Compile-time constants and companion object constants use `UPPER_SNAKE_CASE` (e.g., `MAX_HISTORY = 1000`, `API_ROOT = "https://ws.audioscrobbler.com/2.0/"`, `PRESET_OBSIDIAN_LIQUID` in `core-model/src/main/kotlin/com/nodeplayer/model/GlassThemeConfig.kt`).
- Private mutable backing fields (especially StateFlow) use leading underscore notation (e.g., `_playbackState` backing `playbackState: StateFlow<PlaybackState>`).
- Classes, interfaces, sealed interfaces, and enums follow `PascalCase` (e.g., `NodeResult`, `AudioEffectConfig`, `HeadlessTestPeer`, `FadeCurve`).
- Android Room entities are named with an `Entity` suffix (e.g., `TrackEntity`, `ScrobbleQueueEntity` in `app-android/src/main/kotlin/com/nodeplayer/music/data/Entities.kt`).
- Feature contracts and cross-module interfaces are suffixed with `FeatureApi` or `Bridge` (e.g., `DiagnosticsFeatureApi.kt`, `ScrobbleFeatureApi.kt`, `PlatformMediaSessionBridge.kt`).

## Code Style

- Official Kotlin Coding Conventions (4-space indentation, trailing commas on multi-line parameter lists, no wildcard imports where possible).
- Explicit target compatibility set to Java 17 across modules (`jvmTarget = "17"`, `sourceCompatibility = JavaVersion.VERSION_17` in `build.gradle.kts`).
- Pure Kotlin core modules (`:core-model`, `:core-dsp`, `:core-library`, `:core-common`, `:integration-api`) enforce strict architectural boundaries prohibiting imports of Android framework packages.
- Kotlin compiler checks configured via Gradle (`1.9.24` / `1.5.14` compose compiler).
- Android Lint integrated into release and staging builds with ProGuard/R8 optimizations (`proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")` in `app-android/build.gradle.kts`).
- Automated architectural boundary enforcement tests (`core-common/src/test/kotlin/com/nodeplayer/common/ArchitectureBoundaryTest.kt`) that fail CI if core modules import `android.*` or if feature modules import internal classes of sibling features.

## Import Organization

- Standard multi-project Gradle module dependencies rather than path aliases:

## Error Handling

- Use domain-specific sealed results (`com.nodeplayer.common.result.NodeResult`) or standard `kotlin.Result` for fallible operations instead of unhandled checked exceptions.
- Offline degradation and queue retry pattern: when external APIs fail (such as Last.fm scrobble), enqueue the payload locally in Room database (`dao.enqueueScrobble(...)`) and flush later without crashing playback (`app-android/src/main/kotlin/com/nodeplayer/music/library/LastFmClient.kt`).
- Hardware decoders and audio engines fail gracefully returning fallback values or `-1` without throwing uncaught runtime exceptions (`app-desktop/src/test/kotlin/com/nodeplayer/desktop/DesktopAudioAndDatabaseTest.kt`).

## Logging

- Core modules and unified logging utilize `com.nodeplayer.common.logging.NodeLog` (`core-common/src/main/kotlin/com/nodeplayer/common/logging/NodeLog.kt`).
- Android platform services utilize `android.util.Log` or delegate to `NodeLog` with tag prefixing.
- Categorized hierarchical tags using `feature/subsystem` syntax (e.g., `NodeLog.i("nodeconnect/logging", "Persistent file logging initialized")`).
- In-memory ring buffer (default 1000 entries) via `ConcurrentLinkedDeque` supporting exportable diagnostics dumps (`NodeLog.exportFormattedLog()`, `NodeLog.getEntriesByFeature()`).
- Avoid logging raw user credentials or secret keys; log masked status checks (e.g., `"Last.fm configured: key yes, secret no"` in `LastFmClient.kt`).

## Comments

- Audio DSP algorithms require mathematical derivations and formula explanations (e.g., biquad peaking filter formulas, equal-power sine/cosine crossfade curves in `core-dsp/src/test/kotlin/com/nodeplayer/dsp/CrossfadeCurveMathTest.kt`).
- Binary protocol frame specifications in network modules document byte offsets, bit-packing layouts, andendianness.
- Explain non-obvious workarounds for Android audio hardware quirks (e.g., dual ExoPlayer DSP chain isolation to prevent filter state click/pop).
- KDoc format (`/** ... */`) is used on public API interfaces, contracts (`PlatformMediaSessionBridge`, `ScrobbleFeatureApi`), and data model parameters.

## Function Design

- Small, focused single-responsibility functions. Audio processing loops are vectorized or structured for deterministic zero-allocation hot paths.
- Default argument values are utilized extensively on data classes and configuration functions (e.g., `AudioEffectConfig`, `GlassThemeConfig`).
- Lambdas and trailing functional callbacks used for asynchronous UI event handling (e.g., `onTrackTransition: (Track) -> Unit`).
- Sealed interfaces (`NodeResult<T>`) or `Result<T>` for I/O and network operations.
- Explicit nullable types (`T?`) for searches and lookups rather than throwing `NoSuchElementException`.

## Module Design

- Explicit module boundaries enforced via multi-project Gradle setup.
- Feature internals are quarantined: cross-feature dependencies within `:app-android` must consume public interfaces rather than `.internal.` packages, verified by `ArchitectureBoundaryTest`.
- Shared Compose Multiplatform UI is centralized in `:core-ui` for multi-target layout rendering across Android and Windows desktop.
- Kotlin package-level exports; related entities are co-located in cohesive packages (e.g., `com.nodeplayer.model`, `com.nodeplayer.network.protocol`).

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## System Overview

```text

```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `app-android` | Android application head, AndroidX Media3 `PlaybackService`, Room persistence, Android UI activities & widgets | `app-android/src/main/kotlin/com/nodeplayer/music/MainActivity.kt` |
| `app-desktop` | Desktop application head, Compose Multiplatform desktop window, WASAPI/JNA native audio output, local SQLite | `app-desktop/src/main/kotlin/com/nodeplayer/desktop/Main.kt` |
| `core-ui` | Compose Multiplatform cross-platform UI components, design system, glassmorphism theme, audio visualization graphs | `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/SharedPlayerLayout.kt` |
| `core-network` | Cross-device P2P protocol (`NodeConnectHub`), mDNS/ADB discovery, TLS framing, real-time audio casting & state sync | `core-network/src/main/kotlin/com/nodeplayer/network/service/NodeConnectHub.kt` |
| `integration-api` | Pure contract for ecosystem companion apps (Wear/Watch, Auto, TV), decoupling external consumers | `integration-api/src/main/kotlin/com/nodeplayer/integration/api/IntegrationContract.kt` |
| `core-dsp` | High-fidelity digital signal processing: 10-band IIR biquad filters, EBU R128 loudness, FFT spectrum, dynamic range meter | `core-dsp/src/main/kotlin/com/nodeplayer/dsp/DspChain.kt` |
| `core-library` | Audio metadata parsing (ID3v1/ID3v2, FLAC, Vorbis), LRC synchronized lyrics parser, library repository abstractions | `core-library/src/main/kotlin/com/nodeplayer/library/repository/TrackRepository.kt` |
| `core-model` | Pure Kotlin data models (`Track`, `Album`, `Artist`, `AudioEffectConfig`, `ThemeConfig`, `PlaybackState`) | `core-model/src/main/kotlin/com/nodeplayer/model/Track.kt` |
| `core-common` | Core foundation primitives: platform abstraction interfaces (`AudioDecoder`, `AudioOutput`, `KeyValueStore`), `NodeLog`, coroutine dispatchers | `core-common/src/main/kotlin/com/nodeplayer/common/platform/PlatformServices.kt` |

## Pattern Overview

- **Strict Architectural Boundaries:** Enforced by `core-common/src/test/kotlin/com/nodeplayer/common/ArchitectureBoundaryTest.kt`. Core modules (`core-common`, `core-model`, `core-dsp`, `core-library`) must remain pure Kotlin with zero Android framework dependencies.
- **Ports & Adapters (Hexagonal):** Platform abstractions (`AudioDecoder`, `AudioOutput`, `KeyValueStore`, `TrackRepository`) are defined in `core-common` and `core-library`. Android implements them via ExoPlayer/Room/SharedPreferences; Desktop implements them via WASAPI/JNA/SQLite.
- **Bidirectional P2P Interconnect:** Node Connect (`core-network`) acts as an orchestration bus allowing Android and Desktop instances to seamlessly discover each other, cast raw audio, transfer library state, or act as remote controllers.
- **Unidirectional Data Flow (UDF):** ViewModels and UI state holders expose Kotlin `StateFlow` streams observed by declarative Compose UI trees.

## Layers

- Purpose: Render user interfaces, handle user gestures, execute visual shaders, manage window/screen lifecycles.
- Location: `core-ui/src/commonMain/kotlin/com/nodeplayer/ui`, `app-android/src/main/kotlin/com/nodeplayer/music/ui`, `app-desktop/src/main/kotlin/com/nodeplayer/desktop`
- Contains: Compose Multiplatform composables, ViewModels, shaders (`LiquidGlassBackground`), canvas visualizers.
- Depends on: `core-model`, `core-dsp`, `core-library`, `core-network`, `core-common`.
- Used by: End users on Android mobile devices and Windows/Linux/macOS desktops.
- Purpose: Device discovery, peer pairing, TLS socket framing, state handoff, Wi-Fi audio casting, external API integrations.
- Location: `core-network/src/main/kotlin/com/nodeplayer/network`, `integration-api/src/main/kotlin/com/nodeplayer/integration/api`
- Contains: `NodeConnectHub`, `NodeDiscovery`, `AdbBridge`, `JitterBuffer`, protocol serializers.
- Depends on: `core-common`, `core-model`, `core-library`.
- Used by: Application runtimes (`PlaybackService` in Android, `Main.kt` in Desktop).
- Purpose: Audio processing math (filtering, crossfading, FFT), audio file tag parsing, playlist calculations.
- Location: `core-dsp/src/main/kotlin/com/nodeplayer/dsp`, `core-library/src/main/kotlin/com/nodeplayer/library`
- Contains: `BiquadFilter`, `DspChain`, `LoudnessR128`, `AudioHeaderParser`, `LrcParser`.
- Depends on: `core-common`, `core-model`.
- Used by: Presentation layer, Playback engines (`DspAudioProcessor`, `WindowsPlaybackEngine`).
- Purpose: Core domain entities, common result types, logging infrastructure, coroutine dispatchers, platform service interfaces.
- Location: `core-model/src/main/kotlin/com/nodeplayer/model`, `core-common/src/main/kotlin/com/nodeplayer/common`
- Contains: Domain data classes, enum definitions, interfaces for decoupling platform implementations.
- Depends on: Standard Kotlin library and coroutines.
- Used by: All modules across the codebase.

## Data Flow

### Primary Request Path (Playback Tap to Audio Output)

### Node Connect Cross-Device Audio Cast Flow

- Kotlin `StateFlow` and Compose `mutableStateOf` manage reactive UI state.
- Local persistence uses Room SQLite on Android (`NodeDatabase.kt`) and JDBC SQLite on Desktop (`DesktopTrackDatabase.kt`).
- User settings are persisted through `SharedPreferences` (Android) and JSON configuration files (Desktop).

## Key Abstractions

- Purpose: Abstract platform-specific hardware decoders and output streams so DSP and core models remain independent of Android/Desktop SDKs.
- Examples: `core-common/src/main/kotlin/com/nodeplayer/common/platform/AudioDecoder.kt`, `AudioOutput.kt`
- Pattern: Strategy / Ports and Adapters.
- Purpose: Provide unified access to music metadata, albums, artists, and playlists regardless of underlying storage mechanism.
- Examples: `core-library/src/main/kotlin/com/nodeplayer/library/repository/TrackRepository.kt`, `app-android/src/main/kotlin/com/nodeplayer/music/data/AppSettingsRepository.kt`, `app-desktop/src/main/kotlin/com/nodeplayer/desktop/data/DesktopTrackRepositoryAdapter.kt`
- Pattern: Repository Pattern.
- Purpose: Composable pipeline for real-time audio manipulation (Parametric EQ, Bass Boost, Reverb, Crossfade).
- Examples: `core-dsp/src/main/kotlin/com/nodeplayer/dsp/DspChain.kt`, `core-dsp/src/main/kotlin/com/nodeplayer/dsp/DspProcessors.kt`
- Pattern: Pipeline / Chain of Responsibility.
- Purpose: Unified façade orchestrating discovery, socket transports, cryptographic pairing, library sync, handoff, and remote control.
- Examples: `core-network/src/main/kotlin/com/nodeplayer/network/service/NodeConnectHub.kt`
- Pattern: Facade / Mediator Pattern.

## Entry Points

- Location: `app-android/src/main/kotlin/com/nodeplayer/music/NodePlayerApplication.kt` and `MainActivity.kt`
- Triggers: OS app launch or media button intents.
- Responsibilities: Initialize logging (`NodeLog`), crash logging, Coil image loaders, database singleton, and start background `PlaybackService`.
- Location: `app-android/src/main/kotlin/com/nodeplayer/music/service/PlaybackService.kt`
- Triggers: Bound/started service lifecycle via AndroidX Media3 `MediaSessionService`.
- Responsibilities: Manage ExoPlayer instances, crossfade coordinator, lock screen notifications, system equalizer broadcasts, and incoming Node Connect cast streams.
- Location: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/Main.kt`
- Triggers: OS process execution (`main()` function).
- Responsibilities: Initialize Compose Multiplatform desktop window (`application { Window(...) }`), WASAPI native audio engine, local SQLite database, settings store, and start `NodeConnectHub`.

## Architectural Constraints

- **Core Module Purity:** No Android dependencies allowed in `core-common`, `core-model`, `core-dsp`, or `core-library`. Enforced by automated test `core-common/.../ArchitectureBoundaryTest.kt`.
- **Feature Encapsulation:** Features inside `app-android` must never import `.internal.` packages belonging to other features.
- **Threading Model:**
- **Global State:** Singletons are strictly scoped (e.g., `NodePlayerApplication.instance` in Android, `NodeLog` sink registry in common). State flows are retained within ViewModels or engine holders rather than mutable global variables.

## Anti-Patterns

### Anti-Pattern 1: Importing Platform Frameworks into Core Modules

### Anti-Pattern 2: Directly Mutating Settings Outside Repository/Store Adapters

## Error Handling

- **Domain Errors:** Operations that can fail (e.g., parsing audio headers or pairing devices) return `NodeResult.Success` or `NodeResult.Failure` (`core-common/.../NodeResult.kt`).
- **Crash Isolation:** Unhandled exceptions in background threads are logged via `Thread.setDefaultUncaughtExceptionHandler` and written to local crash logs (`LocalCrashLogger.kt`).
- **Audio Playback Resilience:** If native WASAPI output or ExoPlayer encounters a playback exception, players degrade gracefully, reset audio pipes, and report errors to the UI rather than crashing the process.

## Cross-Cutting Concerns

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.agents/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-fast` for a trivial task inline, with no subagents and no PLAN.md
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
