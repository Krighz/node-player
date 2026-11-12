# Node Player — Milestone v2.2 (Phases 14–17) Progress & Technical Audit

**Milestone:** v2.2 Online Features — Lyrics, Login, Playlists & Spotify Transfer  
**Base Commit Range:** `1db4d00` (Phase 14) → `ce2a761` (Phase 17)  
**Current Branch:** `feature/liquid-dock`  
**Date:** November 10, 2026  
**Target Environments:** Android ART (API 26+) & Windows Desktop (JVM 17 / Compose Desktop)

---

## 1. Overview of Milestone v2.2

Milestone v2.2 builds on top of the v2.1 online streaming architecture (visionOS client streaming, 15-minute cache, home shelves, and lazy resolving). It introduces four core features:
1. **Phase 14**: Lyrics Engine & Apple Music-Style Liquid Glass Lyrics View (`1db4d00`)
2. **Phase 15**: YouTube Account Login & SAPISID Authentication (`77c1013`)
3. **Phase 16**: Playlists (Online Mode) & YouTube Account Playlists (`c19d4cf`)
4. **Phase 17**: Spotify Playlist Transfer (Metadata-Only with PKCE OAuth) (`ce2a761`)

---

## 2. Phase-by-Phase Breakdown: What Changed, Updated & Built

### Phase 14: Lyrics Engine & Apple Music-Style Lyrics View
*Commit: `1db4d00` | 14 files changed (+1,636 / -45 lines)*

- **What Was Done & Implemented:**
  - **Lyrics Domain Model (`core-model`)**: Updated `Lyrics.kt` and `LyricLine` to support per-word timestamps (`WordTiming`), end timestamps, and estimated-timing indicators.
  - **LRC Parser (`core-library`)**: Completely overhauled `LrcParser.kt` to handle plain text, line-synced LRC (`[mm:ss.xx]`), and enhanced syllable/word-synced LRC (`<mm:ss.xx>`). Includes fallback character estimation for lines lacking word tokens.
  - **Provider Architecture (`core-network` & `core-library`)**:
    - Created `LyricsProvider` interface in `core-library`.
    - Implemented `LrclibLyricsProvider` in `core-network` using LRCLIB API with exact signature match (`track_name`, `artist_name`, `album_name`, `duration`) and fuzzy search fallback.
    - Implemented `LyricsRepository` with persistent file-based disk caching per track ID (caching positive hits and "not found" negative hits).
  - **UI & Presentation (`core-ui` & `app-android`)**:
    - Built `AppleMusicLyricsView.kt` in `core-ui`: Apple Music-inspired liquid glass aesthetics, word-by-word progressive fill, dynamic scaling/blurring of non-active lines based on distance from current time.
    - Spring physics auto-scroll with upper-middle alignment, drag-to-pause with resume timeout, and tap-to-seek playback integration.
    - Added `LyricsOffsetDialog.kt` to allow users to adjust and persist per-track timing offsets in ±ms.
    - Integrated lyrics toggle button and full glass view into `NowPlayingSheet.kt`.
  - **Verification:** Unit tests added in `LrcParserTest.kt` and `LrclibLyricsProviderTest.kt`.

---

### Phase 15: YouTube Account Login & Authentication
*Commit: `77c1013` | 12 files changed (+979 / -44 lines)*

- **What Was Done & Implemented:**
  - **Authentication Core (`core-network`)**:
    - Added `YouTubeAuth.kt`: generates `SAPISIDHASH` tokens via SHA-1: `sha1("${timestamp}_${sapisid}_${origin}")` where origin is `https://music.youtube.com`.
    - Zero credential logging: log format strictly outputs header names, timestamp lengths, and status codes. Never logs cookies or raw tokens.
  - **Session Management (`app-android`)**:
    - Created `SecureSessionStore.kt`: encrypted storage backed by Android Jetpack Security `MasterKey` and `EncryptedSharedPreferences`.
    - Created `YouTubeAccountManager.kt`: stateful controller exposing `accountState: StateFlow<YouTubeAccount?>`, validating sessions on launch, and coordinating login/logout.
  - **Login UI (`app-android`)**:
    - Built `YouTubeLoginSheet.kt`: glass bottom sheet hosting an isolated `AndroidView(WebView)` for `https://accounts.google.com/ServiceLogin?service=youtube`.
    - `CookieManager` intercepts `SAPISID`, `SSID`, `SID`, `HSID`, and `APISID` cookies upon successful login and immediately clears WebView caches.
    - Integrated account header card into `SettingsScreen.kt` displaying user avatar, display name, handle, and a glass Logout action.
  - **InnerTube Integration**:
    - Added authenticated request pipeline to `InnerTubeClient.kt` attaching `Authorization: SAPISIDHASH ...` and `Cookie: ...`.
  - **Verification:** Unit tests in `YouTubeAuthTest.kt` verifying hash derivation, header construction, and parsing.

---

### Phase 16: Playlists (Online Mode) & YouTube Account Playlists
*Commit: `c19d4cf` | 17 files changed (+1,770 / -32 lines)*

- **What Was Done & Implemented:**
  - **Room Database Entities (`app-android`)**:
    - Added `PlaylistEntity` and `PlaylistTrackCrossRefEntity` to `NodeDatabase.kt` with index optimization.
    - Room DAOs supporting local-first playlist CRUD: create, rename, delete, add track, remove track, and reorder.
  - **Online Playlist UI**:
    - Created `PlaylistDetailScreen.kt`: glass playlist header, dynamic artwork aggregation from child tracks, play all, shuffle, reorder, and swipe-to-delete.
    - Created `AddToPlaylistSheet.kt`: quick-save modal accessible from track overflow menus and Now Playing sheet.
    - Added playlist filter chips (All, Local, YouTube, Spotify) to `LibraryScreen.kt`.
  - **YouTube Playlist Sync (`YouTubePlaylistImporter.kt` & `InnerTubeClient.kt`)**:
    - Implemented snapshot importer to read YouTube playlists and import metadata into local Room SQLite.
    - Added InnerTube playlist endpoints: `getPlaylist(playlistId)`, `getAccountPlaylists()`.
  - **Verification:** Unit tests in `YouTubePlaylistTest.kt` verifying playlist JSON response parsing and track item mapping.

---

### Phase 17: Spotify Playlist Transfer (Metadata-Only)
*Commit: `ce2a761` | 13 files changed (+1,802 / -3 lines)*

- **What Was Done & Implemented:**
  - **PKCE OAuth (`core-network` & `app-android`)**:
    - Added `SpotifyAuth.kt`: RFC 7636 PKCE Authorization Code flow (generates cryptographic `code_verifier`, SHA-256 `code_challenge`, and state tokens).
    - Custom deep link scheme `nodeplayer://spotify-callback` registered in `AndroidManifest.xml` and caught in `MainActivity.kt`.
    - Reads `spotify.client.id` safely from `local.properties` via `BuildConfig.SPOTIFY_CLIENT_ID`; displays graceful setup guide when missing.
    - Session tokens persisted securely in `SecureSessionStore.kt` with auto-refresh on token expiry.
  - **Spotify API Client (`core-network`)**:
    - Implemented `SpotifyClient.kt`: fetches user playlists via `GET /v1/me/playlists` and playlist tracks via `GET /v1/playlists/{id}/tracks` with pagination.
    - Strict compliance: metadata-only (title, artist, album, duration, ISRC). Zero audio access or DRM circumvention.
    - Rate limit resilience: exponential backoff handling HTTP 429 (`Retry-After`).
  - **Smart Matching Engine (`SpotifyTrackMatcher.kt`)**:
    - Primary pass: ISRC exact match against YouTube/InnerTube.
    - Fallback pass: Normalized string similarity (Levenshtein title/artist distance) + duration delta check (±5 seconds tolerance).
    - Classifies tracks into: `MATCHED`, `UNCERTAIN`, `NOT_FOUND`.
  - **Transfer UI (`app-android` & `core-ui`)**:
    - Created `SpotifyImportDialog.kt`: glass modal to select remote Spotify playlists.
    - Created `SpotifyTransferReviewScreen.kt`: interactive resolution screen allowing users to review matches, swap uncertain tracks, or exclude missing songs before importing to local Room database.
  - **Verification:** Unit tests in `SpotifyClientTest.kt` and `SpotifyTrackMatcherTest.kt`.

---

## 3. Known Bugs, Glitches & Architectural Challenges Encountered

Below is the complete analysis of issues uncovered during testing across Phases 14 through 17, and during the latest playlist sync experiments:

### Bug 1: Edge Proxy 400 Bad Request (`Origin doesn't match Host for XD3`)
- **Location:** `core-network/src/main/kotlin/com/nodeplayer/network/innertube/InnerTubeClient.kt`
- **What Happened:** When attempting to send a raw `Origin: https://music.youtube.com` header globally in Ktor's `defaultRequest`, Google's frontend proxy (XD3) rejected subsequent requests across the app (search, home shelves, and stream extraction) with `400 Bad request: Origin doesn't match Host for XD3`.
- **Status:** **Resolved by Reversion.** The global `Origin` header was removed. Convx reference confirms that InnerTube only requires `X-Origin: https://music.youtube.com` and `Referer: https://music.youtube.com/`.

### Bug 2: YouTube Account Playlists Empty Promo (`FEmusic_liked_playlists`)
- **Location:** `core-network/src/main/kotlin/com/nodeplayer/network/innertube/InnerTubeClient.kt`
- **What Happened:**
  - Querying `FEmusic_library_playlists` failed with HTTP 400 (deprecated browseId).
  - Switching to `FEmusic_liked_playlists` returned HTTP 200, but did not contain the user's created playlists. Instead, it returned an empty promo message: `{"runs": [{"text": "Looking for what you’ve liked?"}]}` because this browseId only tracks third-party playlists saved/liked by the user.
- **Status:** **Identified / Work In Progress.** The user's created playlists reside in `FEmusic_library_landing` under the playlists filter chip (`browseId: "FEmusic_library_landing"`, `params: "ggMPOg1wbGF5bGlzdHNfdWNm"`).

### Bug 3: `account/account_menu` 400 Bad Request (Invalid Argument)
- **Location:** `core-network/src/main/kotlin/com/nodeplayer/network/innertube/InnerTubeClient.kt`
- **What Happened:** Profile name and avatar fetching via `account/account_menu` returned `400 Bad Request (INVALID_ARGUMENT)`.
- **Root Cause:** The `Json` serializer configuration serialized optional null properties in `Context` (`visitorData: null`, `onBehalfOfUser: null`). YouTube's protobuf backend rejects explicit `null` fields.
- **Fix Required:** Add `explicitNulls = false` to the Ktor `Json` serializer instance.

### Bug 4: Two-Way Remote Playlist Mutations Not Yet Wired
- **Location:** `app-android/src/main/kotlin/com/nodeplayer/music/library/YouTubePlaylistImporter.kt`
- **What Happened:** While local playlist operations (create, add, delete in Room DB) work immediately, bi-directional mutations to remote YouTube Music (`browse/edit_playlist` with `ACTION_ADD_VIDEO` or `ACTION_REMOVE_VIDEO`, and `playlist/create`) need `setVideoId` tracking (`playlistSetVideoId`) to successfully delete items from YouTube.
- **Status:** Pending safe implementation without touching global Ktor headers.

---

## 4. Current Stability & Verification Status

| Component | Status | Verification Method |
|---|---|---|
| **Audio Playback Engine** | 🟢 **Stable** | ExoPlayer Opus 160k / AAC 128k playing without stalls on realme RMX3853 (Android 16). |
| **Home & Explore Feeds** | 🟢 **Stable** | 7 diversified shelves, 15-min in-memory cache, lazy artwork loading. |
| **LRCLIB Lyrics View** | 🟢 **Stable** | Word-fill animations, smooth auto-scroll, offset dialog tested. |
| **YouTube Account Login** | 🟡 **Partial** | WebView captures cookies & stores securely; account avatar fetch blocked by `explicitNulls`. |
| **Spotify Playlist Transfer** | 🟢 **Stable** | PKCE flow, metadata fetching, and ISRC matching verified with mock & live APIs. |
| **Local Room Playlists** | 🟢 **Stable** | Fast, local-first Room SQLite operations in Library and Add to Playlist sheet. |
| **YouTube Account Playlists** | 🟡 **In Progress** | Reverted to clean state to protect streaming; needs `FEmusic_library_landing` endpoint. |

---

## 5. Next Steps to Complete Milestone v2.2

1. **Fix `explicitNulls = false`** in `InnerTubeClient.kt` and re-test `account/account_menu` for avatar and name display.
2. **Implement `FEmusic_library_landing` with playlist chip params** to fetch custom user playlists.
3. **Add playlist mutation endpoints** (`create_playlist`, `browse/edit_playlist`) with action strings matching Convx (`ACTION_ADD_VIDEO`, `ACTION_REMOVE_VIDEO` with `setVideoId`).
4. **Compile, install, and run on Android device `4a505e5`** to confirm end-to-end functionality.
