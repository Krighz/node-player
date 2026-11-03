# Node Player - Project State

Last Updated: 2026-11-03 14:20:00 +05:30
Current Version: v1.4.0
Current Branch: `feature/shared-ui`

> [!NOTE]
> Standalone Windows prototype work has been preserved on branch `wip/windows-standalone` (commit `3b4470f`) and `feature/windows`.
> Active development has converged into `feature/shared-ui`: single shared Compose Multiplatform UI across Android Phone (portrait), Android Landscape (16:9), and Windows Desktop.

---

## 1. What Is Built

### Compose Multiplatform Architecture (`:core-ui`)
- **Shared UI Layer**: Complete port of Node Player's interface into Compose Multiplatform (commonMain, androidMain, desktopMain):
  - `SharedPlayerLayout`: Master adaptive layout providing 16:9 sidebar layout for desktop/tablets and vertical portrait layout for phones.
  - `BottomPlayerDock`: Liquid glass media dock with scrubber, visualizer, volume, and playback controls.
  - `NowPlayingView`: Synchronized `.lrc` lyrics parsing (`LrcParser`), visualizer, and queue management.
  - `EqualizerView`: Full 10-band biquad parametric EQ with interactive draggable nodes, tone knobs, Fletcher-Munson loudness equalizer, harmonic bass exciter, spatial stereo width, studio reverb, peak limiter, preset chips, and AutoEQ import.
  - `CustomizeUiView`: 6 color palettes, 6 liquid shader modes, animation speed/intensity, Snell's law refraction slider, rim highlight, and display options.
  - `LibraryView`: Songs, Albums, Artists, Folders tabs, search, and list/grid toggle.
  - `SettingsView`: WASAPI exclusive/shared switch, crossfade slider, gapless toggle, ReplayGain mode, Last.fm, and music folder selector.
  - `LiquidGlassBackground`: Pure multiplatform canvas Skia math rendering supporting all 6 liquid animated shaders (Blobs, Aurora, Mesh Gradient, Album-Art Blur, Slow Caustics, Static) with OLED pure black support.

### Desktop Shell (`:app-desktop`)
- **Audio Output**: `WasapiAudioOutput` supporting WASAPI shared/exclusive mode, sample rate ladder fallback (`targetRate -> 48000 -> 44100`), and real-time float buffer resampling.
- **Audio Decoders**: Pure Java MP3 decoding (`jlayer:1.0.1`), pure Java FLAC decoding (`jflac-codec:1.5.2`), JavaSound PCM/WAV/AIFF decoder (`JvmAudioDecoder`), and FFmpeg pipe fallback for AAC/ALAC/Opus/OGG (`FFmpegAudioDecoder`).
- **Playback Engine**: `WindowsPlaybackEngine` combining decoders, `DspChain` effects, queue management, volume, repeat, shuffle, and lyrics sync.
- **Data Persistence**: KeyValueStore persisted to `%APPDATA%\NodePlayer\settings.properties` and local SQLite JDBC database (`DesktopTrackDatabase`).
- **Windows Packaging**: Compose Desktop MSI installer (`NodePlayer-1.4.0.msi`, 111,251,506 bytes) and standalone distributable application directory.

### Core Architecture & Audio (Android & Multiplatform)
- **Shared Interfaces**: `AudioOutput`, `AudioDecoder`, `MediaLibraryScanner`, `KeyValueStore`, `MediaSessionBridge`, `PlatformServices`, `TrackRepository`.
- **System-Wide EQ & Widgets**: Retained exclusively in `:app-android` as platform-specific services.

---

## 2. Verification Status & Evidence

| Component / Task | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| Complete Project Test Suite | `.\gradlew.bat test` | Done | Gradle output: `BUILD SUCCESSFUL in 1m 28s`, 143 actionable tasks, 0 failures |
| Desktop Unit Tests | `:app-desktop:test` | Done | Gradle output: `BUILD SUCCESSFUL in 20s`, 4 tests passed, 0 failures |
| Android Unit Tests | `:app-android:testDebugUnitTest` | Done | Gradle output: `BUILD SUCCESSFUL in 1m 19s`, 0 failures |
| Core UI Multiplatform Assemble | `:core-ui:assemble` | Done | Gradle output: `BUILD SUCCESSFUL in 33s` |
| Desktop Distributable App | `:app-desktop:createDistributable` | Done | Output: `D:\AI MAKING\node-player\app-desktop\build\compose\binaries\main\app\NodePlayer\NodePlayer.exe` (548,864 bytes) |
| Desktop MSI Installer Package | `:app-desktop:packageMsi` | Done | Output: `NodePlayer-1.4.0.msi` (111,251,506 bytes, SHA-256 `1F60C980153B6CF5B2D289E7121D4314A3D816795EDE2C47F43D229F80F10E8A`) |


---

## 3. Known Issues & Unverified Items
- **On-device visual verification**: Changes compiled into debug APK, but live rendering on the Realme GT 6T screen has not yet been exercised via physical user touch.
- **Online Sync**: Dropped and deferred as requested. Local `.lrc` file parsing remains fully functional.

---

## 4. Key Architectural Decisions
- **Haze Input for AGSL Refraction**: Android `RenderEffect.createRuntimeShaderEffect` modifies the current layer's drawn buffer (`"contents"`). By chaining `glassSurface(hazeState)` inside the bubble Box, the blurred screen buffer is drawn and becomes the input to AGSL, enabling true optical magnification and dispersion of the underlying UI.
- **Static High-DPI RemoteViews**: To preserve battery while achieving crisp liquid-glass visuals on 1080p+ OLED screens, launcher widget bitmaps are rendered at 2x native resolution via `HardwareRenderer` on track change events only.
