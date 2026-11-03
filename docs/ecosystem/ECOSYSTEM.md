# Node Player Ecosystem Integration

Version: Protocol v1 (Node Player 1.4.0)

## Overview
Node Player provides a stable, decoupled integration surface enabling external companion apps—such as the upcoming **Node Player Android TV app**, Wear OS controllers, and Android Auto/Assistant surfaces—to communicate seamlessly with the core audio engine.

## 1. Pure Kotlin Integration Contract (`:integration-api`)
The `:integration-api` module is a zero-dependency pure Kotlin library defining:
- `ProtocolVersion`: Semantic integer negotiation (Current: `1`, Min Supported: `1`).
- `ExternalTrackItem`: Serializable representation of metadata (id, title, artist, album, duration, artworkUri).
- `NowPlayingState`: Real-time immutable playback snapshot (currentTrack, isPlaying, positionMs, durationMs, shuffle, repeat).
- `PlaybackCommand`: Sealed command hierarchy (`PlayPause`, `Next`, `Previous`, `SeekTo`, `PlayTrack`).
- `IntegrationClientContract`: High-level interface for IPC adapters.

## 2. Media3 `MediaLibraryService` Browse Tree
AndroidX Media3 `MediaSessionService` in `PlaybackService` exposes the system media browser tree:
- **Root Node**: `[nodeplayer://library/root]`
- **Categories**:
  - `tracks`: Flat chronological or alphabetical song list.
  - `artists`: Grouped artist catalog with sub-albums.
  - `albums`: Album grid with thumbnail URIs.
  - `smart_playlists`: Dynamically generated local playlists (Recently Added, Most Played, Favorites).
- **Control Interface**: Standard Media3 `MediaController` connects automatically from Android TV, Wear OS, and Android Auto without custom binaries.

## 3. Data File Formats & Schemas
Extension points use versioned JSON data schemas:
- **Theme Format** (`ExtensionThemeMeta`): schema version 1, defining `accentWarmHex`, `accentCoolHex`, `backgroundBaseHex`, and `defaultGlassOpacity`.
- **EQ Profiles**: 10-band frequency gain mappings (`schemaVersion = 1`).
- **Smart Playlist Rules**: Filter criteria JSON format (`operator`, `field`, `value`).

## 4. Security & Permissions
- Inter-app data provider uses signature-level permissions (`android:protectionLevel="signature"`) so only companion apps signed with the same developer release key can perform administrative queries.
- Public deep links (`nodeplayer://play`, `nodeplayer://pause`, `nodeplayer://skip`) handle standard intent commands.
