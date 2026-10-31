# Windows Porting Guide & Architecture

## Shared Modules (Ready for Windows / Compose Multiplatform)
The architecture is deliberately modularized so core logic can be shared directly with Windows:

1. **`core-model`** (100% Pure Kotlin):
   - Data structures: `Track`, `Album`, `Artist`, `Playlist`, `Lyrics`, `AudioEffectConfig`, `AudioOutputInfo`, `GlassThemeConfig`.
   - Ready to share across Android, Windows, macOS, Linux with Kotlin Multiplatform (KMP).

2. **`core-dsp`** (100% Pure Kotlin):
   - Math & audio DSP: `BiquadFilter`, `ParametricEqualizer`, `SoftLimiter`, `PreampGain`, `ReplayGainProcessor`, `StereoWidthProcessor`, `CrossfeedFilter`, `DspChain`.
   - Zero Android dependencies. Tested on the JVM with unit tests verifying sine sweeps, full-scale limiting, and gain calculations.
   - Runs directly on Windows via Java Sound / WASAPI float streams.

3. **`core-library`** (100% Pure Kotlin):
   - `LrcParser`: LRC synchronized & unsynchronized lyric parser.
   - `CueSheetParser`: CUE sheet track splitter parser.
   - `M3uParser`: Playlist parser and exporter.
   - `AutoEqParser`: Parametric EQ text profile parser.

---

## What Needs to Be Implemented for Windows

1. **Audio Output Subsystem:**
   - On Android: Media3 (`ExoPlayer`) + `DefaultAudioSink` + `AudioTrack` float output.
   - On Windows: WASAPI Exclusive mode (for bit-perfect bitstreams) or ASIO via `JNA` / `jASIOHost` / Java Sound API with 32-bit float buffers fed into `DspChain.processInterleavedFloat()`.

2. **File & Library Scanning:**
   - On Android: `MediaStore.Audio.Media` content resolver.
   - On Windows: Native folder scanner using `java.nio.file.Files.walk()` + tag parsing via `jaudiotagger` or pure Kotlin ID3/Vorbis comment parsers.

3. **UI Porting Strategy:**
   - Migrate UI code from Jetpack Compose to **Compose Multiplatform (Desktop)**.
   - Glassmorphism on Windows: Utilize Windows 11 Mica / Acrylic surfaces via Win32 DwmSetWindowAttribute or software translucent noise-blended surfaces.
