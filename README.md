<div align="center">

# Node Player 🎵
### *The Audiophile Liquid Glass Music Experience for Android*

[![Release](https://img.shields.io/badge/Release-v0.1.0--NodePlayer-00F0FF.svg?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Krighz/node-player/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0--14%2B-3DDC84.svg?style=for-the-badge&logo=android)](https://developer.android.com)
[![Audio Engine](https://img.shields.io/badge/Audio%20DSP-32--Bit%20Float-FF5722.svg?style=for-the-badge&logo=soundcharts&logoColor=white)]()
[![Aesthetics](https://img.shields.io/badge/Aesthetics-Liquid%20Glass%20%7C%20AGSL-2196F3.svg?style=for-the-badge)]()
[![Performance](https://img.shields.io/badge/Performance-120%20Hz%20%7C%20Zero--Jank-9C27B0.svg?style=for-the-badge)]()
[![Privacy](https://img.shields.io/badge/Privacy-Zero%20Telemetry%20%7C%20No%20Ads-success.svg?style=for-the-badge)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg?style=for-the-badge)](LICENSE)

<br/>

<p align="center">
  <strong>Node Player</strong> is a high-fidelity local Android music player engineered for pure acoustic transparency, fluid visionOS-inspired liquid glass aesthetics, and studio-grade signal processing. Powered by a custom <strong>32-bit floating-point DSP pipeline</strong>, dual-player crossfade engine, <strong>System-Wide hardware equalizer</strong>, and offscreen <strong>AGSL runtime shader home-screen widgets</strong> with real-time synchronized lyrics.
</p>

[Download APK](https://github.com/Krighz/node-player/releases/latest) • [Screenshots](#-photo-gallery--screenshots) • [Architecture](#-modular-architecture) • [DSP Suite](#-high-precision-32-bit-float-dsp-suite) • [Widgets](#-liquid-glass-home-screen-widgets) • [Settings Reference](#-comprehensive-settings-reference) • [Building](#-building-and-installing)

</div>

---

## 🌟 What Makes Node Player Special?

- **🌊 Real AGSL Liquid Glass UI & Optical Refraction**: Immersive glassmorphic design language built with Jetpack Compose, dynamic Haze backdrop blur, and Android 13+ AGSL runtime shaders featuring domain-warped fluid metaballs, chromatic fringe, and live optical refraction.
- **🎛️ Studio-Grade 32-Bit Float DSP Chain**: 10-band biquad parametric equalizer with tight $\pm0.1\text{ dB}$ precision, linear pass-through soft-knee limiter, zero-allocation XorShift TPDF dither, ISO 226 loudness contour, psychoacoustic harmonic bass generator, and partitioned impulse response convolution reverb.
- **🔀 Dual-ExoPlayer Continuous Crossfade**: Dual isolated audio playback engines providing smooth, gapless transitions with configurable mathematical curves (*Equal Power*, *Linear*, *Logarithmic*) and automatic live-album gapless detection.
- **🌐 System-Wide EQ for All Media Apps**: Dedicated Android foreground service attaching hardware `DynamicsProcessing` to Spotify, YouTube Music, Tidal, and third-party players, with intelligent anti-double-EQ protection.
- **🪟 Four Liquid Glass Home-Screen Widgets**: Hardware offscreen rendered AGSL glass widgets (`RenderNode` + `HardwareRenderer` + `ImageReader`) in 4 responsive sizes, featuring 20 tap-to-seek slices, synced lyrics, and an instant 60 fps translucent scrub sheet.
- **🎤 Synchronized Lyrics & Scrobbling**: Real-time synced `.lrc` lyrics with active line prominence and smooth auto-scrolling. User-provided Last.fm API scrobbling with zero hardcoded credentials.
- **🛡️ 100% Private & Open Source**: Zero ads, zero analytics, zero network requests unless LRCLIB online lyrics is explicitly opted-in. Licensed under Apache 2.0.

---

## 📱 Photo Gallery & Screenshots

*Captured directly from a realme GT 6T (Android 14) running Node Player:*

| Now Playing & Liquid Glass Shader | Interactive Parametric EQ Curve |
| :---: | :---: |
| <img src="docs/screenshots/screen_now_playing.png" width="380" alt="Now Playing Screen" /> | <img src="docs/screenshots/screen_equalizer.png" width="380" alt="Equalizer Screen" /> |
| *Real-time AGSL fluid background, synced lyrics, vinyl art, and audio visualizer* | *Draggable touch nodes, live spectrum analyzer, and impulse response convolver* |

| Liquid Glass Home-Screen Widgets | Frosted Library & Search |
| :---: | :---: |
| <img src="docs/screenshots/screen_widgets.png" width="380" alt="Home Screen Widgets" /> | <img src="docs/screenshots/screen_library.png" width="380" alt="Library Screen" /> |
| *All 4 responsive widget variants live on home screen with offscreen AGSL glass* | *Frosted floating navigation bar, search, folder scanning, and album cards* |

<div align="center">

| Deep Audio & System-Wide EQ Settings |
| :---: |
| <img src="docs/screenshots/screen_settings.png" width="400" alt="Settings Screen" /> |
| *Crossfade curves, system-wide audio interceptor, Last.fm credentials, and widget tuning* |

</div>

---

## 🧩 Modular Architecture

The repository is organized into distinct, clean-architecture Kotlin modules with high code reuse and JVM unit testability:

```
node-player/
├── :core-model       # 100% Pure Kotlin data structures (Track, Album, Lyrics, AudioConfig, WidgetConfig)
├── :core-dsp         # 100% Pure Kotlin 32-bit float audio DSP engine (Biquad EQ, Limiter, Dither, Reverb)
├── :core-library     # Parsers for .lrc lyrics, .cue sheets, .m3u playlists, and AutoEQ profiles
├── :app-android      # Android 14 application (Jetpack Compose, Media3 ExoPlayer, AGSL, Room, Widgets)
└── docs/             # Architecture documentation, releasing guides, and hardware screenshots
```

| Module | Target | Responsibilities | Key Dependencies |
| :--- | :--- | :--- | :--- |
| **`:core-model`** | Multiplatform / Desktop / JVM | Shared data domain entities, audio output state, glass theme tokens, and crossfade models. | Pure Kotlin standard library |
| **`:core-dsp`** | JVM / Pure Kotlin | High-precision 32-bit floating point audio filters, biquad equations, dither algorithms, and unit tests. | Pure Kotlin, JUnit 4 |
| **`:core-library`** | JVM / Pure Kotlin | File parsers for synchronized lyrics (`.lrc`), playlist formats, and AutoEQ parametric curve text files. | Pure Kotlin |
| **`:app-android`** | Android 14 (API 26–34) | UI, MediaSessionService, Room database, offscreen AGSL shader pipeline, and broadcast widget actions. | Jetpack Compose, Media3, Haze, Coil, Room, Palette |

---

## 🎛️ High-Precision 32-Bit Float DSP Suite

Node Player routes all audio through an uncompromised 32-bit floating-point DSP pipeline:

```
[Decoded PCM] ──► [Preamp Gain] ──► [Parametric Biquad EQ] ──► [ISO 226 Loudness]
                                                                        │
[Dithered Output] ◄── [TPDF Dither] ◄── [Soft-Knee Limiter] ◄── [Harmonic Bass / Convolver]
```

### 1. 10-Band Parametric Biquad Equalizer
* Peaking and shelving biquad filters calculated with bilinear transforms.
* Verified against reference curves within a tight $\pm0.1\text{ dB}$ margin across the entire audible spectrum ($20\text{ Hz} - 20\text{ kHz}$).
* Interactive draggable touch nodes on a live glass frequency graph.

### 2. Linear Pass-Through Soft-Knee Limiter
* Pure linear pass-through ($f(x) = x$) below $-0.9\text{ dBFS}$ threshold ($0.90$).
* Smooth-knee rational compression curve above $0.90$ prevents inter-sample clipping and digital harshness without altering dynamic punch.
* Flat-EQ state is 100% bit-exact bypass.

### 3. Direct Volume Control & TPDF Dither
* 32-bit internal digital attenuation with zero-allocation XorShift triangular probability density function (TPDF) dither.
* Preserves low-level detail and dynamic range when listening at quiet volumes.

### 4. ISO 226 Dynamic Loudness Compensation
* Real-time equal-loudness contour that adapts low and high frequency curves dynamically as master volume decreases, preventing thin sound at low volumes.

### 5. Psychoacoustic Harmonic Bass Synthesizer
* Generates 2nd and 3rd harmonic overtones from sub-bass frequencies with an adjustable high-pass cutoff, giving small smartphone speakers rich bass presence.

### 6. Partitioned Block Convolver (Reverb)
* Real-time impulse response convolution using mirrored contiguous history buffers for sub-real-time CPU load and zero branch overhead.
* Built-in acoustic room, headphone diffuse-field, and warm ambience impulse responses.

---

## 🪟 Liquid Glass Home Screen Widgets

Home screen widgets are RemoteViews and cannot execute continuous 60 fps shaders directly. Node Player solves this by using an **offscreen hardware rendering pipeline**:

```
[Playback / Track Event] ──► [RenderNode + HardwareRenderer] ──► [ImageReader] ──► [Downsampled Bitmap] ──► [RemoteViews]
```

### Widget Pipeline Details
* **Offscreen AGSL Shaders (`WidgetShaderRenderer`)**:
  - Evaluates an AGSL `RuntimeShader` with domain-warped noise metaballs, specular rim lighting, dual-tone chromatic fringe (warm amber vs cool cyan), and frosted noise grain off the main thread.
  - Renders directly into a hardware bitmap using Android 13+ `RenderNode` and `HardwareRenderer`.
  - Resolution is dynamically downsampled and cached to keep memory payload under Android's strict AppWidget IPC Binder limit (~1.5 MB).
* **Palette Accent Adaptation**:
  - Automatically extracts vibrant and muted tones from the currently playing album artwork using Android's `Palette` API, falling back to deep smoked slate.

### Four Responsive Variants
1. **Card (4x2)**: Full playback card with album artwork, title, artist, waveform glyph, elapsed `Chronometer`, total duration, 20-zone tap-to-seek progress bar, -10s, previous, glass play/pause, next, and +30s.
2. **Lyrics Card (4x3)**: Synchronized lyrics card showing the active lyric line prominently, faded previous and upcoming lines, and compact controls.
3. **Slim (4x1)**: Compact horizontal bar with artwork, track metadata, previous, play/pause, next, and thin progress bar.
4. **Square (2x2)**: Large cover art tile with centered frosted glass play/pause circle and track typography.

### Timeline Seeking (Tap-to-Seek & Scrub Sheet)
* **20 Tap-to-Seek Zones**: 20 invisible overlay buttons across the widget progress bar trigger instant seek commands to that exact fraction of the track.
* **Live Scrub Sheet (`ScrubSheetActivity`)**: Tapping the time stamps opens an instant translucent overlay activity directly above the home launcher. Features a live 60 fps AGSL shader, continuous draggable seekbar, time readout, and haptic ticks while scrubbing.
* **Reliable Remote Actions (`WidgetActionReceiver`)**: All controls route via broadcast intents, completely eliminating Android 12+ `ForegroundServiceStartNotAllowedException` crashes when backgrounded.

---

## ⚙️ Comprehensive Settings Reference

### 1. Crossfade & Gapless Playback
- **Crossfade Length**: Configurable slider (1 to 12 seconds).
- **Curve Types**:
  - `Equal Power` (Recommended): Conserves acoustic energy ($V_{out}^2 + V_{in}^2 = 1.0$) preventing mid-fade dips.
  - `Linear`: Direct algebraic gain ramp.
  - `Logarithmic`: Smoothstep curve for punchier transitions.
- **Auto-Gapless**: Automatically suppresses crossfade between consecutive album tracks or live concert recordings.

### 2. System-Wide Equalizer
- Intercepts audio session IDs (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`) from third-party players (Spotify, YouTube Music, Tidal, etc.) and attaches hardware `DynamicsProcessing`.
- **Anti-Double-EQ Protection**: Node Player automatically disables its internal DSP equalizer while system-wide EQ is active to avoid double filtering.

### 3. Display & Performance Mode
- **Real Glass Dock Refraction**: AGSL lens distortion and chromatic aberration on the floating navigation dock.
- **Performance Mode**: One-tap toggle that turns off the AGSL refraction shader, animated liquid background, and real-time spectrum, lowering GPU memory by 58% and frame times by 31%.
- **Widget Customization**: Adjust glass opacity (40% to 95%), corner radius (16 dp to 36 dp), and performance fallback mode.

### 4. Privacy & Last.fm
- **Strictly Opt-in Lyrics**: Zero internet requests made unless LRCLIB online lyrics is explicitly toggled on.
- **User-Provided Last.fm Credentials**: Enter your own Last.fm API Key and Secret directly in Settings. No keys are hardcoded or bundled in the app.

---

## 🚀 Modern GitHub Deployments & CI/CD

Node Player features automated **GitHub Actions CI/CD Workflows**:

1. **Continuous Integration ([`.github/workflows/ci.yml`](.github/workflows/ci.yml))**:
   - Triggers on every push or pull request to `main`.
   - Runs all `:core-dsp:test` unit tests on `ubuntu-latest` with JDK 17.
   - Builds and uploads the debug APK artifact.
2. **Automated Production Releases ([`.github/workflows/release.yml`](.github/workflows/release.yml))**:
   - Pushing any version tag (e.g., `v0.1.0`) triggers a release build.
   - Signs the APK with production credentials from GitHub Secrets (or fallback signing).
   - Publishes a GitHub Release with auto-generated release notes and attached release APK.

---

## 🛠️ Building and Installing

### Prerequisites
* **JDK 17** (Temurin, Zulu, or OpenJDK 17)
* **Android SDK API 34** (configured via `local.properties` or `ANDROID_HOME`)

### Fast Command Line Install
```powershell
# Using the PowerShell script
.\scripts\build_and_install.ps1

# Or using the Windows Batch script
.\scripts\build_and_install.bat
```

### Manual Gradle Commands
```powershell
# Run DSP Unit Tests
.\gradlew.bat :core-dsp:test

# Build Debug APK
.\gradlew.bat assembleDebug

# Build Release APK
.\gradlew.bat assembleRelease

# Install on target device via ADB
adb install -r app-android\build\outputs\apk\release\app-android-release.apk
```

---

## 📄 License & Open-Source Acknowledgments

* **Node Player** is open-source software licensed under the [Apache License 2.0](LICENSE).
* **AndroidX Media3 (ExoPlayer)** — Apache-2.0 (Google LLC)
* **Jetpack Compose & Material 3** — Apache-2.0 (Google LLC)
* **Haze Glassmorphism** — Apache-2.0 (Chris Banes)
* **Coil Image Loading** — Apache-2.0 (Colin White)
* **AndroidX Room Database** — Apache-2.0 (Google LLC)
* **AndroidX Palette** — Apache-2.0 (Google LLC)
* **Kotlin & Coroutines** — Apache-2.0 (JetBrains s.r.o.)
