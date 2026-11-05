# Coding Conventions

**Analysis Date:** 2026-11-05

## Naming Patterns

**Files:**
- Kotlin source files use `PascalCase.kt` matching their primary class or object (e.g., `app-android/src/main/kotlin/com/nodeplayer/music/library/LastFmClient.kt`, `core-common/src/main/kotlin/com/nodeplayer/common/logging/NodeLog.kt`).
- Unit and integration tests mirror production class names with a `Test` suffix: `core-dsp/src/test/kotlin/com/nodeplayer/dsp/DspChainTest.kt`, `app-android/src/androidTest/kotlin/com/nodeplayer/music/CrossfadeIntegrationTest.kt`.
- Utility collections or multi-entity files use descriptive plural or domain PascalCase (e.g., `app-android/src/main/kotlin/com/nodeplayer/music/data/Entities.kt`).

**Functions:**
- Use standard Kotlin `camelCase` for functions and methods (e.g., `fun getEffectiveApiKey()`, `fun processInterleavedFloat()`, `fun calculateGains()`).
- Use `@Composable` PascalCase for Jetpack Compose UI component functions (e.g., `@Composable fun NowPlayingScreen()`, `@Composable fun OpticalRailDock()`).
- Test methods use `camelCase` prefixed with `test` (e.g., `fun testLimiterNeverExceedsFullScale()`, `fun testMutualTlsHandshakeAndControlCommand()`).

**Variables:**
- Local variables and class properties follow `camelCase` (e.g., `sampleRate`, `totalSamples`, `currentSinks`).
- Compile-time constants and companion object constants use `UPPER_SNAKE_CASE` (e.g., `MAX_HISTORY = 1000`, `API_ROOT = "https://ws.audioscrobbler.com/2.0/"`, `PRESET_OBSIDIAN_LIQUID` in `core-model/src/main/kotlin/com/nodeplayer/model/GlassThemeConfig.kt`).
- Private mutable backing fields (especially StateFlow) use leading underscore notation (e.g., `_playbackState` backing `playbackState: StateFlow<PlaybackState>`).

**Types:**
- Classes, interfaces, sealed interfaces, and enums follow `PascalCase` (e.g., `NodeResult`, `AudioEffectConfig`, `HeadlessTestPeer`, `FadeCurve`).
- Android Room entities are named with an `Entity` suffix (e.g., `TrackEntity`, `ScrobbleQueueEntity` in `app-android/src/main/kotlin/com/nodeplayer/music/data/Entities.kt`).
- Feature contracts and cross-module interfaces are suffixed with `FeatureApi` or `Bridge` (e.g., `DiagnosticsFeatureApi.kt`, `ScrobbleFeatureApi.kt`, `PlatformMediaSessionBridge.kt`).

## Code Style

**Formatting:**
- Official Kotlin Coding Conventions (4-space indentation, trailing commas on multi-line parameter lists, no wildcard imports where possible).
- Explicit target compatibility set to Java 17 across modules (`jvmTarget = "17"`, `sourceCompatibility = JavaVersion.VERSION_17` in `build.gradle.kts`).
- Pure Kotlin core modules (`:core-model`, `:core-dsp`, `:core-library`, `:core-common`, `:integration-api`) enforce strict architectural boundaries prohibiting imports of Android framework packages.

**Linting:**
- Kotlin compiler checks configured via Gradle (`1.9.24` / `1.5.14` compose compiler).
- Android Lint integrated into release and staging builds with ProGuard/R8 optimizations (`proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")` in `app-android/build.gradle.kts`).
- Automated architectural boundary enforcement tests (`core-common/src/test/kotlin/com/nodeplayer/common/ArchitectureBoundaryTest.kt`) that fail CI if core modules import `android.*` or if feature modules import internal classes of sibling features.

## Import Organization

**Order:**
1. Package declaration (e.g., `package com.nodeplayer.music.audio`)
2. Framework and standard libraries (`java.*`, `javax.*`, `kotlin.*`, `kotlinx.coroutines.*`)
3. Android / Jetpack libraries (`android.*`, `androidx.*`)
4. Third-party dependencies (`dev.chrisbanes.haze.*`, `io.coil.*`)
5. Internal core dependencies (`com.nodeplayer.model.*`, `com.nodeplayer.common.*`, `com.nodeplayer.dsp.*`)
6. Internal app/module classes (`com.nodeplayer.music.ui.*`, `com.nodeplayer.music.data.*`)

**Path Aliases:**
- Standard multi-project Gradle module dependencies rather than path aliases:
  - `project(":core-common")`
  - `project(":core-model")`
  - `project(":core-dsp")`
  - `project(":core-library")`
  - `project(":core-network")`
  - `project(":integration-api")`
  - `project(":core-ui")`

## Error Handling

**Patterns:**
- Use domain-specific sealed results (`com.nodeplayer.common.result.NodeResult`) or standard `kotlin.Result` for fallible operations instead of unhandled checked exceptions.
  ```kotlin
  sealed interface NodeResult<out T> {
      data class Success<out T>(val data: T) : NodeResult<T>
      data class Failure(val error: Throwable, val message: String = error.message ?: "Unknown error") : NodeResult<Nothing>
      ...
  }
  ```
- Offline degradation and queue retry pattern: when external APIs fail (such as Last.fm scrobble), enqueue the payload locally in Room database (`dao.enqueueScrobble(...)`) and flush later without crashing playback (`app-android/src/main/kotlin/com/nodeplayer/music/library/LastFmClient.kt`).
- Hardware decoders and audio engines fail gracefully returning fallback values or `-1` without throwing uncaught runtime exceptions (`app-desktop/src/test/kotlin/com/nodeplayer/desktop/DesktopAudioAndDatabaseTest.kt`).

## Logging

**Framework:**
- Core modules and unified logging utilize `com.nodeplayer.common.logging.NodeLog` (`core-common/src/main/kotlin/com/nodeplayer/common/logging/NodeLog.kt`).
- Android platform services utilize `android.util.Log` or delegate to `NodeLog` with tag prefixing.

**Patterns:**
- Categorized hierarchical tags using `feature/subsystem` syntax (e.g., `NodeLog.i("nodeconnect/logging", "Persistent file logging initialized")`).
- In-memory ring buffer (default 1000 entries) via `ConcurrentLinkedDeque` supporting exportable diagnostics dumps (`NodeLog.exportFormattedLog()`, `NodeLog.getEntriesByFeature()`).
- Avoid logging raw user credentials or secret keys; log masked status checks (e.g., `"Last.fm configured: key yes, secret no"` in `LastFmClient.kt`).

## Comments

**When to Comment:**
- Audio DSP algorithms require mathematical derivations and formula explanations (e.g., biquad peaking filter formulas, equal-power sine/cosine crossfade curves in `core-dsp/src/test/kotlin/com/nodeplayer/dsp/CrossfadeCurveMathTest.kt`).
- Binary protocol frame specifications in network modules document byte offsets, bit-packing layouts, andendianness.
- Explain non-obvious workarounds for Android audio hardware quirks (e.g., dual ExoPlayer DSP chain isolation to prevent filter state click/pop).

**JSDoc/TSDoc:**
- KDoc format (`/** ... */`) is used on public API interfaces, contracts (`PlatformMediaSessionBridge`, `ScrobbleFeatureApi`), and data model parameters.

## Function Design

**Size:**
- Small, focused single-responsibility functions. Audio processing loops are vectorized or structured for deterministic zero-allocation hot paths.

**Parameters:**
- Default argument values are utilized extensively on data classes and configuration functions (e.g., `AudioEffectConfig`, `GlassThemeConfig`).
- Lambdas and trailing functional callbacks used for asynchronous UI event handling (e.g., `onTrackTransition: (Track) -> Unit`).

**Return Values:**
- Sealed interfaces (`NodeResult<T>`) or `Result<T>` for I/O and network operations.
- Explicit nullable types (`T?`) for searches and lookups rather than throwing `NoSuchElementException`.

## Module Design

**Exports:**
- Explicit module boundaries enforced via multi-project Gradle setup.
- Feature internals are quarantined: cross-feature dependencies within `:app-android` must consume public interfaces rather than `.internal.` packages, verified by `ArchitectureBoundaryTest`.
- Shared Compose Multiplatform UI is centralized in `:core-ui` for multi-target layout rendering across Android and Windows desktop.

**Barrel Files:**
- Kotlin package-level exports; related entities are co-located in cohesive packages (e.g., `com.nodeplayer.model`, `com.nodeplayer.network.protocol`).

---

*Convention analysis: 2026-11-05*
