# Node Player - Project State

Last Updated: 2026-11-03 14:55:00 +05:30
Current Version: v1.4.0
Current Branch: `feature/shared-ui`

> [!NOTE]
> Active development on `feature/shared-ui` has fixed lossless FLAC decoding, wired 10-band Equalizer and DSP settings persistence, moved music folder management inside Settings, and revamped the liquid-glass optical shaders with live user tokens (commit `b752e8a`).

---

## 1. What Is Built

### Compose Multiplatform Architecture (`:core-ui`)
- **Shared UI Layer**: Complete port of Node Player's interface into Compose Multiplatform (commonMain, androidMain, desktopMain):
  - `SharedPlayerLayout`: Master adaptive layout providing 16:9 sidebar layout for desktop/tablets and vertical portrait layout for phones. Provides live `LocalGlassTokens` connected to `themeConfig`.
  - `BottomPlayerDock`: Liquid glass media dock with scrubber, visualizer, volume, and playback controls.
  - `NowPlayingView`: Synchronized `.lrc` lyrics parsing (`LrcParser`), visualizer, and queue management.
  - `EqualizerView`: Full 10-band biquad parametric EQ with interactive draggable nodes, tone knobs, Fletcher-Munson loudness equalizer, harmonic bass exciter, spatial stereo width, studio reverb, peak limiter, preset chips, and AutoEQ import.
  - `CustomizeUiView`: 8 color palettes, 6 liquid shader modes, animation speed/intensity, Snell's law refraction slider, rim highlight, and display options.
  - `LibraryView`: Songs, Albums, Artists, Folders tabs, search, and list/grid toggle.
  - `SettingsView`: Music folder picker and library rescan inside Settings, WASAPI exclusive/shared switch, crossfade slider, gapless toggle, ReplayGain mode, and Last.fm.
  - `LiquidGlassBackground`: Pure multiplatform canvas Skia math rendering supporting all 6 liquid animated shaders (Blobs, Aurora, Mesh Gradient, Album-Art Blur, Slow Caustics, Static) with dynamic continuous organic wave and blob motion.

### Desktop Shell (`:app-desktop`)
- **Lossless Audio Decoders**: Pure Java FLAC decoding (`FlacAudioDecoder`) extracting raw subframe channel data directly for 16-bit, 24-bit, and 32-bit FLAC streams with random-access seeking (`RandomFileInputStream`), pure Java MP3 decoding (`jlayer:1.0.1`), JavaSound 16/24/32-bit PCM/WAV/AIFF decoder (`JvmAudioDecoder`), and FFmpeg pipe fallback for AAC/ALAC/Opus/OGG (`FFmpegAudioDecoder`).
- **Audio Output**: `WasapiAudioOutput` supporting WASAPI shared/exclusive mode, sample rate ladder fallback (`targetRate -> 48000 -> 44100`), and real-time float buffer resampling.
- **Playback Engine**: `WindowsPlaybackEngine` combining decoders, `DspChain` effects with live coefficient re-computation on sample rate change, queue management, volume, repeat, shuffle, and lyrics sync.
- **Data Persistence**: KeyValueStore persisted to `%APPDATA%\NodePlayer\settings.properties` storing all EQ bands, tone controls, loudness, harmonic bass, reverb, theme configs, and last music folder path (`DesktopSettingsStore`).
- **Windows Packaging**: Compose Desktop MSI installer (`NodePlayer-1.4.0.msi`, 111,284,276 bytes) and standalone distributable application directory.

---

## 2. Verification Status & Evidence

| Component / Task | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| Desktop Unit Tests | `:app-desktop:test` | Done | Gradle output: `BUILD SUCCESSFUL in 14s`, 7 tests passed (WAV decoder, FLAC lifecycle, DB, Store, WASAPI, Scanner), 0 failures |
| Core UI Multiplatform Assemble | `:core-ui:assemble` | Done | Gradle output: `BUILD SUCCESSFUL in 30s` (desktopJar, bundleReleaseAar, bundleDebugAar) |
| Android Debug APK | `:app-android:assembleDebug` | Done | Output: `app-android-debug.apk` (23,584,384 bytes, 14:31:20) |
| Android Unit Tests | `:app-android:testReleaseUnitTest` | Done | Gradle output: `BUILD SUCCESSFUL in 1m 28s`, 0 failures |
| Desktop Distributable App | `:app-desktop:createDistributable` | Done | Output: `D:\AI MAKING\node-player\app-desktop\build\compose\binaries\main\app\NodePlayer\NodePlayer.exe` (548,864 bytes) |
| Desktop MSI Installer Package | `:app-desktop:packageMsi` | Done | Output: `NodePlayer-1.4.0.msi` (111,284,276 bytes, SHA-256 `6B08E4C75218BA0D4A1B35F4744EAD05BD8781F0717AE172912E446C9747C48C`) |


---

## 3. Known Issues & Unverified Items
- **On-device visual verification**: Changes compiled into debug APK, but live rendering on the Realme GT 6T screen has not yet been exercised via physical user touch.
- **Online Sync**: Dropped and deferred as requested. Local `.lrc` file parsing remains fully functional.

---

## 4. Key Architectural Decisions
- **Haze Input for AGSL Refraction**: Android `RenderEffect.createRuntimeShaderEffect` modifies the current layer's drawn buffer (`"contents"`). By chaining `glassSurface(hazeState)` inside the bubble Box, the blurred screen buffer is drawn and becomes the input to AGSL, enabling true optical magnification and dispersion of the underlying UI.
- **Static High-DPI RemoteViews**: To preserve battery while achieving crisp liquid-glass visuals on 1080p+ OLED screens, launcher widget bitmaps are rendered at 2x native resolution via `HardwareRenderer` on track change events only.
