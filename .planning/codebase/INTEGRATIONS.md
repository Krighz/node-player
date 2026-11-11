<!-- refreshed: 2026-11-11 -->
# External Integrations

**Analysis Date:** 2026-11-11

## APIs & External Services

**Music Scrobbling & Metadata:**
- **Last.fm 2.0 Audioscrobbler API** - Track scrobbling, live "Now Playing" status updates, and session token authentication (`auth.getToken`, `auth.getSession`, `track.updateNowPlaying`, `track.scrobble`).
  - SDK/Client: Handcrafted HTTP REST client using standard Java `HttpURLConnection` and custom MD5 API signature computation (`com.nodeplayer.library.LastFmSigner` in `core-library/src/main/kotlin/com/nodeplayer/library/LastFmSigner.kt`, `app-android/src/main/kotlin/com/nodeplayer/music/library/LastFmClient.kt`).
  - Auth: API Key (`LASTFM_API_KEY`) and Shared Secret (`LASTFM_API_SECRET`) injected via `local.properties`, `app-android/lastfm.properties`, or in-app Settings UI (`custom_lastfm_api_key`, `custom_lastfm_api_secret`).
  - Offline Resilience: Persistent offline queue backed by Room database table `scrobble_queue` (`app-android/src/main/kotlin/com/nodeplayer/music/data/NodeDatabase.kt`), retrying scrobbles once connectivity resumes.

**Software Updates:**
- **GitHub Releases API** - In-app version checking and APK updates against the public releases repository endpoint (`https://api.github.com/repos/Krighz/node-player/releases/latest`).
  - SDK/Client: Direct `HttpURLConnection` JSON parsing (`app-android/src/main/kotlin/com/nodeplayer/music/update/UpdateChecker.kt`).
  - Download & Verification: `app-android/src/main/kotlin/com/nodeplayer/music/update/UpdateDownloadManager.kt` downloads APK assets over HTTPS (strictly verified against `https://github.com/Krighz/node-player/releases/download/` and `https://objects.githubusercontent.com/`) and enforces SHA-256 checksum verification before triggering Android package installation.
  - Auth: None (public read-only GitHub API).

**YouTube Music / InnerTube Streaming API:**
- **InnerTube API & YouTube Stream Extraction** - Real-time YouTube Music streaming resolution, recommendations, home feeds, explore shelves, and playlist sync (core-network/src/main/kotlin/com/nodeplayer/network/recommender/, core-network/src/main/kotlin/com/nodeplayer/network/search/, pp-android/src/main/kotlin/com/nodeplayer/music/library/YouTubePlaylistImporter.kt).
  - Auth: Optional user SAPISID cookie login managed via YouTubeAccountManager.kt (pp-android/src/main/kotlin/com/nodeplayer/music/auth/YouTubeAccountManager.kt) and YouTubeLoginSheet.kt.
  - Stream Resolution: VisionOS & iOS client clientSpoofing with visitorData to prevent 403 playback cutoff.

**Spotify Music Migration:**
- **Spotify Web API & PKCE OAuth** - Playlist import, user playlist retrieval, and library migration (core-network/src/main/kotlin/com/nodeplayer/network/spotify/SpotifyClient.kt).
  - Matching Engine: ISRC-first matching with fuzzy Levenshtein and duration-distance fallbacks (SpotifyTrackMatcher.kt).
  - Auth: Pure PKCE OAuth flow with SHA-256 code challenge (SpotifyAuthManager.kt).

**Synchronized Lyrics:**
- **LRCLIB API** (https://lrclib.net) - Online synchronized and plain lyrics fetching (core-network/src/main/kotlin/com/nodeplayer/network/lyrics/LrcLibClient.kt), integrated seamlessly into AppleMusicLyricsView.kt and FullScreenLyricsView.kt.

**Synchronized Lyrics (Deferred/Opt-in Spec):**
- **LRCLIB API** (`https://lrclib.net`) - Public lyrics database specification for timed `.lrc` lines.
  - Current Status: Network queries deferred to maintain 100% offline-by-default architecture; local synchronized `.lrc` file parsing is handled offline via `core-library/src/main/kotlin/com/nodeplayer/library/LrcParser.kt` and `LyricsManager`.

**Local P2P Network Protocol:**
- **Node Connect Protocol** - Custom peer-to-peer binary protocol connecting Android and Windows instances over the local subnet without any cloud brokers or external servers (`core-network/`).
  - Network Architecture: Zero-cloud guarantee. All discovery, pairing, audio streaming, control commands, and library queries happen directly on the local subnet (Wi-Fi, hotspot, or USB).
  - Discovery Mechanisms:
    - UDP Broadcast on subnet interfaces and `255.255.255.255:54821`
    - UDP Multicast on `239.255.77.88:54821`
    - Direct Gateway probing for Wi-Fi hotspot links
    - Subnet /24 sweep fallback
    - Deterministic USB ADB port forwarding bridge via `adb forward tcp:54825 tcp:54820` and `adb reverse tcp:54826 tcp:54820` (`core-network/src/main/kotlin/com/nodeplayer/network/discovery/AdbBridge.kt`)
  - Transport Layer:
    - Default Port: `54820` (dynamic port fallback up to 10 ports)
    - Simple Mode: TLS 1.3 / 1.2 with opportunistic self-signed certificate acceptance or optional plain TCP.
    - Strict Mode: Mutual TLS (mTLS) with strict X.509 EC-256 certificate pinning via `PinningTrustManager` and 6-digit dynamic pairing PIN.
  - Protocol Framing: Custom binary framing (`MAGIC = "NODE"`, 1-byte version, 2-byte type, 1-byte flags, 8-byte sequence ID, 4-byte payload length, CRC32 checksum) handling multiplexed control planes, real-time PCM audio casting, and library replication (`core-network/src/main/kotlin/com/nodeplayer/network/protocol/NodeFraming.kt`).

## Data Storage

**Databases:**
- **Android - Room (SQLite):**
  - Database name: `node_player.db` (Schema version 7)
  - Tables: `tracks`, `playlists`, `playlist_track_cross_ref`, `audio_presets`, `smart_playlists`, `daily_listening_stats`, `scrobble_queue`, `scanned_folders`
  - Location: Private application data directory (`app-android/src/main/kotlin/com/nodeplayer/music/data/NodeDatabase.kt`)
- **Windows Desktop - SQLite (JDBC):**
  - Database URL: `jdbc:sqlite:%APPDATA%\NodePlayer\library.db`
  - Driver: `org.xerial:sqlite-jdbc:3.45.1.0`
  - Tables: `tracks` (metadata, format info, favorites, play count) (`app-desktop/src/main/kotlin/com/nodeplayer/desktop/data/DesktopTrackDatabase.kt`)

**File Storage:**
- **Local filesystem only:**
  - Android: Local media files scanned from `MediaStore.Audio.Media` and direct user-selected directory trees (`app-android/src/main/kotlin/com/nodeplayer/music/data/FolderScanner.kt`).
  - Windows Desktop: Local music folders scanned recursively via standard file I/O (`app-desktop/src/main/kotlin/com/nodeplayer/desktop/data/WindowsFileScanner.kt`).
  - Desktop Settings: JSON-based key-value configuration stored at `%APPDATA%\NodePlayer\settings.json` (`app-desktop/src/main/kotlin/com/nodeplayer/desktop/data/DesktopSettingsStore.kt`).

**Caching:**
- In-memory artwork thumbnail cache (`com.nodeplayer.ui.components.ArtworkCache` in `core-ui`)
- Coil disk and memory image cache for Android cover art (`io.coil-kt:coil-compose`)

## Authentication & Identity

**Auth Provider:**
- **Custom Local-First Node Connect Identity:**
  - Zero external identity providers or cloud accounts.
  - Uses self-generated Elliptic Curve secp256r1 (EC-256) keypairs with pure Kotlin self-signed X.509 certificate generation (`core-network/src/main/kotlin/com/nodeplayer/network/crypto/KeyManager.kt`).
  - Devices are identified by their unique SHA-256 certificate fingerprint (`computeSha256Fingerprint`).
  - Pairing verification performed out-of-band via QR code exchange or dynamic 6-digit numeric PINs.
- **Last.fm Authentication:**
  - Web authentication handshake: queries `auth.getToken`, opens `http://www.last.fm/api/auth/?api_key={key}&token={token}` in browser, and exchanges token for session key via `auth.getSession` (`app-android/src/main/kotlin/com/nodeplayer/music/library/LastFmClient.kt`).
  - Session keys are stored locally in private `SharedPreferences` / DataStore.

## Monitoring & Observability

**Error Tracking:**
- **None (Zero third-party crash reporting SDKs):**
  - No Crashlytics, Sentry, Bugsnag, or remote error reporting.
  - In line with strict privacy guarantee, zero analytics or telemetry data leaves user devices.

**Logs:**
- **Custom Structured In-Memory Logger (`NodeLog`):**
  - Central logging abstraction in `core-common/src/main/kotlin/com/nodeplayer/common/logging/NodeLog.kt`.
  - Routes to `android.util.Log` on Android and standard console output on Desktop.
  - Sanitized on-device diagnostics export: `app-android/src/main/kotlin/com/nodeplayer/music/diagnostics/DiagnosticsDumpManager.kt` provides privacy-filtered log dumps with regex redacting of sensitive tokens, usernames, and file paths.

## CI/CD & Deployment

**Hosting:**
- **GitHub Pages:** Static project website hosted at `https://krighz.github.io/node-player/` deployed from `site/` directory (`.github/workflows/pages.yml`).
- **GitHub Releases:** Release binaries, APK artifacts, and SHA-256 checksums distributed via `https://github.com/Krighz/node-player/releases`.

**CI Pipeline:**
- **GitHub Actions (`.github/workflows/ci.yml`):**
  - Runs on `ubuntu-latest` with JDK 17 (`actions/setup-java@v4` temurin).
  - Triggers on push and pull requests targeting branch `main`.
  - Executes DSP unit tests: `./gradlew :core-dsp:test --stacktrace`.
  - Builds debug APK: `./gradlew assembleDebug --stacktrace`.
  - Uploads debug APK build artifacts (`actions/upload-artifact@v4`).

## Environment Configuration

**Required env vars:**
- Development / CI:
  - `ANDROID_HOME` or `ANDROID_SDK_ROOT`: Path to Android SDK.
  - `JAVA_HOME`: JDK 17 root directory.
- Release signing (Optional - falls back to debug or local keys):
  - `KEYSTORE_FILE`: Absolute path to Android keystore file.
  - `KEYSTORE_PASSWORD`: Keystore decryption password.
  - `KEY_ALIAS`: Key alias name.
  - `KEY_PASSWORD`: Key password.
  - `NODEPLAYER_SIGNING_PROPS`: Optional path to signing properties file.
- Optional Service Credentials:
  - `LASTFM_API_KEY`: API key for Last.fm Audioscrobbler.
  - `LASTFM_API_SECRET`: Shared secret for Last.fm Audioscrobbler.

**Secrets location:**
- Local development secrets: `local.properties` (gitignored), `app-android/lastfm.properties` (fallback), or `~/Documents/node-player-signing/signing.properties`.
- In-app secrets: User-entered API tokens are stored in Android private `SharedPreferences` / DataStore.
- CI secrets: Configured via GitHub Actions repository secrets if signing in automation.

## Webhooks & Callbacks

**Incoming:**
- **None:** The application is a client/peer-to-peer desktop and mobile media player; no HTTP server exposes public incoming webhook endpoints.

**Outgoing:**
- **Last.fm Audioscrobbler Events:** Outgoing HTTP POST requests sent on track start (`track.updateNowPlaying`) and track completion threshold (`track.scrobble`) if opted in.
- **GitHub Release Checks:** Periodic unmetered HTTP GET requests to `https://api.github.com/repos/Krighz/node-player/releases/latest` if update checking is enabled.
- **Node Connect Local Sockets:** Event-driven binary message packets broadcast or streamed across local TCP/TLS sockets for control, sync, and casting (`core-network/src/main/kotlin/com/nodeplayer/network/protocol/NodeFraming.kt`).

---

*Integration audit: 2026-11-05*
