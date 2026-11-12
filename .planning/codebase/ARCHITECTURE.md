<!-- refreshed: 2026-11-11 -->
# Architecture

**Analysis Date:** 2026-11-11

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Presentation & Platform Heads                         │
├──────────────────────────────────────┬──────────────────────────────────────┤
│             app-android              │             app-desktop              │
│ `app-android/src/main/kotlin/...`    │ `app-desktop/src/main/kotlin/...`    │
│  - Jetpack Compose + Haze shaders    │  - Compose Multiplatform (Desktop)   │
│  - Media3 / ExoPlayer Service        │  - WASAPI + JNA Native Engine        │
│  - Room DB + MediaStore scanner      │  - Desktop SQLite + File Scanner     │
└──────────────────┬───────────────────┴──────────────────┬───────────────────┘
                   │                                      │
                   ▼                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Shared UI Layer                                  │
│ `core-ui/src/commonMain/kotlin/...`                                         │
│  - SharedPlayerLayout, BottomPlayerDock, NowPlayingView                     │
│  - EqualizerView, GlassTheme, WaveformSeekbar, SpectrogramView             │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Cross-Device Networking & Sync                        │
│ `core-network/src/main/kotlin/...`                                          │
│  - NodeConnectHub (mDNS / AdbBridge / TLS / Socket Framing)                 │
│  - Real-time Audio Cast (PCM jitter buffer), Handoff, Two-way Sync          │
│ `integration-api/src/main/kotlin/...`                                       │
│  - Pure Kotlin Companion Contract (NowPlayingState, PlaybackCommand)        │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Audio DSP & Domain Business Logic                       │
├──────────────────────────────────────┬──────────────────────────────────────┤
│               core-dsp               │             core-library             │
│ `core-dsp/src/main/kotlin/...`       │ `core-library/src/main/kotlin/...`   │
│  - 10-band Biquad IIR Filter         │  - TrackRepository interface         │
│  - R128 Loudness Normalization       │  - AudioHeaderParser (ID3/FLAC)      │
│  - FFT Spectrum & DynamicRangeMeter  │  - LrcParser, DuplicateFinder        │
└──────────────────┬───────────────────┴──────────────────┬───────────────────┘
                   │                                      │
                   ▼                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Core Models & Abstractions                          │
│ `core-model/src/main/kotlin/...`    │ `core-common/src/main/kotlin/...`     │
│  - Track, Album, Artist, Equalizer  │  - DispatcherProvider, NodeLog        │
│  - AudioAnalysisModels, ThemeConfig │  - AudioDecoder, AudioOutput, Result  │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2026-11 Updates (Milestone v2.3 & Online Services)
- **InnerTube / YouTube Music Integration:** Seamless fallback recommendations, home feed generation, dynamic shelves (core-network/recommender/, core-network/search/), and stream resolution using VisionOS/iOS clients to avoid playback 403s.
- **Spotify Migration Bridge:** Cross-service matching engine (core-network/spotify/SpotifyTrackMatcher.kt) matching ISRC metadata and fuzzy Levenshtein distance with PKCE OAuth authentication.
- **Synchronized Lyrics Engine:** LRCLIB network client (core-network/lyrics/LrcLibClient.kt) feeding interactive Apple Music-style lyrics (FullScreenLyricsView.kt, AppleMusicLyricsView.kt).
- **Liquid Glass & Convx Dock:** Liquid backdrop shaders, gesture-driven auto-hiding dock (ConvxLiquidDock.kt, DockScrollController.kt), vertical equalizer sliders with horizontal Hz scrolling (LiquidVerticalSlider.kt, EqualizerView.kt), and full token-driven theming (GlassTokens.kt).

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

**Overall:** Modular Layered Clean Architecture with Multiplatform UI & Platform Adaptors (Hexagonal/Ports-and-Adapters).

**Key Characteristics:**
- **Strict Architectural Boundaries:** Enforced by `core-common/src/test/kotlin/com/nodeplayer/common/ArchitectureBoundaryTest.kt`. Core modules (`core-common`, `core-model`, `core-dsp`, `core-library`) must remain pure Kotlin with zero Android framework dependencies.
- **Ports & Adapters (Hexagonal):** Platform abstractions (`AudioDecoder`, `AudioOutput`, `KeyValueStore`, `TrackRepository`) are defined in `core-common` and `core-library`. Android implements them via ExoPlayer/Room/SharedPreferences; Desktop implements them via WASAPI/JNA/SQLite.
- **Bidirectional P2P Interconnect:** Node Connect (`core-network`) acts as an orchestration bus allowing Android and Desktop instances to seamlessly discover each other, cast raw audio, transfer library state, or act as remote controllers.
- **Unidirectional Data Flow (UDF):** ViewModels and UI state holders expose Kotlin `StateFlow` streams observed by declarative Compose UI trees.

## Layers

**Presentation Layer (`app-android`, `app-desktop`, `core-ui`):**
- Purpose: Render user interfaces, handle user gestures, execute visual shaders, manage window/screen lifecycles.
- Location: `core-ui/src/commonMain/kotlin/com/nodeplayer/ui`, `app-android/src/main/kotlin/com/nodeplayer/music/ui`, `app-desktop/src/main/kotlin/com/nodeplayer/desktop`
- Contains: Compose Multiplatform composables, ViewModels, shaders (`LiquidGlassBackground`), canvas visualizers.
- Depends on: `core-model`, `core-dsp`, `core-library`, `core-network`, `core-common`.
- Used by: End users on Android mobile devices and Windows/Linux/macOS desktops.

**Network & Ecosystem Layer (`core-network`, `integration-api`):**
- Purpose: Device discovery, peer pairing, TLS socket framing, state handoff, Wi-Fi audio casting, external API integrations.
- Location: `core-network/src/main/kotlin/com/nodeplayer/network`, `integration-api/src/main/kotlin/com/nodeplayer/integration/api`
- Contains: `NodeConnectHub`, `NodeDiscovery`, `AdbBridge`, `JitterBuffer`, protocol serializers.
- Depends on: `core-common`, `core-model`, `core-library`.
- Used by: Application runtimes (`PlaybackService` in Android, `Main.kt` in Desktop).

**Domain & DSP Engine (`core-dsp`, `core-library`):**
- Purpose: Audio processing math (filtering, crossfading, FFT), audio file tag parsing, playlist calculations.
- Location: `core-dsp/src/main/kotlin/com/nodeplayer/dsp`, `core-library/src/main/kotlin/com/nodeplayer/library`
- Contains: `BiquadFilter`, `DspChain`, `LoudnessR128`, `AudioHeaderParser`, `LrcParser`.
- Depends on: `core-common`, `core-model`.
- Used by: Presentation layer, Playback engines (`DspAudioProcessor`, `WindowsPlaybackEngine`).

**Domain Core (`core-model`, `core-common`):**
- Purpose: Core domain entities, common result types, logging infrastructure, coroutine dispatchers, platform service interfaces.
- Location: `core-model/src/main/kotlin/com/nodeplayer/model`, `core-common/src/main/kotlin/com/nodeplayer/common`
- Contains: Domain data classes, enum definitions, interfaces for decoupling platform implementations.
- Depends on: Standard Kotlin library and coroutines.
- Used by: All modules across the codebase.

## Data Flow

### Primary Request Path (Playback Tap to Audio Output)

1. **User Interaction:** User taps a track item in Compose UI (`LibraryView.kt` or `LibraryScreen.kt`).
2. **ViewModel Dispatch:** `PlayerViewModel.playTrack(track)` (`app-android/.../PlayerViewModel.kt:390`) sends a playback command to `MediaController`.
3. **Service Reception:** `PlaybackService` (`app-android/.../PlaybackService.kt:180`) handles the `MediaSession` callback and prepares `ExoPlayer` or `CrossfadePlayerManager`.
4. **DSP Processing:** As PCM audio frames are decoded, `DspAudioProcessor` (`app-android/.../DspAudioProcessor.kt`) invokes `DspChain.process(buffer)` (`core-dsp/.../DspChain.kt:45`), applying active EQ biquads and R128 loudness gains.
5. **Hardware Output:** Processed audio is passed to the platform audio sink (`AudioTrack` on Android; WASAPI native render client via JNA on Desktop).
6. **State Emission:** `PlayerViewModel` updates `_playbackState` and `_currentPositionMs` `StateFlow`s; UI updates seekbars and visualizer composables.

### Node Connect Cross-Device Audio Cast Flow

1. **Discovery & Connection:** `NodeDiscovery` broadcasts/listens via UDP mDNS (`core-network/.../NodeDiscovery.kt`) or ADB forwarder (`AdbBridge.kt`).
2. **Handshake & Session:** TLS connection established; `NodeConnectHub` executes protocol handshake (`NodeConnectHub.kt:210`).
3. **Stream Initiation:** Sender initiates Cast session; decoded PCM frames are packaged into framed messages (`NodeFraming.kt`).
4. **Reception & Buffer:** Receiver `PlaybackService` passes incoming frames into `JitterBuffer` (`core-network/.../JitterBuffer.kt`) to eliminate network jitter before writing to hardware `AudioTrack`.

**State Management:**
- Kotlin `StateFlow` and Compose `mutableStateOf` manage reactive UI state.
- Local persistence uses Room SQLite on Android (`NodeDatabase.kt`) and JDBC SQLite on Desktop (`DesktopTrackDatabase.kt`).
- User settings are persisted through `SharedPreferences` (Android) and JSON configuration files (Desktop).

## Key Abstractions

**`PlatformServices` & `AudioDecoder` / `AudioOutput`:**
- Purpose: Abstract platform-specific hardware decoders and output streams so DSP and core models remain independent of Android/Desktop SDKs.
- Examples: `core-common/src/main/kotlin/com/nodeplayer/common/platform/AudioDecoder.kt`, `AudioOutput.kt`
- Pattern: Strategy / Ports and Adapters.

**`TrackRepository`:**
- Purpose: Provide unified access to music metadata, albums, artists, and playlists regardless of underlying storage mechanism.
- Examples: `core-library/src/main/kotlin/com/nodeplayer/library/repository/TrackRepository.kt`, `app-android/src/main/kotlin/com/nodeplayer/music/data/AppSettingsRepository.kt`, `app-desktop/src/main/kotlin/com/nodeplayer/desktop/data/DesktopTrackRepositoryAdapter.kt`
- Pattern: Repository Pattern.

**`DspProcessor` & `DspChain`:**
- Purpose: Composable pipeline for real-time audio manipulation (Parametric EQ, Bass Boost, Reverb, Crossfade).
- Examples: `core-dsp/src/main/kotlin/com/nodeplayer/dsp/DspChain.kt`, `core-dsp/src/main/kotlin/com/nodeplayer/dsp/DspProcessors.kt`
- Pattern: Pipeline / Chain of Responsibility.

**`NodeConnectHub`:**
- Purpose: Unified façade orchestrating discovery, socket transports, cryptographic pairing, library sync, handoff, and remote control.
- Examples: `core-network/src/main/kotlin/com/nodeplayer/network/service/NodeConnectHub.kt`
- Pattern: Facade / Mediator Pattern.

## Entry Points

**Android Application Entry:**
- Location: `app-android/src/main/kotlin/com/nodeplayer/music/NodePlayerApplication.kt` and `MainActivity.kt`
- Triggers: OS app launch or media button intents.
- Responsibilities: Initialize logging (`NodeLog`), crash logging, Coil image loaders, database singleton, and start background `PlaybackService`.

**Android Media Playback Service:**
- Location: `app-android/src/main/kotlin/com/nodeplayer/music/service/PlaybackService.kt`
- Triggers: Bound/started service lifecycle via AndroidX Media3 `MediaSessionService`.
- Responsibilities: Manage ExoPlayer instances, crossfade coordinator, lock screen notifications, system equalizer broadcasts, and incoming Node Connect cast streams.

**Desktop Application Entry:**
- Location: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/Main.kt`
- Triggers: OS process execution (`main()` function).
- Responsibilities: Initialize Compose Multiplatform desktop window (`application { Window(...) }`), WASAPI native audio engine, local SQLite database, settings store, and start `NodeConnectHub`.

## Architectural Constraints

- **Core Module Purity:** No Android dependencies allowed in `core-common`, `core-model`, `core-dsp`, or `core-library`. Enforced by automated test `core-common/.../ArchitectureBoundaryTest.kt`.
- **Feature Encapsulation:** Features inside `app-android` must never import `.internal.` packages belonging to other features.
- **Threading Model:**
  - Real-time DSP and WASAPI audio feeds run on dedicated real-time audio threads or `Dispatchers.Default`.
  - Database queries and file system tag scanning run strictly on `Dispatchers.IO`.
  - UI state transformations and Compose bindings run on `Dispatchers.Main`.
- **Global State:** Singletons are strictly scoped (e.g., `NodePlayerApplication.instance` in Android, `NodeLog` sink registry in common). State flows are retained within ViewModels or engine holders rather than mutable global variables.

## Anti-Patterns

### Anti-Pattern 1: Importing Platform Frameworks into Core Modules
**What happens:** Adding `import android.content.Context` or `import android.media.*` into `core-dsp` or `core-library`.
**Why it's wrong:** Breaks multiplatform reusability on Desktop and triggers failures in `ArchitectureBoundaryTest`.
**Do this instead:** Define platform interfaces in `core-common/src/main/kotlin/com/nodeplayer/common/platform/` and supply concrete implementations in `app-android` or `app-desktop`.

### Anti-Pattern 2: Directly Mutating Settings Outside Repository/Store Adapters
**What happens:** UI components modifying shared preference keys or desktop JSON files directly.
**Why it's wrong:** Causes synchronization drift between UI state, running DSP chains, and persistent storage.
**Do this instead:** Mutate settings through `AppSettingsRepository` or `DesktopSettingsStore`, which emit updated configuration state flows.

## Error Handling

**Strategy:** Explicit result wrapping using `NodeResult<T>` combined with Kotlin coroutine structured concurrency and defensive catch-all boundaries in background engines.

**Patterns:**
- **Domain Errors:** Operations that can fail (e.g., parsing audio headers or pairing devices) return `NodeResult.Success` or `NodeResult.Failure` (`core-common/.../NodeResult.kt`).
- **Crash Isolation:** Unhandled exceptions in background threads are logged via `Thread.setDefaultUncaughtExceptionHandler` and written to local crash logs (`LocalCrashLogger.kt`).
- **Audio Playback Resilience:** If native WASAPI output or ExoPlayer encounters a playback exception, players degrade gracefully, reset audio pipes, and report errors to the UI rather than crashing the process.

## Cross-Cutting Concerns

**Logging:** Centralized structured logging via `NodeLog` (`core-common/.../NodeLog.kt`). Sinks route logs to Android Logcat, Desktop stdout, and local persistent files (`node_connect.log`).
**Validation:** Tag header sanitization in `AudioHeaderParser.kt`, frame checksum/length verification in `NodeFraming.kt`, and mTLS certificate pinning in `PinningTrustManager.kt`.
**Security & Authentication:** Ed25519/ECDSA key pairs and X.509 certificates managed by `KeyManager.kt` and `SecureKeyStorage.kt` for secure mTLS cross-device communication.

---

*Architecture analysis: 2026-11-05*
