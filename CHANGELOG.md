# Changelog

All notable changes to **Node Player** are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-11-02

### Added
- **Multi-Stage Artwork Decoder**: Custom Coil `AudioArtworkFetcher` supporting Android 10+ (API 29+) MediaStore `loadThumbnail`, `MediaMetadataRetriever` embedded APIC/FLAC picture extraction, and content resolver fallbacks.
- **Enhanced Launcher Widgets**: Card (4x2), Lyrics (4x3), Slim (4x1), and Square (2x2) widgets display sharp album art, palette-adaptive wave shaders, and track persistence via SharedPreferences when playback is idle.
- **Expanded Home Screen Suggestions**: Added dynamic shelves beyond Rediscover—Heavy Rotation ("On Repeat"), Artist Spotlight mix, Hi-Res Studio Masters with sample rate/bit depth badges, Fresh Discoveries, and Quick Mixes chips.
- **Truthful Signal Path**: Real-time visual pipeline inspection card showing source encoding, 32-bit float DSP chain modifications, AudioTrack sink configuration, and hardware routing node.
- **Algorithmic Reverb**: Freeverb 4-comb diffusion processor with configurable room size and damping controls.
- **Sound Lab & Acoustic Diagnostics**: Built-in fake hi-res audio spectrum detector, EBU R128 integrated loudness scanner, dynamic range (DR) approximation meter, and real-time audio visualizer.
- **Listening Stats & Yearly Recap**: Wall-clock listening time tracking with top artists, top albums, and annual recap insights stored 100% locally on device.
- **Local Diagnostics**: Private `LocalCrashLogger` and in-app sanitized crash viewer with zero external uploads.
- **Data Portability**: Unified `.nodebackup` export and import for playlists, play counts, favorites, and settings, plus full M3U playlist file import.
- **First-Run Onboarding**: 3-step onboarding flow introducing audio engine features and ColorOS / realme GT 6T battery optimization guidance.

### Changed
- Refactored `HomeScreen`, `FrostedNavAndPlayer`, and `NowPlayingSheet` to consume unified `Track` Coil models.
- Updated release build type with R8 minification and resource shrinking enabled.
- Hardened background widget action receivers to route through broadcast intents, avoiding Android 12+ foreground start restrictions.

### Fixed
- Fixed missing album artwork in launcher AppWidgets and in-app carousels on Android 10+ scoped storage.
- Fixed sample rate and bit-depth parsing from genuine audio file headers (including FLAC STREAMINFO metadata offsets).
- Fixed main-thread player access and strict mode I/O violations during playback service preloading.

---

## [0.1.0] - 2026-11-01

### Added
- **Core Architecture**: Modular pure-Kotlin foundation (`:core-model`, `:core-dsp`, `:core-library`) and Android application module (`:app-android`).
- **32-Bit Float Audio DSP**: High-precision audio pipeline including 10-band biquad parametric equalizer ($\pm0.1\text{ dB}$ tolerance at band centres), linear pass-through soft-knee limiter (pure linear below $-0.9\text{ dBFS}$), ISO 226 loudness contour, harmonic bass synthesizer, partitioned convolution reverb, and zero-allocation TPDF dither.
- **Dual-ExoPlayer Crossfade**: Seamless track crossfading with mathematical curves (Equal Power, Linear, Logarithmic) and auto-gapless live album detection.
- **System-Wide EQ**: Android foreground service attaching software `DynamicsProcessing` effect to compatible third-party players (tested with Spotify and YouTube Music) with anti-double-EQ bypass.
- **Liquid Glass Compose UI**: Glassmorphism design language using Haze backdrop blur, Android 13+ AGSL optical lens refraction dock shader, and OLED / Dark / Light themes.
- **Four Liquid Glass Home-Screen Widgets**: Hardware offscreen rendered AGSL glass widgets (`RenderNode` + `HardwareRenderer` + `ImageReader`) with 20 tap-to-seek slices, synced lyrics, and translucent 60 fps scrub sheet.
- **Metadata Parsers**: Pure-Kotlin parsers for `.lrc` synchronized lyrics, AutoEQ headphone correction text profiles, `.cue` sheets, and `.m3u` playlists.
- **Last.fm Scrobbling**: Opt-in Last.fm scrobbler with browser authorization flow and offline retry queue.
- **Battery Optimization Guide**: In-app guidance tailored for ColorOS and Android battery management.
