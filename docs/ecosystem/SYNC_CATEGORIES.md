# Node Connect: Sync Categories & Transfer Rail Specification

**Module:** `:core-network`  
**Current Milestone:** v1.6.0  
**Transport:** `NodeConnectTransferService` (Chunked gRPC Stream, 256 KB Chunks with SHA-256)  

---

## 1. Category Definitions, Scopes & Transfer Rules

The Data Transfer Rail moves structured metadata, profiles, and state between paired nodes over an isolated channel. Each category has an independent per-device toggle and configurable direction (**Send**, **Receive**, or **Bidirectional**):

| Category | Schema Version | Default State | Direction Default | Conflict Resolution Strategy |
| :--- | :---: | :---: | :---: | :--- |
| **Playlists & Smart Rules** | `v1` | **Enabled** | Bidirectional | Item-level Hybrid Logical Clock (HLC) + tombstones for track removals. |
| **Favorites & Ratings** | `v1` | **Enabled** | Bidirectional | Monotonic state merge (favorited on any device = favorited). |
| **Play Counts & History** | `v1` | **Enabled** | Bidirectional | Append-only listening log merge with UUID deduplication. Total count = $\max(\text{count}) + \text{new unique plays}$. |
| **EQ & DSP Profiles** | `v1` | **Enabled** | Bidirectional | Named profiles merged; conflicting identical names prompt human confirmation ("Keep Mine", "Keep Theirs", "Keep Both as Copy"). |
| **Theme & Appearance** | `v1` | **Disabled** | Send / Receive | Last-write-wins per token key based on HLC timestamp. |
| **Synchronized Lyrics (.lrc)** | `v1` | **Enabled** | Bidirectional | Hash-based deduplication; preserves manual line-offset adjustments. |
| **Library Tag Edits** | `v1` | **Disabled** | Bidirectional | HLC timestamp per tag field (Title, Artist, Album, Genre, Year). |
| **Cached Audio Analysis** | `v1` | **Enabled** | Bidirectional | Keyed by 128-bit audio fingerprint: shares Hi-Res validity, EBU R128 LUFS, and DR crest score to avoid recomputation. |
| **Sleep Timer Preferences** | `v1` | **Disabled** | Bidirectional | Target end-time mapped to UTC timestamp. Follows initiator's clock. |
| **Active Queue** | `v1` | **Disabled** | One-time / Pull | Overwrites receiver's current queue after user confirmation. |
| **Full Backup (.nodebackup)** | `v1` | **Disabled** | Send Now / Pull | Verified `.nodebackup` payload streamed to destination storage. |

---

## 2. Settings Scopes (Shared vs. Per-Platform vs. Never Synced)

To prevent breaking platform-specific hardware configurations, settings are strictly partitioned into three scopes:

### A. Shared Settings (Synced when Theme & Appearance is enabled)
- Accent colors (`accentColor`, custom palette hex)
- Glass opacity, blur strength, grain texture, edge highlight
- Theme presets (*Obsidian Liquid*, *Frost Pearl*, *Neon Aurora*, etc.)
- Typography choice (*Inter*, *Outfit*, *JetBrains Mono*, *System*)
- Corner radius tokens (Sharp, Rounded, Pill)
- Motion tokens (Snappy, Smooth, Bouncy, Gentle) and speed multiplier
- For You mix count and card sizes

### B. Per-Platform Settings (Never Synced Across Different OS Types)
- **Audio Output Sinks**: WASAPI Shared / Exclusive toggles, WASAPI buffer size, DirectSound device IDs (Windows only).
- **AudioTrack Sinks**: Android audio offload, OpenSL ES / AAudio sink toggles (Android only).
- **System-Wide EQ**: Android foreground service audio session interception (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`).
- **Widgets**: Home-screen AppWidget layout, DPI scaling, RemoteViews toggles (Android only).
- **Window State**: Window geometry, maximize state, minimize-to-tray (Windows only).
- **File System Roots**: Concrete folder paths (`C:\Users\...`, `/storage/emulated/0/...`). Instead, directories are matched abstractly by folder basename.

### C. Never Synced (Strict Security Guard)
- Private cryptographic keys (`node_connect.p12`, Android Keystore keys)
- Pinned device certificates and pairing tokens
- Last.fm OAuth session keys and API secrets
- Android release signing keystores and passwords
- UPI IDs and financial credentials
- Local crash dumps, raw hardware logs, and memory traces

---

## 3. Track Content Fingerprinting

Because file paths vary radically between Android (`/storage/emulated/0/Music/Album/track.flac`) and Windows (`D:\Music\Album\track.flac`), tracks must be matched by content identity rather than file path.

### Fingerprint Formulation (Assumed Default)
The fingerprint is an efficient 128-bit composite hash:
$$\text{TrackFingerprint} = \text{Murmur3\_128}\Big( \text{Normalize}(\text{title}) \;\|\; \text{Normalize}(\text{artist}) \;\|\; \text{durationMs} \;\Big) \oplus \text{MD5}\Big( \text{First64KB} \;\|\; \text{Last64KB} \Big)$$

- **Zero Full-Disk Read Overhead**: Only 128 KB of audio stream data (64 KB header + 64 KB tail) is read. Large 100 MB 24-bit/96kHz lossless FLAC files are fingerprinted in $<1.5\text{ ms}$.
- **Unresolved Track Handling**: When a playlist is received containing tracks not present on the local machine, the entries are saved as dimmed **"Unresolved Tracks"** with their fingerprint. If matching files are imported later, they resolve automatically.

---

## 4. Safety Architecture: Snapshots & One-Tap Undo

Before applying any sync batch that modifies playlists, favorites, or settings:
1. **Local Pre-Sync Snapshot**: The Transfer service serializes a lightweight JSON snapshot of affected database rows into `%APPDATA%\NodePlayer\sync_snapshots\` (Windows) or `context.filesDir/sync_snapshots/` (Android).
2. **Rolling History**: Exactly 3 snapshots are preserved; older snapshots are purged automatically.
3. **One-Tap Undo**: In the **Sync Center**, an **"Undo Last Sync"** button restores the database state immediately if an unwanted sync occurred.

---

## 5. Conflict Resolution Model

1. **Hybrid Logical Clocks (HLC)**: Every synced entity carries an HLC timestamp $(L, C)$ combining wall-clock physical time with a monotonic logical counter, resolving clock drift between devices without requiring NTP synchronization.
2. **Tombstones for Deletions**: When a track is removed from a playlist, a tombstone record $(\text{trackId}, \text{timestamp}, \text{isDeleted}=\text{true})$ is stored and gossiped. This prevents a disconnected device from re-introducing deleted songs upon reconnecting.
3. **Listening Stats Union**: Play history records carry a UUID $\text{uuid} = \text{UUIDv5}(\text{trackFingerprint}, \text{startTimestampUs})$. Merging two history tables is a set union $(\mathcal{H}_A \cup \mathcal{H}_B)$, making duplicate syncs completely idempotent.

---

*Sync specification maintained by the Node Player Core Engineering Team.*
