# Platform Implementation Changes (v1.6.0)

**Branch:** `feature/node-connect`  
**Base Release:** v1.5.0  

---

## Change Log

### [2026-11-04] - Step 1: Documentation Honesty & Dynamic Verification Pass
- **Shared Code (`core-model`, `core-library`, `core-ui`)**:
  - `AudioEffectConfig.kt`: Added `isDspBypassed` calculating whether all 8 DSP modules are flat/bypassed.
  - `AudioOutputInfo.kt`: Added `isTrulyBitPerfect(srcRate, srcDepth, dspBypassed)`.
  - `AudioHeaderParser.kt`: Restored in-memory `parseBytes` function and sub-parsers (`parseFlacBytes`, `parseWavBytes`, `parseMp3Bytes`, `parseOggOrOpusBytes`, `parseM4aOrAlacBytes`) to ensure full test suite compatibility.
  - `SharedPlayerLayout.kt`: Dynamically computes truthful output status string (e.g. "Bit-Perfect Output" vs. "32-bit Float DSP Active" vs. "Direct").
- **Android (`app-android`)**:
  - `TrackDetailsSheet.kt`: Evaluates `isBitPerfect` strictly against source format and DSP bypass state. Shows "Bit-perfect direct stream" only when true.
- **Documentation**:
  - `PRIVACY.md`: Updated to "No network use unless you opt in" and documented the 4 opt-in network capabilities.
  - `README.md` & `site/index.html`: Replaced "100% offline" with opt-in disclosures. Clarified that Windows MSI/EXE are Authenticode-unsigned (SmartScreen prompt on initial run) and APK is signed with release keystore (v2 scheme). Emphasized SHA-256 as primary trust hash. Added v0.1.0 to version history.
- **Status**: Completed, tests pass, committed locally (`bf47ffb`).

---

### [2026-11-04] - Step 2: Node Connect & UI Overhaul Specification
- **Ecosystem Specs Created**:
  - `docs/ecosystem/NODE_CONNECT.md`: Comprehensive protocol, roles, gRPC service definitions, message flows, and timing constants.
  - `docs/ecosystem/NODE_CONNECT_SECURITY.md`: Zero-trust mTLS architecture, QR / PIN pairing, Android Keystore & Windows DPAPI key storage, and granular permissions.
  - `docs/ecosystem/SYNC_CATEGORIES.md`: Complete sync category rules, shared/per-platform scopes, Murmur3+MD5 track fingerprint, and 3-snapshot undo engine.
  - `docs/ecosystem/NODE_CONNECT_TESTS.md`: Automated test matrix for mTLS, fault injection, streaming, and conflict resolution.
  - `docs/ecosystem/NODE_CONNECT_DEVICE_TESTS.md`: Human verification sheet with 23 step-by-step test cases.
  - `docs/platforms/windows-ui.md`: Windows UI overhaul reference map and token definitions.
- **Status**: Completed, specification ready for implementation.

---

### [2026-11-04] - Step 3: Windows UI Overhaul Matching Reference Image
- **Compose Multiplatform Components (`core-ui`)**:
  - `SearchCapsule.kt`: Top-centered search bar with Ctrl+F auto-focus and quick clear button.
  - `DeviceConnectPill.kt`: Top-right pill button with dynamic glowing status indicator dot (Cyan = Connected, Amber = Discovering, Gray = Disconnected) and peer quick-sheet.
  - `OpticalRailDock.kt`: Left navigation rail featuring a convex liquid-glass optical lens bubble with spring physics animation and pop-out scale on drag/selection.
  - `WaveformSeekbar.kt`: Real audio peak rendering with smooth scrub support, unplayed bar dimming, and linear fallback.
  - `BottomPlayerDeck.kt`: Floating liquid glass player deck pinned to bottom edge matching reference image (`media_1793787814079_13a6cdd8.jpg`) with track artwork, favorite toggle, transport controls, waveform seekbar, volume control, authoritative quality pill (`Track.formatBadgeText`), and cast icon.
  - `HomeView.kt`: Overhauled with FOR YOU local genre mix cards (dynamic multi-artwork collages), REDISCOVER row with real play-history badges ("Last played 6 months ago", "Old favorite"), and RECENTLY PLAYED row with horizontal mouse-wheel scrolling.
  - `SettingsView.kt`: Re-architected into 2-column liquid glass card grid with search filtering, sub-sections for Audio Routing / DAC (WASAPI Exclusive mode switch), DSP & EQ, Appearance & Glass (OLED Pure Black switch), General (hotkey sheet, library rescan), and Node Connect Sync Center.
  - `SharedPlayerLayout.kt`: Unified expanded 16:9 layout with global keyboard shortcuts (<kbd>Space</kbd>, <kbd>Ctrl+F</kbd>, <kbd>Ctrl+1/2/3</kbd>, <kbd>Arrows</kbd>, <kbd>M</kbd>), keeping Android portrait layout 100% intact.
- **Status**: Completed, tests pass, committed locally (`facfbff`).

---

### [2026-11-04] - Step 4: Foundation (`core-network` Module & mTLS Security)
- **Module Creation (`core-network`)**:
  - Added `:core-network` module in `settings.gradle.kts` and configured `build.gradle.kts` (JVM 17 / Kotlin Multiplatform).
  - `KeyManager.kt`: EC-256 (secp256r1) keypair generation, pure Kotlin ASN.1 DER self-signed X.509 certificate generator (10-year validity, zero external dependencies/licenses), and colon-hex SHA-256 cert fingerprinting.
  - `PinningTrustManager.kt` & `NodeSslContext.kt`: Enforces mutual TLS (TLSv1.3 with TLSv1.2 fallback) with certificate pinning. Untrusted / unpinned certificates rejected during TLS handshake before byte processing.
  - `SecureKeyStorage.kt`: Persistent device key storage in `%APPDATA%\NodePlayer\keys` (Windows) and app-private directory (Android).
  - `NodeFraming.kt`: Binary packet framing with 4-byte magic (`NODE`), version byte, 2-byte message type, sequence ID, length, and CRC32 payload checksum.
  - `NodeMessages.kt`: Zero-reflection JSON/binary message serializers for handshakes, control commands, audio cast chunks, and sync payloads.
  - `DeviceRegistry.kt` & `PairingManager.kt`: One-Time Token (OTT) and 6-digit PIN pairing state machine with role permissions and instant revocation.
  - `HeadlessTestPeer.kt`: In-process peer runner for automated integration testing and fault injection (packet drop, jitter delay).
- **Status**: Completed, all tests pass, committed locally (`8936ec7`).

---

### [2026-11-04] - Step 5: Control Plane (Remote Control, Library Browse & Mirror)
- **Services (`core-network`)**:
  - `NodeConnectLibraryService.kt`: Remote library browse and universal search with pagination. Privacy guarantee: local absolute filesystem paths are replaced with virtual remote identifiers (`nodeconnect://remote/track/<id>`). Remote album art thumbnail retrieval.
  - `NodeConnectControlService.kt`: Remote transport command execution (Play, Pause, Seek, Next, Prev, Volume, Shuffle, Repeat) and state broadcasting to authorized controllers.
  - `NodeConnectMirrorService.kt`: Now Playing mirror service for displaying remote playback on larger screens (display only; zero local audio output).
  - `NodeConnectHub.kt`: Central lifecycle coordinator binding discovery, server, registry, and control services.
- **Status**: Completed, all tests pass, committed locally (`fb99b51`).

---

### [2026-11-04] - Step 6: Audio Sharing (Pre-DSP PCM Streaming & Jitter Buffer)
- **Audio Streaming Engine (`core-network`)**:
  - `NodeConnectCastService.kt`: Live pre-DSP raw PCM audio streamer. Zero file transmission, receiver never writes audio bytes to disk. Immediate `CAST_FLUSH` packet on seek/skip with sequence counter increment.
  - `JitterBuffer.kt`: Dynamic ring buffer (target 150-300ms) with stale packet dropping, buffer underrun/overrun protection, and instant zero-latency flush on track seek.
- **Status**: Completed, all tests pass, committed locally (`ccbe5c6`).

---

### [2026-11-04] - Step 7: Handoff & Sync Extras
- **Handoff Service (`core-network`)**:
  - `NodeConnectHandoffService.kt`: Seamless mid-track playback handoff moving active track, millisecond position, play queue, active EQ preset, and sleep timer remaining milliseconds to the target device.
- **Status**: Completed, all tests pass, committed locally (`c36e1c4`).

---

### [2026-11-04] - Step 8: Data Transfer & Sync Rail
- **Conflict-Free Sync Engine (`core-network`)**:
  - `HlcClock.kt`: Hybrid Logical Clock tracking causality across distributed devices without synchronized wall clocks.
  - `SyncEngine.kt`: Multi-domain synchronization (Playlists, Favorites, Play History & Stats, EQ Profiles). 30-day tombstones preventing resurrected deleted items. Stats union merge (`max(playCount)`, `max(lastPlayedTimestamp)`).
  - **3-Snapshot Undo Engine**: Automatically captures pre-sync snapshots before applying remote changes. Allows rolling back the last 3 sync operations with a single click.
  - `NodeConnectTransferService.kt`: Low-priority chunked transfer in 256 KB slices over TLS with progress tracking.
- **Status**: Completed, all tests pass, committed locally (`31e60f0`).

---

### [2026-11-04] - Step 9: Third-Party Media Sessions
- **Session Bridge (`core-network`)**:
  - `ExternalSessionService.kt`: Transmits now-playing metadata and forwards play, pause, and skip commands for third-party media players (Spotify, YouTube Music).
  - **Security Guarantee**: Zero audio capture or transmission. Strictly metadata and control.
  - Off by default; requires explicit `canControlExternalSessions` device permission.
- **Status**: Completed, all tests pass, committed locally (`4fcc29c`).
