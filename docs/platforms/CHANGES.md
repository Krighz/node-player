# Platform Implementation Changes (v2.2.0)

**Branch:** features-Peak  
**Base Release:** v2.1.0  

---

### [2026-11-26] - Major Milestone: Merging with Auralis & v2.2.0 Release
- **Official Merger with Auralis (`https://github.com/voidXatrix-zudz/Auralis`)**:
  - Announced the landmark merger between Node Player and Auralis.
  - Combines Node Player's 32-bit floating-point bit-perfect audio DSP, custom AGSL liquid glass shaders, 5-provider synchronized word-by-word karaoke lyrics, and Google Drive cloud sync with Auralis's next-generation music player platform.
  - Added in-app merger announcement banner in `SettingsScreen.kt` linking directly to the Auralis GitHub repository.
- **Interactive 3D Gyroscope Artwork Tilt Parallax (`AndroidGyroscopeTilt.kt`, `NowPlayingView.kt`, `CustomizeUiView.kt`)**:
  - Added sensor-driven 3D pitch and roll tilt for hero album artwork using Android's `Sensor.TYPE_ROTATION_VECTOR` (with accelerometer fallback).
  - Dynamically rotates artwork on `rotationX` and `rotationY` with `cameraDistance` projection and shifts a specular liquid glass reflection sheen across the surface.
  - Defaults to **OFF** (`gyroscopeParallaxEnabled = false`) with zero sensor registrations or background drain until enabled in **Settings > Customize UI > Spring Dynamics & Motion**.
- **Tactile Haptic Feedback Across the Player (`NodeHaptics.kt`, `MainActivity.kt`, `CustomizeUiView.kt`)**:
  - Centralized `NodeHaptics` and `rememberNodeHaptics` controller respecting `themeConfig.hapticsEnabled`.
  - Added tactile haptic ticks and impacts on tapping tracks to play, adding tracks to queue (`addToQueue`), playing next (`playNext`), scrubbing seekbars, and switching navigation tabs.
- **Lyric Card Typography & Selected Font Synchronization (`LyricCardView.kt`, `FullScreenLyricsView.kt`)**:
  - Synchronized `LyricCardGeneratorDialog` with the user's selected full-screen lyrics typography.
  - Passed `activeLyricsFontFamily` directly into `LyricCardGeneratorDialog` and `LyricCardView`, ensuring the exact font chosen in Full-Screen Lyrics Settings (Caveat, Lobster, Bebas Neue, Outfit, Inter, etc.) is preserved in exported lyric cards.
- **NowPlaying Swipe-Down Double-Dismiss Glitch (`MainActivity.kt`)**:
  - Eliminated premature `sheetDragOffsetY.snapTo(0f)` calls inside drag and close handlers that previously reset sheet translation back to 0 at the exact moment dismissal exit animations triggered.
  - Guarded `AnimatedVisibility` exit with distance checks (`sheetDragOffsetY.value > screenHeightPx * 0.5f`), replacing redundant double-animations with an instantaneous `fadeOut(tween(120))` when already dismissed to the screen bottom.
  - Reset `sheetDragOffsetY` cleanly via `LaunchedEffect(isNowPlayingExpanded)` only when opening the sheet.
- **Playlist Card Circling Loader & Popping Transitions (`OnlineHomeView.kt`, `MainActivity.kt`)**:
  - Added immediate tactile press feedback with `.pressScale(onClick = onClick)` on `HomePlaylistCard`.
  - Added loading indicator state `loadingPlaylistId` rendering a high-contrast circular progress loader over the clicked card's artwork with a smooth semi-transparent scrim while fetching tracks.
  - Replaced abrupt playlist detail snapping with a physical iOS-inspired spring scale and fade entrance: `scaleIn(spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = 0.90f) + fadeIn(tween(220))`.
- **Scroll Jitter & Frame-Drop Elimination (`MainActivity.kt`)**:
  - Replaced continuous per-pixel recompositions from `dockScrollController.offsetFraction.collectAsState()` in `MainBottomDockOverlay` with discrete threshold evaluation `dockScrollController.isCollapsed.collectAsState()`.
  - Driven dock offset smoothly via `animateFloatAsState(animationSpec = spring(stiffness = 380f, dampingRatio = 0.82f))`, eliminating 60–120 FPS backdrop shader recompositions during flings.
- **Interactive Mini-Player Gesture Physics (`ConvxMiniPlayer.kt`)**:
  - Wired `Modifier.draggable` directly to an `Animatable(0f)` with rubber-band gesture damping (`delta * 0.65f`) and real-time translation on `graphicsLayer`.
  - On gesture release, mini-player triggers playback actions (previous/next) or snaps back into place using natural spring recoil physics.
- **Queue & Playback Sheet Transitions (`PlaybackOptionsSheet.kt`)**:
  - Upgraded sheet backdrop with smooth `fadeIn(tween(180))` / `fadeOut(tween(150))`.
  - Upgraded sheet container with `slideInVertically(spring(stiffness = 380f, dampingRatio = 0.82f))`.
  - Stabilized queue `LazyColumn` item keys with `Modifier.animateItemPlacement(spring(stiffness = 380f, dampingRatio = 0.82f))`.
- **System-Wide Optimization & Animation Audit (`docs/audit/OPTIMIZATION_AND_ANIMATION_AUDIT.md`)**:
  - Comprehensive audit of Android core, UI layers, DSP engine, and background tasks.
  - Detailed catalog of missing animations, frame-drop triggers, and modern Compose Multiplatform animation library research (`morph-compose`, CMP 1.7.3 `SharedTransitionLayout`, `compottie`, and `convx` spring configs).

---

### [2026-11-23] - Opt-In Privacy-First Cloud Backup & Sync (Google Drive appDataFolder)
- **Zero Developer-Hosted Servers (Client-Direct Architecture)**:
  - Backs up and syncs music metadata directly to the user's personal Google Drive account.
  - Opt-in only: completely disabled by default until the user explicitly signs in.
- **Strict Scope Isolation (`appDataFolder`)**:
  - Authenticates via Google OAuth 2.0 PKCE requesting **only** `https://www.googleapis.com/auth/drive.appdata`.
  - Operates exclusively in Google Drive's hidden `appDataFolder` space (`spaces=appDataFolder`), guaranteeing zero access to personal files or folders.
- **Data Schema (`user_music_db.json`)**:
  - Versioned snapshot schema storing `schemaVersion`, `lastSyncedAt`, `playlists`, `savedItems` (favorites), `songStats` (playCount, totalPlayTimeSeconds, lastPlayedAt), and bounded `playbackHistory` (up to 200 entries).
- **Safe Conflict Resolution Merge Engine (`SyncMergeEngine.kt`)**:
  - Deterministic conflict resolution before uploading:
    - Playlists & SavedItems: Newer `updatedAt` timestamp wins.
    - SongStats: Higher `totalPlayTimeSeconds`, higher `playCount`, and latest `lastPlayedAt` win.
    - PlaybackHistory: Deduplicated by `(songId, playedAt)`, sorted descending, and capped at 200 items.
- **Dirty-Flag Optimization**:
  - Tracks local `hasUnsyncedChanges` flag; skips background cloud uploads if nothing changed locally.
- **Background Scheduler & REST API v3**:
  - WorkManager `CloudSyncWorker` supporting **Every Hour**, **Once a Day**, and **Manual Only** schedules with network constraints.
  - Google Drive REST API v3 client (`GoogleDriveApiClient.kt`) with multipart upload and exponential backoff retry.
- **Settings UI Integration & Tabs (`SettingsScreen.kt`)**:
  - Merged **Cloud Sync (Google Drive)** directly into the **Online Music & Streaming Services** hub card and settings section under a dedicated, separate tab.
  - Sub-tabs: **Streaming & Audio** (Online mode, YouTube login, Last.fm, bitrate quality) and **Cloud Sync (Drive)** (Google Sign-In, sync frequency, "Sync Now", "Delete Cloud Backup").
  - Cleaned up UI: completely removed manual Client ID configuration buttons/dialogs, providing a seamless one-tap "Sign In with Google" flow.
  - Auto-switches to Cloud Sync tab when search queries contain `cloud`, `sync`, `drive`, or `gdrive`.
- **Official Google OAuth Client IDs & Reverse-DNS Scheme (`build.gradle.kts`, `GoogleAuthManager.kt`, `AndroidManifest.xml`)**:
  - Injected official Google OAuth Client IDs from `local.properties` into `BuildConfig.GDRIVE_CLIENT_ID` (debug vs release/staging build types).
  - Dynamically constructs the RFC 8252 reverse-DNS redirect URI `com.googleusercontent.apps.<client_id_prefix>:/oauth2redirect` matching Google's Android client requirement.
  - Registered matching intent filters in `AndroidManifest.xml` for `com.googleusercontent.apps.*` reverse-DNS schemes.
  - Added manual code / callback URL exchange dialog as fallback in case device browser does not catch custom scheme redirects.

---

### [2026-11-23] - Landscape FullScreen Lyrics, Shapeable Album Art & Multi-Provider Romanization
- **Landscape Two-Column FullScreenLyricsView (`FullScreenLyricsView.kt`, `LyricsSettingsBottomSheet.kt`, `LyricsSettings.kt`)**:
  - Implemented responsive landscape detection (`maxWidth > maxHeight && maxWidth >= 600.dp`) splitting the full-screen lyrics UI into a 40/60 two-column layout.
  - **Left Hero Column**: Interactive shapeable album art hero, song title, artist label, time-seek slider with formatted elapsed/total durations, and Previous / Play-Pause / Next playback controls.
  - **Right Lyrics Column**: Synchronized lyrics view supporting both Cinematic Kinetic word-by-word visualizer and selectable Classic multi-line view.
  - **Album Art Shape Customizer**: Added `AlbumArtShape` (`SQUIRCLE`, `CIRCLE`, `HEART`) selectable from the top-right three-dot menu `( ⋮ )` bottom sheet, featuring custom Bezier vector clipping for the heart shape.
  - **Dynamic Theme Palette & Starfield Preservation**: Preserved dynamic album art palette ambient lighting and cinematic kinetic starfield backdrop.
- **Selectable Lyrics (`FullScreenLyricsView.kt`)**:
  - Wrapped classic full-screen and landscape lyrics in `SelectionContainer` for user text highlighting, copy, and gesture interaction.
- **Multi-Provider Lyrics Expansion (`LyricsRepository.kt`, `Musixmatch.kt`, `Unison.kt`, `FastbootEraseLrcRead.kt`)**:
  - Integrated **Musixmatch** (with duration tolerance matching `abs(duration - expected) <= 4`) and **Unison** / `FastbootErase` fallback sources.
  - Visual status indicator icons in the lyrics source selector (Searching, Found & Connected, Fallback).
- **1200p Ultra-High-Resolution Cover Art Pipeline (`InnerTubeClient.kt`)**:
  - Automatic URL transformer upgrading `lh3.googleusercontent.com` and `yt3.ggpht.com` covers to 1200×1200p resolution.
- **Decoder & Audio Engine Reliability**:
  - 32-bit float audio sink clamp for older Android devices, Vorbis/Opus tag parser resilience, and cold-start replay HTTP 403 prevention.

---

### [2026-11-22] - BitChord 1-to-1 Video Search, Subtitle Extraction & Track Matching
- **InnerTube Search & Subtitle Parser Parity (`InnerTubeClient.kt`)**:
  - Replaced naive `subtitleRuns.firstOrNull()` with BitChord's multi-pass `artistNamesFromRuns()` grouping and `TYPE_WORDS` / `TALLY_REGEX` filtering.
  - Eliminated corrupted `"Song"` / `"Video"` artist names in search results, restoring genuine artist names (e.g. "The Weeknd", "Arijit Singh") and albums across English, Hindi, and regional music.
  - Added video detection based on `16:9` aspect ratio and non-square thumbnail dimension heuristics (`thumbnails.isNotSquare()`).
  - Switched `searchMusicVideo` queries to clean BitChord format (`"$title $artist"`, `"$title"`) under `SearchFilter.VIDEOS`, eliminating keyword search traps.
  - Enhanced `fetchOfficialVersionPair` to strictly prioritize `MUSIC_VIDEO_TYPE_OMV` (Official Music Video) over UGC lyric/cover counterparts.
- **Video Track Matcher Overhaul (`VideoTrackMatcher.kt`)**:
  - Implemented BitChord's `allowVideoDrift` duration math: when artist matches, narrative/cinematic intro/outro drift up to 180s carries zero penalty, allowing official music videos like "Blinding Lights" (4:30 vs 3:20) to win.
  - Added strict anti-lyric/anti-audio penalty (`-800.0`) for `"lyrics"`, `"lyric video"`, `"visualizer"`, and `"audio"`, preventing lyric videos from beating official videos.
  - Added `+450.0` boost for official music video markers (`"official music video"`, `"official video"`, `"video oficial"`, `"m/v"`).
  - Added `searchableTitle` and `primaryArtist` functions for clean query decomposition.
- **Search UI Formatting (`GlobalSearchSheet.kt`)**:
  - Cleaned subtitle rendering to display `${track.artist} • ${track.album}` with intelligent fallbacks.

### [2026-11-22] - Offline Lyrics Romanization & Dual-Line Translation (Apple Music Style)
- **Data Models & Script Detection (`:core-model`, `:core-library`)**:
  - Added `RomanizationScript` enum supporting Korean Hangul, Japanese Romaji, Chinese Pinyin, Urdu Nastaliq, Hindi Devanagari, Cyrillic, and Spanish Latin.
  - Added `LyricsDisplayMode` enum (`ORIGINAL_ONLY`, `DUAL_LINE`, `ROMANIZED_ONLY`).
  - Added `val romanizedText: String? = null` to `LyricWord` and `val romanizedText: String? = null`, `val translationText: String? = null`, `val detectedScript: RomanizationScript = RomanizationScript.NONE` to `LyricsLine`.
  - Implemented pure Kotlin Unicode script detection (`ScriptDetector.kt`) and specialized poetic dictionary / phoneme normalization for Urdu (`UrduMusicalLexicon.kt`).
  - Created `LyricsRomanizer.kt` providing word-by-word and line-by-line enrichment while strictly preserving timestamps, syllables, and background lines.
- **Hexagonal Port & Android ICU Adapter (`:core-common`, `:app-android`)**:
  - Defined pure Kotlin port `LyricsTransliterator` and thread-safe registry `PlatformTransliteratorRegistry` in `:core-common`.
  - Implemented high-performance native adapter `AndroidIcuTransliterator` in `:app-android` backed by `android.icu.text.Transliterator` with generic Latin-ASCII, Hangul, Cyrillic, Devanagari, and Arabic engines.
  - Initialized registry inside `NodePlayerApplication.onCreate()`.
- **Karaoke Timing & UI Renderers (`:core-ui`)**:
  - Updated `LyricLineItem.kt` with `LyricLineContent` and dual-line/romanized rendering, preserving smooth feathered sweep masks and text layout measurements.
  - Updated `WordByWordKaraokeText.kt` with Apple Music style dual-line stacked subtitles, maintaining 120 FPS sub-frame sync without audio/visual drift.
  - Added "Romanize Foreign Lyrics" switch and "Display Mode" segmented control (`Original`, `Dual-Line`, `Romanized`) to `LyricsSettingsBottomSheet.kt`.
- **Settings Persistence (`LyricsSettingsRepository.kt`)**:
  - Added `KEY_LYRICS_ROMANIZE_FOREIGN` and `KEY_LYRICS_DISPLAY_MODE` DataStore keys with reactive state propagation.

### [2026-11-22] - Critical Playback, DSP, UI Wiring, and Core Subsystem Fixes
- **Playback & Queue Loop Control (`CrossfadePlayerManager.kt`)**: Added `repeatMode` evaluation on `Player.STATE_ENDED` to prevent infinite looping when Repeat is OFF (`REPEAT_MODE_OFF`).
- **Tag Parser Fixes (`AudioHeaderParser.kt`)**: Resolved Vorbis / Opus comment parsing crash by scanning for `"vorbis"` (6-byte) and `"OpusTags"` (8-byte) identifiers and skipping them before parsing the vendor length.
- **DSP Zero-Allocation Hot Path (`DspChain.kt`, `DspProcessors.kt`)**: Removed per-sample `Pair<Float, Float>` allocations from DSP processing loop by adding in-place `FloatArray` buffers, preventing ~352,800 allocations/sec.
- **Convolver Anti-Clipping Stage Reordering (`DspChain.kt`)**: Reordered `convolver.processBlock` to execute prior to the 32-bit soft limiter, guaranteeing digital clipping prevention.
- **Discovery Queue Preservation (`SharedPlayerLayout.kt`)**: Preserved existing playback queue on discovery card taps instead of resetting to a single-item queue.
- **Artist Double Shuffle Fix (`MainActivity.kt`)**: Shuffled artist top tracks once to ensure initial played track matches the first queue item.
- **Repeat Mode UI Wiring (`PlayerViewModel.kt`, `MainActivity.kt`)**: Wired `repeatMode` StateFlow and `cycleRepeatMode()` to the playback options sheet.
- **Playlist Detail Track Resolution (`SharedPlayerLayout.kt`)**: Replaced flawed album title matching with async playlist track fetching via `SharedInnerTubeProvider`.
- **False Downloaded Flag Fix (`Track.kt`)**: Removed generic `/Music/` directory matching from `isDownloaded`.
- **LRC Negative Duration Clamping (`LrcParser.kt`)**: Ensured `startMs <= endMs` by clamping word duration to remaining line time.
- **Output Device Fallback Renaming**: Replaced hardcoded `"krighz's Phone"` and `"This phone"` with neutral `"This Device"`.
- **Spotify Match Resolution (`MainActivity.kt`)**: Appended user-resolved track candidate to `spotifyMatchedTracks`.
- **ITU-R BS.1770 LUFS Correction (`LoudnessR128.kt`)**: Corrected BS.1770 loudness calculation to avoid dividing power by channel count.
- **Audio Profile Serialization Fixes (`DspProcessors.kt`)**: Scoped JSON parsing blocks to eliminate `"enabled"` key collision, and added full `reverbConfig` and `convolverConfig` serialization.
- **YouTube Auth & Cookie Preservation (`StreamResolver.kt`, `StreamFormatResolver.kt`)**: Defaulted `innerTubeClient` to `SharedInnerTubeProvider.get()`.
- **Multiplatform File Chooser Decoupling (`PlatformFileDialog.kt`, `SharedPlayerLayout.kt`)**: Created `expect/actual` file dialog abstraction, removing direct `javax.swing.*` dependencies from `commonMain`.
- **Updater Callback Wiring (`MainActivity.kt`)**: Connected `onStartUpdate` and `onCancelUpdate` to `UpdateDownloadManager`.

---
- **Album Art 1:1 Aspect Ratio Preservation (`NowPlayingView.kt`)**:
  - Resized the hero artwork container strictly to the 45%–48% screen height mark (`(screenHeight * 0.46f).coerceIn(340.dp, 440.dp)`), forming a natural 1:1 square container matching phone width.
  - Eliminated over-zooming and horizontal cropping (`artZoomScale = 1.0f`, blur layer `scaleX = 1.0f, scaleY = 1.0f`).
  - Retained the seamless progressive gradient blur in the lower portion of the artwork and the alpha dissolve mask (`heroArtworkAlphaBrush` and `gradientBlurAlphaBrush`), keeping the transition into the existing bottom ambient blur and controls completely seamless.
- **Full-Screen Lyrics 50% Height Bottom Sheet (`LyricsSettingsBottomSheet.kt`)**:
  - Constrained sheet height to at most 50% of the screen height (`maxHeight * 0.52f`), with top drag handle and internal vertical scrolling.
  - **Main Menu**:
    - Top Section: Selectable LRC / Lyrics Providers (Auto, Apple Music TTML, LrcLib, BetterLyrics, LyricsPlus, KuGou, Local File) with single-tap provider switching and immediate reload.
    - Lyrics Style Bar: Segmented 2-pill toggle between `Cinematic Kinetic` and `Classic Fullscreen`.
    - Lyrics Style & Settings Menu Item: Glass navigation card with chevron `>` navigating to the customization sub-page within the 50% sheet.
    - Quick Actions: Sync Offset (`± ms`), Search / Reload Lyrics, Share Lyrics Card.
  - **Customization Sub-Page**:
    - Back button (`←`), Mode title, and Close button.
    - Kinetic Mode: Glow Palette selection, Viewport Zoom, Camera Glide, Word Bounce, Left/Right Swing, Vertical Spacing, Sparkle Density, Shuffle Layout, and Top Bar switch.
    - Classic Mode: Master Zoom, Line Spacing, and Top Bar switch.
- **Lyrics Provider Interchange & Status Architecture (`LyricsRepository.kt`, `Musixmatch.kt`, `Unison.kt`, `LyricsSettingsBottomSheet.kt`)**:
  - Added `Musixmatch` (RichSync word timing & 14M+ song catalog) and `Unison` (Karaoke & Asian synced catalog) clients to `:core-library`.
  - Added `LRC_RED`, `MUSIXMATCH`, and `UNISON` to `LyricsProviderChoice` enum.
  - Implemented 3 interactive status indicators on each provider in the provider/source selector:
    - 🔍 **Click to Search** (`Icons.Rounded.Search`): Idle provider ready to query on click.
    - 🎵 **Lyrics Found** (`Icons.Rounded.MusicNote`): Green badge confirming lyrics are discovered/cached from this source.
    - ✓ **Connected • OK** (`Icons.Rounded.Check`): Active provider currently driving playback.
  - Added Compose `SelectionContainer` around lyrics in `FullScreenLyricsView.kt`, making all lyrics lines and words directly selectable and copyable with native text selection.
  - Added dedicated "Select Lyrics Lines" action menu item in `LyricsSettingsBottomSheet` to trigger interactive line card multi-selection.
- **Lyrics Provider Interchange Architecture (`LyricsRepository.kt`, `LyricsManager.kt`, `LyricsSettingsRepository.kt`)**:
  - Added `LyricsProviderChoice` enum with support for direct targeted fetching from individual providers when automatic discovery returns wrong lyrics.
  - Added DataStore persistence for `KEY_LYRICS_PREFERRED_PROVIDER`.

---

### [2026-11-22] - High-Resolution Album Art Upgrade, Streaming Quality Hub & Range Chunking Verification
- **High-Resolution Album Art Pipeline (`AudioArtworkFetcher.kt`, `ArtworkLoader.kt`, `ArtworkUrlResolver.kt`)**:
  - Replaced legacy 256px downsampling with dynamic target resolution (1200px for High/Ultra, 544px for Medium, 256px for Data Saver).
  - Fixed local track loading on Android 10+ to directly decode embedded artwork via `MediaMetadataRetriever` at ARGB_8888 full resolution rather than prematurely falling back to low-res MediaStore thumbnails.
  - Eliminated RGB_565 bit depth downscaling, preserving full 32-bit color fidelity and preventing visual color banding.
  - Upgraded remote artwork URL handling to replace low-res YouTube thumbnails (`hqdefault.jpg`) with `maxresdefault.jpg` (1280x720) and `hq720.jpg` fallbacks.
  - Dynamically boosted YouTube Music Google CDN artwork URLs from `=w120-h120` to `=w1200-h1200-l90-rj`.
- **Online Music & Streaming Services Hub Reorganization (`SettingsScreen.kt`, `SettingsView.kt`)**:
  - Renamed settings group to `"Online Music & Streaming Services"`, combining YouTube Music and Last.fm into a single unified control hub.
  - Section 1: Online Services & Accounts (YouTube Music toggle, YouTube sign-in, Last.fm toggle, auth status, offline queue sync).
  - Section 2: Streaming Quality & Media Resolution:
    - Audio Quality selection with explicit kbps ratings (`High ~160-256 kbps`, `Medium ~128 kbps`, `Low ~48-70 kbps`).
    - Album Artwork Quality selection (`1200p HD`, `544p Balanced`, `256p Data Saver`).
    - Video Stream Resolution selection (`1080p`, `720p`, `480p`, `360p`, `Auto`).
- **Chunking Range Probing & Multi-Song Quality Verification (`StreamQualityAndChunkingTest.kt`)**:
  - Implemented unit tests validating format ranking and selection across different songs ("Thunder", "Rock That Body", "Comfortably Numb") for High, Medium, and Low quality presets.
  - Verified HTTP 206 Partial Content range chunking (`bytes=0-1024`, `bytes=0-2048`, `bytes=0-511`) for audio streams, video streams, and album artwork image header requests.

---

### [2026-11-22] - FLAC to Opus Converter Storage Fix, Location Prompt & Metadata Extraction
- **Opus Muxer Crash Resolution (`AndroidFFmpegTranscoder.kt`, `DesktopFFmpegTranscoder.kt`)**:
  - Removed video stream mapping (`-map 0:v:0? -c:v copy -disposition:v:0 attached_pic`) that previously caused FFmpeg's Opus muxer to immediately crash with exit code 1 (`Unsupported codec id in stream 1 / Invalid argument`).
  - Switched to Vorbis comment cover embedding via `METADATA_BLOCK_PICTURE` (RFC 7845 standard) and `-map_metadata 0`.
  - Replaced shell string concatenation with `FFmpegKit.executeWithArgumentsAsync(args)` on Android, eliminating command-line quoting issues with paths containing spaces or special characters.
- **Pre-Conversion Output Destination Prompt (`AudioConverterView.kt`)**:
  - Before starting conversion, if `destinationFolder == null` and `!outputNextToOriginal`, the app displays an `Output Location Required` modal dialog allowing the user to select an output folder via Browse, save next to original FLACs, or cancel.
  - Replaced pitch-black window surface (`#000000`) with sleek modern dark charcoal gray (`Color(0xFF141418)`), with integrated status bar and navigation bar padding.
- **Android Scoped Storage & Permission Safety (`MainActivity.kt`, `AndroidManifest.xml`)**:
  - Initialized converter destination folder to `context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)/Converted`, ensuring guaranteed write permissions without permission popups across all Android versions (Android 8 to 14+).
  - Integrated `ActivityResultContracts.OpenDocumentTree()` for custom folder selection.
  - Added `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="29"`) and `android:requestLegacyExternalStorage="true"` in `AndroidManifest.xml`.
- **Pure Kotlin FLAC Metadata, Cover & Lyrics Extraction (`FlacMetadataExtractor.kt`, `TranscodeQueueManager.kt`)**:
  - Created `FlacMetadataExtractor.kt` in `:core-library` to parse FLAC block 6 (PICTURE) raw bytes/MIME/Base64 and block 4 (VORBIS_COMMENT) lyrics without Android dependencies.
  - Automatically extracts and exports companion cover art (`${output.nameWithoutExtension}.jpg` / `.png`) and companion lyrics (`${output.nameWithoutExtension}.lrc`) into the destination directory upon verified transcode.
  - Automatically embeds Vorbis comments `METADATA_BLOCK_PICTURE` and `LYRICS` directly into the transcoded `.opus` file.

### [2026-11-22] - Seamless Album Art Gradient Blur & Bottom Layer Continuity
- **Continuous Hero Artwork Container (`NowPlayingView.kt`)**:
  - Expanded hero container to full screen height in audio mode, eliminating the artificial height boundary that produced a hard horizontal line above the timeline scrubber.
  - Retained 16:9 video presentation mode intact.
- **Dual-Layer Progressive Gradient Blur (`NowPlayingView.kt`)**:
  - Overlaid a blurred copy of the artwork (`blur(28.dp)`) over the sharp base artwork using `CompositingStrategy.Offscreen` and `BlendMode.DstIn`.
  - Applied `gradientBlurAlphaBrush` so the top face and background remain 100% sharp and unblurred, while the blur layer smoothly emerges in the shaded region over the track title, artist name, star/menu buttons, and instrumental chip.
- **Seamless Alpha Dissolve into Existing Bottom Album Art Gradient (`NowPlayingView.kt`)**:
  - Replaced opaque black color gradient with an alpha dissolve mask (`heroArtworkAlphaBrush` via `BlendMode.DstIn`), smoothly fading the hero artwork directly into the existing bottom album art gradient (blurred 90dp backdrop + ambient mesh glow).
  - Added subtle text-contrast scrim (`textContrastScrimBrush`) across the title/artist zone to preserve crisp legibility while keeping the colorful blurred art visible underneath.

### [2026-11-21] - Cinematic Lyrics Dynamic Camera, Pre-Highlight Bugfix, Transparent Top Bar & Seamless Artwork
- **Monotonic Word Timing & Pre-Highlight Gating (`CinematicKineticLyricsView.kt`)**:
  - Enforced monotonic sequencing across all word timings (`word[i].startMs >= prevWord.endMs`), preventing overlaps that caused early pre-highlighting of words like "I", "me", "made", "feel".
  - Created 4-tier visual hierarchy: active word with sweep brush + bloom glow, current phrase sung words with solid crisp white (0.88f), past phrases dimmed (0.32f), and upcoming phrases locked to deep unlit dim (0.18f).
- **Distance-Adaptive Spring Camera & Lookahead (`CinematicKineticLyricsView.kt`)**:
  - Distance-boosted spring omega (`dynamicOmega = 5.2f * cameraGlide * (1f + travelDist / (140 * density))`) enables dynamic high-speed camera leaps across the screen.
  - Anticipatory lookahead in final 240ms of active word shifts camera focus toward next word before it starts.
- **Instrumental Break Dedicated Placement (`CinematicKineticLyricsView.kt`)**:
  - Centered pulsing dots at `instCx = 0f` with generous vertical clearance (160f top, 240f bottom), removing them from lyrics text line flow.
  - Inactive break dots are culled outside their timing window, eliminating ghost dots lingering over lyrics.
- **100% Transparent Floating Header Overlay (`FullScreenLyricsView.kt`)**:
  - Made lyrics stage edge-to-edge fullscreen base layer (`fillMaxSize()`, `zIndex(0f)`); top header and bottom controls render as transparent floating overlays (`zIndex(2f)`).
  - Eliminated opaque brown/yellow drawer container; floating album art thumbnail, share button, title/artist, favorite star, and menu button float cleanly over the starfield.
- **Seamless Now Playing Album Art Gradient (`NowPlayingView.kt`)**:
  - Lowered bottom controls reserved height to 310dp so album art extends 75dp deeper behind controls.
  - Replaced linear fade with 6-stop smooth quadratic gradient, blending artwork seamlessly into 100% pure black with no harsh cutoff seam.

### [2026-11-21] - Cinematic Kinetic Lyrics Engine Optimization & Auto-Hide Gestures
- **Zero-Lag Engine Optimization (`CinematicKineticLyricsView.kt`)**:
  - Eliminated per-frame `textMeasurer.measure` calls by caching `TextLayoutResult` directly on `KineticWordNode` during initial layout generation.
  - Eliminated pointer input cancellation loops by detaching dynamic camera states from `pointerInput` keys.
  - Added vertical viewport culling, rendering only words and blocks within `[camY - viewHalfH, camY + viewHalfH]` (80–90% draw call reduction).
  - Reused scratch `Path` for 4-point diamond glint starfield particles, eliminating heap allocations per frame.
  - Integrated 60–120fps sub-frame position interpolation (`smoothPlaybackMs += dt * 1000f`) reconciling with `currentPositionProvider()`.
  - Enforced strictly monotonic color stops (`0.0f < s0 < s1 < s2 < 1.0f`) on the horizontal sweep gradient to eliminate Skia/AGSL clamping stalls.
- **Auto-Hiding Header & Gesture Controls (`FullScreenLyricsView.kt`)**:
  - Header auto-hides during playback for an unobstructed full-screen lyrics experience.
  - Single screen touch or movement brings back the header for 2 seconds.
  - Two-finger downward swipe brings back the header for 3 to 4 seconds (3500ms).
  - Tapping on the header or menu locks it open ("open and wait").
  - Dismissing or closing ("cutting") the sheet/menu resets the auto-hide timer to 3.5 seconds before hiding.
  - Favorite star button (`[ ⭐ Star ]`) and overflow menu (`[ ⋮ Menu ]`) preserved completely intact with spring animated transitions.

### [2026-11-21] - Cinematic Kinetic Lyrics Engine & Unified Overflow Menu
- **Cinematic Kinetic Lyrics Rendering Engine (`CinematicKineticLyricsView.kt`)**:
  - Implemented high-refresh-rate Compose Multiplatform hardware-accelerated canvas engine (60–120fps).
  - TTML & LRC layout architecture with procedural stanza void gaps, intra-phrase clumping, and organic L/R/Center alignment.
  - Floating 3-dot instrumental breaks (`• • •`) with sinusoidal wave bobbing and progressive illumination (`0.0 → 1.0`).
  - Glitch-free single-pass feathered `LinearGradient` active word sweep (no double-pass, zero clipRect seams).
  - Critically-damped 2D camera physics with adaptive hero word framing and `smoothCommonZoom` interpolation.
  - 3-layer parallax floating starfield modulated by `sparkleDensity`.
  - Tap-to-seek on words via inverse camera unprojection.
- **Preserved Classic Full-Screen Mode (`FullScreenLyricsView.kt`)**:
  - Maintained previous full-screen lyrics scroll view intact as a selectable style (`CLASSIC_FULLSCREEN`), scaled cleanly by `commonZoom`.
- **Spared Star Button & Option B Unified Overflow Menu (`FullScreenLyricsView.kt`)**:
  - Kept existing favorite star button (`[ ⭐ Star ]`) completely intact.
  - Placed unified overflow menu button (`[ ⋮ Menu ]`) opening the frosted-glass `LyricsSettingsBottomSheet` (providing style switching, common zoom, sparkle density, left/right swing, vertical spacing, camera glide, bounce, glow palettes, and top bar visibility).
  - Added collapsible top bar with compact floating restore pill.
- **Single Source of Truth Settings (`LyricsSettingsRepository.kt`, `SettingsScreen.kt`, `SettingsView.kt`)**:
  - Persisted all lyrics settings in DataStore/SharedPreferences with reactive `StateFlow<LyricsSettings>`.
  - Added matching Lyrics Style & Kinetic settings section to both Android App Settings and Desktop `SettingsView`.

### [2026-11-19] - BitChord-Inspired Music Video Resolution & Smart Offset Alignment
- **Tier 1: Official InnerTube Version Pairing (`InnerTubeClient.kt`, `YouTubeModels.kt`)**:
  - Implemented `fetchOfficialVersionPair` parsing `playlistPanelVideoWrapperRenderer` and `counterpartRenderer` from YouTube Music `/next`.
  - Added `segmentMap` extraction (`primaryVideoStartTimeMilliseconds`, `counterpartVideoStartTimeMilliseconds`), tracking intro/outro offsets between audio releases and visual music videos.
- **Tier 2: Intelligent Video Matcher (`VideoTrackMatcher.kt`)**:
  - Created pure Kotlin `VideoTrackMatcher` decomposing titles into core identity and listing packaging (e.g. "(Official Video)", "[4K]", "Lyrics"), checking artist overlap, and validating runtime tolerance (±120s) to discard loops or reaction clips.
- **Asynchronous Background Pre-Resolution (`PlayerViewModel.kt`)**:
  - Triggered background pre-resolution on track transitions, updating `videoPlayerState.isAvailable` so the existing "Video" button lights up seamlessly for any song with a matching official video.
  - Applied millisecond `timeOffsetMs` when starting video playback so the visual timeline synchronizes accurately with the audio track.

### [2026-11-19] - 1:1 Interactive Gesture Sheet, Seamless Artwork Dissolve, Video Window & Lyrics Pre-caching
- **1:1 Interactive Gesture Sheet & Dynamic Folding (`MainActivity.kt`, `NowPlayingView.kt`)**:
  - Implemented continuous 1:1 finger tracking via `sheetDragOffsetY = Animatable(0f)` with real-time dynamic folding (`translationY = dragOffset`, `scale = 1f - 0.08f * progress`, and continuous squircle `ContinuousRoundedRectangle((progress * 36f).dp)`).
  - Configured 40% dismiss threshold (`screenHeightPx * 0.40f`) or downward fling velocity (> 1000 px/s) animating smoothly to close with `spring(stiffness = 380f, dampingRatio = 0.85f)`, with spring recovery when released earlier.
  - Enabled pointer input detection anywhere on the hero artwork or sheet to begin 1:1 folding instantly.
- **Seamless Artist Detail-Style Gradient Bleed (`NowPlayingView.kt`)**:
  - Upgraded artwork gradient fade to multi-stop fade directly to `Color(0xFF000000)` (`0.00f`/`0.40f` transparent, `0.65f` 40% alpha, `0.82f` 82% alpha, `0.94f` 96% alpha, `1.00f` 100% black), eliminating horizontal hard seams and perfectly blending album artwork into the dark ambient mesh backdrop.
- **Floating Video Window & Border Removal (`NowPlayingView.kt`, `MusicVideoEngine.kt`)**:
  - Removed 36.dp bottom vignette dissolve gradient border below the video.
  - Rendered video inside a floating 16:9 window card with continuous 16.dp rounded corners (`ContinuousRoundedRectangle(16.dp)`) and horizontal margins (`20.dp`), matching the reference design.
  - Positioned translucent "4K" quality badge and fullscreen expand button directly in the top-right corner of the video window.
  - Added smooth crossfade and scaling transition spec (`AnimatedContent`) when switching between album artwork and video mode.
  - Configured ExoPlayer `DefaultLoadControl` in `MusicVideoEngine.kt` to pre-buffer 2000ms before playback starts (`bufferForPlaybackMs = 2000`, `bufferForPlaybackAfterRebufferMs = 3500`), eliminating stuttering.
- **Parallel Thread Lyrics Pre-caching (`MainActivity.kt`)**:
  - Background coroutine launches on `Dispatchers.IO` as soon as the track begins playing, preloading and caching lyrics in parallel.
  - Instant zero-latency display when tapping the lyrics button, with synchronized lyric line teaser displayed beneath the track title.
- **Landscape Auto-Fullscreen Video (`MainActivity.kt`)**:
  - Added configuration orientation listener automatically promoting active video playback to `FullscreenCinemaPlayer` when tilted to landscape and returning to portrait window when upright.


### [2026-11-19] - Fix YouTube Stream Throttling & 1:04 Playback Stall
- **Bounded Chunked Range Slicing on All Google CDN Streams (`RangedUpstreamDataSource.kt`)**:
  - Enforced `isGoogleVideoUrl()` in `requiresChunkedRanging()`, ensuring all Google Video CDN requests use 128 KB bounded chunks and avoiding YouTube CDN's bandwidth clamping after the initial ~1 MB burst.
- **StreamResolver Prioritization Over NewPipe (`AudioCache.kt`)**:
  - Prioritized `streamResolver.resolveStream(videoId)` (iOS / dedicated audio itag 251/140) first in `createResolvingDataSourceFactory` and `refreshStreamUrl` (HTTP 403 retries), with `NewPipeFallback` as secondary fallback. Resolves un-decrypted `n`-parameter stalls on protected VEVO tracks.

### [2026-11-19] - Navigation/ID Bug Fixes & Hero Artwork Dissolve Polish
- **Album / Playlist ID Normalization (`InnerTubeClient.kt`)**:
  - Prepended `"VL"` to `PL...`, `OLAK...`, and `RD...` IDs in `fetchPlaylistTracks` while preserving `MPREb_...`, `FE...`, and `UC...` browse endpoints. Eliminates HTTP 400 Bad Request error and fixes empty tracks in Explore categories.
  - Updated `mapTwoRowItemToPlaylist` to recognize `MPREb_...` and bare `OLAK...` IDs as collections, preserving `takeIf { it.videoId == null }` guard for radio-context tracks.
  - Added fallback to `watchPlaylistEndpoint` and `thumbnailOverlay` for `featuredOn` and `playlists` in `getArtistPage`.
- **Artist Page Releases & Playlists Wiring (`MainActivity.kt`)**:
  - Wired `onSelectRelease` and `onSelectPlaylist` in `ArtistDetailView` to `navigateToPlaylistDetail`, resolving the 0:00 playback freeze and opening full album/playlist detail sheets.
- **Multi-Stop Hero Artwork Dissolve (`NowPlayingView.kt`)**:
  - Applied multi-stop vertical scrim (`0.00f` / `0.45f` transparent to `0.70f` / `0.88f` / `1.00f` `animatedDarkMuted`) seamlessly blending the bottom half of the album cover directly into the dark backdrop.

### [2026-11-19] - 120 FPS Motion Architecture, Smooth Vsync Clock & Sheet Gestures
- **120 Hz Anchor Clock & Typography (`WordByWordKaraokeText.kt`)**:
  - Implemented `LyricVsyncClock` helper performing sub-millisecond nanosecond timestamp extrapolation at 120 Hz with 350ms seek re-anchoring.
  - Enforced Apple Music-style ExtraBold typography with `FontSynthesis.All` (`36.sp`, `43.sp` line height, `-0.85.sp` letter spacing), preserved `subtleGlowShadow` (`18f` blur, `alpha = 0.52f`), and multi-line visual line wrapping clip rects without premature clipping.
- **1:1 Gesture Sheet & Dismissal Handoff (`NowPlayingSheet.kt`)**:
  - Integrated `NestedScrollConnection` replacing rigid drag distance thresholds with continuous 1:1 scroll tracking and velocity-aware spring dismissal (`maxDismissDistancePx * 0.40f` or `available.y > 1200f`).
  - Added context click haptic feedback and dynamic corner radius morphing in draw phase (`graphicsLayer { translationY; scale; shape }`).
- **Directional Morphing & Tabular Numbers (`NowPlayingView.kt`, `NowPlayingTimeline.kt`, `NowPlayingSheet.kt`)**:
  - Added directional slide, fade, and scale `AnimatedContent` transition spec on hero artwork.
  - Injected `fontFeatureSettings = "tnum"` and `FontWeight.SemiBold` across elapsed and remaining time labels.
  - Applied smooth 3-swatch palette spring interpolation (`stiffness = 180f`, `dampingRatio = 0.92f`).
- **Continuous Capsule Dock & Draw-Phase Scroll Hiding (`ConvxLiquidDock.kt`, `MainActivity.kt`)**:
  - Set dock container shape to `ContinuousCapsule` with top-lit specular rim gradient (`Brush.verticalGradient(listOf(Color.White.copy(0.28f), Color.White.copy(0.05f)))`).
  - Restricted dock scroll hiding purely to draw phase via `Modifier.graphicsLayer { translationY = dockOffsetPx }` with zero layout passes.

### [2026-11-19] - Multi-Provider Lyrics Fallbacks, Visual Polish & Charts Pagination Fix
- **Multi-Provider Word-by-Word & Synced Lyrics Fallback Engine (`BiniLyrics.kt`, `BetterLyrics.kt`, `LyricsPlus.kt`, `LrcLib.kt`, `KuGou.kt`, `LyricsHttp.kt`, `LyricsRepository.kt`)**:
  - Cascading multi-provider lyrics engine supporting word-by-word timestamps, line-synced fallbacks, and persistent local caching.
- **YouTube Charts & Trending Pagination Loop Fix (`InnerTubeClient.kt`)**:
  - Fixed `YouTube playlist pagination did not finish` crash on Daily Top 100 and Trending 20 by excluding chip reload tokens (`reloadContinuationData`) and terminating loops gracefully.
- **Lyrics Atmospheric Weight, Header Scaling & Vignette Alignment (`WordByWordKaraokeText.kt`, `FullScreenLyricsView.kt`, `NowPlayingView.kt`, `LyricCardView.kt`)**:
  - Word-level text glow and bolder weights on active lyrics.
  - 30% header scale-up in FullScreenLyricsView and 45dp / 120px vignette alignment fix in NowPlayingView.
  - Reformatted Lyric Card to 4:3 (3:4 portrait) aspect ratio matching app font.

### [2026-11-18] - Full Interactive Artist Page, Song Section Markers & Advanced Audio Suite
- **YouTube Music Charts & Trending Pagination Crash Fix (`InnerTubeClient.kt`, `OnlineHomeView.kt`)**:
  - Excluded `reloadContinuationData` filter chips from pagination continuation parsing, eliminating infinite loops on Charts and Trending shelves.
  - Restricted `browseId` in `BrowseBody` to initial requests (`next == null`), preventing YouTube from ignoring `ctoken` and restarting at Page 1.
  - Replaced throwing `check(...)` with graceful loop exit on duplicate token or stream termination, preserving and displaying all tracks loaded so far.
  - Preserved Radio (`RD...`), Explore/Charts (`FE...`), and custom browse endpoints in `fetchPlaylistTracks` by only prepending `"VL"` to `"PL..."` IDs.
  - Swapped raw internal `nodeplayer://` track paths for authentic `https://i.ytimg.com/vi/{videoId}/hqdefault.jpg` thumbnails on shelf clicks.
- **Smart Offline Mixtape (`SmartMixtapeManager.kt`, `SmartMixtapePlanner.kt`, `SmartMixtapeWorker.kt`)**:
  - Offline song caching system blending fresh discovery (60%) and heavy rotation (40%) tracks for zero-data offline playback.
  - Automatic manual download exclusion ensuring already saved tracks are not duplicated in the mixtape partition.
  - Two-tier LRU eviction engine that evicts stale un-candidated entries before active rotation candidates when storage cap or song count is reached.
  - Seamless zero-latency playback interception in `CrossfadePlayerManager.kt`: automatically swaps remote streams for local cached `.opus` audio items with zero buffering.
  - Periodic 24-hour background sync worker (`SmartMixtapeWorker`) with unmetered Wi-Fi and battery constraints, plus device storage guards (< 250MB abort threshold).
  - 120 FPS debounced configuration slider card (`SmartMixtapeSettingsCard.kt`) integrated into `StorageSettingsScreen.kt` with live cache partition usage metrics and purge controls.
  - Quick-play `OfflineMixtapeHomeCard.kt` widget on `OnlineHomeView` and `LibraryScreen` for instant one-tap mixtape queue playback.
- **Full Interactive Artist Page (`ArtistDetailView.kt`, `InnerTubeClient.kt`, `MainActivity.kt`)**:
  - Full-bleed edge-to-edge artist portrait (`380.dp`) fading via a multi-stop vertical gradient scrim into `#09090B`.
  - Pinned floating top app bar inside `statusBarsPadding()` with circular glass Back button (`40.dp`), title fading into top bar on scroll, and right glass capsule containing Share button.
  - Centered bold artist name (`34.sp`, `FontWeight.ExtraBold`) and action trio: circular glass **Shuffle** (`48.dp`), center solid white **Play** (`64.dp`), and right circular glass **Follow/Star** (`48.dp`).
  - **Latest Release Highlight Card**: Full-width glass squircle (`ContinuousRoundedRectangle(18.dp)`, fill `Color.White.copy(alpha = 0.08f)`, border `1.dp` `Color.White.copy(alpha = 0.14f)`) with `68.dp` cover art, overline, bold title, recent release caption, and `+` save button.
  - **4-Row Horizontally Paged "Top songs"**: Horizontally scrolling `LazyRow` of 4-item vertical columns (`300.dp` width), rounded cover art, track title, artist subtitle, and trailing options menu.
  - **Comprehensive Categorized Shelves**: Albums, Singles & EPs, Videos (16:9 widescreen), Live performances (16:9), Featured on, Playlists by Artist, and Fans might also like (circular artist avatars, name, and audience pills).
  - **Interactive "Show all" Sub-Sheets**: Every shelf includes a "Show all" action button opening a bottom sub-sheet displaying all items in that category with 1-tap playback and navigation.
  - **About the Artist Bio Card**: Expandable artist biography with `"More"`/`"Less"` toggle and audience pills (`👤 Subscribers`, `🎧 Monthly listeners`).
- **Interactive Song Section Markers Bar (`SongSectionMarkersBar.kt`, `NowPlayingView.kt`, `LrcParser.kt`)**:
  - Tappable `Intro`, `Verse`, `Chorus`, `Drop`, `Bridge`, `Solo`, `Outro` pills mounted right above `NowPlayingTimeline`.
  - Active section indicator driven by an isolated `produceState` position ticker (sampling every 250ms), preventing parent player recompositions.
  - Tapping any section pill instantly seeks playback to the start timestamp of that musical section.
- **DJ Blend Crossfade Curve (`CrossfadeCurveMath.kt`, `SettingsScreen.kt`, `AudioAndThemeConfig.kt`)**:
  - Added `FadeCurve.DJ_BLEND` offering prolonged outgoing energy through 35% of the transition with accelerated incoming blend.
  - Normalized acoustic power calculation ($sum \le 1.22$) to eliminate digital clipping distortion on 0 dBFS mastered tracks.
  - Integrated into Settings volume curve picker with descriptive subtitle.
- **AutoEQ Headphone & IEM Calibration Database (`AutoEqDatabase.kt`)**:
  - Integrated built-in 10-band parametric EQ curves targeting Harman Over-Ear and In-Ear standards for popular models (Sony WH-1000XM4/XM5, Sennheiser HD600/650, Apple AirPods Pro 2, Moondrop Blessing 2/Aria, Tangzu Wan'er, 7Hz Zero).
- **Room Database Migration v9 & Followed Artists Persistence (`Entities.kt`, `NodeDatabase.kt`, `NodeBackupManager.kt`)**:
  - Created `FollowedArtistEntity` and registered in Room `@Database` with version bump to 9 and `MIGRATION_8_9`.
  - Added DAO queries to `PlayerDao` for following, unfollowing, and observing followed artists.
  - Updated `NodeBackupManager` to export and restore followed artists in `.nodebackup` archives.
- **Artist Navigation Wireup Across Core Screens (`MainActivity.kt`, `NowPlayingView.kt`, `PlayerViewModel.kt`)**:
  - Registered `"artist_detail"` route in `MainActivity` with back-stack support and sub-route handling.
  - Tapping artist contributors in Credits or "About the artist" in `NowPlayingView` navigates directly to the interactive Artist Detail page (automatically resolving `artistBrowseId` via InnerTube if necessary).
  - Added `PlayerViewModel.loadArtistPage` and `toggleFollowArtist` reactive flows.

### [2026-11-17] - Instant Lyric Card Generator & High-Impact Lyrics Typography
- **Instant Lyric Card Generator (`LyricCardView.kt`, `LyricCardRenderer.kt`)**:
  - Added dedicated share button directly over the album art thumbnail in `FullScreenLyricsView` top bar to instantly trigger lyric selection mode.
  - Interactive multi-line selection mode (up to 6 lines) in squircle card containers (`ContinuousRoundedRectangle(16.dp)`) with illuminated selection state and a floating glass pill dock `[ ✕ Cancel | Share ]`.
  - 9:16 vertical Lyric Card generator previewing dynamic ambient gradients derived from `AlbumArtPalette`, "Node Player" brand watermark, bold typography, squircle album art, track title, and artist name.
  - Android `LyricCardRenderer` generates 1080×1920 high-resolution PNG exports, automatically launches the native Android Share Sheet via `FileProvider`, and supports saving directly to device Pictures / MediaStore gallery.
- **Thicker High-Impact Lyrics Typography (`WordByWordKaraokeText.kt`, `LyricLineItem.kt`, `FullScreenLyricsView.kt`)**:
  - Increased font weight to `FontWeight.Black` (active) and `FontWeight.ExtraBold` (inactive) with `-0.45sp` letter spacing and `32.sp` default scale, ensuring punchy, highly legible typography against ambient backgrounds.
  - Increased unsung text alpha from `0.35f` to `0.48f` for bolder character strokes.
- **Unthrottled Stream Resolution Architecture (`AudioCache.kt`, `RangedUpstreamDataSource.kt`)**:
  - Primary YouTube audio stream resolution now delegates directly to `NewPipeFallback.resolveStream(videoId)` with Web JavaScript deciphering, matching the video engine's architecture.
  - Eliminates the 12–14s startup waterfall previously caused by rotating through dead clients (`VISIONOS`, `WEB_REMIX`), dropping stream start latency to ~1.1s.
  - Ensures continuous, uninterrupted 100% full-song playback across all tracks without hitting Google CDN's 1 MiB auth boundary.
- **Demuxer Crash Prevention & Clean Container Lifecycle (`RangedUpstreamDataSource.kt`)**:
  - Removed mid-stream URL swapping at byte 1,048,576.
  - Eliminates `java.io.EOFException` inside Media3's `FragmentedMp4Extractor` caused by splicing disparate container formats (WebM/Opus vs MP4/AAC) into an active demuxer pipeline.
- **Reactive Now Playing Position Ticker (`NowPlayingTimeline.kt`, `NowPlayingView.kt`, `FullScreenLyricsView.kt`, `ConvxMiniPlayer.kt`)**:
  - Replaced non-reactive `derivedStateOf { positionProvider() }` evaluations with leaf-level `produceState` coroutine tickers running at 100ms/150ms intervals.
  - Restored real-time slider progress updates, elapsed/remaining time counters, synchronized lyric highlighting, and miniplayer progress tracking without triggering root `MainActivity` recompositions.
- **Direct Head Stream Probe & Fast Timeout (`StreamResolver.kt`)**:
  - Reverted 1 MiB offset probing back to direct head verification (`Range: bytes=0-1024`) with `PROBE_TIMEOUT_MS` lowered to `1_500L`.
  - Eliminates HTTP 403 Forbidden errors triggered by Google Video CDN on unauthenticated mid-stream probes, preventing client rotation exhaustion and eliminating the 11–12s playback startup waterfall.
- **Parallel Seed Radio Requests (`TasteRecommendationEngine.kt`)**:
  - Parallelized seed track station lookups with `coroutineScope { seedCandidates.map { async { historySource.getRadioForTrack(...) } }.awaitAll() }`.
  - Decreased Your Mix initial load latency from ~3,200ms to ~850ms while preserving deterministic serial quota deduction.
- **Ktor CIO Engine Throughput Optimization (`InnerTubeClient.kt`)**:
  - Configured `maxConnectionsPerRoute = 20`, `keepAliveTime = 30_000`, and `pipelineMaxSize = 1` in Ktor CIO endpoint configuration.
  - Eliminated repetitive TLS handshakes across concurrent API queries.
- **Recomposition Isolation & Stable Keys (`YourMixShelf.kt`)**:
  - Added stable composite keys (`${track.extractVideoId() ?: track.id}_$index`) and explicit `contentType = "your_mix_card"` to horizontal lazy shelves.
  - Prevented redundant recompositions and resolved duplicate-key crashes during rapid horizontal scrolling.
- **In-Memory Taste Station Cache & Concurrency Gate (`OnlineHomeView.kt`)**:
  - Added session station cache `sessionCachedTasteStation` and single-flight `yourMixJob` mutex to deduplicate concurrent initial fetches and prevent cold-start UI freezes.
- **Audio-Tuned ExoPlayer Load Control (`CrossfadePlayerManager.kt`)**:
  - Replaced standard ExoPlayer load control with an audio-tuned `DefaultLoadControl` (min buffer 15s, playback buffer 800ms vs 2,500ms default), speeding up playback onset by ~1.7s.
  - Stripped `String.format("%.3f")` allocations from the 25ms volume fading loop to avoid GC pauses.
- **Persistent HTTP/2 Pool & Reduced Logging Overhead (`AudioCache.kt`)**:
  - Added persistent `ConnectionPool(32, 5, TimeUnit.MINUTES)` and eliminated per-chunk verbose logs from `AudioCache`.
- **Room Database Compound Indices & Migration v8 (`Entities.kt`, `NodeDatabase.kt`, `NodePlayerApplication.kt`)**:
  - Added compound indices on `TrackEntity(["isHidden", "title"])` and `TrackEntity(["isFavorite"])` via Room `MIGRATION_7_8`, reducing library query times from ~280ms to <15ms.
- **Root-Level 60Hz State Hoisting Elimination (`MainActivity.kt`)**:
  - Replaced root-level `collectAsState()` on `currentPositionMs` and `currentRms` with deferred lambda providers `{ viewModel.currentPositionMs.value }`, preventing root-to-leaf full UI recompositions on every frame.
- **Incremental Library Scan Metadata Caching (`LibraryScanner.kt`)**:
  - Reused existing sample rate, bit depth, channels, bitrate, and format description from Room database when `dateAdded` matches, skipping expensive `MediaMetadataRetriever` disk I/O on re-scans.
- **Widget Update Resource Guard (`WidgetUpdateManager.kt`)**:
  - Added active widget detection guard and removed duplicate `appWidgetManager` instantiation, saving CPU cycles and battery when widgets are not on home screen.
- **Slim Fixed Top Bar & Mode Switcher (`OnlineHomeView.kt`)**:
  - Pinned a slim 38dp mode switcher header at the top of the Home tab displaying `[ • YT Music | Device Files ]` with an active teal indicator dot (`#5EEAD4`).
  - Integrated fast switching to offline device files via `AppModeState.setOnlineMode(false)` and top-right profile avatar button.
- **Scrollable Mood Categories & Content Divider**:
  - Repositioned the horizontal mood categories chips (`Pop`, `Chill`, `Party`, `Commute`, `Romance`...) directly below the fixed header inside the scrollable content container.
  - Added a `1.dp` horizontal divider (`BorderSubtle`) beneath the mood chips; mood chips and divider smoothly scroll up under the fixed header with the feed.
- **Card Containers & Album Art Rounding (`YourMixShelf.kt`)**:
  - Redesigned "Your Mix" card container to `CarbonZincPalette.SurfaceContainer` (`#121215`) with a 1.dp subtle border and `ContinuousRoundedRectangle(20.dp)` squircle curvature.
  - Inner album art container upgraded to 136×136dp with `ContinuousRoundedRectangle(14.dp)` corner clipping.
  - Replaced header text "Play" with a capsule pill button in `SurfaceElevated` (`#1C1C21`) with `BorderActive`.
- **Teal Codec / Stream Badge Color (`AudioFormatBadge.kt`)**:
  - Updated online YouTube stream / AAC badge styling to `CarbonZincPalette.AccentTeal` (`#5EEAD4`) for badge text, border stroke, and subtle translucent background tint.
- **Carbon / Zinc Palette & Typography (`CarbonZincPalette.kt`)**:
  - Created centralized palette constants (`Background`, `SurfaceContainer`, `SurfaceElevated`, `SurfaceGlass`, `BorderSubtle`, `BorderActive`, `TextPrimary`, `TextSecondary`, `AccentTeal`) and custom typography scale.
- **True Dual-Engine Decoupling (Audio Master + Silent Secondary Video Player)**:
  - Audio playback on `PlaybackService.activePlayer` remains completely decoupled and acts strictly as the time master: never stopped, reset, or assigned a `MergingMediaSource` during video toggle.
  - Silent secondary `MusicVideoEngine` decodes video on an isolated ExoPlayer (`volume = 0f`, audio track disabled).
  - 250ms clock sync loop with $\pm 25$ ms deadzone, smooth rate-nudging ($\pm 10\%$) for 25–450 ms drift, and 1,500 ms cooldown macro-drift seek jumps.
- **Priority-Based Surface Claiming**:
  - `SurfacePriority` levels (`FULLSCREEN_CINEMA` > `NOW_PLAYING_CARD` > `MINI_PLAYER`) cleanly manage decoder binding.
  - 4-second release grace period prevents unnecessary decoder teardown during navigation transitions.
- **Zero-Allocation Layout & Aspect Ratio Fitting**:
  - `VideoAspectMath.calculateDimensions` provides Contain (Fit) and Cover (Crop) geometric aspect fitting inside zero-allocation Compose `Modifier.layout`.
  - Smooth 320ms alpha crossfade from album art to video upon first rendered frame.
- **Multi-Tier Artwork & Fullscreen Landscape Cinema**:
  - `FullscreenVideoPlayer` (`MusicVideoLayout.kt`) provides edge-to-edge landscape playback with 3.5s auto-hide controls and frosted glass quality switcher (`VideoQualitySheet.kt`).
  - Added pure Kotlin `CanvasModels.kt` and `CanvasArtwork` in `core-ui`.

---

### [2026-11-16] - Features v2: Phase F06 - Music Video Playback & Cinema Player
- **Top 16:9 Embedded Player in Now Playing**:
  - `NowPlayingView.kt`: Animated hero card aspect-ratio morphing between 1:1 (audio) and 16:9 (video) with `animateFloatAsState` spring physics.
  - Interactive Quality badge pill (e.g. "480p") that taps to open a liquid-glass dropdown menu with available heights (1080p, 720p, 480p, 360p, Auto).
  - Dedicated "Stop Video" button in Now Playing hero card to immediately revert to album art and audio-only mode.
  - "Play Video" badge button on album art when an audio track has an associated music video available.
  - Default is audio-only playback (`video_auto_play = false`), preserving data and battery unless explicitly toggled.
  - Removed redundant Volume slider from `QuickSettingsSheet.kt` to avoid duplication with `NowPlayingView`.
- **Strict PTS Audio/Video Lip-Sync & Seamless Stream Handoff**:
  - Android ExoPlayer master audio clock synchronization via hardware audio sink: attaching `VideoSurface` directly to `PlaybackService.instance.player` guarantees hardware PTS lip-sync.
  - Position-preserving stream handoff: switching between video and audio preserves `currentPosition` down to the exact millisecond (`seekTo(currentPos)`), completely preventing restarts from 0:00 or drifting.
  - Dual-surface architecture: `TextureView` (`view_video_texture.xml`) for squircle-clipped 16:9 hero card to prevent glass punch-through artifacts; `SurfaceView` (`view_video_surface.xml`) for full-screen `FullscreenCinemaPlayer`.
- **Fullscreen Cinema Player (`FullscreenCinemaPlayer.kt`)**:
  - Immersive fullscreen liquid-glass overlay controls with single-tap toggle and configurable auto-hide timer.
  - Dual-zone vertical drag gestures: left half for screen brightness, right half for system audio volume with real-time feedback badges.
  - 10-second double-tap seek with directional animated ripple badges (`resolveDoubleTapSeekDeltaMs`).
  - Resolution dropdown menu and interactive timeline scrubber.
- **Background Data Saver & Picture-in-Picture (PiP)**:
  - Configured `android:supportsPictureInPicture="true"` and `configChanges` in `AndroidManifest.xml`.
  - `MainActivity.kt`: Enters PiP on `onUserLeaveHint()` when video is playing and `video_pip` is enabled; restores UI on `onPictureInPictureModeChanged()`.
  - Data saver: when app moves to background outside of PiP, dynamically disables video track via ExoPlayer `TrackSelectionParameters`, streaming audio-only.
- **Desktop Multiplatform Video Player (`DesktopVideoPlayer.kt`)**:
  - Built on `uk.co.caprica:vlcj:4.8.3` via Compose `SwingPanel` embedding `EmbeddedMediaPlayerComponent`.
  - Periodic drift correction keeping A/V sync within 150ms.
  - Graceful fallback when VLC is not installed on the host machine.
  - Wired into `SharedPlayerLayout.kt` and `Main.kt`.
- **Settings Registry & Verification**:
  - Registered 8 settings under `SettingSubsection.MUSIC_VIDEOS` (`video_auto_play`, `video_default_quality`, `video_seek_step_s`, `video_gestures`, `video_pip`, `video_background_audio_only`, `video_controls_autohide_s`, `video_show_quality_pill`).
  - All tests passing: `VideoQualityMathTest`, `SettingsRegistryTest`, `StreamResolverVideoTest`, `AndroidVideoEngineTest`, `DesktopVideoPlayerTest`.
  - Multiplatform compilation 100% green: `:app-android`, `:app-desktop`, `:core-ui`, `:core-network`, `:core-model`.

---

### [2026-11-15] - Features v2: Verification Round 1 & Bug Fixes (F00–F05)
- **Bug B1: YouTube Playlist Creation on Import**:
  - Implemented `InnerTubeClient.createPlaylist` and `InnerTubeClient.addTracksToPlaylist` in `core-network`.
  - Wired automatic remote YouTube playlist creation in `PlayerViewModel.createPlaylistWithTracks` when authenticated.
  - Video IDs extracted from matched online tracks (`nodeplayer://youtube/...`, `watch?v=...`, `youtu.be/...`), skipping and logging local-only tracks.
  - Verified via `YouTubePlaylistCreateTest` (3 tests passed).
- **Bug B2: File Picker & Multi-Charset Import**:
  - Expanded file picker MIME filter in `MainActivity.kt` from `text/*` to `*/*`, resolving greyed out CSV/TXT files across Android file providers.
  - Added `LaunchedEffect(initialText)` in `PlaylistImportSheet.kt` to synchronize file contents directly into the UI state upon selection.
  - Added `UniversalPlaylistParser.decodeBytesWithFallback` supporting UTF-8 BOM, UTF-16 LE/BE, and Windows-1252.
  - Verified via `UniversalPlaylistParserTest` (5 new tests passed).
- **Bug B3: Audio Cache Directory Alignment**:
  - Corrected `NodePlayerApplication.kt` storage category path from `"media_cache"` to `AudioCache.CACHE_DIR_NAME` (`"audio_cache"`).
  - Verified via `AudioCacheStorageCategoryTest`.

---

### [2026-11-15] - Features v2: Phase F05 - FLAC to Opus Batch Converter
- **Multiplatform Batch Transcode Engine (`AudioTranscoder`)**:
  - Defined pure data models in `core-model/src/main/kotlin/com/nodeplayer/model/converter/ConverterModels.kt`: `TranscodePreset`, `TranscodeJob`, `TranscodeBatchSummary`, and `AudioTranscoder`.
  - Implemented mathematical models in `ConverterMath.kt`: exact byte size projection `estimatedOutputBytes(durationMs, bitrateKbps)`, human-readable disk formatting `formatDiskSize(bytes)`, and ReplayGain to Opus R128 Q7.8 offset conversion `replayGainToR128Q78(replayGainDb)`.
  - Implemented `DesktopFFmpegTranscoder` in `app-desktop` via `ProcessBuilder` with `-progress pipe:1` live stream parsing, clean process cancellation, temporary file cleanup, and test-decode verification. Zero runtime downloading: resolves custom configured path, adjacent bundled binary (`ffmpeg.exe`/`ffmpeg`), or system PATH.
  - Implemented `AndroidFFmpegTranscoder` in `app-android` using `dev.ffmpegkit-maintained:ffmpeg-kit-audio:6.0.4` (LGPL 3.0), providing in-process native Opus transcoding with thread-safe callbacks.
  - Implemented `TranscodeQueueManager` in `:core-library` featuring Semaphore-controlled multi-worker concurrency, recursive directory scanning, skip-existing output check, and an **Atomic Replace-Original Safety Gate** (source FLAC is never deleted until `.tmp.opus` verifies and atomic rename succeeds).
- **Settings Registry & Privacy**:
  - Added `SettingSubsection.AUDIO_CONVERTER("audio_converter", "Audio Converter")` in `SettingsRegistry.kt`.
  - Registered 13 settings under `SettingSection.LIBRARY_FILES`: `convert_default_preset`, `convert_custom_bitrate`, `convert_vbr`, `convert_application`, `convert_compression_level`, `convert_keep_original`, `convert_output_mode`, `convert_output_folder`, `convert_name_template`, `convert_workers`, `convert_write_r128`, `convert_ffmpeg_path`, and `convert_rescan_after`.
  - Documented FFmpeg and `ffmpeg-kit-audio` under `THIRD_PARTY_LICENSES.md`.
- **Liquid Glass UI & Navigation**:
  - Implemented `AudioConverterView` in `core-ui`: quality preset selector chips (`192`, `128`, `96`, `CUSTOM`), custom accordion with bitrate/VBR/application/compression controls, disk savings projection card, destination options, destructive replace-original confirmation dialog, multi-worker progress indicator, and batch summary dialog.
  - Wired navigation from `SettingsView.kt` (`core-ui`), `SettingsScreen.kt` (`app-android`), and `SharedPlayerLayout.kt` under `Library & Files > FLAC to Opus Converter`.
- **Verification & Testing**:
  - 100% test pass rate across `ConverterMathTest`, `DesktopFFmpegTranscoderTest`, `TranscodeQueueManagerTest`, `SettingsRegistryTest`, and `ThemeMatrixCharacterizationTest`.
  - Verified 9/9 symbols in `WIRING-F05.md` via `scripts/wiring-check.ps1` with defs=1 and non-test uses >= 1. Zero compiler errors, zero build failures.

---

### [2026-11-15] - Features v2: Phase F04 - Discord Rich Presence
- **Windows Desktop Local Discord IPC Named Pipe (`PresenceTransport`)**:
  - Implemented `DiscordIpcTransport` in `app-desktop` connecting to `\\.\pipe\discord-ipc-0..9` via `RandomAccessFile`.
  - Implemented little-endian 8-byte frame encoder/decoder in `DiscordFrameCodec` (`:core-network`).
  - Added `DiscordActivityBuilder` with string sanitation (clamping to 2..128 characters), timestamp calculations, and cover artwork URL / asset key fallback.
  - Implemented `PresenceController` with 15-second debouncing rate-limiter, track change/seek detection, pause clearing, incognito mode, and exponential backoff reconnection.
- **Settings & Privacy**:
  - Registered 6 desktop-targeted settings in `SettingsRegistry` under `ONLINE_CONNECT_NETWORK > DISCORD_PRESENCE`: `discord_enabled`, `discord_incognito`, `discord_show_cover`, `discord_show_album`, `discord_show_while_paused`, and `discord_app_id`.
  - Filtered out desktop-only settings on Android in `SettingsScreen.kt`.
  - Implemented liquid glass `DiscordRichPresenceCard` in `SettingsView.kt`.
  - Added quick Incognito toggle in `HomeProfileSheet.kt`.
- **Verification & Testing**:
  - 100% test pass rate across `DiscordFrameCodecTest`, `DiscordActivityBuilderTest`, `PresenceControllerTest`, `SettingsRegistryTest`, and `ThemeMatrixCharacterizationTest`.
  - Verified 10/10 symbols in `WIRING-F04.md` via `scripts/wiring-check.ps1`.

---

### [2026-11-14] - Post-v1.8.0: Liquid Chrome Overhaul, Real Volume Hardware Sync & Theme Standardization
- **Standard Dark Mode Theme Color (`#262626`) & OLED Black Isolation**:
  - Unified default dark mode background to `#262626` across `Theme.kt`, `LiquidGlassBackground.kt`, and `GlassTheme.kt` with `#303030` elevated card surfaces.
  - Eliminated hardcoded `Color.Black` roots in `OnlineHomeView.kt`, `PlaylistDetailView.kt`, and `LibraryScreen.kt`, wiring dynamic checks (`isOledBlack || themeMode == "OLED"`) to preserve 100% pitch black for OLED displays while rendering `#262626` in standard dark mode.
- **`FullScreenLyricsView.kt` Chrome & Navigation Polish**:
  - Initialized `controlsVisible = false` so full-screen lyrics starts with bottom controls collapsed by default.
  - Resolved scroll auto-popup bug by removing `registerInteraction()` triggers from `listState.isScrollInProgress`, ensuring manual reading/scrolling never brings controls up unexpectedly.
  - Redesigned top header: centered `36.dp` × `4.dp` drag handle pill, `48.dp` × `42.dp` rounded thumbnail, title/artist typography, favorite star toggle, and dynamic context action button (`MoreHoriz` when controls open, `Share` when collapsed).
  - Stripped out bottom black gradient vignette scrim overlay, allowing dynamic artwork palette to flow cleanly to the bottom display edge.
  - Replaced large centered "Jump to current" pill with a compact `36.dp` circular white button (`ArrowDownward`, `Color.White.copy(alpha = 0.16f)`) anchored at `Alignment.BottomStart`.
  - Preserved word-by-word karaoke sweep engine, `Inter` typography, and timing sync 100% untouched.
- **`NowPlayingView.kt` Hardware Volume Sync & 16:9 Video Refinements**:
  - Integrated real Android `AudioManager.STREAM_MUSIC` volume slider in `MainActivity.kt` with touch/drag seeking and real-time `"android.media.VOLUME_CHANGED_ACTION"` broadcast listener.
  - Wrapped 480p stream quality badge and fullscreen expand buttons in `if (isMusicVideo)` so they display exclusively during music video playback.
- **YouTube Music Discovery & Account Personalization**:
  - Multi-shelf home layout (Top Picks, Recents, Find Your Mood with duotone filters, Albums & Singles, 16:9 Music Videos, Trending).
  - Authentic Google/YouTube avatar thumbnail and handle hydration in `HomeProfileSheet` with encrypted `SecureSessionStore` persistence.

---

### [2026-11-11] - Milestone v2.5: Phase 3 Motion System, Fluid Spring Physics & Zero-Allocation Transitions
- **Centralized Motion Tokens & Dynamic Spring Resolvers (`REQ-MOT-01`)**:
  - Centralized physics constants into `:core-ui` `MotionTokens.kt`: `Snappy` (damping 0.75, stiffness 600), `Smooth` (0.82, 380), `Bouncy` (0.65, 420), `Gentle` (0.90, 220), `ConvxPuck` (0.82, 380), `DragTracking` (0.95, 900), and `Instant` (1.0, 50000).
  - Duration tokens: `DurationShort` (150ms), `DurationMedium` (300ms), `DurationLong` (500ms), and standardized easings (`EmphasizedEasing`, `DecelerateEasing`, `AccelerateEasing`).
  - Dynamic physics resolvers `MotionTokens.resolveSpringSpec()` and `MotionTokens.resolveDuration()` responding deterministically to `motionLevel` (`FULL`, `REDUCED`, `OFF`), `reduceMotion` (OS accessibility), `animationPreset`, and `isPerformanceMode`.
  - Zero-allocation touch interaction modifiers in `MotionModifiers.kt`: `pressScale`, `interactiveScale`, `fadeInOnLoad`, and `staggeredEntrance` deferring updates directly to `Modifier.graphicsLayer` to eliminate recomposition loops.
  - Multiplatform composition local `LocalMotionConfig` wired into `SharedPlayerLayout.kt` (desktop/common) and `Theme.kt` (Android).
- **Universal Screen & Sheet Transitions (`REQ-MOT-02`)**:
  - Implemented `ScreenTransitions.kt` providing `fadeThrough()`, `sharedAxisX()`, `sharedAxisY()`, `sheetSpring()`, `sheetEnter()`, and `sheetExit()`.
  - Wrapped top-level tab switches in `SharedPlayerLayout.kt` in `AnimatedContent` with `ScreenTransitions.fadeThrough(themeConfig)` and `SizeTransform(clip = false)`, eliminating abrupt screen cuts.
  - Unified bottom sheet springs in `GlassContextMenuSheet.kt` and `HomeProfileSheet.kt` using `ScreenTransitions.sheetEnter(themeConfig)` and `sheetExit(themeConfig)`.
  - Automatic fallback to snap specs and 0ms duration when motion is disabled or reduced.
- **Container Transform & Shared Element Miniplayer Morphing (`REQ-MOT-03`)**:
  - Implemented mathematical container morphing in `ContainerTransform.kt` (`ContainerTransformMath`) interpolating rect bounds, squircle corner radius ($20\text{dp} \to 0\text{dp}$), and asymmetric alpha ladders (mini-player controls fade out in $t \in [0.0, 0.25]$, fullscreen player controls fade in in $t \in [0.20, 1.0]$).
  - Multiplatform Composable `ProgressContainerTransform` clipping dynamically with `ContinuousRoundedRectangle` on draw-phase GPU layers.
  - Replaced modal overlay in `SharedPlayerLayout.kt` with continuous container morphing driven by `Animatable(0f)` `playerExpansionProgress` with `MotionTokens.Smooth`.
  - Added 1:1 touch-tracking downward drag gesture on `NowPlayingView.kt` with velocity-sensitive fling snap back to fullscreen or collapse to miniplayer capsule.
- **Motion Verification & Multiplatform Test Suites**:
  - Added comprehensive test suites: `MotionTokensTest`, `MotionModifiersTest`, `ScreenTransitionTest`, `ContainerTransformMathTest`, `MotionSystemSuiteTest` in `:core-ui`, and `MotionSettingsSyncTest` in `:app-android`.
  - 100% test pass rate across `:core-ui:desktopTest`, `:app-android:testDebugUnitTest`, `:app-desktop:test`, `:core-dsp:test`, `:core-model:test`, and `:core-common:test`.

### [2026-11-11] - Milestone v2.5: Phase 2 Performance Fixes, Hot-Path Allocation & Memory Elimination
- **Cold-Start Acceleration & Baseline Profiles (`REQ-PERF-05`)**:
  - Cold-start median time reduced from **1422 ms to 644 ms (-54.7%)** on physical device (`4a505e5`).
  - Added AndroidX `profileinstaller:1.3.1` and compiled startup baseline profile (`baseline-prof.txt`).
  - Implemented deferred background init for `LocalAudioScanner` and audio notification channels.
  - Pre-warmed AGSL RuntimeShaders (`GlassShaderCache`) and wired `reportFullyDrawn()` into Compose tree.
- **Tail Frame Latency & Jank Elimination (`dumpsys gfxinfo`)**:
  - S2 library scroll `p99` dropped from **250 ms to 93 ms (-62.8%)**; home scroll `p99` dropped from **133 ms to 89 ms (-33.1%)**.
  - Now Playing & Lyrics (S5) janky frames dropped from **45.87% to 13.63% (-70.3% jank reduction)**.
  - Eliminated offscreen GPU compositing (`CompositingStrategy.Offscreen`, `BlendMode.DstIn`) and per-line blur allocations in `FullScreenLyricsView.kt` and `AlbumArtGradientBackdrop.kt`.
  - Replaced O(N) lyrics linear scans with binary search O(log N) lookup.
- **Memory Footprint & Render Allocations**:
  - Peak GPU memory reduced by **85–91%** across interaction scenarios (from 276–342 MB down to 24–41 MB).
  - Bounded LRU `ArtworkCache` with explicit `inSampleSize` downsampling and direct stream decoding in Coil fetcher, eliminating raw JPEG re-encoding.
  - Hoisted lazy item keys, item types, and shape/brush allocations across all scrolling lists.
  - Moved `NodeLog` disk I/O off the UI thread via non-blocking daemon queue (`ArrayBlockingQueue(2048)`), eliminating StrictMode disk write violations.
  - Configured Compose Compiler stability configuration (`compose_compiler_config.conf`), cutting unstable arguments by **65.2%**.

### [2026-11-11] - Milestone v2.3: Phase 10 Polish, Windows Desktop Parity & Token Verification
- **120 Hz Scroll Performance Optimization (`POL-01`)**:
  - Implemented stable keys on all lazy item factories in `OnlineHomeView.kt`, `OfflineHomeView.kt`, `LibraryView.kt`, and `PlaylistDetailView.kt`.
  - Replaced high-frequency per-pixel `LaunchedEffect(listState.firstVisibleItemScrollOffset)` scroll observers with `snapshotFlow { ... }.distinctUntilChanged()`.
  - Pre-allocated `StartCenterOrigin = TransformOrigin(0f, 0.5f)` and `BaseLyricTextStyle` in `LyricLineItem.kt`, eliminating allocation overhead inside draw and graphicsLayer passes.
- **Windows Desktop Parity (`POL-02`)**:
  - Fixed `OpticalRailDock` puck vertical alignment by eliminating the hardcoded 62dp offset, restoring exact alignment: `selectedIndex * (tabHeightDp.value + tabSpacingDp.value)`.
  - Embedded `DesktopModeRailSwitcher` directly below `OpticalRailDock` in `SharedPlayerLayout.kt` for instant Online/Offline switching in expanded desktop mode.
- **Token Centralization & Zero Hardcoded Hex Elimination (`POL-03`)**:
  - Centralized semantic color tokens into `LiquidColors` in `GlassTokens.kt` (`SlateDark`, `SlateDeep`, `SlateNavy`, `CyanDeep`, `GoldHiRes`, `SkyIce`, etc.).
  - Replaced remaining hardcoded hex values across `OpticalRailDock.kt`, `ConvxLiquidDock.kt`, `ConvxMiniPlayer.kt`, `DeviceConnectPill.kt`, and `AudioFormatBadge.kt`.
  - Verified all tokens with automated unit tests in `DesignTokensTest.kt`.
- **Milestone Regression & Verification**:
  - 100% tests passing across `:core-ui:desktopTest`, `:core-dsp:test`, `:core-network:test`, `:core-common:test`, `:app-android:testDebugUnitTest`, and `:app-desktop:test`.
  - Zero architectural boundary violations (`ArchitectureBoundaryTest` passed).


### [2026-11-08] - Post-Port Hardware Fix 4: Equalizer & Settings Stack Overflow (SIGSEGV libhwui) Root Cause Fix & Stability Test
- **Root Cause Discovered via adb logcat on Physical Device (Android 16 / realme RMX3853)**:
  - Opening Equalizer or Settings crashed with `Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR) ... Cause: stack pointer is not in a rw map; likely due to stack overflow. 512 total frames in libhwui.so (prepareTreeImpl -> prepareListAndChildren)`.
  - In `MainActivity.kt`, page content was wrapped in `Modifier.layerBackdrop(pageBackdrop)` while `LocalControlBackdrop` was provided as `pageBackdrop`. In-page controls (`LiquidSlider`, `LiquidToggle`) sampled `LocalControlBackdrop.current` (`pageBackdrop`), attempting to record `pageBackdrop`'s RenderNode into its own children's display lists, creating a recursive cyclic scene graph that triggered infinite recursion during Skia's `prepareTreeImpl`.
- **Architectural Fix**:
  - `LayerBackdrop.kt`: Added `val isRecording: Boolean` guard. `DrawScope.drawBackdrop()` immediately aborts if `isRecording == true` to prevent self-sampling RenderNode cycles.
  - `LayerBackdropModifier.kt`: Wrapped `recordLayer()` with `backdrop._isRecording = true` in `try { ... } finally { backdrop._isRecording = false }`.
  - `MainActivity.kt` & `SharedPlayerLayout.kt`: Isolated the in-page screen container inside `CompositionLocalProvider(LocalControlBackdrop provides remember { emptyBackdrop() })`. In-page controls never sample parent page layer, while floating chrome siblings (`ConvxLiquidDock`, `ConvxMiniPlayer`) above page layer sample `pageBackdrop` safely.
  - `LiquidControls.kt`: `LiquidToggle` detects `effectiveSuppliedBackdrop is EmptyBackdrop` and safely falls back to lightweight plain squircle mode.
- **Verification & Test Automation**:
  - Created automated instrumented test `ScreenStabilityTest.kt` covering `EqualizerScreen`, `SettingsScreen`, `CustomizeUiScreen`, `WidgetSettingsScreen`, slider/toggle interactions, and negative recursion guard test (`RMX3853 Tests 6/6 completed, 0 failed, BUILD SUCCESSFUL`).
  - Re-ran `GlassPixelProofTest.kt` on physical device (`1/1 completed, 0 failed, BUILD SUCCESSFUL`).
  - Live adb cold start into Equalizer and Settings verified running with 0 crashes (`pidof com.nodeplayer.music` verified alive).
  - Dumped `dumpsys gfxinfo`: 206 frames rendered, 50th percentile 15ms, 90th percentile 20ms, GPU 50th percentile 7ms.


### [2026-11-08] - Post-Port Hardware Fix 3: Compose 1.7 Upgrade & Native GraphicsLayer RenderEffect with Pixel Proof
- **Build Toolchain Upgrade (Option A)**:
  - Upgraded Kotlin to `2.0.21`, AGP to `8.5.2`, Compose Multiplatform to `1.7.3`, KSP to `2.0.21-1.0.28`, and Compose BOM to `2024.10.01`.
  - Applied `org.jetbrains.kotlin.plugin.compose` across multiplatform and Android modules.
- **Deleted GraphicsLayer Shims**:
  - Deleted custom shims (`GraphicsLayer.kt`, `GraphicsLayer.android.kt`, `GraphicsLayer.desktop.kt`, `GraphicsLayerCompat.kt`, `GraphicsContextCompat.kt`).
  - Switched to native `androidx.compose.ui.graphics.layer.GraphicsLayer` from Compose UI 1.7, restoring hardware compositor `RenderEffect` on Android.
- **Haze Purge & Control Modernization**:
  - Fully purged all legacy `dev.chrisbanes.haze` dependencies, states, and modifier extensions.
  - Replaced Material3 controls across Android screens with `LiquidSlider`, `LiquidToggle`/`LiquidSwitch`, `LiquidChip`, `LiquidRadioButton`, and `LiquidCheckbox`.
- **Physical Device Pixel Proof Verification (`GlassPixelProofTest.kt`)**:
  - Executed automated instrumented test on realme device (RMX3853, Android 16) with 100% PASS:
    - Stripe Background Edge Energy: `0.125`
    - Dock Body Interior Edge Energy: `0.0283`
    - Energy Ratio (Dock / Stripe): `22.68%` (<= 40% target passed)
    - Dock Mean Luminance: `0.448` (89.5% of page luminance -> non-black passed)
    - Dock Luminance StdDev: `0.193` (non-flat passed)
- **App Launch Verification**:
  - Installed `app-android-debug.apk` via `adb install -r`.
  - Process PID `8316` verified running stably with 0 crashes.

### [2026-11-08] - Ground-Up Liquid Glass Rebuild: Phase 5 Diagnostics Test Screen, Verification, Builds & Acceptance
- **Dedicated Optical Diagnostics Test Screen & Triple-Zone Pattern (VERIFY-01)**:
  - Created `OpticalDiagnosticsView.kt` in `:core-ui` featuring a high-contrast, vertically scrollable triple-zone backdrop:
    1. Alternating monochrome high-contrast vertical and diagonal stripes.
    2. Dense multi-size typography blocks (display headline, body, caption, code, and small print).
    3. High-frequency pixel alignment grid (sub-pixel grid lines and concentric circles) to expose distortion and curvature.
  - Layered the entire active liquid glass chrome over the test pattern via `Modifier.layerBackdrop(pageBackdrop)`:
    - 64dp capsule dock (`ConvxLiquidDock` / `LiquidDock`) with sliding tab puck.
    - 56dp squircle mini player capsule (`ConvxMiniPlayer`) floating 8dp above the dock.
    - 56dp circular Search button (`LiquidButton`).
    - Interactive live test `LiquidSlider` directly embedded over the test pattern.
  - Wired navigation routes in `SettingsView.kt` ("Liquid Glass Optical Test") and Android `SettingsScreen.kt` ("Settings > Diagnostics & Graphics > Liquid Glass Optical Test").
- **R8 / ProGuard Keep Rules Enforcement (VERIFY-04)**:
  - Audited `app-android/proguard-rules.pro` to guarantee full preservation of runtime shaders, models, and serialization:
    - `com.kyant.backdrop.**` (runtime shaders, AGSL strings, uniforms, and render effects).
    - `com.nodeplayer.model.GlassControlsConfig` and data models.
    - Native WASAPI JNA bindings and Room DAOs.
- **Multi-Module Regression Test Suite Execution (VERIFY-03)**:
  - Verified 100% clean test execution across all modules: `:core-model:test`, `:core-common:test`, `:core-ui:desktopTest`, and `:app-android:testDebugUnitTest` (0 failures, 0 errors).
- **Multiplatform Production & Debug Binaries Assembly (VERIFY-03)**:
  - Built Android debug APK via `:app-android:assembleDebug` (`app-android/build/outputs/apk/debug/app-android-debug.apk`, 27,524,414 bytes) ready for physical device sideloading via `adb install -r`.
  - Compiled Windows Desktop JVM target via `:app-desktop:compileKotlin` with classes verified on disk.
- **Visual Acceptance Checklist & Human Review Protocol (VERIFY-02)**:
  - Generated `.planning/phases/05-diagnostics-test-screen-verification-builds-acceptance/05-UAT.md` cataloging the 5 prompt rubrics marked `[ ] HUMAN CHECK PENDING (phone photo review)`:
    1. Body blur & refraction.
    2. Zero sharp text inside dock.
    3. Puck chromatic fringing.
    4. Continuous slider drag.
    5. Zero card self-refraction.
- **Documentation Synchronization & Open-Source Attribution (VERIFY-05)**:
  - Logged emulator GPU SkSL precision variances in `docs/state/KNOWN_ISSUES.md`.
  - Updated `docs/platforms/CHANGES.md`, `docs/state/FIXES_AND_STATUS.md`, and `docs/reference/SETTINGS.md`.
  - Confirmed Kyant0 `AndroidLiquidGlass` Apache-2.0 attribution in `THIRD_PARTY_LICENSES.md`.
  - Observed strict local-only git policy on `feature/liquid-dock`.

### [2026-11-08] - Ground-Up Liquid Glass Rebuild: Phase 4 Global Settings, Per-Item Overrides & Real-Time Live Preview
- **Master Liquid Glass Controls Card & Presets (SETTINGS-01)**:
  - Implemented `MasterControlsCard` in `CustomizeUiView.kt` providing a global master on/off switch (`masterEnabled`), 3 tiered presets (`Full`, `Balanced`, `Light`), and 12 shared master optical and spring sliders.
  - Added preset change safety dialog ("Keep existing overrides" vs "Reset all to preset") preventing accidental overwrite of custom adjustments.
  - Master OFF switch turns all components into high-performance zero-sampling continuous squircles (`ResolvedGlassParams.LIGHTER_SQUIRCLE`).
- **9 Per-Item Override Accordions & List Optimization (SETTINGS-02)**:
  - Built 9 collapsible accordion cards in `CustomizeUiView.kt` for Buttons, Toggles, Sliders, Dock, Chips, Dialogs, Mini Player, Now Playing, and Widgets.
  - Implemented immediate "Use Global" switch with smooth expand/collapse chevron animation and "Reset to Global" per-item restoration.
  - Added granular "Force Lighter Style in Lists" toggle for Buttons, Toggles, and Chips, eliminating heavy shader passes in dense lists.
- **Unified Domain Models, Versioned Serialization & Persistence (SETTINGS-03)**:
  - Created pure Kotlin `GlassControlsConfig.kt` in `:core-model` with zero Android framework dependencies.
  - Implemented `GlassControlsSerializer.kt` handling versioned JSON format (`NodePlayerGlassControls` v1) with 64KB bounds, numeric clamping, and corrupt payload recovery.
  - Added 1-click clipboard export/import dialogs and factory reset engine.
  - Integrated persistent reactive storage via `AppSettingsRepository.kt` (DataStore) on Android and `DesktopSettingsStore.kt` on Windows Desktop.
- **Interactive Live Preview Canvas & Dynamic GPU Cost Meter (SETTINGS-04)**:
  - Built `LivePreviewCanvas.kt` pinned/sticky at top on mobile portrait and in a 2-column split (45% preview / 55% controls) on desktop 16:9 (>840dp).
  - Implemented dynamic animated caustics and chromatic metaballs backdrop (`DynamicPreviewCausticsBackdrop`) using stable root textures (`rememberLayerBackdrop()`).
  - Added real-time GPU cost meter with empirical frame-time calculation (`calculateGpuCost`) and color-coded classification badge (`LOW`, `MODERATE`, `HIGH`, `INTENSE`).
  - Rendered complete miniature interactive suite: mini player capsule with animated equalizer bars, mini button with spring press scale, draggable mini toggle, selectable mini chip, continuous mini slider with touch slop isolation, and mini dock with sliding tab puck.
- **Dynamic Parameter Wiring & Exhaustive SETTINGS.md Audit (SETTINGS-05)**:
  - Provided `LocalGlassControlsConfig` in `SharedPlayerLayout.kt`, dynamically consumed by `LiquidControls.kt`, `ConvxLiquidDock.kt`, and `ConvxMiniPlayer.kt`.
  - Updated `docs/reference/SETTINGS.md` with an exhaustive audit of all 15+ settings parameters, line references, GPU cost formulas, and JSON schemas.
- **Multiplatform Verification**:
  - All tests passing in `:core-model:test` and `:core-ui:desktopTest`.
  - Full project compilation clean across `:app-android:compileDebugKotlin` and `:app-desktop:compileKotlin`.
  - Visual inspections cataloged as "human check pending" in `04-UAT.md`.


### [2026-11-08] - Ground-Up Liquid Glass Rebuild: Phase 3 Drag Controls Everywhere & Dock Auto-Hide
- **Continuous Gesture Draggable `LiquidSlider` (CTRL-01)**:
  - Upgraded `LiquidSlider.kt` with continuous gesture dragging using `DampedDragAnimation`, horizontal velocity stretch (`scaleX`), vertical press-squish (`scaleY`), and spring release snapping.
  - Enforced $\ge$48dp touch hitboxes on both thumb and container (`Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`), tap-to-seek, division-by-zero protection, and safe clamped bounds.
- **Unified `LiquidControls.kt` Suite & Tap/Drag `LiquidToggle` (CTRL-02)**:
  - Enhanced `LiquidToggle.kt` supporting both instantaneous tap toggling and fluid continuous drag tracking with spring snap at the 0.5f threshold.
  - Built comprehensive `LiquidControls.kt` suite in `core-ui`: `LiquidButton`, `LiquidIconButton`, `LiquidChip`, `LiquidSegmentedControl`, `LiquidDialog`, `LiquidCheckbox`, and `LiquidRadioButton`.
  - Added lighter glass styling mode (`lighterStyle = true`) utilizing plain continuous squircle geometry (`ContinuousRoundedRectangle`) without heavy backdrop sampling for high frame rate (60/120 fps) list items.
  - Converted legacy `LiquidGlassControls.kt` into a backwards-compatible delegate façade.
- **Systematic Plain Material Controls Replacement (CTRL-03)**:
  - Replaced all direct uses of plain Material controls (`Button`, `IconButton`, `Switch`, `Slider`, `FilterChip`, `TabRow`, `SegmentedButton`, `AlertDialog`, `RadioButton`, `Checkbox`) across all app screens: `EqualizerView`, `SettingsView`, `CustomizeUiView`, `LibraryView`, `NowPlayingView`, `BottomPlayerDeck`, `BottomPlayerDock`, and `NodeConnectPairingDialog`.
- **Intelligent Dock Nested Scroll Auto-Hide & Return Triggers (CTRL-04)**:
  - Implemented `DockScrollController.kt` managing `offsetFraction` and 4 return triggers: 1) scrolling up, 2) list top reached, 3) 2-second scroll pause timeout, 4) bottom edge tap (~40dp invisible touch target active when hidden).
  - Wired `SharedPlayerLayout.kt` to translate floating dock and mini player by `120.dp * offsetFraction` with spring physics (`dampingRatio = 0.82f, stiffness = 380f`) and alpha fade (`1f - offsetFraction`).
  - Respected user settings: `dockAutoHideEnabled`, `dockAutoHideMiniPlayer`, `dockScrollSensitivity`, and `dockReturnDelayMs`.
- **Node Connect Header Integration & List Smoothness (CTRL-05)**:
  - Preserved `DeviceConnectPill` in `HomeView.kt` mobile portrait header, `HomeScreen.kt`, desktop top bar, and Windows rail.
  - Maintained 100% operational peer discovery, pairing, and audio casting with lighter styling across list rows.
- **Multiplatform Verification**:
  - All tests passing in `:core-ui:desktopTest` (including `LiquidControlsGestureTest`, `LiquidSliderMathTest`, and `DockScrollControllerTest`).
  - Full project compilation verified across `:app-android:compileDebugKotlin` and `:app-desktop:compileKotlin`.
  - Visual inspections marked as "human check pending" in `03-UAT.md`.


### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 2 Layering & LiquidBottomTabs Dock + Mini Player
- **Root Layer Stacking Inversion (D-01, DOCK-01)**:
  - Inverted the root Compose layout hierarchy in `SharedPlayerLayout.kt`: root full-bleed `Box` records scrolling page content and background into a single `pageBackdrop` using `Modifier.layerBackdrop(pageBackdrop)`.
  - Floating chrome (`ConvxMiniPlayer`, `LiquidDock`, `LiquidButton`) anchored as sibling overlays above the page layer in z-order, receiving `pageBackdrop`.
  - Introduced `LocalBottomBarPadding` dynamic composition local providing 136dp (when mini player is active) and 80dp (dock-only) so scrollable lists glide edge-to-edge behind the glass.
- **Elimination of Page Card Self-Refraction (D-02, DOCK-02)**:
  - Stripped all `drawBackdrop` and lens refraction shaders from inner page cards and badges in `GlassComponents.kt` and screens (`HomeView.kt`, `LibraryView.kt`, `EqualizerView.kt`, `CustomizeUiView.kt`, `SettingsView.kt`).
  - Rendered all page cards using plain squircle surfaces (`ContinuousRoundedRectangle`), eliminating rainbow edge fringes and self-sampling artifacts.
- **Upstream LiquidBottomTabs Dock & Search Companion (D-03, D-04, DOCK-03, DOCK-04)**:
  - Built `LiquidDock.kt` integrating upstream `LiquidBottomTabs`: 64dp capsule bar, 8dp blur, vibrancy, (24dp, 24dp) lens, ~40% alpha container, and hidden tab copy with accent color tint.
  - Implemented 56dp sliding puck using `rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)`, press-scaled lens, chromatic aberration, and `DampedDragAnimation` over 4 tabs (Home, Library, Equalizer, Settings).
  - Built companion 56dp circular Search `LiquidButton` floating adjacent to the dock capsule, sharing `pageBackdrop`.
- **Floating Mini Player Capsule (D-05, DOCK-04)**:
  - Refined `ConvxMiniPlayer.kt` to a 56dp squircle capsule floating 8dp above the dock capsule.
  - Samples `pageBackdrop` without sampling the dock, featuring squircle album artwork thumbnail, title, artist, live audio-reactive equalizer bars, and isolated transport controls.
- **Desktop Windows Rail & Deck Integration (D-06, DOCK-05)**:
  - Enabled `pageBackdrop` sampling across `OpticalRailDock.kt` (left vertical rail) and `BottomPlayerDeck.kt` (bottom player deck) on Windows desktop via SkSL shaders.
- **Multiplatform Verification**:
  - `:core-ui:desktopTest`, `:app-desktop:compileKotlin`, and `:app-android:compileDebugKotlin` all compile and pass cleanly. Visual inspections marked as "human check pending" in `02-UAT.md`.

### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 1 Plan 02 Staged Clean Slate & Legacy Purge
- **Callers Migrated to `com.kyant.backdrop.*`**:
  - `ConvxLiquidDock.kt`, `ConvxMiniPlayer.kt`, `OpticalRailDock.kt`, `LiquidGlassControls.kt`, `NowPlayingView.kt`, `BottomPlayerDock.kt`, and `NowPlayingSheet.kt` switched from `com.nodeplayer.ui.component.backdrop.*` to `com.kyant.backdrop.*`.
  - `GlassShaderSelfTest.kt` in `app-android`: Re-pointed AGSL shader diagnostic self-test to `com.kyant.backdrop.internal.RoundedRectRefractionShaderString` with updated uniform bindings (`size`, `offset`, `cornerRadii`, `refractionHeight`, `refractionAmount`, `depthEffect`), preserving offscreen pixel validation and safe fallback.
  - `app-android/proguard-rules.pro`: Replaced legacy package keep rules with `com.kyant.backdrop.**`.
- **Legacy Code Purged (37 files deleted)**:
  - Deleted old cut-down vendored package directory and platform counterparts:
    - `core-ui/src/androidMain/kotlin/com/nodeplayer/ui/component/backdrop/Platform.android.kt`
    - `core-ui/src/androidMain/kotlin/com/nodeplayer/ui/component/backdrop/RuntimeShader.android.kt`
    - `core-ui/src/androidMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/RenderEffect.android.kt`
    - `core-ui/src/desktopMain/kotlin/com/nodeplayer/ui/component/backdrop/Platform.desktop.kt`
    - `core-ui/src/desktopMain/kotlin/com/nodeplayer/ui/component/backdrop/RuntimeShader.desktop.kt`
    - `core-ui/src/desktopMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/RenderEffect.desktop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/Backdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/BackdropEffectScope.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/CompatibilityShims.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/DrawBackdropModifier.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/Platform.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/RuntimeShader.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/RuntimeShaderCache.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/backdrops/Backdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/backdrops/CanvasBackdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/backdrops/EmptyBackdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/backdrops/LayerBackdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidBottomTab.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidBottomTabs.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidButton.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidSlider.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidToggle.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/utils/DampedDragAnimation.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/utils/DragGestureInspector.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/utils/InteractiveHighlight.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/effects/Blur.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/effects/ColorFilter.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/effects/Lens.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/effects/RenderEffect.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/highlight/Highlight.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/highlight/HighlightStyle.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/Outline.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/RenderEffect.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/Shaders.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/ShapeProvider.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/shadow/InnerShadow.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/shadow/Shadow.kt`
  - Removed temporary `CompatibilityShims.kt`.
  - Zero references to `com.nodeplayer.ui.component.backdrop` remain across all production code.

---

### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 3 Real Drag Controls, Animations & Dock Auto-Hide
- **Continuous Gesture Dragging for Liquid Sliders (core-ui)**:
  - LiquidSlider.kt: Refactored pointer handling to capture drag gestures across the full container row (detectDragGestures).
  - Added real-time tracking with thumb velocity squish and stretch, live onValueChange dispatch, and spring release.
  - Guaranteed minimum 48dp touch interaction target across all glass controls (LiquidGlassSlider, LiquidGlassSwitch, LiquidGlassButton, LiquidGlassIconButton, LiquidGlassChip, LiquidGlassSegmentedControl).
- **Dock Scroll Auto-Hide Integration (pp-android)**:
  - MainActivity.kt: Wired DockScrollController and 
ememberDockScrollConnection to screen content with 
`nestedScroll`.
  - Smooth vertical slide-down and fade animation during downward list scrolling, restoring on upward scroll, top of list, or pause timeout.
  - Respects dockAutoHideEnabled, dockAutoHideMiniPlayer, dockScrollSensitivity, and dockReturnDelayMs.
- **Spring Physics & Motion Tokens**:
  - Applied Convx critically damped springs (dampingRatio = 0.82f, stiffness = 380f) across dock puck, tab switches, and sheets.
  - Respected user Motion Level settings (Off, Reduced, Full) and system 'remove animations' accessibility preference.
- **Unit Testing**:
  - Added LiquidControlsGestureTest.kt verifying slider normalized delta conversions, clamping bounds, and toggle snap thresholds.
- **Status**: Completed Phase 3. Both Android assembleDebug and Desktop tests pass cleanly. Visual and device checks marked 'human check pending'.

---

### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 2 Convx-Style Dock & Mini Player
- **Convx-Style Liquid Glass Dock (core-ui, app-android, app-desktop)**:
  - ConvxLiquidDock.kt: Built continuous squircle capsule dock (ContinuousCapsule) matching Convx reference.
  - Interactive sliding lens puck with DampedDragAnimation and Convx critically damped spring physics (dampingRatio = 0.82f, stiffness = 380f).
  - Active tab accent tinting and circular glass Search button.
- **Convx-Style Floating Mini Player (core-ui, app-android, app-desktop)**:
  - ConvxMiniPlayer.kt: Floating continuous squircle capsule (ContinuousRoundedRectangle(20.dp)) hovering directly above the dock.
  - Squircle artwork thumbnail, title/artist metadata, audio-reactive animated RMS equalizer bars, play/pause, and skip controls.
- **Clean Layer Separation (Zero Torn Text)**:
  - Text, icons, artwork, and buttons rendered strictly in a clean pass above the glass substrate, eliminating torn text or self-refraction artifacts.
- **Desktop Windows Rail & Player Alignment**:
  - OpticalRailDock.kt: Updated with continuous squircle geometry (ContinuousRoundedRectangle(48.dp)) and Convx spring puck.
- **Obsolete Code Purge**:
  - Deleted FrostedNavAndPlayer.kt. Updated CustomizeUiView.kt and SETTINGS.md tracing to point to ConvxLiquidDock.kt and ConvxMiniPlayer.kt.
- **Status**: Completed Phase 2. Android assembleDebug, desktopTest, and app-desktop test all pass cleanly. Visual and physical checks marked 'human check pending'.

---

### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 1 Clean Slate & Full Upstream Library Integration
- **Full Upstream Kyant0 Library & Desktop Runtime Effect Verification**:
  - Full Kyant0 backdrop engine and complete demo catalog integrated with complete multiplatform support (DampedDragAnimation.kt, DragGestureInspector.kt, InteractiveHighlight.kt, LiquidSlider.kt, LiquidToggle.kt, LiquidButton.kt, LiquidBottomTabs.kt).
  - Desktop Skia SkSL Engine: Verified high-fidelity RuntimeEffect.makeForShader SkSL multiplatform shader bridge (Platform.desktop.kt, RuntimeShader.desktop.kt, RenderEffect.desktop.kt) on Windows Compose Multiplatform.
  - Multiplatform Continuous Squircle Geometry: Implemented ContinuousRoundedRectangle and ContinuousCapsule (core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/shapes/ContinuousRoundedRectangle.kt) providing continuous iOS HIG squircle curves without platform-specific dependencies.
  - Verified compilation: :core-ui:desktopTest, :app-desktop:compileKotlin, :app-android:compileDebugKotlin, and :app-android:assembleDebug all build and pass cleanly.
- **Purge Plan Audit & Retention**:
  - Obsolete glass prototypes scheduled for replacement in Phase 2 & 3: FrostedNavAndPlayer.kt (Android dock), OpticalRailDock.kt, and BottomPlayerDock.kt (Windows dock & deck).
  - Preserved critical architectures: GlassThemeConfig settings persistence, AppSettingsRepository, playback engines (PlaybackService, WindowsPlaybackEngine), NodeConnectHub Device Connect, and GlassShaderSelfTest fallback mechanisms.
- **Status**: Completed Phase 1. Android assembleDebug and Desktop tests pass. Visual and device checks marked 'human check pending'.

---

### [2026-11-07] - Liquid Glass Revamp: Phase 5 More Liquid Glass Settings in Customize UI
- **Live Preview & Granular Controls (`core-ui`, `core-model`)**:
  - `GlassLivePreview.kt`: Interactive liquid glass sample card displaying real-time feedback as optical parameters are adjusted.
  - Granular parameters: Added `glassRefractionHeightDp`, `glassRefractionAmountDp`, `glassChromaticFringe`, and `glassBackdropScale`.
  - Component toggles: Added independent toggles `dockGlassLensEnabled`, `miniPlayerGlassEnabled`, `nowPlayingGlassEnabled`, `controlsGlassEnabled`, and `widgetsGlassEnabled`.
  - Presets & Reset: Integrated GPU cost indicators and "Reset to recommended" preset action in `GlassThemeConfig`.
- **Exhaustive Settings Reference (`docs/reference/SETTINGS.md`)**:
  - Comprehensive documentation of all settings with types, defaults, GPU impact hints, and source file + line number traceability.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`). Device checks marked "human check pending".

---

### [2026-11-07] - Liquid Glass Revamp: Phase 4 Dock Hides While Scrolling
- **Intelligent Dock Scroll Auto-Hide (`core-ui`, `core-model`)**:
  - `DockScrollController.kt`: State holder tracking scroll delta accumulation and spring-animating dock slide-down and fade. Includes automatic timeout timer to restore dock after user stops scrolling.
  - `rememberDockScrollConnection`: Intercepts `onPreScroll` and `onPreFling` on long lists (Home, Library) with edge fling detection.
  - `GlassThemeConfig`: Added preferences `dockAutoHideEnabled`, `dockAutoHideMiniPlayer`, `dockScrollSensitivity`, and `dockReturnDelayMs`.
  - Windows Desktop: Kept stationary rail and deck visible by default, with opt-in support.
  - `DockScrollControllerTest.kt`: Unit tests verifying scroll down hide, scroll up restore, and timeout recovery.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`). Device checks marked "human check pending".

---

### [2026-11-07] - Liquid Glass Revamp: Phase 3 Smooth iOS-like Animations (like Convx)
- **Spring Physics Engine (`core-ui`, `core-model`)**:
  - `MotionTokens.kt`: Added Convx-matched spring configurations (`ConvxPuck`: `dampingRatio = 0.82f`, `stiffness = 380f`; `DragTracking`: `dampingRatio = 0.95f`, `stiffness = 900f`).
  - `MotionLevel`: Added `FULL`, `REDUCED`, and `OFF` modes to `GlassThemeConfig` in `core-model`.
  - System Accessibility: Automatically drops spring animations down to instantaneous snap transitions when `reduceMotion` or system animations are disabled.
  - `MotionTokensTest.kt`: Unit tests verifying spring resolution across all motion tiers.
- **Draw/Layout Phase Optimization (`core-ui`)**:
  - Enforced GPU-accelerated layer transforms (`graphicsLayer { scaleX = ...; scaleY = ... }`) for press scales and thumb bounce, avoiding per-frame recomposition.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`). Device checks marked "human check pending".

---

### [2026-11-07] - Liquid Glass Revamp: Phase 2 Revamp the Glass Code
- **Unified Glass Architecture (`core-model`, `core-ui`)**:
  - `GlassQuality`: Introduced `FULL`, `BALANCED`, and `LIGHT` quality modes to `GlassThemeConfig` in `core-model`.
  - `GlassDesignTokens.kt`: Added canonical design tokens in `core-ui` unifying radii, optics, blur, rim specular alpha, and vibrancy, dynamically adapting based on `GlassQuality`.
  - `GlassTokensTest.kt`: Unit tests verifying token resolution for all three glass quality tiers.
- **Fallbacks & Multiplatform Interface**:
  - Single multiplatform backdrop API in `core-ui` backed by AGSL on Android and SkSL on Windows Desktop.
  - Verified `GlassShaderSelfTest` and frosted fallback mode functionality.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`, `:core-common:test`). Device checks marked "human check pending".

---

### [2026-11-07] - Liquid Glass Revamp: Phase 1 Fix What Is Broken
- **LiquidSlider Math & Layout Safety (`core-ui`)**:
  - `LiquidSlider.kt`: Clamped computed track width and placeable bounds to `>= 0`, guarding against negative dimensions and division-by-zero on drag.
  - `LiquidSliderMathTest.kt`: Added unit test covering boundary cases, zero widths, and narrow constraints.
- **Dock Refraction & Aberration Tuning (`core-ui`, `app-android`)**:
  - `FrostedNavAndPlayer.kt` & `OpticalRailDock.kt`: Scaled refraction lens parameters proportionally to container heights; tuned default chromatic fringe from 0.8f down to 0.25f–0.35f to eliminate color fringing and edge glitches.
- **Controls Optimization & Plain-Compose Glass Fallback (`core-ui`)**:
  - `LiquidGlassControls.kt`: Switched `LiquidGlassIconButton`, `LiquidGlassSegmentedControl`, and `LiquidGlassChip` to lightweight plain-Compose styling (translucent fills, specular border gradient, highlight, shadow) without costly backdrop passes. Reserved lens shaders for hero components.
- **Build & Diagnostics (`core-ui`, `app-android`)**:
  - `proguard-rules.pro`: Added keep rules for liquid glass backdrop runtime shaders and render effects for R8 release builds.
  - `FrameTimeOverlay.kt`: Created on-screen real-time FPS and frame-time profiling overlay.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`, `:core-dsp:test`, `:core-common:test`, `:core-network:test`). Device checks marked "human check pending".

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

---

### [2026-11-05] - Step 11: Node Connect Operationalization & Diagnostics Overhaul
- **Core Network Foundation (`core-network`, `core-common`)**:
  - `NodeConnectHub.kt`: Resolved double-handshake bug on outgoing client connections. Factored out `registerAndProcessConnection(conn)` for completed handshakes (`connectToHost`, `pairWithHost`), eliminating spurious 8s timeout terminations. Added persistent device caching upon handshake completion.
  - `NodeServer.kt`: Added `SecurityLevel` configuration (`SIMPLE` as default, `STRICT` for certificate pinning and PIN requirement). Dynamic port binding across fallback range `54820..54830` with `0.0.0.0` all-interface listener.
  - `PinningTrustManager.kt` & `NodeSslContext.kt`: Added `PermissiveTrustManager` enabling unauthenticated TLS in Simple Mode without sacrificing stream encryption.
  - `NodeDiscovery.kt`: Overhauled to multi-method discovery: global broadcast (`255.255.255.255`), per-interface subnet broadcasts (`x.x.x.255`), multicast (`239.255.77.88`), default gateway probe, and 150ms parallel sweep across local `/24` subnets.
  - `NodeLog.kt`: Persistent file logging to app-private log directory with structured timestamping and feature tags (`nodeconnect/discovery`, `nodeconnect/connect`, `nodeconnect/server`, `nodeconnect/hub`).
- **Android Platform (`app-android`)**:
  - Manifest & Permissions: Added `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, and `CAMERA`.
  - `PlaybackService.kt`: Added `acquireMulticastLock()` via `WifiManager.createMulticastLock("node_player_discovery")` to prevent Android Wi-Fi power-saving from dropping UDP broadcasts.
  - `QrScannerSheet.kt`: Built offline CameraX camera scanner with ZXing `MultiFormatReader`, scan reticle, and torch control.
  - `MainActivity.kt`: Integrated "Allow Connection?" modal dialog for incoming peers with "Allow Once" / "Always Allow" / "Deny" choices.
- **Desktop Platform & Shared UI (`app-desktop`, `core-ui`)**:
  - `Main.kt`: Fixed Compose layout imports and wired `onAllowPrompt` modal. Standalone executable packaging via `createDistributable`.
  - `NodeConnectPairingDialog.kt`: 3-tab layout: "Devices" (peer list with discovery type badges, one-tap Connect, manual IP connect), "My QR Code" (compact offline URI QR code with candidate IPs and Simple Mode warning banner), and "Doctor" (adapter table, port listener status, interactive TCP ping tool, gateway probe, log export).
- **Status**: Completed, 21 unit tests pass, real-device bidirectional discovery verified in logs over phone Wi-Fi hotspot with 4-9ms latency.

---

### [2026-11-05] - Step 12: Live Control Verification, USB ADB Link & Granular Feature Switches
- **Core Network & Discovery (`core-network`)**:
  - `AdbBridge.kt`: Implemented deterministic zero-Wi-Fi USB debugging tunnel using `adb forward tcp:54825 tcp:54820` (PC to Phone) and `adb reverse tcp:54826 tcp:54820` (Phone to PC). Added `AdbBridgeTest.kt` unit test.
  - `NodeConnectHub.kt`: Surfaced `connectedPeers: StateFlow<List<ConnectedPeer>>` with live heartbeat RTT (down to 5-8ms). Added `startUsbDebuggingLink()` and transport helper functions (`sendRemotePlayPause`, `sendRemoteNext`, `sendRemotePrevious`, `sendRemoteSeek`, `sendRemoteVolume`, `disconnectDevice`).
  - `NodeConnectConfig.kt`: Expanded config to support 10 granular feature toggles, discovery method toggles (mDNS, UDP Broadcast, Hotspot Gateway Probe, Subnet Scan), Transport Encryption (TLS 1.3), Security Level (Simple vs Strict), and Verbose Network Logging with persistent storage.
- **Shared UI & Components (`core-ui`)**:
  - `NodeConnectPairingDialog.kt`:
    - Tab 0 (Devices): Active connection card with live RTT latency badge, transport controls (`[⏮]`, `[▶⏸]`, `[⏭]`), Disconnect button, and action buttons (`[Cast Audio]`, `[⇄ Handoff]`). Added Simple Mode security warning banner.
    - Tab 1 (My QR): Clean offline QR code containing candidate IP list without certificate pinning or PIN code bloat.
    - Tab 2 (Doctor): Added 1-click `[Setup USB Debugging Link (ADB 127.0.0.1)]` button alongside Socket Ping Test and Gateway Probe.
    - Tab 3 (Settings): Added 4 sections: Master & Access, Security & Trust (Security Level, Trust All, TLS 1.3 toggle), Discovery Methods (mDNS, UDP broadcast, Gateway probe, Subnet scan), Feature Toggles (10 independent switches), and Advanced / Tuning (Verbose logging, Jitter buffer, Keepalive interval).
- **Live Physical Verification (realme RMX3853 Android 16 ↔ Windows 11 PC)**:
  - Verified remote control execution from phone to PC with real log lines: `PLAY_PAUSE`, `NEXT`, `PREVIOUS` executed locally on PC playback engine.
  - Verified ADB USB loopback forwarding with `TcpTestSucceeded: True` and `REVERSE_SUCCESS`.
- **Status**: Completed (L3 Evidence achieved across all core functions).

---

### [2026-11-05] - Step 13: Pre-DSP PCM Audio Casting, Handoff & L4 User Verification
- **Audio Cast & ExoPlayer Threading (`app-android`, `core-network`)**:
  - `PlaybackService.kt`: Resolved `IllegalStateException: Player is accessed on the wrong thread` by reading `currentPosition` strictly on the main thread before dispatching raw PCM capture taps to `Dispatchers.IO`.
  - `NodeConnectCastService.kt`: Added `nodeconnect/cast` logging tags and clean start/stop lifecycle management.
  - `WindowsPlaybackEngine.kt`: Added handler for incoming remote cast stream initialization and PCM audio buffer queuing.
- **Mid-Track Handoff (`core-network`)**:
  - `NodeConnectHandoffService.kt`: Implemented fuzzy track matching fallback on desktop when identical local file paths do not exist.
- **Live Evidence & L4 User Verification**:
  - Audio Cast start and stop confirmed live between realme RMX3853 and Windows PC.
  - User confirmed core Node Connect functionality: **L4 Verified by User** ("its working").
- **Status**: Completed (L4 achieved for Core Link, Remote Control, Audio Cast, and Handoff).



### [2026-11-05] - Step 7: Node Connect UI Polish, Cross-Platform Search, Sleep Timer & Stability
- **Phone UI & Shaders**:
  - `GlassRefractionShader.kt` & `FrostedNavAndPlayer.kt`: Removed `glassLensBubble` AGSL shader distortion from the dock tab selector pill. Flattened the middle field and softened the border to 0.75dp glass shimmer (`alpha = 0.14f`), completely eliminating the "Saturn / satin ring" artifact while maintaining crystal-clear liquid-glass transparency.
  - `CrossfadePlayerManager.kt`: Fixed playback `FileNotFoundException` by checking local filesystem paths (`java.io.File(track.path)`) with `Uri.fromFile` before falling back to MediaStore IDs. Resolves song playback failures for re-scanned or searched tracks.
- **Desktop UI & Navigation**:
  - `OpticalRailDock.kt`: Removed pointer drag gesture handler that previously snapped navigation back to Home (index 0) upon clicking Library or other tabs. Dock container made translucent (`Color(0xFF0F172A).copy(alpha = 0.22f)`).
  - `SharedPlayerLayout.kt`: Enhanced Search Capsule to transition directly from Home or any screen into Library search results upon typing. Added dynamic artwork accent extraction (`extractVibrantColor`) using Skia that smoothly animates the desktop aura and highlights (650ms tween).
  - `BottomPlayerDeck.kt`: Added Sleep Timer button with dropdown menu (15, 30, 45, 60, 90 mins, turn off).
- **Node Connect Cross-Platform Search & Artwork Transfer**:
  - `NodeConnectLibraryService.kt`: Added `sequenceId = reqId` in `sendPayload` for `ARTWORK_RESULT` frames, fixing thumbnail request-response matching. Added string unescaping.
  - `NodeConnection.kt`: Supported explicit `sequenceId` in `sendPayload`.
  - `NodeConnectHub.kt` & `PlaybackService.kt`: Passed `artworkProvider` lambda returning compressed JPEG thumbnail bytes. Prefetched and cached remote artwork into in-memory `ArtworkCache`.
  - `PlatformArtworkProvider.desktop.kt`: Checked `ArtworkCache.getBytes` in `loadArtwork` before attempting local file reads, enabling instant rendering of remote peer thumbnails and cast album art.
- **Audio Streaming & Jitter Buffer**:
  - `JitterBuffer.kt` & `NodeConnectCastService.kt`: Increased buffer settings (`targetBufferMs = 700`, `minBufferMs = 450`, `maxBufferMs = 2500`) to eliminate audio jitter and micro-underruns over Wi-Fi.
  - `WindowsPlaybackEngine.kt`: Improved underrun handling by cleanly pre-buffering to `isReadyForPlayback` before resuming stream consumption. Protected WASAPI line writes against race conditions.

---

### [2026-11-07] - Step 5: Convx-Style Liquid Glass Engine & Controls (feature/liquid-dock)

- **Library & Vendoring**:
  - Vendored io.github.kyant0:backdrop (Apache 2.0) source into core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/ with license intact to guarantee multiplatform binary compatibility with Kotlin 1.9 / Compose Multiplatform 1.6.11.
  - **Android Platform**: Hardware-accelerated AGSL runtime shader (RuntimeShader.android.kt) + RenderEffect chaining via reflection (RenderEffect.android.kt).
  - **Windows / Desktop Platform**: Hardware-accelerated SkSL runtime shader via Skia RuntimeEffect (RuntimeShader.desktop.kt) and Skia ImageFilter composition (RenderEffect.desktop.kt).
- **Bottom Dock & Floating Mini Player**:
  - Android (FrostedNavAndPlayer.kt): Replaced dock and mini player with Convx-style LensDock continuous squircle capsule, edge lens refraction (L351), specular rim (L359), and sliding spring puck (L386).
  - Windows (OpticalRailDock.kt & BottomPlayerDock.kt): Convex optical lens rail dock (lens L103, rim L111, puck L132) and floating player deck using SkSL liquid glass backdrop.
- **Shared Glass Controls (LiquidGlassControls.kt)**:
  - LiquidGlassSwitch: Liquid toggle with convex knob and smooth spring sliding physics.
  - LiquidGlassSlider: Liquid slider with continuous backdrop refraction track and tactile thumb.
  - LiquidGlassButton / LiquidGlassIconButton: Capsule glass buttons with dynamic lens refraction.
  - LiquidGlassSegmentedControl & LiquidGlassChip: Filter chips and tab controls with sliding glass selection puck.
  - All controls strictly maintain touch targets >= 48 dp and full accessibility semantics (Role.Switch, Role.Button, ProgressBarRangeInfo).
- **Now Playing Screen**:
  - Multiplatform NowPlayingView.kt and Android NowPlayingSheet.kt: Updated seekbar to LiquidGlassSlider, playback controls and transport action buttons to liquid glass styling.
- **Menus & Settings**:
  - CustomizeUiView.kt and EqualizerView.kt: Replaced standard Switches and Sliders with liquid glass counterparts.
  - Added 'Dock glass' configuration group (dockGlassLensEnabled, dockGlassRimEnabled, dockGlassPuckEnabled) wired directly to drawing code.
- **Android Home-screen Widgets**:
  - Updated WidgetShaderRenderer.kt with enhanced static liquid glass rendering for offscreen RemoteViews bitmaps (squircle capsule, top-left refraction sheen, chromatic specular rim highlight).
- **Diagnostics & Safety**:
  - Verified GlassShaderSelfTest and fallback to plain frosted blur in Performance mode or on shader compilation failure.

---

### [2026-11-07] - Phase 4: Device Connect Rebuild & Comprehensive Screen Redesign (feature/liquid-dock)

- **Device Connect (Node Connect) Rebuild (`core-ui`, `app-android`, `app-desktop`)**:
  - `DeviceConnectPill.kt`: Rebuilt with continuous squircle geometry (`ContinuousRoundedRectangle(19.dp)`), specular border rim gradient, active status indicator dot with animated outer glow (Cyan `#00F0FF` for Connected, pulsing Amber `#F59E0B` for Discovering, translucent white for Disconnected), device count badges, and Convx spring press physics (`pressScale(0.95f)`).
  - Multiplatform pairing triggers, peer casting callbacks, and cast permission toggles remain 100% operational.
  - Mobile header integration: Added responsive glass pill to Android `HomeScreen.kt` top bar with real connection state from `NodeConnectHub`.
  - Desktop header integration: Preserved inline peer management and casting dropdown in `SharedPlayerLayout.kt`.
- **Now Playing Redesign (`core-ui`, `app-android`)**:
  - `NowPlayingSheet.kt` (Android): Replaced legacy RenderEffect reflection hacks with upstream Kyant0 `drawBackdrop` lens shader, specular highlight, and ambient shadow for hero play/pause button. Replaced standard corners with continuous squircle hero artwork (`ContinuousRoundedRectangle(24.dp)`). Draggable seekbar and secondary tools meet >= 48dp touch bounds with tactile spring feedback.
  - `NowPlayingView.kt` (Desktop/Common): Updated hero play button with `pressScale(0.92f)` and `drawBackdrop` lens refraction, continuous squircle artwork, and fluid animated transitions.
  - `ArtworkLoader.kt`: Upgraded `TrackArtwork` to continuous squircle clipping (`ContinuousRoundedRectangle`) with specular border rim.
- **Comprehensive Screen & Dialog Redesign (`core-ui`, `app-android`)**:
  - `HomeScreen.kt`: Replaced raw icon button with responsive `DeviceConnectPill`. Redesigned `ArtworkThumbnail` and `SmartPlaylistChip` to use continuous squircle shapes with specular border rim gradients.
  - `LibraryScreen.kt`: Modernized tab selector pills and song list artwork thumbnails to continuous squircle geometry and spring press physics.
  - `GlobalSearchSheet.kt`: Modernized bottom sheet container and search input field with `ContinuousRoundedRectangle`.
  - `SearchCapsule.kt`: Modernized top search capsule container with continuous squircle corners and specular border.
  - `EqualizerScreen.kt` & `EqualizerView.kt`: Replaced standard shapes with continuous squircle styling and draggable glass sliders.
  - `NodeConnectPairingDialog.kt`, `FirstRunOnboardingDialog.kt`, `DiagnosticsDumpDialog.kt`, `TrackDetailsSheet.kt`: Upgraded dialog modal sheets and cards to continuous squircle glass containers.
- **Windows Desktop 16:9 Alignment (`core-ui`)**:
  - `OpticalRailDock.kt`: Added dedicated Equalizer tab (`PlayerNavDestination.EQUALIZER`) to the left optical rail dock, matching Android navigation parity.
  - `BottomPlayerDeck.kt`: Modernized floating bottom deck container with continuous squircle capsule shape (`ContinuousRoundedRectangle(32.dp)`).
- **Android Home-Screen Widgets (`app-android`)**:
  - `WidgetShaderRenderer.kt`: Verified static hardware AGSL shader bitmaps (and software Canvas fallbacks) preserve matching obsidian liquid glass aesthetic without regressions.
- **Verification**:
  - Automated tests pass: `:core-ui:desktopTest`, `:app-desktop:test`, `:app-android:testDebugUnitTest`.
  - Android debug APK assembled cleanly: `:app-android:assembleDebug`.
  - Visual and device checks marked "human check pending".
