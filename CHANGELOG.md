# Changelog

All notable changes to **Node Player** are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.2.0] - 2026-11-26

### 🚀 Major Milestone: Merging with Auralis
- **Node Player is Merging with [Auralis](https://github.com/voidXatrix-zudz/Auralis)!**:
  - Node Player is officially joining forces with **Auralis** ([github.com/voidXatrix-zudz/Auralis](https://github.com/voidXatrix-zudz/Auralis)) to create the ultimate open-source, local-first audiophile music client.
  - This merger combines Node Player's bit-perfect 32-bit floating-point audio DSP engine, 120 Hz hardware-accelerated AGSL liquid glass shaders, 5-provider synchronized word-by-word karaoke lyrics, and Google Drive cloud sync with Auralis's next-generation player architecture.
  - Future development, updates, and community roadmaps will continue collaboratively under this united vision.

### Added
- **Interactive 3D Gyroscope Artwork Tilt Parallax (`NowPlayingView.kt`, `AndroidGyroscopeTilt.kt`, `CustomizeUiView.kt`)**:
  - Implemented sensor-driven 3D perspective for hero album artwork using Android's `Sensor.TYPE_ROTATION_VECTOR` with low-pass spring smoothing.
  - Dynamically tilts artwork on `rotationX` and `rotationY` with realistic camera distance projection.
  - Overlays a dynamic liquid glass specular highlight sheen that glides across the artwork surface as the device is angled.
  - Completely opt-in: defaults to **OFF** (`gyroscopeParallaxEnabled = false`) with zero sensor registrations or background battery draw until enabled in **Settings > Customize UI > Spring Dynamics & Motion**.
- **Full-Player Tactile Haptic Feedback (`NodeHaptics.kt`, `MainActivity.kt`, `CustomizeUiView.kt`)**:
  - Centralized `NodeHaptics` and `rememberNodeHaptics` utility respecting `themeConfig.hapticsEnabled`.
  - Added tactile haptic micro-ticks and impacts on tapping tracks to play, adding tracks to queue (`addToQueue`), playing next (`playNext`), scrubbing seekbars, and switching navigation dock tabs.
- **Synchronized Lyric Card Typography (`LyricCardView.kt`, `FullScreenLyricsView.kt`)**:
  - Exported Lyric Cards now dynamically inherit and render with the exact font chosen in Full-Screen Lyrics settings (Inter, Outfit, Caveat, Lobster, Bebas Neue, Minecraft, Cinzel, etc.).
  - Added `fontFamily` propagation across `LyricCardGeneratorDialog` and `LyricCardView`, bridging Compose dialog window isolation.
- **Playlist Card Press-Scale & Circular Loading Indicator (`OnlineHomeView.kt`, `MainActivity.kt`)**:
  - Added `.pressScale(onClick = onClick)` on `HomePlaylistCard` for immediate tactile spring compression on touch.
  - Added `loadingPlaylistId` state rendering a high-contrast circular progress indicator centered over the playlist artwork with a semi-transparent scrim while fetching tracks.
  - Upgraded playlist detail entrance to an iOS-inspired spring pop: `scaleIn(spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = 0.90f) + fadeIn(tween(220))`.
- **Mini-Player Interactive Drag Physics (`ConvxMiniPlayer.kt`)**:
  - Connected `Modifier.draggable` to an `Animatable(0f)` with rubber-band gesture resistance (`delta * 0.65f`) and real-time translation on `graphicsLayer`.
  - Mini-player springs back into place with natural recoil physics upon gesture release.
- **Queue Sheet Spring Transitions (`PlaybackOptionsSheet.kt`)**:
  - Upgraded sheet backdrop with smooth `fadeIn(tween(180))` / `fadeOut(tween(150))`.
  - Upgraded sheet container with `slideInVertically(spring(stiffness = 380f, dampingRatio = 0.82f))`.
  - Added stable keys and `Modifier.animateItemPlacement(spring(...))` for queue list reordering.

### Fixed
- **NowPlaying Swipe-Down Double-Dismiss Glitch (`MainActivity.kt`)**:
  - Eliminated premature `sheetDragOffsetY.snapTo(0f)` calls inside drag and close handlers that previously reset sheet translation back to 0 at the exact moment dismissal exit animations triggered.
  - Guarded `AnimatedVisibility` exit with distance checks (`sheetDragOffsetY.value > screenHeightPx * 0.5f`), replacing redundant double-animations with an instantaneous `fadeOut(tween(120))` when already dismissed to the screen bottom.
- **Scroll Jitter & Frame-Drop Elimination (`MainActivity.kt`)**:
  - Replaced continuous per-pixel recompositions from `dockScrollController.offsetFraction.collectAsState()` in `MainBottomDockOverlay` with discrete threshold evaluation `dockScrollController.isCollapsed.collectAsState()`.
  - Driven dock offset smoothly via `animateFloatAsState(animationSpec = spring(stiffness = 380f, dampingRatio = 0.82f))`, eliminating 60–120 FPS backdrop shader recompositions during flings.

---

## [2.1.0] - 2026-11-23

### Added
- **Opt-In Privacy-First Cloud Backup & Sync with Google Drive (`GoogleAuthManager.kt`, `GoogleDriveApiClient.kt`, `CloudSyncManager.kt`, `CloudSyncWorker.kt`)**:
  - **Zero Developer-Hosted Servers**: Backs up user playlists, bookmarked favorites, per-song listening statistics, and playback history directly to the user's personal Google Drive.
  - **Strict `appDataFolder` Scope Isolation**: Authenticates via Google OAuth 2.0 PKCE requesting exclusively `https://www.googleapis.com/auth/drive.appdata` inside Google Drive's hidden `appDataFolder` (`spaces=appDataFolder`). The application cannot read, write, or see personal Drive files.
  - **Safe Conflict-Resolution Merge Engine (`SyncMergeEngine.kt`)**: Automatically downloads remote backups and merges before uploading:
    - *Playlists & Saved Items*: Newer `updatedAt` timestamp wins.
    - *Song Statistics*: Keeps the higher `totalPlayTimeSeconds` and `playCount`, and latest `lastPlayedAt` per track.
    - *Playback History*: Deduplicates by `(songId, playedAt)`, sorts descending, and caps at the latest 200 entries.
  - **Dirty-Flag Background Optimization (`CloudSyncManager.kt`)**: Tracks local `hasUnsyncedChanges` to skip background uploads if no playlists were altered or tracks played.
  - **Periodic Sync Scheduling**: Background `WorkManager` execution supporting **Every Hour**, **Once a Day**, or **Manual Only** sync frequencies with network constraints.
  - **Dedicated Settings Tab (`SettingsScreen.kt`)**: Integrated directly into **Online Music & Streaming Services** as a dedicated sub-tab with account status, sync frequency chips, "Sync Now" button with progress indicator, and "Delete Cloud Backup" option.
  - **Hardware Keystore AES-256-GCM Token Encryption**: Securely persists OAuth tokens in platform-encrypted storage (`SecureSessionStore.kt`) with automatic 401 refresh handling.
  - **RFC 8252 Reverse-DNS Redirect URI**: Official Google OAuth Client IDs injected from `local.properties` via `BuildConfig.GDRIVE_CLIENT_ID` with reverse-DNS custom scheme handling (`com.googleusercontent.apps.*`).
- **Auto-Collapsing Left Hero Controls in Landscape Cinematic Mode (`FullScreenLyricsView.kt`)**:
  - Implemented dynamic 4.5-second inactivity auto-collapse for the left-hand column (album art, seek bar, and transport controls).
  - Designed fluid spring collapse & expand animations anchored at the left-middle field (`TransformOrigin(0f, 0.5f)` near camera cutout) that smoothly tucks into the screen edge when playing.
  - Tapping anywhere on screen or clicking the sleek floating left-center chevron immediately expands controls back out.
  - Controls remain permanently expanded when playback is paused.
- **Active Transport Button State Contrast & Cleanup (`FullScreenLyricsView.kt`)**:
  - Transport buttons (Shuffle and Repeat) dynamically invert when active: background turns solid `Color.White` with pitch `Color.Black` icons, mirroring the Play button.
  - Removed the redundant favorite heart button from the landscape transport controls row.
- **Edge-to-Edge Display Cutout & Status Bar Immersion (`MainActivity.kt`, `themes.xml`, `AppSettingsRepository.kt`, `LyricsSettingsRepository.kt`, `SettingsScreen.kt`)**:
  - Configured `android:windowLayoutInDisplayCutoutMode = shortEdges` (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`) across XML themes and runtime activity setup, eliminating Android letterboxing and black bars around camera cutouts in landscape.
  - Added "Hide Status Bar" options in both Settings (`Customize UI` global immersive mode) and Lyrics Settings (auto-hide status bar when entering full-screen lyrics), enabling uninterrupted edge-to-edge viewing.
  - Upgraded Landscape `CinematicKineticLyricsView` to span 100% of the display edge-to-edge with right-weighted camera centering (`wPx * 0.68f`), allowing floating lyrics to render with zero constraining box borders.
- **Landscape Two-Column FullScreenLyricsView (`FullScreenLyricsView.kt`, `LyricsSettingsBottomSheet.kt`, `LyricsSettings.kt`)**:
  - **Split Landscape Architecture**: Implemented a responsive two-column split layout for landscape viewports (`maxWidth > maxHeight && maxWidth >= 600.dp`).
  - **Left Hero Column**: Interactive shapeable album art hero, song title, artist label, time-seek slider with formatted elapsed/total durations, and Previous / Play-Pause / Next playback controls.
  - **Right Lyrics Column**: Synchronized lyrics column supporting both Cinematic Kinetic word-by-word visualizer and selectable Classic multi-line view.
  - **Album Art Shape Customizer**: Added `AlbumArtShape` (`SQUIRCLE`, `CIRCLE`, `HEART`) selectable from the top-right three-dot menu `( ⋮ )` bottom sheet, featuring custom Bezier vector clipping for the heart shape.
  - **Dynamic Theme Palette & Starfield Preservation**: Left-to-right ambient gradient derived from album art colors with kinetic starfield backdrop.
- **Selectable Lyrics with Text Selection (`FullScreenLyricsView.kt`)**:
  - Wrapped Classic full-screen and landscape lyrics in `SelectionContainer`, allowing users to highlight, copy, and interact with synchronized lyrics directly on screen.
- **Multi-Provider Lyrics Expansion (`LyricsRepository.kt`, `Musixmatch.kt`, `Unison.kt`, `FastbootEraseLrcRead.kt`)**:
  - Expanded the lyrics search network to include **Musixmatch** (with strict duration delta verification `abs(duration - expected) <= 4`) and **Unison** / `FastbootErase` fallback sources.
  - Visual status indicator icons in the lyrics source selector:
    - 🔍 **Searching**: Background query in progress.
    - 🎵 **Found & Connected**: Valid synced lyrics loaded.
    - ⚠️ / ❌ **Unmatched / Estimated**: Fallback word-estimation or plain text.
- **Offline Lyrics Romanization & Dual-Line Translation (`LyricsRomanizer.kt`, `LyricsSettingsBottomSheet.kt`)**:
  - Embedded multi-script romanization engine for Japanese (Hiragana/Katakana), Korean (Hangul), Chinese (Pinyin), Cyrillic, Greek, Devanagari (Hindi), and Arabic.
  - Dual-line rendering option with phonetics below original lyrics, accessible via an interactive 50% bottom sheet.
- **1200p Ultra-High-Resolution Album Art Pipeline (`InnerTubeClient.kt`)**:
  - Automatic URL transformer upgrading `lh3.googleusercontent.com` and `yt3.ggpht.com` covers from 120p/544p thumbnails to pristine 1200×1200p artwork.

### Fixed
- **Genuine Library Screen Listening Metrics (`LibraryScreen.kt`)**:
  - Replaced placeholder fallback calculations (which previously substituted total track file durations and count when listening history was empty) with genuine real-time statistics from `dailyStats` and track play counters.
- **Cinematic Kinetic Sweep & Playback Pausing (`CinematicKineticLyricsView.kt`)**:
  - Bound `isPlaying` dynamically with `rememberUpdatedState` inside the 60–120fps animation ticker, immediately halting downward sweeping, star drifting, and time progression when playback is paused.
- **Landscape Classic Lyrics Typography, Spring Bounce & Glow Parity (`FullScreenLyricsView.kt`)**:
  - Integrated `WordByWordKaraokeText` into landscape classic mode, restoring `LocalLyricsFontFamily` font rendering, per-word bounce springs (`dampingRatio = 0.62f`), and subtle active glow shadows (`Shadow(blurRadius = 18f)`).
- **32-Bit Float Audio Silence & Sinks (`PlaybackService.kt`, `WindowsPlaybackEngine.kt`)**:
  - Enforced sample-rate and bit-depth clamping in the audio sink pipeline, resolving silent playback on legacy Android versions.
- **Vorbis/Opus Tag Metadata Parsing (`AudioHeaderParser.kt`)**:
  - Corrected comment block parsing when user metadata contains multi-line comments or non-standard padding.
- **Opus Transcoder Video Track Crash (`OpusTranscoder.kt`)**:
  - Safely ignored embedded video track headers during audio-only transcode pipelines.
- **Cold-Start Replay HTTP 403 (`InnerTubeStreamResolver.kt`)**:
  - Reset byte-range request offsets during stream re-anchoring, eliminating stale 403 Forbidden errors on cached audio chunks.

### Performance
- **Zero-Allocation DSP Filtering**: Vectorized in-place `FloatArray` buffers for real-time 10-band IIR biquad equalizer and loudness calculation.

---

## [2.0.0] - 2026-11-19

### Added
- **BitChord-Inspired 2-Tier Music Video Resolution & Smart Alignment (`VideoTrackMatcher.kt`, `InnerTubeClient.kt`, `PlayerViewModel.kt`)**:
  - **Tier 1 (Official InnerTube Counterparts)**: Added `fetchOfficialVersionPair` parsing `playlistPanelVideoWrapperRenderer` and `counterparts` from YouTube Music `/next`. Extracts exact official paired music videos and parses `segmentMap` (`primaryVideoStartTimeMilliseconds`, `counterpartVideoStartTimeMilliseconds`) for intro/outro time-offset alignment.
  - **Tier 2 (Intelligent Video Matcher)**: Added `VideoTrackMatcher` decomposing track titles into core recording identity versus listing packaging (e.g. "(Official Video)", "[4K]", "Lyrics"), validating artist overlap, enforcing version fidelity (Remix, Acoustic, Live), and validating duration tolerance (±120s) to discard 1-hour loops or reaction videos.
  - **Background Pre-Resolution & UI Lighting**: Automatically resolves video availability asynchronously on track change in `PlayerViewModel`, setting `isAvailable = true` to light up the existing "Video" button in `NowPlayingView` without modifying UI layout or video engines.

### Fixed
- **Release Crash & Concurrent Drag Gesture Contention (MainActivity.kt, NowPlayingView.kt)**:
  - Replaced unmanaged coroutine dispatches with a dedicated composeScope and sequential job cancellation (dragAnimJob?.cancel()), preventing concurrent nimateTo interruptions and CancellationException crashes upon touch release.
  - Added safe 	ry / catch boundary around drag release spring animations and an isDismissingSheet lock to prevent redundant dismiss triggers.
- **Centered Floating Video Window & Status Bar Inset (NowPlayingView.kt)**:
  - Enforced contentAlignment = Alignment.Center and Modifier.fillMaxSize() on AnimatedContent hero container, eliminating top clipping behind the status bar and header.
  - Added statusBarsPadding().padding(top = 48.dp, bottom = 12.dp) positioning the 16:9 video window symmetrically centered in the hero viewport, completely clear of the top header and camera notch.

### Added
- **Smooth Quadratic Backdrop Blur & Dynamic Dissolve Curve (MainActivity.kt)**:
  - Added an interactive background glass blur (36.dp) that is rich and blurry when Now Playing is fully expanded on top, and smoothly dissolves along an exponential quadratic curve ((1f - progress)^2) to complete transparency as dragged down, revealing the underlying screen seamlessly.

### Fixed
- **Apple Music-Style Smooth Lyrics Sweep & Overlay Fix (`WordByWordKaraokeText.kt`, `FullScreenLyricsView.kt`)**:
  - Eliminated the stuck white rectangular overlay and halo box artifact around active words by removing the erroneous `drawRect(BlendMode.DstIn)` feathering and word-level `Shadow` blur that previously rendered white gradient blocks over active lyrics.
  - Implemented hardware-accelerated, frame-synchronized sub-pixel `clipRect` wiping driven by `withFrameNanos`, ensuring continuous 60/120 FPS sweep animation across syllables and lines without freezing or stuttering.
  - Fixed compilation order in `FullScreenLyricsView.kt` where `enterSelectionMode()` referenced `activeIndex` before its declaration.
  - Resolved Lyric Card text mismatch: card generator now pre-selects the playing on-screen lyrics (`activeIndex..activeIndex+3`), scrolls to the active position, and renders in crisp, bold sans-serif typography (`Typeface.SANS_SERIF, Typeface.BOLD`), completely preventing fallback to Android cursive system fonts.

### Added
- **Instant Lyric Card Generator (`LyricCardView.kt`, `LyricCardRenderer.kt`)**: Added high-resolution (1080×1920) 9:16 vertical Lyric Card generator matching BitChord and Apple Music aesthetics:
  - Album art thumbnail with dedicated share indicator in `FullScreenLyricsView` top bar opens interactive line-selection mode.
  - Interactive multi-line selection (up to 6 lines) in squircle card containers (`ContinuousRoundedRectangle(16.dp)`) with illuminated selection state and a floating glass pill dock `[ ✕ Cancel | Share ]`.
  - High-res card composition featuring dynamic vertical ambient gradients derived from `AlbumArtPalette`, "Node Player" brand watermark, bold typography, squircle album art, track title, and artist name.
  - Android `LyricCardRenderer` generates crisp PNG exports, automatically launches the native Android Share Sheet via `FileProvider`, and supports saving directly to device Pictures / MediaStore gallery.
- **Thicker High-Impact Lyrics Typography (`WordByWordKaraokeText.kt`, `LyricLineItem.kt`, `FullScreenLyricsView.kt`)**:
  - Increased font weight to `FontWeight.Black` (active) and `FontWeight.ExtraBold` (inactive) with `-0.45sp` letter spacing and `32.sp` default scale, ensuring punchy, highly legible typography against ambient backgrounds.
  - Increased unsung text alpha from `0.35f` to `0.48f` for bolder character strokes.
- **Carbon / Zinc Design System (`CarbonZincPalette.kt`)**: Added neutral dark palette (`#09090B` floor, `#121215` container, `#1C1C21` elevated surface, `#5EEAD4` AccentTeal) and unified typography scale.
- **Fixed Slim Top Bar & Mode Switcher (`OnlineHomeView.kt`)**: Slim 38dp pinned header featuring a unified mode switcher `[ • YT Music | Device Files ]` with active teal indicator dot and instant toggle to offline files, accompanied by a circular profile avatar button.
- **Scrollable Mood Filter Bar & Content Divider**: Horizontally scrollable mood chips (`Pop`, `Chill`, `Party`, `Commute`, `Romance`...) placed at the top of the Home feed, followed by a `1.dp` subtle divider that scrolls up cleanly under the fixed top bar.
- **Modern "Your Mix" Cards & Squircle Art Covering (`YourMixShelf.kt`)**: Redesigned cards with 20.dp continuous rounded rectangle borders, 136×136dp inner album art with 14.dp squircle curvature, and an elevated capsule pill "Play" button.
- **Hardware-Synchronized System Volume Slider**: Real-time integration of the volume slider in `NowPlayingView` with Android's `AudioManager.STREAM_MUSIC`, complete with drag/tap seeking and a `BroadcastReceiver` listening to `"android.media.VOLUME_CHANGED_ACTION"` for hardware rocker synchronization.
- **Widescreen 16:9 Music Video Player**: Automatic container aspect ratio transition to 16:9 for video streams, with stream quality badge (`480p`) and fullscreen expand controls conditionally visible only for video playback.
- **Multi-Shelf YouTube Music Discovery**: Integrated 6 dynamic shelves on Home: Top Picks for You, Recents, Find Your Mood (with duotone category matrix filters), Albums & Singles, Music Videos, and Trending.
- **Profile Avatar & Session Hydration**: Added authentic Google/YouTube avatar thumbnails, channel handle (`@username`), and account metadata in `HomeProfileSheet` backed by encrypted `SecureSessionStore`.
- **TTML Syllable Lyrics & Karaoke**: Added syllable-level karaoke lyrics parsing from TTML formats, duet vocalist alignment, backing vocals, and instrumental break countdown dots.
- **Bottom-Left Jump to Current**: Added a compact `36.dp` circular white button (`Icons.Rounded.ArrowDownward`, `Color.White.copy(alpha = 0.16f)`) anchored in the bottom-left corner of `FullScreenLyricsView`.

### Performance & Optimization (CPU, Network & Memory)
- **Unthrottled Stream Resolution Architecture (`AudioCache.kt`, `RangedUpstreamDataSource.kt`)**: Primary YouTube audio stream resolution now delegates directly to `NewPipeFallback.resolveStream(videoId)` with signature deciphering (matching the video engine's architecture). Eliminates the 12–14s startup waterfall previously caused by rotating through dead clients (`VISIONOS`, `WEB_REMIX`), dropping stream start latency to ~1.1s with continuous, uninterrupted 100% full-song playback and seamless seeking across all tracks.
- **Demuxer Crash Prevention & Clean Container Lifecycle (`RangedUpstreamDataSource.kt`)**: Removed mid-stream URL swapping at byte 1,048,576. Eliminates `java.io.EOFException` inside Media3's `FragmentedMp4Extractor` caused by splicing disparate container formats (WebM/Opus vs MP4/AAC) into an active demuxer pipeline.
- **Reactive Now Playing Position Ticker (`NowPlayingTimeline.kt`, `NowPlayingView.kt`, `FullScreenLyricsView.kt`, `ConvxMiniPlayer.kt`)**: Replaced non-reactive `derivedStateOf { positionProvider() }` evaluations with leaf-level `produceState` coroutine tickers running at 100ms/150ms intervals. Restored real-time slider progress updates, elapsed/remaining time counters, synchronized lyric highlighting, and miniplayer progress tracking without triggering root `MainActivity` recompositions.
- **Direct Head Probe & Fast Timeout (`StreamResolver.kt`)**: Reverted 1 MiB offset probing back to direct head verification (`Range: bytes=0-1024`) with `PROBE_TIMEOUT_MS` reduced to `1_500L`. Eliminates HTTP 403 Forbidden errors triggered by Google Video CDN on unauthenticated mid-stream probes, preventing client rotation exhaustion and eliminating the 11–12s playback startup waterfall.
- **Parallel Taste Recommendation Dispatch (`TasteRecommendationEngine.kt`)**: Converted sequential seed candidate iteration to parallel coroutine dispatch using `async(Dispatchers.IO) { historySource.getRadioForTrack(...) }`, cutting "Your Mix" load time from ~3,200ms to ~850ms while preserving deterministic quota deduction.
- **Ktor CIO HTTP Connection Pooling (`InnerTubeClient.kt`)**: Tuned CIO engine endpoint settings with `maxConnectionsPerRoute = 20`, `pipelineMaxSize = 1`, and `keepAliveTime = 30_000`, eliminating redundant TLS handshakes across concurrent YouTube Music calls.
- **Recomposition Isolation & Stable Keys (`YourMixShelf.kt`)**: Added stable composite keys (`${track.extractVideoId() ?: track.id}_$index`) and explicit `contentType = "your_mix_card"`, eliminating duplicate-key crashes and preventing unnecessary recompositions during horizontal list scrolling.
- **In-Memory Station Cache & Deduplicated Cold Start (`OnlineHomeView.kt`)**: Added `sessionCachedTasteStation` cache and single-flight `yourMixJob` mutex, eliminating duplicate concurrent API queries on app launch and preventing startup UI thread freezes.
- **Audio-Tuned ExoPlayer Load Control (`CrossfadePlayerManager.kt`)**: Configured tailored `DefaultLoadControl` (min buffer 15s, buffer for playback 800ms vs 2,500ms default), cutting stream start latency by ~1.7s without buffer starvation risk.
- **Zero-Allocation Fading Loop (`CrossfadePlayerManager.kt`)**: Removed per-tick `String.format("%.3f")` allocations from the 25ms monitor loop, preventing GC pressure and micro-stutters during active track crossfades.
- **Persistent HTTP/2 Pool & Reduced Logging Overhead (`AudioCache.kt`)**: Added dedicated `ConnectionPool(32, 5, TimeUnit.MINUTES)` and eliminated per-chunk log outputs from `AudioCache`, eliminating main thread logging contention.
- **Compound Database Indices & Migration v8 (`Entities.kt`, `NodeDatabase.kt`, `NodePlayerApplication.kt`)**: Added indices on `TrackEntity(["isHidden", "title"])` and `TrackEntity(["isFavorite"])` via Room `MIGRATION_7_8`, speeding up library queries from ~280ms to <15ms.
- **Root-Level 60Hz State Hoisting Elimination (`MainActivity.kt`)**: Replaced root-level `collectAsState()` on `currentPositionMs` and `currentRms` with deferred lambda providers `{ viewModel.currentPositionMs.value }`, preventing root-to-leaf full UI recompositions on every 16ms frame.
- **Incremental Library Scan Metadata Caching (`LibraryScanner.kt`)**: Reused existing sample rate, bit depth, channels, bitrate, and format description from Room database when `dateAdded` matches, skipping expensive `MediaMetadataRetriever` disk I/O on re-scans.
- **Widget Update Resource Guard (`WidgetUpdateManager.kt`)**: Added active widget detection guard and removed duplicate `appWidgetManager` instantiation, saving CPU cycles and battery when widgets are not on home screen.

### Changed
- **Online Stream & AAC Badge Accent**: Updated YouTube stream and AAC badges in `AudioFormatBadge.kt` to use `CarbonZincPalette.AccentTeal` (`#5EEAD4`) text, stroke, and translucent container tint.
- **Home Feed Shelves Hierarchy**: Updated home screen sections, video cards, and squircle album cards to utilize the Carbon/Zinc surface hierarchy.
- **Default Dark Mode Theme Color**: Set default dark mode background to charcoal `#262626` across `Theme.kt`, `LiquidGlassBackground.kt`, and `GlassTheme.kt` with `#303030` elevated card surfaces, while strictly maintaining pure `#000000` pitch black for OLED mode.
- **FullScreenLyricsView Redesigned Top Bar**: Centered drag handle pill (`36.dp` × `4.dp`), `48.dp` × `42.dp` thumbnail, song title & artist typography, favorite star button, and dynamic context action button (More Options when controls are open, Share when controls are collapsed).
- **Default Collapsed Lyrics Controls**: Full-screen lyrics playback dock now starts collapsed by default for an unobstructed, distraction-free reading experience, auto-collapsing after 3.5 seconds of inactivity when revealed.

### Fixed
- **YouTube Playback 1m04s / 1 MiB CDN 403 Stall & 128 KB Chunking**:
  - Implemented 128 KB bounded chunk range requests (`DEFAULT_CHUNK_SIZE = 128L * 1024`) in `RangedUpstreamDataSource.kt` to bypass Google Video CDN's 1 MiB cutoff on mobile unauthenticated streams.
  - Rewrote upstream chunk read loop to prevent premature `RESULT_END_OF_INPUT` signals on chunk boundaries, enabling continuous audio buffering without stalls.
  - Fixed `openChildWithRetry` to extract and pass the real 11-character `videoId` during stream refreshes instead of passing raw googlevideo URLs.
  - Integrated `media3-datasource-okhttp` to maintain persistent HTTP/2 connection pooling across chunk boundaries.
- **YouTube Stream Resolution & Client Rotation (`StreamResolver.kt`, `YouTubeModels.kt`)**:
  - Upgraded Apple client models (`IOS`, `IPADOS`) with complete iOS 18.2 client metadata (`21.03.1`).
  - Demoted broken `ANDROID_VR` and bot-blocked `TVHTML5` clients in stream rotation, requiring HTTP 200/206 validation before handing streams to ExoPlayer.
- **MusicVideoEngine Video Playback Failure**:
  - Configured `DefaultMediaSourceFactory` using `AudioCache.createDataSourceFactory()` for `MusicVideoEngine`'s secondary player to prevent naked unbounded video requests that triggered CDN 403 errors.
  - Added playback error listener to `MusicVideoEngine` for immediate diagnosis.
- **Lyrics Scroll Auto-Popup Bug**: Decoupled manual list scrolling from control visibility so scrolling the lyrics list never unexpectedly brings up the bottom playback bar.
- **Bottom Scrim Vignette Removal**: Removed the black gradient scrim overlay at the bottom of `FullScreenLyricsView`, enabling the dynamic album art palette to flow cleanly to the bottom edge.
- **Root Screen Background De-Hardcoding**: Removed hardcoded `Color.Black` roots in `OnlineHomeView`, `PlaylistDetailView`, and `LibraryScreen`, enabling proper rendering of `#262626` in standard dark mode while preserving pure black in OLED mode.
- **Windows Build File Locking**: Resolved file lock conflicts during multiplatform jar bundling (`bundleLibCompileToJarDebug`).

---

## [1.6.0] - 2026-11-06

### Added
- **Node Connect Peer-to-Peer Audio Mesh**: Decentralized, zero-configuration local Wi-Fi connectivity between Windows PC and Android devices.
- **Bit-Perfect Audio Casting**: Pre-DSP 16-bit 48kHz PCM casting with adaptive jitter buffer, sub-100ms synchronization, and lossless packet delivery.
- **Cross-Platform Remote Search & Click-to-Play**: Search remote library tracks across devices, tap to start playback remotely, and stream back real-time playback audio, album art, and track metadata.
- **Live State & Transport Synchronization**: Two-way playback synchronization for play/pause, seek positions, volume adjustment, shuffle, and repeat modes.
- **Stateful Playback Handoff**: One-tap transfer of active music playback with millisecond position persistence between phone and PC.
- **Silent Local Casting**: Added toggle to mute the physical audio output on the sender device while maintaining continuous pre-DSP streaming to the remote receiver.
- **Node Connect Pairing Hub**: In-app QR code discovery, manual IP connect, per-device feature permission controls (Cast, Control, Library, Handoff), and duplicate connection protection.

### Fixed
- **ExoPlayer Threading Safety**: Resolved wrong-thread assertion crash in `onTrackTransition` by ensuring player state callbacks execute on the main playback looper.
- **Artwork & Metadata Stream Sync**: Resolved issue where metadata and album art did not update on remote receiver during track transitions.
- **Audio Packet Crackle & Jitter**: Re-tuned jitter buffer parameters (target buffer 400ms, max 2000ms) with background worker audio chunk channel to eliminate packet transition crackles.
- **Phone UI Selector Polish**: Removed unwanted ring artifact in the middle field of the phone navigation selector pill.
- **Cross-Platform Search Duplication**: Resolved key conflict crashes and duplicate peer entries during LAN discovery scans.

---

## [1.5.0] - 2026-11-03

### Added
- **Universal Compose Multiplatform Architecture**: One unified UI codebase shared across Android phone (portrait), Android landscape (16:9), and Windows Desktop (16:9).
- **Windows Desktop Shell (`:app-desktop`)**: Native Windows desktop player with 16:9 layout, hover-expanding sidebar navigation, responsive Now Playing centerpiece, and mobile disc logo.
- **Bit-Perfect WASAPI Audio Output**: Low-latency Windows Audio Session API integration supporting both Shared mode and Exclusive mode with zero resampling.
- **Pure 64-Bit Float Desktop DSP**: Cross-platform 10-band biquad equalizer, Freeverb reverb, loudness contour, psychoacoustic bass, and TPDF dither shared between Windows and Android.
- **Strict Zero-Estimation Metadata Extraction**: Pure header parsing for FLAC (STREAMINFO total samples, Vorbis comments), WAV (fmt and data chunks), MP3 (ID3v2 tags, TLEN, Xing/VBRI frame counts), M4A/ALAC (mvhd timescale/duration, ilst tags), and OGG/Opus without arbitrary guesses or 3-minute clamps.
- **Desktop Navigation & Controls**: Mouse wheel seeking on scrubbers (±5s seek), spacebar play/pause, arrow seeking & volume, Ctrl+F search, and hardware media keys.
- **Windows MSI Installer & Standalone Bundle**: Official WiX-packaged MSI installer (`NodePlayer-1.5.0.msi`) and portable zero-dependency executable distribution.

### Fixed
- **Android 120fps Fluid Performance**: Decoupled heavy desktop disk-byte artwork extraction from shared UI. Android now uses hardware-accelerated, zero-lag `RGB_565` bitmap memory management.
- **Lossless FLAC & MP3 Playback**: Fixed JLayer MPEG sync word frame alignment past ID3v2 tags/padding and eliminated stream positioning stalls.
- **Scrubber Seek Synchronization**: Synchronized playback position atomically to eliminate scrubber jumps during seeking.
- **Audio Cleanliness**: Disabled TPDF dither by default on desktop to eliminate white noise hiss. Cashmere Beige default accent and OLED black off by default on initial launch.

---

## [1.4.0] - 2026-11-03

### Changed
- **Internal Modular Restructure**: Clean architectural boundary separation with explicit public feature contracts (`WidgetFeatureApi`, `ScrobbleFeatureApi`, `UpdaterFeatureApi`, `DiagnosticsFeatureApi`, `LyricsFeatureApi`).
- **Pure Kotlin Common Module (`:core-common`)**: Central `NodeLog` with feature tags and 500-entry ring-buffer, `DispatcherProvider`, and sealed `NodeResult`.
- **Ecosystem Integration Surface (`:integration-api`)**: Pure Kotlin IPC contract with protocol versioning and immutable state snapshots (`NowPlayingState`, `PlaybackCommand`, `ExternalTrackItem`) for upcoming Android TV companion apps and external controllers.
- **Architectural Boundary Enforcement**: Added automated test `ArchitectureBoundaryTest` ensuring core modules remain pure Kotlin without Android framework dependencies and enforcing strict encapsulation across feature internals.
- **Side-by-Side Staging Build (`.staging`)**: Added `staging` build type with `.staging` application ID suffix, allowing parallel side-by-side test installations on device without touching production data.

---

## [1.3.1] - 2026-11-03

### Fixed
- Revamped liquid glass dock refraction with pure crystal pearl specular rim and dynamic optical magnification.
- Restored Now Playing fluid glass background bleeding and liquid play/pause lens bubble.
- Fixed Customize UI continuous glass opacity, grain texture, and lens refraction strength controls.
- Added high-DPI 2x static widget rendering for 1080p+ displays and widget customization options.

---

## [1.3.0] - 2026-11-03

### Added
- **Liquid-Glass Lens Dock**: Floating pill dock featuring draggable spring-physics magnifying bubble, amber/cyan specular rim, light haptic feedback on tab boundary crossing, and separate circular glass search button.
- **In-App Background Animation Engine**: High-resolution procedural background engine with 6 styles (Liquid blobs, Aurora, Mesh gradient, Album-art blur, Slow caustics, Static), dynamic speed (0.2x–3.0x), intensity (0.1–1.0), color source (Artwork, Palette, Custom), animate-only-while-playing gate, frame rate cap (Auto, 60, 90, 120), adaptive quality scaling, and 64x64 blue-noise dithering to eliminate gradient banding.
- **Static Liquid-Glass Home Widgets**: 4 static launcher widgets (Card 4x2, Lyrics 4x3, Slim 4x1, Square 2x2) with baked-in liquid-glass shimmer look, event-driven RemoteViews re-rendering, live in-app preview configuration screen, and granular element visibility toggles (artwork, waveform, title/artist, seekbar/times, transport, lyrics).
- **Player UI Restructuring**: Reordered Now Playing hierarchy (Artwork &rarr; Title/Artist &rarr; One clickable quality line [source format + bit-perfect/resampled sink] &rarr; Seekbar with times &rarr; Primary transport controls &rarr; Secondary tools row: shuffle, repeat, queue bottom sheet, lyrics, sleep timer, EQ) with universal 48dp touch targets.
- **Sectioned Settings Hub**: Redesigned Settings into a 2-column glass card grid with real-time search filtering, quick mode chips (Performance, Shader, Theme), subpage navigation with state memory, and per-section reset to defaults.
- **Full Customize UI Suite**: Comprehensive personalization hub with interactive live preview cards, 6 theme presets (*Obsidian Liquid*, *Frost Pearl*, *Amber Flare*, *Neon Aurora*, *Stealth OLED*, *Minimal Glass*), bundled fonts (*Inter*, *Outfit*, *JetBrains Mono*, *System Default*), corner roundness (*Sharp 8dp*, *Rounded 16dp*, *Pill 24dp*), spacing density (*Compact 10dp*, *Comfortable 16dp*, *Roomy 22dp*), lens refraction & rim highlight sliders, component toggles, and `.nodetheme` JSON export/import.
- **iOS-Grade Spring Motion & Animation Playground**: Unified spring physics tokens (*Snappy*, *Smooth*, *Bouncy*, *Gentle*), fluid `.pressScale()` modifier on cards & buttons, interactive draggable spring pucks, and an Animation Playground screen to test motion profiles.

---

## [1.2.0] - 2026-11-02

### Added
- **In-App Update System**: Opt-in GitHub Releases update checker comparing semantic versions (`SemVer`), with background checks via battery-friendly `WorkManager` (daily, unmetered network) and foreground throttling (at most once every 12 hours). Includes first-run opt-in dialog and privacy disclosures.
- **Glass Update Banner**: Non-blocking liquid glass card on the Home screen featuring release notes, asset size, and actions (*Update now*, *Later*, *Skip this version*).
- **Background Download Engine**: Resumable HTTPS download directly to private app storage, notification progress, storage space checks, and strict SHA-256 checksum verification against official release assets.
- **Secure PackageInstaller Integration**: Streams updates directly into Android `PackageInstaller` with `USER_ACTION_NOT_REQUIRED` on Android 12+, unknown app sources guidance (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`), pre-install security validation (verifying package name, strictly newer version, and matching signing certificates), and active playback confirmation.
- **Post-Update What's New Sheet**: Highlights new features and improvements immediately after an update installs.
- **Community & Feedback Hub**: Enabled GitHub Discussions on the repository and added *Send feedback* and *Report a bug* (with device and OS details pre-filled) options across *More*, *Settings*, the project README, and website footer.
- **Voluntary Support Integration**: Added optional voluntary UPI support (`7063061981@fam`, payee `Krighz`) with one-tap payment launching and clipboard copying in *Settings*, *More*, `.github/FUNDING.yml`, and website with a crisp inline SVG QR code.
- **Debug Mock Updater**: Added debug controls in *Graphics Diagnostics* to test update banner rendering, dismissals, and version unskipping.

### Fixed
- Fixed `UnsupportedOperationException: Tried to obtain display from a Context not associated with one` on Android 16 (API 36) by querying `DisplayManager` and falling back to display metrics.

---

## [1.1.0] - 2026-11-02

### Added
- **AGSL Shader Hardening**: Hardened refraction shaders across the dock, mini player, scrub sheet, and home widgets using high-precision `float` coordinates, normalized math (`0..1`), divide/normalize guards with epsilon, time/coordinate modulo wrapping, and strict view boundary clipping to prevent GPU-specific out-of-bounds artifact spills (e.g. Mali-G77 / Dimensity 8020).
- **Automated Shader Self-Test**: Added an offscreen rendering self-test that verifies bounding transparency, pixel sanity, and plausible luminance on launch. Automatically switches device to safe Haze blur with rim highlight if any anomaly is detected, with a debug switch to force-fail for testing.
- **Glass Appearance Controls**: Unified glass controls under Settings controlling every surface (dock, mini player, cards, sheets, dialogs, lyrics, home widgets): sliders for Opacity, Blur Strength, Grain Texture, and Edge Highlight (0–100%), presets (*Clear*, *Light*, *Balanced*, *Frosted*), and *Fully Transparent Glass* mode with *Keep Text Readable* scrim.
- **Graphics Diagnostics Screen**: New screen under *More &rarr; Graphics Diagnostics* displaying device model, Android OS, SoC model, GPU renderer from temporary EGL context, display metrics, self-test status, side-by-side shader previews, and safe graphics mode toggle.
- **Diagnostics Dump Engine & 60s Record Mode**: One-tap export to `nodeplayer-diagnostics-<date>.zip` containing PixelCopy screenshot, rolling ring buffer of 200 graphics events, process logcat, and hardware info with strictly zero personal data. Includes 60s record mode and system share sheet integration.
- **Graphics Glitch Issue Template**: Added GitHub issue template and website FAQ entry for visual and shader troubleshooting.

### Changed
- Extended widget glass opacity range to `0%..100%`, dynamically synced with app glass theme tokens.
- Optimized rendering pipeline to completely skip blur and shader layers when opacity or blur is zero.
- Re-architected glass tokens into a single DataStore source of truth in `NodePlayerTheme`.

---

## [1.0.0] - 2026-11-02

### Added
- **Multi-Stage Artwork Decoder**: Custom Coil `AudioArtworkFetcher` supporting Android 10+ (API 29+) MediaStore `loadThumbnail`, `MediaMetadataRetriever` embedded APIC/FLAC picture extraction, and content resolver fallbacks.
- **Enhanced Launcher Widgets**: Card (4x2), Lyrics (4x3), Slim (4x1), and Square (2x2) widgets display sharp album art, palette-adaptive wave shaders, and track persistence via SharedPreferences when playback is idle.
- **Expanded Home Screen Suggestions**: Added dynamic shelves beyond Rediscover—Heavy Rotation ("On Repeat"), Artist Spotlight mix, Hi-Res Studio Masters with sample rate/bit depth badges, Fresh Discoveries, and Quick Mixes chips.
- **Truthful Signal Path**: Real-time visual pipeline inspection card showing source encoding, 32-bit float DSP chain modifications, AudioTrack sink configuration, and hardware routing node.
- **Algorithmic Reverb**: Freeverb 4-comb diffusion processor with configurable room size and damping controls.
- **Sound Lab & Acoustic Diagnostics**: Built-in fake hi-res audio spectrum detector, EBU R128 integrated loudness scanner, dynamic range (DR) approximation meter, and real-time audio visualizer.
- **Listening Stats & Yearly Recap**: Wall-clock listening time tracking with top artists, top albums, and annual recap insights stored 100% locally on device.
- **Local Diagnostics**: Private `LocalCrashLogger` and in-app sanitized crash viewer with zero external uploads.
- **Data Portability**: Unified `.nodebackup` export and import for playlists, play counts, favorites, and settings, plus full M3U playlist file import.
- **First-Run Onboarding**: 3-step onboarding flow introducing audio engine features and ColorOS / realme GT 6T battery optimization guidance.

### Changed
- Refactored `HomeScreen`, `FrostedNavAndPlayer`, and `NowPlayingSheet` to consume unified `Track` Coil models.
- Updated release build type with R8 minification and resource shrinking enabled.
- Hardened background widget action receivers to route through broadcast intents, avoiding Android 12+ foreground start restrictions.

### Fixed
- Fixed missing album artwork in launcher AppWidgets and in-app carousels on Android 10+ scoped storage.
- Fixed sample rate and bit-depth parsing from genuine audio file headers (including FLAC STREAMINFO metadata offsets).
- Fixed main-thread player access and strict mode I/O violations during playback service preloading.

---

## [0.1.0] - 2026-11-01

### Added
- **Core Architecture**: Modular pure-Kotlin foundation (`:core-model`, `:core-dsp`, `:core-library`) and Android application module (`:app-android`).
- **32-Bit Float Audio DSP**: High-precision audio pipeline including 10-band biquad parametric equalizer ($\pm0.1\text{ dB}$ tolerance at band centres), linear pass-through soft-knee limiter (pure linear below $-0.9\text{ dBFS}$), ISO 226 loudness contour, harmonic bass synthesizer, partitioned convolution reverb, and zero-allocation TPDF dither.
- **Dual-ExoPlayer Crossfade**: Seamless track crossfading with mathematical curves (Equal Power, Linear, Logarithmic) and auto-gapless live album detection.
- **System-Wide EQ**: Android foreground service attaching software `DynamicsProcessing` effect to compatible third-party players (tested with Spotify and YouTube Music) with anti-double-EQ bypass.
- **Liquid Glass Compose UI**: Glassmorphism design language using Haze backdrop blur, Android 13+ AGSL optical lens refraction dock shader, and OLED / Dark / Light themes.
- **Four Liquid Glass Home-Screen Widgets**: Hardware offscreen rendered AGSL glass widgets (`RenderNode` + `HardwareRenderer` + `ImageReader`) with 20 tap-to-seek slices, synced lyrics, and translucent 60 fps scrub sheet.
- **Metadata Parsers**: Pure-Kotlin parsers for `.lrc` synchronized lyrics, AutoEQ headphone correction text profiles, `.cue` sheets, and `.m3u` playlists.
- **Last.fm Scrobbling**: Opt-in Last.fm scrobbler with browser authorization flow and offline retry queue.
- **Battery Optimization Guide**: In-app guidance tailored for ColorOS and Android battery management.
