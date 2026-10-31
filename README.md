# Node Player 🎵

A local Android music player built with a Glassmorphism design language, 32-bit floating-point DSP pipeline, bit-perfect USB DAC support, and deep audio settings.

---

## Architecture & Modules

* **`:core-model`**: Pure Kotlin models shared with desktop/Windows (`Track`, `Album`, `Playlist`, `Lyrics`, `AudioEffectConfig`, `AudioOutputInfo`, `GlassThemeConfig`).
* **`:core-dsp`**: Pure Kotlin 32-bit float audio DSP engine (Parametric Biquad Equalizer, Soft Limiter, ReplayGain, Preamp, Crossfeed, Stereo Width). Contains JVM unit tests.
* **`:core-library`**: Parsers for `.lrc` synced lyrics, `.cue` sheets, `.m3u` playlists, and AutoEQ headphone profiles.
* **`:app-android`**: Android application module (Jetpack Compose, Material 3, Haze blur, Media3 ExoPlayer, MediaSessionService, Room Database, Palette dynamic colors).

---

## How to Build & Run

### Prerequisites
* JDK 17
* Android SDK (configured at `D:\AI MAKING\android-sdk`)

### Build from Command Line
```powershell
# Set ANDROID_HOME
$env:ANDROID_HOME = "D:\AI MAKING\android-sdk"

# Build Debug APK
.\gradlew.bat assembleDebug

# Run DSP Unit Tests
.\gradlew.bat :core-dsp:test
```

### Install and Launch on Phone
```cmd
build-and-install.bat
```

---

## Audio Pipeline & Verification

* **32-Bit Float Processing**: Audio is processed in 32-bit floating point throughout the DSP chain.
* **Anti-Clip Soft Limiter**: Smooth tanh saturation at -0.1 dBFS prevents digital harshness and inter-sample clipping.
* **Signal Path Panel**: Displays real-time format, sample rate, bit depth, output device, and honest resampling alerts.
* **Bit-Perfect Output**: Direct hardware routing on Android 14+ for connected USB DACs.

---

## Glassmorphism Customization

Glass tokens are centralized in `com.nodeplayer.music.ui.theme.GlassTokens`:
* `defaultBlurRadius`: Radius of backdrop blur (24.dp).
* `lightBorderBrush()` / `darkBorderBrush()` / `oledBorderBrush()`: Lit-edge illuminated gradient borders.
* `Modifier.glassSurface()`: Uniform glass styling across cards, sheets, and bottom navigation.
