# What's Changed After Node Player v1.8.0

> **Build Version:** Unspecified / Post-v1.8.0 (`feature/liquid-dock`)  
> **Date:** November 2026  
> **Target Platforms:** Android 8.0–16 (API 26–34+), Windows 10/11 (x64)  
> **Architectural Standard:** Compose Multiplatform 1.6.11, Kotlin 1.9.24, Java 17  

---

## 🌟 Executive Summary

Following the release of **Node Player v1.8.0** (which introduced 120 Hz display locking, AGSL liquid glass shaders, visionOS streaming resolution, and the Convx mini-player dock), substantial architectural, visual, and stability advancements were implemented. 

This document details every major system enhancement, interface refinement, and bug fix applied across the Kotlin/Compose codebase (`app-android`, `app-desktop`, `core-ui`, `core-network`, `core-library`, `core-model`, and `core-common`).

---

## 🎨 1. UI & Theme Color Architecture

### 1.1 Standard Dark Mode Background (`#262626`)
- **Theme Color Standardization:** Unified the default dark mode background across the entire application to hex `#262626` (`Color(0xFF262626)`), providing an elegant charcoal tone with high contrast and smooth visual warmth.
- **Theme Color Schemes (`Theme.kt`):**
  - Updated `DarkColorScheme`: `background` set to `Color(0xFF262626)`, and `surface` set to `Color(0xFF303030)`.
  - In `NodePlayerTheme`: Dynamically assigns `background = if (isOled) Color.Black else if (isDark) Color(0xFF262626) else Color(0xFFF4F5FB)` and `surface = if (isOled) Color(0xFF0A0A0A) else if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF)`.
- **Liquid Glass Background Tinting (`LiquidGlassBackground.kt`):**
  - Updated dark base gradient anchors: `DARK_BASE_COLORS = listOf(Color(0xFF202020), Color(0xFF262626), Color(0xFF1C1C1C))`.
  - Updated fallback flat color: `flatColor = if (isLight) Color(0xFFF4F5FB) else Color(0xFF262626)`.
- **Glass Surfaces (`GlassTheme.kt`):**
  - Updated `Modifier.glassSurface(...)` tinting: `isDark -> Color(0xFF262626).copy(alpha = (0.78f * effectiveAlpha).coerceIn(0.30f, 0.88f))`.

### 1.2 Pure OLED Black (`#000000`) Guarantee
- **Pixel Shutdown Preservation:** When OLED Black mode is enabled (`isOledBlack == true` or `themeMode == "OLED"`), all screens and surfaces render pure pitch black (`Color.Black` / `#000000`), guaranteeing complete OLED subpixel power-down and maximum battery conservation.
- **Root Container De-hardcoding:**
  - `OnlineHomeView.kt`: Replaced hardcoded `.background(Color.Black)` with dynamic theme color evaluation:
    ```kotlin
    val isOled = themeConfig.isOledBlack || themeConfig.themeMode == "OLED"
    val homeBackgroundColor = if (isOled) Color.Black else Color(0xFF262626)
    ```
  - `LibraryScreen.kt`: Replaced hardcoded `Color.Black` with `if (isOled) Color.Black else Color(0xFF262626)`.
  - `PlaylistDetailView.kt`: Replaced hardcoded `Color.Black` on root container with `if (isOled) Color.Black else Color(0xFF262626)`.

---

## 🎤 2. Full-Screen Synchronized Lyrics View (`FullScreenLyricsView.kt`)

### 2.1 Default Collapsed Playback Controls
- **Distraction-Free Immersion:** Initialized `var controlsVisible by remember { mutableStateOf(false) }`, ensuring that when the user enters the full-screen lyrics screen, the bottom playback dock starts collapsed by default, putting the focus entirely on the lyrics and artwork.
- **Timed Auto-Collapse:** When the user taps the screen to reveal controls, a `LaunchedEffect(isPlaying, controlsVisible, interactionTick)` runs a `3500L` (3.5s) countdown while audio is actively playing, smoothly animating the controls back out via `fadeOut() + slideOutVertically()`.

### 2.2 Fix for the Scroll Auto-Popup Bug
- **Bug Resolution:** Previously, scrolling through the lyrics list triggered `registerInteraction()`, which inadvertently forced `controlsVisible = true` and popped up the bottom controls bar unexpectedly during reading.
- **Fix:** Decoupled list scroll listening from control visibility. `listState.isScrollInProgress` now exclusively manages `userScrolledManually = true` (to show the jump button and pause auto-scrolling) and **never** mutates `controlsVisible`. Tapping the screen remains the sole user gesture to toggle controls.

### 2.3 Redesigned Top Header
- **Centered Drag Handle Pill:** Placed inside `statusBarsPadding()`, measured at `36.dp` × `4.dp`, clipped with `CircleShape`, tinted `Color.White.copy(alpha = 0.35f)`, with a tap handler invoking `onClose()`.
- **Artwork Thumbnail:** Fixed dimensions to `48.dp` × `42.dp` with `RoundedCornerShape(10.dp)` on the left side of the header.
- **Track Information:** Song title (`16.sp`, `FontWeight.Bold`, white) above artist name (`13.sp`, `FontWeight.Normal`, white with `alpha = 0.65f`) with 1-line ellipsis clipping.
- **Context-Sensitive Action Buttons:** Two `38.dp` translucent circular buttons (`CircleShape`, `Color.White.copy(alpha = 0.12f)`):
  - **Star Button:** Toggles track favorite state (`Icons.Rounded.Star` in `#FBBF24` gold vs `Icons.Rounded.StarBorder`).
  - **Context Action Button:** Automatically switches glyph and action based on control state:
    - Displays **More Options** (`Icons.Rounded.MoreHoriz`) when `controlsVisible == true`.
    - Displays **Share** (`Icons.Rounded.Share`) when `controlsVisible == false`.

### 2.4 Removal of Black Bottom Vignette Scrim
- **Clean Palette Bleed:** Stripped out the `100.dp` bottom vertical gradient overlay and `scrimBrush`. The dynamic artwork palette now flows cleanly and uninhibited all the way to the bottom display bezel.

### 2.5 Bottom-Left "Jump to Current" Icon Button
- **Ergonomic Relocation:** Removed the large, centered pill button in the middle of the screen.
- **Subtle Circular Icon:** Replaced with a compact `36.dp` circular button anchored at `Alignment.BottomStart` (`padding(start = 20.dp, bottom = 16.dp)`), featuring a `Color.White.copy(alpha = 0.16f)` background and an `18.dp` `Icons.Rounded.ArrowDownward` glyph. Tapping it resets manual scroll mode and smoothly animates the active lyric line back into view.

### 2.6 Word-by-Word Karaoke Engine Preservation
- **Pure Karaoke Integrity:** Maintained 100% untouched lyric line rendering, timing offsets, bundled `Inter` typography (`28.sp` Bold), smooth auto-scroll interpolation, binary search $O(\log N)$ active line lookup, and instrumental gap indicators with pulsing rhythmic dots.
- **No Watermarks or Clutter:** Zero watermarks, globe icons, or translation tags added.

---

## 📱 3. Now Playing Screen & System Audio Integration (`NowPlayingView.kt`)

### 3.1 Hardware-Synchronized System Volume Slider
- **Android `AudioManager` Integration:** Connected the volume slider in `NowPlayingView` directly to Android's `AudioManager.STREAM_MUSIC`.
  - **Initialization:** Initializes slider fraction from `audioManager.getStreamVolume(STREAM_MUSIC).toFloat() / maxVolume`.
  - **Interactive Drag & Tap:** Added `detectTapGestures` and `detectDragGestures` to the slider container with comfortable touch padding, mapping position fractions directly to `audioManager.setStreamVolume(STREAM_MUSIC, targetVolume, 0)`.
  - **Hardware Button Synchronization:** Registered a `DisposableEffect` with a `BroadcastReceiver` for `"android.media.VOLUME_CHANGED_ACTION"`. Physical volume rocker presses now instantly update the on-screen slider in real time.
- **Multiplatform Decoupling:** Added `volume: Float` and `onVolumeChange: ((Float) -> Unit)?` parameters to `NowPlayingView` with default fallback to internal state, preserving pure multiplatform compatibility for Desktop while empowering native Android execution.

### 3.2 16:9 Music Video Widescreen Player
- **Dynamic Aspect Ratio Morphing:** Automatically transitions the hero media container from a square `1:1` album art format to a widescreen `16:9` (`16f / 9f`) container when playing video tracks.
- **Conditional Video Overlays:**
  - Wrapped the top-right `"480p"` stream quality badge and fullscreen expand button in `if (isMusicVideo) { ... }`.
  - Standard audio tracks display clean square cover art without video badge overlays.

### 3.3 Dynamic Ambient Artwork Palette
- **Multi-Swatch Extraction:** Full-bleed background animated with `animateColorAsState` using colors extracted from the active track's cover art via `PaletteExtractor`.
- **Contrast Adaptation:** Enforces legibility of white transport typography against extracted vibrant and dark muted swatches.

---

## 🌐 4. YouTube Music Online Discovery & Authentication

### 4.1 Multi-Shelf Online Home Feed (`OnlineHomeView.kt`)
- **6 Structured Shelves:**
  1. **Top Picks for You:** 1:1 squircle recommendation cards.
  2. **Recents:** Horizontal and vertical history items with last played timestamps.
  3. **Find Your Mood:** Category and genre cards with duotone color matrix filtering.
  4. **Albums & Singles:** Curated releases with cover art and artist metadata.
  5. **Music Videos:** 16:9 widescreen video cards with duration and view counts.
  6. **Trending:** Regional trending charts with rank indicators.
- **Pull-to-Refresh & Shimmer Skeletons:** Implemented responsive loading shimmers for shelves while fetching live payloads.

### 4.2 Account Profile Hydration & Persistence
- **Profile Sheet (`HomeProfileSheet.kt`):** Displays authentic Google/YouTube avatar thumbnail, channel handle (`@username`), account name, and email address.
- **Secure Persistence (`SecureSessionStore`):** Persists session tokens, cookies, avatar URLs, and channel handles with immediate restoration on application boot.
- **Session-Isolated Cache (`HomeFeedDiskCache`):** Separate caches for guest vs authenticated accounts, ensuring clean feed refreshes and zero session data crossover.

### 4.3 Category Browsing & Playlist Details
- **`CategoryPlaylistsView.kt`:** Deep exploration of moods, genres, and category shelves with responsive grid layouts.
- **`PlaylistDetailView.kt`:** Dynamic ambient glow backdrop derived from playlist artwork and header collapse animations during list scrolling.

---

## 🎶 5. TTML Syllable Lyrics & Karaoke Enhancements

### 5.1 Syllable-Level TTML Parser
- **Rich Synchronized Parsing:** Parses syllable-level timing from Apple Music / TTML formats (`<tt>`, `<span>` with `begin` and `end` timestamps).
- **Duet & Vocalist Alignment:** Supports multi-vocalist assignments (lead vocalist vs backing vocalist, left/right visual orientation).
- **Instrumental Countdown:** Added rhythmic countdown dots for instrumental breaks exceeding 4 seconds.

### 5.2 Active Glow Karaoke Wipe
- **`WordByWordKaraokeText.kt`:** Real-time horizontal sweep fill driven by `positionProvider()` with active text glow and smooth per-word bounce physics.

---

## ⚡ 6. GPU Shaders, Backdrops & Memory Optimization

### 6.1 Zero-Allocation GPU Backdrops (`ArtworkMeshBackdrop`)
- **Direct GPU Mesh Shaders:** Implemented zero-allocation GPU mesh backgrounds rendering soft organic movement derived from cover art without per-frame CPU allocations.
- **Forwarding Facades:** Refactored legacy `AmbientLyricsBackdrop` and `AlbumArtGradientBackdrop` into lightweight forwarding facades, reducing GC pressure.

### 6.2 Process & Build Stability
- **Windows File Lock Resolution:** Resolved Gradle daemon file handle locks on `bundleLibCompileToJarDebug/classes.jar` during multiplatform builds.
- **Compose Stability:** Maintained strict Compose compiler stability rules (`compose_compiler_config.conf`), ensuring zero recomposition loops inside draw and layout phases.

---

## 📁 7. Modified & Added Files Index

| File Path | Description of Changes |
| :--- | :--- |
| `app-android/.../MainActivity.kt` | Integrated Android `AudioManager`, `VOLUME_CHANGED_ACTION` broadcast receiver, system volume state, and passed volume bindings to `NowPlayingView`. |
| `app-android/.../ui/theme/Theme.kt` | Configured `#262626` default dark mode background, `#303030` surface cards, and preserved pitch black for OLED. |
| `app-android/.../ui/theme/GlassTheme.kt` | Updated glass surface tinting for dark mode `#262626`. |
| `app-android/.../ui/components/LiquidGlassBackground.kt` | Updated dark base color array and fallback flat color to `#262626`. |
| `app-android/.../ui/screens/HomeScreen.kt` | Wired category navigation, explore routing, and updated online feed delegates. |
| `app-android/.../ui/screens/LibraryScreen.kt` | Dynamic OLED vs `#262626` background evaluation and updated listening experience metrics. |
| `core-ui/.../ui/lyrics/FullScreenLyricsView.kt` | Controls collapsed by default, eliminated scroll auto-popup bug, redesigned top header with 48x42dp thumbnail and dual action buttons, removed bottom vignette scrim, and added bottom-left jump-to-current icon. |
| `core-ui/.../ui/lyrics/WordByWordKaraokeText.kt` | Word-by-word karaoke sweep extensions, syllable wipe rendering, and active text glow. |
| `core-ui/.../ui/player/NowPlayingView.kt` | Added `volume` and `onVolumeChange` parameters, interactive volume bar with tap/drag gestures, and conditional 16:9 video badge/fullscreen overlays. |
| `core-ui/.../ui/screens/OnlineHomeView.kt` | Dynamic OLED vs `#262626` background, 6 YouTube Music shelves, and session-isolated feed loading. |
| `core-ui/.../ui/screens/PlaylistDetailView.kt` | Dynamic OLED vs `#262626` background and ambient glow header styling. |
| `core-ui/.../ui/screens/CategoryPlaylistsView.kt` | Multi-shelf category and mood browsing interface. |
| `core-network/.../StreamResolver.kt` | YouTube visionOS profile streaming resolution with visitor-data authentication. |
| `core-network/.../service/NodeConnectHub.kt` | Local P2P Wi-Fi casting and state synchronization enhancements. |

---

## 🧪 8. Verification & Test Confirmation

- **Kotlin Multiplatform Compilation:** Verified clean compilation via:
  ```powershell
  .\gradlew :app-android:compileDebugKotlin :core-ui:compileKotlinDesktop
  ```
  Result: **`BUILD SUCCESSFUL`** with zero compilation errors.
- **Physical Device Validation:** Verified on physical Android 16 (realme RMX3853) with 120 Hz display lock, responsive volume slider dragging, fluid karaoke lyrics sync, and instant home shelf navigation.
