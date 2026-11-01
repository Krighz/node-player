# Node Player 🎵

A local Android music player built with a Glassmorphism design language, 32-bit floating-point DSP pipeline, System-Wide EQ, Liquid Glass AGSL home screen widgets, and deep audio customisation.

---

## Architecture & Modules

* **`:core-model`** *(100% Pure Kotlin)*:
  Shared data structures across mobile and desktop (`Track`, `Album`, `Playlist`, `Lyrics`, `AudioEffectConfig`, `AudioOutputInfo`, `GlassThemeConfig`, `CrossfadeConfig`, `AutoEqProfile`, `WidgetConfig`).
* **`:core-dsp`** *(100% Pure Kotlin)*:
  High-precision 32-bit float audio DSP engine. Fully tested with JVM unit tests:
  - **Parametric Equalizer**: 10-band biquad peaking/shelf filters with tight $\pm0.1\text{ dB}$ tolerance.
  - **Linear / Knee Limiter**: Strict linear pass-through ($f(x) = x$) below $0.90$ threshold ($-0.9\text{ dBFS}$), with soft-knee rational compression above. Flat-EQ bypass is 100% bit-exact.
  - **Direct Volume Control & TPDF Dither**: 32-bit internal volume with zero-allocation XorShift TPDF dither, preserving audio resolution at low volumes.
  - **ISO 226 Loudness Compensation**: Dynamic equal-loudness contour boosting bass and treble proportionally at quiet listening levels.
  - **Harmonic Bass Synthesizer**: Psychoacoustic missing fundamental generator adding 2nd/3rd harmonics with an optional high-pass cutoff to protect phone speakers.
  - **Partitioned Convolver**: Real-time impulse response convolution with mirrored contiguous history buffers for zero branch overhead and sub-real-time CPU load.
  - **A/B Loudness Matcher**: Pink-noise weighted gain normalization to compare EQ ON vs OFF at equal perceived loudness.
* **`:core-library`** *(100% Pure Kotlin)*:
  Parsers for `.lrc` synced lyrics, `.cue` sheets, `.m3u` playlists, and AutoEQ headphone correction text profiles.
* **`:app-android`**:
  Android 14 application module:
  - **Jetpack Compose & AGSL**: Glassmorphism with Haze backdrop blur and Android 13+ AGSL optical lens refraction shader on the floating dock.
  - **Dual-ExoPlayer Crossfade**: Seamless track crossfading with isolated DSP chains (`Linear`, `Equal Power`, `Logarithmic`) and automatic gapless detection.
  - **System-Wide EQ**: Android foreground service attaching hardware `DynamicsProcessing` to other media apps broadcasting session IDs, with anti-double-EQ bypass.
  - **Liquid Glass Home Screen Widgets**: Hardware offscreen rendered AGSL glass widgets in 4 responsive variants.

---

## Liquid Glass Home Screen Widgets

Home screen widgets are RemoteViews and cannot execute continuous 60 fps shaders directly. Node Player solves this by using an offscreen hardware rendering pipeline:

* **Offscreen AGSL Pipeline (`WidgetShaderRenderer`)**:
  - Uses `RenderNode` + `HardwareRenderer` + `ImageReader` (Android 13+ / API 33+) to evaluate the AGSL shader offscreen into a hardware bitmap.
  - Fluid metaballs, specular rim lighting, dual-tone chromatic aberration (amber vs cyan), and light noise grain are baked into the bitmap on state transitions (track change, play/pause, theme change).
  - Bitmaps are conservatively sized and downsampled to stay safely below the Android AppWidget IPC Binder memory limit (~1.5 MB).
* **Four Responsive Variants**:
  1. **Card (4x2)**: Full playback card with album artwork, title, artist, waveform glyph, elapsed Chronometer, total duration, 20-zone tap-to-seek progress bar, -10s, previous, glass play/pause, next, and +30s.
  2. **Lyrics Card (4x3)**: Synchronized lyrics card showing the active lyric line prominently, faded previous and upcoming lines, and compact controls.
  3. **Slim (4x1)**: Compact horizontal bar with artwork, track metadata, previous, play/pause, next, and thin progress bar.
  4. **Square (2x2)**: Large cover art tile with centered frosted glass play/pause circle and track typography.
* **Seeking Architecture**:
  - **Tap-to-Seek**: 20 invisible touch zones laid over the widget progress bar allow instant fractional seeks.
  - **Scrub Sheet**: Tapping the time labels launches an instant translucent overlay activity with a live 60 fps AGSL shader and real draggable seekbar with haptic ticks, without bringing the main app to the front.
* **Reliable Remote Actions**:
  - Uses a dedicated `BroadcastReceiver` (`WidgetActionReceiver`) with `PendingIntent.getBroadcast` to prevent Android 12+ `ForegroundServiceStartNotAllowedException`.

---

## Audio Customisation & Settings

### 1. Crossfade & Gapless
- **Crossfade Length**: Configurable slider (1 to 12 seconds).
- **Fade Curves**:
  - `Equal Power` (Recommended): Conserves acoustic energy ($V_{out}^2 + V_{in}^2 = 1.0$) preventing volume drops mid-fade.
  - `Linear`: Direct algebraic gain ramp.
  - `Logarithmic`: Smoothstep curve for punchier transitions.
- **Auto-Gapless**: Automatically suppresses crossfade between consecutive album tracks or live mixes.

### 2. System-Wide EQ (All Apps)
- Operates via a dedicated foreground service with its own notification.
- Intercepts audio session IDs (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`) from third-party players (Spotify, YouTube Music, etc.) and attaches hardware `DynamicsProcessing`.
- **Anti-Double-EQ Protection**: Automatically disables internal DSP equalization while system-wide EQ is active to avoid double filtering.

### 3. Audio Processing & DSP Controls
- **Draggable Parametric Curve**: Interactive glass graph allowing direct touch dragging of frequency, gain, and Q nodes, overlayed with a live spectrum analyzer.
- **Tone Controls**: 100 Hz bass shelf and 8 kHz treble shelf ($\pm 12\text{ dB}$).
- **Harmonic Bass**: Generates musical harmonics for deep bass feel on mobile phone speakers.
- **Convolver**: Real-time partitioned impulse response loader with live CPU load telemetry.
- **AutoEQ Import**: Loads headphone correction profiles directly from AutoEQ text files.
- **Per-Output Profiles**: Automatically saves and restores distinct EQ curves for Speaker, Wired, and Bluetooth.

### 4. Privacy & Last.fm Scrobbling
- **Strictly Opt-in Lyrics**: Zero internet requests made unless LRCLIB online lyrics is explicitly enabled.
- **User-Provided Last.fm Credentials**: Enter your own Last.fm API Key and Secret directly in Settings. No keys are hardcoded or bundled in the app.

---

## How to Build & Install

### Prerequisites
* JDK 17
* Android SDK (API 34)

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
adb install -r app-android\build\outputs\apk\debug\app-android-debug.apk
```

---

## Releasing & CI/CD

See [docs/RELEASING.md](docs/RELEASING.md) for details on the automated GitHub Actions release workflow, setting up the 4 signing secrets, and managing your release keystore.

---

## License

```
Copyright 2026 Node Player Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
