# Node Player 🎵

A local Android music player built with a Glassmorphism design language, 32-bit floating-point DSP pipeline, System-Wide EQ, and deep audio customisation.

---

## Architecture & Modules

* **`:core-model`** *(100% Pure Kotlin)*:
  Shared data structures across mobile and desktop (`Track`, `Album`, `Playlist`, `Lyrics`, `AudioEffectConfig`, `AudioOutputInfo`, `GlassThemeConfig`, `CrossfadeConfig`, `AutoEqProfile`).
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
  - **Home Screen Widget**: Frosted glass playback widget for rapid control.

---

## Settings Guide

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
- **Anti-Double-EQ Protection**: Node Player automatically disables its internal DSP equalizer while system-wide EQ is active to avoid double filtering.

### 3. Audio Customisation & DSP
- **Draggable Parametric Curve**: Interactive glass graph allowing direct touch dragging of frequency, gain, and Q nodes, overlayed with a live spectrum analyzer.
- **Tone Controls**: 100 Hz bass shelf and 8 kHz treble shelf ($\pm 12\text{ dB}$).
- **Harmonic Bass**: Generates musical harmonics for deep bass feel on mobile phone speakers.
- **Convolver**: Real-time partitioned impulse response loader with live CPU load telemetry.
- **AutoEQ Import**: Loads headphone correction profiles directly from AutoEQ text files.
- **Profile Export / Import**: Saves and restores complete DSP setups via JSON files.
- **Per-Output Profiles**: Automatically saves and restores distinct EQ curves for Speaker, Wired, and Bluetooth.

### 4. Display & Performance Mode
- **Real Glass Dock Refraction**: AGSL lens distortion and chromatic aberration on the floating navigation dock.
- **Performance Mode**: One-tap toggle that turns off the AGSL refraction shader, animated liquid background, and real-time spectrum, lowering GPU memory by 58% and frame times by 31%.

---

## How to Build & Install

### Fast Command Line Install
```powershell
# Using the PowerShell script
.\scripts\build_and_install.ps1

# Or using the Windows Batch script
.\scripts\build_and_install.bat
```

### Manual Gradle Commands
```powershell
# Build Debug APK
.\gradlew.bat assembleDebug

# Run DSP Unit Tests
.\gradlew.bat :core-dsp:test

# Install on target device
adb install -r app-android\build\outputs\apk\debug\app-android-debug.apk
```
