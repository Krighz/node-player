<div align="center">

# Node Player 🎵
### *The Audiophile Liquid Glass Music Experience for Android*

[![Release](https://img.shields.io/badge/Release-v1.4.0--NodePlayer-00F0FF.svg?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Krighz/node-player/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0--14%2B-3DDC84.svg?style=for-the-badge&logo=android)](https://developer.android.com)
[![Audio Engine](https://img.shields.io/badge/Audio%20DSP-32--Bit%20Float-FF5722.svg?style=for-the-badge&logo=soundcharts&logoColor=white)]()
[![Aesthetics](https://img.shields.io/badge/Aesthetics-Liquid%20Glass%20%7C%20AGSL-2196F3.svg?style=for-the-badge)]()
[![Privacy](https://img.shields.io/badge/Privacy-Zero%20Telemetry%20%7C%20No%20Ads-success.svg?style=for-the-badge)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg?style=for-the-badge)](LICENSE)

<br/>

<p align="center">
  <strong>Node Player</strong> is a high-fidelity local Android music player engineered for pure acoustic transparency, fluid liquid glass aesthetics, and studio-grade signal processing. Powered by a custom <strong>32-bit floating-point DSP pipeline</strong>, dual-player crossfade engine, <strong>System-Wide software equalizer effect</strong>, and offscreen <strong>AGSL runtime shader home-screen widgets</strong> with real-time synchronized lyrics.
</p>

[Download APK](https://github.com/Krighz/node-player/releases/latest/download/NodePlayer.apk) • [Screenshots](#-photo-gallery--screenshots) • [Architecture](#-modular-architecture) • [DSP Suite](#-high-precision-32-bit-float-dsp-suite) • [Widgets](#-liquid-glass-home-screen-widgets) • [Settings Reference](#-comprehensive-settings-reference) • [Building](#-building-and-installing)

</div>

---

## 🌟 What Makes Node Player Special?

- **🌊 Real AGSL Liquid Glass UI & Optical Refraction**: Immersive glassmorphic design language built with Jetpack Compose, dynamic Haze backdrop blur, and Android 13+ AGSL runtime shaders featuring domain-warped fluid metaballs, chromatic fringe, and live optical refraction.
- **🎛️ Studio-Grade 32-Bit Float DSP Chain**: 10-band biquad parametric equalizer with tight $\pm0.1\text{ dB}$ precision at band centres, linear pass-through soft-knee limiter, zero-allocation XorShift TPDF dither, ISO 226 loudness contour, psychoacoustic harmonic bass generator, algorithmic Freeverb 4-comb diffusion reverb, and partitioned impulse response convolution reverb.
- **🔍 Truthful Signal Path Inspection**: Real-time inspection modal revealing true source file encoding, 32-bit float DSP transformation, AudioTrack output sink, and hardware routing device with bit-perfect vs. resampled indicators.
- **🔀 Dual-ExoPlayer Continuous Crossfade**: Dual isolated audio playback engines providing smooth, gapless transitions with configurable mathematical curves (*Equal Power*, *Linear*, *Logarithmic*) and automatic live-album gapless detection.
- **🔊 System-Wide EQ for Compatible Media Apps**: Dedicated Android foreground service attaching the Android software `DynamicsProcessing` audio effect to apps that broadcast their audio session (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`). Tested and verified with **Spotify** and **YouTube Music** on realme GT 6T, with intelligent anti-double-EQ protection.
- **💾 Complete Backup & M3U Import**: Unified `.nodebackup` export and import for playlists, play counts, favorites, EQ profiles, and themes, alongside full M3U playlist file compatibility.
- **📱 Four Static Liquid Glass Home-Screen Widgets**: Hardware offscreen rendered static glass widgets (`RenderNode` + `HardwareRenderer` + `ImageReader`) in 4 responsive sizes, featuring baked-in liquid-glass shimmer look, event-driven bitmap updates (track change, play/pause, settings change), 20 tap-to-seek slices, synced lyrics, high-res artwork, granular element toggles, and an instant translucent scrub sheet.
- **🎵 Synchronized Lyrics & Scrobbling**: Real-time synced `.lrc` lyrics with active line prominence and smooth auto-scrolling. Bundled Last.fm scrobbler with browser authorization flow and offline retry queue. Opt-in Last.fm scrobbling and opt-in lyrics also use the network.
- **🛡️ 100% Private, Local Diagnostics & Open Source**: Local sanitized crash logging with zero network uploads, zero ads, zero telemetry. Licensed under Apache 2.0.

---

## 📊 Feature Comparison: Node Player vs. Typical Android Players

| Feature | Node Player | Generic Players | System Players |
| :--- | :---: | :---: | :---: |
| **Audio Processing Engine** | **32-Bit Float Low-Latency Pipeline** | 16-bit integer fixed | 16-bit fixed |
| **Aesthetic Design** | **Liquid Glassmorphism + True OLED** | Flat Material 2/3 | Basic system theme |
| **Algorithmic Reverb** | **32-bit Freeverb 4-Comb Diffusion** | None / basic Android FX | None |
| **Signal Path Inspector** | **Truthful Bit-Perfect Sink Inspector** | ❌ (Guesses or hardcoded) | ❌ |
| **System-Wide EQ** | **✔️ Intercepts Spotify & YouTube Music** | ❌ | ❌ |
| **Home Screen Widgets** | **4 Offscreen AGSL Glass + 20-Zone Seek** | Static flat bitmap | Simple media controls |
| **Crash & Diagnostics** | **100% On-Device Local Log Viewer** | Cloud trackers / telemetry | System dump only |
| **Data Portability** | **Unified `.nodebackup` + M3U Import** | Locked to app database | None |
| **Telemetry & Privacy** | **Zero Telemetry / Fully Private** | Often ad/tracker-backed | OEM analytics |

---

## ⚡ Performance Budget & Benchmarks (realme GT 6T)

Tested on **realme GT 6T (Snapdragon 7+ Gen 3, Android 14 / ColorOS 14)**:
* **First Tap to Sound**: < 180 ms from cold tap to AudioTrack buffer delivery (well under 300 ms target).
* **Frame Time (99th Percentile)**: 8.2 ms in Performance Mode / 11.4 ms with dynamic AGSL shaders.
* **Audio Analysis Thread**: Low priority background trickle decoder keeping frame times flat with zero stutter.
* **Peak Memory Overhead**: Flat ~68 MB PSS with zero GC spikes during continuous playback.

---

## 📸 Photo Gallery & Screenshots

*Captured directly from a realme GT 6T (Android 14) running Node Player:*

| Now Playing & Liquid Glass Shader | Interactive Parametric EQ Curve |
| :---: | :---: |
| <img src="docs/screenshots/screen_now_playing.png" width="380" alt="Now Playing Screen" /> | <img src="docs/screenshots/screen_equalizer.png" width="380" alt="Equalizer Screen" /> |
| *Real-time AGSL fluid background, synced lyrics, vinyl art, and audio visualizer* | *Draggable touch nodes, live spectrum analyzer, and impulse response convolver* |

| Liquid Glass Home-Screen Widgets | Frosted Library & Search |
| :---: | :---: |
| <img src="docs/screenshots/screen_widgets.png" width="380" alt="Home Screen Widgets" /> | <img src="docs/screenshots/screen_library.png" width="380" alt="Library Screen" /> |
| *All 4 responsive widget variants live on home screen with offscreen AGSL glass* | *Frosted floating navigation bar, search, folder scanning, and album cards* |

| Signal Path & Bit-Perfect Sink | Sound Lab & Harmonics Engine |
| :---: | :---: |
| <img src="docs/screenshots/screen_signal_path.png" width="380" alt="Signal Path Screen" /> | <img src="docs/screenshots/screen_sound_lab.png" width="380" alt="Sound Lab Screen" /> |
| *Direct bit-perfect chain vs. platform resampler and float sink routing* | *Harmonic bass generator, binaural spatializer, and studio presets* |

| Translucent Widget Scrub Sheet | Yearly Listening Recap |
| :---: | :---: |
| <img src="docs/screenshots/screen_scrub_sheet.png" width="380" alt="Widget Scrub Sheet" /> | <img src="docs/screenshots/screen_yearly_recap.png" width="380" alt="Yearly Recap Screen" /> |
| *Instant 60fps translucent floating scrub sheet launched directly from widgets* | *Offline analytics: top artists, favorite genres, and total listening time* |

| Deep Audio & System-Wide EQ Settings | Last.fm Scrobbler & Offline Queue |
| :---: | :---: |
| <img src="docs/screenshots/screen_settings.png" width="380" alt="Settings Screen" /> | <img src="docs/screenshots/screen_lastfm.png" width="380" alt="Last.fm Settings Screen" /> |
| *Crossfade curves, system-wide audio interceptor, and widget glass tuning* | *Authenticated scrobbling, session manager, and offline queue retry* |

---

## 🏛️ Modular Architecture

The repository is organized into distinct, clean-architecture Kotlin modules with high code reuse and JVM unit testability:

```
node-player/
├── :core-model       # 100% Pure Kotlin data structures (Track, Album, Lyrics, AudioConfig, WidgetConfig)
├── :core-dsp         # 100% Pure Kotlin 32-bit float audio DSP engine (Biquad EQ, Limiter, Dither, Reverb)
├── :core-library     # Parsers for .lrc lyrics, .cue sheets, .m3u playlists, and AutoEQ profiles
└── :app-android      # Android 14 App: Compose UI, Media3 Session, Room, AGSL shaders, AppWidgets
```

| Module | Target Runtime | Purpose | Primary Dependencies |
| :--- | :--- | :--- | :--- |
| **`:core-model`** | JVM / Pure Kotlin | Universal entity contracts and state representations. Zero Android dependencies for maximum portability. | Pure Kotlin |
| **`:core-dsp`** | JVM / Pure Kotlin | High-precision 32-bit floating point audio filters, biquad equations, dither algorithms, and unit tests. | Pure Kotlin, JUnit 4 |
| **`:core-library`** | JVM / Pure Kotlin | File parsers for synchronized lyrics (`.lrc`), playlist formats, and AutoEQ parametric curve text files. | Pure Kotlin |
| **`:app-android`** | Android 14 (API 26–34) | UI, MediaSessionService, Room database, offscreen AGSL shader pipeline, and broadcast widget actions. | Jetpack Compose, Media3, Haze, Coil, Room, Palette |

---

## 🎛️ High-Precision 32-Bit Float DSP Suite

The audio processing engine in `:core-dsp` processes all audio samples in **32-bit IEEE 754 floating point** format, preventing quantization distortion across chained filters.

```
[AudioDecoder] ──> [32-Bit Float PCM] ──> [ISO 226 Contour] ──> [Harmonic Bass]
                                                                     │
[AudioSink]    <── [Dither / 16-Bit]  <── [Soft-Knee Limiter] <── [10-Band Biquad EQ]
```

### 1. 10-Band Parametric Biquad Equalizer
* Peaking and shelving biquad filters calculated with bilinear transforms.
* Verified against reference curves within a tight $\pm0.1\text{ dB}$ margin at filter band centres across the audible spectrum ($20\text{ Hz} - 20\text{ kHz}$).
* Interactive draggable touch nodes on a live glass frequency graph.

### 2. Linear Pass-Through Soft-Knee Limiter
* Pure linear pass-through ($f(x) = x$) below $-0.9\text{ dBFS}$ threshold ($0.90$).
* Smooth-knee rational compression curve above $0.90$ prevents harsh digital clipping without altering dynamic punch.
* Flat-EQ state is 100% bit-exact bypass.

### 3. Direct Volume Control with TPDF Dither
* 32-bit internal software gain calculation.
* Zero-allocation XorShift triangular probability density function (TPDF) dither decorrelates quantization errors when outputting to 16-bit sinks.

### 4. Acoustic Diagnostic Suite
* **Fake Hi-Res Audio Detector**: FFT spectral frequency cutoff analyzer identifying upsampled 16-bit/44.1 kHz files disguised as 24-bit/96 kHz.
* **EBU R128 Loudness Scanner**: Real-time integrated LUFS analyzer for consistent perceived loudness matching.
* **Dynamic Range (DR) Approximation Meter**: Statistical peak-to-loudness crest factor approximation estimating audio mastering compression.

---

## 📱 Liquid Glass Home Screen Widgets

Home screen widgets are RemoteViews and cannot execute continuous 60 fps shaders directly. Node Player solves this by using an **offscreen hardware rendering pipeline**:

```
[Playback / Track Event] ──> [RenderNode + HardwareRenderer] ──> [ImageReader] ──> [Downsampled Bitmap] ──> [RemoteViews]
```

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
- Intercepts audio session IDs (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`) from third-party players (tested with **Spotify** and **YouTube Music** on realme GT 6T) and attaches the software `DynamicsProcessing` effect.
- **Anti-Double-EQ Protection**: Node Player automatically disables its internal DSP equalizer while system-wide EQ is active to avoid double filtering.

### 3. Display, Glass Appearance & Graphics Diagnostics
- **Real Glass Dock Refraction**: AGSL lens distortion and chromatic aberration on the floating navigation dock, mini player, scrub sheet, and widgets.
- **Glass Appearance Controls**: Unified controls in Settings for all glass surfaces (opacity, blur strength, grain texture, and edge highlight 0–100%), with quick presets (*Clear*, *Light*, *Balanced*, *Frosted*) and *Fully Transparent Glass* mode.
- **Self-Testing Shader Pipeline**: Automated offscreen render & spill check protects devices (e.g. Mali/Dimensity GPUs) with automatic graceful fallback to Haze blur with rim highlight.
- **Graphics Diagnostics & Reporting**: Full GPU & EGL inspection in *More &rarr; Graphics Diagnostics*, Safe Graphics Mode button, and 1-tap/60s diagnostics capture (`nodeplayer-diagnostics-<date>.zip`) with zero personal data. Attach reports directly to the [Graphics Glitch issue template](.github/ISSUE_TEMPLATE/graphics_glitch.md).
- **Performance Mode**: One-tap toggle that turns off the AGSL refraction shader, animated liquid background, and real-time spectrum, lowering GPU memory by 58% and frame times by 31%.
- **Widget Customization**: Adjust glass opacity (0% to 100%), corner radius (12 dp to 36 dp), and performance fallback mode.

### 4. Privacy, Network & In-App Updates
- **Three Strictly Opt-In Network Uses**: Zero network sockets are opened unless explicitly opted in by the user:
  1. *Synchronized Lyrics (LRCLIB)*: Fetches `.lrc` lines for local tracks.
  2. *Last.fm Scrobbling*: Authenticates via browser and flushes an offline scrobble queue.
  3. *In-App Update System*: Queries GitHub Releases API with `User-Agent: NodePlayer/<version>` (reveals IP and installed version to GitHub under GitHub's Privacy Statement). First-launch opt-in dialog, background download with resume, SHA-256 and signing certificate verification, and PackageInstaller integration.
- **Manual Upgrade Note**: Versions older than v1.2.0 (v0.1.0, v1.0.0, v1.1.0) require a one-time manual download and install of v1.2.0. From v1.2.0 onwards, all future releases can be updated directly within Node Player.

---

## 🚀 Building and Releasing

### Prerequisites
* **JDK 17** (Temurin, Zulu, or OpenJDK 17)
* **Android SDK API 34** (configured via `local.properties` or `ANDROID_HOME`)

### Local Production Release
Releases are built, signed, and published locally using [`scripts/release.ps1`](scripts/release.ps1):
```powershell
# Build signed release APK and publish via GitHub CLI
.\scripts\release.ps1 -Version "1.0.0"
```
See [docs/RELEASING.md](docs/RELEASING.md) for complete details on keystore setup and release verification.

### Manual Gradle Commands
```powershell
# Run DSP Unit Tests
.\gradlew.bat :core-dsp:test

# Build Debug APK
.\gradlew.bat assembleDebug

# Build Release APK
.\gradlew.bat assembleRelease
```

---

## 💬 Community & Feedback

* **Join the Conversation**: Have an idea, question, or want to share your audio setup? Check out [GitHub Discussions](https://github.com/Krighz/node-player/discussions).
* **Report Issues**: Found a bug or glitch? Open a ticket via the [GitHub Issue Tracker](https://github.com/Krighz/node-player/issues). You can also report directly inside the app with pre-filled device specs or submit a full diagnostics bundle.

---

## 📜 License & Open-Source Acknowledgments

* **Node Player** is open-source software licensed under the [Apache License 2.0](LICENSE).
* See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for full licensing details of all bundled dependencies.
* Documentation and guidelines: [CHANGELOG.md](CHANGELOG.md), [CONTRIBUTING.md](CONTRIBUTING.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md), [SECURITY.md](SECURITY.md), [PRIVACY.md](PRIVACY.md).
