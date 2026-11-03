# Node Player Shared UI Architecture

## Overview
Node Player uses a single, shared Compose Multiplatform UI across:
- **Android Phone** (portrait layout, touch navigation, Android MediaSession, System-wide EQ, and home screen widgets).
- **Android Landscape** (16:9 responsive layout with collapsible sidebar navigation, split content panel, and docked media controls).
- **Windows Desktop** (16:9 desktop window, native mouse and keyboard navigation, WASAPI bit-perfect/shared audio output, local SQLite database, and high-performance Skia liquid shaders).

All platforms run strictly on version **v1.4.0**.

## Module Structure
- `:core-model`: Data structures (Track, Album, Artist, EqualizerConfig, GlassThemeConfig, PlaybackState). Multiplatform with JVM and Android targets.
- `:core-dsp`: 10-band biquad parametric EQ, Fletcher-Munson loudness compensation, harmonic bass exciter, stereo widener, studio reverb, and peak limiter.
- `:core-library`: Audio scanner parser, track metadata repository interfaces, and search indexing.
- `:core-common`: Common utilities, architecture interfaces (`AudioOutput`, `AudioDecoder`, `MediaLibraryScanner`, `KeyValueStore`, `PlatformServices`).
- `:core-ui`: Shared Compose Multiplatform UI:
  - `SharedPlayerLayout`: Master adaptive shell switching between phone portrait and 16:9 expanded layout.
  - `BottomPlayerDock`: Frosted glass media dock with scrubber, visualizer, volume, and playback controls.
  - `NowPlayingView`: Fullscreen liquid-glass sheet with synchronized `.lrc` lyrics, dynamic spectrum visualizer, and queue management.
  - `EqualizerView`: Complete 10-band EQ curve graph with interactive biquad nodes, tone knobs, and DSP toggles.
  - `CustomizeUiView`: 6-shader liquid background engine, color palette swatches, lens refraction, and rim highlight sliders.
  - `LibraryView`: Songs, Albums, Artists, Folders browser with search and grid/list view toggles.
  - `SettingsView`: Audio device output, sample rate selector, WASAPI mode, crossfade, and gapless settings.
- `:app-android`: Android application shell wrapping `:core-ui` with Media3 service, AudioTrack output, hardware volume keys, and system widgets.
- `:app-desktop`: Desktop application shell using Compose Desktop, JavaSound/WASAPI audio pipeline, JLayer MP3, jFLAC decoders, SQLite JDBC persistence, and native packaging (MSI / Exe / App image).
