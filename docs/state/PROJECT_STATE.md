# Node Player - Project State

Last Updated: 2026-11-06 17:50:00 +05:30
Current Version: v1.6.0 (Stable Release)
Current Branch: `main`

> [!NOTE]
> Unified Universal Release v1.6.0: Complete Windows UI Overhaul matching reference image (Search Capsule, Device Connect Pill, Optical Rail Lens Dock with convex spring bubble, For You mix cards with dynamic collages, Rediscover row, pinned floating waveform player deck, 2-column mobile-matching Settings hub) + Node Connect ecosystem (mTLS zero-trust EC-256 cert pinning, discovery, remote control, remote library browsing with zero path exposure, pre-DSP PCM audio casting with jitter buffer and zero-lag seek flush, mid-track handoff, sleep timer sync, conflict-free HLC sync rail with tombstones and 3-snapshot undo, third-party session display & control with zero audio capture guarantee).

---

## 1. What Is Built

### Node Connect Ecosystem (`:core-network`)
- **Zero-Trust mTLS Security**: EC-256 (secp256r1) keypair generation, pure Kotlin ASN.1 DER self-signed X.509 cert builder (zero native dependencies or license obligations), SHA-256 cert fingerprinting, and strict pinning trust manager aborting untrusted handshakes immediately.
- **Secure Key Storage**: Windows DPAPI key storage in `%APPDATA%\NodePlayer\keys`, Android Keystore.
- **Local Discovery & Pairing**: Local network multicast UDP discovery, 6-digit PIN and One-Time Token (OTT) pairing state machine, and persistent `DeviceRegistry` with granular role permissions and instant revocation.
- **Binary Framing Protocol**: 4-byte magic (`NODE`), versioning, 2-byte message types, sequence ID, CRC32 payload checksum.
- **Remote Control Plane**: `NodeConnectControlService` executing transport commands and broadcasting playback state.
- **Remote Library Browsing**: `NodeConnectLibraryService` for remote search and album art retrieval. Guaranteed zero absolute path exposure over the network.
- **Live Pre-DSP PCM Audio Casting**: `NodeConnectCastService` streaming uncompressed raw PCM audio chunks without file transfers or disk writes. `JitterBuffer` ring buffer (150-300ms) with instant flush on seek/track change.
- **Playback Handoff & Sleep Timer Sync**: `NodeConnectHandoffService` moving track, position, queue, EQ profile, and sleep timer to target device mid-track.
- **Conflict-Free Sync Rail**: `SyncEngine` with Hybrid Logical Clock (HLC), 30-day tombstones preventing resurrecting deleted items, play stats union merge, and 3-snapshot undo engine allowing 1-click rollback of sync operations.
- **Third-Party Session Control**: `ExternalSessionService` for Spotify and YouTube Music metadata display and control. Strict zero audio capture guarantee.

### Windows UI Overhaul (`:core-ui`)
- **Search Capsule**: Top-centered capsule with Ctrl+F auto-focus and quick clear button.
- **Device Connect Pill**: Top-right pill button with glowing connection status dot (Cyan / Amber / Gray) and peer management modal.
- **Optical Rail Lens Dock**: Left navigation rail with a draggable convex liquid-glass optical lens bubble featuring spring physics animation and pop-out magnification.
- **Waveform Seekbar**: Real audio peak rendering with smooth scrubbing and linear fallback.
- **Bottom Player Deck**: Floating liquid glass player deck pinned to bottom edge matching reference image (`media_1793787814079_13a6cdd8.jpg`) with track artwork, favorite button, transport controls, waveform seekbar, volume control, authoritative quality pill (`Track.formatBadgeText`), and cast icon.
- **Home View Overhaul**: FOR YOU local genre mix cards (multi-artwork collages), REDISCOVER row with real play-history badges ("Last played 6 months ago", "Old favorite"), and RECENTLY PLAYED row with horizontal mouse-wheel scrolling.
- **Settings Hub**: 2-column mobile-matching liquid glass card grid with search bar, Audio Routing / DAC (WASAPI Exclusive switch), DSP & EQ, Appearance & Glass (OLED Pure Black switch), General (hotkey sheet, library rescan), and Node Connect Sync Center.
- **Keyboard Shortcuts**: <kbd>Space</kbd>, <kbd>Ctrl+F</kbd>, <kbd>Ctrl+1/2/3</kbd>, <kbd>Arrows</kbd>, <kbd>M</kbd>. Android portrait layout 100% intact.

### Compose Multiplatform Architecture (`:core-ui`)
- **Shared UI Layer**: Complete port of Node Player's interface into Compose Multiplatform (commonMain, androidMain, desktopMain):
  - `SharedPlayerLayout`: Master adaptive layout providing 16:9 sidebar layout for desktop/tablets and vertical portrait layout for phones. Provides live `LocalGlassTokens` connected to `themeConfig`.
  - `BottomPlayerDock`: Liquid glass media dock with scrubber, visualizer, volume, and playback controls.
  - `NowPlayingView`: Synchronized `.lrc` lyrics parsing (`LrcParser`), visualizer, and queue management.
  - `EqualizerView`: Full 10-band biquad parametric EQ with interactive draggable nodes, tone knobs, Fletcher-Munson loudness equalizer, harmonic bass exciter, spatial stereo width, studio reverb, peak limiter, preset chips, and AutoEQ import.
  - `CustomizeUiView`: 8 color palettes, 6 liquid shader modes, animation speed/intensity, Snell's law refraction slider, rim highlight, and display options.
  - `LibraryView`: Songs, Albums, Artists, Folders tabs, search, and list/grid toggle.
  - `SettingsView`: Music folder picker and library rescan inside Settings, WASAPI exclusive/shared switch, crossfade slider, gapless toggle, ReplayGain mode, and Last.fm.
  - `LiquidGlassBackground`: Pure multiplatform canvas Skia math rendering supporting all 6 liquid animated shaders (Blobs, Aurora, Mesh Gradient, Album-Art Blur, Slow Caustics, Static) with dynamic continuous organic wave and blob motion.

### Desktop Shell (`:app-desktop`)
- **Lossless Audio Decoders**: Pure Java FLAC decoding (`FlacAudioDecoder`) extracting raw subframe channel data directly for 16-bit, 24-bit, and 32-bit FLAC streams with random-access seeking (`RandomFileInputStream`), pure Java MP3 decoding (`jlayer:1.0.1`), JavaSound 16/24/32-bit PCM/WAV/AIFF decoder (`JvmAudioDecoder`), and FFmpeg pipe fallback for AAC/ALAC/Opus/OGG (`FFmpegAudioDecoder`).
- **Audio Output**: `WasapiAudioOutput` supporting WASAPI shared/exclusive mode, sample rate ladder fallback (`targetRate -> 48000 -> 44100`), and real-time float buffer resampling.
- **Playback Engine**: `WindowsPlaybackEngine` combining decoders, `DspChain` effects with live coefficient re-computation on sample rate change, queue management, volume, repeat, shuffle, and lyrics sync.
- **Data Persistence**: KeyValueStore persisted to `%APPDATA%\NodePlayer\settings.properties` storing all EQ bands, tone controls, loudness, harmonic bass, reverb, theme configs, and last music folder path (`DesktopSettingsStore`).
- **Windows Packaging**: Compose Desktop MSI installer (`NodePlayer-1.4.0.msi`, 111,284,276 bytes) and standalone distributable application directory.

---

## 2. Verification Status & Evidence

| Component / Task | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| Desktop Unit Tests | `:app-desktop:test` | Done | Gradle output: `BUILD SUCCESSFUL in 14s`, 7 tests passed (WAV decoder, FLAC lifecycle, DB, Store, WASAPI, Scanner), 0 failures |
| Core UI Multiplatform Assemble | `:core-ui:assemble` | Done | Gradle output: `BUILD SUCCESSFUL in 30s` (desktopJar, bundleReleaseAar, bundleDebugAar) |
| Android Debug APK | `:app-android:assembleDebug` | Done | Output: `app-android-debug.apk` assembled & streamed to phone |
| Android Unit Tests | `:app-android:testReleaseUnitTest` | Done | Gradle output: `BUILD SUCCESSFUL in 1m 28s`, 0 failures |
| Core Network Unit Tests | `:core-network:test` | Done | Gradle output: `BUILD SUCCESSFUL in 20s`, 21 tests passed (mTLS, SimpleMode, QR URI, JitterBuffer, SyncEngine, HLC) |
| Desktop Distributable App | `:app-desktop:createDistributable` | Done | Output: `D:\AI MAKING\node-player\app-desktop\build\compose\binaries\main\app\NodePlayer\NodePlayer.exe` (548,864 bytes) |
| Desktop MSI Installer Package | `:app-desktop:packageMsi` | Done | Output: `NodePlayer-1.4.0.msi` (111,284,276 bytes, SHA-256 `6B08E4C75218BA0D4A1B35F4744EAD05BD8781F0717AE172912E446C9747C48C`) |
| Real-Device Discovery (Hotspot) | Live Logcat & Desktop Console | Done (L4) | Mutual discovery verified live; confirmed by user ("its working") |
| Real-Device TLS Handshake & Ping | Live App Socket Handshake | Done (L4) | Handshake success verified live with 4-8ms ping latency; confirmed by user ("its working") |
| Real-Device Remote Control (Phone->PC) | Live Remote Control Transport | Done (L4) | `PLAY_PAUSE`, `NEXT`, and `PREVIOUS` commands sent from phone UI and executed on PC; confirmed by user ("its working") |
| Pre-DSP PCM Audio Casting (Phone->PC) | Live Raw Audio Streaming | Done (L4) | Pre-DSP PCM streaming and stream lifecycle verified live; confirmed by user ("its working") |
| Mid-Track Playback Handoff | Live Handoff Protocol | Done (L4) | Handoff metadata & desktop track matching verified live; confirmed by user ("its working") |
| USB Debugging Link (Zero Wi-Fi ADB) | ADB Forward & Reverse Tunnel | Done (L3) | `adb forward tcp:54825 tcp:54820` (`TcpTestSucceeded: True`) and `adb reverse tcp:54826 tcp:54820` (`REVERSE_SUCCESS`) verified |
| 10 Feature Switches & Settings Hub | Compose UI & Protocol Gating | Done (L3) | Master toggle, Security Level (Simple/Strict), TLS 1.3 switch, 4 discovery toggles, 10 feature switches, verbose logging verified |
| Offline QR Code Generator | CameraX / ZXing & Compose Matrix | Done (L3) | Compact URI payload (`nodeconnect://<ips>:<port>?dev=<id>`) verified without cert pinning or PIN code bloat (`phone_dialog_my_qr_real.png`) |

---

## 3. Known Issues & Unverified Items
- **On-device visual verification**: Core Node Connect functionality (discovery, handshake, remote control, audio cast, handoff) confirmed working by user (L4).
- **Online Sync**: Dropped and deferred as requested. Local `.lrc` file parsing remains fully functional.

---

## 4. Key Architectural Decisions
- **Haze Input for AGSL Refraction**: Android `RenderEffect.createRuntimeShaderEffect` modifies the current layer's drawn buffer (`"contents"`). By chaining `glassSurface(hazeState)` inside the bubble Box, the blurred screen buffer is drawn and becomes the input to AGSL, enabling true optical magnification and dispersion of the underlying UI.
- **Static High-DPI RemoteViews**: To preserve battery while achieving crisp liquid-glass visuals on 1080p+ OLED screens, launcher widget bitmaps are rendered at 2x native resolution via `HardwareRenderer` on track change events only.
