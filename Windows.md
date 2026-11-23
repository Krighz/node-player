# Node Player — Windows Desktop vs. Android Feature Audit & Status

This document provides a comprehensive comparison between the **Android** (`com.nodeplayer.music` in `app-android`) and **Windows Desktop** (`app-desktop`) applications in the Node Player repository. It details all features present on Android that are absent on Windows, unique Windows desktop capabilities, and audits all unwired or incomplete implementations identified in the desktop module.

---

## 1. Architectural Overview

| Subsystem | Android (`app-android`) | Windows Desktop (`app-desktop`) |
| :--- | :--- | :--- |
| **UI Framework** | Jetpack Compose + Compose Multiplatform | Compose Multiplatform (Desktop / JVM 17) |
| **Audio Playback Engine** | AndroidX Media3 (ExoPlayer) + `AudioTrack` | `WindowsPlaybackEngine` + Custom Multi-Format Decoders |
| **Audio Output API** | Android `AudioTrack` (16/24/32-bit PCM float) | Windows WASAPI (JNA) — Shared & Bit-Perfect Exclusive Mode |
| **Database / ORM** | AndroidX Room ORM + SQLite | SQLite JDBC (`DesktopTrackDatabase`) |
| **Settings Storage** | AndroidX DataStore Preferences (`AppSettingsRepository`) | JSON Key-Value Store (`DesktopSettingsStore`) |
| **Glass Shaders** | Hardware AGSL (`liquid_glass.agsl`, Android 13+) + Skia fallback | Skia Multiplatform Graphics Shaders |
| **Media Controls** | Android `MediaSession` + Foreground Notification | In-app Compose Controls (No Windows SMTC yet) |
| **Background Service** | Android `MediaSessionService` + `WorkManager` | JVM Daemon Threads / Coroutine Scopes |

---

## 2. Features Present in Android (Missing in Windows Desktop)

### 2.1 System Integration & Media Lifecycle
1. **System Media Transport Controls (SMTC) & Media Keys**:
   - *Android*: Native `MediaSessionCompat` / Media3 session integrated with Android system lock screen, notification shade controls, volume rocker ducking, and hardware media buttons.
   - *Windows*: No Windows SMTC integration (Windows 10/11 volume flyout, lock screen controls, and global media keys `VK_MEDIA_PLAY_PAUSE`, `VK_MEDIA_NEXT_TRACK`, `VK_MEDIA_PREV_TRACK` when minimized or unfocused do not trigger playback actions).
2. **Audio Focus & System Ducking**:
   - *Android*: Implements `AudioManager.OnAudioFocusChangeListener` to pause or duck playback when receiving phone calls, GPS prompts, or notifications, and handles `ACTION_AUDIO_BECOMING_NOISY` on headset disconnect.
   - *Windows*: Desktop has no audio focus management or automatic pause on headphone unplug.
3. **Bluetooth AVRCP Metadata & State Broadcasting**:
   - *Android*: Automatically broadcasts track title, artist, album, duration, and position over Bluetooth AVRCP to car head units and wireless headphones.
   - *Windows*: Lacks Bluetooth AVRCP transport integration.
4. **Android Home Screen App Widgets**:
   - *Android*: Features interactive home screen widgets (4x1 compact pill, 4x2 hero with album art and seekbar, and 2x2 square) built with Glance and RemoteViews.
   - *Windows*: No Windows desktop gadget, Live Tile, or system tray mini-controller.
5. **System Equalizer / DSP Intent Broadcasting**:
   - *Android*: Broadcasts `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` so system equalizers (Wavelet, Poweramp Equalizer, Dolby Atmos) can attach to the audio session ID.
   - *Windows*: System equalizer routing not applicable.

### 2.2 Playback Pipeline & DSP Features
6. **Gapless Dual-Player Crossfade Engine**:
   - *Android*: `CrossfadePlayerManager` / `CrossfadeCoordinator` runs dual ExoPlayer instances with mathematically verified equal-power sine/cosine crossfade curves and configurable lead-in durations.
   - *Windows*: Single `WindowsPlaybackEngine` instance with sequential playback. Does not yet implement dual-stream crossfading.
7. **Volume Normalization & ReplayGain Engine**:
   - *Android*: Integrated `VolumeNormalizationProcessor` and `ReplayGainAudioProcessor` reading track loudness tags (`REPLAYGAIN_TRACK_GAIN`, EBU R128 LUFS) to balance volume levels dynamically.
   - *Windows*: ReplayGain settings exist in UI models, but `WindowsPlaybackEngine` does not yet apply gain adjustments to the PCM float buffer.

### 2.3 Background Processing & Offline Downloader
8. **Background Track & Video Downloader**:
   - *Android*: Features `DownloadQueueManager` and `DownloadService` powered by `androidx.work.WorkManager` for background downloading of YouTube Music audio and video streams, complete with automated ID3v2/Vorbis metadata embedding, embedded artwork tagging, and notification progress.
   - *Windows*: Has `DesktopAudioCache` for on-the-fly streaming cache, but lacks a dedicated offline download manager and download queue UI.
9. **Automated Background Audio Analysis**:
   - *Android*: Runs WorkManager workers to compute EBU R128 loudness, Dynamic Range (DR) scores, and spectrogram frequency cutoffs in the background.
   - *Windows*: Has DSP analysis code in `:core-dsp`, but no background scheduler or automated library-wide analysis queue.

### 2.4 Ecosystem & External Integrations
10. **Wear OS Smartwatch Companion**:
    - *Android*: Implements `integration-api` contracts for Wear OS companion apps (remote playback control, volume, playlist browsing from the wrist).
    - *Windows*: Not applicable / Not present.
11. **Android Auto & Android TV Support**:
    - *Android*: Features Android Auto media browser service and Android TV leanback interfaces.
    - *Windows*: Not applicable.
12. **CameraX QR Code Scanning for NodeConnect**:
    - *Android*: Instant pairing via live camera QR code scanner.
    - *Windows*: Desktop displays the QR code and IP/PIN for devices to scan, but cannot scan a peer's QR code via webcam.
13. **Google / YouTube Music Account Login**:
    - *Android*: Embedded OAuth/WebView login flow to synchronize private playlists, liked songs, and subscription shelves directly from YouTube Music.
    - *Windows*: Lacks an embedded webview authentication flow; currently relies on public InnerTube endpoints or synchronization from Android via NodeConnect.

---

## 3. Features Exclusive to Windows Desktop (Missing in Android)

1. **WASAPI Bit-Perfect Exclusive Mode**:
   - Windows desktop can bypass the Windows Audio Engine mixer entirely via JNA `WasapiAudioOutput`, driving USB DACs at their native sample rates (44.1 kHz, 96 kHz, 192 kHz, 384 kHz) with zero OS resampling or jitter.
2. **Discord Rich Presence Integration**:
   - Desktop communicates directly with the local Discord desktop client via native Windows Named Pipes (`\\.\pipe\discord-ipc-0`) to display rich status: track name, artist, album art, elapsed time, and duration.
3. **Integrated FFmpeg Audio Batch Transcoder**:
   - `DesktopFFmpegTranscoder` and `TranscodeQueueManager` allow users to select lossless files (FLAC, WAV) and batch transcode them into MP3, AAC, Opus, ALAC, or WAV with custom bitrates.
4. **VLCJ Native Desktop Video Surface**:
   - Uses `uk.co.caprica:vlcj` to embed high-performance hardware-accelerated video playback inside Compose Desktop windows.
5. **Direct ADB USB NodeConnect Link**:
   - Automatically detects connected Android phones via `adb forward` to establish a zero-latency wired TCP channel for NodeConnect casting and sync.
6. **Desktop Keyboard Hotkeys**:
   - Space (Play/Pause), Left/Right Arrows (Seek ±5s), Shift+Arrows (Next/Prev track), Up/Down Arrows (Volume ±5%), M (Mute), Ctrl+F (Search), Ctrl+1/2/3 (Tab navigation).

---

## 4. Audit of Unwired & Incomplete Issues in `app-desktop`

During the codebase inspection of `app-desktop` and its interaction with `:core-ui`, the following unwired or stubbed functionalities were identified:

### 4.1 Database & Repository Stubs (`DesktopTrackDatabase.kt` & `DesktopTrackRepositoryAdapter.kt`)
- **Favorites Persistence**: `setFavorite(id, isFav)` in `DesktopTrackRepositoryAdapter` was an empty stub `{}`. `DesktopTrackDatabase` did not expose a `setFavorite` update query.
- **Play Count Tracking**: `incrementPlayCount(id)` and `recordTrackPlayed(id, timestamp)` were empty stubs `{}`. Play counts remained 0 on desktop.
- **Playlist Management**: `DesktopTrackDatabase` only created a `tracks` table. Methods `getAllPlaylists`, `insertPlaylist`, `renamePlaylist`, `deletePlaylist`, `getTracksForPlaylist`, `addTrackToPlaylist`, `removeTrackFromPlaylist`, and `reorderPlaylistTracks` were hardcoded stubs returning empty lists or doing nothing.
- **Audio Analysis Storage**: `updateTrackAudioAnalysis(...)` was an empty stub `{}`.

### 4.2 Playback Engine Issues (`WindowsPlaybackEngine.kt`)
- **Shuffle Ignored in `next()`**: While `toggleShuffle()` toggled `_isShuffle.value`, the `next()` method only advanced linearly via `currentQueueIndex + 1`, never consulting `_isShuffle`.
- **Playback Tracking**: `WindowsPlaybackEngine` did not notify the database when a track finished or started, so play history and play count never incremented.
- **Dynamic Track Updates**: Engine lacked a method to update the in-memory `_currentTrack` metadata (e.g., when the user favorites the active song).

### 4.3 Desktop Main & UI Wiring (`Main.kt` & `SharedPlayerLayout.kt`)
- **Playback Settings Not Persisted**:
  - `crossfadeSeconds`, `gaplessEnabled`, `replayGainMode`, and `lastFmEnabled` were not wired to `DesktopSettingsStore` in `Main.kt`, remaining at default constants.
  - Playback volume, shuffle state, and repeat mode were not saved across app restarts.
- **Now Playing Discovery Cards Unwired**:
  - `Main.kt` did not fetch `TrackExtendedInfo` when `currentTrack` changed.
  - `SharedPlayerLayout.kt` did not receive or pass `trackExtendedInfo` to `NowPlayingView`, leaving the 4 Spotify-style discovery cards (Credits, About Artist, More by Artist, More like this) unpopulated on desktop.
- **Volume & Controls in Fullscreen Player**:
  - `NowPlayingView` in `SharedPlayerLayout.kt` did not have `volume` or `onVolumeChange` wired, defaulting to 0.75f.
  - Favorite star toggle in `NowPlayingView` only toggled internal local state and was not bound to repository callbacks.
- **Empty Cast Callback on Deck**:
  - `BottomPlayerDeck`'s `onCastClick` parameter was assigned an empty lambda `{ /* open cast sheet */ }` instead of invoking `onPairNewDevice()`.
- **Artist Navigation in Context Menu**:
  - `TrackActionId.GO_TO_ARTIST` in `SharedPlayerLayout.kt` switched the tab to `LIBRARY` but did not pass the artist query to filter the library.

---

## 5. Recommended Parity Roadmap

1. **Database & Repository Completeness**:
   - Implement `playlists` and `playlist_tracks` tables in `DesktopTrackDatabase`.
   - Wire `setFavorite`, `incrementPlayCount`, and playlist operations in `DesktopTrackRepositoryAdapter`.
2. **Playback Engine Polish**:
   - Add shuffle index randomization to `WindowsPlaybackEngine.next()`.
   - Restore volume, shuffle, and repeat modes from `DesktopSettingsStore`.
   - Attach play count incrementing to track start / completion events.
3. **UI & Discovery Wiring**:
   - Wire `trackExtendedInfo` in desktop `Main.kt` via `InnerTubeClient` with in-memory caching.
   - Pass `trackExtendedInfo`, `volume`, `onVolumeChange`, and `onToggleFavorite` into `SharedPlayerLayout` -> `NowPlayingView`.
   - Connect `onCastClick` on `BottomPlayerDeck` to open the NodeConnect pairing dialog.
4. **Windows System Media Transport Controls (SMTC)**:
   - Implement a JNA wrapper for Windows 10/11 `SystemMediaTransportControls` (`ISystemMediaTransportControls`) to provide OS volume overlay, lock screen controls, and global media keyboard shortcuts.
