# Node Player - Project State

Last Updated: 2026-11-03 13:06:00 +05:30
Current Version: v1.4.0 (Windows Desktop & Android)
Current Commit: `fad3000` on branch `feature/windows`

---

## 1. What Is Built

### Windows Desktop App (v1.4.0)
- **Engine & Output**: Dual WASAPI output (`WasapiAudioOutput`) supporting WASAPI Shared and WASAPI Exclusive (Bit-Perfect) modes with 32-bit float internal routing.
- **Decoding**: Hybrid `CompositeAudioDecoder` utilizing pure JVM decoding for WAV/AIFF and FFmpeg pipe for AAC, ALAC, Opus, OGG, and MP3.
- **Shared Code Reuse**: 100% reuse of `:core-dsp` (`DspChain`, parametric EQ, preamp, limiter), `:core-model` (`Track`, `AudioEffectConfig`), `:core-library` (`AudioHeaderParser`), and `:core-common` platform contracts.
- **Storage**: SQLite database at `%APPDATA%\NodePlayer\library.db` and settings at `%APPDATA%\NodePlayer\settings.properties`.
- **UI & Experience**: Compose Desktop liquid-glass interface with Skia runtime refraction shaders, collapsible navigation sidebar, 10-band equalizer, search, and persistent Now Playing glass dock.

### Android Core Architecture & Audio
- **Audio Engine**: 32-bit float audio pipeline, resampler options (Auto, 44.1k, 48k, 96k, 192k), gapless playback, crossfade manager.
- **DSP Chain**: 10-band equalizer, bass boost, virtualizer, reverb, loudness enhancer.
- **System-Wide EQ**: Background audio effect session interception via `AudioSessionReceiver` and `SystemWideEqService`.
- **MediaSession & Service**: AndroidX Media3 `MediaSessionService` (`PlaybackService`) with notification channel and lockscreen controls.

### Liquid-Glass UI & Dock Overhaul (v1.3.1)
- **Dock Refraction & Styling**:
  - Removed yellow amber rim/border tints (`Color(0xFFFDE68A)`, `0xFFF59E0B`), replacing with pure crystal pearl specular gradients (`Color.White` + `Color(0xFFBAE6FD)`).
  - Passed `hazeState` to `glassSurface` inside the selector bubble, enabling Haze backdrop blur layer to be sampled by the `glassLensBubble` AGSL refraction shader.
  - Active tab icon set to pure `Color.White` with dynamic optical magnification (`1.22x`).
  - Bubble motion dynamic spring physics wired to `MotionTokens.resolveSpringSpec(themeConfig)`.
  - Dock pill opacity dynamically scales with `(themeConfig.effectiveTintAlpha * 0.75f)`.
- **Now Playing Liquid-Glass Screen**:
  - Removed opaque `Color(0xFF07080E)` root background and high-contrast dark scrims, restoring fluid background bleeding.
  - Passed `themeConfig` and `sheetHazeState` to `LiquidGlassBackground`.
  - Replaced flat play/pause button with a circular liquid-glass lens bubble (`glassLensBubble` + `glassSurface` + concentric glass ring) matching reference design.
  - Seekbar restyled with crystal translucent track and pure white active track.
- **Customize UI Controls**:
  - Fixed glass opacity slider affecting all `GlassCard` and dock components (`tintAlpha` null default).
  - Fixed glass grain slider via cached 64x64 blue noise bitmap shader (`GlassNoiseCache`).
  - Added continuous Lens Refraction Strength slider (`0.20f..1.50f`).
  - Added "BACKGROUND AMBIENCE & STYLES" section: 6 style chips (`LIQUID_BLOBS`, `AURORA`, `MESH_GRADIENT`, `ALBUM_ART_BLUR`, `SLOW_CAUSTICS`, `STATIC`), animation speed dial (`0.2x..3.0x`), intensity slider (`0.1..1.0`), color source chips, and animate-only-while-playing switch.
- **Widgets Overhaul**:
  - Replaced hardcoded amber shader colors with liquid obsidian depth, pearl caustics, and anti-aliased crystal specular rim.
  - Added High-DPI 2x resolution rendering for 1080p+ screens (`1080x560` for 4x2, `1080x760` for 4x3, `1080x280` for 4x1, `560x560` for 2x2).
  - Added widget settings: `refractionStyle` preset, `edgeHighlightStrength` slider, `highDpi` toggle, and reduced default glass opacity to 0.52f.
- **Online Sync / LRCLIB Dropped**:
  - Online sync deferred; LRCLIB disabled and labeled deferred; 0 network requests executed; pure local offline operation.

---

## 2. Verification Status & Evidence

| Component / Task | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| Core Common Module | `:core-common:test` | Done | Gradle output: `BUILD SUCCESSFUL in 14s` |
| Core DSP Unit Tests | `:core-dsp:test` | Done | Gradle output: `BUILD SUCCESSFUL in 14s` |
| Android Unit Tests | `:app-android:testDebugUnitTest` | Done | Gradle output: `BUILD SUCCESSFUL in 14s`, 43 actionable tasks |
| Widget Split & API | Compile & unit tests | Done | Commit `cd33f48` (`WidgetArtworkLoader`, `WidgetRemoteViewsHelper`, `WidgetFeatureApi`) |
| Scrobble Feature API | Compile verification | Done | Commit `5dc2888` (`ScrobbleFeatureApi` on `LastFmClient`) |
| Updater Feature API | Compile verification | Done | Commit `8e0fdad` (`UpdaterFeatureApi` on `UpdateManager`) |
| Diagnostics Feature API | Compile verification | Done | Commit `cea3a5b` (`DiagnosticsFeatureApi` on `DiagnosticsDumpManager`) |
| Lyrics Feature API | Compile verification | Done | Commit `9f2c37a` (`LyricsFeatureApi` on `LyricsManager`) |
| Architecture Boundary Test | `:core-common:test` | Done | Commit `4199131` (`ArchitectureBoundaryTest` passing) |
| Integration API Module | `:integration-api:test` | Done | Commit `df20102` (`ProtocolVersionTest` passing) |
| Staging Build (`.staging`) | `:app-android:assembleStaging` | Done | R8 minified, verified v2 signing, SHA-256 `1b6d42e0...` |
| Release Build (v1.4.0) | `:app-android:assembleRelease` | Done | R8 minified, verified v2 signing, SHA-256 `5278a574...` |
| GitHub Draft Release | `gh release list` | Done | Published strictly as Draft `v1.4.0` with release + staging APKs |
| Debug APK Build | `:app-android:assembleDebug` | Done | Gradle output: `BUILD SUCCESSFUL in 23s`, 49 actionable tasks |
| Release APK Build | `:app-android:assembleRelease` | Done | Gradle output: `BUILD SUCCESSFUL in 2m 26s`, 62 actionable tasks |
| APK Signing Verification | `apksigner.bat verify --verbose` | Done | Output: `Verified using v2 scheme (APK Signature Scheme v2): true` |
| GitHub Release v1.3.1 | `gh release view v1.3.1` | Done | Release published to GitHub Releases with `NodePlayer.apk` & `NodePlayer.apk.sha256` |
| Dock Refraction on Device | Physical device test | Not verified | Compiled in debug APK; physical visual validation pending user test |
| Customize UI Sliders on Device | Physical device test | Not verified | Compiled in debug APK; physical UI interaction pending user test |
| High-DPI Widgets on Device | Physical device test | Not verified | Compiled in debug APK; home screen launcher placement pending user test |
| Real-time Display FPS | `dumpsys gfxinfo` / Systrace | Not verified | Live frame profiling not run during build turn |

---

## 3. Known Issues & Unverified Items
- **On-device visual verification**: Changes compiled into debug APK, but live rendering on the Realme GT 6T screen has not yet been exercised via physical user touch.
- **Online Sync**: Dropped and deferred as requested. Local `.lrc` file parsing remains fully functional.

---

## 4. Key Architectural Decisions
- **Haze Input for AGSL Refraction**: Android `RenderEffect.createRuntimeShaderEffect` modifies the current layer's drawn buffer (`"contents"`). By chaining `glassSurface(hazeState)` inside the bubble Box, the blurred screen buffer is drawn and becomes the input to AGSL, enabling true optical magnification and dispersion of the underlying UI.
- **Static High-DPI RemoteViews**: To preserve battery while achieving crisp liquid-glass visuals on 1080p+ OLED screens, launcher widget bitmaps are rendered at 2x native resolution via `HardwareRenderer` on track change events only.
