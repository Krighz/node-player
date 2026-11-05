# Node Player: Comprehensive Evolution & Version History (v1.0.0 – v1.5.0)

**Document Classification:** Technical Architecture & Release Changelog  
**First Release (v1.0.0):** 2026-11-02  
**Current Milestone (v1.5.0):** 2026-11-03  
**Platforms Supported:** Windows 10/11 (x64) & Android 8.0–15+ (Universal)  
**License:** Apache 2.0  

---

## Table of Contents
1. [Architectural Overview & Core Design Philosophy](#1-architectural-overview--core-design-philosophy)
2. [Module Hierarchy & Ecosystem Map](#2-module-hierarchy--ecosystem-map)
3. [Chronological Evolution: Version by Version](#3-chronological-evolution-version-by-version)
   - [v0.1.0 — Initial Prototype & Architecture Proof of Concept](#v010--initial-prototype--architecture-proof-of-concept-2026-11-01)
   - [v1.0.0 — Production Audio DSP & Modular Android Player](#v100--production-audio-dsp--modular-android-player-2026-11-02)
   - [v1.1.0 — AGSL Shader Hardening, Diagnostics Dump & Mali GPU Compatibility](#v110--agsl-shader-hardening-diagnostics-dump--mali-gpu-compatibility-2026-11-02)
   - [v1.2.0 — Secure In-App Update Engine & Privacy Guard](#v120--secure-in-app-update-engine--privacy-guard-2026-11-02)
   - [v1.3.0 — Liquid-Glass Lens Dock Overhaul, 4 Static Widgets & Customization Hub](#v130--liquid-glass-lens-dock-overhaul-4-static-widgets--customization-hub-2026-11-03)
   - [v1.3.1 — Crystal Specular Refraction Polish, High-DPI Widgets & Offline Guard](#v131--crystal-specular-refraction-polish-high-dpi-widgets--offline-guard-2026-11-03)
   - [v1.4.0 — Architecture Boundary Enforcement & Ecosystem Integration API](#v140--architecture-boundary-enforcement--ecosystem-integration-api-2026-11-03)
   - [v1.5.0 — Compose Multiplatform Desktop, WASAPI, Zero Estimation & 120 FPS Android](#v150--compose-multiplatform-desktop-wasapi-zero-estimation--120-fps-android-2026-11-03)
4. [Deep Dive: The 32-Bit Floating Point DSP Pipeline](#4-deep-dive-the-32-bit-floating-point-dsp-pipeline)
5. [Deep Dive: Exact Audio Metadata Header Parsing (Zero Estimation)](#5-deep-dive-exact-audio-metadata-header-parsing-zero-estimation)
6. [Deep Dive: Liquid Glass Graphics Architecture (AGSL & Skia)](#6-deep-dive-liquid-glass-graphics-architecture-agsl--skia)
7. [Cryptographic Manifest & Binary Verification (v1.5.0)](#7-cryptographic-manifest--binary-verification-v150)

---

## 1. Architectural Overview & Core Design Philosophy

Node Player was born from a singular vision: to bridge the gap between uncompromising studio-grade acoustic transparency and fluid, organic liquid-glass aesthetics. Typical consumer music players subject audio to fixed 16-bit integer transformations, platform resamplers, and flat Material design surfaces. Node Player was designed from the ground up around four non-negotiable principles:

1. **Acoustic Truthfulness**: Audio is processed strictly in 32-bit IEEE 754 floating point format. Signal paths are transparently inspected down to hardware routing sinks. No arbitrary frequency caps, no dynamic range degradation, and zero guesswork on track durations or bitrates.
2. **True Liquid Glassmorphism**: Physical optical refraction, Snell's law domain-warped caustics, and pearl specular rim lighting rendered via native AGSL (Android Graphics Shading Language) on Android and Skia shaders on Windows Desktop.
3. **Strict Privacy & No Network Use Unless You Opt In**: Zero analytics SDKs, zero telemetry, zero advertising, and zero unauthorized background sockets. Node Player operates strictly offline by default; all network use is strictly opt-in and local-first (GitHub Releases update check, Last.fm scrobbling, synchronized lyrics via LRCLIB, and peer-to-peer Node Connect on your local Wi-Fi/LAN only). Diagnostics are strictly on-device, sanitized, and exportable solely at user discretion.
4. **Platform-Optimized Multiplatform**: Unified Kotlin codebase sharing core models, DSP algorithms, and Compose Multiplatform UI, while completely decoupling performance-critical platform primitives (WASAPI on Windows, MediaCodec / MediaSessionService on Android).

---

## 2. Module Hierarchy & Ecosystem Map

```
node-player/
├── :core-model           # 100% Pure Kotlin data contracts (Track, Album, Lyrics, AudioConfig, ThemeConfig)
├── :core-dsp             # Pure Kotlin 32-bit float DSP suite (Biquads, Limiter, TPDF Dither, Freeverb, Convolver)
├── :core-library         # Exact binary header parsers (FLAC, MP3, WAV, M4A, OGG), .lrc, .cue, .m3u
├── :core-common          # Central logging (NodeLog ring buffer), dispatchers, feature API contracts
├── :core-ui              # Compose Multiplatform shared UI, Skia liquid glass, adaptive layout, themes
├── :integration-api      # Versioned IPC protocol contracts (Android TV companion & external controllers)
├── :app-desktop          # Windows 10/11 shell: Skia UI, WASAPI low-latency output, JLayer/Vorbis/Flac4J
└── :app-android          # Android 14/15 shell: MediaSessionService, Room, AGSL shaders, RemoteViews widgets
```

---

## 3. Chronological Evolution: Version by Version

### v0.1.0 — Initial Prototype & Architecture Proof of Concept (2026-11-01)

The inaugural baseline that proved the core principles of Node Player:
* **Modular Multiplatform Foundation**: Initial separation of pure Kotlin modules (`:core-model`, `:core-dsp`, `:core-library`) and the Android host app (`:app-android`).
* **32-Bit Floating Point DSP Proof**: First implementation of 10-band biquad parametric EQ equations with bilinear transforms, linear pass-through soft-knee limiter, ISO 226 loudness contour, and zero-allocation TPDF dither.
* **Dual-Player Crossfade Engine**: Initial prototype of dual ExoPlayer instances providing smooth transitions with equal power, linear, and logarithmic curves.
* **System-Wide EQ Interception**: Initial foreground service attaching Android software `DynamicsProcessing` to third-party audio sessions.
* **Early Liquid Glass Experimentation**: First AGSL runtime shader prototypes on Android 13+, Haze blur integration, and offscreen launcher widget rendering.
* **File Parsers**: Initial pure Kotlin parsers for `.lrc` lyrics, `.m3u` playlists, and AutoEQ headphone profiles.

---

### v1.0.0 — Production Audio DSP & Modular Android Player (2026-11-02)

The baseline production release transitioned Node Player from an experimental prototype into a feature-complete local music player:

* **Pure Kotlin Modular Architecture**: Decoupled the project into `:core-model`, `:core-dsp`, `:core-library`, and `:app-android`.
* **Multi-Stage Artwork Decoder**: Introduced a custom Coil `AudioArtworkFetcher` supporting Android 10+ (API 29+) MediaStore `loadThumbnail`, `MediaMetadataRetriever` embedded APIC/FLAC picture extraction, and content resolver fallbacks.
* **Truthful Signal Path Inspector**: Built an interactive visual pipeline inspection modal detailing source container encoding, 32-bit float DSP transformation, AudioTrack output sink, and hardware routing device with bit-perfect vs. resampled indicators.
* **Sound Lab & Acoustic Diagnostics**:
  - *Fake Hi-Res Audio Detector*: FFT spectral cutoff analyzer detecting 16-bit/44.1 kHz tracks upsampled to 24-bit/96 kHz.
  - *EBU R128 Loudness Scanner*: Integrated LUFS analyzer for consistent perceived loudness matching.
  - *Dynamic Range (DR) Approximation Meter*: Statistical crest-factor calculation estimating mastering compression.
* **Four Responsive Launcher Widgets**: Card (4x2), Lyrics Card (4x3), Slim (4x1), and Square (2x2) featuring hardware offscreen rendering (`RenderNode` + `HardwareRenderer` + `ImageReader`), 20 tap-to-seek zones, and persistent track state.
* **Unified Data Portability**: Introduced `.nodebackup` export and import for playlists, play counts, favorites, and settings, alongside standard M3U playlist import.

---

### v1.1.0 — AGSL Shader Hardening, Diagnostics Dump & Mali GPU Compatibility (2026-11-02)

Focused on GPU compatibility, mathematical robustness of custom shaders, and on-device graphics diagnostics:

* **AGSL Shader Hardening**:
  - Eliminated GPU-specific out-of-bounds artifact spills observed on Mali-G77 and MediaTek Dimensity GPUs.
  - Converted all coordinate systems to normalized floating-point space (`0.0 .. 1.0`).
  - Added division-by-zero guards with epsilon thresholds ($\varepsilon = 1\times 10^{-5}$) and modulo wrapping on time coordinates.
* **Automated Shader Self-Test**:
  - Offscreen rendering self-test on app initialization that checks for boundary transparency spills, non-finite pixel values, and plausible luminance.
  - Automatically activates Safe Graphics Mode (fallback to Haze blur with rim highlight) if any GPU anomaly is detected.
* **Graphics Diagnostics Screen & 60s Record Mode**:
  - Dedicated screen under *More &rarr; Graphics Diagnostics* exposing device SoC, temporary EGL GPU renderer string, display metrics, self-test status, and live side-by-side shader comparisons.
  - One-tap diagnostics export to `nodeplayer-diagnostics-<date>.zip` containing PixelCopy frame capture, rolling 200-event graphics log, and sanitized hardware properties with zero personal data.
* **Unified Glass Appearance Controls**:
  - Added continuous sliders for Opacity, Blur Strength, Grain Texture, and Edge Highlight (0–100%).
  - Added quick presets (*Clear*, *Light*, *Balanced*, *Frosted*) and a *Fully Transparent Glass* mode.

---

### v1.2.0 — Secure In-App Update Engine & Privacy Guard (2026-11-02)

Established a secure, privacy-preserving in-app update pipeline directly from GitHub Releases:

* **Opt-In Update Engine**:
  - Zero network queries on startup unless explicitly opted in by the user during the first-run onboarding dialog.
  - Periodic background update checks via Android `WorkManager` (daily, unmetered network only) and foreground throttling (at most once every 12 hours).
  - Uses `User-Agent: NodePlayer/<version>` against GitHub's public Releases API without cookies or tracking tokens.
* **Non-Blocking Glass Update Banner**:
  - Appears natively on the Home screen when a newer release is discovered, displaying release notes, asset size, and actions (*Update now*, *Later*, *Skip this version*).
* **Cryptographic Download Engine & PackageInstaller**:
  - Resumable HTTPS download stream written to private cache storage.
  - Mandatory SHA-256 hash calculation and verification against official release assets before install handoff.
  - Direct integration with Android `PackageInstaller` utilizing `USER_ACTION_NOT_REQUIRED` on Android 12+ (API 31+).
  - Pre-install package inspection validating package name matching, version strictly greater than current, and cryptographic signing certificate matching.
* **Android 16 Compatibility**: Resolved `UnsupportedOperationException: Tried to obtain display from a Context not associated with one` by querying `DisplayManager` directly.
* **Community & Support**: Added GitHub Discussions links and voluntary UPI support (`7063061981@fam`).

---

### v1.3.0 — Liquid-Glass Lens Dock Overhaul, 4 Static Widgets & Customization Hub (2026-11-03)

A transformative visual overhaul elevating the user experience to match high-end iOS glass aesthetics:

* **Liquid-Glass Lens Dock**:
  - Floating navigation pill featuring a draggable spring-physics magnifying bubble.
  - Ambient specular rim lighting and subtle haptic ticks on tab boundary crossings.
  - Separate floating circular glass search trigger.
* **Procedural Background Ambience Engine**:
  - 6 distinct background animation styles: *Liquid Blobs*, *Aurora*, *Mesh Gradient*, *Album-Art Blur*, *Slow Caustics*, and *Static*.
  - Configurable animation speed ($0.2\times - 3.0\times$), intensity ($0.1 - 1.0$), frame rate limits (Auto, 60, 90, 120 fps), and animate-only-while-playing power gating.
  - 64x64 blue-noise dithering texture preventing color-banding on OLED displays.
* **Static Liquid-Glass Launcher Widgets**:
  - Complete overhaul of all 4 launcher widgets with baked-in liquid-glass shimmer look.
  - Event-driven re-rendering: updates strictly on track change, play/pause, or settings modification to preserve battery.
  - In-app widget configuration preview with granular toggles for artwork, waveform glyph, metadata, seekbar, transport controls, and lyrics.
* **Customize UI Hub**:
  - 6 theme presets (*Obsidian Liquid*, *Frost Pearl*, *Amber Flare*, *Neon Aurora*, *Stealth OLED*, *Minimal Glass*).
  - Font typography selection (*Inter*, *Outfit*, *JetBrains Mono*, *System Default*).
  - Corner roundness presets (*Sharp 8dp*, *Rounded 16dp*, *Pill 24dp*) and spacing density presets (*Compact 10dp*, *Comfortable 16dp*, *Roomy 22dp*).
  - `.nodetheme` JSON export and import for community theme sharing.
* **Spring Physics Motion Tokens**:
  - Unified spring constants (*Snappy*, *Smooth*, *Bouncy*, *Gentle*) and fluid `.pressScale()` click feedback.

---

### v1.3.1 — Crystal Specular Refraction Polish, High-DPI Widgets & Offline Guard (2026-11-03)

Targeted refinements ensuring optical realism and total offline security:

* **Crystal Specular Refraction**:
  - Removed outdated yellow/amber borders, replacing them with pure crystal pearl gradients (`Color.White` + `Color(0xFFBAE6FD)`).
  - Passed `hazeState` directly into `glassSurface` inside the selector bubble, enabling the AGSL shader to sample blurred underlying screen contents for true optical lens magnification ($1.22\times$).
* **Now Playing Bleed & Liquid Lens Button**:
  - Removed opaque root background scrims in Now Playing, allowing fluid background colors to bleed through translucent glass cards.
  - Replaced flat play/pause button with a circular liquid-glass lens bubble with concentric glass rings.
* **High-DPI 2x Widget Rendering**:
  - Added 2x native resolution bitmap generation for 1080p+ and 1440p displays (`1080x560` for 4x2, `1080x760` for 4x3, `1080x280` for 4x1, `560x560` for 2x2).
* **Complete Offline Guard**:
  - Deferred external online sync and disabled remote LRCLIB requests, ensuring pure 100% offline local operation with local `.lrc` file support.

---

### v1.4.0 — Architecture Boundary Enforcement & Ecosystem Integration API (2026-11-03)

Hardened internal module architecture and prepared the foundation for cross-platform desktop and external controllers:

* **Explicit Public Feature Contracts**:
  - Encapsulated internal logic behind clean public interfaces: `WidgetFeatureApi`, `ScrobbleFeatureApi`, `UpdaterFeatureApi`, `DiagnosticsFeatureApi`, `LyricsFeatureApi`.
* **Pure Kotlin Common Module (`:core-common`)**:
  - Central `NodeLog` with feature tags and a thread-safe 500-entry ring buffer.
  - Platform-agnostic `DispatcherProvider` and functional `NodeResult<T>` error monad.
* **Ecosystem Integration Surface (`:integration-api`)**:
  - Pure Kotlin IPC contract with semantic protocol versioning.
  - Immutable state snapshots (`NowPlayingState`, `PlaybackCommand`, `ExternalTrackItem`) establishing cross-process communication for desktop companions and external audio hardware.
* **Automated Architecture Boundary Testing**:
  - Implemented `ArchitectureBoundaryTest` running in CI to enforce that core modules contain zero Android framework imports (`android.*`) and maintain strict package encapsulation.
* **Parallel Staging Build Type**:
  - Added `.staging` application ID suffix allowing production and test builds to coexist side-by-side on real devices without database collisions.

---

### v1.5.0 — Compose Multiplatform Desktop, WASAPI, Zero Estimation & 120 FPS Android (2026-11-03)

The landmark milestone unifying Windows and Android into a universal liquid-glass audiophile player:

* **Universal Compose Multiplatform Architecture (`:core-ui`)**:
  - Created a single shared UI codebase running seamlessly on Android phones (portrait), Android landscape (16:9), and Windows Desktop (16:9).
  - Adaptive master layout (`SharedPlayerLayout`) managing navigation, theme tokens, and liquid glass surfaces.
* **Native Windows Desktop Shell (`:app-desktop`)**:
  - Expansive 16:9 desktop workspace with an animated expanding sidebar that collapses to clean glyphs when idle and smoothly glides open on hover.
  - Centerpiece Now Playing screen, responsive folder browsing in Library, and dynamic For You home view refreshed on each launch with mobile disc branding.
  - Complete desktop hotkey matrix (<kbd>Space</kbd> play/pause, <kbd>→</kbd>/<kbd>←</kbd> seek, <kbd>Ctrl+F</kbd> search, <kbd>Ctrl+O</kbd> folder picker, <kbd>M</kbd> mute, <kbd>Esc</kbd> dismiss).
  - Precision timeline scrubber with live mouse wheel seeking ($+5\text{s} / -5\text{s}$).
* **Bit-Perfect WASAPI Audio Output**:
  - Native Windows Audio Session API integration supporting both Shared mode and Exclusive mode with 32-bit float internal mixing and non-blocking streaming.
  - Dynamic sample rate ladder fallback (`targetRate -> 48000 -> 44100`) ensuring compatibility across all DACs and audio interfaces.
  - Zero-glitch circular ring buffering eliminating pops, clicks, and buffer under-runs.
* **Strict Zero-Estimation Audio Metadata Engine**:
  - Completely purged division-based duration estimations and arbitrary hardcoded limits (eliminating the 3-minute clamp).
  - Ground-truth binary header parsing for MP3 (Xing/VBRI/CBR), FLAC (`STREAMINFO`), M4A/AAC (`stts`/`mdhd`), WAV (`fmt`/`data`), and OGG Vorbis (`granule_position`).
* **Android 120 FPS Performance Decoupling**:
  - Isolated desktop Skia file reading from the mobile codebase.
  - Android uses native C++ `MediaMetadataRetriever` with downsampled `RGB_565` memory buffers, eliminating disk I/O stalls during scrolling and keeping display refresh pinned at 120 fps.
* **Clean Sound & Default Aesthetics**:
  - Eliminated desktop background hiss by disabling TPDF dither by default on 24/32-bit outputs.
  - Cashmere Beige default accent with OLED Pure Black cleanly turned off on initial launch.
* **Official Windows Distributables**:
  - Produced official WiX MSI installer (`NodePlayer-1.5.0.msi`), portable standalone ZIP (`NodePlayer-1.5.0-windows-x64.zip`), and standalone executable (`NodePlayer.exe`).

---

## 4. Deep Dive: The 32-Bit Floating Point DSP Pipeline

All audio sample data in Node Player flows through `:core-dsp` as **32-bit IEEE 754 floating point** values (normalized to the range $[-1.0, +1.0]$). This avoids intermediate integer truncation, eliminating the harmonic distortion and dynamic range loss inherent in conventional 16-bit integer mixing:

```
[Audio Decoder / Stream]
         │
         ▼
[32-Bit Float PCM Normalizer]
         │
         ▼
[Fletcher-Munson ISO 226 Loudness Contour]
         │
         ▼
[Psychoacoustic Harmonic Bass Synthesizer]
         │
         ▼
[10-Band Biquad Parametric Equalizer] (Q: 0.5 – 10.0, ±15 dB)
         │
         ▼
[Freeverb 4-Comb Diffusion Reverb & Convolver]
         │
         ▼
[Linear Pass-Through Soft-Knee Limiter] (Threshold: -0.9 dBFS)
         │
         ▼
[Zero-Allocation XorShift TPDF Dither] (Active for 16-bit sinks only)
         │
         ▼
[Platform Output Sink: WASAPI Float / AudioTrack Float]
```

### Key Mathematical Characteristics
1. **10-Band Biquad Equalizer**: Peaking and shelving filters implemented via bilinear transform equations:
   $$H(s) = \frac{s^2 + \frac{\omega_0}{Q} s + \omega_0^2}{s^2 + \frac{\omega_0}{Q_{pole}} s + \omega_0^2} \xrightarrow{\text{Bilinear}} H(z) = \frac{b_0 + b_1 z^{-1} + b_2 z^{-2}}{1 + a_1 z^{-1} + a_2 z^{-2}}$$
   Verified to maintain tight $\pm0.1\text{ dB}$ tolerance at band centres ($31\text{ Hz}, 62\text{ Hz}, 125\text{ Hz}, 250\text{ Hz}, 500\text{ Hz}, 1\text{ kHz}, 2\text{ kHz}, 4\text{ kHz}, 8\text{ kHz}, 16\text{ kHz}$).
2. **Soft-Knee Limiter**: Pure linear pass-through ($f(x) = x$) below $-0.9\text{ dBFS}$ ($0.90$). Above $0.90$, a rational compression curve prevents hard digital clipping while maintaining acoustic punch.
3. **TPDF Dither**: Triangular probability density function dither generated using a zero-allocation XorShift pseudorandom generator, decorrelating quantization distortion when downsampling to 16-bit sinks. Automatically bypassed on 24/32-bit sinks to ensure absolute silence.

---

## 5. Deep Dive: Exact Audio Metadata Header Parsing (Zero Estimation)

Starting in v1.5.0, Node Player implements direct binary header parsing in `com.nodeplayer.library.AudioHeaderParser`. Audio file length and properties are never estimated from file size divided by bitrate.

| Audio Format | Parsing Strategy | Duration Calculation |
| :--- | :--- | :--- |
| **FLAC** | Reads binary `fLaC` stream marker followed by the 34-byte `STREAMINFO` block. | Samples $S$ (36-bit unsigned at byte offset 13–17) and sample rate $R$:<br>$$\text{durationMs} = \frac{S \times 1000}{R}$$ |
| **WAV / PCM** | Parses RIFF container chunks: extracts sample rate and bit depth from `fmt ` chunk; extracts data byte length from `data` chunk. | Byte length $L$ and byte rate $B$:<br>$$\text{durationMs} = \frac{L \times 1000}{B}$$ |
| **MP3 (VBR / CBR)** | Skips ID3v2 tags via syncsafe integer size. Traverses to first MPEG frame sync (`0xFFE0`). Parses `Xing` or `Info` header frame count, or Fraunhofer `VBRI` header frame count. For pure CBR without VBR headers, walks frame headers to compute exact frame count. | Frame count $F$, samples per frame $N$ (typically 1152), and sample rate $R$:<br>$$\text{durationMs} = \frac{F \times N \times 1000}{R}$$ |
| **M4A / ALAC / AAC** | Recursively traverses ISO/IEC 14496-12 box structures down to `moov.trak.mdia.mdhd` or `moov.mvhd`. | Duration units $D$ and timescale $T$:<br>$$\text{durationMs} = \frac{D \times 1000}{T}$$ |
| **OGG Vorbis** | Locates `OggS` page headers. Reads last Ogg page at EOF to capture the final `granule_position`. | Granule position $G$ and sample rate $R$:<br>$$\text{durationMs} = \frac{G \times 1000}{R}$$ |

---

## 6. Deep Dive: Liquid Glass Graphics Architecture (AGSL & Skia)

Node Player achieves fluid, organic glass refraction across both platforms by utilizing the native graphics hardware of each operating system:

### Android AGSL Pipeline
* **Lens Refraction Shader**: Uses Android 13+ AGSL (`RuntimeShader`). Evaluates domain-warped Perlin noise coordinates with Snell's law refraction vectors:
  $$\vec{r} = \eta \vec{i} + \left( \eta (\vec{n} \cdot \vec{i}) - \sqrt{1 - \eta^2 (1 - (\vec{n} \cdot \vec{i})^2)} \right) \vec{n}$$
* **Haze Backdrop Layering**: By passing `hazeState` to `glassSurface` inside the selector bubble, the underlying composited screen buffer is blurred and fed into AGSL as an input texture (`"contents"`), achieving true optical magnification ($1.22\times$) and chromatic fringe.
* **Offscreen Widget Rendering**: RemoteViews cannot execute continuous shaders. Node Player evaluates AGSL offscreen on playback events into hardware bitmaps via `RenderNode` + `HardwareRenderer` + `ImageReader`, caching 2x high-DPI bitmaps within Android's IPC Binder limit.

### Windows Skia Pipeline
* **Compose Multiplatform Canvas**: Uses Skia's hardware-accelerated Direct3D / OpenGL backend on Windows.
* **Organic Liquid Blobs & Caustics**: Procedural mathematical rendering evaluated continuously on Skia canvas, producing fluid metaball merging, specular rim highlights, and ambient light bleeding without requiring mobile-only Android APIs.

---

## 7. Cryptographic Manifest & Binary Verification (v1.5.0)

Every production binary in Node Player v1.5.0 has verified cryptographic checksums.

> [!NOTE]
> **Authenticode & Code Signing Status**:
> - **Android APKs (`NodePlayer.apk`, `NodePlayer-staging.apk`)**: Cryptographically signed using the official release keystore with **APK Signature Scheme v2** (verified with `apksigner`).
> - **Windows Packages (`NodePlayer-1.5.0.msi`, `NodePlayer.exe`)**: Currently **Authenticode-unsigned**. Because they are not signed with a paid Extended Validation (EV) certificate, Windows Defender SmartScreen displays a standard "Unknown Publisher" protection prompt on first launch.
> - **Checksum Hierarchy**: **SHA-256 is the authoritative cryptographic checksum** for verifying download integrity and release authenticity. MD5 checksums are provided strictly as secondary legacy digests.

### Hash Verification Table

| File Name | Platform | File Size (Bytes) | MD5 Checksum | SHA-256 Checksum |
| :--- | :---: | :---: | :--- | :--- |
| `NodePlayer-1.5.0.msi` | Windows | 111,398,965 | `BDAEB03969967806BA19D0B8B4D320AE` | `c2b528988eac69ebe2bb6d00059d74e0e97c42d1c6b93e390bc92bc6c2f4d452` |
| `NodePlayer-1.5.0-windows-x64.zip` | Windows | 110,335,788 | `D3CE26BD8A2635905603D61A2C21C3B4` | `a3b6278ba613406189baf74ec2055acb162f73ed22431beed4fcb296988ed88e` |
| `NodePlayer.exe` | Windows | 457,216 | `7CF23C1A62C2639C589319B0F1A3BD49` | `d4e9c87f685c9f3555db98285420d2b3e78af1bba8a3812042d84f4a0b7363e6` |
| `NodePlayer.apk` | Android | 4,748,439 | `28D4006D8CC37D8FC49CF404C99999B0` | `b5ee393b7f6db7c646160391ecdc6734f836acd663a6d667f7caf6d15dfd31c1` |
| `NodePlayer-staging.apk` | Android | 4,748,459 | `E588B4FA02CC818B8B8B8C82EFC1ACB0` | `a7c790c166617dfcc23c4fd4a52d8b1c78f9e4e83c80ed18056c85dfd2de00de` |

### Android Signature Verification
```
$ apksigner verify --verbose dist/NodePlayer.apk
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v4 scheme (APK Signature Scheme v4): false
Signer #1 certificate DN: CN=NodePlayer, OU=Audio, O=Krighz, L=Global, ST=Earth, C=US
Signer #1 certificate SHA-256 digest: b5ee393b7f6db7c646160391ecdc6734f836acd663a6d667f7caf6d15dfd31c1
```

---

*Document maintained by the Node Player Core Engineering Team. For questions or architecture reviews, consult [docs/README.md](README.md) or open a discussion on GitHub.*
