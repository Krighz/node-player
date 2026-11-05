# Codebase Structure

**Analysis Date:** 2026-11-05

## Directory Layout

```
node-player/
├── .github/                      # GitHub Actions workflows and CI configurations
├── .planning/                    # Project roadmap, GSD documentation, codebase maps
│   └── codebase/                 # Architectural and structural codebase documentation
├── app-android/                  # Android application module
│   ├── src/
│   │   ├── androidTest/          # Android instrumentation & integration tests
│   │   └── main/                 # Android source code, AndroidManifest, resources
│   │       ├── assets/           # Bundled assets (fonts, default assets)
│   │       ├── kotlin/           # Android Kotlin source code (UI, Service, DAOs)
│   │       └── res/              # Android layout XMLs, drawables, mipmaps, values
│   └── build.gradle.kts          # Android build script
├── app-desktop/                  # Desktop application module (Compose for Desktop)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/           # Desktop main entry point, WASAPI engine, decoders
│   │   │   └── resources/        # Desktop resources (app icons, assets)
│   │   └── test/                 # Desktop unit & audio engine tests
│   └── build.gradle.kts          # Desktop build script
├── app-windows/                  # Windows platform target / native packaging artifacts
├── core-common/                  # Core abstractions, logging, result models, dispatchers
│   ├── src/
│   │   ├── main/kotlin/          # Pure Kotlin platform interfaces & NodeLog
│   │   └── test/kotlin/          # Architecture boundary & unit tests
│   └── build.gradle.kts
├── core-dsp/                     # Audio DSP engine (Filters, EQ, Loudness, FFT)
│   ├── src/
│   │   ├── main/kotlin/          # Biquad filters, DSP chain, loudness, meters
│   │   └── test/kotlin/          # DSP math, audio analysis, filter verification tests
│   └── build.gradle.kts
├── core-library/                 # Audio tag parser, LRC lyrics, track repositories
│   ├── src/
│   │   ├── main/kotlin/          # ID3/FLAC parsers, duplicate finder, stats tracker
│   │   └── test/kotlin/          # Metadata parsing & library unit tests
│   └── build.gradle.kts
├── core-model/                   # Pure Kotlin domain data models & entities
│   ├── src/
│   │   └── main/kotlin/          # Track, Album, Artist, Equalizer, Theme models
│   └── build.gradle.kts
├── core-network/                 # Cross-device P2P protocol, casting, discovery & sync
│   ├── src/
│   │   ├── main/kotlin/          # NodeConnectHub, mDNS discovery, TLS transport, jitter buffer
│   │   └── test/kotlin/          # Protocol framing, mTLS, sync, casting unit tests
│   └── build.gradle.kts
├── core-ui/                      # Shared Compose Multiplatform UI components & themes
│   ├── src/
│   │   ├── androidMain/kotlin/   # Android-specific UI adaptations
│   │   ├── commonMain/kotlin/    # Cross-platform Compose UI, screens, players, visualizers
│   │   └── desktopMain/kotlin/   # Desktop-specific UI adaptations
│   └── build.gradle.kts
├── dist/                         # Packaged release binaries & distributions
├── docs/                         # Project design docs, protocols, specifications
├── gradle/                       # Gradle wrapper binaries and libraries
├── integration-api/              # Pure Kotlin companion contract for TV/Watch/Auto
│   ├── src/
│   │   ├── main/kotlin/          # Integration contracts, protocol versions, models
│   │   └── test/kotlin/          # Integration protocol tests
│   └── build.gradle.kts
├── scripts/                      # Build automation, packaging, and CI helper scripts
├── site/                         # Landing page and documentation website
├── build.gradle.kts              # Root Gradle build configuration
├── gradle.properties             # JVM args, AndroidX and Kotlin compiler properties
└── settings.gradle.kts           # Multi-module Gradle project configuration
```

## Directory Purposes

**`app-android/`:**
- Purpose: Complete Android application implementation.
- Contains: Android Jetpack Compose screens, ViewModels, `PlaybackService` (Media3), Room entities/DAOs, notification providers, update managers, widget providers, and Android diagnostic tools.
- Key files: `app-android/src/main/kotlin/com/nodeplayer/music/MainActivity.kt`, `app-android/src/main/kotlin/com/nodeplayer/music/service/PlaybackService.kt`, `app-android/src/main/kotlin/com/nodeplayer/music/ui/PlayerViewModel.kt`.

**`app-desktop/`:**
- Purpose: Standalone desktop player targeting Windows/macOS/Linux via Compose Multiplatform.
- Contains: Desktop application lifecycle, WASAPI audio output engine via JNA, multi-format audio decoders (Composite/FLAC/MP3/FFmpeg/JVM), desktop SQLite database, and file system scanner.
- Key files: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/Main.kt`, `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/WindowsPlaybackEngine.kt`, `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/WasapiAudioOutput.kt`.

**`core-ui/`:**
- Purpose: Reusable cross-platform user interface and visual design system.
- Contains: Glassmorphic theme styling (`GlassTheme.kt`), motion tokens, player deck docks, now playing screens, parametric equalizer curve graphs, soundwave visualizers, and spectrogram views.
- Key files: `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/SharedPlayerLayout.kt`, `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/player/BottomPlayerDock.kt`, `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/theme/GlassTheme.kt`.

**`core-network/`:**
- Purpose: Node Connect peer-to-peer networking, device pairing, audio casting, and synchronization.
- Contains: UDP mDNS discovery, ADB bridge discovery, TLS framing, cryptographic key storage, PCM jitter buffer, handoff protocols, and hybrid logical clock (HLC) sync engine.
- Key files: `core-network/src/main/kotlin/com/nodeplayer/network/service/NodeConnectHub.kt`, `core-network/src/main/kotlin/com/nodeplayer/network/transport/NodeConnection.kt`, `core-network/src/main/kotlin/com/nodeplayer/network/cast/NodeConnectCastService.kt`.

**`core-dsp/`:**
- Purpose: Pure Kotlin digital signal processing engine.
- Contains: 10-band IIR biquad filters, EBU R128 loudness normalization, FFT spectrum analyzers, dynamic range meters, and crossfade curve mathematics.
- Key files: `core-dsp/src/main/kotlin/com/nodeplayer/dsp/DspChain.kt`, `core-dsp/src/main/kotlin/com/nodeplayer/dsp/BiquadFilter.kt`, `core-dsp/src/main/kotlin/com/nodeplayer/dsp/LoudnessR128.kt`.

**`core-library/`:**
- Purpose: Audio file parsing, metadata extraction, lyrics handling, and library persistence interfaces.
- Contains: ID3 and FLAC header parsers, LRC synchronized lyrics parsers, duplicate audio finders, and `TrackRepository` contracts.
- Key files: `core-library/src/main/kotlin/com/nodeplayer/library/repository/TrackRepository.kt`, `core-library/src/main/kotlin/com/nodeplayer/library/AudioHeaderParser.kt`, `core-library/src/main/kotlin/com/nodeplayer/library/LrcParser.kt`.

**`core-model/`:**
- Purpose: Universal domain models and entities shared across all layers.
- Contains: Models for tracks, albums, artists, playlists, audio effect configurations, theme preferences, and widget settings.
- Key files: `core-model/src/main/kotlin/com/nodeplayer/model/Track.kt`, `core-model/src/main/kotlin/com/nodeplayer/model/AudioAndThemeConfig.kt`, `core-model/src/main/kotlin/com/nodeplayer/model/Lyrics.kt`.

**`core-common/`:**
- Purpose: Foundational abstractions and cross-platform infrastructure.
- Contains: Platform hardware interfaces (`AudioDecoder`, `AudioOutput`, `KeyValueStore`), structured logging framework (`NodeLog`), error result primitives (`NodeResult`), and coroutine dispatcher providers.
- Key files: `core-common/src/main/kotlin/com/nodeplayer/common/logging/NodeLog.kt`, `core-common/src/main/kotlin/com/nodeplayer/common/platform/PlatformServices.kt`, `core-common/src/main/kotlin/com/nodeplayer/common/result/NodeResult.kt`.

**`integration-api/`:**
- Purpose: Decoupled client contract for companion ecosystem devices (Wear OS, Auto, Android TV).
- Contains: Playback commands, track projections, now playing state contracts, and protocol versioning.
- Key files: `integration-api/src/main/kotlin/com/nodeplayer/integration/api/IntegrationContract.kt`, `integration-api/src/main/kotlin/com/nodeplayer/integration/api/IntegrationModels.kt`.

## Key File Locations

**Entry Points:**
- `app-android/src/main/kotlin/com/nodeplayer/music/NodePlayerApplication.kt`: Android Application initialization and dependency wiring.
- `app-android/src/main/kotlin/com/nodeplayer/music/MainActivity.kt`: Android main UI activity and window configuration.
- `app-android/src/main/kotlin/com/nodeplayer/music/service/PlaybackService.kt`: Android background audio playback service.
- `app-desktop/src/main/kotlin/com/nodeplayer/desktop/Main.kt`: Desktop application main entry point.

**Configuration:**
- `settings.gradle.kts`: Gradle root module definitions.
- `build.gradle.kts`: Root Gradle build script and plugin declarations.
- `gradle.properties`: Build environment, JVM tuning, and Kotlin/Compose compiler flags.
- `app-android/build.gradle.kts`: Android SDK versions, dependencies, and build flavors.
- `app-desktop/build.gradle.kts`: Desktop JVM runtime and packaging settings.

**Core Logic:**
- `core-dsp/src/main/kotlin/com/nodeplayer/dsp/DspChain.kt`: Real-time DSP audio processing chain.
- `core-network/src/main/kotlin/com/nodeplayer/network/service/NodeConnectHub.kt`: Central coordination hub for Node Connect.
- `core-library/src/main/kotlin/com/nodeplayer/library/AudioHeaderParser.kt`: Audio metadata and tag extraction.
- `app-android/src/main/kotlin/com/nodeplayer/music/audio/CrossfadePlayerManager.kt`: Gapless and crossfade playback orchestration on Android.
- `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/WindowsPlaybackEngine.kt`: WASAPI playback loop on Desktop.

**Testing:**
- `core-common/src/test/kotlin/com/nodeplayer/common/ArchitectureBoundaryTest.kt`: Architectural boundary rule enforcement.
- `core-dsp/src/test/kotlin/com/nodeplayer/dsp/DspChainTest.kt`: DSP audio filter and processing verification.
- `core-network/src/test/kotlin/com/nodeplayer/network/AudioCastIntegrationTest.kt`: Network casting and jitter buffer verification.
- `app-android/src/androidTest/kotlin/com/nodeplayer/music/CrossfadeIntegrationTest.kt`: Android playback crossfade test suite.

## Naming Conventions

**Files:**
- Kotlin classes and objects: PascalCase matching the primary type (e.g., `DspAudioProcessor.kt`, `WindowsPlaybackEngine.kt`).
- Extension / platform expect-actual files: PascalCase with platform suffix (e.g., `PlatformArtworkProvider.android.kt`, `PlatformArtworkProvider.desktop.kt`).
- Composables and UI views: PascalCase suffixed with View, Screen, or Dialog (e.g., `SharedPlayerLayout.kt`, `EqualizerView.kt`, `NodeConnectPairingDialog.kt`).
- Unit and integration tests: PascalCase suffixed with `Test` (e.g., `DspChainTest.kt`, `CrossfadeIntegrationTest.kt`).

**Directories:**
- Root modules: kebab-case with descriptive prefix (e.g., `core-*`, `app-*`, `integration-*`).
- Kotlin package directories: lowercase matching reverse-DNS package namespace (e.g., `com/nodeplayer/network/transport`).

## Where to Add New Code

**New Feature (e.g., Cloud Sync, Lyrics Provider, New Audio Format):**
- Domain models: Add entities to `core-model/src/main/kotlin/com/nodeplayer/model/`.
- Business logic & parsing: Implement in `core-library/src/main/kotlin/com/nodeplayer/library/` or `core-network/`.
- Platform interfaces: Declare interfaces in `core-common/src/main/kotlin/com/nodeplayer/common/platform/`.
- Concrete implementations: Add to `app-android/src/main/kotlin/com/nodeplayer/music/` and `app-desktop/src/main/kotlin/com/nodeplayer/desktop/`.
- Unit tests: Add corresponding test classes in the respective module's `src/test/kotlin/`.

**New UI Component / Screen:**
- Cross-platform shared UI: Add composables to `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/` or `.../screens/`.
- Android-specific views/widgets: Add to `app-android/src/main/kotlin/com/nodeplayer/music/ui/`.
- Platform-specific resources: Place in `core-ui/src/androidMain/` or `core-ui/src/desktopMain/`.

**Audio DSP Effect:**
- Implementation: Add filter/effect processor to `core-dsp/src/main/kotlin/com/nodeplayer/dsp/`.
- Integration: Register processor inside `DspChain.kt`.
- Tests: Add unit tests under `core-dsp/src/test/kotlin/com/nodeplayer/dsp/`.

**Utilities & Shared Helpers:**
- Core utilities (logging, async, results): Add to `core-common/src/main/kotlin/com/nodeplayer/common/`.
- UI utility functions: Add to `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/theme/ColorUtils.kt` or `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/`.

## Special Directories

**`.planning/`:**
- Purpose: Contains GSD planning state, execution roadmaps, milestone tracking, and codebase architectural documentation.
- Generated: Maintained by GSD tooling; human-readable.
- Committed: Yes.

**`app-windows/`:**
- Purpose: Target staging directory for Windows native binaries, installers, and MS-specific packaging artifacts.
- Generated: Partially populated during Windows deployment workflows.
- Committed: Yes.

**`dist/`:**
- Purpose: Contains packaged builds and distribution artifacts.
- Generated: Yes (during release builds).
- Committed: Tracked per release needs or gitignored depending on pipeline.

---

*Structure analysis: 2026-11-05*
