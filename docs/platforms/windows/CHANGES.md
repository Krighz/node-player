# Windows Desktop App Changes (v1.4.0)

## Added Modules & Capabilities
1. **`:app-windows`**:
   - Compose Multiplatform 1.6.11 Desktop application.
   - Dual-mode Windows Audio Session API (`WasapiAudioOutput`) supporting:
     - WASAPI Shared mode with 32-bit float internal routing.
     - WASAPI Exclusive mode (Bit-Perfect output matching source rate and bit depth).
   - Hybrid decoding pipeline (`CompositeAudioDecoder`):
     - Pure JVM decoder (`JvmAudioDecoder`) for WAV, AIFF, and uncompressed PCM.
     - FFmpeg subprocess/library bridge (`FFmpegAudioDecoder`) for AAC, ALAC, Opus, OGG, and MP3.
   - Storage & Persistence:
     - SQLite library catalog (`DesktopTrackDatabase`) via `org.xerial:sqlite-jdbc`.
     - Settings persistence (`DesktopSettingsStore`) at `%APPDATA%\NodePlayer\settings.properties`.
   - Liquid Glass UI:
     - Skia runtime frosted glass and specular rim reflection (`SkiaGlassShader.kt`).
     - Persistent Now Playing dock with time scrubber, volume control, and shuffle/repeat modes.
     - 10-band parametric equalizer screen with live biquad filter DSP tuning.
     - Settings hub with WASAPI Exclusive mode toggle and database statistics.

2. **`:core-common`**:
   - Added platform abstraction contracts in `com.nodeplayer.common.platform`:
     - `AudioOutput.kt`
     - `AudioDecoder.kt`
     - `KeyValueStore.kt`
     - `FileScanner.kt`
     - `MediaSessionBridge.kt`
