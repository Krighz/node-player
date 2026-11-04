# Node Connect Features Specification & Architecture Map

**Target Release:** v1.6.0  
**Status:** Feature Specification & Architecture Map  
**Security Baseline:** Simple Mode (Default) / Strict Mode (Opt-In)

---

## 1. Connectivity Features (C1 – C4)

| Code | Feature Name | What the User Sees | Messages & Protocol | Android Implementation | Windows Implementation | Risks & Mitigations | Settings Toggle & Defaults |
| :---: | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **C1** | **Multi-Method Discovery** | Discovered devices list with connection method badge (mDNS, Broadcast, Gateway, Subnet) and last-seen timer; "Scan Network" button. | UDP broadcast (`255.255.255.255:54821`, subnet `.255:54821`), Multicast (`239.255.77.88`), TCP gateway probe (`<gw>:54820`), Subnet scan (`/24`). Announcement payload: `NP_DISCOVER:v2:<id>:<name>:<platform>:<port>:<ips>`. | `WifiManager.MulticastLock`, `DatagramSocket`, `NetworkInterface.getNetworkInterfaces()`, candidate IP enumeration. | Java `NetworkInterface`, `DatagramSocket` across all non-loopback adapters, Powershell gateway query fallback. | Hotspots blocking multicast; Mitigated by UDP direct broadcast and Gateway probe. | **Master:** Discovery On/Off (Default: Off until turned on). Individual toggles for mDNS, Broadcast, Gateway Probe, Subnet Scan. |
| **C2** | **Connection & Session** | Connection status pill (Searching, Connecting, Connected, Disconnected); dynamic ping latency display. | TCP direct connection; `HELLO` exchange (`0x0001`): `deviceId`, `deviceName`, `platform`, `appVersion`, `supportedFeatures`; Heartbeat `PING` (`0x0002`) / `PONG` (`0x0003`) every 5s. Reconnect with exponential backoff (1s..15s). | Coroutine TCP socket client/server via `java.net.Socket`, `ServerSocket` / `SSLSocket`. Parallel connect to candidate IPs with 3s timeout. | Same multiplatform coroutine socket stack. Port binding `0.0.0.0:54820..54830`. | Network switches dropping sockets; Mitigated by 5s keepalive ping and automatic reconnection. | Auto-reconnect (Default: On); Keepalive interval (Default: 5s); Port (Default: 54820 auto). |
| **C3** | **Trust & Pairing** | "Allow <Device> to connect?" modal dialog with [Allow Once], [Always Allow], [Deny]. List of Trusted Devices with "Forget" button. | Simple Mode: `HELLO` accepted; if not trusted, prompts UI. In Strict Mode: `PAIRING_REQ` (`0x0010`) / `PAIRING_RESP` (`0x0011`) with OTT + 6-digit PIN. | `DeviceRegistry` backed by JSON file in `context.filesDir/device_registry.json`. | `DeviceRegistry` backed by `%APPDATA%/NodePlayer/device_registry.json`. | Untrusted peers on shared networks in Simple Mode; Mitigated by default Allow prompt and "Trust all devices" toggle off by default. | "Ask before allowing new devices" (Default: On); "Trust all devices on my network" (Default: Off); Security Level: Simple / Strict. |
| **C4** | **Foreground Service & Notification** | Persistent Android notification: "Node Connect Active • Connected to PC • 1ms". Windows system tray icon with status tooltip. | Internal lifecycle monitoring; IPC between `NodeConnectService` and UI. | Android `ForegroundService` with `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` / `SPECIAL_USE` and ongoing notification. | Java Desktop SystemTray integration (`java.awt.SystemTray`) with minimize-to-tray option. | Realme / OEM battery managers killing background services; Mitigated by foreground service notification and wake lock. | "Run in background" (Default: On); "Show persistent notification" (Default: On). |

---

## 2. User-Facing Features (F1 – F8)

| Code | Feature Name | What the User Sees | Messages & Protocol | Android Implementation | Windows Implementation | Risks & Mitigations | Settings Toggle & Defaults |
| :---: | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **F1** | **Remote Control** | Media player controls: Play/Pause, Next, Previous, Seek slider, Volume slider, Shuffle, Repeat. Operates in both directions. | `CONTROL_CMD` (`0x0020`): action, seekPositionMs, volume. `CONTROL_STATE` (`0x0021`): isPlaying, positionMs, durationMs, trackTitle, artist, volume, timestamp. Sent on state change + 1 Hz heartbeat. Receiver interpolates position locally. | `RemotePlaybackController` mapped to Android `ExoPlayer` / `PlaybackService`. | `RemotePlaybackController` mapped to Windows audio engine (`WasapiAudioEngine`). | Out-of-sync playback state; Mitigated by sequence IDs and 1 Hz synchronization heartbeat. | "Let others control this device" (Default: On); "Allow remote volume control" (Default: On). |
| **F2** | **Now Playing Mirror** | Live mirrored display of remote playback: track title, artist, album, small cached artwork thumbnail, playback quality pill (`FLAC 24-bit / 48kHz`). Display only. | `MIRROR_STATE` (`0x0022`): title, artist, album, duration, sampleRate, bitDepth, channels, mimeType. `ARTWORK_REQ` (`0x0023`) / `ARTWORK_RESP` (`0x0024`) chunked JPEG thumbnail (max 256x256). | `NodeConnectMirrorService` updating Compose UI state; thumbnail cached in LRU memory cache. | Same Compose Multiplatform mirror UI view in Now Playing sheet or deck. | High bandwidth usage from artwork transfer; Mitigated by downscaling thumbnails to 256x256 WebP/JPEG before transfer. | "Now Playing Mirror" (Default: On). |
| **F3** | **Library Browse & Search** | Remote library browser: paged artists, albums, tracks, and folders. Search tab with "Universal Search" fan-out searching both local and remote libraries. | `LIB_QUERY_REQ` (`0x0030`) / `LIB_QUERY_RESP` (`0x0031`): opaque track IDs (`node://track/<uuid>`), titles, artists, durations. Zero native file paths transmitted. | `NodeConnectLibraryService` querying local `Room` / SQLite `TrackRepository` and streaming paged results. | Querying Windows desktop track database and streaming paged responses over TCP. | Path traversal or sensitive filesystem exposure; Mitigated by opaque UUID addressing only. | "Share my library" (Default: On); "Universal search" (Default: On). |
| **F4** | **Audio Cast** | "Cast to…" device picker; receiver shows "Playing from <Device>" banner with [Play Here] button. Sender taps pre-DSP PCM and streams real-time audio. | `CAST_START` (`0x0040`), `CAST_FRAME` (`0x0041`), `CAST_PAUSE` (`0x0042`), `CAST_RESUME` (`0x0043`), `CAST_SEEK_FLUSH` (`0x0044`), `CAST_STOP` (`0x0045`). Frame header: sequenceId, timestampNs, sampleRate, channels, format. | Sender taps ExoPlayer audio processor buffer; Receiver plays via low-latency `AudioTrack` with `JitterBuffer` (50–300ms dynamic). | Sender taps pre-DSP WASAPI stream; Receiver plays via WASAPI shared mode sink with JitterBuffer. | Audio buffer underrun / jitter; Mitigated by dynamic jitter buffer, sequence tracking, and seek flush reset. | "Cast Audio Send" (Default: On); "Receive Audio Cast" (Default: On); Jitter buffer size (Default: 100ms). |
| **F5** | **Playback Handoff & Queue Push** | "Handoff to <Device>" button: seamlessly transfers active queue, exact track position, and EQ profile, then stops local playback. Context menu options: "Play Next on <Device>" and "Add to Queue on <Device>". | `HANDOFF_REQ` (`0x0050`) / `HANDOFF_ACK` (`0x0051`): trackUri/opaqueId, positionMs, queueList, activeEqPreset, sleepTimerRemainingMs. | Triggers local pause, packages current player state, sends payload, receiver resumes playback immediately. | Mirrors handoff handling in desktop playback queue and WASAPI engine. | Duplicate concurrent playback; Mitigated by atomic handoff ack before local player pause. | "Allow Playback Handoff" (Default: On); "Allow Queue Push" (Default: On). |
| **F6** | **Data & Settings Sync** | Sync screen with categories: Playlists & smart rules, Favourites, EQ profiles, Settings & Themes, Play counts & history, Lyrics offsets. Modes: [Send Now] and [Keep in Sync]. Preview diff and conflict resolution. | `SYNC_OFFER` (`0x0060`), `SYNC_DATA` (`0x0061`), `SYNC_ACK` (`0x0062`). Hybrid Logical Clocks (`HlcClock`), CRDT tombstones, 3-snapshot rollbacks. | `SyncEngine` handling serialization/deserialization into local database with transaction rollback. | Multiplatform sync logic shared directly in `core-network`. | Database corruption or accidental overwrite; Mitigated by atomic transactions, 3-snapshot undo history, and preview diffs. | Per-category sync toggles (Default: Playlists, Favourites, EQ On; Settings Off). Sync Mode: Manual / Keep in Sync. |
| **F7** | **Sleep Timer Sync** | Setting sleep timer on one device prompts or automatically syncs remaining sleep time to the connected device. | `SLEEP_TIMER_SYNC` (`0x0070`): remainingMs, action (SET/CANCEL). | Tied to Android sleep timer coroutine. | Tied to Desktop sleep timer coroutine. | Desynchronized timers; Mitigated by absolute timestamp expiration (`targetEpochMs`). | "Sync Sleep Timer" (Default: On). |
| **F8** | **Third-Party Media Sessions** | Displays Now Playing info for Spotify / YouTube Music from the remote device with Play/Pause/Skip controls. Zero audio capture guarantee. | `EXTERNAL_SESSION_EVENT` (`0x0080`): appName, trackTitle, artist, isPlaying. `EXTERNAL_SESSION_CMD` (`0x0081`): PLAY, PAUSE, NEXT, PREV. | Android `NotificationListenerService` / `MediaSessionManager` reading active token metadata only. | Windows System Media Transport Controls (`SMTC` / `GlobalSystemMediaTransportControlsSessionManager`). | OS permission rejection or background battery throttling; Mitigated by explicit user permission flow and graceful fallback. | "Third-Party Media Sessions" (Default: Off). |

---

## 3. Node Connect Settings Architecture & Structure

```
Settings > Node Connect
├── Master
│   ├── [Toggle] Enable Node Connect (Default: OFF until turned on)
│   ├── [Text] Device Name (e.g. "Satya's Phone")
│   ├── [Toggle] Make Device Discoverable (Default: ON when Node Connect is on)
│   └── [Toggle] Allow Incoming Connections (Default: ON)
├── Security & Trust
│   ├── [Dropdown] Security Level: Simple (Active) / Strict (Cryptographic Pinning)
│   ├── [Toggle] Ask Before Allowing New Devices (Default: ON)
│   ├── [Toggle] Trust All Devices on My Network (Default: OFF)
│   ├── [Toggle] Encryption: TLS (Default: ON) / Plain TCP (Debug)
│   └── [Button] Manage Trusted Devices (View, Edit Permissions, Forget)
├── Connection Methods
│   ├── [Toggle] mDNS Discovery
│   ├── [Toggle] UDP Broadcast Beacons
│   ├── [Toggle] Hotspot & Gateway Probing
│   ├── [Toggle] Parallel Subnet Scanning (/24)
│   ├── [Toggle] USB Debugging Link (ADB Forwarding - Advanced)
│   └── [Number] Port: Auto (54820..54830) or Fixed
├── Feature Permissions & Toggles
│   ├── [Toggle] Remote Control (Allow incoming / Allow outgoing)
│   ├── [Toggle] Share My Music Library
│   ├── [Toggle] Audio Cast (Send stream / Receive stream)
│   ├── [Toggle] Playback Handoff & Queue Push
│   ├── [Toggle] Now Playing Mirror
│   ├── [Toggle] Universal Library Search
│   ├── [Toggle] Sleep Timer Sync
│   ├── [Submenu] Sync Categories (Playlists, Favourites, EQ, Settings)
│   └── [Toggle] Third-Party Media Sessions
├── Advanced & Audio Tuning
│   ├── [Slider] Jitter Buffer Target: 100ms (50ms - 500ms)
│   ├── [Dropdown] Audio Cast Format: Native PCM (Default) / Opus 160kbps Fallback
│   ├── [Slider] Keepalive Ping Interval: 5s (2s - 30s)
│   └── [Toggle] Verbose Node Connect Logging (Always ON in Debug)
└── Diagnostic Tools (Connection Doctor)
    ├── Local Interfaces & Candidate IPs table
    ├── Reachability & Port status
    ├── [Button] Scan Network Now
    ├── [Button] Add Device by IP
    ├── [Button] Show My QR Code
    ├── [Button] Scan QR Code (Android Camera)
    ├── [Button] Cast Test Tone (440Hz Sine Wave)
    ├── [Button] Copy Debug Log
    └── [Button] Export Debug Log File
```
