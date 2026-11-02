# Changelog

All notable changes to **Node Player** are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.3.0] - 2026-11-03

### Added
- **Liquid-Glass Lens Dock**: Floating pill dock featuring draggable spring-physics magnifying bubble, amber/cyan specular rim, light haptic feedback on tab boundary crossing, and separate circular glass search button.
- **In-App Background Animation Engine**: High-resolution procedural background engine with 6 styles (Liquid blobs, Aurora, Mesh gradient, Album-art blur, Slow caustics, Static), dynamic speed (0.2x–3.0x), intensity (0.1–1.0), color source (Artwork, Palette, Custom), animate-only-while-playing gate, frame rate cap (Auto, 60, 90, 120), adaptive quality scaling, and 64x64 blue-noise dithering to eliminate gradient banding.
- **Static Liquid-Glass Home Widgets**: 4 static launcher widgets (Card 4x2, Lyrics 4x3, Slim 4x1, Square 2x2) with baked-in liquid-glass shimmer look, event-driven RemoteViews re-rendering, live in-app preview configuration screen, and granular element visibility toggles (artwork, waveform, title/artist, seekbar/times, transport, lyrics).
- **Player UI Restructuring**: Reordered Now Playing hierarchy (Artwork &rarr; Title/Artist &rarr; One clickable quality line [source format + bit-perfect/resampled sink] &rarr; Seekbar with times &rarr; Primary transport controls &rarr; Secondary tools row: shuffle, repeat, queue bottom sheet, lyrics, sleep timer, EQ) with universal 48dp touch targets.
- **Sectioned Settings Hub**: Redesigned Settings into a 2-column glass card grid with real-time search filtering, quick mode chips (Performance, Shader, Theme), subpage navigation with state memory, and per-section reset to defaults.
- **Full Customize UI Suite**: Comprehensive personalization hub with interactive live preview cards, 6 theme presets (*Obsidian Liquid*, *Frost Pearl*, *Amber Flare*, *Neon Aurora*, *Stealth OLED*, *Minimal Glass*), bundled fonts (*Inter*, *Outfit*, *JetBrains Mono*, *System Default*), corner roundness (*Sharp 8dp*, *Rounded 16dp*, *Pill 24dp*), spacing density (*Compact 10dp*, *Comfortable 16dp*, *Roomy 22dp*), lens refraction & rim highlight sliders, component toggles, and `.nodetheme` JSON export/import.
- **iOS-Grade Spring Motion & Animation Playground**: Unified spring physics tokens (*Snappy*, *Smooth*, *Bouncy*, *Gentle*), fluid `.pressScale()` modifier on cards & buttons, interactive draggable spring pucks, and an Animation Playground screen to test motion profiles.

---

## [1.2.0] - 2026-11-02

### Added
- **In-App Update System**: Opt-in GitHub Releases update checker comparing semantic versions (`SemVer`), with background checks via battery-friendly `WorkManager` (daily, unmetered network) and foreground throttling (at most once every 12 hours). Includes first-run opt-in dialog and privacy disclosures.
- **Glass Update Banner**: Non-blocking liquid glass card on the Home screen featuring release notes, asset size, and actions (*Update now*, *Later*, *Skip this version*).
- **Background Download Engine**: Resumable HTTPS download directly to private app storage, notification progress, storage space checks, and strict SHA-256 checksum verification against official release assets.
- **Secure PackageInstaller Integration**: Streams updates directly into Android `PackageInstaller` with `USER_ACTION_NOT_REQUIRED` on Android 12+, unknown app sources guidance (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`), pre-install security validation (verifying package name, strictly newer version, and matching signing certificates), and active playback confirmation.
- **Post-Update What's New Sheet**: Highlights new features and improvements immediately after an update installs.
- **Community & Feedback Hub**: Enabled GitHub Discussions on the repository and added *Send feedback* and *Report a bug* (with device and OS details pre-filled) options across *More*, *Settings*, the project README, and website footer.
- **Voluntary Support Integration**: Added optional voluntary UPI support (`7063061981@fam`, payee `Krighz`) with one-tap payment launching and clipboard copying in *Settings*, *More*, `.github/FUNDING.yml`, and website with a crisp inline SVG QR code.
- **Debug Mock Updater**: Added debug controls in *Graphics Diagnostics* to test update banner rendering, dismissals, and version unskipping.

### Fixed
- Fixed `UnsupportedOperationException: Tried to obtain display from a Context not associated with one` on Android 16 (API 36) by querying `DisplayManager` and falling back to display metrics.

---

## [1.1.0] - 2026-11-02

### Added
- **AGSL Shader Hardening**: Hardened refraction shaders across the dock, mini player, scrub sheet, and home widgets using high-precision `float` coordinates, normalized math (`0..1`), divide/normalize guards with epsilon, time/coordinate modulo wrapping, and strict view boundary clipping to prevent GPU-specific out-of-bounds artifact spills (e.g. Mali-G77 / Dimensity 8020).
- **Automated Shader Self-Test**: Added an offscreen rendering self-test that verifies bounding transparency, pixel sanity, and plausible luminance on launch. Automatically switches device to safe Haze blur with rim highlight if any anomaly is detected, with a debug switch to force-fail for testing.
- **Glass Appearance Controls**: Unified glass controls under Settings controlling every surface (dock, mini player, cards, sheets, dialogs, lyrics, home widgets): sliders for Opacity, Blur Strength, Grain Texture, and Edge Highlight (0–100%), presets (*Clear*, *Light*, *Balanced*, *Frosted*), and *Fully Transparent Glass* mode with *Keep Text Readable* scrim.
- **Graphics Diagnostics Screen**: New screen under *More &rarr; Graphics Diagnostics* displaying device model, Android OS, SoC model, GPU renderer from temporary EGL context, display metrics, self-test status, side-by-side shader previews, and safe graphics mode toggle.
- **Diagnostics Dump Engine & 60s Record Mode**: One-tap export to `nodeplayer-diagnostics-<date>.zip` containing PixelCopy screenshot, rolling ring buffer of 200 graphics events, process logcat, and hardware info with strictly zero personal data. Includes 60s record mode and system share sheet integration.
- **Graphics Glitch Issue Template**: Added GitHub issue template and website FAQ entry for visual and shader troubleshooting.

### Changed
- Extended widget glass opacity range to `0%..100%`, dynamically synced with app glass theme tokens.
- Optimized rendering pipeline to completely skip blur and shader layers when opacity or blur is zero.
- Re-architected glass tokens into a single DataStore source of truth in `NodePlayerTheme`.

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
