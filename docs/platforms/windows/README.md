# Node Player for Windows (v1.4.0)

## Overview
Node Player for Windows brings the signature Liquid Glass design and bit-perfect audio engine of Node Player to Windows desktop, sharing 100% of the core DSP, models, and header parsing modules with Android.

## Architecture & Shared Modules
- **`:core-dsp`**: 32-bit floating point parametric equalizer, preamp gain, soft limiter, tone controls, loudness compensation, convolver, and crossfeed.
- **`:core-model`**: Pure Kotlin models (`Track`, `AudioEffectConfig`, `EqBandConfig`, `HiResStatus`).
- **`:core-library`**: Pure Kotlin header parser (`AudioHeaderParser`) inspecting FLAC, WAV, MP3, AAC, ALAC, Opus, and OGG streams.
- **`:core-common`**: Cross-platform platform abstractions (`AudioOutput`, `AudioDecoder`, `KeyValueStore`, `FileScanner`, `MediaSessionBridge`).
- **`:app-windows`**: Compose Desktop application with WASAPI audio engine (shared & exclusive modes), Skia liquid glass shaders, and SQLite library database.

## Building & Running
```powershell
# Run desktop app locally
.\gradlew.bat :app-windows:run

# Run Windows unit test suite
.\gradlew.bat :app-windows:test

# Package MSI installer
.\gradlew.bat :app-windows:packageMsi
```

## Storage Locations
- **Library Database**: `%APPDATA%\NodePlayer\library.db` (SQLite)
- **Settings & Config**: `%APPDATA%\NodePlayer\settings.properties`
