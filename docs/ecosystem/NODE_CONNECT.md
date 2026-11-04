# Node Connect Protocol Specification (v1.0)

**Module:** `:core-network`  
**Current Milestone:** v1.6.0  
**Transport:** gRPC over TCP (default port 47200, dynamic fallback)  
**Security:** Mandatory Mutual TLS (mTLS) with Pinned Public Key Fingerprints  
**Network Boundary:** Strictly Local Area Network (Wi-Fi / LAN). Zero Cloud Sockets.  

---

## 1. Roles and Session Authority

Node Connect treats all connected devices (Android phone, Windows PC, future TV companion) as peer nodes within a secure local trust mesh. Each node can simultaneously assume multiple distinct roles:

| Role | Responsibility | Authority Model |
| :--- | :--- | :--- |
| **Audio Sender** | Decodes local audio, manages decoder position/sync, streams pre-DSP PCM/Opus chunks to receiver. | Owns track decoding clock, playback position, and stream pace. |
| **Audio Receiver** | Receives audio stream, manages jitter buffer (150–300 ms), applies local DSP/EQ (or sender's if requested), outputs to sound card. | Owns buffer consumption, hardware sink latency reporting, and drift correction. |
| **Library Server** | Indexes local media, generates search indices, serves metadata, track details, and artwork thumbnails. | Authoritative for its own local tracks and metadata. |
| **Library Client** | Queries remote library servers, merges search results with local catalog, requests playback via audio stream. | Reads remote catalogs; does not modify remote databases without explicit permission. |
| **Controller** | UI control surface: issues play, pause, seek, volume, queue modification commands. | Sends commands with monotonic sequence numbers to the Session Authority. |
| **Session Authority** | The specific device that currently owns the active queue, playback state, and sequence ordering. | Orders and executes playback commands; broadcasts state changes to all connected controllers. |
| **Transfer Peer** | Sends or receives non-audio sync data (playlists, favorites, stats, EQ profiles, theme files, backups). | Synchronizes data using Hybrid Logical Clocks (HLC) and conflict resolution rules. |
| **Mirror Target** | Passively displays real-time Now Playing metadata and visualizer from another device. | Read-only display; passes user interaction back to the remote authority. |

### Session Authority Resolution
- When Device A is playing its local queue, **Device A is the Session Authority**.
- When Device A casts audio to Device B ("Cast to Device B"), **Device A remains the Session Authority**, while Device B acts as the Audio Receiver.
- If the user initiates a **Playback Handoff** to Device B, Session Authority transfers to Device B: the current queue, track index, exact millisecond offset, and active EQ profile are handed over, and Device B takes ownership of playback.

---

## 2. Service Architecture & gRPC Channels

Node Connect uses distinct gRPC services over a single multiplexed HTTP/2 mTLS connection:

```
[Peer Node A] <======== Mutual TLS (HTTP/2 over TCP:47200) ========> [Peer Node B]
      │                                                                    │
      ├── NodeConnectSessionService        (Session lifecycle & heartbeat) ──┤
      ├── NodeConnectControlService        (Playback & queue remote control) ┤
      ├── NodeConnectLibraryService        (Browse, search, artwork thumb)   ┤
      ├── NodeConnectCastService           (Pre-DSP audio streaming)        ┤
      ├── NodeConnectTransferService       (Data sync rail & backup chunks)  ┤
      ├── NodeConnectMirrorService         (Remote display mirroring)        ┤
      └── NodeConnectExternalSessionsService (Third-party Spotify/YTM SMTC)  ┤
```

### Channel Separation Principles
1. **Control Channel (`NodeConnectControlService`)**: Low latency, lightweight protobuf RPCs for immediate transport commands (<10 ms round-trip).
2. **Audio Cast Channel (`NodeConnectCastService`)**: Dedicated streaming RPC (`StreamAudio`) with prioritized thread scheduling, fixed-interval chunks, and adaptive drift compensation.
3. **Data Transfer Channel (`NodeConnectTransferService`)**: Background, chunked data rail with bandwidth throttling on metered Wi-Fi, chunk resume, and SHA-256 integrity validation.
4. **Third-Party Sessions Channel (`NodeConnectExternalSessionsService`)**: Metadata and playback commands only. **Zero audio capture**, strictly isolated from the audio engine.

---

## 3. Message Flows

### A. Audio Sharing ("Cast without sharing the file")
*The sender decodes the audio file locally. The receiver receives only raw PCM or Opus frames directly into an in-memory ring buffer. No file bytes or temporary audio files are ever written to the receiver's disk.*

```
Sender (Device A)                                    Receiver (Device B)
       │                                                      │
       ├──── CastRequest(streamFormat, dspBypassPreferred) ──>│
       │<─── CastResponse(ACCEPTED, preferredBufferSize) ────┤
       │                                                      │
       │    [Loop: 20ms audio frames]                         │
       ├──── AudioChunk(seq, ptsUs, pcmBytes, isDiscontinuity)>│ (Buffered in 150-300ms
       ├──── AudioChunk(seq, ptsUs, pcmBytes, false) ────────>│  JitterBuffer)
       │                                                      │
       │    [User drags scrubber: Seek to 45.2s]              │
       ├──── AudioFlush(targetPtsUs = 45_200_000) ───────────>│ (Flushes buffer immediately)
       ├──── AudioChunk(seq+N, ptsUs=45.2s, pcmBytes, true) ─>│ (Resumes with discontinuity flag)
       │                                                      │
       │    [User taps "Play Here"]                           │
       ├──── StopCastRequest() ──────────────────────────────>│
       │<─── StopCastResponse(lastPlayedPtsUs) ───────────────┤ (Local playback resumes seamlessly)
```

#### Audio Stream Characteristics
- **Pre-DSP Stream**: By default, the sender streams decoded PCM before applying its own EQ, loudness, or reverb. The receiver applies its own local DSP profile calibrated to its connected speakers/headphones.
- **Sender DSP Mode**: An explicit toggle ("Use sender's sound settings") causes the sender to apply its DSP chain before transmission, streaming post-DSP PCM.
- **Drift Handling**: The receiver measures PTS clock drift relative to its audio output sink clock. If drift exceeds $\pm 30\text{ ms}$, band-limited sinc interpolation resamples frames by $\pm 0.05\%$ until phase alignment is re-established (inaudible, pitch-neutral).
- **Opus Fallback**: When Wi-Fi packet latency jitter exceeds $100\text{ ms}$ or packet loss exceeds $2\%$, the stream seamlessly negotiates a fallback to Opus 320 kbps streaming. UI clearly indicates `"Opus 320 kbps (Network Adaptive)"`.

---

### B. Library Server & Universal Search
- **Metadata Only**: Browsing a remote library returns `RemoteTrackItem` records containing metadata, duration, audio format, and a thumbnail URI token.
- **Thumbnail Streaming**: High-resolution album artwork is requested on-demand as downsampled WebP/JPEG thumbnails (256x256 or 512x512) and cached in an in-memory LRU cache.
- **Universal Search**: The top search capsule executes asynchronous searches across the local library and all connected remote libraries in parallel. Results are returned with a 1500 ms timeout and grouped with badges: `[This PC]`, `[Phone]`, `[Living Room]`.
- **Playback Trigger**: Clicking a remote track in search triggers a `StreamRemoteTrack` command; the remote device decodes the song and streams the audio back to the local device. The file is never transferred.

---

### C. Playback Handoff
- Moving a track mid-song to another device transfers the complete state:
  1. Sender pauses playback and captures exact timestamp $T$ (e.g. `124,530 ms`).
  2. Sender transmits `HandoffRequest(track, positionMs = T, queue, queueIndex, activeEqProfile)`.
  3. Receiver loads queue, seeks to $T$, applies matching EQ profile, and starts playback.
  4. Sender displays banner: `"Playing on <DeviceName>"` with `"Play Here"` button.

---

## 4. Error Handling, Network Drops & Reconnection

- **Heartbeat & Keepalive**: gRPC HTTP/2 PING frames every 5 seconds. If no response is received within 3 seconds, connection is marked `DEGRADED`.
- **Drop Detection**: If the connection breaks during an active audio cast:
  - Receiver immediately soft-fades out the remaining 100 ms of its jitter buffer (zero loud clicks or pops).
  - Receiver enters `RECONNECTING` state with a 10-second grace window, displaying `"Reconnecting to <DeviceName>..."`.
  - Sender pauses playback clock after 500 ms buffer underrun.
  - Upon reconnection, sender queries `GetPlaybackPosition()` and resumes streaming from the receiver's last rendered frame. If reconnection fails after 10 seconds, both devices cleanly return to idle state.
- **Idempotent Commands**: All transport and control RPCs carry client-generated UUIDs and monotonic sequence numbers to prevent duplicate execution during network retries.

---

## 5. Byte and Timing Specifications (Estimates & Configurable Limits)

All values below are configurable engineering thresholds labeled as estimates:

| Parameter | Default Value | Range / Options | Classification |
| :--- | :--- | :--- | :--- |
| **Default TCP Port** | `47200` | `47200 – 47250` (auto-scan next available) | Configurable |
| **mDNS Service Type** | `_nodeplayer._tcp.local.` | Fixed protocol identifier | Protocol Constant |
| **Audio Frame Interval** | `20 ms` | `10 ms` / `20 ms` | Engine Constant |
| **Initial Jitter Buffer** | `200 ms` | `100 ms – 500 ms` | Configurable |
| **Max Jitter Buffer** | `800 ms` | Upper limit under extreme congestion | Engine Guard |
| **Drift Correction Threshold** | `30 ms` | $\pm 10\text{ ms} – \pm 50\text{ ms}$ | Tuning Parameter |
| **Opus Fallback Bitrate** | `320 kbps` | `192 kbps` / `256 kbps` / `320 kbps` | Configurable |
| **Search Query Timeout** | `1500 ms` | `500 ms – 3000 ms` | Engine Guard |
| **Max Sync Chunk Size** | `256 KB` | Fixed transfer chunk limit | Memory Guard |
| **Max Metadata Payload** | `4 MB` | Enforced message size limit | Security Guard |
| **Keepalive Timeout** | `3000 ms` | `1000 ms – 5000 ms` | Protocol Constant |

---

*Specification maintained by the Node Player Core Network Engineering Team.*
