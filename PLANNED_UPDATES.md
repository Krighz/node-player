# NodePlayer: Planned Updates & Comprehensive Feature Roadmap

> **Target Codebase:** NodePlayer (`D:\Node player\node-player`)  
> **Reference Architecture:** BitChord (`D:\BitChord-main`)  
> **Location:** `C:\Users\satya\Documents\Planned Updates\PLANNED_UPDATES.md`  
> **Workflow Model:** Two-Phase GSD (Getting Shit Done) Execution Pipeline  
> **Status:** Master Architecture & Stabilization Roadmap

---

## Phase 0: Core Stabilization, Performance & Motion Overhaul (Priority First)
*Eliminates frame drops, unifies the visual language with Now Playing, and wires the spring-physics engine across all screens.*

### 0.1 Eliminating Lag & Frame Drops
- **Fix 120 FPS Fullscreen Overdraw (`LiquidGlassBackground.kt`):**
  - Remove per-frame dither/noise overlay and optimize gradient drawing.
- **Stop Per-Frame `RenderEffect` Re-Creation (`GlassRefractionShader.kt`):**
  - Cache `RenderEffect.createRuntimeShaderEffect(...)` keyed by layout dimensions and optical parameters so AGSL shaders only recompile uniforms when bounds or settings change.
- **Optimize Spectrogram Rendering (`SpectrogramView.kt`):**
  - Replace the `64 × 48` nested `drawRect` loop (3,072 draw calls/frame) with a single `64 × 48` `IntArray` pixel buffer rendered via a single `drawImage(...)` call.
- **Fix Scroll Touch Slop & List Recomposition (`MotionTokens.kt`, `LibraryScreen.kt`):**
  - Replace `pointerInput { detectTapGestures(...) }` inside `Modifier.pressScale` with `MutableInteractionSource` + `collectIsPressedAsState()` so list rows never fight `LazyColumn` vertical scroll gestures.

### 0.2 Visual Consistency Across All Screens
- **Fix Flat Cards (`GlassTheme.kt`):**
  - Implement a consistent 2-tier obsidian liquid surface with top-to-bottom translucent gradients, `1.dp` top-lit specular hairline borders (`liquidBorderBrush`), and standardized `ContinuousRoundedRectangle` squircle corners matching `NowPlayingSheet`.
- **Purge Hardcoded Accent Overrides:**
  - Replace hardcoded `Color(0xFF6366F1)` and `Color(0xFF818CF8)` values in `SettingsScreen.kt` (`SettingsHubCard`) and `WidgetSettingsScreen.kt` with `LocalAccentColor.current`.
- **Replace Standard Material3 Controls with Liquid Components:**
  - Upgrade `NowPlayingSheet.kt` seekbar to `LiquidGlassSlider` and build all hardware controls with `LiquidToggle`, `LiquidSlider`, `LiquidChip`, and `LiquidButton`.

### 0.3 Wiring `MotionTokens` & Spring Physics
- **Screen & Tab Transitions (`MainActivity.kt`, `LibraryScreen.kt`, `SettingsScreen.kt`):**
  - Wrap route changes, Settings Hub <-> Subpage navigation, and Library tab switches in `AnimatedContent` driven by `MotionTokens.resolveSpringSpec(themeConfig)` and `themeConfig.screenTransitionStyle` (`SLIDE`, `FADE`, `SHARED`).
- **List & Card Entrance Animations (`LibraryScreen.kt`):**
  - Apply `Modifier.animateItem()` with `MotionTokens.Smooth` placement specs across `SongsList`, `AlbumsGrid`, `ArtistsList`, `FoldersList`, and `PlaylistsTab`.
- **Interactive *Now Playing* Motion (`NowPlayingSheet.kt`):**
  - Add real-time horizontal drag offset + subtle rotation tilt to `HeroArtwork` during swipe-to-skip gestures and smooth `AnimatedContent` track transitions.

### 0.4 Critical Bug & Encoding Fixes
- **Remove Duplicate Playback Section (`SettingsScreen.kt`):** Delete the dead second `PLAYBACK ENGINE` card (`gaplessEnabled` / `crossfadeSeconds`).
- **Fix Empty `.nodebackup` Exports (`SettingsScreen.kt`):** Wire real `allTracks` and `effectConfig` from `PlayerViewModel` into `NodeBackupManager.exportNodeBackup(...)`.
- **Restore `Albums` Tab & Artist Navigation (`LibraryScreen.kt`):** Add `"Albums"` back into the Library tab bar (`Songs`, `Albums`, `Artists`, `Folders`, `Playlists`) and wire `onArtistClick` to open an artist detail view.
- **Fix Mojibake UTF-8 Strings:** Clean corrupted symbols across UI files.

---

## Phase 0.5: Dedicated Hardware Settings Page, Grain Removal & Symmetrical Dock

### 0.5.1 New Dedicated Hardware & Audio Engine Settings Page (`HardwareSettingsScreen.kt`)
- Dedicated **"Hardware & Audio Engine"** hub entry in `SettingsScreen.kt` consolidating all hardware-level controls using 100% Liquid UI components (`LiquidToggle`, `LiquidSlider`, `LiquidChip`, `LiquidButton`):
  1. **Audio DAC & Output Hardware:** Bit-Perfect Direct USB DAC, 32-bit Float Pipeline, Hardware Resampler Target Rate (`Auto`, `44.1k`, `48k`, `96k`, `192k`), Auto-Switch Per-Device Profiles, Resume on Headset Connect, System-Wide Hardware Effects (`DynamicsProcessing`).
  2. **Display & GPU Shader Hardware:** Display Refresh Rate Cap (`Auto`, `60Hz`, `120Hz`, `Highest`), AGSL Shader Pipeline Mode (`Auto`, `Full`, `Simple`, `Blur Only`, `Off`), Hardware Performance Mode, Anti-Banding Display Dithering, Optical Diagnostics & AGSL Self-Test status.
  3. **Sensors & Tactile Haptics:** System Haptic Feedback, Accelerometer Shake-to-Extend Sleep Timer (toggle + sensitivity & extension duration sliders).
  4. **CPU, Thermal & Battery Hardware:** Background Audio Analysis CPU Throttle (`Slow`, `Normal`, `Fast`), Analyze Only While Charging, ColorOS / realme Battery & App Hardware shortcuts.
  5. **Local Network Hardware (`Node Connect`):** LAN mTLS & Multicast Discovery, Direct PCM Audio Cast, Mute Local Output During Cast, Remote Control, Library Sharing, Playback Handoff, PIN/QR Device Pairing.

### 0.5.2 Complete Removal of Grain / Texture Slider & Shader Noise
- Remove the **"Glass Texture (Grain)"** slider completely from `CustomizeUiScreen.kt`.
- Remove `GlassNoiseCache` and `grainModifier` from `GlassTheme.kt` and strip noise hash math from `GlassRefractionShader.kt`.

### 0.5.3 Symmetrical Bottom Navigation Dock Fix (`ConvxLiquidDock.kt`)
- Center the bottom dock capsule horizontally with equal-width slots, align the `56.dp` capsule height with the `56.dp` circular search button, and fix the circular `48.dp` optical lens puck center offset so it aligns symmetrically on every tab icon.

---

## Phase 1–4: Future Feature Roadmap (Deferred for Later Implementation)
1. **Pure YouTube History Taste Engine** (`FEmusic_history` weighted sampling, `RDAMVM<videoId>` dynamic stations, contextual time-of-day moods).
2. **Playlist Importer (CSV & TXT)** (Spotify/Apple/TunemyMusic CSV & line-delimited TXT resolver).
3. **Offline Downloader & Multi-Tier Queue Manager** (1–4 concurrent threads, Wi-Fi auto-pause/resume, storage manager).
4. **Discord Rich Presence (RPC) Integration** (Windows named pipe IPC + Android Mobile Gateway WebSocket).
5. **High-Fidelity FLAC to OPUS Converter** (`192 kbps` / `128 kbps` / `96 kbps` VBR with Vorbis tag & cover art preservation).
